package com.skipad.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.widget.Toast
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.skipad.data.AppLog
import com.skipad.data.LogEntry
import com.skipad.data.Prefs
import com.skipad.data.RuleRepo
import com.skipad.engine.SkipEngine

class SkipService : AccessibilityService() {

    private lateinit var engine: SkipEngine
    private val handler = Handler(Looper.getMainLooper())
    private var pendingEvaluate: Runnable? = null

    private var lastSeenPkg: String? = null
    private var lastDumpedPkg: String? = null

    // ---- 引导点击状态（主线程访问） ----
    private var pendingText: String? = null
    private var nextText: String? = null
    private var pendingScroll = false
    private var nextScroll = false
    private var pendingUntil = 0L
    private var lastScrollAt = 0L
    private var scrollCount = 0

    private fun startGuidedClick(first: String, second: String?, scrollSecond: Boolean) {
        pendingText = first
        pendingScroll = true
        nextText = second
        nextScroll = scrollSecond
        pendingUntil = SystemClock.elapsedRealtime() + 15_000
        lastScrollAt = 0
        scrollCount = 0
        Log.i(TAG, "guided click requested: $first -> $second")
    }

    /** 特化应用：进入后 20 秒内，任何"跳出去"都拦回来 */
    private var specializedPkg: String? = null
    private var specializedAt = 0L

    private var polling = false
    private var clickedAt = 0L
    private var clickedPkg: String? = null

    /**
     * 开屏广告的画面经常是自绘/视频，不会发无障碍事件；
     * 所以进入一个新 App 后，在窗口期内主动轮询（400ms 一次）。
     * 点到之后继续盯 2.5 秒（800ms 一次），万一没点掉还能补一次。
     *
     * 「全程拦截」名单里的应用则是持续轮询（900ms 一次），直到切走或息屏。
     */
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return

            val s = Prefs.get(applicationContext).settings.value
            val pkg = engine.currentPackage()
            val isDemoPkg = pkg?.startsWith("com.skipad") == true

            // 排除名单/黑名单里的应用直接停轮询，别白跑
            if (pkg != null && !isDemoPkg && (pkg in s.excludes || pkg in s.blacklist)) {
                polling = false
                Log.i(TAG, "poll stop: blacklisted or excluded")
                return
            }

            // 后台运行关闭：不做常驻轮询（只保留事件驱动）
            if (!s.backgroundEnabled) {
                polling = false
                Log.i(TAG, "poll stop: background disabled")
                return
            }

            val blockMode = pkg != null && !isDemoPkg && pkg in s.blocked
            if (blockMode && !isScreenOn()) {
                polling = false
                Log.i(TAG, "poll stop: screen off")
                return
            }

            val clicked = runCatching { engine.evaluate(this@SkipService) }.getOrDefault(false)
            val now = SystemClock.elapsedRealtime()
            if (clicked) {
                clickedAt = now
                clickedPkg = engine.currentPackage()
                scheduleMisclickGuard()
            }

            if (blockMode) {
                // 全程模式：一直转，直到应用切走
                handler.postDelayed(this, 900)
                return
            }

            val elapsed = engine.sessionElapsed()
            val window = s.windowMs
            val verifyDone = clickedAt > 0 && now - clickedAt > 2_500

            if (verifyDone || elapsed > window) {
                polling = false
                Log.i(TAG, "poll stop clickedAt=$clickedAt elapsed=${elapsed}ms")
                return
            }
            handler.postDelayed(this, if (clickedAt > 0) 800 else 400)
        }
    }

    /** 打开 MIUI 页面后，按顺序帮用户点按钮，找不到还可以自动滚动 */
    private fun tryPendingClick(pkg: String?) {
        val want = pendingText ?: return
        val now = SystemClock.elapsedRealtime()
        if (now > pendingUntil) {
            clearPending()
            return
        }
        // MIUI 的隐私页/权限页都在 securitycenter 里，但信任设置页也行
        if (pkg == null || (pkg != "com.miui.securitycenter" && pkg != "com.android.settings")) return

        val roots = A11y.rootsFor(this, pkg)
        for (root in roots) {
            val node = findTextNode(root, want) ?: continue
            clickNodeOrCenter(node)
            Log.i(TAG, "guided click: $want")
            advancePending(now)
            return
        }

        // 没找到就滚一下（最多滚 6 次）
        if (pendingScroll && now - lastScrollAt > 700 && scrollCount < 6) {
            lastScrollAt = now
            scrollCount++
            swipeUp()
            Log.i(TAG, "guided scroll for: $want (#$scrollCount)")
        }
    }

    private fun advancePending(now: Long) {
        val next = nextText
        if (next != null) {
            pendingText = next
            pendingScroll = nextScroll
            pendingUntil = now + 15_000
            nextText = null
            nextScroll = false
            lastScrollAt = 0
            scrollCount = 0
        } else {
            clearPending()
        }
    }

    private fun clearPending() {
        pendingText = null
        nextText = null
        pendingScroll = false
        nextScroll = false
        pendingUntil = 0
        scrollCount = 0
    }

    private fun clickNodeOrCenter(node: android.view.accessibility.AccessibilityNodeInfo) {
        var cur: android.view.accessibility.AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 4) {
            if (cur.isClickable &&
                cur.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            ) return
            cur = cur.parent
            depth++
        }
        val r = android.graphics.Rect()
        node.getBoundsInScreen(r)
        if (!r.isEmpty) {
            val path = android.graphics.Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
            val g = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            dispatchGesture(g, null, null)
        }
    }

    // ---------------- 自动点击器 ----------------

    private val autoHandler = Handler(Looper.getMainLooper())
    private var autoRunnable: Runnable? = null
    private var autoRemain = 0

    /** 开始连点：points 为屏幕坐标列表，intervalMs 间隔，times<=0 表示一直点 */
    private fun startAutoClick(points: List<Pair<Int, Int>>, intervalMs: Long, times: Int) {
        if (points.isEmpty()) {
            Toast.makeText(this, "还没有点击点，先添加一个", Toast.LENGTH_SHORT).show()
            return
        }
        stopAutoClick(showToast = false)
        autoRemain = times
        autoClicking = true

        val runnable = object : Runnable {
            override fun run() {
                if (!autoClicking) return
                if (autoRemain > 0) {
                    autoRemain--
                }
                dispatchClickPoints(points)
                if (autoRemain == 0) {
                    autoClicking = false
                    Toast.makeText(this@SkipService, "连点完成", Toast.LENGTH_SHORT).show()
                    return
                }
                autoHandler.postDelayed(this, intervalMs.coerceIn(30L, 60_000L))
            }
        }
        autoRunnable = runnable
        autoHandler.postDelayed(runnable, 120L)
        Toast.makeText(this, "连点开始（${points.size} 个点，间隔 ${intervalMs}ms）", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "auto click start points=${points.size} interval=$intervalMs times=$times")
    }

    /** 一次手势里同时按下所有点（和自动点击器 App 的行为一致） */
    private fun dispatchClickPoints(points: List<Pair<Int, Int>>) {
        runCatching {
            val builder = android.accessibilityservice.GestureDescription.Builder()
            for ((x, y) in points) {
                val path = android.graphics.Path().apply { moveTo(x.toFloat(), y.toFloat()) }
                builder.addStroke(
                    android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 40)
                )
            }
            dispatchGesture(builder.build(), null, null)
        }.onFailure { Log.w(TAG, "dispatch auto click failed", it) }
    }

    private fun stopAutoClick(showToast: Boolean = true) {
        autoRunnable?.let { autoHandler.removeCallbacks(it) }
        autoRunnable = null
        val was = autoClicking
        autoClicking = false
        if (showToast && was) Toast.makeText(this, "连点已停止", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "auto click stop")
    }

    private fun swipeUp() {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val path = android.graphics.Path().apply {
            moveTo(x, dm.heightPixels * 0.75f)
            lineTo(x, dm.heightPixels * 0.35f)
        }
        val g = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(
                android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 320)
            )
            .build()
        dispatchGesture(g, null, null)
    }

    private fun findTextNode(root: android.view.accessibility.AccessibilityNodeInfo, text: String): android.view.accessibility.AccessibilityNodeInfo? {
        val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 1500) {
            val node = queue.removeFirst()
            visited++
            if (node.text?.toString()?.trim() == text) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    /** 后台运行 + 静默后台 决定用不用常驻通知 */
    private fun syncForegroundMode() {
        val s = Prefs.get(applicationContext).settings.value
        val wantForeground = s.enabled && s.backgroundEnabled && !s.silentBackground
        if (wantForeground) {
            KeepAlive.startForeground(this)
        } else {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        }
    }

    private fun isScreenOn(): Boolean = runCatching {
        getSystemService(android.os.PowerManager::class.java)?.isInteractive ?: true
    }.getOrDefault(true)

    private fun startPolling() {
        handler.removeCallbacks(pollRunnable)
        syncForegroundMode()
        polling = true
        handler.postDelayed(pollRunnable, 350)
    }

    /**
     * 误点保险：点完 1.2 秒 / 2.6 秒各查一次，如果前台跳到了别的应用
     * （广告落地页、浏览器、应用商店），立刻按返回键退回来。
     */
    private fun scheduleMisclickGuard() {
        val expected = clickedPkg ?: return
        listOf(1_200L, 2_600L).forEach { delay ->
            handler.postDelayed({
                val top = A11y.rootFor(this, null)?.packageName?.toString() ?: return@postDelayed
                if (top == expected || top == SYSTEM_UI || top == "com.android.permissioncontroller") {
                    return@postDelayed
                }
                Log.w(TAG, "misclick guard: $expected -> $top, pressing back")
                performGlobalAction(GLOBAL_ACTION_BACK)
                AppLog.add(
                    LogEntry(
                        time = System.currentTimeMillis(),
                        pkg = expected,
                        appName = "保护",
                        ruleId = "",
                        result = "误点已撤回",
                        detail = "跳转到了 $top",
                    )
                )
            }, delay)
        }
    }

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                android.content.Intent.ACTION_SCREEN_OFF -> {
                    polling = false
                    runCatching { engine.reset() }
                    Log.i(TAG, "screen off: engine reset")
                }
                android.content.Intent.ACTION_SCREEN_ON -> {
                    // 亮屏后不要延续旧会话，等真正的窗口切换再开始
                    runCatching { engine.reset() }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = applicationContext
        runCatching {
            val filter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_SCREEN_OFF)
                addAction(android.content.Intent.ACTION_SCREEN_ON)
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(screenReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
        }.onFailure { Log.w(TAG, "screen receiver failed", it) }
        engine = SkipEngine(app, RuleRepo(app), Prefs.get(app))
        AppLog.add(
            LogEntry(
                time = System.currentTimeMillis(),
                pkg = "",
                appName = "系统",
                ruleId = "",
                result = "无障碍服务已连接",
            )
        )
        Log.i(TAG, "service connected")
        instance = this
        syncForegroundMode()

        // 被 ROM 掐断后重连的情况：接管当前前台应用，抢最后几秒
        handler.postDelayed({
            runCatching { engine.recoverSession(this) }
                .onFailure { Log.w(TAG, "recover failed", it) }
            startPolling()
        }, 600)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = e.packageName?.toString()
                val cls = e.className?.toString()
                Log.i(TAG, "window pkg=$pkg cls=$cls")

                // 黑名单：完全不管（连窗口扩展、跳转拦截都不做）
                if (pkg != null && pkg in Prefs.get(this).settings.value.blacklist) {
                    Log.i(TAG, "blacklist skip: $pkg")
                    return
                }

                // 特化名单：从被保护的应用跳到别的应用，一律按返回
                val prev = lastSeenPkg
                if (pkg != null && prev != null && pkg != prev) {
                    val now = SystemClock.elapsedRealtime()
                    val withinEntry = now - specializedAt < SPECIALIZED_GUARD_MS
                    val recentClick = clickedAt > 0 && now - clickedAt < 15_000 && clickedPkg == prev
                    val suspicious = pkg in SUSPICIOUS_TARGETS
                    val shouldBlock = prev == specializedPkg && withinEntry &&
                        (recentClick || suspicious)

                    val targetIgnored = pkg in Prefs.get(applicationContext).settings.value.blacklist ||
                        pkg in Prefs.get(applicationContext).settings.value.excludes
                    if (shouldBlock && pkg !in SYSTEM_PKGS && !targetIgnored) {
                        Log.w(TAG, "jump blocked: $prev -> $pkg (recentClick=$recentClick suspicious=$suspicious)")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        // 只有还停在落地页时才补第二下，避免把用户自己的 App 也退掉
                        handler.postDelayed({
                            val top = A11y.rootFor(this, null)?.packageName?.toString()
                            if (top == pkg) performGlobalAction(GLOBAL_ACTION_BACK)
                        }, 450)
                        AppLog.add(
                            LogEntry(
                                time = System.currentTimeMillis(),
                                pkg = prev,
                                appName = "保护",
                                ruleId = "",
                                result = "已拦截跳转",
                                detail = pkg,
                            )
                        )
                    }
                }

                val extended = engine.onWindowChanged(pkg, cls)

                // 落地页保险：只有「刚点完就跳到浏览器/应用商店」才算误点。
                // 之前按类名猜（含 browser/webview 就退），会把用户正常浏览的网页也退掉。
                val settingNow = Prefs.get(applicationContext).settings.value
                val elapsedSinceClick = SystemClock.elapsedRealtime() - clickedAt
                if (pkg != null && clickedAt > 0 && elapsedSinceClick < 3_000 &&
                    pkg != clickedPkg &&
                    pkg in SUSPICIOUS_TARGETS &&
                    pkg !in settingNow.blacklist &&
                    pkg !in settingNow.excludes
                ) {
                    Log.w(TAG, "landing target detected: $pkg, pressing back")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    AppLog.add(
                        LogEntry(
                            time = System.currentTimeMillis(),
                            pkg = pkg,
                            appName = "保护",
                            ruleId = "",
                            result = "误点已撤回",
                            detail = "$pkg (跳到外部)",
                        )
                    )
                    clickedAt = 0
                }

                tryPendingClick(pkg)

                val pkgChanged = pkg != null && pkg != lastSeenPkg
                if (pkgChanged) {
                    lastSeenPkg = pkg
                    runCatching {
                        sendBroadcast(
                            android.content.Intent(FloatingService.ACTION_FOREGROUND)
                                .setPackage(packageName)
                                .putExtra(FloatingService.EXTRA_PKG, pkg),
                        )
                    }
                    maybeDump(pkg!!)
                    val specialized = Prefs.get(applicationContext).settings.value.specialized
                    if (pkg in specialized) {
                        specializedPkg = pkg
                        specializedAt = SystemClock.elapsedRealtime()
                    }
                }
                if (pkgChanged || extended) startPolling()
                schedule(260)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                tryPendingClick(e.packageName?.toString())
                schedule(60)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        stopAutoClick(showToast = false)
        runCatching { unregisterReceiver(screenReceiver) }
        if (instance === this) instance = null
        pendingEvaluate?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun schedule(delayMs: Long) {
        pendingEvaluate?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingEvaluate = null
            runCatching { engine.evaluate(this) }
                .onFailure { Log.w(TAG, "evaluate failed", it) }
        }
        pendingEvaluate = runnable
        handler.postDelayed(runnable, delayMs)
    }

    /** 调试模式：把一个 App 启动后的节点树抓下来，方便写规则 */
    private fun maybeDump(pkg: String) {
        if (!Prefs.get(applicationContext).settings.value.debugDump) return
        if (pkg == lastDumpedPkg) return
        lastDumpedPkg = pkg
        // 抓三次：开屏刚出现、2.5 秒后、4.5 秒后（低端机上广告常常是延迟渲染的）
        listOf(900L, 2500L, 4500L).forEachIndexed { index, delayMs ->
            handler.postDelayed({
                runCatching { NodeDumper.dump(this, pkg, index + 1) }
                    .onFailure { Log.w(TAG, "dump failed", it) }
            }, delayMs)
        }
    }

    companion object {
        private const val TAG = "SkipService"
        private const val SYSTEM_UI = "com.android.systemui"

        /** 这些类名看起来像"广告落地页"，点完跳进去要退回来 */
        private val LANDING_HINTS = listOf(
            "proxy", "browser", "scheme", "landing", "webviewactivity", "download",
        )

        @Volatile
        private var instance: SkipService? = null

        /** 自动点击器是否在跑 */
        @Volatile
        var autoClicking: Boolean = false
            private set

        /** 开始连点（读设置里的点） */
        fun startAutoClickFromPrefs(context: android.content.Context): Boolean {
            val svc = instance ?: return false
            val cfg = Prefs.get(context).settings.value
            if (!cfg.autoClickEnabled) {
                Toast.makeText(context, "先打开「启用连点器」总开关", Toast.LENGTH_SHORT).show()
                return false
            }
            val points = cfg.autoClickPoints.mapNotNull { p ->
                val parts = p.split(",")
                val x = parts.getOrNull(0)?.toIntOrNull()
                val y = parts.getOrNull(1)?.toIntOrNull()
                if (x != null && y != null) x to y else null
            }
            svc.startAutoClick(points, cfg.autoClickIntervalMs, cfg.autoClickTimes)
            return true
        }

        fun stopAutoClicking() {
            instance?.stopAutoClick()
        }

        /** 设置变更后重刷常驻通知（隐藏通知开关要用） */
        fun refreshNotification() {
            instance?.let { svc -> runCatching { KeepAlive.startForeground(svc) } }
        }

        /** 让无障碍服务按顺序点两个按钮（第二个找不到会自动滚动） */
        fun requestGuidedClick(first: String, second: String?, scrollSecond: Boolean = false) {
            instance?.startGuidedClick(first, second, scrollSecond)
        }

        /** 这些包名不算"跳转"（系统界面/桌面/权限弹窗） */
        private val SYSTEM_PKGS = setOf(
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.miui.home",
            "com.skipad",
        )

        /** 这些包名是"可疑跳转目标"：安装器 / 应用商店 / 浏览器，广告落地最爱去 */
        private val SUSPICIOUS_TARGETS = setOf(
            "com.miui.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.xiaomi.market",
            "com.android.vending",
            "com.android.chrome",
            "com.android.browser",
            "com.mi.globalbrowser",
            "com.quark.browser",
            "com.UCMobile",
            "com.taobao.taobao",
            "com.jingdong.app.mall",
            "com.xunmeng.pinduoduo",
        )

        private const val SPECIALIZED_GUARD_MS = 60_000L
    }
}
