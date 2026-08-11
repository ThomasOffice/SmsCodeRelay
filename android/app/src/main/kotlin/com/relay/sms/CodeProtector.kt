package com.relay.sms

import android.os.Build
import android.text.TextUtils

object CodeProtector {

    enum class Risk { NONE, SUSPECTED, HIGH }

    data class Result(val risk: Risk, val message: String)

    private val protectedBrands = setOf("huawei", "honor", "xiaomi", "redmi", "oppo", "vivo", "oneplus", "realme")

    fun isProtectedBrand(): Boolean {
        val brand = (Build.BRAND ?: "").lowercase()
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        return protectedBrands.any { brand.contains(it) || manufacturer.contains(it) }
    }

    fun getSettingsPath(): String {
        val brand = (Build.BRAND ?: "").lowercase()
        return when {
            brand.contains("huawei") || brand.contains("honor") ->
                "设置 → 安全 → 更多安全设置 → 验证码安全保护"
            brand.contains("xiaomi") || brand.contains("redmi") ->
                "设置 → 隐私保护 → 保护隐私 → 验证码安全保护"
            else ->
                "设置 → 安全/隐私 → 验证码保护（路径因品牌而异）"
        }
    }

    fun detect(body: String): Result {
        if (body.isBlank()) return Result(Risk.NONE, "")
        val hasKeyword = body.contains("验证码") || body.contains("动态码") ||
                body.contains("校验码") || body.contains("认证码") ||
                body.contains("code", ignoreCase = true) || body.contains("OTP", ignoreCase = true)
        if (!hasKeyword) return Result(Risk.NONE, "")

        val code = CodeExtractor.extract(body)
        if (!code.isNullOrEmpty()) return Result(Risk.NONE, "")

        if (body.contains("***") || body.contains("*")) {
            return Result(Risk.HIGH,
                "检测到验证码安全保护可能已开启！短信验证码被系统屏蔽为星号，无法提取。\n" +
                "请关闭：${getSettingsPath()}")
        }
        return Result(Risk.SUSPECTED,
            "短信含验证码关键词但未提取到数字，可能被保护功能拦截。\n" +
            "若持续失败请检查：${getSettingsPath()}")
    }
}
