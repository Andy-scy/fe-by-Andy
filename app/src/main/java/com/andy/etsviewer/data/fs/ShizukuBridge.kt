package com.andy.etsviewer.data.fs

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Shizuku 状态与授权的小封装。
 *
 * Shizuku 服务可以由用户手动启动，也可以像联调时那样用 `adb shell sh start.sh` 激活；
 * 应用侧只需要「检测存活 → 申请权限 → 拿到后走 [ShizukuChannel]」。
 */
object ShizukuBridge {

    private const val REQUEST_CODE = 0x5A12
    private const val PREFS = "shizuku_permission"
    private const val KEY_ASKED = "asked_once"

    fun installed(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    }.getOrDefault(false)

    fun alive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun granted(): Boolean = runCatching {
        alive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * 是否已经自动弹过一次授权框。
     *
     * 换机/重装后 Shizuku 的授权是按 UID 记录的，新装应用默认是「未授权」状态；
     * 这里只在每个安装周期自动弹一次，避免像某些厂商（adb 权限受限时）反复打扰。
     */
    fun autoAskedBefore(context: Context): Boolean = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ASKED, false)

    fun markAutoAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ASKED, true)
            .apply()
    }

    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (!alive()) {
            onResult(false)
            return
        }
        runCatching {
            val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == REQUEST_CODE) {
                    onResult(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQUEST_CODE)
        }.onFailure { onResult(false) }
    }

    fun statusLine(): String = when {
        !alive() -> "Shizuku 服务未运行"
        granted() -> "Shizuku 已授权"
        else -> "Shizuku 已运行，待授权"
    }
}
