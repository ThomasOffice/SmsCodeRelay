package com.relay.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat

class SmsReceiver : BroadcastReceiver() {
    companion object {
        const val PREFS = "sms_relay_prefs"
        const val KEY_DEVICE = "device_address"
        const val KEY_TOKEN = "token"

        private var lastTs: Long = 0
        private var lastBody: String = ""
        private val lock = Any()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return

        val sender = msgs[0].displayOriginatingAddress ?: msgs[0].originatingAddress ?: ""
        val body = msgs.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
        Log.i("SmsReceiver", "广播收到短信 sender=$sender")

        // 去重：与 ContentObserver 避免重复处理
        synchronized(lock) {
            val now = System.currentTimeMillis()
            if (body == lastBody && now - lastTs < 3000) return
            lastTs = now
            lastBody = body
        }

        SmsProcessor.process(context, sender, body)
    }
}
