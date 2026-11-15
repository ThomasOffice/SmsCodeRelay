package com.relay.sms

import android.content.Context
import android.content.SharedPreferences

/** SharedPreferences 常量与访问入口（自旧 SmsReceiver 迁出，存储名不变，旧配置不丢失）。 */
object Prefs {
    const val NAME = "sms_relay_prefs"
    const val KEY_DEVICE = "device_address"
    const val KEY_TOKEN = "token"
    const val KEY_APPEARANCE = "appearance"
    const val KEY_AUTOSTART = "autostart"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}