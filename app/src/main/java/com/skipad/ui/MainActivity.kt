package com.skipad.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Surface
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import com.skipad.shizuku.ShizukuBridge
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skipad.data.AppLog
import com.skipad.data.Prefs
import com.skipad.data.RuleRepo
import com.skipad.service.ServiceStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        restoreFloatingIfNeeded()
        setContent {
            SkipAdTheme {
                SkipAdApp()
            }
        }
    }

    private fun restoreFloatingIfNeeded() {
        runCatching {
            val st = Prefs.get(this).settings.value
            val enabled = st.floatingEnabled && st.forceKeepAlive
            if (enabled && Settings.canDrawOverlays(this)) {
                androidx.core.content.ContextCompat.startForegroundService(
                    this,
                    Intent(this, com.skipad.service.FloatingService::class.java),
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching { notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
}

@Composable
fun SkipAdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2F6FEB),
            secondary = Color(0xFF4A5568),
        ),
        content = content,
    )
}

private val TABS = listOf("首页", "特化", "规则", "日志", "其他", "关于")

@Composable
fun SkipAdApp() {
    var tab by remember { mutableIntStateOf(0) }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val appPrefs = remember { Prefs.get(appContext) }
    val appSettings by appPrefs.settings.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Text(title.take(1)) },
                        label = { Text(title) },
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!com.skipad.data.DeviceEnv.isMiui && appSettings.unsupportedBanner) {
                UnsupportedBanner()
            }
            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    0 -> HomeScreen()
                    1 -> SpecializeScreen()
                    2 -> RulesScreen()
                    3 -> LogsScreen()
                    4 -> OthersScreen()
                    else -> AboutScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnsupportedBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF1E0))
            .padding(vertical = 6.dp, horizontal = 12.dp),
    ) {
        Text(
            "本机不是小米 / 红米系统（" + com.skipad.data.DeviceEnv.brandLabel +
                "）：部分功能与此系统不适配，请谅解",
            fontSize = 12.sp,
            color = Color(0xFF9C4A1B),
            maxLines = 1,
            modifier = Modifier.basicMarquee(),
        )
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()

    var serviceOn by remember { mutableStateOf(ServiceStatus.isEnabled(context)) }
    val shizuku by ShizukuBridge.status.collectAsState()
    val scope = rememberCoroutineScope()
    var healing by remember { mutableStateOf(false) }
    var showA11yGuide by remember { mutableStateOf(false) }
    var showBootDialog by remember { mutableStateOf(false) }
    var showShizukuInfo by remember { mutableStateOf(false) }
    var showBlacklist by remember { mutableStateOf(false) }
    var showFloatSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            serviceOn = ServiceStatus.isEnabled(context)
            delay(1000)
        }
    }

    // 没开无障碍 → 每次打开都弹引导
    LaunchedEffect(Unit) {
        if (!ServiceStatus.isEnabled(context)) showA11yGuide = true
        runCatching { ShizukuBridge.init() }
    }

    // 首次启动 → 问一次开机自启
    LaunchedEffect(Unit) {
        if (!settings.bootAsked && ServiceStatus.isEnabled(context)) showBootDialog = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (serviceOn) Color(0xFFE8F1FF) else Color(0xFFFFF1E8),
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = if (serviceOn) "服务运行中" else "服务未开启",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (serviceOn) Color(0xFF1B4F9C) else Color(0xFF9C4A1B),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (serviceOn) {
                        "开屏广告出现时会自动点掉「跳过」。"
                    } else {
                        "需要先开启无障碍服务。Android 13 以上如果开关是灰的，先去应用信息里允许「受限设置」。"
                    },
                    fontSize = 13.sp,
                    color = Color(0xFF4A5568),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                ) {
                    Text(if (serviceOn) "查看无障碍设置" else "去开启服务")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (serviceOn) {
                        "开启路径：设置 → 无障碍 → 已下载的应用\n→ 跳过 → 打开开关\n" +
                            "被系统关掉后，按同样路径重新打开即可"
                    } else {
                        "开启路径：设置 → 无障碍 → 已下载的应用\n→ 跳过 → 打开开关\n" +
                            (if (com.skipad.data.DeviceEnv.isMiui) "（小米/红米在「设置 → 更多设置 → 无障碍」；找不到就用搜索）"
                 else "（各系统的无障碍入口不同，找不到就用设置里的搜索）")
                    },
                    fontSize = 11.sp,
                    color = if (serviceOn) Color(0xFF4A5568) else Color(0xFF9C4A1B),
                )
                if (!serviceOn && shizuku.ready) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        enabled = !healing,
                        onClick = {
                            healing = true
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    ShizukuBridge.enableAccessibility(
                                        context.packageName,
                                        com.skipad.service.SkipService::class.java.name,
                                    )
                                }
                                healing = false
                                android.widget.Toast.makeText(
                                    context,
                                    if (r.contains(context.packageName)) "已恢复，稍等两秒看看状态" else "恢复失败：$r",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                    ) { Text(if (healing) "恢复中…" else "用 Shizuku 一键恢复无障碍") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        TipCard()

        Spacer(Modifier.height(12.dp))

        SensorPermissionCard()

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("总开关", fontWeight = FontWeight.SemiBold)
                    Text(
                        "关掉后即使服务开着也不会点击",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { on ->
                        prefs.setEnabled(on)
                        if (!on) {
                            // 总开关关闭：跳过、连点、DNS、笔记、悬浮面板全部停下
                            runCatching { com.skipad.service.SkipService.stopAutoClicking() }
                            runCatching {
                                context.startService(
                                    android.content.Intent(context, com.skipad.service.DnsFilterService::class.java)
                                        .setAction(com.skipad.service.DnsFilterService.ACTION_STOP),
                                )
                            }
                            runCatching { com.skipad.service.FloatingService.stopAllPanels() }
                            android.widget.Toast.makeText(context, "已暂停所有功能", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("后台运行", fontWeight = FontWeight.SemiBold)
                    Text("关掉后不做后台轮询和保活，只在收到界面事件时响应", fontSize = 12.sp, color = Color(0xFF6B7280))
                }
                Switch(
                    checked = settings.backgroundEnabled,
                    onCheckedChange = { prefs.setBackgroundEnabled(it) },
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("静默后台（无通知）", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (settings.floatingEnabled) {
                            "悬浮窗保活必须用前台服务，所以这里会自动显示常驻通知"
                        } else {
                            "常驻通知很烦人；打开后靠无障碍服务本身活着，被系统清掉时没有提示"
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                    )
                }
                Switch(
                    checked = settings.silentBackground && !settings.floatingEnabled,
                    enabled = settings.backgroundEnabled && !settings.floatingEnabled,
                    onCheckedChange = { prefs.setSilentBackground(it) },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("隐藏通知", fontWeight = FontWeight.SemiBold)
                    Text(
                        "不显示横幅、不占锁屏，只在后台静默运行",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                    )
                }
                Switch(
                    checked = settings.hiddenNotification,
                    onCheckedChange = {
                        prefs.setHiddenNotification(it)
                        com.skipad.service.SkipService.refreshNotification()
                        com.skipad.service.FloatingService.refreshNotification()
                    },
                )
            }
            HorizontalDivider()
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("悬浮窗保活", fontWeight = FontWeight.SemiBold)
                    Text(
                        "显示一个小圆点，并以前台服务形态运行；从最近任务划掉后更不容易被清理",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                    )
                    TextButton(
                        onClick = { showFloatSettings = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text("设置大小 / 颜色 / 点击动作", fontSize = 12.sp)
                    }
                }
                Switch(
                    checked = settings.floatingEnabled,
                    onCheckedChange = { on ->
                        if (!on) {
                            prefs.setFloatingEnabled(false)
                            runCatching {
                                context.stopService(
                                    Intent(context, com.skipad.service.FloatingService::class.java)
                                )
                            }
                        } else if (!Settings.canDrawOverlays(context)) {
                            android.widget.Toast.makeText(
                                context,
                                "先允许「显示在其他应用上层」，回来再打开",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:" + context.packageName),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        } else {
                            android.util.Log.i(
                                "SkipAdFloat",
                                "toggle on, canDrawOverlays=" + Settings.canDrawOverlays(context),
                            )
                            prefs.setFloatingEnabled(true)
                            runCatching {
                                androidx.core.content.ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, com.skipad.service.FloatingService::class.java),
                                )
                            }.onFailure {
                                android.util.Log.w("SkipAdFloat", "startForegroundService failed", it)
                                android.widget.Toast.makeText(
                                    context,
                                    "启动悬浮窗失败：" + it.message,
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("开机自启动", fontWeight = FontWeight.SemiBold)
                    Text(
                        "重启后自动拉起保活服务；无障碍被系统关掉时会发通知提醒（安卓机制，重启必关）",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                    )
                }
                Switch(
                    checked = settings.bootEnabled,
                    onCheckedChange = { on ->
                        prefs.setBootEnabled(on)
                        if (on) {
                            android.widget.Toast.makeText(
                                context,
                                (if (com.skipad.data.DeviceEnv.isMiui) "记得在 MIUI「自启动」里也允许本应用，否则重启后拉不起来"
                                    else "记得在系统「自启动 / 后台管理」里允许本应用，否则重启后拉不起来"),
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("强制保活（总开关）", fontWeight = FontWeight.SemiBold)
                    Text(
                        "关掉后立即停掉悬浮窗、取消划掉重启，不再强留进程；不想被缠住或准备卸载时先关这个",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                    )
                }
                Switch(
                    checked = settings.forceKeepAlive,
                    onCheckedChange = { on ->
                        prefs.setForceKeepAlive(on)
                        if (!on) {
                            prefs.setFloatingEnabled(false)
                            runCatching {
                                context.stopService(
                                    Intent(context, com.skipad.service.FloatingService::class.java)
                                )
                            }
                            android.widget.Toast.makeText(
                                context,
                                "已停止强制保活",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("黑名单", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${settings.blacklist.size} 个应用：完全不找弹窗、不点击、不监控",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                    )
                }
                OutlinedButton(onClick = { showBlacklist = true }) { Text("管理") }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "今日跳过",
                value = settings.todayCount.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "累计跳过",
                value = settings.totalCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("检测窗口：${settings.windowMs / 1000} 秒", fontWeight = FontWeight.SemiBold)
                Text(
                    "应用启动后只在这个时间内找跳过按钮，越短越不容易误点，太短可能来不及。",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                )
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = settings.windowMs / 1000f,
                    onValueChange = { prefs.setWindowMs(it.toLong() * 1000) },
                    valueRange = 5f..20f,
                    steps = 14,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("保活", fontWeight = FontWeight.SemiBold)
                Text(
                    "允许「忽略电池优化」，系统才不会在你打开别的 App 时把跳过服务清掉。",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                )
                Spacer(Modifier.height(10.dp))
                val pm = context.getSystemService(android.os.PowerManager::class.java)
                val ignoring = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:" + context.packageName),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                ) {
                    Text(if (ignoring) "已忽略电池优化" else "申请忽略电池优化")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("调试：抓取节点树", fontWeight = FontWeight.SemiBold)
                    Text(
                        "每个 App 启动后把界面控件树存到应用目录，用来写/改规则",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                    )
                }
                Switch(
                    checked = settings.debugDump,
                    onCheckedChange = { prefs.setDebugDump(it) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("还没装 Shizuku？", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showShizukuInfo = true }) { Text("详细") }
                }
                Text(
                    "「高级禁用」需要它。装好后在「特化 → 高级禁用 → 开启引导」里启动",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                )
                Spacer(Modifier.height(10.dp))
                val installed = isShizukuInstalled(context)
                Button(onClick = { installBundledShizuku(context) }) {
                    Text(if (installed) "重装 / 更新 Shizuku" else "一键安装 Shizuku（内置）")
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            openUrl(context, "https://github.com/RikkaApps/Shizuku/releases")
                        },
                    ) { Text("GitHub 页面") }
                    OutlinedButton(
                        onClick = {
                            openUrl(context, "https://ghproxy.net/https://github.com/RikkaApps/Shizuku/releases")
                        },
                    ) { Text("国内镜像") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "内置的是 v13.6.0（2.4MB），装完回本页点「刷新」。不需要联网，也不用跳浏览器",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("试一试", fontWeight = FontWeight.SemiBold)
                Text(
                    "打开一个内置的模拟开屏广告，看它会不会被自动点掉。",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(context, DemoSplashActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                ) {
                    Text("打开模拟开屏广告")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        ExperimentalCard()

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    if (com.skipad.data.DeviceEnv.isMiui) "小米 / Redmi 保活设置" else "保活建议",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                if (com.skipad.data.DeviceEnv.isMiui) {
                    Tip("应用信息 → 省电策略 → 无限制")
                    Tip("应用信息 → 自启动 → 允许")
                    Tip("最近任务里把「跳过」下拉锁定")
                    Tip("无障碍被系统关掉后，回来重新打开即可")
                } else {
                    Tip("把本应用加入系统的电池优化白名单（否则后台会被清）")
                    Tip("允许本应用后台运行 / 自启动（各系统叫法不同）")
                    Tip("最近任务里把「跳过」锁定")
                    Tip("无障碍被关掉后，回来重新打开即可")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:" + context.packageName),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                ) {
                    Text("打开本应用信息页")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showA11yGuide) {
        AlertDialog(
            onDismissRequest = { showA11yGuide = false },
            title = { Text("需要无障碍权限") },
            text = {
                Text("跳过广告靠无障碍服务读屏幕、点按钮，没它就用不了。\n\n" +
                    "开启后如果开关是灰的：应用信息 → 右上角 ⋮ → 允许受限设置")
            },
            confirmButton = {
                TextButton(onClick = {
                    showA11yGuide = false
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) {
                    Text("去开启")
                    if (!settings.bootAsked) showBootDialog = true
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showA11yGuide = false
                    if (!settings.bootAsked) showBootDialog = true
                }) { Text("稍后") }
            },
        )
    }

    if (showFloatSettings) {
        FloatingSettingsDialog(onDismiss = { showFloatSettings = false })
    }

    if (showBlacklist) {
        BlacklistDialog(onDismiss = { showBlacklist = false })
    }

    if (showShizukuInfo) {
        ShizukuInfoDialog(onDismiss = { showShizukuInfo = false })
    }

    if (showBootDialog) {
        AlertDialog(
            onDismissRequest = {
                showBootDialog = false
                prefs.setBootEnabled(false)
            },
            title = { Text("允许开机自启动？") },
            text = {
                val extra = if (com.skipad.data.DeviceEnv.isMiui) {
                    "另外建议在 MIUI 的「自启动」里也允许本应用，否则后台容易被清"
                } else {
                    "另外建议在系统的自启动 / 后台管理里允许本应用，否则后台容易被清"
                }
                Text(
                    "允许后，手机重启时会提醒你重新开启无障碍（系统重启一定会关掉无障碍，谁也绕不过）。\n\n" + extra
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBootDialog = false
                    prefs.setBootEnabled(true)
                }) { Text("允许") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBootDialog = false
                    prefs.setBootEnabled(false)
                }) { Text("取消") }
            },
        )
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun Tip(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("· ", color = Color(0xFF2F6FEB))
        Text(text, fontSize = 13.sp, color = Color(0xFF374151))
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2F6FEB))
            Text(title, fontSize = 12.sp, color = Color(0xFF6B7280))
        }
    }
}

@Composable
private fun SpecializeScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) } // 0=跳转拦截 1=全程拦截

    var apps by remember { mutableStateOf(loadLauncherApps(context)) }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.first.contains(query, true) || it.second.contains(query, true) }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("拦截名单", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode == 0) {
                    Button(onClick = { mode = 0 }) { Text("跳转拦截 ${settings.specialized.size}") }
                } else {
                    OutlinedButton(onClick = { mode = 0 }) { Text("跳转拦截 ${settings.specialized.size}") }
                }
                if (mode == 1) {
                    Button(onClick = { mode = 1 }) { Text("全程拦截 ${settings.blocked.size}") }
                } else {
                    OutlinedButton(onClick = { mode = 1 }) { Text("全程拦截 ${settings.blocked.size}") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { apps = loadLauncherApps(context) }) { Text("刷新") }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (mode == 0) {
                    "开启后，这些应用里的「跳转」会被拦下来：一旦跳到别的应用或落地页，立刻按返回。"
                } else {
                    "开启后，这个应用在前台时会被持续监控（900ms 扫一次）：出现「广告 / 摇一摇 / " +
                        "跳转详情 / 第三方内容 / 上滑了解更多」等字样，就去点它的 × / 关闭 / 跳过。\n" +
                        "只靠无障碍就能跑，不需要 Shizuku；功耗会略高，只给广告泛滥的应用开。"
                },
                fontSize = 12.sp,
                color = Color(0xFF6B7280),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索应用") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            ShizukuCard(settings.specialized)
            Spacer(Modifier.height(4.dp))
        }
        HorizontalDivider()
        }

        items(filtered, key = { it.second }) { pair ->
                val (label, pkg) = pair
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, fontWeight = FontWeight.SemiBold)
                        Text(pkg, fontSize = 11.sp, color = Color(0xFF9CA3AF))
                    }
                    Switch(
                        checked = if (mode == 0) pkg in settings.specialized else pkg in settings.blocked,
                        onCheckedChange = {
                            if (mode == 0) prefs.setSpecialized(pkg, it) else prefs.setBlocked(pkg, it)
                        },
                    )
                }
                HorizontalDivider()
            }
    }
}


@Composable
private fun ShizukuCard(specialized: Set<String>) {
    val context = LocalContext.current
    val status by ShizukuBridge.status.collectAsState()
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var showGuide by remember { mutableStateOf(false) }
    var wifiOn by remember { mutableStateOf(isWifiAdbOn(context)) }

    LaunchedEffect(Unit) { ShizukuBridge.init() }

    fun runAction(block: () -> String) {
        busy = true
        scope.launch {
            result = withContext(Dispatchers.IO) { block() }
            busy = false
            wifiOn = isWifiAdbOn(context)
            ShizukuBridge.refresh()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (status.ready) Color(0xFFE8F5EE) else Color(0xFFF3F4F6),
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("高级禁用", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        status.ready -> "已就绪"
                        status.alive -> "待授权"
                        else -> "未开启"
                    },
                    fontSize = 11.sp,
                    color = if (status.ready) Color(0xFF16794B) else Color(0xFF9C4A1B),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (settings.advancedEnabled) "启用" else "已停用",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                )
                Switch(
                    checked = settings.advancedEnabled,
                    onCheckedChange = { prefs.setAdvancedEnabled(it) },
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Text(status.message, fontSize = 12.sp, color = Color(0xFF4A5568))
            if (wifiOn) {
                Text("无线调试：已打开（有安全风险，用完记得关）", fontSize = 11.sp, color = Color(0xFF9C4A1B))
            }

            Spacer(Modifier.height(8.dp))

            if (!settings.advancedEnabled) {
                Text(
                    "已停用：不会执行任何命令，Shizuku 的状态保持不动（就绪还是就绪）。" +
                        "想用的时候把右上角开关打回来即可",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                )
            } else if (!status.ready) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showGuide = true }) { Text("开启引导") }
                    OutlinedButton(onClick = { ShizukuBridge.refresh() }) { Text("刷新") }
                    if (status.alive && !status.granted) {
                        Button(onClick = { ShizukuBridge.requestPermission() }) { Text("授权") }
                    }
                }
            } else {
                Text(
                    "对已选的 ${specialized.size} 个应用执行（命令走 Shizuku）：",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy && specialized.isNotEmpty(),
                        onClick = {
                            runAction {
                                specialized.forEach { ShizukuBridge.setAppLinks(it, false) }
                                "已禁止 ${specialized.size} 个应用的链接跳转"
                            }
                        },
                    ) { Text(if (busy) "执行中…" else "禁止链接跳转") }
                    OutlinedButton(
                        enabled = !busy && specialized.isNotEmpty(),
                        onClick = {
                            runAction {
                                specialized.forEach { ShizukuBridge.setAppLinks(it, true) }
                                "已恢复 ${specialized.size} 个应用的链接"
                            }
                        },
                    ) { Text("恢复链接") }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !busy && specialized.isNotEmpty(),
                        onClick = {
                            runAction {
                                val pm = context.packageManager
                                var total = 0
                                specialized.forEach { pkg ->
                                    ShizukuBridge.adComponents(pm, pkg).forEach { comp ->
                                        ShizukuBridge.disableComponent(comp)
                                        total++
                                    }
                                }
                                "已禁用 $total 个广告组件（重启 App 生效）"
                            }
                        },
                    ) { Text("实验：禁用广告页") }
                    OutlinedButton(
                        enabled = !busy && specialized.isNotEmpty(),
                        onClick = {
                            runAction {
                                val pm = context.packageManager
                                var total = 0
                                specialized.forEach { pkg ->
                                    ShizukuBridge.adComponents(pm, pkg).forEach { comp ->
                                        ShizukuBridge.enableComponent(comp)
                                        total++
                                    }
                                }
                                "已恢复 $total 个组件"
                            }
                        },
                    ) { Text("恢复组件") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    enabled = !busy && com.skipad.data.DeviceEnv.isMiui,
                    onClick = { runAction { closeAdvanced(context) } },
                ) {
                    Text(
                        if (com.skipad.data.DeviceEnv.isMiui) "一键关闭（无线调试 + Shizuku）"
                        else "一键关闭（仅小米 / 红米可用）",
                    )
                }
            }

            if (result.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(result, fontSize = 11.sp, color = Color(0xFF374151))
            }
        }
    }

    if (showGuide) {
        AdvancedGuideDialog(
            onDismiss = { showGuide = false },
            onResult = {
                result = it
                wifiOn = isWifiAdbOn(context)
                ShizukuBridge.refresh()
            },
        )
    }
}

@Composable
private fun AdvancedGuideDialog(onDismiss: () -> Unit, onResult: (String) -> Unit) {
    val context = LocalContext.current
    val status by ShizukuBridge.status.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("高级禁用 · 引导", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                GuideSection("它能做什么")
                Text(
                    "用 Shizuku 的 shell 权限执行 pm 命令：关掉某些 App 的「链接自动打开」，" +
                        "或者直接禁用它们内置的广告 SDK 页面。适合广告特别多、普通跳过压不住的 App。",
                    fontSize = 12.sp,
                    color = Color(0xFF374151),
                )

                Spacer(Modifier.height(10.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5))) {
                    Column(Modifier.padding(10.dp)) {
                        Text("⚠️ 无线调试的风险", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF9C4A1B))
                        Text(
                            "打开「无线调试」等于把手机的 adb 权限暴露在同一个 Wi-Fi 里：" +
                                "同网络的设备只要拿到配对码，就能读应用数据、装应用、看屏幕内容。" +
                                "只在需要时打开，用完点下面的「一键关闭」。不要在公共 Wi-Fi 下开。",
                            fontSize = 12.sp,
                            color = Color(0xFF7C3A12),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                GuideSection("三步开起来")
                GuideStep("1", "手机：开发者选项 → 无线调试 → 打开（重启后系统会自动关掉，需要再开一次）")
                GuideStep("2", "电脑：运行下面这个脚本（也可以发到电脑/其它设备上再运行）。脚本会自动找端口并启动 Shizuku")
                GuideStep("3", "回到本页点「刷新」，看到「已就绪」就能用下面的功能了")

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val msg = shareStartScript(context)
                        onResult(msg)
                    }) { Text("发送启动脚本") }

                    OutlinedButton(onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                        onResult("已打开开发者选项")
                    }) { Text("打开开发者选项") }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    onResult(closeAdvanced(context))
                }) { Text("一键关闭无线调试 + Shizuku") }

                Spacer(Modifier.height(10.dp))
                Text(
                    "当前状态：${status.message}",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                )

                Spacer(Modifier.height(12.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("我知道了") }
            }
        }
    }
}

@Composable
private fun GuideSection(title: String) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2937))
    Spacer(Modifier.height(3.dp))
}

@Composable
private fun GuideStep(index: String, text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$index. ", fontSize = 12.sp, color = Color(0xFF2F6FEB))
        Text(text, fontSize = 12.sp, color = Color(0xFF374151))
    }
}

private fun isWifiAdbOn(context: android.content.Context): Boolean = runCatching {
    android.provider.Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled") == 1
}.getOrDefault(false)

private fun shareStartScript(context: android.content.Context): String = runCatching {
    val file = File(context.cacheDir, "start-shizuku.cmd")
    context.assets.open("start-shizuku.cmd").use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "跳过 · Shizuku 启动脚本")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, "发送启动脚本").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    "已打开分享面板，选微信/QQ/邮件发到电脑即可"
}.getOrElse { "分享失败：${it.message}" }

private fun closeAdvanced(context: android.content.Context): String {
    val sb = StringBuilder()
    if (!ShizukuBridge.status.value.ready) {
        sb.append("Shizuku 未就绪，跑不了 shell 命令")
        if (isWifiAdbOn(context)) sb.append("（请到开发者选项手动关掉无线调试）")
        return sb.toString()
    }
    if (isWifiAdbOn(context)) {
        ShizukuBridge.exec("settings put global adb_wifi_enabled 0")
        sb.append("已关闭无线调试")
    } else {
        sb.append("无线调试本来就是关的")
    }
    // shell 身份可以 kill 同 uid 的 shizuku_server，直接把 Shizuku 停掉
    ShizukuBridge.exec("pkill -f shizuku_server")
    sb.append("；已停止 Shizuku")
    return sb.toString()
}

private fun loadLauncherApps(context: android.content.Context): List<Pair<String, String>> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return runCatching {
        pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
            val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
            val pkg = ai.packageName
            val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pkg)
            label to pkg
        }
    }.getOrDefault(emptyList())
        .distinctBy { it.second }
        .filter { it.second != context.packageName }
        .sortedBy { it.first.lowercase() }
}

@Composable
private fun RulesScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    val repo = remember { RuleRepo(context.applicationContext) }
    val rules = remember(settings.gkdRulesEnabled) { repo.builtinRules() }
    val gkdCount = remember(settings.gkdRulesEnabled) { repo.gkdRuleCount() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF6FF)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("GKD 社区规则包", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.gkdRulesEnabled) {
                                "已启用，共 $gkdCount 条；分类开关在「其他」页"
                            } else {
                                "已关闭，只使用内置规则"
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280),
                        )
                    }
                    Switch(
                        checked = settings.gkdRulesEnabled,
                        onCheckedChange = {
                            prefs.setGkdRulesEnabled(it)
                            repo.reload()
                        },
                    )
                }
            }
        }
        item {
            Text(
                "内置规则 ${rules.size} 条。关掉的规则不会生效。",
                fontSize = 12.sp,
                color = Color(0xFF6B7280),
            )
        }
        items(rules, key = { it.id }) { rule ->
            val enabled = rule.id !in settings.disabledRules
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.app, fontWeight = FontWeight.SemiBold)
                        Text(rule.pkg, fontSize = 11.sp, color = Color(0xFF6B7280))
                        Text(rule.summary(), fontSize = 11.sp, color = Color(0xFF9CA3AF))
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { prefs.setRuleEnabled(rule.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LogsScreen() {
    val items = AppLog.items
    var showDetail by remember { mutableStateOf(false) }
    val format = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("最近 ${items.size} 条", fontSize = 12.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(1f))
            TextButton(onClick = { showDetail = true }) { Text("详细") }
            TextButton(onClick = { AppLog.clear() }) { Text("清空") }
        }
        HorizontalDivider()
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有记录。跳过成功后会出现在这里。", fontSize = 13.sp, color = Color(0xFF9CA3AF))
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items) { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    entry.result,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (entry.result == "已跳过") Color(0xFF16794B) else Color(0xFF9C4A1B),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    entry.appName,
                                    fontSize = 13.sp,
                                    color = Color(0xFF374151),
                                )
                                Spacer(Modifier.weight(1f))
                                Text(format.format(Date(entry.time)), fontSize = 11.sp, color = Color(0xFF9CA3AF))
                            }
                            if (entry.detail.isNotBlank()) {
                                Text(entry.detail, fontSize = 11.sp, color = Color(0xFF9CA3AF))
                            }
                            if (entry.ruleId.isNotBlank()) {
                                Text("规则：${entry.ruleId}  ${entry.pkg}", fontSize = 10.sp, color = Color(0xFFD1D5DB))
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDetail) DetailedLogsDialog { showDetail = false }
}


private val PIC_ALGOS = listOf(
    Triple("番茄", 0, 1.0),      // TOMATO
    Triple("逐像素", 1, 0.666),  // PER_PIXEL
    Triple("行像素", 2, 0.666),  // ROW_PIXEL
    Triple("块", 3, 0.666),      // BLOCK
    Triple("行混淆", 4, 0.666),  // PIC_ENCRYPT_ROW
    Triple("行列混淆", 5, 0.666), // PIC_ENCRYPT_ROW_COLUMN
)

private fun runPicScramble(
    bitmap: android.graphics.Bitmap,
    algoIndex: Int,
    key: Double,
    encrypt: Boolean,
): android.graphics.Bitmap? = runCatching {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    val type = com.skipad.image.ImageScramble.ProcessType.valueOf(if (encrypt) "ENCRYPT" else "DECRYPT")
    val result = when (algoIndex) {
        0 -> com.skipad.image.TomatoScramble(pixels, w, h, key).process(type)
        1 -> com.skipad.image.PerPixelScramble(pixels, w, h, key.toString()).process(type)
        2 -> com.skipad.image.RowPixelScramble(pixels, w, h, key.toString()).process(type)
        3 -> com.skipad.image.BlockScramble(pixels, w, h, key.toString()).process(type)
        4 -> com.skipad.image.PicEncryptRowScramble(pixels, w, h, key).process(type)
        else -> com.skipad.image.PicEncryptRowColumnScramble(pixels, w, h, key).process(type)
    } ?: return null
    android.graphics.Bitmap.createBitmap(result.getPixels(), result.getWidth(), result.getHeight(), android.graphics.Bitmap.Config.ARGB_8888)
}.getOrNull()

private val NOTE_TEXT_COLORS = listOf(
    0xFF3B2E0A.toInt(), 0xFF1F2937.toInt(), 0xFF1E3A8A.toInt(),
    0xFF166534.toInt(), 0xFF7C2D12.toInt(), 0xFF6B21A8.toInt(),
)

private val NOTE_COLORS = listOf(
    0xFFFFF8E1.toInt(), 0xFFFFE4E6.toInt(), 0xFFE0F2FE.toInt(),
    0xFFE8F5E9.toInt(), 0xFFF3E8FF.toInt(), 0xFFF5F5F4.toInt(),
)

/** 把文本里的 URL 变成蓝色可点的链接 */
private fun linkified(text: String, fontSize: androidx.compose.ui.unit.TextUnit, color: Color = Color(0xFF374151)): AnnotatedString {
    return buildAnnotatedString {
        val regex = Regex("https?://[^\\s，。；、）)]+")
        var last = 0
        val match = regex.findAll(text)
        for (m in match) {
            if (m.range.first > last) {
                withStyle(SpanStyle(color = color, fontSize = fontSize)) {
                    append(text.substring(last, m.range.first))
                }
            }
            pushLink(
                LinkAnnotation.Url(
                    m.value,
                    TextLinkStyles(
                        SpanStyle(
                            color = Color(0xFF2F6FEB),
                            fontSize = fontSize,
                            textDecoration = TextDecoration.Underline,
                        )
                    ),
                )
            )
            append(m.value)
            pop()
            last = m.range.last + 1
        }
        if (last < text.length) {
            withStyle(SpanStyle(color = color, fontSize = fontSize)) {
                append(text.substring(last))
            }
        }
    }
}

/** PCL 风格的今日人品：按日期定值，同一天结果固定 */
private fun todayLuck(dateSeed: String): Int {
    var h = 1125899906842597L
    for (c in dateSeed) h = 31 * h + c.code
    val r = java.util.Random(h)
    // 偏中间分布：三次取最小，运气差的时候也常见（更像抽卡体验）
    val a = r.nextInt(101)
    val b = r.nextInt(101)
    val c = r.nextInt(101)
    return (a + b + c) / 3
}

private fun luckComment(v: Int): String = when {
    v >= 95 -> "欧皇附体，今天买彩票吧"
    v >= 85 -> "运气爆棚，适合开新坑"
    v >= 70 -> "状态不错，做事顺一点"
    v >= 50 -> "普普通通，稳着来"
    v >= 30 -> "有点背，重要操作先存档"
    v >= 10 -> "不宜折腾，喝口水休息"
    else -> "今天宜躺平，改天再战"
}

@Composable
private fun LuckDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    val today = remember {
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    }
    val value = if (settings.luckDate == today && settings.luckValue >= 0) settings.luckValue else -1
    var shown by remember { mutableIntStateOf(value) }
    var drawing by remember { mutableStateOf(false) }
    var rerolled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("今日人品", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (shown < 0) "—" else shown.toString(),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        shown < 0 -> Color(0xFF9CA3AF)
                        shown >= 85 -> Color(0xFFB8860B)
                        shown >= 50 -> Color(0xFF16794B)
                        else -> Color(0xFF9C4A1B)
                    },
                )
                Text(
                    if (shown < 0) "点下面按钮抽一次" else luckComment(shown),
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                )

                Spacer(Modifier.height(16.dp))
                if (shown < 0 || rerolled) {
                    Button(
                        enabled = !drawing,
                        onClick = {
                            drawing = true
                            scope.launch {
                                // 0.8、1.2 秒的假滚动，最后落到当天定值
                                repeat(12) { i ->
                                    shown = (0..100).random()
                                    kotlinx.coroutines.delay(60L)
                                }
                                val finalValue = if (settings.luckDate == today && settings.luckValue >= 0 && !rerolled) {
                                    settings.luckValue
                                } else {
                                    todayLuck(today + "skipad")
                                }
                                shown = finalValue
                                if (settings.luckDate != today || settings.luckValue < 0 || rerolled) {
                                    prefs.setLuck(today, finalValue)
                                }
                                drawing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD9B44A),
                            contentColor = Color(0xFF3B2E0A),
                        ),
                    ) { Text(if (drawing) "抽签中…" else "抽一次") }
                } else {
                    TextButton(onClick = {
                        rerolled = true
                        shown = -1
                    }) { Text("再抽一次（不影响今天记录）") }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "按日期定值，同一天结果固定；PCL 的今日人品也是这个思路",
                    fontSize = 10.sp,
                    color = Color(0xFF9CA3AF),
                )
            }
        }
    }
}

@Composable
private fun NotesScreen(onClose: () -> Unit, onOpenIsland: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    var notes by remember { mutableStateOf(com.skipad.data.NoteStore.all(context)) }
    var editing by remember { mutableStateOf<com.skipad.data.Note?>(null) }
    var isNew by remember { mutableStateOf(false) }

    fun reload() {
        notes = com.skipad.data.NoteStore.all(context)
        com.skipad.service.FloatingService.refreshNoteWindowIfShown()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFDFCF7)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("笔记", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("关闭") }
                }

                val note = editing
                if (note != null) {
                    // 编辑模式
                    var title by remember(note.id) { mutableStateOf(note.title) }
                    var content by remember(note.id) { mutableStateOf(note.content) }
                    var color by remember(note.id) { mutableIntStateOf(note.color) }
                    var images by remember(note.id) { mutableStateOf(note.images) }
                    var textColor by remember(note.id) { mutableIntStateOf(note.textColor) }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("内容（支持链接，自动识别）") },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as? android.content.ClipboardManager
                            val uri = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                            if (uri == null) {
                                android.widget.Toast.makeText(context, "剪贴板里没有图片", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                val bmp = runCatching {
                                    context.contentResolver.openInputStream(uri)?.use {
                                        android.graphics.BitmapFactory.decodeStream(it)
                                    }
                                }.getOrNull()
                                if (bmp == null) {
                                    android.widget.Toast.makeText(context, "读取图片失败", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    val dir = java.io.File(context.filesDir, "notes")
                                    dir.mkdirs()
                                    val f = java.io.File(dir, "img_${System.currentTimeMillis()}.png")
                                    runCatching {
                                        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, it) }
                                        images = images + f.absolutePath
                                    }.onFailure {
                                        android.widget.Toast.makeText(context, "保存图片失败", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }, border = BorderStroke(1.dp, Color(0xFF2F6FEB))) { Text("粘贴图片") }
                        Text("剪贴板里的图，直接进笔记", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                    }
                    if (images.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            images.forEach { path ->
                                Box {
                                    androidx.compose.foundation.Image(
                                        bitmap = remember(path) {
                                            android.graphics.BitmapFactory.decodeFile(path)
                                        }?.asImageBitmap() ?: androidx.compose.ui.graphics.ImageBitmap(1, 1),
                                        contentDescription = null,
                                        modifier = Modifier.height(64.dp).width(64.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    )
                                    Text(
                                        "✕",
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        modifier = Modifier
                                            .background(Color(0x99000000), CircleShape)
                                            .padding(horizontal = 4.dp)
                                            .clickable { images = images - path },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NOTE_COLORS.forEach { c ->
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .background(Color(c), CircleShape)
                                    .clickable { color = c }
                                    .then(
                                        if (c == color) Modifier.border(2.dp, Color(0xFF2F6FEB), CircleShape)
                                        else Modifier
                                    )
                            )
                        }
                    }
                    if (content.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("预览", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                        Text(
                            linkified(content, 12.sp, Color(textColor)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 80.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            com.skipad.data.NoteStore.save(
                                context,
                                com.skipad.data.Note(
                                    id = if (isNew) System.currentTimeMillis() else note.id,
                                    title = title,
                                    content = content,
                                    color = color,
                                    textColor = textColor,
                                    updatedAt = System.currentTimeMillis(),
                                    images = images,
                                ),
                            )
                            editing = null
                            isNew = false
                            reload()
                        }) { Text("保存") }
                        OutlinedButton(onClick = {
                            com.skipad.data.NoteStore.delete(context, note.id)
                            editing = null
                            isNew = false
                            reload()
                        }) { Text("删除") }
                        TextButton(onClick = { editing = null; isNew = false }) { Text("返回") }
                    }
                } else {
                    // 总页面
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("笔记悬浮窗", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("关闭时不显示任何笔记窗口", fontSize = 11.sp, color = Color(0xFF6B7280))
                        }
                        Switch(
                            checked = settings.notesFloating,
                            onCheckedChange = {
                                prefs.setNotesFloating(it)
                                if (!it) {
                                    com.skipad.service.FloatingService.toggleNoteWindow()
                                } else if (settings.notesIndependent) {
                                    com.skipad.service.FloatingService.toggleNoteWindow()
                                }
                            },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("在灵动岛里打开笔记", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (Prefs.ISLAND_NOTE in settings.islandItems)
                                    "已开启：点主悬浮窗的灵动岛 →「笔记」展开"
                                else "去灵动岛设置里勾上「笔记」，之后不再单独显示气泡",
                                fontSize = 11.sp,
                                color = Color(0xFF6B7280),
                            )
                        }
                        TextButton(onClick = {
                            prefs.setNotesFloating(false)
                            runCatching { com.skipad.service.FloatingService.hideNoteWindowNow() }
                            onOpenIsland()
                        }) { Text("去设置") }
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            editing = com.skipad.data.Note(
                                id = 0L,
                                title = "",
                                content = "",
                                color = NOTE_COLORS[0],
                                updatedAt = 0L,
                            )
                            isNew = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD9B44A),
                            contentColor = Color(0xFF3B2E0A),
                        ),
                    ) { Text("新建笔记") }

                    Spacer(Modifier.height(10.dp))
                    Text("历史笔记（点击编辑）", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (notes.isEmpty()) {
                        Text("还没有笔记", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(notes) { n ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            editing = n
                                            isNew = false
                                        },
                                    colors = CardDefaults.cardColors(containerColor = Color(n.color)),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                n.title.ifBlank { "（无标题）" },
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(n.textColor),
                                            )
                                            if (n.content.isNotBlank()) {
                                                Text(
                                                    linkified(n.content.take(60), 11.sp, Color(n.textColor)),
                                                    maxLines = 2,
                                                )
                                            }
                                            if (n.images.isNotEmpty()) {
                                                Spacer(Modifier.height(4.dp))
                                                androidx.compose.foundation.Image(
                                                    bitmap = remember(n.images.first()) {
                                                        android.graphics.BitmapFactory.decodeFile(n.images.first())
                                                    }?.asImageBitmap() ?: androidx.compose.ui.graphics.ImageBitmap(1, 1),
                                                    contentDescription = null,
                                                    modifier = Modifier.height(56.dp).width(84.dp),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                )
                                            }
                                        }
                                        TextButton(onClick = {
                                            com.skipad.data.NoteStore.delete(context, n.id)
                                            reload()
                                        }) { Text("删") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PicScrambleDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var algoIndex by remember { mutableIntStateOf(0) }
    var keyText by remember { mutableStateOf("1") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var encryptMode by remember { mutableStateOf(false) }
    var resultBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        inputUri = uri
        preview = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        }.getOrNull()
        resultBmp = null
        message = if (preview != null) "已选择 ${preview!!.width}×${preview!!.height}" else "读取图片失败"
    }

    fun save(bmp: android.graphics.Bitmap): String = runCatching {
        val name = "pic_${System.currentTimeMillis()}.png"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/跳过")
            }
        }
        val uri = context.contentResolver.insert(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ) ?: return "保存失败：无法创建文件"
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        "已保存到相册：$name"
    }.getOrElse { "保存失败：${it.message}" }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(Modifier.padding(16.dp).heightIn(max = 720.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("图片解混淆", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Text(
                    "算法来自开源项目 PicEncrypt（MIT）。混淆和解混淆用同一组算法+key，key 要一致",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            picker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        border = BorderStroke(1.dp, Color(0xFF2F6FEB)),
                    ) { Text("选择图片") }

                    // 混淆 / 解混淆 模式
                    listOf(false to "解混淆", true to "混淆").forEach { (enc, label) ->
                        val on = enc == encryptMode
                        OutlinedButton(
                            onClick = { encryptMode = enc },
                            border = BorderStroke(1.dp, if (on) Color(0xFF2F6FEB) else Color(0xFFE5E7EB)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (on) Color(0xFFEAF1FF) else Color.Transparent,
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }

                if (preview != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                message = "处理中…"
                                scope.launch {
                                    val key = keyText.toDoubleOrNull() ?: 1.0
                                    val out = withContext(kotlinx.coroutines.Dispatchers.Default) {
                                        runPicScramble(preview!!, algoIndex, key, encryptMode)
                                    }
                                    if (out == null) {
                                        message = "处理失败"
                                    } else {
                                        resultBmp = out
                                        message = save(out)
                                    }
                                    busy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F6FEB)),
                        ) { Text(if (encryptMode) "混淆并保存" else "解混淆并保存") }

                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                message = "自检中…"
                                scope.launch {
                                    val key = keyText.toDoubleOrNull() ?: 1.0
                                    val res = withContext(kotlinx.coroutines.Dispatchers.Default) {
                                        val enc = runPicScramble(preview!!, algoIndex, key, true)
                                            ?: return@withContext "自检失败：混淆返回空"
                                        val dec = runPicScramble(enc, algoIndex, key, false)
                                            ?: return@withContext "自检失败：解混淆返回空"
                                        // 比较像素
                                        val w = preview!!.width
                                        val h = preview!!.height
                                        if (dec.width != w || dec.height != h) {
                                            return@withContext "自检失败：尺寸 ${dec.width}×${dec.height} ≠ ${w}×$h"
                                        }
                                        val a = IntArray(w * h)
                                        val b = IntArray(w * h)
                                        preview!!.getPixels(a, 0, w, 0, 0, w, h)
                                        dec.getPixels(b, 0, w, 0, 0, w, h)
                                        var diff = 0
                                        for (i in a.indices) if (a[i] != b[i]) diff++
                                        if (diff == 0) "自检通过：混淆→解混淆 像素完全还原（$w×$h）"
                                        else "自检失败：$diff / ${a.size} 像素不一致"
                                    }
                                    message = res
                                    busy = false
                                }
                            },
                            border = BorderStroke(1.dp, Color(0xFF2F6FEB)),
                        ) { Text("自检") }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("算法", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                PIC_ALGOS.chunked(3).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { item ->
                            val idx = PIC_ALGOS.indexOf(item)
                            val on = idx == algoIndex
                            OutlinedButton(
                                onClick = {
                                    algoIndex = idx
                                    keyText = item.third.toString()
                                },
                                border = BorderStroke(1.dp, if (on) Color(0xFF2F6FEB) else Color(0xFFE5E7EB)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (on) Color(0xFFEAF1FF) else Color.Transparent,
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                            ) { Text(item.first, fontSize = 11.sp) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("key", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("番茄：0–1.618\n其余：0–1", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                }

                if (preview != null) {
                    Spacer(Modifier.height(10.dp))
                    Text("预览", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    androidx.compose.foundation.Image(
                        bitmap = (resultBmp ?: preview!!).asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }

                if (message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(message, fontSize = 11.sp, color = Color(0xFF4B5563))
                }
            }
        }
    }
}

@Composable
private fun MemCleanerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val am = remember { context.getSystemService(android.app.ActivityManager::class.java) }
    var availMb by remember { mutableLongStateOf(0L) }
    var totalMb by remember { mutableLongStateOf(0L) }
    var lowMem by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }

    fun readMem() {
        val info = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        availMb = info.availMem / 1024 / 1024
        totalMb = info.totalMem / 1024 / 1024
        lowMem = info.lowMemory
    }

    LaunchedEffect(Unit) {
        while (true) {
            readMem()
            kotlinx.coroutines.delay(1500)
        }
    }

    fun runCurrentAvail(): Long {
        val info = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        return info.availMem / 1024 / 1024
    }

    fun quickClean() {
        busy = true
        val before = availMb
        val n = runCatching {
            var count = 0
            @Suppress("DEPRECATION")
            am?.runningAppProcesses?.forEach { info ->
                val pkg = info.pkgList?.firstOrNull() ?: return@forEach
                if (pkg == context.packageName) return@forEach
                if (info.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    return@forEach
                }
                runCatching {
                    am?.killBackgroundProcesses(pkg)
                    count++
                }
            }
            count
        }.getOrDefault(0)
        System.gc()
        // 普通应用枚举不到别人的进程（Android 8+ 限制），没有可用目标就借 Shizuku
        var extra = ""
        if (n == 0) {
            val r = runCatching { com.skipad.shizuku.ShizukuBridge.exec("am kill-all") }.getOrDefault("")
            extra = if (r.isBlank()) {
                "系统限制：普通应用枚举不到其它进程，装 Shizuku 后可深度清理"
            } else {
                "已通过 Shizuku 结束后台进程"
            }
        }
        val after = runCurrentAvail()
        readMem()
        busy = false
        result = if (n > 0) {
            "已清理 $n 个后台进程，可用 ${before / 1024} → ${after / 1024} GB"
        } else {
            "$extra（可用 ${before / 1024} → ${after / 1024} GB）"
        }
        android.widget.Toast.makeText(context, result, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun deepClean() {
        busy = true
        val out = StringBuilder()
        Thread {
            val before = availMb
            val killAll = runCatching { com.skipad.shizuku.ShizukuBridge.exec("am kill-all") }.getOrDefault("")
            System.gc()
            Thread.sleep(600)
            val after = runCurrentAvail()
            out.append(
                if (killAll.isBlank()) "Shizuku 没连上，深度清理不可用"
                else "深度清理完成：可用 ${before / 1024} → ${after / 1024} GB"
            )
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                readMem()
                busy = false
                result = out.toString()
            }
        }.start()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("清理内存", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "可用 ${availMb / 1024} GB / 共 ${totalMb / 1024} GB",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (lowMem) Color(0xFF9C4A1B) else Color(0xFF16794B),
                )
                Text(
                    if (lowMem) "系统报告内存紧张" else "内存状态正常",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                )

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = { quickClean() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD9B44A),
                            contentColor = Color(0xFF3B2E0A),
                        ),
                    ) { Text("快速清理") }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { deepClean() },
                        border = BorderStroke(1.dp, Color(0xFFD9B44A)),
                    ) { Text("深度清理（Shizuku）") }
                }

                if (result.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(result, fontSize = 11.sp, color = Color(0xFF4B5563))
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "快速清理：结束后台进程释放内存（不动前台应用）\n深度清理：经 Shizuku 执行 am kill-all，一次性回收所有后台进程的内存",
                    fontSize = 10.sp,
                    color = Color(0xFF9CA3AF),
                )
            }
        }
    }
}

@Composable
private fun SitePickDialog(
    pick: com.skipad.data.SitePick,
    onReroll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember(pick.url) { mutableStateOf(false) }

    fun copy() {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("link", pick.url))
        copied = true
        android.widget.Toast.makeText(context, "已复制链接", android.widget.Toast.LENGTH_SHORT).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("网站推荐", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(pick.tag, fontSize = 11.sp, color = Color(0xFFB08A2E))
                }
                Spacer(Modifier.height(12.dp))
                Text(pick.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(pick.note, fontSize = 12.sp, color = Color(0xFF6B7280))
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = Color(0xFFFBF5E3),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, Color(0xFFE3CC8A)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        pick.url,
                        fontSize = 12.sp,
                        color = Color(0xFF3B2E0A),
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { copy() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD9B44A),
                            contentColor = Color(0xFF3B2E0A),
                        ),
                    ) { Text(if (copied) "已复制" else "复制链接") }
                    OutlinedButton(
                        onClick = { onReroll(); copied = false },
                        border = BorderStroke(1.dp, Color(0xFFD9B44A)),
                    ) { Text("换一条") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
private fun GhAccelDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    var dnsRunning by remember { mutableStateOf(false) }

    var hostCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        hostCount = runCatching { com.skipad.dns.DnsOverrides.load(context) }.getOrDefault(0)
        while (true) {
            dnsRunning = com.skipad.service.DnsFilterService.running
            kotlinx.coroutines.delay(1000)
        }
    }

    fun restartDns() {
        runCatching {
            context.startService(
                android.content.Intent(context, com.skipad.service.DnsFilterService::class.java)
                    .setAction(com.skipad.service.DnsFilterService.ACTION_RESTART),
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GitHub 加速", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Text(
                    "命中 GitHub 系域名时，DNS 直接回答优选 IP；证书仍是官方证书，不做中间人",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                )

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("启用加速", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.ghAccelEnabled) "已启用；DNS 通道会自动重启生效"
                            else "关闭时不干预任何域名解析",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280),
                        )
                    }
                    Switch(
                        checked = settings.ghAccelEnabled,
                        onCheckedChange = {
                            prefs.setGhAccelEnabled(it)
                            if (it) restartDns()
                            android.widget.Toast.makeText(
                                context,
                                if (it) "已开启，正在重启 DNS 通道并加载映射" else "已关闭加速",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    if (dnsRunning) "DNS 通道：运行中" else "DNS 通道：未运行（要先在首页实验性卡片里启动）",
                    fontSize = 12.sp,
                    color = if (dnsRunning) Color(0xFF16794B) else Color(0xFF9C4A1B),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "内置 $hostCount 条域名映射（GitHub520，MIT）",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { restartDns() }) { Text("重启 DNS 通道") }
                    OutlinedButton(onClick = onDismiss) { Text("好了") }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "对照验证：github.global.ssl.fastly.net 应解析到 128.121.243.106",
                    fontSize = 10.sp,
                    color = Color(0xFF9CA3AF),
                )
            }
        }
    }
}

@Composable
private fun AutoClickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    var running by remember { mutableStateOf(false) }
    var interval by remember { mutableFloatStateOf(settings.autoClickIntervalMs.toFloat()) }
    var timesText by remember { mutableStateOf(if (settings.autoClickTimes <= 0) "0" else settings.autoClickTimes.toString()) }

    LaunchedEffect(Unit) {
        while (true) {
            running = com.skipad.service.SkipService.autoClicking
            kotlinx.coroutines.delay(500)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(Modifier.padding(16.dp).heightIn(max = 700.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("自动点击器", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Text(
                    "用无障碍模拟点击，不需要额外装 App",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("启用连点器", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.autoClickEnabled) "总开关已开；不用时关掉，悬浮窗和标记都会收起"
                            else "关着时不显示标记、控制板，也不会点击",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280),
                        )
                    }
                    Switch(
                        checked = settings.autoClickEnabled,
                        onCheckedChange = {
                            prefs.setAutoClickEnabled(it)
                            com.skipad.service.FloatingService.refreshAutoClickOverlays()
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("点位（${settings.autoClickPoints.size}）", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        val ok = com.skipad.service.FloatingService.pickPoint()
                        android.widget.Toast.makeText(
                            context,
                            if (ok) "拖动十字到目标位置，点确定" else "先在首页打开「悬浮窗保活」",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }) { Text("添加点位") }
                }
                if (settings.autoClickPoints.isEmpty()) {
                    Text("还没有点位，点「添加点位」用取点准星选一个", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                } else {
                    settings.autoClickPoints.sorted().forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("($p)", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                prefs.removeAutoClickPoint(p)
                                com.skipad.service.FloatingService.refreshAutoClickOverlays()
                            }) { Text("删") }
                        }
                    }
                    TextButton(onClick = {
                        prefs.clearAutoClickPoints()
                        com.skipad.service.FloatingService.refreshAutoClickOverlays()
                    }) { Text("清空点位") }
                }

                Spacer(Modifier.height(10.dp))
                Text("间隔：${interval.toInt()} ms", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = interval,
                    onValueChange = { interval = it },
                    valueRange = 50f..2000f,
                    onValueChangeFinished = { prefs.setAutoClickInterval(interval.toLong()) },
                )

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("次数", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = timesText,
                        onValueChange = { v ->
                            timesText = v.filter { it.isDigit() }.take(5)
                            prefs.setAutoClickTimes(timesText.toIntOrNull() ?: 0)
                        },
                        singleLine = true,
                        modifier = Modifier.width(96.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("0 = 一直点", fontSize = 11.sp, color = Color(0xFF6B7280))
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("显示点位标记", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("在屏幕上画出带序号的圆点，方便确认位置", fontSize = 11.sp, color = Color(0xFF6B7280))
                    }
                    Switch(
                        checked = settings.autoClickMarkers,
                        onCheckedChange = {
                            prefs.setAutoClickMarkers(it)
                            com.skipad.service.FloatingService.refreshAutoClickOverlays()
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("悬浮控制板", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("一个可拖动的小条，在别的应用里也能开/停", fontSize = 11.sp, color = Color(0xFF6B7280))
                    }
                    Switch(
                        checked = settings.autoClickPanel,
                        onCheckedChange = {
                            prefs.setAutoClickPanel(it)
                            com.skipad.service.FloatingService.refreshAutoClickOverlays()
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (running) {
                        Button(onClick = { com.skipad.service.SkipService.stopAutoClicking() }) { Text("停止") }
                    } else {
                        Button(
                            enabled = settings.autoClickEnabled && settings.autoClickPoints.isNotEmpty(),
                            onClick = {
                                prefs.setAutoClickInterval(interval.toLong())
                                val ok = com.skipad.service.SkipService.startAutoClickFromPrefs(context)
                                android.widget.Toast.makeText(
                                    context,
                                    if (ok) "开始连点" else "无障碍服务没连上，先确认服务状态",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            },
                        ) { Text("开始连点") }
                    }
                    Text(
                        if (running) "运行中" else "未运行",
                        fontSize = 12.sp,
                        color = if (running) Color(0xFF16794B) else Color(0xFF9CA3AF),
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "一次手势同时按下所有点位（和常见自动点击器一致）；点完次数会自动停。",
                    fontSize = 10.sp,
                    color = Color(0xFF9CA3AF),
                )
            }
        }
    }
}

@Composable
private fun GuideLine(raw: String) {
    val line = raw.trimEnd()
    when {
        line.startsWith("# ") -> {
            Spacer(Modifier.height(10.dp))
            Text(line.removePrefix("# "), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        line.startsWith("## ") -> {
            Spacer(Modifier.height(14.dp))
            Text(line.removePrefix("## "), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
        }
        line.startsWith("### ") -> {
            Spacer(Modifier.height(8.dp))
            Text(line.removePrefix("### "), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        line.startsWith("- ") -> Text(
            "· ${line.removePrefix("- ")}",
            fontSize = 13.sp,
            color = Color(0xFF374151),
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
        line.startsWith("|") -> {
            // 表格按行简化显示，分隔行（|---|）跳过
            if (!line.contains("---")) {
                Text(line.replace("|", " ").trim(), fontSize = 12.sp, color = Color(0xFF4B5563))
            }
        }
        line.isBlank() -> Spacer(Modifier.height(6.dp))
        else -> Text(line, fontSize = 13.sp, color = Color(0xFF374151))
    }
}

@Composable
private fun GuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        runCatching {
            context.assets.open("guide.md").bufferedReader().use { it.readText() }
        }.getOrDefault("指南文件缺失")
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(Modifier.padding(16.dp).heightIn(max = 720.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("使用指南", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                HorizontalDivider()
                LazyColumn(
                    Modifier.heightIn(max = 560.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                ) {
                    items(text.split("\n")) { line -> GuideLine(line) }
                }
            }
        }
    }
}

@Composable
private fun DetailedLogsDialog(onDismiss: () -> Unit) {
    val items = AppLog.items
    val format = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) {
        items.toList()
    } else {
        items.filter {
            it.appName.contains(query, true) || it.pkg.contains(query, true) ||
                it.result.contains(query, true) || it.detail.contains(query, true) ||
                it.ruleId.contains(query, true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(Modifier.padding(16.dp).heightIn(max = 680.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("详细日志", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "共 ${items.size} 条，显示 ${filtered.size} 条（最多保留 200 条）",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280),
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索应用 / 包名 / 规则 / 结果") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                HorizontalDivider()
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Text("没有匹配的记录", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                    }
                } else {
                    LazyColumn(Modifier.heightIn(max = 460.dp)) {
                        items(filtered) { e ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        e.result,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (e.result.contains("跳过")) Color(0xFF16794B) else Color(0xFF9C4A1B),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(e.appName, fontSize = 12.sp, color = Color(0xFF374151))
                                    Spacer(Modifier.weight(1f))
                                    Text(format.format(Date(e.time)), fontSize = 10.sp, color = Color(0xFF9CA3AF))
                                }
                                Text(
                                    "${e.pkg}${if (e.ruleId.isNotBlank()) " · ${e.ruleId}" else ""}" +
                                        if (e.detail.isNotBlank()) " · ${e.detail}" else "",
                                    fontSize = 10.sp,
                                    color = Color(0xFF6B7280),
                                )
                            }
                            HorizontalDivider(color = Color(0xFFF1F2F4))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorPermissionCard() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    val target = (settings.blocked + settings.specialized).firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("设备动作与方向权限设置页", fontWeight = FontWeight.SemiBold, color = Color(0xFF1B4F9C))
            Spacer(Modifier.height(4.dp))
            Text(
                if (com.skipad.data.DeviceEnv.isMiui) {
                    "按「设置 → 隐私与安全 → 其他权限 → 获取设备动作与方向」的路径，" +
                        "前两步我帮你自动点，最后你在列表里选那个权限就行"
                } else {
                    "这个入口只适配小米 / 红米（HyperOS、MIUI）。当前系统是 " +
                        com.skipad.data.DeviceEnv.brandLabel +
                        "，路径可能不同，请到系统设置里手动找「传感器 / 运动与方向」相关权限"
                },
                fontSize = 12.sp,
                color = Color(0xFF4A5568),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                enabled = com.skipad.data.DeviceEnv.isMiui,
                onClick = { openSensorPermission(context) },
            ) {
                Text(if (com.skipad.data.DeviceEnv.isMiui) "打开设置页" else "当前系统不适配")
            }
        }
    }
}

@Composable
private fun TipsEditorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()

    var draft by remember(settings.tips) {
        mutableStateOf(settings.tips.joinToString("\n"))
    }
    var saved by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(Modifier.padding(16.dp).heightIn(max = 620.dp)) {
                Text("编辑小提示", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "一行一条 · 存在本机 · 当前 ${settings.tips.size} 条",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it; saved = false },
                    label = { Text("提示内容") },
                    minLines = 8,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val list = draft.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                        prefs.setTips(list)
                        saved = true
                    }) { Text("保存") }

                    OutlinedButton(onClick = {
                        prefs.resetTips()
                        draft = Prefs.DEFAULT_TIPS.joinToString("\n")
                        saved = true
                    }) { Text("恢复默认") }
                }

                if (saved) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "已保存，现在共 ${settings.tips.size} 条",
                        fontSize = 11.sp,
                        color = Color(0xFF16794B),
                    )
                }

                Spacer(Modifier.height(10.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
            }
        }
    }
}

@Composable
private fun OthersScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    val repo = remember { RuleRepo(context.applicationContext) }
    var expanded by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showAutoClicker by remember { mutableStateOf(false) }
    var showGhAccel by remember { mutableStateOf(false) }
    var showSites by remember { mutableStateOf(false) }
    var showMemClean by remember { mutableStateOf(false) }
    var showPicTool by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var openFloatFromNotes by remember { mutableStateOf(false) }
    var showLuck by remember { mutableStateOf(false) }
    var sitePick by remember { mutableStateOf<com.skipad.data.SitePick?>(null) }

    val stats = remember(settings.gkdRulesEnabled, settings.disabledCategories, refreshKey) {
        repo.categoryStats()
    }
    val gkdTotal = remember(settings.gkdRulesEnabled, refreshKey) { repo.gkdRuleCount() }
    val gkdEnabled = remember(settings.gkdRulesEnabled, settings.disabledCategories, refreshKey) {
        repo.gkdEnabledCount()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF6FF)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("社区规则包", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "收起" else "展开")
                        }
                    }
                    Text(
                        "共 $gkdTotal 条规则 · 覆盖 921 个 App · 当前生效 $gkdEnabled 条",
                        fontSize = 12.sp,
                        color = Color(0xFF4A5568),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("来源：", fontSize = 11.sp, color = Color(0xFF6B7280))
                        Text(
                            "GKD_subscription",
                            fontSize = 11.sp,
                            color = Color(0xFF2F6FEB),
                            modifier = Modifier.clickable {
                                openUrl(context, "https://github.com/Lin-arm/GKD_subscription")
                            },
                        )
                    }
                    Text(
                        "GPL-3.0，已离线转换内置",
                        fontSize = 10.sp,
                        color = Color(0xFF9CA3AF),
                    )

                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("启用规则包", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.gkdRulesEnabled,
                            onCheckedChange = {
                                prefs.setGkdRulesEnabled(it)
                                repo.reload()
                                refreshKey++
                            },
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("严格匹配", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "只点「关闭/跳过/不感兴趣」这类按钮，宁可漏点也不乱点；关掉会用上全部规则，误触明显变多",
                                fontSize = 11.sp,
                                color = Color(0xFF6B7280),
                            )
                        }
                        Switch(
                            checked = settings.gkdStrict,
                            onCheckedChange = {
                                prefs.setGkdStrict(it)
                                repo.reload(strictOverride = it)
                                refreshKey++
                            },
                        )
                    }

                    if (expanded) {
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        Text(
                            "按分类启用（默认关闭「功能类」：那是自动化操作，不是去广告）",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280),
                        )
                        Spacer(Modifier.height(6.dp))
                        stats.keys.toList().forEach { cat ->
                            val (total, enabledNow) = stats[cat] ?: (0 to 0)
                            val isOn = settings.gkdRulesEnabled && cat !in settings.disabledCategories
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(cat, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "$total 条 · 生效 $enabledNow 条",
                                        fontSize = 11.sp,
                                        color = Color(0xFF6B7280),
                                    )
                                }
                                Switch(
                                    checked = isOn,
                                    enabled = settings.gkdRulesEnabled,
                                    onCheckedChange = {
                                        prefs.setCategoryEnabled(cat, it)
                                        repo.reload()
                                        refreshKey++
                                    },
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEC)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("为什么只转换了一部分？", fontWeight = FontWeight.SemiBold, color = Color(0xFF9B3A3A))
                    Text(
                        "原订阅 4926 条里，有 1717 条是「结构性选择器」——比如「点这个布局的第 3 个子节点」。 " +
                            "我们目前只支持 文字 / 描述 / 资源 id / 区域，这类规则没有可用属性，转换过来也命不中，所以没收录。",
                        fontSize = 12.sp,
                        color = Color(0xFF4A3A1A),
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFBF5E3)),
                border = BorderStroke(1.dp, Color(0xFFD9B44A)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "百宝箱",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6B5320),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showAutoClicker = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD9B44A),
                                contentColor = Color(0xFF3B2E0A),
                            ),
                        ) {
                            Text("连点器")
                        }
                        Button(
                            onClick = { showGhAccel = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEBD79A),
                                contentColor = Color(0xFF3B2E0A),
                            ),
                        ) {
                            Text(if (settings.ghAccelEnabled) "GitHub 加速 · 开" else "GitHub 加速")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { sitePick = com.skipad.data.SitePicks.random(); showSites = true },
                            border = BorderStroke(1.dp, Color(0xFFD9B44A)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color(0xFF6B5320),
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("网站推荐")
                        }
                        OutlinedButton(
                            onClick = { showMemClean = true },
                            border = BorderStroke(1.dp, Color(0xFFD9B44A)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color(0xFF6B5320),
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("清理内存")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showPicTool = true },
                            border = BorderStroke(1.dp, Color(0xFF2F6FEB)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color(0xFF1F4FB0),
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("图片解混淆")
                        }
                        OutlinedButton(
                            onClick = { showNotes = true },
                            border = BorderStroke(1.dp, Color(0xFF2F6FEB)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color(0xFF1F4FB0),
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("笔记")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showLuck = true },
                        border = BorderStroke(1.dp, Color(0xFF2F6FEB)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFF1F4FB0),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("今日人品")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3FBF5)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("其他同类型去广告软件推荐", fontWeight = FontWeight.SemiBold)
                    Text(
                        "同类工具各有取舍，多了解几个没坏处",
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("GKD", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "开源的无障碍点击工具，规则生态最全；本应用的「社区规则包」就来自它的订阅",
                                fontSize = 11.sp,
                                color = Color(0xFF4A5568),
                            )
                        }
                        TextButton(onClick = { openUrl(context, "https://github.com/gkd-kit/gkd") }) {
                            Text("GitHub")
                        }
                    }
                    HorizontalDivider()

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("李跳跳", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "老牌开屏跳过工具，口碑很好；2023 年收到律师函后宣布无限期停更，" +
                                    "社区镜像保留了旧版与自定义规则",
                                fontSize = 11.sp,
                                color = Color(0xFF4A5568),
                            )
                        }
                        TextButton(onClick = { openUrl(context, "https://github.com/rongzhiy/LiTiaotiao") }) {
                            Text("GitHub")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "仅为致敬与方便查找，与本应用没有隶属关系；上面都是第三方页面/安装包，请自行判断是否安装",
                        fontSize = 10.sp,
                        color = Color(0xFF9CA3AF),
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF2F8)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("哔哩哔哩第三方客户端（无广告）", fontWeight = FontWeight.SemiBold)
                    Text(
                        "官方客户端广告多、还老改版；这几个是社区维护的纯净版",
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("PiliPlus", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "PiliPala 的活跃分支，Flutter 开发，功能最全（19.6k stars）",
                                fontSize = 11.sp,
                                color = Color(0xFF4A5568),
                            )
                        }
                        TextButton(onClick = {
                            openUrl(context, "https://github.com/bggRGjQaUbCoE/PiliPlus")
                        }) { Text("GitHub") }
                    }
                    HorizontalDivider()

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("BiliPai", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "原生开发、纯净可扩展，体积小（4.9k stars）",
                                fontSize = 11.sp,
                                color = Color(0xFF4A5568),
                            )
                        }
                        TextButton(onClick = {
                            openUrl(context, "https://github.com/jay3-yy/BiliPai")
                        }) { Text("GitHub") }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "都是 GPL-3.0 开源项目，与本应用无关；第三方客户端有账号风控风险，请自行判断",
                        fontSize = 10.sp,
                        color = Color(0xFF9CA3AF),
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7FF)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("DeepSeek 官方入口", fontWeight = FontWeight.SemiBold)
                    Text(
                        "顺手放一下，免得每次都要搜",
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("对话（C 端）", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "网页版聊天，手机/电脑都能用",
                                fontSize = 11.sp,
                                color = Color(0xFF4A5568),
                            )
                        }
                        TextButton(onClick = { openUrl(context, "https://chat.deepseek.com") }) {
                            Text("打开")
                        }
                    }
                    HorizontalDivider()

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("API 平台（开发者）", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "拿 API Key、看用量、充值，接自己的程序",
                                fontSize = 11.sp,
                                color = Color(0xFF4A5568),
                            )
                        }
                        TextButton(onClick = { openUrl(context, "https://platform.deepseek.com") }) {
                            Text("打开")
                        }
                    }
                }
            }
        }


    }
    if (showAutoClicker) AutoClickerDialog { showAutoClicker = false }
    if (showGhAccel) GhAccelDialog { showGhAccel = false }
    if (showSites && sitePick != null) {
        SitePickDialog(
            pick = sitePick!!,
            onReroll = { sitePick = com.skipad.data.SitePicks.random() },
            onDismiss = { showSites = false },
        )
    }
    if (showMemClean) MemCleanerDialog { showMemClean = false }
    if (showPicTool) PicScrambleDialog { showPicTool = false }
    if (openFloatFromNotes) {
        FloatingSettingsDialog(onDismiss = { openFloatFromNotes = false })
    }
    if (showNotes) {
        NotesScreen(
            onClose = { showNotes = false },
            onOpenIsland = {
                showNotes = false
                openFloatFromNotes = true
            },
        )
    }
    if (showLuck) LuckDialog { showLuck = false }
}

@Composable
private fun AboutScreen() {
    val context = LocalContext.current
    val aboutPrefs = remember { Prefs.get(context) }
    val aboutSettings by aboutPrefs.settings.collectAsState()
    var showGuide by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("跳过 1.0.0", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("基于无障碍服务的开屏广告自动跳过工具", fontSize = 13.sp, color = Color(0xFF6B7280))
        Spacer(Modifier.height(6.dp))
        Text(
            "制作人员：DeepSeek-V4.1-Flash-1017",
            fontSize = 12.sp,
            color = Color(0xFF8A6A2A),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { showGuide = true }) { Text("详细功能指南") }

        if (!com.skipad.data.DeviceEnv.isMiui) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("系统不适配提示条", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "当前系统不是小米 / 红米，顶部会滚动提示哪些功能不适配；可以关掉",
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                    )
                }
                Switch(
                    checked = aboutSettings.unsupportedBanner,
                    onCheckedChange = { aboutPrefs.setUnsupportedBanner(it) },
                )
            }
            HorizontalDivider()
        }

        Spacer(Modifier.height(16.dp))
        Text("工作原理", fontWeight = FontWeight.SemiBold)
        Text(
            "监听窗口切换事件 → 判断是否处于应用启动后的开屏窗口 → 在控件树里找「跳过 / Skip / 关闭广告」这类按钮 → " +
                "通过无障碍点击（或模拟坐标点击）点掉它。全程离线，不读取、不上传任何内容。",
            fontSize = 13.sp,
            color = Color(0xFF374151),
        )

        Spacer(Modifier.height(16.dp))
        Text("免责声明", fontWeight = FontWeight.SemiBold)
        Text(
            "本应用仅供个人学习与自用，不用于商业用途；请自行承担使用风险。它只模拟用户点击屏幕上一个本来就存在的按钮，" +
                "不破解、不注入、不修改任何其他应用。若你不接受，请卸载并停止使用。",
            fontSize = 13.sp,
            color = Color(0xFF374151),
        )

        Spacer(Modifier.height(16.dp))
        var showResetAll by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { showResetAll = true }) { Text("恢复默认设置") }
        Text(
            "会清空所有本机配置：拦截名单、黑名单、悬浮窗样式、小贴士等；无障碍开关不受影响",
            fontSize = 11.sp,
            color = Color(0xFF6B7280),
        )
        if (showResetAll) {
            AlertDialog(
                onDismissRequest = { showResetAll = false },
                title = { Text("恢复默认设置？") },
                text = { Text("所有名单、悬浮窗样式、小贴士都会被清空，且无法撤销") },
                confirmButton = {
                    TextButton(onClick = {
                        Prefs.get(context).resetAll()
                        showResetAll = false
                        android.widget.Toast.makeText(context, "已恢复默认设置", android.widget.Toast.LENGTH_SHORT).show()
                    }) { Text("确认恢复") }
                },
                dismissButton = {
                    TextButton(onClick = { showResetAll = false }) { Text("取消") }
                },
            )
        }

        Text("隐私", fontWeight = FontWeight.SemiBold)
        Text(
            "无网络权限，不联网；不收集任何信息。无障碍权限仅用于读取当前界面的控件文字以定位跳过按钮。",
            fontSize = 13.sp,
            color = Color(0xFF374151),
        )

        Spacer(Modifier.height(24.dp))
    }
    if (showGuide) GuideDialog { showGuide = false }
}
private val HOME_TIPS = listOf(
    "关掉目标应用的「获取设备动作与方向」权限，摇一摇就摇不动啦~",
    "这招比点跳过还快：摇一摇广告靠陀螺仪触发，权限一关它就没脾气",
    "本软件适用于小米 / 红米系列手机，其它品牌也能用，只是保活要自己调",
    "本软件由 deep seek V4.1 大肥鱼友情制作~",
    "MIUI 里把「跳过」的省电策略调成无限制，后台才不容易被清掉",
    "开发者选项里把动画缩放调成 0.5x，手机顺一点，跳过也更快",
    "开屏广告只在应用启动后 12 秒内找，等太久就不管了哦",
    "跳过失败的话去「日志」页看一眼，是不是规则没命中，截图发我就行",
    "「全程拦截」功耗略高，只给广告泛滥的应用开，别全开",
    "想少看广告又不装软件？设置 → 隐私保护 → 权限管理，把不常用应用的传感器权限都关掉",
    "点这张卡片可以换下一条，我知道的还多着呢~",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TipCard() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    var index by remember { mutableIntStateOf(0) }
    var showEditor by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { index = (index + 1) % (settings.tips.size.coerceAtLeast(1)) },
                onLongClick = { showEditor = true },
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E6)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("小贴士", fontWeight = FontWeight.SemiBold, color = Color(0xFF8A5A00))
                Spacer(Modifier.weight(1f))
                Text("点我换一条 · 长按可编辑 · ${index % settings.tips.size + 1}/${settings.tips.size}", fontSize = 11.sp, color = Color(0xFFB4894A))
            }
            Spacer(Modifier.height(6.dp))
            val tips = settings.tips
            Text(tips[index % tips.size], fontSize = 13.sp, color = Color(0xFF4A3A1A))
        }
    }

    if (showEditor) {
        TipsEditorDialog(onDismiss = { showEditor = false })
    }
}

/** 打开「获取设备动作与方向」相关的设置页（MIUI 组件各版本不同，逐个试） */
private fun appLabel(context: android.content.Context, pkg: String): String = runCatching {
    val ai = context.packageManager.getApplicationInfo(pkg, 0)
    context.packageManager.getApplicationLabel(ai).toString()
}.getOrDefault(pkg)

/**
 * 打开「获取设备动作与方向」相关的设置页。
 * 实测（HyperOS 3）：PermissionsEditorActivity + extra_pkgname 能打开某个应用的权限页，
 * 再点「其他权限」就能看到「获取设备动作与方向」开关；
 * OtherPermissionsActivity 直接启动会被系统拒绝，所以只能停在这一层。
 */
private fun openSensorPermission(context: android.content.Context) {
    if (!com.skipad.data.DeviceEnv.isMiui) {
        android.widget.Toast
            .makeText(context, "当前系统不是小米 / 红米，这个入口不适配", android.widget.Toast.LENGTH_SHORT)
            .show()
        return
    }
    val miui = "com.miui.securitycenter"
    val started = runCatching {
        context.startActivity(
            Intent().setComponent(
                android.content.ComponentName(miui, "com.miui.permcenter.privacycenter.PrivacySafetyActivity")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    if (started) {
        // 自动点「其他权限」，再自动滚到「获取设备动作与方向」
        com.skipad.service.SkipService.requestGuidedClick(
            first = "其他权限",
            second = "获取设备动作与方向",
            scrollSecond = true,
        )
        return
    }

    runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_PRIVACY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun isShizukuInstalled(context: android.content.Context): Boolean = runCatching {
    context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
    true
}.getOrDefault(false)

/** 把内置的 Shizuku APK 拷出来交给系统安装器 */
private fun installBundledShizuku(context: android.content.Context) {
    // 先确认有没有「安装未知应用」权限
    val canInstall = runCatching {
        context.packageManager.canRequestPackageInstalls()
    }.getOrDefault(true)

    if (!canInstall) {
        runCatching {
            context.startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:" + context.packageName),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        android.widget.Toast
            .makeText(context, "先允许「安装未知应用」，回来再点一次", android.widget.Toast.LENGTH_LONG)
            .show()
        return
    }

    runCatching {
        val out = File(context.cacheDir, "shizuku-install.apk")
        context.assets.open("shizuku.apk").use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", out)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        android.widget.Toast
            .makeText(context, "安装失败：${it.message}", android.widget.Toast.LENGTH_LONG)
            .show()
    }
}

@Composable
private fun ShizukuInfoDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(
                Modifier
                    .padding(18.dp)
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Shizuku 是什么", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "一个开源小工具，把手机的 adb（shell）权限借给普通应用用。" +
                        "它自己不带任何功能，只是个「权限中转站」",
                    fontSize = 12.sp, color = Color(0xFF374151),
                )

                Spacer(Modifier.height(12.dp))
                Text("它能让本应用做什么", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                InfoLine("关掉某些 App 的「链接自动打开」，广告 deeplink 直接打不开")
                InfoLine("禁用应用里广告 SDK 的页面组件，从源头减少广告页")
                InfoLine("一键关闭无线调试和 Shizuku，用完就断，不留尾巴")

                Spacer(Modifier.height(12.dp))
                Text("怎么启动它", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                InfoLine("电脑 adb：手机连电脑，跑一次启动命令（重启后要重来）")
                InfoLine("无线调试：手机自己配对启动，不用电脑，但需要开无线调试")
                InfoLine("有 root 的话可以直接开机自启")
                Spacer(Modifier.height(4.dp))
                Text(
                    "本应用只做了前两种的引导，点「高级禁用 → 开启引导」有详细步骤",
                    fontSize = 11.sp, color = Color(0xFF6B7280),
                )

                Spacer(Modifier.height(12.dp))
                Text("安全吗", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "开源（Apache-2.0）、本地运行、不联网回传。授权是显式的，随时能在 Shizuku 里撤销。" +
                        "不过它给的是 shell 权限，理论上很强——所以别授权给来路不明的应用",
                    fontSize = 12.sp, color = Color(0xFF374151),
                )

                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6))) {
                    Column(Modifier.padding(10.dp)) {
                        Text("内置版本", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "本应用内置的是 v13.6.0（2.4MB），点「一键安装」直接装，不用联网、不用跳浏览器",
                            fontSize = 12.sp, color = Color(0xFF374151),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("知道了") }
            }
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("· ", color = Color(0xFF2F6FEB), fontSize = 12.sp)
        Text(text, fontSize = 12.sp, color = Color(0xFF374151))
    }
}

@Composable
private fun BlacklistDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    var apps by remember { mutableStateOf(loadLauncherApps(context)) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.first.contains(query, true) || it.second.contains(query, true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(Modifier.padding(16.dp).heightIn(max = 620.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("黑名单", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (settings.blacklist.isNotEmpty()) {
                        TextButton(onClick = { prefs.clearBlacklist() }) { Text("清空") }
                    }
                    TextButton(onClick = { apps = loadLauncherApps(context) }) { Text("刷新") }
                }
                Text(
                    "勾选的应用会被完全忽略：不找跳过按钮、也不监控弹窗",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索应用") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))

                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(filtered, key = { it.second }) { pair ->
                        val (label, pkg) = pair
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(label, fontSize = 13.sp)
                                Text(pkg, fontSize = 10.sp, color = Color(0xFF9CA3AF))
                            }
                            Switch(
                                checked = pkg in settings.blacklist,
                                onCheckedChange = { prefs.setBlacklist(pkg, it) },
                            )
                        }
                        HorizontalDivider()
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
            }
        }
    }
}

private val FLOAT_COLORS = listOf(
    0xFF2F6FEB.toInt(),
    0xFF12B76A.toInt(),
    0xFFF79009.toInt(),
    0xFFE5484D.toInt(),
    0xFF8B5CF6.toInt(),
    0xFF6B7280.toInt(),
)

@Composable
private fun FloatingSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()

    var size by remember {
        mutableFloatStateOf(settings.floatingSize.coerceIn(com.skipad.service.FloatingService.MIN_DOT_DP, 60).toFloat())
    }
    var color by remember { mutableIntStateOf(settings.floatingColor) }
    var alpha by remember { mutableFloatStateOf(settings.floatingAlpha) }
    var hiddenDot by remember { mutableStateOf(settings.floatingHidden) }
    var hideInGames by remember { mutableStateOf(settings.floatingHideInGames) }
    var clickable by remember { mutableStateOf(settings.floatingClickable) }
    var longPress by remember { mutableStateOf(settings.floatingLongPressClose) }
    var action by remember { mutableStateOf(settings.floatingClickAction) }
    var pkg by remember { mutableStateOf(settings.floatingCustomPkg) }
    var picking by remember { mutableStateOf(false) }

    fun restartFloat() {
        if (settings.floatingEnabled && settings.forceKeepAlive) {
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    Intent(context, com.skipad.service.FloatingService::class.java),
                )
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            prefs.setFloatingImage(uri.toString())
            restartFloat()
        }
    }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            prefs.setFloatingSound(uri.toString())
        }
    }

    val actions = listOf(
        Prefs.ACTION_OPEN_APP to "打开本应用",
        Prefs.ACTION_TOGGLE_SKIP to "暂停 / 开启拦截",
        Prefs.ACTION_CUSTOM_APP to "打开指定应用",
        Prefs.ACTION_KILL_BG to "一键清后台（不含自己）",
        Prefs.ACTION_NONE to "什么都不做",
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(
                Modifier
                    .padding(16.dp)
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("悬浮窗设置", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("改完点「保存并应用」立即生效", fontSize = 11.sp, color = Color(0xFF6B7280))
                Spacer(Modifier.height(10.dp))

                Text(
                    "大小：${size.toInt()} dp",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Slider(value = size, onValueChange = { size = it }, valueRange = com.skipad.service.FloatingService.MIN_DOT_DP.toFloat()..60f)

                Spacer(Modifier.height(6.dp))
                Text("颜色", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FLOAT_COLORS.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable { color = c },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (color == c) {
                                Text("✓", color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "透明度：${(alpha * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Slider(value = alpha, onValueChange = { alpha = it }, valueRange = 0.05f..1f)

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("可以被点击", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = clickable, onCheckedChange = { clickable = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("长按关闭悬浮窗", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = longPress, onCheckedChange = { longPress = it })
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("隐藏悬浮窗", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "缩成 1 像素、全透明、点不到；要操作时从通知或首页进来",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280),
                        )
                    }
                    Switch(checked = hiddenDot, onCheckedChange = { hiddenDot = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("全屏游戏自动隐藏", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "王者、原神等游戏在前台时自动藏起来，退出游戏再出现",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280),
                        )
                    }
                    Switch(
                        checked = hideInGames,
                        enabled = !hiddenDot,
                        onCheckedChange = { hideInGames = it },
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自定义贴图", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.floatingImageUri.isBlank()) "没选就用默认圆点；支持 PNG 透明通道和 GIF 动图"
                            else "已使用自定义图片（GIF 会动，点「清除」回到圆点）",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280),
                        )
                    }
                    TextButton(onClick = { imagePicker.launch(arrayOf("image/*", "image/gif")) }) { Text("选择") }
                    if (settings.floatingImageUri.isNotBlank()) {
                        TextButton(onClick = {
                            prefs.setFloatingImage("")
                            restartFloat()
                        }) { Text("清除") }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("点击音效", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.floatingSoundUri.isBlank()) "默认无音效"
                            else "已设置（点「清除」恢复静音）",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280),
                        )
                    }
                    TextButton(onClick = { soundPicker.launch(arrayOf("audio/*")) }) { Text("选择") }
                    if (settings.floatingSoundUri.isNotBlank()) {
                        TextButton(onClick = { prefs.setFloatingSound("") }) { Text("清除") }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("点按展开功能面板（灵动岛）", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "关掉则点一下直接执行「打开本应用」",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280),
                        )
                    }
                    Switch(
                        checked = settings.islandEnabled,
                        onCheckedChange = { prefs.setIslandEnabled(it) },
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text("灵动岛里显示哪些功能", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                listOf(
                    Prefs.ISLAND_OPEN to "打开本应用",
                    Prefs.ISLAND_TOGGLE to "暂停 / 开启拦截",
                    Prefs.ISLAND_KILL to "一键清后台（不含自己）",
                    Prefs.ISLAND_CUSTOM to "打开指定应用",
                    Prefs.ISLAND_NOTE to "笔记",
                    Prefs.ISLAND_DNS to "DNS 录入（实验）",
                    Prefs.ISLAND_CLOSE to "关闭悬浮窗",
                ).forEach { (key, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = key in settings.islandItems,
                            onCheckedChange = { on ->
                                prefs.setIslandItem(key, on)
                                // 灵动岛接管笔记后，独立气泡必须让位
                                if (key == Prefs.ISLAND_NOTE && on) {
                                    prefs.setNotesIndependent(false)
                                    runCatching { com.skipad.service.FloatingService.hideNoteWindowNow() }
                                }
                            },
                        )
                    }
                }

                if (Prefs.ISLAND_CUSTOM in settings.islandItems) {
                    TextButton(onClick = { picking = true }) {
                        Text(
                            if (settings.floatingCustomPkg.isBlank()) "选择要跳转的应用"
                            else "已选：" + appLabel(context, settings.floatingCustomPkg),
                            fontSize = 13.sp,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            prefs.setFloatingStyle(
                                size = size.toInt(),
                                color = color,
                                alpha = alpha,
                                clickable = clickable,
                                longPressClose = longPress,
                                action = action,
                                customPkg = pkg,
                            )
                            prefs.setFloatingHidden(hiddenDot)
                            prefs.setFloatingHideInGames(hideInGames)
                            // 只要保活总开关开着就重启悬浮窗，让隐藏/尺寸/颜色立刻生效
                            if (settings.forceKeepAlive) {
                                runCatching {
                                    androidx.core.content.ContextCompat.startForegroundService(
                                        context,
                                        Intent(context, com.skipad.service.FloatingService::class.java),
                                    )
                                }
                            }
                            onDismiss()
                        },
                    ) { Text("保存并应用") }
                    TextButton(onClick = {
                        prefs.resetFloatingStyle()
                        prefs.setFloatingHidden(false)
                        prefs.setFloatingHideInGames(true)
                        hiddenDot = false
                        hideInGames = true
                        size = 30f
                        color = 0xFF2F6FEB.toInt()
                        alpha = 0.85f
                        clickable = true
                        longPress = true
                        action = Prefs.ACTION_OPEN_APP
                        pkg = ""
                        restartFloat()
                        android.widget.Toast.makeText(
                            context,
                            "悬浮窗已恢复默认",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }) { Text("恢复默认") }
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
            }
        }
    }

    if (picking) {
        AppPickerDialog(onDismiss = { picking = false }) { picked ->
            pkg = picked
            picking = false
        }
    }
}

@Composable
private fun AppPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf(loadLauncherApps(context)) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.first.contains(query, true) || it.second.contains(query, true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(Modifier.padding(16.dp).heightIn(max = 600.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("选一个应用", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { apps = loadLauncherApps(context) }) { Text("刷新") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(filtered, key = { it.second }) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(pair.second) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(pair.first, fontSize = 13.sp)
                                Text(pair.second, fontSize = 10.sp, color = Color(0xFF9CA3AF))
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("取消") }
            }
        }
    }
}

@Composable
private fun ExperimentalCard() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    var showLists by remember { mutableStateOf(false) }
    var vpnReady by remember { mutableStateOf(false) }
    var dnsRunning by remember { mutableStateOf(false) }
    var pendingStartDns by remember { mutableStateOf(false) }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val ok = result.resultCode == android.app.Activity.RESULT_OK
        vpnReady = ok || runCatching { android.net.VpnService.prepare(context) }.getOrNull() == null
        if (ok && pendingStartDns) {
            pendingStartDns = false
            runCatching {
                context.startService(
                    Intent(context, com.skipad.service.DnsFilterService::class.java)
                        .setAction(com.skipad.service.DnsFilterService.ACTION_START),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            vpnReady = runCatching { android.net.VpnService.prepare(context) }.getOrNull() == null
            dnsRunning = com.skipad.service.DnsFilterService.running
            kotlinx.coroutines.delay(1000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3EF)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("实验性功能", fontWeight = FontWeight.SemiBold, color = Color(0xFF9C3A1B))
            Text(
                "下面这些还没稳定验证，随时可能改，默认关闭",
                fontSize = 12.sp,
                color = Color(0xFF7A5B4A),
            )

            Spacer(Modifier.height(10.dp))
            Text("DNS 广告拦截", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "做法：本地 VPN 只接管 DNS，命中的域名直接回 NXDOMAIN，广告素材加载失败就没了。不需要 root。\n" +
                    "限制：App 一旦用 HTTPDNS / DoH，DNS 就绕过去了——所以下面那项 HTTPDNS 抑制是配套的。",
                fontSize = 12.sp,
                color = Color(0xFF6B7280),
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val intent = runCatching { android.net.VpnService.prepare(context) }.getOrNull()
                    if (intent != null) {
                        pendingStartDns = true
                        runCatching { vpnLauncher.launch(intent) }
                    } else {
                        vpnReady = true
                        android.widget.Toast
                            .makeText(context, "VPN 权限已就绪", android.widget.Toast.LENGTH_SHORT)
                            .show()
                    }
                }) { Text(if (vpnReady) "VPN 已授权" else "申请 VPN 权限") }

                if (dnsRunning) {
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startService(
                                Intent(context, com.skipad.service.DnsFilterService::class.java)
                                    .setAction(com.skipad.service.DnsFilterService.ACTION_STOP),
                            )
                        }
                    }) { Text("停止 DNS 拦截") }
                } else {
                    OutlinedButton(
                        enabled = vpnReady,
                        onClick = {
                            runCatching {
                                context.startService(
                                    Intent(context, com.skipad.service.DnsFilterService::class.java)
                                        .setAction(com.skipad.service.DnsFilterService.ACTION_START),
                                )
                            }
                            android.widget.Toast
                                .makeText(context, "正在启动 DNS 拦截…", android.widget.Toast.LENGTH_SHORT)
                                .show()
                        },
                    ) { Text(if (vpnReady) "启动 DNS 拦截" else "先授权 VPN") }
                }
            }
            Text(
                when {
                    dnsRunning -> "DNS 通道：运行中（系统状态栏会有钥匙图标）"
                    vpnReady -> "DNS 通道：未运行（点「启动 DNS 拦截」）"
                    else -> "DNS 通道：未授权 VPN"
                },
                fontSize = 11.sp,
                color = if (dnsRunning) Color(0xFF16794B) else Color(0xFF9C4A1B),
            )

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("实验：HTTPDNS 抑制", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "阻断常见 App 的 HTTPDNS 端点，逼它回落系统 DNS。副作用：应用启动时可能卡几秒",
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                    )
                }
                Switch(
                    checked = settings.dnsHttpDnsBlock,
                    onCheckedChange = { prefs.setDnsHttpDnsBlock(it) },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DNS 广告拦截", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "总开关。名单里的域名会被拦掉；白名单里的永不拦",
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                    )
                }
                Switch(
                    checked = settings.dnsEnabled,
                    onCheckedChange = { prefs.setDnsEnabled(it) },
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showLists = true }) {
                    Text("名单：黑 ${settings.dnsBlocked.size} / 白 ${settings.dnsAllowed.size}")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "录入方式：先把「DNS 录入（实验）」加到灵动岛，点圆点 → DNS录入 → " +
                    "出现标记窗后，看到广告点一下（红），退出广告再点一下（绿，录软件自身域名），长按退出。",
                fontSize = 11.sp,
                color = Color(0xFF6B7280),
            )
        }
    }

    if (showLists) {
        DnsListsDialog(onDismiss = { showLists = false })
    }
}

@Composable
private fun DnsListsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    var input by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }

    fun normalizeDomain(raw: String): String? {
        // 中文输入法/粘贴常见的全角标点，先归一化
        var d = raw.trim().lowercase()
            .replace('。', '.').replace('．', '.').replace('｡', '.')
            .replace('－', '-').replace('＿', '_')
        d = d.filterNot { it.isWhitespace() || it == '\u3000' }
        if (d.isEmpty()) return null
        d = d.removePrefix("http://").removePrefix("https://")
        d = d.substringBefore('/').substringBefore(':')
        d = d.removePrefix("*.")
        d = d.trimEnd('.')
        if (d.isEmpty() || d.length > 100) return null
        if (!d.contains('.')) return null
        if (!d.matches(Regex("^[a-z0-9._-]+$"))) return null
        return d
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(Modifier.padding(16.dp).heightIn(max = 620.dp)) {
                Text("DNS 名单", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    "黑名单 ${settings.dnsBlocked.size} 条 · 白名单 ${settings.dnsAllowed.size} 条",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                )
                Text(
                    "白名单优先级最高：同一个域名同时在两边时，永不拦截",
                    fontSize = 11.sp,
                    color = Color(0xFF9C4A1B),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; hint = "" },
                        label = { Text("手动输入域名") },
                        placeholder = { Text("ads.example.com") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                val d = normalizeDomain(input)
                                if (d == null) {
                                    hint = "格式不对：只填域名，比如 ads.example.com"
                                } else {
                                    prefs.addDnsBlocked(listOf(d))
                                    hint = "已加入黑名单：$d"
                                    input = ""
                                }
                            },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                        val clip = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        if (clip.isNotBlank()) input = clip
                    }) { Text("粘贴") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val d = normalizeDomain(input)
                        if (d == null) {
                            hint = "格式不对：只填域名，比如 ads.example.com"
                        } else {
                            prefs.addDnsBlocked(listOf(d))
                            hint = "已加入黑名单：$d"
                            input = ""
                        }
                    }) { Text("加入黑名单") }
                    OutlinedButton(onClick = {
                        val d = normalizeDomain(input)
                        if (d == null) {
                            hint = "格式不对：只填域名，比如 api.example.com"
                        } else {
                            prefs.addDnsAllowed(listOf(d))
                            hint = "已加入白名单：$d"
                            input = ""
                        }
                    }) { Text("加入白名单") }
                }
                if (hint.isNotBlank()) {
                    Text(
                        hint,
                        fontSize = 11.sp,
                        color = if (hint.startsWith("格式")) Color(0xFF9C4A1B) else Color(0xFF16794B),
                    )
                }
                Text(
                    "支持直接粘整条链接，会自动去掉 https:// 和路径；白名单优先级最高",
                    fontSize = 10.sp,
                    color = Color(0xFF9CA3AF),
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    if (settings.dnsBlocked.isEmpty() && settings.dnsAllowed.isEmpty()) {
                        item {
                            Text(
                                "还没有录入任何域名。先把「DNS 录入（实验）」加到灵动岛，点圆点开始录。",
                                fontSize = 12.sp,
                                color = Color(0xFF9CA3AF),
                            )
                        }
                    }
                    if (settings.dnsBlocked.isNotEmpty()) {
                        item { Text("黑名单（拦截）", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        items(settings.dnsBlocked.sorted()) { domain ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(domain, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { prefs.removeDnsBlocked(domain) }) { Text("删") }
                            }
                            HorizontalDivider()
                        }
                    }
                    if (settings.dnsAllowed.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text("白名单（放行）", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        items(settings.dnsAllowed.sorted()) { domain ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(domain, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { prefs.removeDnsAllowed(domain) }) { Text("删") }
                            }
                            HorizontalDivider()
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { prefs.resetDnsLists() }) { Text("清空全部") }
                    Button(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}
