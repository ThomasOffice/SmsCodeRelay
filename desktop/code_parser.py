"""验证码提取器：从短信正文提取验证码。"""
import re
import logging

log = logging.getLogger("code_parser")


def extract_code(body: str, rules: list) -> str | None:
    """根据规则列表从短信正文提取验证码，返回首个命中规则的捕获组。

    Args:
        body: 短信正文
        rules: 配置中的 code_rules 列表，每项含 name/pattern/可选 flags
    Returns:
        验证码字符串或 None
    """
    for rule in rules:
        pattern = rule.get("pattern", "")
        flags = 0
        flag_str = rule.get("flags", "")
        if flag_str:
            for f in flag_str.split("|"):
                f = f.strip().upper()
                if f == "IGNORECASE":
                    flags |= re.IGNORECASE
                elif f == "MULTILINE":
                    flags |= re.MULTILINE
        m = re.search(pattern, body, flags)
        if m and m.lastindex is not None and m.group(1):
            code = m.group(1)
            log.debug("规则[%s] 命中: %s", rule.get("name", "?"), code)
            return code
    log.debug("未匹配任何规则: %s", body[:60])
    return None
