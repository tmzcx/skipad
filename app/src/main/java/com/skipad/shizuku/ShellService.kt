package com.skipad.shizuku

import android.content.Context
import android.util.Log
import androidx.annotation.Keep

/**
 * Shizuku 用户服务：由 Shizuku 以 shell 身份实例化（不是 Android Service），
 * 所以必须直接继承 AIDL Stub。详见 Shizuku-API demo 的 UserService。
 */
class ShellService : IShellService.Stub {

    @Keep
    constructor() {
        Log.i(TAG, "ctor")
    }

    @Keep
    constructor(context: Context) {
        Log.i(TAG, "ctor context=$context")
    }

    override fun exec(command: String): String = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        process.waitFor()
        "uid=${android.os.Process.myUid()}\n$out$err"
    }.getOrElse { "ERR: ${it.message}" }

    override fun destroy() {
        Log.i(TAG, "destroy")
        System.exit(0)
    }

    override fun exit() = destroy()

    companion object {
        private const val TAG = "ShellService"
    }
}
