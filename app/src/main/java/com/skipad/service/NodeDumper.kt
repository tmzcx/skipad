package com.skipad.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.skipad.data.AppLog
import com.skipad.data.LogEntry
import java.io.File

/** 把当前窗口的节点树写成文件，用于离线分析/写规则 */
object NodeDumper {

    private const val TAG = "NodeDumper"
    private const val MAX_NODES = 3000

    fun dump(service: AccessibilityService, pkg: String, index: Int = 1) {
        val roots = A11y.rootsFor(service, pkg)
        if (roots.isEmpty()) {
            Log.w(TAG, "no root for $pkg (#$index)")
            return
        }
        val sb = StringBuilder()
        val counter = intArrayOf(0)
        roots.forEachIndexed { i, root ->
            sb.append("<!-- window #").append(i).append(" -->\n")
            append(root, sb, 0, counter)
        }

        val dir = File(service.filesDir, "dumps").apply { mkdirs() }
        val file = File(dir, "${pkg}_${index}_${System.currentTimeMillis()}.xml")
        file.writeText(sb.toString())
        Log.i(TAG, "dumped ${counter[0]} nodes -> ${file.absolutePath}")
        AppLog.add(
            LogEntry(
                time = System.currentTimeMillis(),
                pkg = pkg,
                appName = "调试",
                ruleId = "",
                result = "已抓取节点树",
                detail = file.name,
            )
        )
    }

    private fun append(
        node: AccessibilityNodeInfo,
        sb: StringBuilder,
        depth: Int,
        counter: IntArray,
    ) {
        if (counter[0]++ > MAX_NODES) return
        sb.append("  ".repeat(depth)).append("<")
        sb.append(shortClass(node.className?.toString()))
        node.viewIdResourceName?.let { sb.append(" id=\"").append(it).append("\"") }
        node.text?.toString()?.takeIf { it.isNotEmpty() }
            ?.let { sb.append(" text=\"").append(escape(it)).append("\"") }
        node.contentDescription?.toString()?.takeIf { it.isNotEmpty() }
            ?.let { sb.append(" desc=\"").append(escape(it)).append("\"") }
        if (node.isClickable) sb.append(" clickable")
        val r = Rect()
        node.getBoundsInScreen(r)
        sb.append(" bounds=\"").append(r.left).append(',').append(r.top).append(',')
            .append(r.right).append(',').append(r.bottom).append("\"")
        sb.append(">\n")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            append(child, sb, depth + 1, counter)
        }
    }

    private fun shortClass(name: String?): String =
        name?.substringAfterLast('.') ?: "View"

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
