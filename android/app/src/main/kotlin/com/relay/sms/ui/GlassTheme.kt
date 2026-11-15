package com.relay.sms.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.relay.sms.R

/** Fluent Glass 设计令牌（与 PC 端 Web UI 同源）。 */
data class GlassColors(
    val bg: Color,
    val panel: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val glassBorderStrong: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val hover: Color,
    val active: Color,
    val track: Color,
    val inputBg: Color,
)

val DarkGlass = GlassColors(
    bg = Color(0xFF0B0D10),
    panel = Color(0xFF14181E),
    glass = Color(0x0BFFFFFF),   // 4.5% 白
    glassStrong = Color(0x12FFFFFF),
    glassBorder = Color(0x17FFFFFF),
    glassBorderStrong = Color(0x29FFFFFF),
    text = Color(0xFFF2F4F7),
    text2 = Color(0xFF9AA3AF),
    text3 = Color(0xFF5C6470),
    hover = Color(0x0FFFFFFF),
    active = Color(0x1AFFFFFF),
    track = Color(0x1AFFFFFF),
    inputBg = Color(0x0AFFFFFF),
)

val LightGlass = GlassColors(
    bg = Color(0xFFF4F6F8),
    panel = Color(0xFFFFFFFF),
    glass = Color(0xA8FFFFFF),   // 66% 白
    glassStrong = Color(0xD1FFFFFF),
    glassBorder = Color(0x100F172A),
    glassBorderStrong = Color(0x1F0F172A),
    text = Color(0xFF1A1D21),
    text2 = Color(0xFF5C6470),
    text3 = Color(0xFF98A0AB),
    hover = Color(0x0D0F172A),
    active = Color(0x140F172A),
    track = Color(0x1F0F172A),
    inputBg = Color(0x080F172A),
)

val Accent1 = Color(0xFF34D399)
val Accent2 = Color(0xFF2DD4BF)
val AccentInk = Color(0xFF04241A)
val Danger = Color(0xFFF87171)
val Warning = Color(0xFFFBBF24)
val Info = Color(0xFF60A5FA)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

object GlassDimens {
    val card: Dp = 16.dp
    val ctl: Dp = 10.dp
    val pill: Dp = 999.dp
    val gap: Dp = 16.dp
}

val InterFamily = FontFamily(
    Font(R.font.inter_var, weight = FontWeight.W400),
    Font(R.font.inter_var, weight = FontWeight.W500),
    Font(R.font.inter_var, weight = FontWeight.W600),
    Font(R.font.inter_var, weight = FontWeight.W700),
)
val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_var, weight = FontWeight.W400),
    Font(R.font.jetbrains_mono_var, weight = FontWeight.W500),
    Font(R.font.jetbrains_mono_var, weight = FontWeight.W600),
    Font(R.font.jetbrains_mono_var, weight = FontWeight.W700),
)

@Composable
fun GlassTheme(
    appearance: String = "system",
    content: @Composable () -> Unit,
) {
    val dark = when (appearance) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val glass = if (dark) DarkGlass else LightGlass

    // 让 M3 组件（ModalBottomSheet 等）也贴合 Glass 配色
    val m3 = if (dark) {
        darkColorScheme(
            primary = Accent1,
            onPrimary = AccentInk,
            primaryContainer = Accent1.copy(alpha = .16f),
            onPrimaryContainer = Accent1,
            surface = glass.bg,
            onSurface = glass.text,
            surfaceVariant = glass.panel,
            onSurfaceVariant = glass.text2,
            surfaceContainerHigh = glass.panel,
            surfaceContainerHighest = glass.panel,
            outline = glass.glassBorder,
            error = Danger,
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF059669),
            onPrimary = Color.White,
            primaryContainer = Accent1.copy(alpha = .18f),
            onPrimaryContainer = Color(0xFF047857),
            surface = glass.bg,
            onSurface = glass.text,
            surfaceVariant = glass.panel,
            onSurfaceVariant = glass.text2,
            surfaceContainerHigh = glass.panel,
            surfaceContainerHighest = glass.panel,
            outline = glass.glassBorder,
            error = Danger,
        )
    }

    CompositionLocalProvider(LocalGlass provides glass) {
        MaterialTheme(colorScheme = m3, content = content)
    }
}