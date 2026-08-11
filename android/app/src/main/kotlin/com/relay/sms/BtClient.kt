package com.relay.sms

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.OutputStream
import java.util.UUID

object BtClient {
    private const val TAG = "BtClient"
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    data class Result(val ok: Boolean, val msg: String)

    fun send(context: Context, deviceAddress: String, payload: JSONObject): Result {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        if (adapter == null) return Result(false, "无蓝牙适配器")
        if (!adapter.isEnabled) return Result(false, "蓝牙未启用")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return Result(false, "缺少 BLUETOOTH_CONNECT 权限")
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return Result(false, "缺少 BLUETOOTH_SCAN 权限，请在 App 内授予")
            }
        }

        val device: BluetoothDevice?
        try {
            device = adapter.getRemoteDevice(deviceAddress.trim())
        } catch (e: Exception) {
            return Result(false, "找不到设备: $deviceAddress (${e.message})")
        }
        if (device == null) return Result(false, "设备为空: $deviceAddress")

        var socket: BluetoothSocket? = null
        var out: OutputStream? = null
        return try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            // cancelDiscovery 需要 BLUETOOTH_SCAN 权限；失败不阻断发送
            try {
                adapter.cancelDiscovery()
            } catch (e: SecurityException) {
                Log.w(TAG, "cancelDiscovery 被权限拒绝，继续: ${e.message}")
            }
            socket.connect()
            out = socket.outputStream
            val data = (payload.toString() + "\n").toByteArray(Charsets.UTF_8)
            out.write(data)
            out.flush()
            Log.i(TAG, "已发送: ${payload.optString("code")}")
            Result(true, "已发送 ${data.size} 字节")
        } catch (e: Exception) {
            Log.e(TAG, "发送失败: ${e.message}")
            Result(false, "连接/发送失败: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            try { out?.close() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
