package com.relay.sms

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Telephony
import android.util.Log

class SmsObserver(private val context: Context, handler: Handler) : ContentObserver(handler) {
    companion object {
        private const val TAG = "SmsObserver"
        private val SMS_URI: Uri = Telephony.Sms.CONTENT_URI
        private var maxSeenId: Long = 0
    }

    fun register() {
        try {
            MainActivity.log("正在注册短信数据库观察器…")
            context.contentResolver.registerContentObserver(SMS_URI, true, this)
            maxSeenId = getLatestSmsId()
            Log.i(TAG, "已注册，基准 _id=$maxSeenId")
            MainActivity.log("短信数据库监听已启动 (基准id=$maxSeenId)")
        } catch (e: Exception) {
            MainActivity.log("✗ 注册观察器失败: ${e.message}")
        }
    }

    fun unregister() {
        try { context.contentResolver.unregisterContentObserver(this) } catch (_: Exception) {}
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        MainActivity.log("数据库变化回调触发")
        scheduleCheck()
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        MainActivity.log("数据库变化回调触发 ($uri)")
        scheduleCheck()
    }

    private var pendingCheck = false

    private fun scheduleCheck() {
        if (pendingCheck) {
            MainActivity.log("已有查询在进行中，跳过")
            return
        }
        pendingCheck = true
        MainActivity.log("将在 800ms 后查询数据库…")
        Thread {
            SystemClock.sleep(800)
            try {
                checkNewSms()
            } finally {
                pendingCheck = false
            }
        }.start()
    }

    private fun checkNewSms() {
        try {
            val cursor = context.contentResolver.query(
                SMS_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.DATE, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.TYPE),
                null,
                null,
                "${Telephony.Sms._ID} DESC"
            )
            if (cursor == null) {
                MainActivity.log("✗ cursor null (无 READ_SMS 权限?)")
                return
            }
            cursor.use {
                if (!it.moveToFirst()) {
                    MainActivity.log("数据库为空")
                    return
                }
                // 显示最新 3 条详情
                var shown = 0
                var newCount = 0
                var topId = maxSeenId
                do {
                    val id = it.getLong(0)
                    val date = it.getLong(1)
                    val sender = it.getString(2) ?: ""
                    val body = it.getString(3) ?: ""
                    val type = it.getInt(4)
                    if (shown < 3) {
                        MainActivity.log("  #$id type=$type sender=$sender | ${body.take(40)}")
                        shown++
                    }
                    if (id > maxSeenId && type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                        newCount++
                        if (id > topId) topId = id
                        MainActivity.log(">>> 处理新短信 id=$id")
                        SmsProcessor.process(context, sender, body)
                    }
                } while (it.moveToNext() && shown < 10)
                maxSeenId = topId
                MainActivity.log("查询完成: 新增 $newCount 条, maxId=$maxSeenId")
            }
        } catch (e: Exception) {
            MainActivity.log("✗ 查询异常: ${e.javaClass.simpleName}: ${e.message}")
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
                if (it.moveToFirst()) return it.getLong(0)
            }
        } catch (_: Exception) {}
        return 0
    }
}
