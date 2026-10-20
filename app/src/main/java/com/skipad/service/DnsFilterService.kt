package com.skipad.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.skipad.data.Prefs
import com.skipad.dns.DnsCapture
import com.skipad.dns.DnsOverrides
import com.skipad.dns.DnsPolicy
import com.skipad.dns.DnsProxy

/**
 * 实验性 DNS 拦截（本地 VPN）。
 *
 * 只接管 DNS：TUN 里只加一条到虚拟 DNS 服务器的路由，其它流量完全不经过我们。
 * 命中黑名单回 NXDOMAIN，未命中转发上游 223.5.5.5。
 *
 * 也承担「DNS 录入」：录入模式下把观察到的域名丢进 DnsCapture 缓冲区，
 * 由悬浮窗那边决定存进黑名单还是白名单。
 */
class DnsFilterService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var proxy: DnsProxy? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                stopVpn()
                startVpn()
            }
            ACTION_START, null -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (tun != null) {
            Log.i(TAG, "already running")
            return
        }
        val prefs = Prefs.get(this)

        val builder = Builder()
            .setSession("跳过 · DNS")
            .addAddress("10.111.222.1", 32)
            .addDnsServer("10.111.222.2")
            .addRoute("10.111.222.2", 32)
            .setMtu(1500)

        // 自己不进隧道，避免上游 socket 被自己套娃
        runCatching { builder.addDisallowedApplication(packageName) }

        val fd = runCatching { builder.establish() }.getOrNull()
        if (fd == null) {
            Log.w(TAG, "establish failed (VPN 未授权?)")
            notifyState("VPN 授权失败，请先点「申请 VPN 权限」")
            stopSelf()
            return
        }
        tun = fd
        running = true
        Log.i(TAG, "vpn established")

        val ghOn = Prefs.get(this).settings.value.ghAccelEnabled
        if (ghOn) DnsOverrides.load(this)

        proxy = DnsProxy(
            tun = fd,
            upstreamHost = UPSTREAM,
            shouldBlock = { domain -> shouldBlock(prefs, domain) },
            override = if (ghOn) { domain, _ -> DnsOverrides.lookup(domain) } else null,
            onQuery = { domain -> DnsCapture.record(domain) },
            protector = { socket -> runCatching { protect(socket) }.getOrDefault(false) },
        ).also { it.start() }
    }

    private fun shouldBlock(prefs: Prefs, domain: String): Boolean {
        val st = prefs.settings.value
        // 保护名单：图片/视频/聊天/系统域名永不拦
        if (DnsPolicy.matches(domain, DnsPolicy.NEVER_BLOCK)) return false
        // 白名单优先：用户明确标记为「软件自身」的域名永不拦
        if (DnsPolicy.matches(domain, st.dnsAllowed)) return false
        // 录入的广告域名
        if (st.dnsEnabled && DnsPolicy.matches(domain, st.dnsBlocked)) return true
        // 内置基础广告域名
        if (st.dnsEnabled && DnsPolicy.matches(domain, DnsPolicy.BASE_ADS)) return true
        // HTTPDNS 端点抑制（独立开关）
        if (st.dnsHttpDnsBlock && DnsPolicy.matches(domain, DnsPolicy.HTTPDNS_ENDPOINTS)) return true
        return false
    }

    private fun stopVpn() {
        running = false
        runCatching { proxy?.shutdown() }
        proxy = null
        runCatching { tun?.close() }
        tun = null
        Log.i(TAG, "vpn stopped")
    }

    override fun onRevoke() {
        Log.i(TAG, "vpn revoked by system")
        stopVpn()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun notifyState(text: String) {
        runCatching { KeepAlive.ensureChannel(this) }
        Log.i(TAG, text)
    }

    companion object {
        private const val TAG = "DnsFilterService"
        private const val UPSTREAM = "223.5.5.5"

        const val ACTION_START = "com.skipad.DNS_START"
        const val ACTION_STOP = "com.skipad.DNS_STOP"
        const val ACTION_RESTART = "com.skipad.DNS_RESTART"

        /** 供 UI 判断是否在跑 */
        @Volatile
        var running: Boolean = false
            private set
    }
}
