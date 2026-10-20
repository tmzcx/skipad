package com.skipad.dns

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * 极简 DNS 代理：跑在 VpnService 的 TUN 上。
 *
 * 只处理 IPv4 + UDP:53：
 * - 命中黑名单 → 直接回 NXDOMAIN（客户端拿不到 IP，广告素材就加载不出来）
 * - 未命中 → 转发给上游 DNS，把回包原样塞回 TUN
 *
 * 上游 socket 必须 protect()，否则它自己也会走进 VPN，形成死循环。
 */
class DnsProxy(
    private val tun: ParcelFileDescriptor,
    private val upstreamHost: String,
    private val shouldBlock: (String) -> Boolean,
    /** 命中返回要直接回答的 IPv4 列表（GitHub 加速用），null 表示不接管 */
    private val override: ((String, Int) -> List<String>?)? = null,
    private val onQuery: (String) -> Unit,
    private val protector: (DatagramSocket) -> Boolean,
) : Thread("skipad-dns-proxy") {

    @Volatile
    private var running = true

    private var input: FileInputStream? = null
    private var output: FileOutputStream? = null
    private var socket: DatagramSocket? = null

    override fun run() {
        val inStream = FileInputStream(tun.fileDescriptor)
        val outStream = FileOutputStream(tun.fileDescriptor)
        input = inStream
        output = outStream

        val up = runCatching {
            DatagramSocket().apply {
                connect(InetAddress.getByName(upstreamHost), 53)
                soTimeout = 4000
                protector(this)
            }
        }.onFailure { Log.w(TAG, "upstream socket failed", it) }.getOrNull()
        socket = up

        val buffer = ByteArray(32 * 1024)
        while (running) {
            val len = try {
                inStream.read(buffer)
            } catch (t: Throwable) {
                if (running) Log.w(TAG, "tun read end: ${t.javaClass.simpleName}")
                break
            }
            if (len <= 0) continue
            runCatching { handle(buffer, len, up) }
                .onFailure { Log.w(TAG, "handle failed", it) }
        }
        Log.i(TAG, "proxy thread exit")
    }

    fun shutdown() {
        running = false
        runCatching { socket?.close() }
        runCatching { input?.close() }
        runCatching { output?.close() }
    }

    // ---------------- 核心处理 ----------------

    private fun handle(buf: ByteArray, len: Int, up: DatagramSocket?) {
        if (len < 28) return
        val version = (buf[0].toInt() and 0xF0) ushr 4
        if (version != 4) return
        val ihl = (buf[0].toInt() and 0x0F) * 4
        if (ihl < 20 || len < ihl + 8) return
        if (buf[9].toInt() != 17) return // 只处理 UDP

        val udpOff = ihl
        val srcPort = u16(buf, udpOff)
        val dstPort = u16(buf, udpOff + 2)
        if (dstPort != 53) return
        val udpLen = u16(buf, udpOff + 4)
        if (udpLen < 8) return
        val payloadOff = udpOff + 8
        val payloadLen = (udpLen - 8).coerceAtMost(len - payloadOff)
        if (payloadLen < 12) return

        val srcIp = buf.copyOfRange(12, 16)
        val dstIp = buf.copyOfRange(16, 20)

        val question = parseQuestion(buf, payloadOff, payloadLen) ?: return
        onQuery(question.name)

        val overrideIps = if (question.qtype == 1) override?.invoke(question.name, question.qtype) else null
        val response = when {
            overrideIps != null && overrideIps.isNotEmpty() ->
                buildAnswerWithDnsjava(buf.copyOfRange(payloadOff, payloadOff + payloadLen), overrideIps)
                    ?: buildAnswerA(buf, payloadOff, question, overrideIps)
            shouldBlock(question.name) ->
                buildNxDomain(buf, payloadOff, payloadLen, question)
            else ->
                forward(buf, payloadOff, payloadLen, up)
        } ?: return

        val packet = buildIpv4Udp(
            srcIp = dstIp,
            srcPort = dstPort,
            dstIp = srcIp,
            dstPort = srcPort,
            payload = response,
        )
        runCatching { output?.write(packet) }
    }

    private fun forward(buf: ByteArray, off: Int, len: Int, up: DatagramSocket?): ByteArray? {
        val sock = up ?: return null
        return runCatching {
            val query = buf.copyOfRange(off, off + len)
            sock.send(DatagramPacket(query, query.size))
            val resp = ByteArray(4096)
            val packet = DatagramPacket(resp, resp.size)
            sock.receive(packet)
            resp.copyOf(packet.length)
        }.onFailure { Log.w(TAG, "forward failed: ${it.javaClass.simpleName}") }.getOrNull()
    }

    /** 构造 NXDOMAIN：保留 question 段，rcode=3 */
    private fun buildNxDomain(
        buf: ByteArray,
        off: Int,
        len: Int,
        q: DnsQuestion,
    ): ByteArray {
        val questionEnd = off + q.questionEndOffset
        val questionLen = (questionEnd - off).coerceAtMost(len)
        val out = ByteArray(12 + questionLen)
        // ID
        out[0] = buf[off]
        out[1] = buf[off + 1]
        // Flags: QR=1, RD=1, RA=1, RCODE=3 → 0x8183
        out[2] = 0x81.toByte()
        out[3] = 0x83.toByte()
        // QDCOUNT = 1
        out[4] = 0
        out[5] = 1
        // AN / NS / AR = 0
        for (i in 6..11) out[i] = 0
        System.arraycopy(buf, off + 12, out, 12, questionLen - 12)
        return out
    }

    /**
     * 用 dnsjava 组一个合法应答（GitHub 加速）。
     * 手拼字节容易被 netd 判成坏包，所以优先走库。
     */
    private fun buildAnswerWithDnsjava(queryBytes: ByteArray, ips: List<String>): ByteArray? =
        runCatching {
            val query = org.xbill.DNS.Message(java.nio.ByteBuffer.wrap(queryBytes))
            val q = query.question ?: return@runCatching null
            val resp = org.xbill.DNS.Message(query.header.id)
            resp.header.setFlag(org.xbill.DNS.Flags.QR.toInt())
            resp.header.setFlag(org.xbill.DNS.Flags.RD.toInt())
            resp.header.setFlag(org.xbill.DNS.Flags.RA.toInt())
            resp.addRecord(q, org.xbill.DNS.Section.QUESTION)
            for (ip in ips) {
                val addr = java.net.InetAddress.getByName(ip)
                if (addr is java.net.Inet4Address) {
                    resp.addRecord(
                        org.xbill.DNS.ARecord(q.name, org.xbill.DNS.DClass.IN, 300L, addr),
                        org.xbill.DNS.Section.ANSWER,
                    )
                }
            }
            resp.toWire()
        }.getOrNull()

    /** 直接回答 A 记录（GitHub 加速） */
    private fun buildAnswerA(
        buf: ByteArray,
        off: Int,
        q: DnsQuestion,
        ips: List<String>,
    ): ByteArray {
        val questionLen = q.questionEndOffset
        var total = 12 + questionLen + ips.size * 16
        val out = ByteArray(total)
        out[0] = buf[off]
        out[1] = buf[off + 1]
        out[2] = 0x81.toByte()   // QR=1 RD=1 RA=1
        out[3] = 0x80.toByte()   // RCODE=0
        out[4] = 0
        out[5] = 1               // QDCOUNT
        out[6] = ((ips.size shr 8) and 0xFF).toByte()
        out[7] = (ips.size and 0xFF).toByte()
        out[8] = 0; out[9] = 0; out[10] = 0; out[11] = 0
        System.arraycopy(buf, off + 12, out, 12, questionLen - 12)

        var p = 12 + questionLen
        for (ip in ips) {
            val bytes = ip.split(".").mapNotNull { it.toIntOrNull()?.toByte() }
            if (bytes.size != 4) continue
            out[p++] = 0xC0.toByte(); out[p++] = 0x0C  // 指向问题里的域名
            out[p++] = 0; out[p++] = 1                  // TYPE A
            out[p++] = 0; out[p++] = 1                  // CLASS IN
            out[p++] = 0; out[p++] = 0; out[p++] = 0x01; out[p++] = 0x2C  // TTL 300
            out[p++] = 0; out[p++] = 4                  // RDLENGTH
            out[p++] = bytes[0]; out[p++] = bytes[1]; out[p++] = bytes[2]; out[p++] = bytes[3]
        }
        return if (p == out.size) out else out.copyOf(p)
    }

    // ---------------- 解析 ----------------

    private data class DnsQuestion(val name: String, val qtype: Int, val questionEndOffset: Int)

    private fun parseQuestion(buf: ByteArray, off: Int, len: Int): DnsQuestion? {
        var pos = off + 12
        val sb = StringBuilder()
        var guard = 0
        while (pos < off + len && guard++ < 128) {
            val l = buf[pos].toInt() and 0xFF
            if (l == 0) {
                pos++
                break
            }
            if (l and 0xC0 == 0xC0) {
                // 压缩指针（查询里几乎不出现，出现就放弃）
                return null
            }
            if (pos + 1 + l > off + len) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(buf, pos + 1, l, Charsets.US_ASCII))
            pos += 1 + l
        }
        if (sb.isEmpty()) return null
        // 跳过 QTYPE + QCLASS
        val end = pos + 4
        if (end > off + len) return null
        val qtype = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
        return DnsQuestion(sb.toString().lowercase(), qtype, end - off)
    }

    private fun u16(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)

    // ---------------- 组包 ----------------

    private fun buildIpv4Udp(
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val total = 20 + 8 + payload.size
        val out = ByteArray(total)
        out[0] = 0x45
        out[1] = 0
        out[2] = ((total shr 8) and 0xFF).toByte()
        out[3] = (total and 0xFF).toByte()
        out[4] = 0
        out[5] = 0
        out[6] = 0x40 // DF
        out[7] = 0
        out[8] = 64 // TTL
        out[9] = 17 // UDP
        // checksum 先置 0，最后算
        System.arraycopy(srcIp, 0, out, 12, 4)
        System.arraycopy(dstIp, 0, out, 16, 4)

        // UDP 头
        val udpOff = 20
        out[udpOff] = ((srcPort shr 8) and 0xFF).toByte()
        out[udpOff + 1] = (srcPort and 0xFF).toByte()
        out[udpOff + 2] = ((dstPort shr 8) and 0xFF).toByte()
        out[udpOff + 3] = (dstPort and 0xFF).toByte()
        val udpLen = 8 + payload.size
        out[udpOff + 4] = ((udpLen shr 8) and 0xFF).toByte()
        out[udpOff + 5] = (udpLen and 0xFF).toByte()
        // UDP checksum（带伪首部）：有些 ROM 会把 checksum=0 的包当坏包丢掉
        out[udpOff + 6] = 0
        out[udpOff + 7] = 0

        System.arraycopy(payload, 0, out, 28, payload.size)
        // IPv4 允许 UDP checksum=0（之前实测可用）

        // IP 头校验和
        var sum = 0
        var i = 0
        while (i < 20) {
            sum += ((out[i].toInt() and 0xFF) shl 8) or (out[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val checksum = sum.inv() and 0xFFFF
        out[10] = ((checksum shr 8) and 0xFF).toByte()
        out[11] = (checksum and 0xFF).toByte()
        return out
    }


    /** UDP 校验和：伪首部(12) + UDP 头 + 数据 */
    private fun udpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        packet: ByteArray,
        udpOff: Int,
        udpLen: Int,
    ): Int {
        var sum = 0L
        fun add(b: Int) { sum += b }
        for (i in 0 until 4) add(srcIp[i].toInt() and 0xFF)
        for (i in 0 until 4) add(dstIp[i].toInt() and 0xFF)
        add(0)
        add(17)
        add((udpLen shr 8) and 0xFF)
        add(udpLen and 0xFF)
        var i = udpOff
        val end = udpOff + udpLen
        while (i + 1 < end) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += ((packet[i].toInt() and 0xFF) shl 8)
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val c = (sum.inv() and 0xFFFF).toInt()
        return if (c == 0) 0xFFFF else c
    }

    companion object {
        private const val TAG = "DnsProxy"
    }
}
