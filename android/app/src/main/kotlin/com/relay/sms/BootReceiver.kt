package com.relay.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/** 开机自启动：接收 BOOT_COMPLETED，若已开启自启且已配置则启动监听服务。
 *
 * 注意：国产 ROM（华为/荣耀/小米等）需在系统「自启动管理」中手动允许本应用，
 * 否则系统会拦截开机广播。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return

        val prefs = Prefs.get(context)
        if (!prefs.getBoolean(Prefs.KEY_AUTOSTART, false)) return
        val device = prefs.getString(Prefs.KEY_DEVICE, null)
        val token = prefs.getString(Prefs.KEY_TOKEN, null)
        if (device.isNullOrEmpty() || token.isNullOrEmpty()) {
            Log.i("BootReceiver", "未配置设备/token，跳过自启")
            return
        }

        Log.i("BootReceiver", "开机自启：启动监听服务")
        val service = Intent(context, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(service)
        } else {
            context.startService(service)
        }
    }
}