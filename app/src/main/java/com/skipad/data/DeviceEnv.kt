package com.skipad.data

import android.os.Build

/**
 * 本机环境检测：判断是不是小米 / 红米（HyperOS / MIUI）。
 *
 * 有些功能是绑死在 MIUI 的实现上的（比如「获取设备动作与方向」的权限页路径、
 * 小米的省电策略 / 自启动引导），换到别的系统上点了也白点，所以要先判断再决定给不给用。
 */
object DeviceEnv {

    /** 是不是小米系（小米 / 红米 / POCO / MIUI 内核属性存在） */
    val isMiui: Boolean by lazy { detectMiui() }

    /** 方便界面上直接显示的名字 */
    val brandLabel: String by lazy {
        listOf(Build.MANUFACTURER, Build.BRAND)
            .filter { !it.isNullOrBlank() }
            .joinToString(" / ")
            .ifBlank { "未知设备" }
    }

    private fun detectMiui(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")) return true
        if (brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")) return true
        // 兜底：MIUI 会在系统属性里留版本号
        return !getSystemProperty("ro.miui.ui.version.name").isNullOrBlank()
    }

    private fun getSystemProperty(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
