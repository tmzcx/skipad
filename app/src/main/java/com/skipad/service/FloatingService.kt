package com.skipad.service

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.ImageView
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.skipad.data.Prefs
import com.skipad.data.Settings
import com.skipad.ui.MainActivity

/**
 * 悬浮窗保活 + 可定制的点击动作。
 * 样式（大小/颜色/透明度/是否可点/长按关闭）和点击行为都由用户设置，
 * 全部从 Prefs 读，改完重启服务即可生效。
 */
class FloatingService : Service() {

    private var view: View? = null
    private var wm: WindowManager? = null

    // 灵动岛：附属窗口（独立于主圆点）
    // DNS 录入标记窗（独立于圆点和灵动岛）
    private var captureView: View? = null
    private var captureLp: WindowManager.LayoutParams? = null
    private var captureAdPhase = true

    /** 当前前台包名（由 SkipService 广播过来，用于游戏自动隐藏） */
    private var currentForegroundPkg: String? = null

    /** 笔记悬浮窗 */
    private var noteView: View? = null

    /** 自动点击器：点位标记层 + 悬浮控制板 */
    private var markerView: View? = null
    private var controlPanel: View? = null
    private var panelHandler: Handler? = null

    /** 自动点击器取点准星 */
    private var pickView: View? = null

    private var islandView: View? = null
    private var islandLp: WindowManager.LayoutParams? = null
    private val islandHandler = Handler(Looper.getMainLooper())
    private val islandHideRunnable = Runnable { hideIsland() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        KeepAlive.startForeground(this)
        registerForegroundReceiver()
        addOverlay()
        syncAutoClickOverlays()
        Log.i(TAG, "floating overlay started")
    
        // 服务重启后恢复笔记窗（独立模式显示笔气泡）
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching {
                val st = Prefs.get(this).settings.value
                if (st.notesFloating && st.notesIndependent && noteRoot == null) showNoteWindow()
            }
        }, 900)
}


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 设置改过后重新应用
        removeOverlay()
        addOverlay()
        syncAutoClickOverlays()
        return START_STICKY
    }

    private fun addOverlay() {
        val settings = Prefs.get(this).settings.value
        if (!settings.forceKeepAlive) {
            Log.i(TAG, "force keep-alive off, stop")
            stopSelf()
            return
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Log.w(TAG, "no overlay permission, stop")
            stopSelf()
            return
        }
        val manager = getSystemService(WindowManager::class.java) ?: return
        wm = manager

        val density = resources.displayMetrics.density
        // 隐藏模式：1 像素、全透明、不可点（游戏里自动隐藏也走这条）
        val hideNow = settings.floatingHidden ||
            (settings.floatingHideInGames && isGameForeground())

        // 下限 24dp：再小窗口只有几个像素，既看不见也点不到
        val sizeDp = if (hideNow) 1 else settings.floatingSize.coerceIn(MIN_DOT_DP, 80)
        Log.i(TAG, "overlay build hideNow=$hideNow hidden=${settings.floatingHidden} " +
            "games=${settings.floatingHideInGames} fg=$currentForegroundPkg size=$sizeDp")
        val sizePx = if (hideNow) {
            1
        } else {
            (sizeDp * density).toInt().coerceAtLeast((MIN_DOT_DP * density).toInt())
        }

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!settings.floatingClickable || hideNow) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        val lp = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // 放右侧、离屏幕边缘留出距离：左右边缘是系统返回手势区，会吃掉触摸
            gravity = Gravity.TOP or Gravity.START
            val maxX = resources.displayMetrics.widthPixels - sizePx
            val maxY = resources.displayMetrics.heightPixels - sizePx
            x = if (settings.floatingX >= 0) {
                settings.floatingX.coerceIn(0, maxX.coerceAtLeast(0))
            } else {
                (maxX - (20 * density).toInt()).coerceAtLeast(0)
            }
            y = if (settings.floatingY >= 0) {
                settings.floatingY.coerceIn(0, maxY.coerceAtLeast(0))
            } else {
                (resources.displayMetrics.heightPixels * 0.42f).toInt().coerceIn(0, maxY.coerceAtLeast(0))
            }
        }

        val color = settings.floatingColor
        val customDrawable = loadDotDrawable(settings.floatingImageUri)
        val dot: View = if (customDrawable != null) {
            ImageView(this).apply {
                setImageDrawable(customDrawable)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        } else {
            View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(withAlpha(color, (255 * 0.35f).toInt()))
                    setStroke((2 * density).toInt().coerceAtLeast(1), withAlpha(color, 220))
                }
            }
        }
        dot.apply {
            alpha = if (hideNow) 0f else settings.floatingAlpha.coerceIn(0.05f, 1f)
            // 自己处理触摸：拖动 / 长按关闭 / 单击动作，互不干扰
            var downX = 0f
            var downY = 0f
            var baseX = 0
            var baseY = 0
            var downAt = 0L
            var dragging = false
            var handled = false
            setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX
                        downY = ev.rawY
                        baseX = lp.x
                        baseY = lp.y
                        downAt = System.currentTimeMillis()
                        dragging = false
                        handled = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (ev.rawX - downX).toInt()
                        val dy = (ev.rawY - downY).toInt()
                        if (!dragging && (kotlin.math.abs(dx) > 14 || kotlin.math.abs(dy) > 14)) {
                            dragging = true
                        }
                        if (dragging) {
                            lp.x = baseX + dx
                            lp.y = baseY + dy
                            runCatching { wm?.updateViewLayout(v, lp) }
                        } else if (!handled && settings.floatingLongPressClose &&
                            System.currentTimeMillis() - downAt > 650
                        ) {
                            handled = true
                            Log.i(TAG, "long press -> stop")
                            Toast.makeText(this@FloatingService, "悬浮窗已关闭", Toast.LENGTH_SHORT).show()
                            stopSelf()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (dragging) {
                            Prefs.get(this@FloatingService).setFloatingPosition(lp.x, lp.y)
                        }
                        if (!dragging && !handled) {
                            playClickSound(settings.floatingSoundUri)
                            if (settings.islandEnabled) {
                                Log.i(TAG, "dot clicked -> island")
                                showIsland()
                            } else {
                                Log.i(TAG, "dot clicked, action=${settings.floatingClickAction}")
                                handleClick(settings)
                            }
                        }
                        true
                    }
                    else -> true
                }
            }
        }

        runCatching { manager.addView(dot, lp) }
            .onSuccess { view = dot }
            .onFailure { Log.w(TAG, "addView failed", it) }
    }

    /**
     * 悬浮窗自定义图片：GIF / 动态 WebP 也能动。
     * API 28+ 用 ImageDecoder（原生支持动图），低版本退化成第一帧。
     */
    private fun loadDotDrawable(uriString: String): android.graphics.drawable.Drawable? {
        if (uriString.isBlank()) return null
        val uri = Uri.parse(uriString)
        if (Build.VERSION.SDK_INT >= 28) {
            val animated = runCatching {
                val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                android.graphics.ImageDecoder.decodeDrawable(source)
            }.getOrNull()
            if (animated is android.graphics.drawable.AnimatedImageDrawable) {
                animated.repeatCount = android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
                runCatching { animated.start() }
                Log.i(TAG, "floating dot: animated image loaded")
                return animated
            }
            if (animated != null) {
                Log.i(TAG, "floating dot: static image via ImageDecoder")
                return animated
            }
        }
        return decodeBitmap(uriString)?.let { android.graphics.drawable.BitmapDrawable(resources, it) }
    }

    private fun decodeBitmap(uriString: String): android.graphics.Bitmap? {
        if (uriString.isBlank()) return null
        return runCatching {
            contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()
    }

    private fun playClickSound(uriString: String) {
        if (uriString.isBlank()) return
        runCatching {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(this, Uri.parse(uriString))
            player.setOnCompletionListener { it.release() }
            player.prepare()
            player.start()
            Log.i(TAG, "click sound played")
        }.onFailure { Log.w(TAG, "sound failed", it) }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun handleClick(settings: Settings) {
        when (settings.floatingClickAction) {
            Prefs.ACTION_OPEN_APP -> runCatching {
                startActivity(
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }

            Prefs.ACTION_TOGGLE_SKIP -> {
                val prefs = Prefs.get(this)
                val now = !prefs.settings.value.enabled
                prefs.setEnabled(now)
                Toast.makeText(this, if (now) "拦截已开启" else "拦截已暂停", Toast.LENGTH_SHORT).show()
            }

            Prefs.ACTION_CUSTOM_APP -> {
                val pkg = settings.floatingCustomPkg
                if (pkg.isBlank()) {
                    Toast.makeText(this, "还没选快捷应用，去「悬浮窗设置」里选一个", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = packageManager.getLaunchIntentForPackage(pkg)
                    if (intent == null) {
                        Toast.makeText(this, "这个应用没有启动入口", Toast.LENGTH_SHORT).show()
                    } else {
                        runCatching {
                            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
            }

            Prefs.ACTION_KILL_BG -> {
                val viaShizuku = com.skipad.shizuku.ShizukuBridge.status.value.ready
                if (viaShizuku) {
                    com.skipad.shizuku.ShizukuBridge.exec("am kill-all")
                    Toast.makeText(this, "已通过 Shizuku 清理后台（自己没动）", Toast.LENGTH_SHORT).show()
                } else {
                    val killed = killBackgroundApps(this)
                    Toast.makeText(
                        this,
                        "已尝试清理 $killed 个后台应用\n（没装 Shizuku 时系统可能不执行）",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }

            else -> Unit
        }
    }

    /** 清掉其它应用的后台进程，自己（前台服务）不动 */
    private fun killBackgroundApps(context: Context): Int {
        val am = context.getSystemService(ActivityManager::class.java) ?: return 0
        var count = 0
        runCatching {
            @Suppress("DEPRECATION")
            am.runningAppProcesses?.forEach { info ->
                val pkg = info.pkgList?.firstOrNull() ?: return@forEach
                if (pkg == context.packageName) return@forEach
                if (info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    return@forEach
                }
                runCatching {
                    am.killBackgroundProcesses(pkg)
                    count++
                }
            }
        }
        return count
    }

    /** 开始 DNS 录入：弹出一个红色标记窗，点一下切换阶段，长按退出 */
    private fun startDnsCapture() {
        if (captureView != null) return
        val manager = wm ?: return
        Prefs.get(this).setDnsCaptureMode(true)
        captureAdPhase = true
        com.skipad.dns.DnsCapture.start()
        // 录入必须让 DNS 代理跑起来，否则什么都看不到
        runCatching {
            startService(
                Intent(this, DnsFilterService::class.java).setAction(DnsFilterService.ACTION_START)
            )
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = 24 * resources.displayMetrics.density
                setColor(0xF0202124.toInt())
                setStroke(dp(2), 0xFFE5484D.toInt())
            }
        }
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFE5484D.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(14), dp(14))
        }
        val label = TextView(this).apply {
            text = "广告录入中"
            setTextColor(0xFFF2F4F8.toInt())
            textSize = 13f
            setPadding(dp(10), 0, 0, 0)
        }
        row.addView(dot)
        row.addView(label)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(96)
        }

        var downAt = 0L
        row.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downAt = System.currentTimeMillis()
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (System.currentTimeMillis() - downAt > 650) {
                        stopDnsCapture()
                    } else {
                        // 先把当前阶段录到的域名提交掉
                        val domains = com.skipad.dns.DnsCapture.drain()
                        val dominCount = domains.size
                        if (domains.isNotEmpty()) {
                            runCatching {
                                if (captureAdPhase) {
                                    Prefs.get(this@FloatingService).addDnsBlocked(domains)
                                } else {
                                    Prefs.get(this@FloatingService).addDnsAllowed(domains)
                                }
                            }
                        }
                        Log.i(TAG, "capture commit ${domains.size} domains, wasAd=$captureAdPhase")
                        captureAdPhase = !captureAdPhase
                        if (captureAdPhase) {
                            label.text = "广告录入中"
                            dot.background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(0xFFE5484D.toInt())
                            }
                            row.background = GradientDrawable().apply {
                                cornerRadius = 24 * resources.displayMetrics.density
                                setColor(0xF0202124.toInt())
                                setStroke(dp(2), 0xFFE5484D.toInt())
                            }
                            Toast.makeText(
                                this@FloatingService,
                                "已存入黑名单 $dominCount 条；现在切回「广告域名录入」",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            label.text = "软件域名录入"
                            dot.background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(0xFF12B76A.toInt())
                            }
                            row.background = GradientDrawable().apply {
                                cornerRadius = 24 * resources.displayMetrics.density
                                setColor(0xF0202124.toInt())
                                setStroke(dp(2), 0xFF12B76A.toInt())
                            }
                            Toast.makeText(
                                this@FloatingService,
                                "已存入黑名单 $dominCount 条；现在录「软件自身域名」（会进白名单）",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        Log.i(TAG, "capture phase ad=$captureAdPhase")
                    }
                    true
                }
                else -> true
            }
        }

        runCatching { manager.addView(row, lp) }
            .onSuccess {
                captureView = row
                captureLp = lp
                Toast.makeText(
                    this,
                    "开始录广告域名：看到广告时点一下标记窗，退出广告后再点一下录软件自身域名",
                    Toast.LENGTH_LONG,
                ).show()
            }
            .onFailure { Log.w(TAG, "capture addView failed", it) }
    }

    private fun stopDnsCapture(showToast: Boolean = true) {
        val wasActive = captureView != null || com.skipad.dns.DnsCapture.active
        // 退出前把还没提交的域名存掉
        runCatching {
            val domains = com.skipad.dns.DnsCapture.drain()
            if (domains.isNotEmpty()) {
                if (captureAdPhase) {
                    Prefs.get(this).addDnsBlocked(domains)
                } else {
                    Prefs.get(this).addDnsAllowed(domains)
                }
                Log.i(TAG, "capture exit commit ${domains.size}")
            }
        }
        com.skipad.dns.DnsCapture.stop()
        captureView?.let { v -> runCatching { wm?.removeView(v) } }
        captureView = null
        captureLp = null
        Prefs.get(this).setDnsCaptureMode(false)
        if (showToast && wasActive) {
            Toast.makeText(this, "已退出 DNS 录入", Toast.LENGTH_SHORT).show()
        }
        Log.i(TAG, "capture stopped")
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** 全屏准星取点：拖动十字到目标位置，点「确定」记录 */
    fun startPointPick() {
        if (pickView != null) return
        val manager = wm ?: return
        val dm = resources.displayMetrics
        val sw = dm.widthPixels.toFloat()
        val sh = dm.heightPixels.toFloat()

        var cx = sw / 2f
        var cy = sh / 2f

        val root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(0x66000000)
        }

        val cross = android.widget.TextView(this).apply {
            text = "＋"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 34f
            gravity = android.view.Gravity.CENTER
        }
        val info = android.widget.TextView(this).apply {
            setTextColor(0xFFE5E7EB.toInt())
            textSize = 13f
            text = "拖到要点的位置"
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setBackgroundColor(0x99000000.toInt())
        }

        val bar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(0xCC111827.toInt())
        }
        val cancel = android.widget.TextView(this).apply {
            text = "取消"
            setTextColor(0xFFF2F4F8.toInt())
            textSize = 15f
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnClickListener { stopPointPick() }
        }
        val confirm = android.widget.TextView(this).apply {
            text = "确定"
            setTextColor(0xFF7DD3FC.toInt())
            textSize = 15f
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnClickListener {
                Prefs.get(this@FloatingService).addAutoClickPoint(cx.toInt(), cy.toInt())
                syncAutoClickOverlays()
                Toast.makeText(
                    this@FloatingService,
                    "已添加点位 (${cx.toInt()}, ${cy.toInt()})",
                    Toast.LENGTH_SHORT,
                ).show()
                stopPointPick()
            }
        }
        bar.addView(cancel)
        bar.addView(confirm)

        root.addView(cross, android.widget.FrameLayout.LayoutParams(dp(56), dp(56)).apply {
            leftMargin = (cx - dp(28)).toInt()
            topMargin = (cy - dp(28)).toInt()
        })
        root.addView(info, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(40); leftMargin = dp(12) })
        root.addView(bar, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM,
        ))

        fun refresh() {
            val lp = cross.layoutParams as android.widget.FrameLayout.LayoutParams
            lp.leftMargin = (cx - dp(28)).toInt()
            lp.topMargin = (cy - dp(28)).toInt()
            cross.layoutParams = lp
            info.text = "(${cx.toInt()}, ${cy.toInt()}) · 拖到要点的位置"
        }

        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        cross.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startX = cx; startY = cy; true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    cx = (startX + ev.rawX - downX).coerceIn(0f, sw)
                    cy = (startY + ev.rawY - downY).coerceIn(0f, sh)
                    refresh()
                    true
                }
                else -> true
            }
        }
        refresh()

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START }

        val added = runCatching { manager.addView(root, lp) }.isSuccess
        if (!added) {
            Toast.makeText(this, "取点窗口创建失败（悬浮窗权限？）", Toast.LENGTH_SHORT).show()
            return
        }
        pickView = root
        root.tag = lp
        Log.i(TAG, "point pick started")
    }

    private fun stopPointPick() {
        pickView?.let { v ->
            val lp = v.tag as? WindowManager.LayoutParams
            runCatching { if (lp != null) wm?.removeViewImmediate(v) else wm?.removeView(v) }
        }
        pickView = null
        Log.i(TAG, "point pick stopped")
    }

    fun isPickingPoint(): Boolean = pickView != null

    // ---------------- 笔记悬浮窗（笔气泡 → 展开 → 内联编辑） ----------------

    private var noteRoot: View? = null
    private var noteLp: WindowManager.LayoutParams? = null
    private var noteState = 0   // 0=气泡 1=展开 2=编辑
    private var notePkg: String? = null

    fun showNoteWindow(expanded: Boolean = false) {
        noteState = if (expanded) 1 else 0
        renderNote()
    }

    /** 按当前状态重建笔记窗 */
    private fun renderNote() {
        val manager = wm ?: return
        val dm = resources.displayMetrics
        val note = com.skipad.data.NoteStore.latest(this)

        val root = android.widget.FrameLayout(this)
        val bg = android.graphics.drawable.GradientDrawable().apply {
            if (noteState == 0) {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xCCD9B44A.toInt())
                setStroke(dp(2), 0x99FFFFFF.toInt())
            } else {
                cornerRadius = dp(16).toFloat()
                setColor(note?.color ?: 0xFFFFF8E1.toInt())
                setStroke(dp(1), 0x33D9B44A)
            }
        }
        root.background = bg

        when (noteState) {
            0 -> {
                val pen = android.widget.TextView(this).apply {
                    setText("✎")
                    textSize = 22f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(0xFF3B2E0A.toInt())
                }
                root.addView(pen, android.widget.FrameLayout.LayoutParams(-1, -1))
            }
            1 -> {
                val col = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(dp(14), dp(10), dp(14), dp(12))
                }
                val head = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                val title = android.widget.TextView(this).apply {
                    setText(if (note?.title.isNullOrBlank()) "笔记" else note!!.title)
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(note?.textColor ?: 0xFF3B2E0A.toInt())
                }
                val collapse = android.widget.TextView(this).apply {
                    setText("▲")
                    textSize = 13f
                    setPadding(dp(10), 0, 0, 0)
                    setTextColor(0xFF9C6B00.toInt())
                    setOnClickListener { noteState = 0; renderNote() }
                }
                head.addView(title, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                head.addView(collapse)
                col.addView(head)

                val body = android.widget.TextView(this).apply {
                    setText(if (note == null) "还没有笔记，长按这里写一条" else note!!.content)
                    textSize = 13f
                    setTextColor(note?.textColor ?: 0xFF3B2E0A.toInt())
                    setAutoLinkMask(android.text.util.Linkify.WEB_URLS)
                    setLinkTextColor(0xFF2F6FEB.toInt())
                    setPadding(0, dp(6), 0, 0)
                }
                col.addView(body)
                root.addView(col, android.widget.FrameLayout.LayoutParams(-1, -2))
            }
            else -> {
                val col = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                val titleEdit = android.widget.EditText(this).apply {
                    setText(note?.title ?: "")
                    hint = "标题"
                    textSize = 13f
                    setSingleLine(true)
                }
                val bodyEdit = android.widget.EditText(this).apply {
                    setText(note?.content ?: "")
                    hint = "内容（支持链接）"
                    textSize = 13f
                    minLines = 3
                    maxLines = 8
                    gravity = android.view.Gravity.TOP
                }
                val bar = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.END
                }
                val cancel = android.widget.TextView(this).apply {
                    setText("取消")
                    textSize = 12f
                    setPadding(dp(8), dp(6), dp(8), dp(4))
                    setTextColor(0xFF6B7280.toInt())
                    setOnClickListener { noteState = 1; renderNote() }
                }
                val save = android.widget.TextView(this).apply {
                    setText("保存")
                    textSize = 12f
                    setPadding(dp(8), dp(6), dp(8), dp(4))
                    setTextColor(0xFF2F6FEB.toInt())
                    setOnClickListener {
                        val t = titleEdit.text.toString()
                        val c = bodyEdit.text.toString()
                        val base = com.skipad.data.NoteStore.latest(this@FloatingService)
                        val saved = com.skipad.data.Note(
                            id = base?.id ?: System.currentTimeMillis(),
                            title = t,
                            content = c,
                            color = base?.color ?: 0xFFFFF8E1.toInt(),
                            textColor = base?.textColor ?: 0xFF3B2E0A.toInt(),
                            updatedAt = System.currentTimeMillis(),
                            images = base?.images ?: emptyList(),
                        )
                        com.skipad.data.NoteStore.save(this@FloatingService, saved)
                        Toast.makeText(this@FloatingService, "已保存到笔记", Toast.LENGTH_SHORT).show()
                        noteState = 1
                        renderNote()
                    }
                }
                bar.addView(cancel)
                bar.addView(save)
                col.addView(titleEdit)
                col.addView(bodyEdit)
                col.addView(bar)
                root.addView(col, android.widget.FrameLayout.LayoutParams(-1, -2))
            }
        }

        // 尺寸
        val w = if (noteState == 0) dp(56) else (dm.widthPixels * 0.66f).toInt()
        val h = if (noteState == 0) dp(56) else WindowManager.LayoutParams.WRAP_CONTENT

        val lp = noteLp ?: WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = (dm.widthPixels * 0.80f).toInt()
            y = (dm.heightPixels * 0.18f).toInt()
        }
        lp.width = w
        lp.height = h
        noteLp = lp

        // 手势：气泡拖动/点击；面板拖动/长按编辑
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        var dragging = false
        var downAt = 0L
        root.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startX = lp.x; startY = lp.y
                    dragging = false; downAt = System.currentTimeMillis()
                    if (noteState == 1) {
                        v.postDelayed({
                            if (!dragging && noteState == 1 && System.currentTimeMillis() - downAt >= 650) {
                                noteState = 2
                                renderNote()
                            }
                        }, 660)
                    }
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) {
                        dragging = true
                        lp.x = (startX + dx).toInt()
                        lp.y = (startY + dy).toInt()
                        runCatching { wm?.updateViewLayout(v, lp) }
                    }
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!dragging && noteState == 0) {
                        noteState = 1
                        renderNote()
                    }
                }
            }
            true
        }

        if (noteRoot != null) {
            runCatching { manager.updateViewLayout(root, lp) }
            runCatching { manager.removeViewImmediate(noteRoot) }
        }
        if (runCatching { manager.addView(root, lp) }.isSuccess) noteRoot = root
        Log.i(TAG, "note window state=$noteState")
    }

    private fun refreshNoteWindow() {
        if (noteRoot != null) renderNote()
    }

    fun hideNoteWindow() {
        noteRoot?.let { v -> runCatching { wm?.removeViewImmediate(v) } }
        noteRoot = null
        noteLp = null
        noteState = 0
    }

    fun isNoteWindowShown(): Boolean = noteRoot != null

    // ---------------- 自动点击器：点位标记 + 悬浮控制板 ----------------

    /** 按设置同步标记层和控制板（点位变更、开关变更后调用） */
    fun syncAutoClickOverlays() {
        val st = Prefs.get(this).settings.value
        if (!st.autoClickEnabled) {
            removeMarkers()
            removeControlPanel()
            if (com.skipad.service.SkipService.autoClicking) {
                com.skipad.service.SkipService.stopAutoClicking()
            }
            return
        }
        val points = st.autoClickPoints.mapNotNull { p ->
            val a = p.split(",")
            val x = a.getOrNull(0)?.toIntOrNull()
            val y = a.getOrNull(1)?.toIntOrNull()
            if (x != null && y != null) x to y else null
        }
        if (st.autoClickMarkers) showMarkers(points) else removeMarkers()
        if (st.autoClickPanel) showControlPanel() else removeControlPanel()
    }

    private fun showMarkers(points: List<Pair<Int, Int>>) {
        removeMarkers()
        val manager = wm ?: return
        if (points.isEmpty()) return
        val view = MarkerView(this, points)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START }
        if (runCatching { manager.addView(view, lp) }.isSuccess) markerView = view
    }

    private fun removeMarkers() {
        markerView?.let { v -> runCatching { wm?.removeViewImmediate(v) } }
        markerView = null
    }

    private fun showControlPanel() {
        if (controlPanel != null) return
        val manager = wm ?: return
        val st = Prefs.get(this).settings.value

        val bar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xE6111827.toInt())
                setStroke(dp(1), 0x66FFFFFF)
            }
        }
        val label = android.widget.TextView(this).apply {
            text = "▶ 连点"
            setTextColor(0xFFF2F4F8.toInt())
            textSize = 14f
        }
        bar.addView(label)

        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        var lpRef: WindowManager.LayoutParams? = null

        bar.setOnTouchListener { v, ev ->
            val lp = (v.tag as? WindowManager.LayoutParams) ?: lpRef
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    startX = lp?.x?.toFloat() ?: 0f
                    startY = lp?.y?.toFloat() ?: 0f
                    dragging = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (!dragging && (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12)) dragging = true
                    if (dragging && lp != null) {
                        lp.x = (startX + dx).toInt()
                        lp.y = (startY + dy).toInt()
                        runCatching { wm?.updateViewLayout(v, lp) }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        toggleAutoClick()
                    } else if (lp != null) {
                        snapPanelToEdge(v, lp)
                        runCatching { wm?.updateViewLayout(v, lp) }
                        Prefs.get(this@FloatingService).setAutoClickPanelPos(lp.x, lp.y)
                    }
                    true
                }
                else -> true
            }
        }

        val dm = resources.displayMetrics
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = if (st.autoClickPanelX >= 0) st.autoClickPanelX else dm.widthPixels - dp(120)
            y = if (st.autoClickPanelY >= 0) st.autoClickPanelY else (dm.heightPixels * 0.25f).toInt()
        }
        bar.tag = lp
        if (runCatching { manager.addView(bar, lp) }.isFailure) return
        controlPanel = bar
        lpRef = lp
        bar.post {
            snapPanelToEdge(bar, lp)
            runCatching { wm?.updateViewLayout(bar, lp) }
            Prefs.get(this@FloatingService).setAutoClickPanelPos(lp.x, lp.y)
        }

        // 每 500ms 刷新按钮文案
        val handler = Handler(Looper.getMainLooper())
        panelHandler = handler
        val tick = object : Runnable {
            override fun run() {
                if (controlPanel == null) return
                label.text = if (com.skipad.service.SkipService.autoClicking) "■ 停止" else "▶ 连点"
                label.setTextColor(
                    if (com.skipad.service.SkipService.autoClicking) 0xFFFFB4AB.toInt() else 0xFFF2F4F8.toInt()
                )
                handler.postDelayed(this, 500)
            }
        }
        handler.post(tick)
    }

    private fun removeControlPanel() {
        panelHandler?.removeCallbacksAndMessages(null)
        panelHandler = null
        controlPanel?.let { v -> runCatching { wm?.removeViewImmediate(v) } }
        controlPanel = null
    }

    /** 把控制板贴到最近的左右边缘，纵向夹在屏幕内 */
    private fun snapPanelToEdge(bar: View, lp: WindowManager.LayoutParams) {
        val dm = resources.displayMetrics
        val w = bar.width.takeIf { it > 0 } ?: dp(120)
        val h = bar.height.takeIf { it > 0 } ?: dp(48)
        val margin = dp(6)
        val centerX = lp.x + w / 2
        lp.x = if (centerX < dm.widthPixels / 2) {
            margin
        } else {
            (dm.widthPixels - w - margin).coerceAtLeast(margin)
        }
        lp.y = lp.y.coerceIn(margin, (dm.heightPixels - h - margin).coerceAtLeast(margin))
    }

    private fun toggleAutoClick() {
        if (com.skipad.service.SkipService.autoClicking) {
            com.skipad.service.SkipService.stopAutoClicking()
        } else {
            val ok = com.skipad.service.SkipService.startAutoClickFromPrefs(this)
            if (!ok) Toast.makeText(this, "无障碍服务没连上", Toast.LENGTH_SHORT).show()
        }
    }

    /** 画点位标记：半透明圆 + 序号，和主流连点器一个样子 */
    private class MarkerView(
        context: android.content.Context,
        private val points: List<Pair<Int, Int>>,
    ) : android.view.View(context) {
        private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x552F6FEB
            style = android.graphics.Paint.Style.FILL
        }
        private val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF7DD3FC.toInt()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
        }
        private val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 34f
            textAlign = android.graphics.Paint.Align.CENTER
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            points.forEachIndexed { i, (x, y) ->
                val cx = x.toFloat()
                val cy = y.toFloat()
                canvas.drawCircle(cx, cy, 34f, fill)
                canvas.drawCircle(cx, cy, 34f, stroke)
                canvas.drawText("${i + 1}", cx, cy + 12f, text)
            }
        }
    }

    /** 当前前台是不是游戏：系统分类优先，包名表兜底 */
    private fun isGameForeground(): Boolean {
        val pkg = currentForegroundPkg ?: return false
        if (pkg in GAME_PACKAGES) return true
        return runCatching {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            ai.category == android.content.pm.ApplicationInfo.CATEGORY_GAME ||
                (ai.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0
        }.getOrDefault(false)
    }

    private val foregroundReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val pkg = intent?.getStringExtra(EXTRA_PKG)
            if (pkg == currentForegroundPkg) return
            currentForegroundPkg = pkg
            // 只在「隐藏」相关状态真的可能变化时重建，避免每次切应用都重画
            val s = Prefs.get(this@FloatingService).settings.value
            if (s.floatingHidden || s.floatingHideInGames) {
                removeOverlay()
                addOverlay()
            }
        }
    }

    private fun registerForegroundReceiver() {
        runCatching {
            val filter = android.content.IntentFilter(ACTION_FOREGROUND)
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(foregroundReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(foregroundReceiver, filter)
            }
        }.onFailure { Log.w(TAG, "foreground receiver failed", it) }
    }

    /** 弹出灵动岛面板（独立窗口，不动主圆点） */
    private fun showIsland() {
        if (islandView != null) {
            hideIsland()
            return
        }
        val manager = wm ?: return
        val settings = Prefs.get(this).settings.value
        val density = resources.displayMetrics.density

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = 26 * density
                setColor(0xF01E2024.toInt())
                setStroke(dp(1), 0x33FFFFFF)
            }
        }

        val items = settings.islandItems
        val actions = mutableListOf<Pair<String, () -> Unit>>()

        fun openMain() {
            runCatching {
                startActivity(
                    Intent(this@FloatingService, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        if (Prefs.ISLAND_OPEN in items) {
            actions += "打开" to { openMain() }
        }
        if (Prefs.ISLAND_TOGGLE in items) {
            actions += (if (settings.enabled) "暂停拦截" else "开启拦截") to {
                val prefs = Prefs.get(this@FloatingService)
                val now = !prefs.settings.value.enabled
                prefs.setEnabled(now)
                Toast.makeText(
                    this@FloatingService,
                    if (now) "拦截已开启" else "拦截已暂停",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        if (Prefs.ISLAND_KILL in items) {
            actions += "清后台" to {
                val n = killBackgroundApps(this@FloatingService)
                Toast.makeText(this@FloatingService, "已清理 $n 个后台应用", Toast.LENGTH_SHORT).show()
            }
        }
        if (Prefs.ISLAND_CUSTOM in items && settings.floatingCustomPkg.isNotBlank()) {
            val label = runCatching {
                val ai = packageManager.getApplicationInfo(settings.floatingCustomPkg, 0)
                packageManager.getApplicationLabel(ai).toString().take(4)
            }.getOrDefault("快捷")
            actions += label to {
                val it2 = packageManager.getLaunchIntentForPackage(settings.floatingCustomPkg)
                if (it2 == null) {
                    Toast.makeText(this@FloatingService, "这个应用没有启动入口", Toast.LENGTH_SHORT).show()
                } else {
                    runCatching { startActivity(it2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
        if (Prefs.ISLAND_NOTE in items) {
            actions += (if (noteRoot != null) "收笔记" else "笔记") to {
                val now = noteRoot == null
                if (now) showNoteWindow(expanded = true) else hideNoteWindow()
                // 和笔记页的总开关保持同步
                Prefs.get(this@FloatingService).setNotesFloating(now)
            }
        }
        if (Prefs.ISLAND_DNS in items) {
            actions += "DNS录入" to { startDnsCapture() }
        }
        if (Prefs.ISLAND_CLOSE in items) {
            actions += "关闭窗" to { stopSelf() }
        }
        if (actions.isEmpty()) {
            actions += "打开" to { openMain() }
        }

        actions.forEach { (label, action) ->
            val btn = TextView(this).apply {
                text = label
                setTextColor(0xFFF2F4F8.toInt())
                textSize = 13f
                setPadding(dp(12), dp(9), dp(12), dp(9))
                isClickable = true
                setOnClickListener {
                    hideIsland()
                    runCatching { action() }
                }
            }
            panel.addView(btn)
        }

        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val dotCenterX = settings.floatingX.takeIf { it >= 0 }?.plus((settings.floatingSize * density / 2).toInt())
            ?: (screenW - dp(40))
        val dotCenterY = settings.floatingY.takeIf { it >= 0 }?.plus((settings.floatingSize * density / 2).toInt())
            ?: (screenH * 0.42f).toInt()

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dotCenterX
            y = dotCenterY
        }

        panel.setOnTouchListener { _, ev ->
            if (ev.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                Log.i(TAG, "island outside touch -> hide")
                hideIsland()
                true
            } else {
                false
            }
        }

        runCatching { manager.addView(panel, lp) }
            .onSuccess {
                islandView = panel
                islandLp = lp
                panel.alpha = 0f
                panel.scaleX = 0.85f
                panel.scaleY = 0.85f
                panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start()
                // 测量后夹到屏幕内，并尽量贴着圆点
                panel.post {
                    val pw = panel.width
                    val ph = panel.height
                    if (pw > 0 && ph > 0) {
                        lp.x = (dotCenterX - pw / 2).coerceIn(dp(4), (screenW - pw - dp(4)).coerceAtLeast(dp(4)))
                        lp.y = (dotCenterY - ph / 2).coerceIn(dp(4), (screenH - ph - dp(4)).coerceAtLeast(dp(4)))
                        runCatching { manager.updateViewLayout(panel, lp) }
                    }
                }
                islandHandler.postDelayed(islandHideRunnable, 5000)
                Log.i(TAG, "island shown")
            }
            .onFailure { Log.w(TAG, "island addView failed", it) }
    }

    private fun hideIsland() {
        islandHandler.removeCallbacks(islandHideRunnable)
        islandView?.let { v ->
            runCatching {
                v.animate().alpha(0f).setDuration(90).withEndAction {
                    runCatching { wm?.removeView(v) }
                }.start()
            }
            runCatching { wm?.removeView(v) }
        }
        islandView = null
        islandLp = null
    }

    private fun removeOverlay() {
        view?.let { v -> runCatching { wm?.removeView(v) } }
        view = null
    }

    override fun onDestroy() {
        hideNoteWindow()
        removeControlPanel()
        removeMarkers()
        stopPointPick()
        if (instance === this) instance = null
        runCatching { unregisterReceiver(foregroundReceiver) }
        stopDnsCapture(showToast = false)
        hideIsland()
        removeOverlay()
        Log.i(TAG, "floating overlay stopped")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val settings = Prefs.get(applicationContext).settings.value
        if (settings.forceKeepAlive) {
            Log.i(TAG, "task removed, schedule restart in 1s")
            runCatching {
                val restart = Intent(applicationContext, FloatingService::class.java)
                val pi = android.app.PendingIntent.getService(
                    this,
                    1,
                    restart,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val am = getSystemService(android.app.AlarmManager::class.java)
                am?.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 1000, pi)
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val TAG = "FloatingService"

        @Volatile
        private var instance: FloatingService? = null

        /** 点位/开关变化后刷新标记和控制板 */
        fun refreshAutoClickOverlays() {
            instance?.let { runCatching { it.syncAutoClickOverlays() } }
        }

        /** 总开关关掉时，把悬浮类的功能全部收起 */
        fun stopAllPanels() {
            val svc = instance ?: return
            runCatching { svc.hideNoteWindow() }
            runCatching { svc.hideIsland() }
            runCatching { svc.removeControlPanel() }
            runCatching { svc.removeMarkers() }
            runCatching { svc.stopPointPick() }
        }

        /** 显示/隐藏笔记悬浮窗 */
        fun toggleNoteWindow(): Boolean {
            val svc = instance ?: return false
            if (svc.isNoteWindowShown()) svc.hideNoteWindow() else svc.showNoteWindow()
            return true
        }

        /** 供外部直接收起笔记窗（灵动岛接管时用） */
        fun hideNoteWindowNow() {
            instance?.hideNoteWindow()
        }

        /** 从灵动岛/开关直接展开笔记 */
        fun showNoteExpanded() {
            instance?.showNoteWindow(expanded = true)
        }

        fun refreshNoteWindowIfShown() {
            instance?.let { if (it.isNoteWindowShown()) it.showNoteWindow() }
        }

        /** 打开取点准星 */
        fun pickPoint(): Boolean {
            val svc = instance ?: return false
            svc.startPointPick()
            return true
        }

        /** 设置变更后重刷常驻通知 */
        fun refreshNotification() {
            instance?.let { svc -> runCatching { KeepAlive.startForeground(svc) } }
        }

        const val ACTION_FOREGROUND = "com.skipad.FOREGROUND_CHANGED"
        const val EXTRA_PKG = "pkg"

        /** 国家/地区常见手游（系统没打游戏标签时兜底用） */
        val GAME_PACKAGES: Set<String> = setOf(
            "com.tencent.tmgp.sgame", // 王者荣耀
            "com.tencent.tmgp.pubgmhd", // 和平精英
            "com.tencent.tmgp.dfm", // 三角洲行动
            "com.tencent.lolm", // 英雄联盟手游
            "com.tencent.tmgp.wildrift", // 英雄联盟手游（国际）
            "com.riotgames.league.wildrift", // Wild Rift
            "com.tencent.jkchess", // 金铲铲之战
            "com.tencent.tmgp.cf", // 穿越火线
            "com.tencent.tmgp.speedmobile", // QQ飞车
            "com.tencent.KiHan", // 火影忍者
            "com.tencent.pao", // 天天酷跑
            "com.netease.dwrg", // 第五人格
            "com.netease.party", // 蛋仔派对
            "com.netease.onmyoji", // 阴阳师
            "com.netease.mrzh", // 明日之后
            "com.netease.g78na.gb", // 无尽的拉格朗日
            "com.netease.yysls", // 逆水寒手游
            "com.miHoYo.Yuanshen", // 原神
            "com.miHoYo.hkrpg", // 崩坏：星穹铁道
            "com.miHoYo.Nap", // 绝区零
            "com.miHoYo.bh3", // 崩坏 3
            "com.miHoYo.cloudgames.hkrpg", // 云星铁
            "com.miHoYo.cloudgames.Nap", // 云绝区零
            "com.hypergryph.arknights", // 明日方舟
            "com.hypergryph.endfield", // 明日方舟：终末地
            "com.kurogame.kjq", // 鸣潮
            "com.kurogame.pgr", // 战双帕弥什
            "com.gaijingames.wtm", // 战争雷霆手游
            "com.minitech.miniworld", // 迷你世界
            "com.mojang.minecraftpe", // 我的世界
            "com.ztgame.bob", // 球球大作战
            "com.supercell.clashofclans", // 部落冲突
            "com.supercell.clashroyale", // 皇室战争
            "com.supercell.brawlstars", // 荒野乱斗
            "com.supercell.boombeach", // 海岛奇兵
            "com.playdigious.deadcells.epic", // 死亡细胞
            "com.playdigious.deadcells", // 死亡细胞（国际）
            "com.recloak.soulknight", // 元气骑士
            "com.and.games505.TerrariaPaid", // 泰拉瑞亚
            "com.xd.roco", // 洛克王国
            "com.tencent.ig", // PUBG Mobile
            "com.pubg.imobile", // BGMI
            "com.dts.freefireth", // Free Fire
            "com.dts.freefiremax", // Free Fire MAX
            "com.activision.callofduty.shooter", // COD Mobile
            "com.garena.game.codm", // CODM（Garena）
            "com.mobile.legends", // Mobile Legends
            "com.garena.game.kgtw", // 传说对决
            "com.miHoYo.GenshinImpact", // 原神（国际）
            "com.HoYoverse.hkrpgoversea", // 星铁（国际）
            "com.roblox.client", // Roblox
            "com.innersloth.spacemafia", // Among Us
            "com.gameloft.android.ANMP.GloftA9HM", // 狂野飙车 9
            "com.gameloft.android.ANMP.GloftA8HM", // 狂野飙车 8
            "com.ea.gp.fifamobile", // FIFA Mobile
            "com.konami.pesam", // eFootball
            "com.epicgames.fortnite", // Fortnite
            "com.tencent.tmgp.cod", // 使命召唤手游（国服）
        )

        /** 悬浮窗最小可用尺寸（dp） */
        const val MIN_DOT_DP = 24
    }
}
