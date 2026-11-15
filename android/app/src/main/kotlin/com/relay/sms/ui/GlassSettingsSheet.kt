package com.relay.sms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.relay.sms.MainViewModel
import com.relay.sms.RelayLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSettingsContent(vm: MainViewModel, onDismiss: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val logs by RelayLog.lines.collectAsStateWithLifecycle()
    val g = LocalGlass.current
    val toast = rememberToastState()

    Column(
        Modifier
            .fillMaxWidth()
            .background(g.panel)
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        // 标题
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(GlassIcons.Settings, null, tint = g.text2, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("设置", color = g.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(GlassIcons.X, null, tint = g.text2)
            }
        }

        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
            // ---- PC 蓝牙设备 ----
            SectionCaption("PC 蓝牙设备", GlassIcons.Shield)
            Spacer(Modifier.height(10.dp))
            DeviceDropdown(state, vm)
            Spacer(Modifier.height(10.dp))
            Row {
                GhostButton("刷新设备列表", GlassIcons.Refresh, onClick = vm::refreshDevices)
            }
            Spacer(Modifier.height(18.dp))

            // ---- Token ----
            SectionCaption("Token（与 PC 端一致）", GlassIcons.Shield)
            Spacer(Modifier.height(10.dp))
            TokenInput(state, vm)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("复制", GlassIcons.Copy, onClick = {
                    vm.copyToClipboard(state.token)
                    toast.show("已复制 Token")
                })
                GradientButton("保存配置", GlassIcons.Save, onClick = vm::saveConfig)
            }
            Spacer(Modifier.height(18.dp))

            // ---- 外观 ----
            SectionCaption("外观", GlassIcons.Sun)
            Spacer(Modifier.height(10.dp))
            GlassSegmented(
                options = listOf(
                    "深色" to GlassIcons.Moon,
                    "浅色" to GlassIcons.Sun,
                    "跟随系统" to GlassIcons.Settings,
                ),
                selected = when (state.appearance) {
                    "dark" -> "深色"
                    "light" -> "浅色"
                    else -> "跟随系统"
                },
                onSelect = { label ->
                    vm.setAppearance(
                        when (label) {
                            "深色" -> "dark"
                            "浅色" -> "light"
                            else -> "system"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            GlassSwitchRow(
                label = "开机自启动",
                checked = state.autostart,
                onChange = vm::setAutostart,
            )
            Text(
                "国产 ROM 需在系统「设置 → 应用 → 自启动管理」中允许本应用，否则开机广播会被拦截。",
                color = g.text3, fontSize = 11.sp, lineHeight = 15.sp,
            )
            Spacer(Modifier.height(18.dp))

            // ---- 运行日志 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionCaption("运行日志", GlassIcons.List)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { RelayLog.clear() }, modifier = Modifier.size(24.dp)) {
                    Icon(GlassIcons.Trash, "清空日志", tint = g.text2, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            LogPanel(logs)
            Spacer(Modifier.height(24.dp))
            }
            BoxToast(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }
    }
}

/* ---------- 设备下拉 ---------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDropdown(state: com.relay.sms.RelayUiState, vm: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = state.devices.firstOrNull { it.address == state.selectedAddress }?.label ?: ""
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("选择已配对的 PC") },
            placeholder = { Text("无已配对设备") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = glassTextFieldColors(),
            shape = RoundedCornerShape(GlassDimens.ctl),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.devices.forEach { d ->
                DropdownMenuItem(
                    text = { Text(d.label) },
                    onClick = {
                        vm.selectDevice(d.address)
                        expanded = false
                    },
                )
            }
        }
    }
}

/* ---------- Token 输入 ---------- */
@Composable
private fun TokenInput(state: com.relay.sms.RelayUiState, vm: MainViewModel) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = state.token,
        onValueChange = vm::setToken,
        label = { Text("Token") },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) GlassIcons.EyeOff else GlassIcons.Eye, null, modifier = Modifier.size(18.dp))
            }
        },
        colors = glassTextFieldColors(),
        shape = RoundedCornerShape(GlassDimens.ctl),
        modifier = Modifier.fillMaxWidth(),
    )
}

/* ---------- 日志面板 ---------- */
@Composable
private fun LogPanel(logs: List<RelayLog.Line>) {
    val g = LocalGlass.current
    val listState = rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(GlassDimens.ctl))
            .background(g.inputBg)
            .border(1.dp, g.glassBorder, RoundedCornerShape(GlassDimens.ctl))
            .padding(10.dp),
    ) {
        if (logs.isEmpty()) {
            Text("(无日志)", color = g.text3, fontSize = 11.sp, fontFamily = MonoFamily)
        } else {
            LazyColumn(state = listState) {
                items(logs) { line ->
                    Text(
                        "${line.ts}  ${line.msg}",
                        color = g.text, fontSize = 11.sp, fontFamily = MonoFamily,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

/* ---------- 底部 toast ---------- */
@Composable
private fun BoxToast(state: ToastState, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        GlassToastHost(state)
    }
}