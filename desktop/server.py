"""验证码蓝牙中继 - PC 端服务

通过 Winsock2 蓝牙 RFCOMM 直接监听 Android App 推送的验证码消息（绕过 COM 端口），
校验 token、去重后写入系统剪贴板，并弹出 Windows 通知。
双击运行后隐藏到系统托盘，双击托盘图标弹出轻量状态窗口。

UI 位于 ui.py（CustomTkinter），配置与路径管理位于 config.py。
"""
import ctypes
import sys

# Windows 高 DPI 支持：必须在创建任何 Tk 窗口之前设置进程 DPI 感知。
# 否则 Tk 窗口句柄会保留 DPI-Unaware 上下文，被系统按 96 DPI 虚拟化，
# 导致打包后的 exe 在 150%/200% 缩放下界面缩小一半、字体模糊。
# （开发环境的 python.exe 自带 DPI 感知 manifest，不受影响；exe 无 manifest，需在此声明。）
if sys.platform == "win32":
    try:
        ctypes.windll.shcore.SetProcessDpiAwareness(2)  # Per-Monitor DPI Aware
    except Exception:
        try:
            ctypes.windll.user32.SetProcessDPIAware()
        except Exception:
            pass

import json
import logging
import sys
import threading
import time

import pyperclip

from config import get_config_dir, load_config
from bt_rfcomm import BtRfcommServer
from email_poller import EmailPoller
from webview_app import RelayApp

try:
    from win11toast import toast
    HAS_TOAST = True
except Exception:
    HAS_TOAST = False

log = logging.getLogger("server")

# ---------------- 日志 ----------------
def setup_logging(level: str):
    lvl = getattr(logging, level.upper(), logging.INFO)
    log_dir = get_config_dir() / "logs"
    log_dir.mkdir(exist_ok=True)
    fmt = "%(asctime)s [%(levelname)s] %(name)s: %(message)s"
    handlers = [logging.FileHandler(log_dir / "server.log", encoding="utf-8")]
    if not getattr(sys, 'frozen', False):
        handlers.append(logging.StreamHandler(sys.stdout))
    logging.basicConfig(level=lvl, format=fmt, handlers=handlers)

# ---------------- 验证码记录（线程安全） ----------------
class CodeRecord:
    def __init__(self):
        self.records = []  # [(time_str, code, sender), ...]
        self.lock = threading.Lock()

    def add(self, code: str, sender: str):
        t = time.strftime("%H:%M:%S")
        with self.lock:
            self.records.append((t, code, sender))
            if len(self.records) > 100:
                self.records.pop(0)

    def get_all(self):
        with self.lock:
            return list(self.records)

    def latest(self):
        with self.lock:
            return self.records[-1] if self.records else None

# ---------------- 验证码处理 ----------------
class CodeHandler:
    def __init__(self, cfg: dict, record: CodeRecord, on_code=None):
        self.cfg = cfg
        self.token = cfg.get("token", "")
        self.dedup_seconds = cfg.get("dedup_seconds", 3)
        self.rules = cfg.get("code_rules", [])
        self.notify = cfg.get("notify", True)
        self.record = record
        self.on_code = on_code  # 回调(code, sender)
        self._last_code = ""
        self._last_ts = 0.0
        from code_parser import extract_code
        self._extract = extract_code

    def handle(self, raw: str) -> bool:
        try:
            msg = json.loads(raw)
        except json.JSONDecodeError as e:
            log.warning("JSON 解析失败: %s | 原文: %s", e, raw[:120])
            return False

        if msg.get("token") != self.token:
            log.warning("token 不匹配，丢弃消息: %s", raw[:120])
            return False

        body = msg.get("body", "")
        sender = msg.get("sender", "")

        code = msg.get("code") or ""
        if not code:
            code = self._extract(body, self.rules) or ""

        if not code:
            log.info("未能提取验证码: sender=%s body=%s", sender, body[:80])
            return False

        return self.process_code(code, sender)

    def process_code(self, code: str, sender: str) -> bool:
        """处理一条验证码：去重 → 写剪贴板 → 记录 → 通知。

        手机短信与邮箱验证码共用此管道。
        """
        now = time.time()
        if code == self._last_code and (now - self._last_ts) < self.dedup_seconds:
            log.info("重复验证码已忽略: %s", code)
            return False

        try:
            pyperclip.copy(code)
        except Exception as e:
            log.error("写入剪贴板失败: %s", e)
            return False

        self._last_code = code
        self._last_ts = now
        self.record.add(code, sender)
        log.info("验证码已复制到剪贴板: %s (sender=%s)", code, sender)

        if self.on_code:
            try:
                self.on_code(code, sender)
            except Exception:
                pass

        if self.notify and HAS_TOAST:
            try:
                toast("验证码已接收", f"{code}\n来自: {sender}", icon="", duration="short")
            except Exception as e:
                log.debug("通知失败: %s", e)
        return True

# ---------------- 主入口 ----------------
def main():
    cfg = load_config()
    setup_logging(cfg.get("log_level", "INFO"))
    log.info("===== 验证码蓝牙中继 PC 端启动 =====")
    log.info("配置: token=%s, dedup=%ss",
             cfg.get("token", "")[:4] + "***", cfg.get("dedup_seconds"))
    log.info("使用 Winsock2 蓝牙 RFCOMM 直连模式（绕过 COM 端口）")

    stop_event = threading.Event()
    record = CodeRecord()
    handler = CodeHandler(cfg, record)
    bt_server = BtRfcommServer(handler, stop_event)
    bt_server.start()

    email_poller = EmailPoller(handler, record, stop_event, cfg.get("email", {}))
    email_poller.start()

    app = RelayApp(cfg, handler, bt_server, email_poller, record, stop_event)
    app.run()

    stop_event.set()
    log.info("服务已停止")

if __name__ == "__main__":
    main()