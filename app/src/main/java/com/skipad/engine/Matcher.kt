package com.skipad.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.skipad.model.MatchSpec
import com.skipad.model.Rule

/** 在节点树里找"跳过"按钮 */
object Matcher {

    private const val MAX_NODES = 2000
    private const val MAX_DEPTH = 40

    private val regexCache = HashMap<String, Regex>()

    private fun regex(pattern: String): Regex =
        regexCache.getOrPut(pattern) { Regex(pattern) }

    fun find(
        root: AccessibilityNodeInfo,
        rule: Rule,
        screenWidth: Int,
        screenHeight: Int,
    ): AccessibilityNodeInfo? {
        val spec = rule.match
        if (spec.ids.isEmpty() && spec.texts.isEmpty() && spec.descs.isEmpty()) return null

        // 前置条件：这个窗口里得先看到「广告」之类的标识，才允许去点关闭
        if (spec.markers.isNotEmpty() && !hasMarker(root, spec.markers)) return null

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++

            if (nodeMatches(node, spec, screenWidth, screenHeight)) {
                // 面积过大的节点多半是页面容器（ViewPager / RecyclerView），
                // 点它等于随机点屏幕中间，会跳进视频或详情页
                if (!isTooBig(node, screenWidth, screenHeight)) return node
            }

            if (depth < MAX_DEPTH) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    queue.add(child to depth + 1)
                }
            }
        }
        if (spec.fallbackCorner) {
            return findCornerClickable(root, spec, screenWidth, screenHeight)
        }
        return null
    }

    /** 一个节点占屏超过 30% 就不当作按钮 */
    private fun isTooBig(node: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int): Boolean {
        if (screenWidth <= 0 || screenHeight <= 0) return false
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.isEmpty) return true
        val area = r.width().toLong() * r.height().toLong()
        val screen = screenWidth.toLong() * screenHeight.toLong()
        return area * 100 > screen * 30
    }

    /**
     * 兜底：在指定区域的"小可点节点"里挑面积最小的那个点掉。
     * 摇一摇/插屏广告的 ✕ 往往没有文字、没有 id、还是个自绘 ImageView，
     * 只能靠"右上角 + 小按钮"这个经验特征找。
     */
    private fun findCornerClickable(
        root: AccessibilityNodeInfo,
        spec: MatchSpec,
        screenWidth: Int,
        screenHeight: Int,
    ): AccessibilityNodeInfo? {
        if (screenWidth <= 0 || screenHeight <= 0) return null
        val screenArea = screenWidth * screenHeight
        var best: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.isClickable && node.isVisibleToUser && !node.isPassword) {
                val r = Rect()
                node.getBoundsInScreen(r)
                if (!r.isEmpty) {
                    val area = r.width() * r.height()
                    val cx = r.exactCenterX() / screenWidth
                    val cy = r.exactCenterY() / screenHeight
                    val inArea = spec.areas.isEmpty() || spec.areas.any { it.contains(cx, cy) }
                    // 只认"小按钮"：小于屏幕面积 2.5%，避免点到整张广告卡
                    if (inArea && area > 0 && area < screenArea / 40 && area < bestArea) {
                        bestArea = area
                        best = node
                    }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return best
    }

    private fun hasMarker(root: AccessibilityNodeInfo, markers: List<String>): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()
            for (marker in markers) {
                if (!text.isNullOrEmpty() && text.length <= 24 && text.contains(marker)) return true
                if (!desc.isNullOrEmpty() && desc.length <= 24 && desc.contains(marker)) return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun nodeMatches(
        node: AccessibilityNodeInfo,
        spec: MatchSpec,
        screenWidth: Int,
        screenHeight: Int,
    ): Boolean {
        // 防御：不可见 / 密码框一律不点
        if (!node.isVisibleToUser || node.isPassword) return false
        if (!inArea(node, spec, screenWidth, screenHeight)) return false

        val id = node.viewIdResourceName
        if (spec.ids.isNotEmpty() && !id.isNullOrEmpty()) {
            for (pattern in spec.ids) {
                if (idMatches(id, pattern)) return true
            }
        }

        val text = node.text?.toString()
        if (spec.texts.isNotEmpty() && !text.isNullOrBlank()) {
            for (pattern in spec.texts) {
                if (regex(pattern).containsMatchIn(text)) return true
            }
        }

        val desc = node.contentDescription?.toString()
        if (spec.descs.isNotEmpty() && !desc.isNullOrBlank()) {
            for (pattern in spec.descs) {
                if (regex(pattern).containsMatchIn(desc)) return true
            }
        }

        return false
    }

    /** id 支持四种写法：完整 id、`:id/xxx`、纯 `xxx`、通配 `*close*` */
    private fun idMatches(fullId: String, pattern: String): Boolean {
        if (pattern.contains('*')) {
            return wildcardRegex(pattern).matches(fullId)
        }
        return when {
            pattern.contains(":id/") -> fullId == pattern
            pattern.startsWith("*") -> fullId.endsWith(pattern.drop(1))
            else -> fullId.endsWith(":id/$pattern") || fullId == pattern
        }
    }

    private fun wildcardRegex(pattern: String): Regex = regexCache.getOrPut("wc:$pattern") {
        val sb = StringBuilder()
        for (ch in pattern) {
            if (ch == '*') sb.append(".*") else sb.append(Regex.escape(ch.toString()))
        }
        Regex(sb.toString())
    }

    private fun inArea(
        node: AccessibilityNodeInfo,
        spec: MatchSpec,
        screenWidth: Int,
        screenHeight: Int,
    ): Boolean {
        if (spec.areas.isEmpty()) return true
        if (screenWidth <= 0 || screenHeight <= 0) return true
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false
        val cx = (rect.left + rect.right) / 2f / screenWidth
        val cy = (rect.top + rect.bottom) / 2f / screenHeight
        return spec.areas.any { it.contains(cx, cy) }
    }
}
