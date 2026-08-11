package com.relay.sms

import android.content.Context
import android.util.Log
import org.json.JSONObject

object SmsProcessor {
    private const val TAG = "SmsProcessor"

    fun process(context: Context, sender: String, body: String) {
        Log.i(TAG, "处理短信 sender=$sender body=${body.take(80)}")
        MainActivity.log("收到短信: $sender | ${body.take(50)}")

        val code = CodeExtractor.extract(body)
        if (code.isNullOrEmpty()) {
            Log.i(TAG, "未提取到验证码，跳过")
            MainActivity.log("未提取到验证码，跳过")
            return
        }

        MainActivity.log("提取到验证码: $code")
        NotifHelper.show(context, "收到验证码 $code", "来自 $sender，正在转发到 PC…")

        val prefs = context.getSharedPreferences(SmsReceiver.PREFS, Context.MODE_PRIVATE)
        val device = prefs.getString(SmsReceiver.KEY_DEVICE, null)
        val token = prefs.getString(SmsReceiver.KEY_TOKEN, null)
        if (device.isNullOrEmpty() || token.isNullOrEmpty()) {
            Log.w(TAG, "未配置 PC 蓝牙地址或 token")
            MainActivity.log("未配置 PC 设备/token，请打开 App 配置")
            return
        }

        val payload = JSONObject().apply {
            put("v", 1)
            put("token", token)
            put("sender", sender)
            put("body", body)
            put("code", code)
            put("ts", System.currentTimeMillis())
        }

        Thread {
            MainActivity.log("开始蓝牙发送 -> $device")
            val result = BtClient.send(context, device, payload)
            Log.i(TAG, "发送结果: ${result.ok} ${result.msg}")
            if (result.ok) {
                MainActivity.log("✓ 已转发 $code (${result.msg})")
                NotifHelper.show(context, "已转发 $code", result.msg)
            } else {
                MainActivity.log("✗ 转发失败: ${result.msg}")
                NotifHelper.show(context, "转发失败 $code", result.msg)
            }
        }.start()
    }
}
