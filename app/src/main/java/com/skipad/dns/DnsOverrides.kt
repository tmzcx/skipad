package com.skipad.dns

import android.content.Context
import android.util.Log

/**
 * GitHub 加速用的「域名 → IP」覆盖表。
 *
 * 原理和 Watt Toolkit（SteamTools）的 DNS 拦截是同一类思路，只是我们的实现轻得多：
 * - 它用 WinDivert 抓 UDP:53，命中规则的域名直接伪造 A/AAAA 回环地址，
 *   再把流量交给本地反向代理（YARP）做 TLS 终止和转发；
 * - 我们没法在手机上做 TLS 中间人（证书问题），所以只做前半段：
 *   命中域名时直接返回「优选 IP」，TLS 仍然是客户端和 GitHub 直接握手，证书校验不受影响。
 *
 * 数据来自 GitHub520 的 hosts 文件（MIT），随安装包内置。
 */
object DnsOverrides {

    private const val TAG = "DnsOverrides"

    @Volatile
    private var table: Map<String, List<String>> = emptyMap()

    fun load(context: Context): Int {
        if (table.isNotEmpty()) return table.size
        val parsed = LinkedHashMap<String, MutableList<String>>()
        runCatching {
            context.assets.open("dns/github_hosts.txt").bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.substringBefore('#').trim()
                    if (line.isEmpty()) return@forEach
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size < 2) return@forEach
                    val ip = parts[0]
                    val host = parts[1].lowercase().trimEnd('.')
                    if (!ip.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) return@forEach
                    if (host.isEmpty()) return@forEach
                    parsed.getOrPut(host) { mutableListOf() }.add(ip)
                }
            }
        }.onFailure { Log.w(TAG, "load failed", it) }

        table = parsed.mapValues { it.value.distinct() }
        Log.i(TAG, "loaded ${table.size} hosts")
        return table.size
    }

    fun size(): Int = table.size

    /** 命中返回 IP 列表（每次随机取一个，起到简单轮询的作用） */
    fun lookup(domain: String): List<String>? {
        val d = domain.lowercase().trimEnd('.')
        val exact = table[d]
        if (exact != null && exact.isNotEmpty()) return listOf(exact.random())
        // 支持子域，例如 assets-cdn.github.com 命中 github.com 的配置
        for ((host, ips) in table) {
            if (d.endsWith(".$host") && ips.isNotEmpty()) return listOf(ips.random())
        }
        return null
    }
}
