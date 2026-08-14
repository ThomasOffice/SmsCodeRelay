"""邮箱验证码轮询器 - IMAP 拉取收件箱，提取验证码并交给共享处理管道。

用 Python 内置 imaplib 定时连接邮箱（默认 QQ 邮箱 imap.qq.com:993，SSL），
解析新邮件的主题与纯文本正文，用 code_parser 的规则提取验证码，
再调用 handler.process_code(code, sender) 复用短信验证码的同一管道
（去重 → 剪贴板 → 通知 → 记录列表）。
"""
import email
import email.message
import imaplib
import logging
import re
import threading
import time
from email.header import decode_header

log = logging.getLogger("email_poller")

# 常见邮箱 IMAP 预设（服务器, 端口, 是否 SSL）
EMAIL_PRESETS = {
    "QQ 邮箱": ("imap.qq.com", 993, True),
    "网易 163": ("imap.163.com", 993, True),
    "网易 126": ("imap.126.com", 993, True),
    "Gmail": ("imap.gmail.com", 993, True),
    "Outlook": ("outlook.office365.com", 993, True),
    "自定义": ("", 993, True),
}

# 严格模式下，规则必须包含这些验证码关键词，避免从营销邮件中误提取
EMAIL_CODE_KEYWORDS = (
    "验证码", "动态码", "校验码", "认证码", "安全码", "授权码",
    "verification", "otp", "passcode", "security code",
)


def _is_keyword_rule(pattern: str) -> bool:
    """判断正则规则是否含验证码关键词（用于邮箱严格模式）。"""
    if not pattern:
        return False
    lower = pattern.lower()
    return any(k.lower() in lower for k in EMAIL_CODE_KEYWORDS)


def _decode_header_text(value: str) -> str:
    """解码 MIME 编码的主题/发件人等文本。"""
    if not value:
        return ""
    parts = decode_header(value)
    out = []
    for text, enc in parts:
        if isinstance(text, bytes):
            try:
                out.append(text.decode(enc or "utf-8", errors="replace"))
            except (LookupError, TypeError):
                out.append(text.decode("utf-8", errors="replace"))
        else:
            out.append(text)
    return "".join(out).strip()


def _get_body_text(msg: email.message.Message) -> str:
    """提取纯文本正文（优先 text/plain，其次 text/html 简单去标签）。"""
    if msg.is_multipart():
        text_plain = None
        text_html = None
        for part in msg.walk():
            if part.get_content_maintype() != "text":
                continue
            payload = part.get_payload(decode=True)
            if payload is None:
                continue
            charset = part.get_content_charset() or "utf-8"
            try:
                content = payload.decode(charset, errors="replace")
            except (LookupError, TypeError):
                content = payload.decode("utf-8", errors="replace")
            if part.get_content_subtype() == "plain" and text_plain is None:
                text_plain = content
            elif part.get_content_subtype() == "html" and text_html is None:
                text_html = content
        body = text_plain or text_html or ""
        if text_plain is None and text_html:
            import re
            body = re.sub(r"<[^>]+>", " ", body)
            body = re.sub(r"\s+", " ", body)
        return body
    payload = msg.get_payload(decode=True)
    if payload is None:
        return ""
    charset = msg.get_content_charset() or "utf-8"
    try:
        return payload.decode(charset, errors="replace")
    except (LookupError, TypeError):
        return payload.decode("utf-8", errors="replace")


class EmailPoller(threading.Thread):
    """IMAP 收件箱轮询线程，定时拉取新邮件提取验证码。"""

    def __init__(self, handler, record, stop_event: threading.Event, cfg: dict = None):
        super().__init__(daemon=True, name="EmailPoller")
        self.handler = handler
        self.record = record
        self.stop_event = stop_event
        self.cfg = dict(cfg or {})
        self.enabled = bool(self.cfg.get("enabled", False))
        self.processed_uids = set()  # 本进程内已处理过的 UID，防止重复
        self._last_error = ""
        self._conn = None
        self._apply_cfg()

    def _apply_cfg(self):
        self.imap_host = self.cfg.get("imap_host", "imap.qq.com") or "imap.qq.com"
        self.imap_port = int(self.cfg.get("imap_port", 993) or 993)
        self.username = self.cfg.get("username", "") or ""
        self.password = self.cfg.get("password", "") or ""
        self.poll_interval = max(int(self.cfg.get("poll_interval", 30) or 30), 5)
        self.since_days = int(self.cfg.get("since_days", 1) or 1)
        self.mark_seen = bool(self.cfg.get("mark_seen", True))
        self.strict = bool(self.cfg.get("strict", True))

    def apply_config(self, cfg: dict):
        """热更新配置（设置对话框保存后调用）。"""
        self.cfg = dict(cfg or {})
        self.enabled = bool(self.cfg.get("enabled", False))
        self._apply_cfg()
        if not self.enabled:
            self._close()
        log.info("邮箱配置已更新: enabled=%s host=%s user=%s",
                 self.enabled, self.imap_host, self.username or "(未填)")

    @property
    def last_error(self) -> str:
        return self._last_error

    def _close(self):
        try:
            if self._conn is not None:
                self._conn.logout()
        except Exception:
            pass
        self._conn = None

    def _connect(self) -> bool:
        """建立 IMAP 连接并登录。成功返回 True。"""
        try:
            self._close()
            conn = imaplib.IMAP4_SSL(self.imap_host, self.imap_port, timeout=15)
            conn.login(self.username, self.password)
            # 给后续所有 IMAP 调用设置超时，避免轮询线程被挂死
            try:
                conn.sock.settimeout(20)
            except Exception:
                pass
            self._conn = conn
            self._last_error = ""
            log.info("邮箱连接成功: %s (%s:%d)", self.username, self.imap_host, self.imap_port)
            return True
        except Exception as e:
            self._last_error = f"连接失败: {e}"
            log.warning("邮箱连接失败: %s", e)
            return False

    @staticmethod
    def _extract_fetch_raw(msg_data) -> bytes:
        """从 imaplib FETCH 响应中稳健提取原始邮件字节。

        不同 IMAP 服务器的返回结构可能不同，逐个元素查找含字面量的元组，
        避免因结构差异而静默跳过邮件。
        """
        for part in msg_data:
            if isinstance(part, tuple) and len(part) >= 2:
                raw = part[1]
                if isinstance(raw, bytes) and raw:
                    return raw
        return b""

    def _fetch_recent(self) -> list:
        """搜索最近 since_days 天的邮件，返回 (uid, Message) 列表。"""
        conn = self._conn
        try:
            conn.select("INBOX", readonly=not self.mark_seen)
            date = time.strftime("%d-%b-%Y",
                                 time.localtime(time.time() - self.since_days * 86400))
            # 必须用 uid('search') 获取 UID；普通 search() 返回的是序号，
            # 若当作 UID 传给 uid('fetch') 会因 UID 不存在而静默返回空。
            status, data = conn.uid("search", None, "SINCE", date)
            if status != "OK" or not data or not data[0]:
                log.info("邮箱搜索无结果 (SINCE %s)", date)
                return []
            uids = data[0].split()
            items = []
            skipped = 0
            for uid in uids:
                uid = uid.decode("ascii")
                if uid in self.processed_uids:
                    continue
                st, msg_data = conn.uid("fetch", uid, "(BODY.PEEK[])")
                if st != "OK" or not msg_data:
                    skipped += 1
                    continue
                raw = self._extract_fetch_raw(msg_data)
                if not raw:
                    skipped += 1
                    continue
                try:
                    msg = email.message_from_bytes(raw)
                except Exception:
                    skipped += 1
                    continue
                self.processed_uids.add(uid)
                items.append((uid, msg))
            log.info("邮箱拉取完成: 共 %d 封新邮件, 跳过 %d", len(items), skipped)
            return items
        except Exception as e:
            self._last_error = f"拉取失败: {e}"
            log.warning("邮箱拉取失败: %s，将重连", e)
            self._close()  # 连接可能已失效，丢弃以便下次重连
            return []

    def _mark_seen(self, uid: str):
        """将单封邮件标记为已读。"""
        if not self.mark_seen or not self._conn:
            return
        try:
            self._conn.uid("STORE", uid, "+FLAGS", "(\\Seen)")
        except Exception:
            pass

    def run(self):
        log.info("邮箱轮询线程启动: host=%s user=%s interval=%ds",
                 self.imap_host, self.username or "(未填)", self.poll_interval)
        while not self.stop_event.is_set():
            try:
                if self.enabled:
                    if self.username and self.password:
                        if self._conn is None and not self._connect():
                            time.sleep(self.poll_interval)
                            continue
                        for uid, msg in self._fetch_recent():
                            code, sender = self._extract(msg)
                            if code:
                                self.handler.process_code(code, sender)
                            self._mark_seen(uid)
                    else:
                        self._last_error = "未配置邮箱账号或授权码"
                        log.warning("未配置邮箱账号或授权码")
                time.sleep(self.poll_interval)
            except Exception as e:
                self._last_error = str(e)
                log.warning("邮箱轮询异常: %s", e)
                time.sleep(self.poll_interval)
        self._close()
        log.info("邮箱轮询线程已停止")

    def _extract(self, msg: email.message.Message) -> tuple:
        """从邮件主题/正文提取验证码，返回 (code, sender)。"""
        from code_parser import extract_code
        rules = self.handler.rules
        if self.strict:
            # 严格模式：只使用含验证码关键词的规则，避免营销邮件误提取
            rules = [r for r in rules if _is_keyword_rule(r.get("pattern", ""))]
        subject = _decode_header_text(msg.get("Subject", ""))
        from_header = msg.get("From", "")
        sender = _decode_header_text(from_header).split("<")[0].strip()
        if not sender:
            sender = from_header

        body = ""
        try:
            body = _get_body_text(msg)
        except Exception:
            pass

        code = extract_code(subject, rules) or extract_code(body, rules)
        if code:
            log.info("邮箱验证码: %s (发件人: %s, 主题: %s)", code, sender, subject[:40])
        else:
            log.info("邮件未提取到验证码: 主题=%s 发件人=%s 正文=%s",
                     subject[:40], sender[:40], body[:120].replace("\n", " "))
        return code or "", sender