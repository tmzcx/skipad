package com.skipad.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object ServiceStatus {

    /** 无障碍服务是否在系统里被勾选并绑定 */
    fun isEnabled(context: Context): Boolean {
        val component = ComponentName(context, SkipService::class.java)
        val flat = component.flattenToString()
        val short = component.flattenToShortString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { it.equals(flat, true) || it.equals(short, true) }
    }
}
