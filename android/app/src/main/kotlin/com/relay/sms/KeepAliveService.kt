package com.relay.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class KeepAliveService : Service() {
    companion object {
        private const val TAG = "KeepAliveService"
        private const val CHANNEL_ID = "sms_relay_channel"
        private const val NOTIF_ID = 1
    }

    private var poller: SmsPoller? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("验证码中继运行中")
            .setContentText("正在轮询短信并转发到 PC")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIF_ID, notif)

        if (poller == null) {
            poller = SmsPoller(this)
            poller?.start()
            Log.i(TAG, "短信轮询已启动")
        } else {
            MainActivity.log("轮询已在运行")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        poller?.stop()
        poller = null
        MainActivity.log("服务已停止")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "验证码中继", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "保持短信监听后台运行" }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }
}
