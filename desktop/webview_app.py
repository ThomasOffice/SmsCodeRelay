"""现代 Web UI（pywebview + WebView2）+ 系统托盘。

取代旧的 CustomTkinter 界面：真实毛玻璃、渐变、动画、Inter/JetBrains Mono 字体。
后端逻辑（蓝牙监听、邮箱轮询、剪贴板管道）完全不变，本模块负责窗口与交互。

线程模型：
  - 主线程运行 webview.start()（WebView2 需要 STA 消息循环）
  - 蓝牙/邮箱后端线程不变；pystray 托盘在守护线程
  - JS→Python：window.pywebview.api.*（js_api 在独立线程执行，可阻塞）
  - Python→JS：1s 刷新线程 + 新验证码事件回调，经 evaluate_js 推送状态（加锁）
"""
import json
import logging
import os
import sys
import threading
import time
import winreg
from pathlib import Path

import webview
import pyperclip

from config import (get_base_dir, get_resource_dir, get_config_dir,
                    save_config, generate_token)
from email_poller import EMAIL_PRESETS

log = logging.getLogger("server")

# ---------------- 开机自启动（注册表 Run 键） ----------------
_AUTOSTART_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
_AUTOSTART_NAME = "SmsRelay"


def _autostart_command() -> str:
    if getattr(sys, "frozen", False):
        return f'"{sys.executable}"'
    pythonw = Path(sys.executable).with_name("pythonw.exe")
    exe = pythonw if pythonw.exists() else Path(sys.executable)
    script = Path(__file__).parent / "server.py"
    return f'"{exe}" "{script}"'


def get_autostart() -> bool:
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, _AUTOSTART_KEY) as k:
            winreg.QueryValueEx(k, _AUTOSTART_NAME)
            return True
    except FileNotFoundError:
        return False
    except Exception:
        return False


def set_autostart(enabled: bool) -> bool:
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, _AUTOSTART_KEY, 0,
                            winreg.KEY_SET_VALUE) as k:
            if enabled:
                winreg.SetValueEx(k, _AUTOSTART_NAME, 0, winreg.REG_SZ,
                                  _autostart_command())
            else:
                try:
                    winreg.DeleteValue(k, _AUTOSTART_NAME)
                except FileNotFoundError:
                    pass
        return True
    except Exception as e:
        log.error("设置开机自启动失败: %s", e)
        return False

try:
    import pystray
    from PIL import Image, ImageDraw
    HAS_TRAY = True
except Exception:
    HAS_TRAY = False


# ---------------- 图标 ----------------
def _load_app_icon_png() -> "Image.Image":
    """加载应用图标 PNG（托盘用）。优先打包内嵌，其次脚本目录。"""
    for d in (get_resource_dir(), get_base_dir()):
        p = d / "app_icon.png"
        if p.exists():
            try:
                return Image.open(p).convert("RGBA").resize((64, 64), Image.LANCZOS)
            except Exception:
                pass
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([8, 12, 56, 52], radius=8, fill=(30, 30, 30), outline=(80, 200, 120), width=2)
    d.text((18, 24), "SMS", fill=(80, 200, 120))
    return img


# ---------------- JS API（暴露给前端 window.pywebview.api） ----------------
class Api:
    def __init__(self, app: "RelayApp"):
        self._app = app

    def get_state(self):
        return self._app.get_state()

    def copy(self, text):
        try:
            pyperclip.copy(text or "")
            return True
        except Exception as e:
            log.error("写入剪贴板失败: %s", e)
            return False

    def gen_token(self):
        app = self._app
        token = generate_token(24)
        app.cfg["token"] = token
        app.handler.token = token
        try:
            save_config(app.cfg)
        except Exception as e:
            log.error("保存配置失败: %s", e)
        log.info("Token 已更新并保存")
        return token

    def save_email(self, cfg):
        app = self._app
        try:
            interval = int(cfg.get("poll_interval", 30))
            port = int(cfg.get("imap_port", 993))
        except Exception:
            return {"ok": False, "error": "端口和轮询间隔必须为整数"}
        email_cfg = {
            "enabled": bool(cfg.get("enabled", False)),
            "imap_host": cfg.get("imap_host") or "imap.qq.com",
            "imap_port": port,
            "username": cfg.get("username", ""),
            "password": cfg.get("password", ""),
            "poll_interval": interval,
            "since_days": (app.cfg.get("email", {}) or {}).get("since_days", 1),
            "mark_seen": bool(cfg.get("mark_seen", True)),
            "strict": bool(cfg.get("strict", True)),
        }
        app.cfg["email"] = email_cfg
        try:
            save_config(app.cfg)
        except Exception as e:
            log.error("保存邮箱配置失败: %s", e)
            return {"ok": False, "error": str(e)}
        app.email_poller.apply_config(email_cfg)
        log.info("邮箱配置已保存: enabled=%s host=%s", email_cfg["enabled"], email_cfg["imap_host"])
        return {"ok": True}

    def test_email(self, cfg):
        import imaplib
        try:
            host = cfg.get("imap_host", "imap.qq.com")
            port = int(cfg.get("imap_port", 993))
            conn = imaplib.IMAP4_SSL(host, port, timeout=15)
            conn.login(cfg.get("username", ""), cfg.get("password", ""))
            conn.logout()
            return {"ok": True}
        except Exception as e:
            return {"ok": False, "error": str(e)}

    def set_notify(self, on):
        app = self._app
        app.cfg["notify"] = bool(on)
        app.handler.notify = bool(on)
        try:
            save_config(app.cfg)
        except Exception as e:
            log.error("保存配置失败: %s", e)

    def set_appearance(self, mode):
        app = self._app
        if mode not in ("dark", "light", "system"):
            return
        app.cfg["appearance"] = mode
        try:
            save_config(app.cfg)
        except Exception as e:
            log.error("保存配置失败: %s", e)

    def set_close_to_tray(self, on):
        app = self._app
        app.cfg["close_to_tray"] = bool(on)
        try:
            save_config(app.cfg)
        except Exception as e:
            log.error("保存配置失败: %s", e)

    def set_start_to_tray(self, on):
        app = self._app
        app.cfg["start_to_tray"] = bool(on)
        try:
            save_config(app.cfg)
        except Exception as e:
            log.error("保存配置失败: %s", e)

    def set_autostart(self, on):
        app = self._app
        ok = set_autostart(bool(on))
        app.cfg["autostart"] = bool(on)
        try:
            save_config(app.cfg)
        except Exception as e:
            log.error("保存配置失败: %s", e)
        return ok

    def quit_app(self):
        self._app.quit()

    def force_reconnect(self):
        log.info("用户触发强制重连")
        self._app.bt_server.force_reconnect()

    def open_config_dir(self):
        os.startfile(str(get_config_dir()))

    def hide_window(self):
        app = self._app
        if app.cfg.get("close_to_tray", True):
            try:
                app.window.hide()
            except Exception:
                pass
        else:
            app.quit()

    def minimize_window(self):
        try:
            self._app.window.minimize()
        except Exception:
            pass


# ---------------- 主应用 ----------------
class RelayApp:
    def __init__(self, cfg: dict, handler, bt_server, email_poller, record,
                 stop_event: threading.Event):
        self.cfg = cfg
        self.handler = handler
        self.bt_server = bt_server
        self.email_poller = email_poller
        self.record = record
        self.stop_event = stop_event

        self.window = None
        self.api = Api(self)
        self._push_lock = threading.Lock()
        self._refresh_stop = threading.Event()
        self._tray_icon = None

        # 新验证码即时推送（后端线程回调）
        self.handler.on_code = lambda code, sender: self.push_state()

    # ---------------- 状态 ----------------
    def get_state(self) -> dict:
        latest = self.record.latest()
        return {
            "bt": {
                "connected": bool(self.bt_server.connected),
                "listening": bool(self.bt_server.listening),
            },
            "email": {
                "enabled": bool(self.email_poller.enabled),
                "error": self.email_poller.last_error or "",
            },
            "latest": ({"code": latest[1], "sender": latest[2], "time": latest[0]}
                       if latest else None),
            "history": [{"time": t, "code": c, "sender": s}
                        for (t, c, s) in self.record.get_all()],
            "token": self.cfg.get("token", ""),
            "notify": self.cfg.get("notify", True),
            "email_cfg": self.cfg.get("email", {}) or {},
            "presets": {k: [v[0], v[1]] for k, v in EMAIL_PRESETS.items()},
            "appearance": self.cfg.get("appearance", "dark"),
            "close_to_tray": self.cfg.get("close_to_tray", True),
            "start_to_tray": self.cfg.get("start_to_tray", False),
            "autostart": get_autostart(),
        }

    def push_state(self):
        if not self.window:
            return
        try:
            state = self.get_state()
            js = "window.__setState(%s)" % json.dumps(state, ensure_ascii=False)
            with self._push_lock:
                self.window.evaluate_js(js)
        except Exception as e:
            log.debug("push_state 失败: %s", e)

    def _refresh_loop(self):
        while not self._refresh_stop.is_set() and not self.stop_event.is_set():
            self.push_state()
            time.sleep(1)

    # ---------------- 窗口 / 托盘 ----------------
    def show_window(self):
        try:
            if self.window:
                self.window.show()
                self.window.restore()
        except Exception as e:
            log.debug("show_window: %s", e)

    def quit(self):
        log.info("用户退出应用")
        self.stop_event.set()
        self._refresh_stop.set()
        try:
            if self._tray_icon:
                self._tray_icon.stop()
        except Exception:
            pass
        try:
            if self.window:
                self.window.destroy()
        except Exception:
            pass

    def _on_started(self):
        t = threading.Thread(target=self._refresh_loop, daemon=True)
        t.start()

    def _run_tray(self):
        if not HAS_TRAY:
            log.warning("pystray 不可用，仅 UI 模式")
            return

        def on_show(icon, item):
            self.show_window()

        def on_quit(icon, item):
            log.info("用户从托盘退出")
            self.stop_event.set()
            self._refresh_stop.set()
            try:
                if self.window:
                    self.window.destroy()
            except Exception:
                pass
            icon.stop()

        menu = pystray.Menu(
            pystray.MenuItem("显示主窗口", on_show, default=True),
            pystray.MenuItem("退出", on_quit),
        )
        self._tray_icon = pystray.Icon("smsrelay", _load_app_icon_png(), "验证码中继", menu)
        self._tray_icon.run()

    def run(self):
        html = get_resource_dir() / "web" / "index.html"
        self.window = webview.create_window(
            "验证码中继",
            url=str(html),
            js_api=self.api,
            width=920, height=620,
            min_size=(820, 560),
            frameless=True,
            easy_drag=False,
            background_color="#0b0d10",
            hidden=self.cfg.get("start_to_tray", False),
            resizable=True,
            text_select=False,
        )

        # 启动托盘子线程
        self.tray_thread = threading.Thread(target=self._run_tray, daemon=True)
        self.tray_thread.start()

        webview.start(self._on_started)