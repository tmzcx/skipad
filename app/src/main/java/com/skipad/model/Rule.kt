package com.skipad.model

import org.json.JSONArray
import org.json.JSONObject

/** 归一化区域：0..1 的屏幕比例坐标 */
data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(cx: Float, cy: Float): Boolean =
        cx >= left && cx <= right && cy >= top && cy <= bottom
}

/** 一条规则怎么匹配 */
data class MatchSpec(
    val ids: List<String> = emptyList(),
    val texts: List<String> = emptyList(),
    val descs: List<String> = emptyList(),
    val areas: List<Area> = emptyList(),
    /** 前置条件：窗口里必须先出现这些东西（比如「广告」标识），否则不点 */
    val markers: List<String> = emptyList(),
    /** 找不到文字/id 时，兜底点击「区域内最小的可点节点」（通常是右上角的 ✕） */
    val fallbackCorner: Boolean = false,
)

/**
 * 一条跳过规则。
 *
 * @param pkg 精确包名；`*` 表示所有应用；以 `*` 结尾表示前缀匹配（调试包名用）
 * @param activity 当前 Activity 类名的正则；null 表示不限
 */
data class Rule(
    val id: String,
    val app: String,
    val pkg: String,
    val activity: String? = null,
    val windowMs: Long = 12_000L,
    val cooldownMs: Long = 3_000L,
    val maxAttempts: Int = 3,
    val priority: Int = 0,
    /** 规则来源分类（GKD 包用：开屏广告/全屏广告/更新提示…），内置规则为 null */
    val category: String? = null,
    /** true = 只在「全程拦截」名单的应用里、不限开屏窗口生效 */
    val continuous: Boolean = false,
    val match: MatchSpec,
) {
    fun pkgMatches(p: String): Boolean = when {
        pkg == "*" -> true
        pkg.endsWith("*") -> p.startsWith(pkg.dropLast(1))
        else -> p == pkg
    }

    fun activityMatches(cls: String?): Boolean {
        if (activity == null) return true
        if (cls == null) return false
        return Regex(activity).containsMatchIn(cls)
    }

    /** 规则卡片上显示的一句话说明 */
    fun summary(): String {
        val parts = mutableListOf<String>()
        if (match.ids.isNotEmpty()) parts += "id×${match.ids.size}"
        if (match.texts.isNotEmpty()) parts += "文字×${match.texts.size}"
        if (match.descs.isNotEmpty()) parts += "描述×${match.descs.size}"
        if (match.areas.isNotEmpty()) parts += "限区域"
        return parts.joinToString(" · ")
    }
}

object RuleParser {

    fun parse(json: String): List<Rule> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("rules") ?: return emptyList()
        val out = ArrayList<Rule>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val m = o.optJSONObject("match") ?: JSONObject()
            out += Rule(
                id = o.getString("id"),
                app = o.optString("app", o.getString("id")),
                pkg = o.getString("package"),
                activity = o.optString("activity").takeIf { it.isNotBlank() && !o.isNull("activity") },
                windowMs = o.optLong("windowMs", 12_000L),
                cooldownMs = o.optLong("cooldownMs", 3_000L),
                maxAttempts = o.optInt("maxAttempts", 3),
                priority = o.optInt("priority", 0),
                category = o.optString("category").takeIf { it.isNotBlank() && !o.isNull("category") },
                continuous = o.optBoolean("continuous", false),
                match = MatchSpec(
                    ids = m.optJSONArray("id").toStringList(),
                    texts = m.optJSONArray("text").toStringList(),
                    descs = m.optJSONArray("desc").toStringList(),
                    areas = m.optJSONArray("areas").toAreaList(),
                    markers = m.optJSONArray("marker").toStringList(),
                    fallbackCorner = m.optBoolean("fallbackCorner", false),
                ),
            )
        }
        return out
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> optString(i).takeIf { it.isNotBlank() } }
    }

    private fun JSONArray?.toAreaList(): List<Area> {
        if (this == null) return emptyList()
        val out = ArrayList<Area>(length())
        for (i in 0 until length()) {
            val a = optJSONArray(i) ?: continue
            if (a.length() < 4) continue
            out += Area(
                left = a.optDouble(0, 0.0).toFloat(),
                top = a.optDouble(1, 0.0).toFloat(),
                right = a.optDouble(2, 1.0).toFloat(),
                bottom = a.optDouble(3, 1.0).toFloat(),
            )
        }
        return out
    }
}
