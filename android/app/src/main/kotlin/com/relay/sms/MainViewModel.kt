package com.relay.sms

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class DeviceItem(val name: String, val address: String) {
    val label: String get() = "$name ($address)"
}

data class RelayUiState(
    val devices: List<DeviceItem> = emptyList(),
    val selectedAddress: String = "",
    val token: String = "",
    val bluetoothEnabled: Boolean = true,
    val serviceRunning: Boolean = false,
    val testing: Boolean = false,
    val statusMessage: String = "请配置并启动",
    val appearance: String = "system",
    val autostart: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(RelayUiState())
    val state: StateFlow<RelayUiState> = _state.asStateFlow()

    private val prefs = Prefs.get(app)

    init {
        _state.update {
            it.copy(
                selectedAddress = prefs.getString(Prefs.KEY_DEVICE, "") ?: "",
                token = prefs.getString(Prefs.KEY_TOKEN, "") ?: "",
                appearance = prefs.getString(Prefs.KEY_APPEARANCE, "system") ?: "system",
                autostart = prefs.getBoolean(Prefs.KEY_AUTOSTART, false),
            )
        }
        refreshDevices()
    }

    fun refreshDevices() {
        val bm = getApplication<Application>()
            .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.update {
                it.copy(bluetoothEnabled = false, devices = emptyList(),
                    statusMessage = "蓝牙未启用，请先开启蓝牙")
            }
            RelayLog.log("蓝牙未启用")
            return
        }
        val devices = try {
            adapter.bondedDevices.map { DeviceItem(it.name ?: "未知", it.address) }
        } catch (e: SecurityException) {
            RelayLog.log("读取配对设备缺少蓝牙权限: ${e.message}")
            emptyList()
        }
        _state.update { st ->
            val selected = if (devices.any { it.address == st.selectedAddress }) st.selectedAddress else ""
            st.copy(
                bluetoothEnabled = true,
                devices = devices,
                selectedAddress = selected,
                statusMessage = if (devices.isEmpty()) "没有已配对设备，请先在系统设置中配对 PC"
                else "已配对设备 ${devices.size} 个，选择 PC 后保存",
            )
        }
        RelayLog.log("发现 ${devices.size} 个已配对设备")
    }

    fun selectDevice(address: String) = _state.update { it.copy(selectedAddress = address) }

    fun setToken(token: String) = _state.update { it.copy(token = token) }

    fun setAppearance(mode: String) {
        _state.update { it.copy(appearance = mode) }
        prefs.edit().putString(Prefs.KEY_APPEARANCE, mode).apply()
    }

    fun setAutostart(on: Boolean) {
        _state.update { it.copy(autostart = on) }
        prefs.edit().putBoolean(Prefs.KEY_AUTOSTART, on).apply()
        RelayLog.log(if (on) "已开启开机自启动（需在系统自启动管理中允许本应用）" else "已关闭开机自启动")
    }

    fun copyToClipboard(text: String) {
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("code", text))
    }

    fun saveConfig() {
        val st = _state.value
        if (st.selectedAddress.isEmpty()) {
            setStatus("请先选择设备")
            return
        }
        if (st.token.isBlank()) {
            setStatus("请输入 token")
            return
        }
        prefs.edit()
            .putString(Prefs.KEY_DEVICE, st.selectedAddress)
            .putString(Prefs.KEY_TOKEN, st.token.trim())
            .apply()
        RelayLog.log("配置已保存: 设备=${st.selectedAddress}")
        setStatus("配置已保存，可点击「启动监听」")
    }

    fun startService() {
        val context = getApplication<Application>()
        val intent = Intent(context, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _state.update { it.copy(serviceRunning = true) }
        setStatus("前台服务已启动，验证码将自动转发")
    }

    fun stopService() {
        val context = getApplication<Application>()
        context.stopService(Intent(context, KeepAliveService::class.java))
        _state.update { it.copy(serviceRunning = false) }
        RelayLog.log("用户停止监听")
        setStatus("监听已停止")
    }

    /** Activity 恢复时与服务真实状态对齐（服务可能被系统回收）。 */
    fun syncServiceState() {
        _state.update { it.copy(serviceRunning = KeepAliveService.isRunning) }
    }

    fun testForward() {
        val device = prefs.getString(Prefs.KEY_DEVICE, null)
        val token = prefs.getString(Prefs.KEY_TOKEN, null)
        if (device.isNullOrEmpty() || token.isNullOrEmpty()) {
            setStatus("请先保存配置")
            return
        }
        _state.update { it.copy(testing = true) }
        RelayLog.log("手动测试: 尝试蓝牙发送到 $device")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("v", 1)
                    put("token", token)
                    put("sender", "TEST")
                    put("body", "【测试】验证码 000000，手动测试")
                    put("code", "000000")
                    put("ts", System.currentTimeMillis())
                }
                val result = BtClient.send(getApplication(), device, payload)
                if (result.ok) {
                    RelayLog.log("✓ 测试发送成功 (${result.msg})")
                    NotifHelper.show(getApplication(), "测试成功", result.msg)
                } else {
                    RelayLog.log("✗ 测试发送失败: ${result.msg}")
                    NotifHelper.show(getApplication(), "测试失败", result.msg)
                }
            } catch (t: Throwable) {
                RelayLog.log("✗ 测试异常: ${t.javaClass.simpleName}: ${t.message}")
            } finally {
                _state.update { it.copy(testing = false) }
            }
        }
    }

    private fun setStatus(msg: String) = _state.update { it.copy(statusMessage = msg) }
}