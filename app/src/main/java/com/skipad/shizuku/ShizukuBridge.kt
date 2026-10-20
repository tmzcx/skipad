package com.skipad.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.os.IBinder
import android.util.Log
import com.skipad.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * Shizuku 桥接。Shizuku 13 不再暴露 newProcess，所以走官方推荐的
 * "UserService"：把 [ShellService] 交给 Shizuku 以 shell 身份拉起，
 * 我们再通过 AIDL 调它执行 pm 命令。
 */
object ShizukuBridge {

    private const val TAG = "ShizukuBridge"
    private const val REQUEST_CODE = 10086

    data class Status(
        val alive: Boolean = false,
        val granted: Boolean = false,
        val shellReady: Boolean = false,
        val message: String = "未连接",
    ) {
        val ready: Boolean get() = alive && granted && shellReady
    }

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private var listenerAdded = false
    private var shell: IShellService? = null
    private var binding = false

    private val resultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        Log.i(TAG, "permission result=$grantResult")
        refresh()
        if (grantResult == PackageManager.PERMISSION_GRANTED) bindShell()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            shell = IShellService.Stub.asInterface(service)
            binding = false
            Log.i(TAG, "shell service connected")
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shell = null
            binding = false
            Log.w(TAG, "shell service disconnected")
            refresh()
        }
    }

    fun init() {
        if (!listenerAdded) {
            runCatching { Shizuku.addRequestPermissionResultListener(resultListener) }
            runCatching {
                Shizuku.addBinderReceivedListenerSticky { refresh() }
                Shizuku.addBinderDeadListener { refresh() }
            }
            listenerAdded = true
        }
        refresh()
    }

    fun refresh() {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val granted = if (!alive) false else runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        if (alive && granted && shell == null) bindShell()

        val shellReady = shell != null
        val msg = when {
            !alive -> "Shizuku 未运行：点「开启引导」按三步启动（先开无线调试，再在电脑跑脚本）"
            !granted -> "Shizuku 已运行，点「授权」允许本应用"
            !shellReady -> "已授权，正在连接 shell 服务…"
            else -> "Shizuku 已就绪（shell 服务已连接）"
        }
        _status.value = Status(alive, granted, shellReady, msg)
    }

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure { Log.w(TAG, "requestPermission failed", it) }
    }

    private fun bindShell() {
        if (shell != null || binding) return
        binding = true
        runCatching {
            val args = Shizuku.UserServiceArgs(
                ComponentName(BuildConfig.APPLICATION_ID, ShellService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("shell")
                .debuggable(BuildConfig.DEBUG)
                .version(1)
            Shizuku.bindUserService(args, connection)
            Log.i(TAG, "bindUserService requested")
        }.onFailure {
            binding = false
            Log.w(TAG, "bindUserService failed", it)
        }
    }

    fun exec(command: String): String {
        val s = shell ?: return "ERR: shell 服务未连接"
        return runCatching { s.exec(command) }
            .getOrElse { "ERR: ${it.message}" }
    }

    // ---------- 具体动作 ----------

    /** 用 shell 权限把无障碍服务重新打开（进程被杀后自愈） */
    fun enableAccessibility(pkg: String, cls: String): String {
        exec("settings put secure enabled_accessibility_services $pkg/$cls")
        exec("settings put secure accessibility_enabled 1")
        return exec("settings get secure enabled_accessibility_services")
    }

    fun setAppLinks(pkg: String, allowed: Boolean): String =
        exec("pm set-app-links-allowed --user 0 --package $pkg $allowed")

    /** 找出广告相关组件（Activity/Service），返回 pkg/cls */
    fun adComponents(pm: PackageManager, pkg: String): List<String> {
        val info: PackageInfo = runCatching {
            pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES)
        }.getOrNull() ?: return emptyList()

        val hints = listOf(
            "interstitial", "splashad", "adactivity", "adproxy", "adpro", "ptg",
            "dsp", "windmill", "ksad", "meishu", "tobid", "pangle", "byted", "gdt",
            "reward", "advert",
        )
        val out = LinkedHashSet<String>()
        info.activities?.forEach { a ->
            val name = a.name ?: return@forEach
            val lower = name.lowercase()
            if (hints.any { lower.contains(it) }) out.add("$pkg/$name")
        }
        info.services?.forEach { s ->
            val name = s.name ?: return@forEach
            val lower = name.lowercase()
            if (hints.any { lower.contains(it) }) out.add("$pkg/$name")
        }
        return out.toList()
    }

    fun disableComponent(component: String): String = exec("pm disable-user --user 0 $component")

    fun enableComponent(component: String): String = exec("pm enable --user 0 $component")
}
