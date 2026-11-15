package com.relay.sms.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.relay.sms.MainViewModel
import com.relay.sms.RelayLog
import com.relay.sms.RelayUiState

@Composable
fun GlassDashboard(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val received by RelayLog.received.collectAsStateWithLifecycle()
    val warning by RelayLog.warning.collectAsStateWithLifecycle()
    val toast = rememberToastState()
    var settingsOpen by remember { mutableStateOf(false) }
    val g = LocalGlass.current

    Box(Modifier.fillMaxSize().background(g.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            GlassTitleBar(
                appearance = state.appearance,
                onCycleAppearance = {
                    val next = when (state.appearance) {
                        "dark" -> "light"
                        "light" -> "system"
                        else -> "dark"
                    }
                    vm.setAppearance(next)
                },
                onSettings = { settingsOpen = true },
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                // 状态行
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusOrb(
                        when {
                            !state.bluetoothEnabled -> OrbState.Err
                            state.serviceRunning -> OrbState.Live
                            else -> OrbState.Muted
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        state.statusMessage,
                        color = g.text2, fontSize = 12.sp,
                        maxLines = 1, modifier = Modifier.weight(1f),
                    )
                }

                warning?.let { msg ->
                    WarningCard(msg, onDismiss = { RelayLog.dismissWarning() })
                    Spacer(Modifier.height(8.dp))
                }

                // Hero + 历史
                Column(Modifier.weight(1f)) {
                    HeroCard(
                        latest = received.lastOrNull(),
                        btConnected = state.serviceRunning,
                        bluetoothEnabled = state.bluetoothEnabled,
                        onCopy = { code ->
                            vm.copyToClipboard(code)
                            toast.show("已复制 $code")
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    HistoryCard(
                        received = received,
                        onCopy = { code ->
                            vm.copyToClipboard(code)
                            toast.show("已复制 $code")
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                // 操作行
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GradientButton(
                        text = if (state.serviceRunning) "停止监听" else "启动监听",
                        icon = if (state.serviceRunning) GlassIcons.Stop else GlassIcons.Play,
                        onClick = { if (state.serviceRunning) vm.stopService() else vm.startService() },
                        modifier = Modifier.weight(1f),
                    )
                    GhostButton(
                        text = if (state.testing) "测试中…" else "测试转发",
                        icon = GlassIcons.Send,
                        onClick = vm::testForward,
                        enabled = !state.testing,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 状态条
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, g.glassBorder)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("SmsRelay · Fluent Glass", color = g.text3, fontSize = 10.sp,
                    fontFamily = MonoFamily)
            }
        }

        GlassToastHost(
            toast,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .fillMaxWidth(),
        )
    }

    if (settingsOpen) {
        GlassSettingsSheet(vm = vm, onDismiss = { settingsOpen = false })
    }
}

/* ---------- 标题栏 ---------- */
@Composable
private fun GlassTitleBar(
    appearance: String,
    onCycleAppearance: () -> Unit,
    onSettings: () -> Unit,
) {
    val g = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark(22.dp)
        Spacer(Modifier.width(10.dp))
        Text("验证码中继", color = g.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(
            "v2.0", color = g.text2, fontSize = 10.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, g.glassBorder, RoundedCornerShape(999.dp))
                .background(g.glass)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onCycleAppearance) {
            Icon(
                when (appearance) {
                    "dark" -> GlassIcons.Sun
                    "light" -> GlassIcons.Moon
                    else -> GlassIcons.Monitor
                },
                contentDescription = "切换外观",
                tint = g.text2,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(GlassIcons.Settings, contentDescription = "设置", tint = g.text2)
        }
    }
}

/* ---------- App 标记（squircle 渐变 + 信号） ---------- */
@Composable
fun AppMark(size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * .25f))
            .background(Brush.linearGradient(listOf(Accent1, Color(0xFF14B8A6)))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val u = w / 24f
            val cx = 12f * u
            val cy = 12.5f * u
            val ink = AccentInk
            val sw = 1.9f * u
            // 外弧（与 assets/icon.svg 同比例）
            drawArc(
                color = ink,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - 5.5f * u, cy - 5.5f * u),
                size = androidx.compose.ui.geometry.Size(11f * u, 11f * u),
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
            // 内弧
            drawArc(
                color = ink,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - 2.5f * u, cy - 2.5f * u),
                size = androidx.compose.ui.geometry.Size(5f * u, 5f * u),
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
            // 圆点
            drawCircle(ink, radius = 1.6f * u, center = Offset(cx, 16.5f * u))
        }
    }
}

/* ---------- Hero 卡片 ---------- */
@Composable
private fun HeroCard(
    latest: RelayLog.Received?,
    btConnected: Boolean,
    bluetoothEnabled: Boolean,
    onCopy: (String) -> Unit,
) {
    val g = LocalGlass.current
    var copied by remember { mutableStateOf(false) }
    GlassCard {
        SectionCaption("最新验证码")
        Spacer(Modifier.height(10.dp))
        Text(
            text = latest?.code ?: "—",
            style = TextStyle(
                fontFamily = MonoFamily,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                brush = Brush.linearGradient(
                    listOf(Color(0xFFE8FFF4), Accent1, Accent2),
                ),
            ),
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Text(
            text = latest?.let { "来自 ${it.sender} · ${it.ts}" } ?: "尚未收到验证码",
            color = g.text2, fontSize = 12.sp,
        )
        Spacer(Modifier.height(16.dp))
        GradientButton(
            text = "复制验证码",
            icon = GlassIcons.Copy,
            copied = copied,
            onClick = {
                latest?.let {
                    onCopy(it.code)
                    copied = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusOrb(if (!bluetoothEnabled) OrbState.Err else if (btConnected) OrbState.Live else OrbState.Idle)
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    !bluetoothEnabled -> "蓝牙未启用"
                    btConnected -> "监听中"
                    else -> "未启动监听"
                },
                color = g.text2, fontSize = 12.sp,
            )
        }
    }
}

/* ---------- 历史卡片 ---------- */
@Composable
private fun HistoryCard(
    received: List<RelayLog.Received>,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val g = LocalGlass.current
    val listState = rememberLazyListState()
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionCaption("最近接收", GlassIcons.History)
            Spacer(Modifier.weight(1f))
            Text(
                "${received.size}", color = g.text2, fontSize = 11.sp, fontFamily = MonoFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(g.glassStrong)
                    .border(1.dp, g.glassBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (received.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(GlassIcons.Inbox, null, tint = g.text3, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(10.dp))
                Text("收到的验证码会出现在这里", color = g.text3, fontSize = 12.sp)
                Text("点击任意一行即可复制", color = g.text3, fontSize = 11.sp)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(received.asReversed()) { r ->
                    HistoryRow(r, onCopy)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(r: RelayLog.Received, onCopy: (String) -> Unit) {
    val g = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassDimens.ctl))
            .clickable { onCopy(r.code) }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(r.ts, color = g.text3, fontSize = 11.sp, fontFamily = MonoFamily)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.code, color = g.text, fontSize = 15.sp, fontFamily = MonoFamily,
                fontWeight = FontWeight.SemiBold)
            Text(r.sender, color = g.text2, fontSize = 11.sp)
        }
        Icon(GlassIcons.Copy, null, tint = g.text3, modifier = Modifier.size(14.dp))
    }
}

/* ---------- 警告卡片 ---------- */
@Composable
private fun WarningCard(msg: String, onDismiss: () -> Unit) {
    val g = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassDimens.card))
            .background(Danger.copy(alpha = .14f))
            .border(1.dp, Danger.copy(alpha = .3f), RoundedCornerShape(GlassDimens.card))
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(msg, color = Danger, fontSize = 12.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) {
            Icon(GlassIcons.X, null, tint = Danger, modifier = Modifier.size(18.dp))
        }
    }
}

/* ---------- 设置抽屉 ---------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSettingsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val g = LocalGlass.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = g.panel,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        GlassSettingsContent(vm, onDismiss)
    }
}