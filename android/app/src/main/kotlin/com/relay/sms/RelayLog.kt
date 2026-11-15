package com.relay.sms

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 全局日志总线：任意后台线程可写，UI 通过 StateFlow 收集。
 *
 * 替代旧 MainActivity.companion 的静态日志——Activity 不在前台时日志也不再丢失。
 */
object RelayLog {
    data class Line(val ts: String, val msg: String)
    data class Received(val code: String, val sender: String, val ts: String)

    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = _lines.asStateFlow()

    private val _warning = MutableStateFlow<String?>(null)
    val warning: StateFlow<String?> = _warning.asStateFlow()

    private val _received = MutableStateFlow<List<Received>>(emptyList())
    val received: StateFlow<List<Received>> = _received.asStateFlow()

    fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _lines.update { (it + Line(ts, msg)).takeLast(200) }
    }

    fun addReceived(code: String, sender: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _received.update { (it + Received(code, sender, ts)).takeLast(100) }
    }

    fun warn(msg: String) {
        log("⚠ $msg")
        _warning.value = msg
    }

    fun dismissWarning() {
        _warning.value = null
    }

    fun clear() {
        _lines.value = emptyList()
    }
}