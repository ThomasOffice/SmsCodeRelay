package com.relay.sms

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var spinner: Spinner
    private lateinit var tokenInput: EditText
    private lateinit var statusText: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var warnContainer: FrameLayout
    private lateinit var warnText: TextView
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private val logLines = mutableListOf<String>()
        @Volatile private var current: MainActivity? = null
        private val mainHandler = Handler(Looper.getMainLooper())

        fun log(msg: String) {
            val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            synchronized(logLines) {
                logLines.add("[$ts] $msg")
                if (logLines.size > 200) logLines.removeAt(0)
            }
            mainHandler.post { current?.refreshLog() }
        }

        fun showWarning(msg: String) {
            mainHandler.post { current?._showWarnCard(msg) }
        }
    }

    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(this, "缺少权限: ${denied.joinToString()}", Toast.LENGTH_LONG).show()
            log("权限被拒: ${denied.joinToString()}")
        } else {
            log("权限已授予")
        }
        refreshDeviceList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(SmsReceiver.PREFS, Context.MODE_PRIVATE)
        spinner = findViewById(R.id.spinnerDevice)
        tokenInput = findViewById(R.id.inputToken)
        statusText = findViewById(R.id.textStatus)
        logView = findViewById(R.id.textLog)
        logScroll = findViewById(R.id.scrollLog)
        warnContainer = findViewById(R.id.warnContainer)
        warnText = findViewById(R.id.textWarn)

        findViewById<Button>(R.id.btnSave).setOnClickListener { saveConfig() }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startService() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refreshDeviceList() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { testForward() }
        findViewById<Button>(R.id.btnWarnDismiss).setOnClickListener {
            warnContainer.visibility = android.view.View.GONE
        }

        requestPermissions()
        tokenInput.setText(prefs.getString(SmsReceiver.KEY_TOKEN, ""))
        refreshDeviceList()
        log("App 已启动")
        checkCodeProtection()
    }

    private fun checkCodeProtection() {
        if (CodeProtector.isProtectedBrand()) {
            val msg = "您的手机品牌可能开启「验证码安全保护」功能，" +
                    "会导致验证码被屏蔽无法提取。\n" +
                    "如遇验证码无法转发，请关闭：\n${CodeProtector.getSettingsPath()}"
            log("⚠ 验证码安全保护提醒: ${CodeProtector.getSettingsPath()}")
            _showWarnCard(msg)
        }
    }

    private fun _showWarnCard(msg: String) {
        warnText.text = msg
        warnContainer.visibility = android.view.View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        current = this
        refreshLog()
    }

    override fun onPause() {
        super.onPause()
        current = null
    }

    private fun refreshLog() {
        synchronized(logLines) {
            logView.text = logLines.joinToString("\n")
        }
        handler.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun requestPermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val toRequest = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            permsLauncher.launch(toRequest.toTypedArray())
        }
    }

    private fun refreshDeviceList() {
        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        if (adapter == null || !adapter.isEnabled) {
            statusText.text = "蓝牙未启用，请先开启蓝牙"
            log("蓝牙未启用")
            return
        }
        val devices = adapter.bondedDevices.toList()
        if (devices.isEmpty()) {
            statusText.text = "没有已配对设备，请先在系统设置中配对 PC"
            log("无已配对蓝牙设备")
            return
        }
        val items = devices.map { "${it.name ?: "未知"} (${it.address})" }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)

        val savedAddr = prefs.getString(SmsReceiver.KEY_DEVICE, null)
        if (savedAddr != null) {
            val idx = devices.indexOfFirst { it.address == savedAddr }
            if (idx >= 0) spinner.setSelection(idx)
        }
        statusText.text = "已配对设备 ${devices.size} 个，选择 PC 后保存"
        log("发现 ${devices.size} 个已配对设备")
    }

    private fun saveConfig() {
        val selected = spinner.selectedItem as? String
        if (selected == null) {
            Toast.makeText(this, "请先选择设备", Toast.LENGTH_SHORT).show()
            return
        }
        val addr = selected.substringAfterLast("(").removeSuffix(")").trim()
        val token = tokenInput.text.toString().trim()
        if (token.isEmpty()) {
            Toast.makeText(this, "请输入 token", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit()
            .putString(SmsReceiver.KEY_DEVICE, addr)
            .putString(SmsReceiver.KEY_TOKEN, token)
            .apply()
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        statusText.text = "配置已保存，可点击「启动监听」"
        log("配置已保存: 设备=$addr")
    }

    private fun startService() {
        val intent = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        statusText.text = "前台服务已启动，验证码将自动转发"
        Toast.makeText(this, "监听已启动", Toast.LENGTH_SHORT).show()
    }

    private fun testForward() {
        val device = prefs.getString(SmsReceiver.KEY_DEVICE, null)
        val token = prefs.getString(SmsReceiver.KEY_TOKEN, null)
        if (device.isNullOrEmpty() || token.isNullOrEmpty()) {
            Toast.makeText(this, "请先保存配置", Toast.LENGTH_SHORT).show()
            return
        }
        log("手动测试: 尝试蓝牙发送到 $device")
        Toast.makeText(this, "正在测试蓝牙发送…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val payload = JSONObject().apply {
                    put("v", 1)
                    put("token", token)
                    put("sender", "TEST")
                    put("body", "【测试】验证码 000000，手动测试")
                    put("code", "000000")
                    put("ts", System.currentTimeMillis())
                }
                val result = BtClient.send(this, device, payload)
                if (result.ok) {
                    log("✓ 测试发送成功 (${result.msg})")
                    NotifHelper.show(this, "测试成功", result.msg)
                } else {
                    log("✗ 测试发送失败: ${result.msg}")
                    NotifHelper.show(this, "测试失败", result.msg)
                }
            } catch (t: Throwable) {
                Log.e("MainActivity", "测试线程异常", t)
                log("✗ 测试异常: ${t.javaClass.simpleName}: ${t.message}")
            }
        }.start()
    }
}
