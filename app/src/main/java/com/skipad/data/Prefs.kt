package com.skipad.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Settings(
    val enabled: Boolean,
    val windowMs: Long,
    val excludes: Set<String>,
    val disabledRules: Set<String>,
    val debugDump: Boolean,
    val todayCount: Int,
    val totalCount: Int,
    val specialized: Set<String>,
    val blocked: Set<String>,
    val tips: List<String>,
    val blacklist: Set<String>,
    val advancedEnabled: Boolean,
    val floatingEnabled: Boolean,
    val floatingSize: Int,
    val floatingColor: Int,
    val floatingAlpha: Float,
    val floatingClickable: Boolean,
    val floatingLongPressClose: Boolean,
    val floatingClickAction: String,
    val floatingCustomPkg: String,
    val forceKeepAlive: Boolean,
    val islandEnabled: Boolean,
    val hiddenNotification: Boolean,
    val floatingHidden: Boolean,
    val floatingHideInGames: Boolean,
    /** 自动点击器的点击点，格式 "x,y" */
    val luckDate: String,
    val luckValue: Int,
    val notesFloating: Boolean,
    val notesIndependent: Boolean,
    val ghAccelEnabled: Boolean,
    val autoClickEnabled: Boolean,
    val autoClickPanel: Boolean,
    val autoClickMarkers: Boolean,
    val autoClickPanelX: Int,
    val autoClickPanelY: Int,
    val autoClickPoints: Set<String>,
    val autoClickIntervalMs: Long,
    val autoClickTimes: Int,
    val gkdStrict: Boolean,
    val gkdRulesEnabled: Boolean,
    val disabledCategories: Set<String>,
    val unsupportedBanner: Boolean,
    val dnsEnabled: Boolean,
    val dnsHttpDnsBlock: Boolean,
    val dnsCaptureMode: Boolean,
    val dnsBlocked: Set<String>,
    val dnsAllowed: Set<String>,
    val islandItems: Set<String>,
    val floatingImageUri: String,
    val floatingSoundUri: String,
    val floatingX: Int,
    val floatingY: Int,
    val backgroundEnabled: Boolean,
    val silentBackground: Boolean,
    val bootEnabled: Boolean,
    val bootAsked: Boolean,
)

class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("skipad_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun todayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun load(): Settings {
        val today = sp.getString(KEY_TODAY_KEY, null)
        if (today != todayKey()) {
            // 跨天了：今日计数归零
            sp.edit().putString(KEY_TODAY_KEY, todayKey()).putInt(KEY_TODAY_COUNT, 0).apply()
        }
        return Settings(
            enabled = sp.getBoolean(KEY_ENABLED, true),
            windowMs = sp.getLong(KEY_WINDOW_MS, DEFAULT_WINDOW_MS),
            excludes = sp.getStringSet(KEY_EXCLUDES, DEFAULT_EXCLUDES) ?: DEFAULT_EXCLUDES,
            disabledRules = sp.getStringSet(KEY_DISABLED_RULES, emptySet()) ?: emptySet(),
            debugDump = sp.getBoolean(KEY_DEBUG_DUMP, false),
            todayCount = sp.getInt(KEY_TODAY_COUNT, 0),
            totalCount = sp.getInt(KEY_TOTAL_COUNT, 0),
            specialized = sp.getStringSet(KEY_SPECIALIZED, emptySet()) ?: emptySet(),
            blocked = sp.getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet(),
            tips = loadTips(),
            blacklist = sp.getStringSet(KEY_BLACKLIST, emptySet()) ?: emptySet(),
            advancedEnabled = sp.getBoolean(KEY_ADVANCED_ENABLED, true),
            floatingEnabled = sp.getBoolean(KEY_FLOATING, false),
            floatingSize = sp.getInt(KEY_FLOAT_SIZE, 30),
            floatingColor = sp.getInt(KEY_FLOAT_COLOR, 0xFF2F6FEB.toInt()),
            floatingAlpha = sp.getFloat(KEY_FLOAT_ALPHA, 0.85f),
            floatingClickable = sp.getBoolean(KEY_FLOAT_CLICKABLE, true),
            floatingLongPressClose = sp.getBoolean(KEY_FLOAT_LONGPRESS, true),
            floatingClickAction = sp.getString(KEY_FLOAT_ACTION, ACTION_OPEN_APP) ?: ACTION_OPEN_APP,
            floatingCustomPkg = sp.getString(KEY_FLOAT_PKG, "") ?: "",
            forceKeepAlive = sp.getBoolean(KEY_FORCE_KEEP, true),
            islandEnabled = sp.getBoolean(KEY_ISLAND, true),
            hiddenNotification = sp.getBoolean(KEY_HIDDEN_NOTIFICATION, false),
            floatingHidden = sp.getBoolean(KEY_FLOAT_HIDDEN, false),
            floatingHideInGames = sp.getBoolean(KEY_FLOAT_HIDE_GAMES, true),
            luckDate = sp.getString(KEY_LUCK_DATE, "") ?: "",
            luckValue = sp.getInt(KEY_LUCK_VALUE, -1),
            notesFloating = sp.getBoolean(KEY_NOTES_FLOAT, false),
            notesIndependent = sp.getBoolean(KEY_NOTES_INDEPENDENT, true),
            ghAccelEnabled = sp.getBoolean(KEY_GH_ACCEL, false),
            autoClickEnabled = sp.getBoolean(KEY_AUTOCLICK_ENABLED, false),
            autoClickPanel = sp.getBoolean(KEY_AUTOCLICK_PANEL, false),
            autoClickMarkers = sp.getBoolean(KEY_AUTOCLICK_MARKERS, true),
            autoClickPanelX = sp.getInt(KEY_AUTOCLICK_PANEL_X, -1),
            autoClickPanelY = sp.getInt(KEY_AUTOCLICK_PANEL_Y, -1),
            autoClickPoints = sp.getStringSet(KEY_AUTOCLICK_POINTS, emptySet()) ?: emptySet(),
            autoClickIntervalMs = sp.getLong(KEY_AUTOCLICK_INTERVAL, 200L),
            autoClickTimes = sp.getInt(KEY_AUTOCLICK_TIMES, 0),
            gkdStrict = sp.getBoolean(KEY_GKD_STRICT, true),
            gkdRulesEnabled = sp.getBoolean(KEY_GKD_RULES, true),
            disabledCategories = sp.getStringSet(KEY_DISABLED_CATS, DEFAULT_DISABLED_CATS)
                ?: DEFAULT_DISABLED_CATS,
            unsupportedBanner = sp.getBoolean(KEY_BANNER, true),
            dnsEnabled = sp.getBoolean(KEY_DNS_ENABLED, false),
            dnsHttpDnsBlock = sp.getBoolean(KEY_DNS_HTTPDNS, true),
            dnsCaptureMode = sp.getBoolean(KEY_DNS_CAPTURE, false),
            dnsBlocked = sp.getStringSet(KEY_DNS_BLOCKED, emptySet()) ?: emptySet(),
            dnsAllowed = sp.getStringSet(KEY_DNS_ALLOWED, emptySet()) ?: emptySet(),
            islandItems = sp.getStringSet(KEY_ISLAND_ITEMS, DEFAULT_ISLAND_ITEMS) ?: DEFAULT_ISLAND_ITEMS,
            floatingImageUri = sp.getString(KEY_FLOAT_IMG, "") ?: "",
            floatingSoundUri = sp.getString(KEY_FLOAT_SOUND, "") ?: "",
            floatingX = sp.getInt(KEY_FLOAT_X, -1),
            floatingY = sp.getInt(KEY_FLOAT_Y, -1),
            backgroundEnabled = sp.getBoolean(KEY_BG_ENABLED, true),
            silentBackground = sp.getBoolean(KEY_BG_SILENT, true),
            bootEnabled = sp.getBoolean(KEY_BOOT_ENABLED, true),
            bootAsked = sp.getBoolean(KEY_BOOT_ASKED, false),
        )
    }

    private fun mutate(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        _settings.value = load()
    }

    fun setEnabled(value: Boolean) = mutate { putBoolean(KEY_ENABLED, value) }

    fun setWindowMs(value: Long) = mutate { putLong(KEY_WINDOW_MS, value) }

    fun setDebugDump(value: Boolean) = mutate { putBoolean(KEY_DEBUG_DUMP, value) }

    fun setRuleEnabled(ruleId: String, enabled: Boolean) = mutate {
        val current = sp.getStringSet(KEY_DISABLED_RULES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (enabled) current.remove(ruleId) else current.add(ruleId)
        putStringSet(KEY_DISABLED_RULES, current)
    }

    fun setExcludes(packages: Set<String>) = mutate { putStringSet(KEY_EXCLUDES, packages) }

    fun addExclude(packageName: String) = mutate {
        val current = sp.getStringSet(KEY_EXCLUDES, DEFAULT_EXCLUDES)?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        putStringSet(KEY_EXCLUDES, current)
    }

    fun removeExclude(packageName: String) = mutate {
        val current = sp.getStringSet(KEY_EXCLUDES, DEFAULT_EXCLUDES)?.toMutableSet() ?: mutableSetOf()
        current.remove(packageName)
        putStringSet(KEY_EXCLUDES, current)
    }

    fun bumpCount() = mutate {
        putInt(KEY_TODAY_COUNT, sp.getInt(KEY_TODAY_COUNT, 0) + 1)
        putInt(KEY_TOTAL_COUNT, sp.getInt(KEY_TOTAL_COUNT, 0) + 1)
        putString(KEY_TODAY_KEY, todayKey())
    }

    fun resetCounters() = mutate {
        putInt(KEY_TODAY_COUNT, 0)
        putInt(KEY_TOTAL_COUNT, 0)
        putString(KEY_TODAY_KEY, todayKey())
    }

    /** 特化名单：这些应用的"跳转"会被拦下来（点完广告跳出去会自动退回） */
    fun setSpecialized(packageName: String, enabled: Boolean) = mutate {
        val current = sp.getStringSet(KEY_SPECIALIZED, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (enabled) current.add(packageName) else current.remove(packageName)
        putStringSet(KEY_SPECIALIZED, current)
    }

    /** 黑名单：这些应用完全不管（不监控、不点击） */
    fun setBlacklist(pkg: String, on: Boolean) = mutate {
        val cur = sp.getStringSet(KEY_BLACKLIST, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (on) cur.add(pkg) else cur.remove(pkg)
        putStringSet(KEY_BLACKLIST, cur)
    }

    fun clearBlacklist() = mutate { putStringSet(KEY_BLACKLIST, emptySet()) }

    /** 高级禁用总开关：关掉后即使 Shizuku 就绪也不执行任何命令 */
    fun setAdvancedEnabled(v: Boolean) = mutate { putBoolean(KEY_ADVANCED_ENABLED, v) }

    fun setFloatingStyle(
        size: Int,
        color: Int,
        alpha: Float,
        clickable: Boolean,
        longPressClose: Boolean,
        action: String,
        customPkg: String,
    ) = mutate {
        putInt(KEY_FLOAT_SIZE, size)
        putInt(KEY_FLOAT_COLOR, color)
        putFloat(KEY_FLOAT_ALPHA, alpha)
        putBoolean(KEY_FLOAT_CLICKABLE, clickable)
        putBoolean(KEY_FLOAT_LONGPRESS, longPressClose)
        putString(KEY_FLOAT_ACTION, action)
        putString(KEY_FLOAT_PKG, customPkg)
    }

    /** 只重置悬浮窗的外观和点击动作（其余设置不动） */
    fun resetFloatingStyle() = mutate {
        remove(KEY_FLOAT_SIZE)
        remove(KEY_FLOAT_COLOR)
        remove(KEY_FLOAT_ALPHA)
        remove(KEY_FLOAT_CLICKABLE)
        remove(KEY_FLOAT_LONGPRESS)
        remove(KEY_FLOAT_ACTION)
        remove(KEY_FLOAT_PKG)
        remove(KEY_FLOAT_IMG)
        remove(KEY_FLOAT_SOUND)
        remove(KEY_FLOAT_X)
        remove(KEY_FLOAT_Y)
    }

    /** 恢复所有默认设置（等于清空本机配置，无障碍开关不受影响） */
    fun resetAll() {
        sp.edit().clear().apply()
        _settings.value = load()
    }

    fun setFloatingImage(uri: String) = mutate { putString(KEY_FLOAT_IMG, uri) }

    fun setFloatingSound(uri: String) = mutate { putString(KEY_FLOAT_SOUND, uri) }

    fun setIslandEnabled(v: Boolean) = mutate { putBoolean(KEY_ISLAND, v) }

    fun setCategoryEnabled(cat: String, enabled: Boolean) = mutate {
        val cur = sp.getStringSet(KEY_DISABLED_CATS, DEFAULT_DISABLED_CATS)?.toMutableSet()
            ?: DEFAULT_DISABLED_CATS.toMutableSet()
        if (enabled) cur.remove(cat) else cur.add(cat)
        putStringSet(KEY_DISABLED_CATS, cur)
    }

    /** 隐藏通知：不显示横幅 / 锁屏，只保留静默状态 */
    fun setHiddenNotification(v: Boolean) = mutate { putBoolean(KEY_HIDDEN_NOTIFICATION, v) }

    /** 隐藏悬浮窗（1 像素、全透明、不可点） */
    fun setFloatingHidden(v: Boolean) = mutate { putBoolean(KEY_FLOAT_HIDDEN, v) }

    /** 全屏游戏里自动隐藏悬浮窗 */
    fun setFloatingHideInGames(v: Boolean) = mutate { putBoolean(KEY_FLOAT_HIDE_GAMES, v) }

    fun addAutoClickPoint(x: Int, y: Int) = mutate {
        val cur = sp.getStringSet(KEY_AUTOCLICK_POINTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        cur.add("$x,$y")
        putStringSet(KEY_AUTOCLICK_POINTS, cur)
    }

    fun removeAutoClickPoint(point: String) = mutate {
        val cur = sp.getStringSet(KEY_AUTOCLICK_POINTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        cur.remove(point)
        putStringSet(KEY_AUTOCLICK_POINTS, cur)
    }

    fun clearAutoClickPoints() = mutate { putStringSet(KEY_AUTOCLICK_POINTS, emptySet()) }

    fun setLuck(date: String, value: Int) = mutate {
        putString(KEY_LUCK_DATE, date)
        putInt(KEY_LUCK_VALUE, value)
    }

    fun setNotesFloating(v: Boolean) = mutate { putBoolean(KEY_NOTES_FLOAT, v) }

    fun setNotesIndependent(v: Boolean) = mutate { putBoolean(KEY_NOTES_INDEPENDENT, v) }

    fun setGhAccelEnabled(v: Boolean) = mutate { putBoolean(KEY_GH_ACCEL, v) }

    fun setAutoClickEnabled(v: Boolean) = mutate { putBoolean(KEY_AUTOCLICK_ENABLED, v) }

    fun setAutoClickPanel(v: Boolean) = mutate { putBoolean(KEY_AUTOCLICK_PANEL, v) }

    fun setAutoClickMarkers(v: Boolean) = mutate { putBoolean(KEY_AUTOCLICK_MARKERS, v) }

    fun setAutoClickPanelPos(x: Int, y: Int) = mutate {
        putInt(KEY_AUTOCLICK_PANEL_X, x)
        putInt(KEY_AUTOCLICK_PANEL_Y, y)
    }

    fun setAutoClickInterval(ms: Long) = mutate { putLong(KEY_AUTOCLICK_INTERVAL, ms) }

    fun setAutoClickTimes(n: Int) = mutate { putInt(KEY_AUTOCLICK_TIMES, n) }

    /** 社区规则严格模式：只保留像「关闭/跳过」的规则 */
    fun setGkdStrict(v: Boolean) = mutate { putBoolean(KEY_GKD_STRICT, v) }

    /** GKD 社区规则包开关 */
    fun setGkdRulesEnabled(v: Boolean) = mutate { putBoolean(KEY_GKD_RULES, v) }

    /** 非小米系统顶部的「不适配」滚动提示条开关 */
    fun setUnsupportedBanner(v: Boolean) = mutate { putBoolean(KEY_BANNER, v) }

    // ---------- 实验性：DNS 拦截 ----------
    fun setDnsEnabled(v: Boolean) = mutate { putBoolean(KEY_DNS_ENABLED, v) }

    fun setDnsHttpDnsBlock(v: Boolean) = mutate { putBoolean(KEY_DNS_HTTPDNS, v) }

    fun setDnsCaptureMode(v: Boolean) = mutate { putBoolean(KEY_DNS_CAPTURE, v) }

    fun addDnsBlocked(domains: Collection<String>) = mutate {
        val cur = sp.getStringSet(KEY_DNS_BLOCKED, emptySet())?.toMutableSet() ?: mutableSetOf()
        cur.addAll(domains)
        putStringSet(KEY_DNS_BLOCKED, cur)
    }

    fun addDnsAllowed(domains: Collection<String>) = mutate {
        val cur = sp.getStringSet(KEY_DNS_ALLOWED, emptySet())?.toMutableSet() ?: mutableSetOf()
        cur.addAll(domains)
        putStringSet(KEY_DNS_ALLOWED, cur)
    }

    fun removeDnsBlocked(domain: String) = mutate {
        val cur = sp.getStringSet(KEY_DNS_BLOCKED, emptySet())?.toMutableSet() ?: mutableSetOf()
        cur.remove(domain)
        putStringSet(KEY_DNS_BLOCKED, cur)
    }

    fun removeDnsAllowed(domain: String) = mutate {
        val cur = sp.getStringSet(KEY_DNS_ALLOWED, emptySet())?.toMutableSet() ?: mutableSetOf()
        cur.remove(domain)
        putStringSet(KEY_DNS_ALLOWED, cur)
    }

    fun resetDnsLists() = mutate {
        remove(KEY_DNS_BLOCKED)
        remove(KEY_DNS_ALLOWED)
    }

    fun setIslandItem(key: String, on: Boolean) = mutate {
        val cur = sp.getStringSet(KEY_ISLAND_ITEMS, DEFAULT_ISLAND_ITEMS)?.toMutableSet()
            ?: DEFAULT_ISLAND_ITEMS.toMutableSet()
        if (on) cur.add(key) else cur.remove(key)
        putStringSet(KEY_ISLAND_ITEMS, cur)
    }

    fun setFloatingPosition(x: Int, y: Int) = mutate {
        putInt(KEY_FLOAT_X, x)
        putInt(KEY_FLOAT_Y, y)
    }

    /** 强制保活总开关：关掉后停悬浮窗、不自动重启 */
    fun setForceKeepAlive(v: Boolean) = mutate { putBoolean(KEY_FORCE_KEEP, v) }

    /** 悬浮窗保活：前台服务 + 小圆点，划掉后更不容易死 */
    fun setFloatingEnabled(v: Boolean) = mutate { putBoolean(KEY_FLOATING, v) }

    /** 后台运行：关掉后不做后台轮询/保活 */
    fun setBackgroundEnabled(v: Boolean) = mutate { putBoolean(KEY_BG_ENABLED, v) }

    /** 静默后台：true = 不显示常驻通知（仅靠无障碍服务活着） */
    fun setSilentBackground(v: Boolean) = mutate { putBoolean(KEY_BG_SILENT, v) }

    /** 开机自启动（首次会弹授权确认） */
    fun setBootEnabled(v: Boolean) = mutate {
        putBoolean(KEY_BOOT_ENABLED, v)
        putBoolean(KEY_BOOT_ASKED, true)
    }

    fun markBootAsked() = mutate { putBoolean(KEY_BOOT_ASKED, true) }

    /** 小贴士：用户自定义过就用用户的，否则用默认 */
    fun loadTips(): List<String> {
        val raw = sp.getString(KEY_TIPS, null) ?: return DEFAULT_TIPS
        val list = raw.split("\u0001").filter { it.isNotBlank() }
        return if (list.isEmpty()) DEFAULT_TIPS else list
    }

    fun setTips(tips: List<String>) = mutate {
        putString(KEY_TIPS, tips.joinToString("\u0001"))
    }

    fun resetTips() = mutate { remove(KEY_TIPS) }

    /** 全程拦截名单：这些应用在前台时会持续扫描并关掉广告（不限开屏窗口） */
    fun setBlocked(packageName: String, enabled: Boolean) = mutate {
        val current = sp.getStringSet(KEY_BLOCKED, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (enabled) current.add(packageName) else current.remove(packageName)
        putStringSet(KEY_BLOCKED, current)
    }

    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_WINDOW_MS = "window_ms"
        const val KEY_EXCLUDES = "excludes"
        const val KEY_DISABLED_RULES = "disabled_rules"
        const val KEY_DEBUG_DUMP = "debug_dump"
        const val KEY_TODAY_COUNT = "today_count"
        const val KEY_TOTAL_COUNT = "total_count"
        const val KEY_TODAY_KEY = "today_key"
        const val KEY_SPECIALIZED = "specialized_packages"
        const val KEY_BLOCKED = "blocked_packages"
        const val KEY_TIPS = "custom_tips"
        const val KEY_BLACKLIST = "blacklist_packages"
        const val KEY_ADVANCED_ENABLED = "advanced_enabled"
        const val KEY_FLOATING = "floating_enabled"
        const val KEY_FLOAT_SIZE = "float_size"
        const val KEY_FLOAT_COLOR = "float_color"
        const val KEY_FLOAT_ALPHA = "float_alpha"
        const val KEY_FLOAT_CLICKABLE = "float_clickable"
        const val KEY_FLOAT_LONGPRESS = "float_longpress"
        const val KEY_FLOAT_ACTION = "float_action"
        const val KEY_FLOAT_PKG = "float_pkg"
        const val KEY_FORCE_KEEP = "force_keep_alive"
        const val KEY_ISLAND = "island_enabled"
        const val KEY_HIDDEN_NOTIFICATION = "hidden_notification"
        const val KEY_FLOAT_HIDDEN = "float_hidden"
        const val KEY_FLOAT_HIDE_GAMES = "float_hide_in_games"
        const val KEY_GKD_RULES = "gkd_rules_enabled"
        const val KEY_GKD_STRICT = "gkd_strict"
        const val KEY_AUTOCLICK_POINTS = "autoclick_points"
        const val KEY_GH_ACCEL = "gh_accel_enabled"
        const val KEY_LUCK_DATE = "luck_date"
        const val KEY_LUCK_VALUE = "luck_value"
        const val KEY_NOTES_FLOAT = "notes_floating"
        const val KEY_NOTES_INDEPENDENT = "notes_independent"
        const val KEY_AUTOCLICK_ENABLED = "autoclick_enabled"
        const val KEY_AUTOCLICK_PANEL = "autoclick_panel"
        const val KEY_AUTOCLICK_MARKERS = "autoclick_markers"
        const val KEY_AUTOCLICK_PANEL_X = "autoclick_panel_x"
        const val KEY_AUTOCLICK_PANEL_Y = "autoclick_panel_y"
        const val KEY_AUTOCLICK_INTERVAL = "autoclick_interval"
        const val KEY_AUTOCLICK_TIMES = "autoclick_times"
        const val KEY_DISABLED_CATS = "disabled_categories"

        /** 默认关掉「功能类」：那是自动化操作，不是去广告 */
        val DEFAULT_DISABLED_CATS = setOf("功能类")
        const val KEY_BANNER = "unsupported_banner"
        const val KEY_DNS_ENABLED = "dns_enabled"
        const val KEY_DNS_HTTPDNS = "dns_httpdns_block"
        const val KEY_DNS_CAPTURE = "dns_capture_mode"
        const val KEY_DNS_BLOCKED = "dns_blocked"
        const val KEY_DNS_ALLOWED = "dns_allowed"
        const val KEY_ISLAND_ITEMS = "island_items"

        const val ISLAND_OPEN = "open"
        const val ISLAND_TOGGLE = "toggle"
        const val ISLAND_KILL = "kill"
        const val ISLAND_CLOSE = "close"
        const val ISLAND_CUSTOM = "custom"
        const val ISLAND_DNS = "dns"
        const val ISLAND_NOTE = "note"

        val DEFAULT_ISLAND_ITEMS = setOf(ISLAND_OPEN, ISLAND_TOGGLE, ISLAND_KILL, ISLAND_CLOSE)
        val ALL_ISLAND_ITEMS = listOf(ISLAND_OPEN, ISLAND_TOGGLE, ISLAND_KILL, ISLAND_CLOSE, ISLAND_CUSTOM, ISLAND_DNS)
        const val KEY_FLOAT_IMG = "float_img"
        const val KEY_FLOAT_SOUND = "float_sound"
        const val KEY_FLOAT_X = "float_x"
        const val KEY_FLOAT_Y = "float_y"

        const val ACTION_OPEN_APP = "open_app"
        const val ACTION_TOGGLE_SKIP = "toggle_skip"
        const val ACTION_CUSTOM_APP = "custom_app"
        const val ACTION_KILL_BG = "kill_bg"
        const val ACTION_NONE = "none"
        const val KEY_BG_ENABLED = "background_enabled"
        const val KEY_BG_SILENT = "silent_background"
        const val KEY_BOOT_ENABLED = "boot_enabled"
        const val KEY_BOOT_ASKED = "boot_asked"

        const val DEFAULT_WINDOW_MS = 12_000L

        /** 默认不介入：自己、系统界面、桌面、设置 */
        val DEFAULT_EXCLUDES: Set<String> = setOf(
            "com.skipad",
            "com.skipad.debug",
            "com.android.systemui",
            "com.miui.home",
            "com.android.settings",
        )

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }

        val DEFAULT_TIPS: List<String> = listOf(
            "关掉目标应用的「获取设备动作与方向」权限，摇一摇就摇不动啦~",
            "这招比点跳过还快：摇一摇广告靠陀螺仪触发，权限一关它就没脾气",
            "本软件适用于小米 / 红米系列手机，其它品牌也能用，只是保活要自己调",
            "本软件由 deep seek V4.1 大肥鱼友情制作~",
            "MIUI 里把「跳过」的省电策略调成无限制，后台才不容易被清掉",
            "开发者选项里把动画缩放调成 0.5x，手机顺一点，跳过也更快",
            "开屏广告只在应用启动后 12 秒内找，等太久就不管了哦",
            "跳过失败的话去「日志」页看一眼，是不是规则没命中，截图发我就行",
            "「全程拦截」功耗略高，只给广告泛滥的应用开，别全开",
            "想少看广告又不装软件？设置 → 隐私保护 → 权限管理，把不常用应用的传感器权限都关掉",
            "彩蛋：长按这张小贴士卡，就能编辑这些提示啦~",
            "点这张卡片可以换下一条，我知道的还多着呢~",
        )
    }
}
