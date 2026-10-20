package com.skipad.dns

import java.util.Collections

/**
 * DNS 录入缓冲区。
 *
 * 录入模式下，DnsProxy 每看到一次查询就丢进来一条；
 * 用户点一下标记窗，就把缓冲区里的域名按当前阶段（广告 / 软件自身）提交到对应名单。
 *
 * 只在同一个进程内使用（DnsFilterService 和 FloatingService 同进程）。
 */
object DnsCapture {

    @Volatile
    var active: Boolean = false

    private val buffer: MutableList<String> = Collections.synchronizedList(mutableListOf())

    fun start() {
        buffer.clear()
        active = true
    }

    fun stop() {
        active = false
        buffer.clear()
    }

    fun record(domain: String) {
        if (!active) return
        val d = domain.lowercase().trim()
        if (d.isEmpty()) return
        if (buffer.size > 5000) return
        buffer.add(d)
    }

    /** 取出并清空 */
    fun drain(): List<String> {
        synchronized(buffer) {
            val out = buffer.toList()
            buffer.clear()
            return out.distinct()
        }
    }

    fun size(): Int = buffer.size
}
