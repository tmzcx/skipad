package com.skipad.data

import android.content.Context
import com.skipad.model.Rule
import com.skipad.model.RuleParser

/**
 * 规则仓库：内置规则 + GKD 社区规则包（可选）。
 *
 * 规则数量大（内置 ~20，GKD 包 ~1400），所以按包名建索引，
 * 引擎每次只需要取当前包名对应的那几条。
 */
class RuleRepo(private val context: Context) {

    private data class Snapshot(
        val all: List<Rule>,
        val builtin: List<Rule>,
        val gkdAll: List<Rule>,
        val byPkg: Map<String, List<Rule>>,
        val wildcard: List<Rule>,
    )

    @Volatile
    private var cache: Snapshot? = null

    fun rules(): List<Rule> = snapshot().all

    /** 规则页只列内置规则，不然 1400 条会卡 */
    fun builtinRules(): List<Rule> = snapshot().builtin

    /** GKD 包总条数（未按分类过滤） */
    fun gkdRuleCount(): Int = snapshot().gkdAll.size

    /** 当前实际生效的 GKD 条数（已按分类过滤） */
    fun gkdEnabledCount(): Int = snapshot().all.size - snapshot().builtin.size

    /** 分类 → (总条数, 启用条数) */
    fun categoryStats(): Map<String, Pair<Int, Int>> {
        val snap = snapshot()
        val total = LinkedHashMap<String, Int>()
        for (r in snap.gkdAll) {
            val c = r.category ?: continue
            total[c] = (total[c] ?: 0) + 1
        }
        val enabled = HashMap<String, Int>()
        for (r in snap.all) {
            val c = r.category ?: continue
            enabled[c] = (enabled[c] ?: 0) + 1
        }
        return total.mapValues { (k, v) -> v to (enabled[k] ?: 0) }
    }

    /** 当前包名可能命中的规则（含通配规则） */
    fun rulesFor(pkg: String?): List<Rule> {
        val snap = snapshot()
        if (pkg == null) return snap.all
        val exact = snap.byPkg[pkg]
        return if (exact == null) snap.wildcard else exact + snap.wildcard
    }

    fun reload(strictOverride: Boolean? = null): List<Rule> {
        synchronized(this) {
            val built = load(strictOverride)
            cache = built
            return built.all
        }
    }

    private fun snapshot(): Snapshot {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val loaded = load()
            cache = loaded
            return loaded
        }
    }

    private fun load(strictOverride: Boolean? = null): Snapshot {
        val builtin = try {
            context.assets.open("rules/builtin.json")
                .bufferedReader()
                .use { RuleParser.parse(it.readText()) }
        } catch (t: Throwable) {
            AppLog.add(
                LogEntry(
                    time = System.currentTimeMillis(),
                    pkg = "",
                    appName = "规则",
                    ruleId = "",
                    result = "内置规则解析失败：${t.message}",
                )
            )
            emptyList()
        }

        val gkd = if (Prefs.get(context).settings.value.gkdRulesEnabled) {
            try {
                // 规则包是 gzip 内容、扩展名用 .dat（.gz 会被 AGP 自动解压）；老包缺文件时回退明文
                val text = runCatching {
                    context.assets.open("rules/gkd.dat").use { input ->
                        java.util.zip.GZIPInputStream(input).bufferedReader().use { it.readText() }
                    }
                }.getOrElse {
                    context.assets.open("rules/gkd.json").use { input ->
                        input.bufferedReader().use { it.readText() }
                    }
                }
                RuleParser.parse(text)
            } catch (t: Throwable) {
                AppLog.add(
                    LogEntry(
                        time = System.currentTimeMillis(),
                        pkg = "",
                        appName = "规则",
                        ruleId = "",
                        result = "GKD 规则包解析失败：${t.message}",
                    )
                )
                emptyList()
            }
        } else {
            emptyList()
        }

        val disabledCats = Prefs.get(context).settings.value.disabledCategories
        val strict = strictOverride ?: Prefs.get(context).settings.value.gkdStrict
        val gkdFiltered = gkd.filter { r ->
            val c = r.category
            val catOn = c == null || c !in disabledCats
            catOn && !isUnsafe(r, strict)
        }
        android.util.Log.i(
            "RuleRepo",
            "load strict=$strict gkd=${gkd.size} filtered=${gkdFiltered.size} builtin=${builtin.size}",
        )
        val all = builtin + gkdFiltered
        val byPkg = HashMap<String, MutableList<Rule>>()
        val wildcard = ArrayList<Rule>()
        for (r in all) {
            if (r.pkg == "*" || r.pkg.contains('*')) {
                if (r.pkg.endsWith("*")) wildcard.add(r) else wildcard.add(r)
            } else {
                byPkg.getOrPut(r.pkg) { ArrayList() }.add(r)
            }
        }
        return Snapshot(
            all = all,
            builtin = builtin,
            gkdAll = gkd,
            byPkg = byPkg,
            wildcard = wildcard,
        )
    }

    /**
     * 社区规则里有一批只盯着「取消 / 确定 / 同意 / 允许 / 拒绝」这类裸按钮的规则。
     *
     * GKD 能点它们，是因为它保留了完整的父子层级（知道这是广告弹窗里的取消）；
     * 我们转换时丢掉了层级，只剩文字，于是同样的规则在开屏 12 秒里可能点到
     * 应用自己的隐私弹窗、权限弹窗上——那就不是去广告，是帮倒忙。
     *
     * 所以这类规则一律不收（内置规则不受影响）。
     */
    private fun isUnsafe(rule: Rule, strict: Boolean): Boolean {
        if (rule.category == null) return false
        // 带文字的规则：文字必须像「关闭/跳过/不感兴趣」，而不是广告文案本身。
        // 例子：B 站 *:id/pager、text="完善信息，获取精准推荐" —— 点的是广告素材，
        // 结果就是自动打开会员购/游戏详情页。
        if (strict && rule.match.texts.isNotEmpty()) {
            val looksLikeDismiss = rule.match.texts.any { t ->
                DISMISS_TEXT_HINTS.any { hint -> t.contains(hint, ignoreCase = true) }
            }
            if (!looksLikeDismiss) return true
        }

        // 只有资源 id、没有文字的规则：要求 id 看起来像「关闭/跳过」按钮。
        // 否则像 *:id/more、*:id/pager、*:id/content 这种，点了就是随便点一下页面，
        // 表现就是「自动打开视频/评论区」——B 站实测踩出来的。
        if (strict && rule.match.texts.isEmpty() && rule.match.descs.isEmpty() &&
            rule.match.ids.isNotEmpty()
        ) {
            val looksLikeClose = rule.match.ids.any { id ->
                CLOSE_ID_HINTS.any { hint -> id.lowercase().contains(hint) }
            }
            if (!looksLikeClose) return true
        }
        for (t in rule.match.texts) {
            val bare = t.trim('^', '$')
            if (bare in UNSAFE_TEXTS) return true
        }
        return false
    }

    companion object {
        /** 只有 id 的规则，id 里必须含这些词才认作关闭按钮 */
        private val CLOSE_ID_HINTS = setOf("close", "skip", "dismiss")

        /** 带文字的规则，文字里必须含这些词才认为是「关闭类」按钮 */
        private val DISMISS_TEXT_HINTS = listOf(
            "关闭", "跳过", "不感兴趣", "不再", "忽略", "以后再说", "暂不",
            "残忍拒绝", "知道了", "×", "✕", "close", "skip", "dismiss",
            "later", "not interested",
        )

        private val UNSAFE_TEXTS = setOf(
            "取消", "确定", "同意", "允许", "拒绝", "不允许",
            "退出", "退出程序", "残忍拒绝", "暂不", "暂不开启",
        )
    }
}
