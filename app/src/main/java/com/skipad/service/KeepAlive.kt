package com.skipad.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.skipad.R
import com.skipad.ui.MainActivity

/**
 * 把无障碍服务本身提升为前台服务（常驻低优先级通知），
 * 避免小米等 ROM 在大应用启动时直接把它杀掉。
 */
object KeepAlive {

    private const val CHANNEL_ID = "skip_running"
    private const val CHANNEL_HIDDEN = "skip_running_hidden"
    private const val NOTIFICATION_ID = 1001
    private const val TAG = "KeepAlive"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "运行状态",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "保持跳过服务运行"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }
        if (nm.getNotificationChannel(CHANNEL_HIDDEN) == null) {
            // 隐藏通知：锁屏不显示内容、不出横幅、不响不震
            val hidden = NotificationChannel(
                CHANNEL_HIDDEN,
                "运行状态（隐藏）",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "不显示横幅和锁屏提示，只保留静默状态"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }
            nm.createNotificationChannel(hidden)
        }
    }

    fun notification(context: Context): android.app.Notification {
        val hidden = com.skipad.data.Prefs.get(context).settings.value.hiddenNotification
        val builder = NotificationCompat.Builder(context, if (hidden) CHANNEL_HIDDEN else CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("跳过 正在运行")
        .setContentText("开屏广告会被自动跳过")
        .setOngoing(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setVisibility(
            if (hidden) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE
        )
        .setShowWhen(false)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        return builder.build()
    }

    /** 从无障碍服务里尝试转前台；失败（系统限制）就退化成普通服务 */
    fun startForeground(service: android.app.Service) {
        try {
            ensureChannel(service)
            service.startForeground(NOTIFICATION_ID, notification(service))
        } catch (t: Throwable) {
            Log.w(TAG, "startForeground failed: ${t.javaClass.simpleName} ${t.message}")
        }
    }
}
