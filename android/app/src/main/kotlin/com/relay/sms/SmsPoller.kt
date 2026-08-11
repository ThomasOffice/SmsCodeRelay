package com.relay.sms

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.Telephony
import android.util.Log

class SmsPoller(private val context: Context) {
    companion object {
        private const val TAG = "SmsPoller"
        private const val INTERVAL_MS = 2000L
        private val SMS_URI: Uri = Uri.parse("content://sms/inbox")
    }

    private var maxSeenId: Long = 0
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        maxSeenId = getLatestSmsId()
        MainActivity.log("轮询启动，基准 id=$maxSeenId")
        running = true
        thread = Thread {
            while (running) {
                try {
                    pollOnce()
                } catch (e: Exception) {
                    Log.e(TAG, "轮询异常: ${e.message}")
                }
                SystemClock.sleep(INTERVAL_MS)
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun pollOnce() {
        val cursor = context.contentResolver.query(
            SMS_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            null,
            null,
            "${Telephony.Sms._ID} DESC"
        ) ?: return
        cursor.use {
            if (!it.moveToFirst()) return
            val topId = it.getLong(0)
            if (topId <= maxSeenId) return  // 无新短信
            // 收集所有新短信（从新到旧，直到遇到已处理的 id）
            val newMsgs = mutableListOf<Triple<Long, String, String>>()
            do {
                val id = it.getLong(0)
                if (id <= maxSeenId) break
                val sender = it.getString(1) ?: ""
                val body = it.getString(2) ?: ""
                newMsgs.add(Triple(id, sender, body))
            } while (it.moveToNext())
            // 按时间顺序处理（旧的先处理）
            newMsgs.reversed().forEach { (id, sender, body) ->
                Log.i(TAG, "轮询发现新短信 id=$id sender=$sender")
                MainActivity.log("轮询发现新短信: id=$id sender=$sender")
                // 验证码安全保护检测
                val prot = CodeProtector.detect(body)
                if (prot.risk != CodeProtector.Risk.NONE) {
                    MainActivity.log("⚠ ${prot.message}")
                    MainActivity.showWarning(prot.message)
                    NotifHelper.show(context, "验证码保护提醒", prot.message)
                }
                SmsProcessor.process(context, sender, body)
            }
            maxSeenId = topId
        }
    }

    private fun getLatestSmsId(): Long {
        try {
            val cursor = context.contentResolver.query(
                SMS_URI,
                arrayOf(Telephony.Sms._ID),
                null,
                null,
                "${Telephony.Sms._ID} DESC"
            ) ?: return 0
            cursor.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(0)
                    MainActivity.log("当前最新短信 id=$id")
                    return id
                }
            }
        } catch (e: Exception) {
            MainActivity.log("查询最新 id 异常: ${e.message}")
        }
        return 0
    }
}
