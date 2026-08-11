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

    // 固定 RFCOMM 端口，必须与 PC 端 bt_rfcomm.py 的 RFCOMM_PORT 一致
    private const val RFCOMM_PORT = 5

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

        var lastError: Exception? = null
        for (attempt in 1..3) {
            var socket: BluetoothSocket? = null
            var out: OutputStream? = null
            try {
                adapter.cancelDiscovery()

                // 方式1：反射直连固定 RFCOMM 端口（绕过 SDP 查询）
                // PC 端用 Winsock2 bind+listen 此端口，不注册 SDP
                Log.i(TAG, "尝试反射直连端口 $RFCOMM_PORT (第${attempt}次)")
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                socket = method.invoke(device, RFCOMM_PORT) as BluetoothSocket

                socket.connect()
                out = socket.outputStream
                val data = (payload.toString() + "\n").toByteArray(Charsets.UTF_8)
                out.write(data)
                out.flush()
                Log.i(TAG, "已发送: ${payload.optString("code")} (端口$RFCOMM_PORT, 第${attempt}次)")
                return Result(true, "已发送 ${data.size} 字节")
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "反射直连失败(第${attempt}次): ${e.message}")
                try { out?.close() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}

                // 方式2：回退到 SDP 方式（兼容旧 PC 端 COM 端口模式）
                if (attempt == 1) {
                    Log.i(TAG, "尝试 SDP 方式回退...")
                    var socket2: BluetoothSocket? = null
                    var out2: OutputStream? = null
                    try {
                        socket2 = device.createRfcommSocketToServiceRecord(SPP_UUID)
                        socket2.connect()
                        out2 = socket2.outputStream
                        val data = (payload.toString() + "\n").toByteArray(Charsets.UTF_8)
                        out2.write(data)
                        out2.flush()
                        Log.i(TAG, "SDP 方式发送成功")
                        return Result(true, "已发送 ${data.size} 字节 (SDP)")
                    } catch (e2: Exception) {
                        Log.w(TAG, "SDP 回退也失败: ${e2.message}")
                        try { out2?.close() } catch (_: Exception) {}
                        try { socket2?.close() } catch (_: Exception) {}
                    }
                }

                if (attempt < 3) Thread.sleep(500)
            }
        }
        return Result(false, "连接/发送失败(重试3次): ${lastError?.javaClass?.simpleName}: ${lastError?.message}")
    }
}
