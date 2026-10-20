package com.skipad.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

/** 取"当前应用的窗口根节点"：开源广告常常弹在独立 window 里，所以要把所有窗口都扫一遍 */
object A11y {

    /** 兼容旧调用：只取第一个 */
    fun rootFor(service: AccessibilityService, pkg: String?): AccessibilityNodeInfo? =
        rootsFor(service, pkg).firstOrNull()

    fun rootsFor(service: AccessibilityService, pkg: String?): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(4)
        val seen = HashSet<Int>()

        val active = service.rootInActiveWindow
        if (active != null && matches(active, pkg)) {
            out.add(active)
            seen.add(active.windowId)
        }

        val windows = try {
            service.windows
        } catch (t: Throwable) {
            emptyList()
        }
        for (w in windows) {
            if (!w.isActive) continue
            val root = w.root ?: continue
            if (!matches(root, pkg)) continue
            if (!seen.add(root.windowId)) continue
            out.add(root)
        }
        return out
    }

    private fun matches(root: AccessibilityNodeInfo, pkg: String?): Boolean {
        if (pkg == null) return true
        val rootPkg = root.packageName?.toString() ?: return false
        return rootPkg == pkg
    }
}
