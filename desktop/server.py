"""验证码蓝牙中继 - PC 端服务

从蓝牙传入 COM 端口读取 Android App 推送的验证码消息，
校验 token、去重后写入系统剪贴板，并弹出 Windows 通知。
双击运行后隐藏到系统托盘，双击托盘图标弹出轻量状态窗口。
"""
import json
import logging
import os
import secrets
import string
import sys
import threading
import time
import tkinter as tk
from tkinter import messagebox, ttk
from pathlib import Path

import serial
import serial.tools.list_ports
import yaml
import pyperclip

try:
    from win11toast import toast
    HAS_TOAST = True
except Exception:
    HAS_TOAST = False

try:
    import pystray
    from PIL import Image, ImageDraw
    HAS_TRAY = True
except Exception:
    HAS_TRAY = False

# ---------------- 路径（兼容 PyInstaller 打包） ----------------
def get_base_dir() -> Path:
    """返回程序所在目录。打包后用 exe 路径，开发时用脚本路径。"""
    if getattr(sys, 'frozen', False):
        return Path(sys.executable).parent
    return Path(__file__).parent

def get_resource_dir() -> Path:
    """返回内嵌资源目录（PyInstaller 解包后的 _MEIPASS），开发时用脚本目录。"""
    if getattr(sys, '_MEIPASS', None):
        return Path(sys._MEIPASS)
    return Path(__file__).parent

def get_config_dir() -> Path:
    """返回用户配置目录：%APPDATA%\\SmsRelay。配置不再暴露在 exe 同目录。"""
    appdata = os.environ.get("APPDATA") or os.environ.get("LOCALAPPDATA") or str(Path.home())
    d = Path(appdata) / "SmsRelay"
    d.mkdir(parents=True, exist_ok=True)
    return d

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

log = logging.getLogger("server")

# ---------------- 配置 ----------------
def load_config() -> dict:
    """加载用户配置。首次运行从内嵌默认配置复制到 %APPDATA%\\SmsRelay。"""
    user_cfg = get_config_dir() / "config.yaml"
    if not user_cfg.exists():
        # 从内嵌资源提取默认配置
        default_cfg = get_resource_dir() / "config.yaml"
        if default_cfg.exists():
            import shutil
            shutil.copy2(default_cfg, user_cfg)
            log.info("已从默认配置初始化: %s", user_cfg)
        else:
            log.error("默认配置不存在: %s", default_cfg)
            sys.exit(1)
    with open(user_cfg, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)

def save_config(cfg: dict):
    """保存配置到 %APPDATA%\\SmsRelay\\config.yaml。"""
    cfg_path = get_config_dir() / "config.yaml"
    with open(cfg_path, "w", encoding="utf-8") as f:
        yaml.dump(cfg, f, allow_unicode=True, default_flow_style=False, sort_keys=False)
    log.info("配置已保存: %s", cfg_path)

def generate_token(length: int = 24) -> str:
    """生成随机 token（字母+数字）。"""
    alphabet = string.ascii_letters + string.digits
    return ''.join(secrets.choice(alphabet) for _ in range(length))

# ---------------- COM 端口探测 ----------------
def detect_bluetooth_com(baudrate: int = 9600) -> str | None:
    """自动探测可打开的蓝牙传入 COM 端口。

    Windows 蓝牙 SPP 端口通过 hwid 区分：
      - 传入端口(可监听)：hwid 中设备地址段为 000000000000（全0）
      - 传出端口(PC主动连手机)：hwid 含真实 12 位设备地址
    幽灵端口(已注册但不可用)会被实测打开排除。
    """
    incoming: list = []
    outgoing: list = []
    for p in serial.tools.list_ports.comports():
        hwid = p.hwid or ""
        desc = (p.description or "").lower()
        is_bt = ("BTHENUM" in hwid or "bluetooth" in hwid.lower()
                 or "蓝牙" in desc or "bluetooth" in desc)
        if not is_bt:
            continue
        if "000000000000" in hwid:
            incoming.append(p)
        else:
            outgoing.append(p)

    log.info("蓝牙传入端口候选: %s", [p.device for p in incoming])
    if outgoing:
        log.info("蓝牙传出端口(将忽略): %s", [p.device for p in outgoing])

    for p in incoming:
        try:
            s = serial.Serial(p.device, baudrate, timeout=1)
            s.close()
            log.info("自动选中可用传入端口: %s", p.device)
            return p.device
        except Exception as e:
            log.warning("传入端口 %s 打不开(幽灵端口?)，跳过: %s", p.device, e)

    log.warning("没有可打开的蓝牙传入端口")
    return None

def list_all_coms() -> list[str]:
    return [p.device for p in serial.tools.list_ports.comports()]

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

# ---------------- 串口读取线程 ----------------
class SerialReader(threading.Thread):
    def __init__(self, cfg: dict, handler: CodeHandler, stop_event: threading.Event):
        super().__init__(daemon=True)
        self.cfg = cfg
        self.handler = handler
        self.stop_event = stop_event
        self.ser: serial.Serial | None = None
        self.com_port: str = ""
        self.baudrate = cfg.get("baudrate", 9600)

    def open_port(self) -> bool:
        port = self.cfg.get("com_port") or ""
        if port:
            ok = self._try_open(port, "(配置)")
            if ok:
                return True
            log.warning("配置的 COM 端口 %s 打不开，回退自动探测", port)
        port = detect_bluetooth_com(self.baudrate)
        if not port:
            return False
        return self._try_open(port, "(自动)")

    def _try_open(self, port: str, tag: str) -> bool:
        """尝试独占打开 COM 端口。pyserial 在 Windows 上默认独占（dwShareMode=0），
        一旦打开其他软件无法抢夺。"""
        try:
            self.ser = serial.Serial(port, self.baudrate, timeout=1)
            self.com_port = port
            log.info("已独占打开 COM 端口%s: %s @ %d baud", tag, port, self.baudrate)
            return True
        except Exception as e:
            err_str = str(e)
            if "Access is denied" in err_str or "拒绝访问" in err_str:
                log.warning("COM %s 被其他程序占用: %s（请关闭串口调试工具等）", port, e)
            elif "cannot find" in err_str or "找不到" in err_str:
                log.warning("COM %s 是幽灵端口（可能被其他软件扫描后损坏）: %s", port, e)
            else:
                log.error("打开 COM 端口 %s 失败: %s", port, e)
            self.ser = None
            return False

    def run(self):
        buf = ""
        fail_count = 0
        while not self.stop_event.is_set():
            if self.ser is None:
                # 重试抢回端口：前5次快速重试(1秒)，之后降频(5秒)
                delay = 1 if fail_count < 5 else 5
                if not self.open_port():
                    fail_count += 1
                    if fail_count == 1:
                        log.warning("端口不可用，正在尝试抢回（其他软件可能占用了端口）...")
                    time.sleep(delay)
                    continue
                fail_count = 0
            try:
                # 持久保持端口打开：readline 超时返回空是正常的，
                # 表示当前没有 Android 连接，但 Windows SPP 仍在监听。
                # 绝不因静默而关闭端口——关闭的瞬间其他软件就会抢走它。
                chunk = self.ser.readline()
                if chunk:
                    try:
                        line = chunk.decode("utf-8", errors="replace")
                    except Exception:
                        line = chunk.decode("latin-1", errors="replace")
                    buf += line
                    while "\n" in buf:
                        msg, buf = buf.split("\n", 1)
                        msg = msg.strip()
                        if msg:
                            log.debug("收到: %s", msg[:120])
                            self.handler.handle(msg)
            except serial.SerialException as e:
                # 仅在真正异常时才关闭重连（不是静默超时）
                log.warning("串口异常，将重连: %s", e)
                try:
                    if self.ser:
                        self.ser.close()
                except Exception:
                    pass
                self.ser = None
                time.sleep(2)
            except Exception as e:
                log.error("读取异常: %s", e)
                time.sleep(1)
        if self.ser:
            try:
                self.ser.close()
            except Exception:
                pass

# ---------------- 托盘图标 ----------------
def _load_app_icon_png() -> "Image.Image":
    """加载应用图标 PNG（托盘和 UI 共用）。优先用打包内嵌，其次脚本目录。"""
    for d in (get_resource_dir(), get_base_dir()):
        p = d / "app_icon.png"
        if p.exists():
            try:
                return Image.open(p).convert("RGBA").resize((64, 64), Image.LANCZOS)
            except Exception:
                pass
    # 兜底：绘制简易图标
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([8, 12, 56, 52], radius=8, fill=(30, 30, 30), outline=(80, 200, 120), width=2)
    d.text((18, 24), "SMS", fill=(80, 200, 120))
    return img

def make_icon_image() -> "Image.Image":
    return _load_app_icon_png()

# ---------------- 主应用（tkinter UI + 托盘） ----------------
class TrayApp:
    def __init__(self, cfg: dict, handler: CodeHandler, reader: SerialReader, record: CodeRecord, stop_event: threading.Event):
        self.cfg = cfg
        self.handler = handler
        self.reader = reader
        self.record = record
        self.stop_event = stop_event

        self.root = tk.Tk()
        self.root.title("验证码蓝牙中继")
        self.root.geometry("500x460")
        self.root.withdraw()  # 启动即隐藏
        self.root.protocol("WM_DELETE_WINDOW", self.hide_window)
        self.root.after(1000, self._refresh_loop)

        # 设置窗口图标
        try:
            from PIL import ImageTk
            icon_img = _load_app_icon_png().resize((48, 48), Image.LANCZOS)
            self._icon_photo = ImageTk.PhotoImage(icon_img)
            self.root.iconphoto(True, self._icon_photo)
        except Exception:
            pass

        self._build_ui()

        # 启动托盘子线程
        self.tray_thread = threading.Thread(target=self._run_tray, daemon=True)
        self.tray_thread.start()

    def _build_ui(self):
        root = self.root
        root.configure(bg="#f0f0f0")

        top = tk.Frame(root, bg="#2e7d32", height=56)
        top.pack(fill=tk.X)
        tk.Label(top, text="验证码蓝牙中继", fg="white", bg="#2e7d32",
                 font=("Microsoft YaHei UI", 14, "bold")).pack(pady=10)

        info = tk.Frame(root, bg="#f0f0f0")
        info.pack(fill=tk.X, padx=12, pady=8)
        self.lbl_status = tk.Label(info, text="状态: 启动中…", anchor="w",
                                   bg="#f0f0f0", font=("Microsoft YaHei UI", 10))
        self.lbl_status.pack(anchor="w")
        self.lbl_port = tk.Label(info, text="端口: -", anchor="w",
                                 bg="#f0f0f0", font=("Microsoft YaHei UI", 10))
        self.lbl_port.pack(anchor="w")

        # Token 显示 + 生成新 Token 按钮（同一行）
        token_row = tk.Frame(info, bg="#f0f0f0")
        token_row.pack(fill=tk.X, pady=2)
        tk.Label(token_row, text="Token: ", bg="#f0f0f0",
                 font=("Microsoft YaHei UI", 10)).pack(side=tk.LEFT)
        self.lbl_token = tk.Label(token_row, text=self.cfg.get("token", ""),
                                  bg="#f0f0f0", fg="#1565C0",
                                  font=("Consolas", 10, "bold"), anchor="w",
                                  cursor="hand2")
        self.lbl_token.pack(side=tk.LEFT, fill=tk.X, expand=True)
        self.lbl_token.bind("<Button-1>", lambda e: self._copy_text(
            self.cfg.get("token", ""), "Token"))
        self.lbl_token.bind("<Enter>",
            lambda e: self.lbl_token.config(fg="#0D47A1"))
        self.lbl_token.bind("<Leave>",
            lambda e: self.lbl_token.config(fg="#1565C0"))
        tk.Button(token_row, text="生成新 Token", command=self._gen_token,
                  height=1).pack(side=tk.RIGHT, padx=(4, 0))

        self.lbl_latest = tk.Label(info, text="最新验证码: -", anchor="w",
                                   bg="#f0f0f0", fg="#1565C0",
                                   font=("Microsoft YaHei UI", 10, "bold"),
                                   cursor="hand2")
        self.lbl_latest.pack(anchor="w")
        self.lbl_latest.bind("<Button-1>", self._copy_latest_label)

        tk.Label(root, text="最近接收记录：", bg="#f0f0f0", anchor="w",
                 font=("Microsoft YaHei UI", 10)).pack(fill=tk.X, padx=12)

        cols = ("time", "code", "sender")
        self.tree = ttk.Treeview(root, columns=cols, show="headings", height=10)
        self.tree.heading("time", text="时间")
        self.tree.heading("code", text="验证码")
        self.tree.heading("sender", text="发送方")
        self.tree.column("time", width=80, anchor=tk.CENTER)
        self.tree.column("code", width=120, anchor=tk.CENTER)
        self.tree.column("sender", width=240, anchor=tk.W)
        self.tree.pack(fill=tk.BOTH, expand=True, padx=12, pady=6)
        self.tree.bind("<<TreeviewSelect>>", self._copy_tree_selected)

        btns = tk.Frame(root, bg="#f0f0f0")
        btns.pack(fill=tk.X, padx=12, pady=8)
        tk.Button(btns, text="复制最新验证码", command=self._copy_latest).pack(side=tk.LEFT, padx=4)
        tk.Button(btns, text="打开配置目录", command=self._open_dir).pack(side=tk.LEFT, padx=4)
        tk.Button(btns, text="强制重连", command=self._force_reconnect).pack(side=tk.LEFT, padx=4)
        tk.Button(btns, text="隐藏到托盘", command=self.hide_window).pack(side=tk.RIGHT, padx=4)

    def _refresh_loop(self):
        """每秒刷新 UI 状态。"""
        if self.stop_event.is_set():
            return
        try:
            if self.reader.ser:
                st = "已连接"
                self.lbl_status.config(text=f"状态: {st}", fg="#2e7d32")
            elif self.reader.com_port:
                st = "端口被占用，正在抢回..."
                self.lbl_status.config(text=f"状态: {st}", fg="#c62828")
            else:
                st = "等待蓝牙传入端口..."
                self.lbl_status.config(text=f"状态: {st}", fg="#f57c00")
            self.lbl_port.config(text=f"端口: {self.reader.com_port or '未连接'}")
            latest = self.record.latest()
            if latest:
                self.lbl_latest.config(text=f"最新验证码: {latest[1]}  (来自 {latest[2]})")
            # 刷新列表
            for i in self.tree.get_children():
                self.tree.delete(i)
            for t, code, sender in reversed(self.record.get_all()):
                self.tree.insert("", tk.END, values=(t, code, sender))
        except Exception:
            pass
        self.root.after(1000, self._refresh_loop)

    def _copy_latest(self):
        latest = self.record.latest()
        if latest:
            pyperclip.copy(latest[1])

    def _force_reconnect(self):
        """强制关闭并重新打开 COM 端口（当被其他软件抢走时手动恢复）。"""
        log.info("用户触发强制重连")
        self._flash_status("正在强制重连...")
        try:
            if self.reader.ser:
                self.reader.ser.close()
        except Exception:
            pass
        self.reader.ser = None
        def _do_reconnect():
            time.sleep(0.5)
            self.reader.open_port()
        threading.Thread(target=_do_reconnect, daemon=True).start()

    def _copy_text(self, text: str, label: str = "内容"):
        """复制文本到剪贴板并闪烁提示。"""
        if not text:
            return
        pyperclip.copy(text)
        self._flash_status(f"已复制{label}")

    def _copy_latest_label(self, event=None):
        """点击最新验证码标签复制。"""
        latest = self.record.latest()
        if latest:
            pyperclip.copy(latest[1])
            self._flash_status(f"已复制验证码 {latest[1]}")

    def _copy_tree_selected(self, event=None):
        """点击列表行复制验证码。"""
        sel = self.tree.selection()
        if not sel:
            return
        vals = self.tree.item(sel[0], "values")
        if vals and len(vals) >= 2:
            pyperclip.copy(vals[1])
            self._flash_status(f"已复制验证码 {vals[1]}")

    def _flash_status(self, msg: str):
        """在状态栏短暂显示提示，1.5 秒后恢复。"""
        prev = self.lbl_status.cget("text")
        self.lbl_status.config(text=msg, fg="#2e7d32")
        self.root.after(1500, lambda: self.lbl_status.config(text=prev, fg="black"))

    def _gen_token(self):
        """生成新随机 token，更新运行时配置并持久化。"""
        new_token = generate_token(24)
        if not messagebox.askyesno("生成新 Token",
                f"新 Token 已生成：\n\n{new_token}\n\n"
                f"点击「是」立即应用并保存。\n"
                f"注意：需同步更新手机 App 中的 Token，否则转发将失败！"):
            return
        self.cfg["token"] = new_token
        self.handler.token = new_token
        try:
            save_config(self.cfg)
        except Exception as e:
            log.error("保存配置失败: %s", e)
            messagebox.showerror("错误", f"保存配置失败：{e}")
            return
        self.lbl_token.config(text=new_token)  # 同步刷新显示
        pyperclip.copy(new_token)
        messagebox.showinfo("Token 已更新",
                f"新 Token：\n{new_token}\n\n已复制到剪贴板。\n请尽快在手机 App 中更新相同 Token。")
        log.info("Token 已更新并保存")

    def _open_dir(self):
        os.startfile(str(get_config_dir()))

    def show_window(self, icon=None, item=None):
        self.root.after(0, lambda: (self.root.deiconify(), self.root.lift(), self.root.focus_force()))

    def hide_window(self):
        self.root.withdraw()

    def _run_tray(self):
        if not HAS_TRAY:
            log.warning("pystray 不可用，仅 UI 模式")
            return

        def on_quit(icon, item):
            log.info("用户从托盘退出")
            self.stop_event.set()
            icon.stop()
            self.root.after(0, self.root.destroy)

        def on_show(icon, item):
            self.show_window()

        menu = pystray.Menu(
            pystray.MenuItem("显示主窗口", on_show, default=True),
            pystray.MenuItem("退出", on_quit),
        )
        icon = pystray.Icon("smsrelay", make_icon_image(), "验证码蓝牙中继", menu)
        icon.run()

    def run(self):
        self.root.mainloop()

# ---------------- 主入口 ----------------
def main():
    cfg = load_config()
    setup_logging(cfg.get("log_level", "INFO"))
    log.info("===== 验证码蓝牙中继 PC 端启动 =====")
    log.info("配置: token=%s, baud=%s, dedup=%ss",
             cfg.get("token", "")[:4] + "***", cfg.get("baudrate"), cfg.get("dedup_seconds"))

    stop_event = threading.Event()
    record = CodeRecord()
    handler = CodeHandler(cfg, record)
    reader = SerialReader(cfg, handler, stop_event)
    reader.start()

    app = TrayApp(cfg, handler, reader, record, stop_event)
    app.run()

    stop_event.set()
    log.info("服务已停止")

if __name__ == "__main__":
    main()
