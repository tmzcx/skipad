package com.skipad.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.skipad.data.AppLog
import com.skipad.data.LogEntry
import com.skipad.data.Prefs
import com.skipad.data.RuleRepo
import com.skipad.service.A11y

/**
 * 跳过引擎：维护"应用会话"，在开屏窗口期内匹配规则并点击。
 * 所有方法都在无障碍服务的主线程里被调用。
 */
class SkipEngine(
    private val context: Context,
    private val repo: RuleRepo,
    private val prefs: Prefs,
) {

    private var sessionPkg: String? = null
    private var sessionClass: String? = null
    private var sessionStart = 0L
    private var sessionFirstStart = 0L
    private var extensions = 0
    private var noRootLogged = false
    private var sessionIsDemo = false

    private val lastClickAt = HashMap<String, Long>()
    private val attempts = HashMap<String, Int>()
    private val labelCache = HashMap<String, String>()

    private var lastEvalAt = 0L
    private var lastAnyClickAt = 0L

    /**
     * 会话级抑制：这一轮已经点过一次跳过，就不要再点第二次。
     * 原因：点完后开屏页还在做退场动画，换一条通用规则（比如穿山甲兜底）
     * 会继续匹配到残留节点再点一下，可能点到还没消失的广告素材。
     */
    private var suppressUntil = 0L

    /** 当前这个「页面」出现的时间：日志里的「耗时」按它算，才不会被上一页拖累 */
    private var windowStart = 0L

    /**
     * @return true 表示窗口被续期了（调用方应该重启轮询）
     */
    fun onWindowChanged(pkg: String?, cls: String?): Boolean {
        if (pkg.isNullOrBlank()) return false

        if (pkg != sessionPkg) {
            sessionPkg = pkg
            sessionClass = cls
            sessionStart = SystemClock.elapsedRealtime()
            sessionFirstStart = sessionStart
            extensions = 0
            attempts.clear()
            suppressUntil = 0L
            noRootLogged = false
            windowStart = sessionStart
            sessionIsDemo = false
            return true
        }

        // 记下"本会话出现过内置演示页"，后面即使 class 变成 FrameLayout 也还能认出是演示
        if (cls != null && cls.contains("DemoSplashActivity")) {
            sessionIsDemo = true
        }

        // 同一个应用内的页面切换：如果是"启动类"页面（白屏→Splash→广告页），续期窗口。
        // 低端机上闪动校园这类应用要 10 秒以上才把开屏页渲染出来，固定窗口会整场错过。
        if (cls != null && cls != sessionClass) {
            sessionClass = cls
            val now = SystemClock.elapsedRealtime()
            windowStart = now
            val withinHardCap = now - sessionFirstStart < HARD_CAP_MS
            if (isLaunchLike(cls) && extensions < MAX_EXTENSIONS && withinHardCap) {
                extensions++
                sessionStart = now
                Log.i(TAG, "window extended by $cls (#$extensions)")
                return true
            }
        }
        return false
    }

    private fun isLaunchLike(cls: String): Boolean {
        val c = cls.lowercase()
        return LAUNCH_HINTS.any { c.contains(it) }
    }

    /** 这些页面明确不是开屏：看图、相册、播放器、评论区、详情页 */
    private fun isNonSplash(cls: String): Boolean {
        val c = cls.lowercase()
        return NON_SPLASH_HINTS.any { c.contains(it) }
    }

    /** 熄屏/切走后复位，避免把旧页面当新会话继续点 */
    fun reset() {
        sessionPkg = null
        sessionClass = null
        sessionStart = 0L
        sessionFirstStart = 0L
        sessionIsDemo = false
        attempts.clear()
        suppressUntil = 0L
        lastAnyClickAt = 0L
        noRootLogged = false
        Log.i(TAG, "engine reset")
    }

    fun currentPackage(): String? = sessionPkg

    fun sessionElapsed(): Long =
        if (sessionPkg == null) Long.MAX_VALUE
        else SystemClock.elapsedRealtime() - sessionStart

    fun currentClass(): String? = sessionClass

    /**
     * 服务被 ROM 掐断后重连时调用：把当前前台应用接管为一个"只剩 graceMs 的会话"，
     * 这样即使跳过服务在开屏瞬间被解绑、随后又被系统拉起来，也还有机会补上点击。
     */
    fun recoverSession(service: AccessibilityService, graceMs: Long = 3_500L) {
        val root = A11y.rootFor(service, null) ?: return
        val pkg = root.packageName?.toString() ?: return
        val cls = root.className?.toString()

        // 只有「看起来像开屏页」才接管。
        // 否则熄屏重开后停在视频页/评论区，会被当成刚启动的会话，开始乱点。
        if (cls == null || !isLaunchLike(cls) || isNonSplash(cls)) {
            Log.i(TAG, "skip recovery: not a splash page (${cls?.substringAfterLast('.')})")
            return
        }
        if (pkg == sessionPkg) return
        sessionPkg = pkg
        sessionClass = cls
        val window = prefs.settings.value.windowMs
        sessionStart = SystemClock.elapsedRealtime() - (window - graceMs).coerceAtLeast(0)
        attempts.clear()
        Log.i(TAG, "recovered session pkg=$pkg grace=${graceMs}ms")
    }

    /** @return true 表示这次真的点到了跳过按钮 */
    fun evaluate(service: AccessibilityService): Boolean {
        val s = prefs.settings.value
        if (!s.enabled) return false

        val pkg = sessionPkg ?: return false
        val now = SystemClock.elapsedRealtime()

        // 节流：内容变化事件很密集
        if (now - lastEvalAt < 100) return false
        lastEvalAt = now

        // 全局点击冷却：一次点击后 1800ms 内不再点任何东西，避免连环点到广告落地页
        if (lastAnyClickAt != 0L && now - lastAnyClickAt < 1800) return false

        val isDemo = sessionIsDemo ||
            (pkg.startsWith("com.skipad") && sessionClass?.contains("DemoSplashActivity") == true)
        if (!isDemo && pkg in s.excludes) return false

        // 全程拦截模式：这个应用在前台时不限开屏窗口，持续找广告
        val blockMode = !isDemo && pkg in s.blocked
        if (!blockMode && now - sessionStart > s.windowMs) return false

        // 停在看图/评论/播放器这类页面时，一律不动手
        val cls = sessionClass
        if (!blockMode && !isDemo && cls != null && isNonSplash(cls)) {
            return false
        }

        // 这一轮已经点过跳过：等它退场，别再补刀（全程拦截模式除外）
        if (!blockMode && now < suppressUntil) return false

        val roots = A11y.rootsFor(service, pkg)
        if (roots.isEmpty()) {
            if (!noRootLogged) {
                noRootLogged = true
                Log.i(TAG, "no root for $pkg")
            }
            return false
        }
        val dm = context.resources.displayMetrics
        val width = dm.widthPixels
        val height = dm.heightPixels

        val candidates = repo.rulesFor(pkg)
            .asSequence()
            .filter { it.pkgMatches(pkg) }
            .filter { s.disabledRules.none { d -> d == it.id } }
            .filter { it.activityMatches(sessionClass) }
            // continuous 规则只在全程拦截模式下、且不限窗口；普通规则仍受开屏窗口约束
            .filter { r -> if (r.continuous) blockMode else now - sessionStart <= r.windowMs }
            .sortedByDescending { it.priority + if (it.pkg == pkg) 1000 else 0 }
            .toList()

        for (root in roots) {
            for (rule in candidates) {
                val tried = attempts[rule.id] ?: 0
                if (tried >= rule.maxAttempts) continue

                val lastClick = lastClickAt[rule.id] ?: 0L
                if (lastClick != 0L && now - lastClick < rule.cooldownMs) continue

                val node = Matcher.find(root, rule, width, height) ?: continue

                val ok = click(service, node)
                attempts[rule.id] = tried + 1

                if (ok) {
                    lastClickAt[rule.id] = now
                    lastAnyClickAt = now
                    if (!blockMode) suppressUntil = now + 2500
                    prefs.bumpCount()
                    AppLog.add(
                        LogEntry(
                            time = System.currentTimeMillis(),
                            pkg = pkg,
                            appName = label(pkg),
                            ruleId = rule.id,
                            result = "已跳过",
                            detail = "耗时 ${now - windowStart} ms",
                        )
                    )
                    Log.i(TAG, "clicked rule=${rule.id} pkg=$pkg cost=${now - windowStart}ms")
                    return true
                } else {
                    AppLog.add(
                        LogEntry(
                            time = System.currentTimeMillis(),
                            pkg = pkg,
                            appName = label(pkg),
                            ruleId = rule.id,
                            result = "点击失败",
                        )
                    )
                    Log.w(TAG, "click failed rule=${rule.id} pkg=$pkg")
                }
            }
        }
        return false
    }

    /**
     * 只点节点自己，或点它自己的坐标。
     *
     * 注意：**绝不能**往上找 clickable 祖先——广告 SDK 常把整个广告卡片包成一个
     * clickable 容器（例如 tobid_interstitial_click_group），点祖先等于点广告，
     * 会直接跳到广告落地页。
     */
    private fun click(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.isEnabled) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false

        val dm = context.resources.displayMetrics
        val x = rect.exactCenterX().coerceIn(2f, dm.widthPixels - 2f)
        val y = rect.exactCenterY().coerceIn(2f, dm.heightPixels - 2f)

        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 55))
            .build()

        return try {
            service.dispatchGesture(gesture, null, null)
        } catch (t: Throwable) {
            Log.w(TAG, "dispatchGesture failed", t)
            false
        }
    }

    fun label(pkg: String): String {
        labelCache[pkg]?.let { return it }
        val name = try {
            val pm = context.packageManager
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
            pm.getApplicationLabel(info).toString()
        } catch (t: Throwable) {
            pkg
        }
        labelCache[pkg] = name
        return name
    }

    companion object {
        /** 这些类名不是开屏页，一律不点 */
        private val NON_SPLASH_HINTS = listOf(
            "image", "gallery", "photo", "picture", "viewer",
            "comment", "reply", "feed", "player", "detail",
        )

        private const val TAG = "SkipEngine"

        /** 这些类名看起来像"启动/开屏/广告"页，出现时续期窗口 */
        private val LAUNCH_HINTS = listOf(
            "splash", "welcome", "launch", "loading", "adactivity", "advert", "guide",
            "interstitial", "reward", "preroll", "adpro", "ptg",
        )
        private const val MAX_EXTENSIONS = 5
        private const val HARD_CAP_MS = 45_000L
    }
}
