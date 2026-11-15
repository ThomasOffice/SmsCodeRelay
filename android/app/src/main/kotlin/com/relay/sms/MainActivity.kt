package com.relay.sms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.relay.sms.ui.GlassDashboard
import com.relay.sms.ui.GlassTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            RelayLog.log("权限被拒: ${denied.joinToString()}")
        } else {
            RelayLog.log("权限已授予")
        }
        vm.refreshDevices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance by vm.state.collectAsStateWithLifecycle()
            GlassTheme(appearance = appearance.appearance) {
                GlassDashboard(vm)
            }
        }
        requestPermissions()
        RelayLog.log("App 已启动")
    }

    override fun onResume() {
        super.onResume()
        vm.syncServiceState()
        vm.refreshDevices()
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
}