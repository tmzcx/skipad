package com.skipad.dns

/**
 * DNS 拦截用的静态策略表。
 *
 * 说明：
 * - BASE_ADS 是「保守」的广告/统计域名，尽量只放明确的广告 SDK、统计 SDK
 * - HTTPDNS_ENDPOINTS 是各家 App 内置 DNS 的入口，阻断后 App 会（通常）回落到系统 DNS，
 *   这时 BASE_ADS 才能生效。代价是应用启动时可能卡几秒。
 *
 * 数据来源：社区整理的 HTTPDNS 端点清单（Loon 模块）与常见广告域名，人工筛过一遍。
 */
object DnsPolicy {

    /**
     * 永不放行的保护名单：图片、视频、聊天、系统这些域名一旦被拦，
     * 表现就是「图裂了 / 视频转圈 / 消息发不出」，比广告难受得多。
     *
     * 命中这里直接放行，优先级高于所有拦截规则。
     */
    val NEVER_BLOCK: List<String> = listOf(
        // B 站：图片 / 视频 / 头像
        "hdslb.com", "bilivideo.com", "biliimg.com", "biliapi.net", "biliapi.com",
        // 微信 / QQ
        "qpic.cn", "qlogo.cn", "weixin.qq.com", "wechat.com", "gtimg.cn", "qq.com",
        // 微博 / 抖音 / 小红书
        "sinaimg.cn", "weibo.cn", "weibo.com",
        "douyinpic.com", "douyinvod.com", "douyincdn.com",
        "xhscdn.com", "xiaohongshu.com",
        // 小米 / 系统
        "mi.com", "miui.com", "xiaomi.com", "xiaomi.net", "mi-img.com",
        // 常见图片 CDN
        "byteimg.com", "bytecdn.cn", "pstatp.com",
    )

    /** 基础广告域名（默认就生效） */
    val BASE_ADS: List<String> = listOf(
        // Google / 通用
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "google-analytics.com",
        "adservice.google.com",
        "admob.com",
        // 国内统计 / 广告 SDK
        "umeng.com",
        "umengcloud.com",
        "cnzz.com",
        "mmstat.com",
        "tanx.com",
        "alimama.com",
        "pos.baidu.com",
        "cpro.baidu.com",
        "mobads.baidu.com",
        "gdt.qq.com",
        "e.qq.com",
        "pgdt.gtimg.cn",
        "adsmind.gdtimg.com",
        "advertise.weibo.com",
        "sax.sina.com.cn",
        "saxn.sina.com.cn",
        "admob.cn",
        "admaster.com.cn",
        "miaozhen.com",
        "irs01.com",
        "irs01.net",
        "adview.cn",
        "inmobicdn.net",
        "domob.cn",
        "domob.org",
        "adcolony.com",
        "applovin.com",
        "unityads.unity3d.com",
        "vungle.com",
        "chartboost.com",
        "ironsrc.com",
        "supersonicads.com",
        "tapjoy.com",
        "pangle.io",
        "pangolin-sdk-toutiao.com",
        "ad.qq.com",
    )

    /** HTTPDNS / 内置 DNS 端点：阻断以迫使回落系统 DNS */
    val HTTPDNS_ENDPOINTS: List<String> = listOf(
        // 阿里
        "httpdns.alicdn.com",
        "httpdns-api.aliyuncs.com",
        "httpdns-sc.aliyuncs.com",
        "amdc.alipay.com",
        "httpdns.danuoyi.tbcache.com",
        // 腾讯 / 微信 / QQ
        "dns.weixin.qq.com",
        "dns.weixin.qq.com.cn",
        "aedns.weixin.qq.com",
        "httpdns.kg.qq.com",
        "httpdns.push.oppomobile.com",
        // 字节 / 快手 / B站
        "httpdns.bilivideo.com",
        "kuaishou.httpdns.pro",
        "httpdns.kwd.inkuai.com",
        // 百度
        "httpdns.baidu.com",
        "httpsdns.baidu.com",
        "httpdns.baidubce.com",
        "httpdns.n.shifen.com",
        // 小米 / 华为 / 魅族
        "httpdns.browser.miui.com",
        "resolver.msg.xiaomi.net",
        "httpdns.platform.dbankcloud.cn",
        "httpdns.platform.dbankcloud.com",
        "httpdns.huaweicloud.com",
        "httpdns.c.cdnhwc2.com",
        "httpdns.ocloud.heytapmobi.com",
        // 网易 / 美团 / 京东 / 微博 / 爱奇艺
        "httpdns.music.163.com",
        "httpdns.n.netease.com",
        "httpdns-sdk.n.netease.com",
        "music.httpdns.c.163.com",
        "lofter.httpdns.c.163.com",
        "httpdns.meituan.com",
        "httpdnsmultiapi.meituan.com",
        "dns.jd.com",
        "dns.weibo.cn",
        "dns.iqiyi.com",
        "doh.iqiyi.com",
        "dns.qiyipic.iqiyi.com",
        // 其他
        "httpdns.cctv.com",
        "httpdns.yunxindns.com",
        "hdns.ksyun.com",
        "serveraddr.service.kugou.com",
    )

    /**
     * 域名是否命中（含子域）
     * 例：规则 `umeng.com` 命中 `alog.umeng.com` / `umeng.com`
     */
    fun matches(domain: String, rules: Collection<String>): Boolean {
        val d = domain.lowercase().trimEnd('.')
        if (d.isEmpty()) return false
        for (rule in rules) {
            val r = rule.lowercase().trimEnd('.')
            if (r.isEmpty()) continue
            if (d == r || d.endsWith(".$r")) return true
        }
        return false
    }
}
