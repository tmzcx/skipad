package com.skipad.data

/**
 * 网站推荐：内置的一份「工具网站」清单，点一次随机抽一条。
 *
 * 说明：只收常用的工具站/资源站，域名随时可能变，抽到打不开就再抽一条。
 */
data class SitePick(val name: String, val url: String, val note: String, val tag: String)

object SitePicks {

    val ALL: List<SitePick> = listOf(
        // 搜索 / 综合
        SitePick("Yandex（俄罗斯）", "https://yandex.ru/", "俄罗斯最大搜索引擎，图片/以图搜图很强", "搜索"),
        SitePick("维基百科", "https://zh.wikipedia.org/", "需要自备网络环境", "知识"),
        SitePick("Internet Archive", "https://archive.org/", "网页/软件/影音的历史存档", "存档"),

        // 系统 / 装机 / 软件
        SitePick("图吧工具箱", "https://www.tbtool.cn/", "硬件检测全家桶，验机、看颗粒、跑分都在里面", "系统"),
        SitePick("果核剥壳", "https://www.ghxi.com/", "绿色软件、精简版工具", "软件"),
        SitePick("423Down", "https://www.423down.com/", "常用软件的便携/精简版本", "软件"),
        SitePick("胡萝卜周", "https://www.carrotchou.blog/", "软件分享站，更新勤", "软件"),
        SitePick("小众软件", "https://www.appinn.com/", "发现小而美的工具", "软件"),
        SitePick("异次元软件", "https://www.iplaysoft.com/", "软件评测与推荐", "软件"),
        SitePick("AlternativeTo", "https://alternativeto.net/", "找一个软件的替代品", "软件"),

        // 图片 / 文档 / 转换
        SitePick("TinyPNG", "https://tinypng.com/", "PNG/JPG 无损压缩", "图片"),
        SitePick("Squoosh", "https://squoosh.app/", "谷歌出的图片压缩，参数细", "图片"),
        SitePick("remove.bg", "https://www.remove.bg/zh", "一键抠图去背景", "图片"),
        SitePick("iLovePDF", "https://www.ilovepdf.com/zh-cn", "PDF 合并/拆分/转换", "文档"),
        SitePick("PDF24", "https://tools.pdf24.org/zh/", "免费 PDF 工具箱，可离线用", "文档"),
        SitePick("CloudConvert", "https://cloudconvert.com/", "两百多种格式互转", "转换"),
        SitePick("草料二维码", "https://cli.im/", "二维码生成与美化", "工具"),

        // 开发 / 在线工具
        SitePick("tool.lu", "https://tool.lu/", "中文在线工具箱，几十个小工具", "工具"),
        SitePick("菜鸟教程", "https://www.runoob.com/", "查语法最顺手的站", "学习"),
        SitePick("MDN", "https://developer.mozilla.org/zh-CN/", "Web 开发权威文档", "学习"),
        SitePick("Regex101", "https://regex101.com/", "正则在线调试，带解释", "开发"),
        SitePick("JSON.cn", "https://www.json.cn/", "JSON 格式化/校验", "开发"),
        SitePick("Can I use", "https://caniuse.com/", "查前端特性的浏览器兼容性", "开发"),
        SitePick("时间戳转换", "https://tool.chinaz.com/tools/unixtime.aspx", "秒/毫秒时间戳互转", "开发"),

        // 游戏 / 娱乐
        SitePick("SteamDB", "https://steamdb.info/", "查 Steam 价格史低、在线人数", "游戏"),
        SitePick("HowLongToBeat", "https://howlongtobeat.com/", "这游戏要玩多久", "游戏"),
        SitePick("LocalSend", "https://localsend.org/", "跨设备互传文件，局域网直连不经过服务器", "工具"),
        SitePick("临时邮箱", "https://temp-mail.org/zh/", "注册小号用，收完就丢", "工具"),
    )

    private var lastIndex = -1

    /** 随机抽一条，尽量不和上一条重复 */
    fun random(): SitePick {
        if (ALL.isEmpty()) return SitePick("空", "", "", "")
        if (ALL.size == 1) return ALL[0]
        var idx: Int
        do {
            idx = ALL.indices.random()
        } while (idx == lastIndex)
        lastIndex = idx
        return ALL[idx]
    }
}
