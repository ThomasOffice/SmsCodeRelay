package com.relay.sms.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/* ---------- 玻璃卡片 ---------- */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val g = LocalGlass.current
    Column(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(GlassDimens.card),
                spotColor = Color.Black.copy(alpha = .30f),
            )
            .clip(RoundedCornerShape(GlassDimens.card))
            .background(g.glass)
            .border(1.dp, g.glassBorder, RoundedCornerShape(GlassDimens.card))
            .padding(18.dp),
        content = content,
    )
}

/* ---------- 小节标题 ---------- */
@Composable
fun SectionCaption(text: String, icon: ImageVector? = null) {
    val g = LocalGlass.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = g.text2, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = g.text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            lineHeight = 12.sp)
    }
}

/* ---------- 渐变主按钮 ---------- */
@Composable
fun GradientButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    copied: Boolean = false,
) {
    val grad = Brush.linearGradient(listOf(Accent1, Accent2))
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(GlassDimens.ctl))
            .background(grad)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (copied) GlassIcons.Check else icon, null,
            tint = AccentInk, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (copied) "已复制" else text, color = AccentInk,
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

/* ---------- 次要描边按钮 ---------- */
@Composable
fun GhostButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val g = LocalGlass.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(GlassDimens.ctl))
            .background(g.glass)
            .border(1.dp, g.glassBorder, RoundedCornerShape(GlassDimens.ctl))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) g.text2 else g.text3, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (enabled) g.text2 else g.text3, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}

/* ---------- 状态球（live 时脉冲） ---------- */
enum class OrbState { Live, Idle, Warn, Err, Muted }

@Composable
fun StatusOrb(state: OrbState, modifier: Modifier = Modifier) {
    val color = when (state) {
        OrbState.Live -> Accent1
        OrbState.Idle -> Info
        OrbState.Warn -> Warning
        OrbState.Err -> Danger
        OrbState.Muted -> LocalGlass.current.text3
    }
    val pulse = if (state == OrbState.Live) {
        val inf = rememberInfiniteTransition(label = "orb")
        val r by inf.animateFloat(
            initialValue = 5f, targetValue = 14f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing), RepeatMode.Restart),
            label = "r",
        )
        val a by inf.animateFloat(
            initialValue = .45f, targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing), RepeatMode.Restart),
            label = "a",
        )
        r to a
    } else 0f to 0f

    Box(modifier.size(9.dp).drawBehind {
        if (pulse.second > 0f) drawCircle(color.copy(alpha = pulse.second), radius = pulse.first)
    }.clip(CircleShape).background(color))
}

/* ---------- 开关 ---------- */
@Composable
fun GlassSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val g = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = g.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Accent1,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = g.track,
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/* ---------- 输入框 ---------- */
@Composable
fun glassTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent1,
    unfocusedBorderColor = LocalGlass.current.glassBorder,
    focusedContainerColor = LocalGlass.current.inputBg,
    unfocusedContainerColor = LocalGlass.current.inputBg,
    focusedTextColor = LocalGlass.current.text,
    unfocusedTextColor = LocalGlass.current.text,
    focusedLabelColor = Accent1,
    unfocusedLabelColor = LocalGlass.current.text2,
    cursorColor = Accent1,
)

/* ---------- 分段选择器 ---------- */
@Composable
fun GlassSegmented(
    options: List<Pair<String, ImageVector?>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val g = LocalGlass.current
    Row(
        modifier
            .clip(RoundedCornerShape(GlassDimens.ctl))
            .background(g.inputBg)
            .border(1.dp, g.glassBorder, RoundedCornerShape(GlassDimens.ctl))
            .padding(4.dp),
    ) {
        options.forEach { (value, icon) ->
            val active = value == selected
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (active) Brush.linearGradient(listOf(Accent1, Accent2))
                    else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(icon, null, tint = if (active) AccentInk else g.text2, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(value, color = if (active) AccentInk else g.text2, fontSize = 12.5.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

/* ---------- Toast ---------- */
class ToastState {
    var text by mutableStateOf<String?>(null)
        private set

    fun show(msg: String) {
        text = msg
    }

    fun clear() {
        text = null
    }
}

@Composable
fun rememberToastState(): ToastState = remember { ToastState() }

@Composable
fun GlassToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    state.text?.let { msg ->
        Box(modifier) {
            Row(
                Modifier
                    .align(Alignment.Center)
                    .shadow(8.dp, RoundedCornerShape(GlassDimens.pill))
                    .clip(RoundedCornerShape(GlassDimens.pill))
                    .background(g.panel)
                    .border(1.dp, Accent1.copy(alpha = .4f), RoundedCornerShape(GlassDimens.pill))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GlassIcons.Check, null, tint = Accent1, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(msg, color = g.text, fontSize = 13.sp)
            }
        }
        LaunchedEffect(msg) {
            delay(1800)
            state.clear()
        }
    }
}