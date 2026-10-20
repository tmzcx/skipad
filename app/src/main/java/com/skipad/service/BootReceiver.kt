package com.skipad.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.skipad.R
import com.skipad.data.Prefs
import android.app.PendingIntent

/**
 * 开机自启：用户同意过的话，开机后如果无障碍没开，提醒一次。
 * （普通应用无法自己打开无障碍，只能引导）
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs.get(context)
        val settings = prefs.settings.value
        if (!settings.bootEnabled) return

        // 开机先把悬浮窗保活拉起来（前台服务 + 小圆点）
        runCatching {
            if (settings.floatingEnabled && settings.forceKeepAlive &&
                android.provider.Settings.canDrawOverlays(context)
            ) {
                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    Intent(context, FloatingService::class.java),
                )
            }
        }

        if (ServiceStatus.isEnabled(context)) return

        runCatching {
            KeepAlive.ensureChannel(context)
            val pi = PendingIntent.getActivity(
                context,
                1,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, "skip_running")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("跳过需要重新开启无障碍")
                .setContentText("系统重启后无障碍会被关掉，点这里去开启")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            context.getSystemService(android.app.NotificationManager::class.java)
                ?.notify(1002, n)
        }.onFailure { Log.w("BootReceiver", "notify failed", it) }
    }
}
