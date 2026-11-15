"""配置与路径管理（UI 与后端共用，兼容 PyInstaller 打包）。"""
import logging
import os
import secrets
import string
import sys
from pathlib import Path

import yaml

log = logging.getLogger("server")


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
    """返回用户配置目录：%APPDATA%\\SmsRelay。配置不暴露在 exe 同目录。"""
    appdata = os.environ.get("APPDATA") or os.environ.get("LOCALAPPDATA") or str(Path.home())
    d = Path(appdata) / "SmsRelay"
    d.mkdir(parents=True, exist_ok=True)
    return d


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