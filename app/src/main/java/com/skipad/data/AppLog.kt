package com.skipad.data

import androidx.compose.runtime.mutableStateListOf

/** 一条运行记录 */
data class LogEntry(
    val time: Long,
    val pkg: String,
    val appName: String,
    val ruleId: String,
    val result: String,
    val detail: String = "",
)

/**
 * 进程内的环形日志。无障碍服务在主线程写，UI 在主线程读，不需要加锁。
 */
object AppLog {

    const val MAX = 200

    val items = mutableStateListOf<LogEntry>()

    fun add(entry: LogEntry) {
        if (items.size >= MAX) {
            items.removeAt(items.size - 1)
        }
        items.add(0, entry)
    }

    fun clear() = items.clear()
}
