package com.relay.sms

object CodeExtractor {
    private val rules = listOf(
        Regex("""(?:验证码|动态码|校验码|认证码|验证|密码)[^\d]{0,20}(\d{4,8})"""),
        Regex("""(?:verification code|code|OTP|passcode)[^\d]{0,20}(\d{4,8})""", RegexOption.IGNORE_CASE),
        Regex("""\b(\d{4,8})\b""")
    )

    fun extract(body: String): String? {
        for (rule in rules) {
            val m = rule.find(body)
            if (m != null && m.groupValues.size > 1 && m.groupValues[1].isNotEmpty()) {
                return m.groupValues[1]
            }
        }
        return null
    }
}
