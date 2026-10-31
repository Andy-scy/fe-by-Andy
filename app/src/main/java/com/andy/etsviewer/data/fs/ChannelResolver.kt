package com.andy.etsviewer.data.fs

import android.content.Context

/**
 * 通道选择：按「代价从低到高」探测，第一个能真正列出目标目录的就用它。
 *
 * 顺序说明：
 * - DIRECT  不需要任何授权，但 Android 15 起基本已被系统加固；
 * - SHIZUKU 需要用户在 Shizuku 里授权，Android 11+ 上最可靠；
 * - ROOT    最后，因为会弹 su 授权框；
 * - MIRROR  兜底：读应用自己的外部目录副本（需一次性 adb 复制）。
 */
class ChannelResolver(context: Context) {

    private val direct = DirectChannel()
    private val shizuku = ShizukuChannel()
    private val root = RootChannel()
    private val mirror = MirrorChannel(context)

    fun probes(): List<ChannelProbe> = listOf(
        ChannelProbe(
            mode = AccessMode.DIRECT,
            available = direct.isAvailable(),
            detail = if (direct.isAvailable()) "零宽字符绕过可用" else "系统已加固 /sdcard/Android/data 访问限制",
        ),
        ChannelProbe(
            mode = AccessMode.SHIZUKU,
            available = shizuku.isAvailable(),
            detail = ShizukuBridge.statusLine(),
        ),
        ChannelProbe(
            mode = AccessMode.ROOT,
            available = root.isAvailable(),
            detail = if (root.isAvailable()) "已获得 su 权限" else "未 root 或 su 未授权",
        ),
        ChannelProbe(
            mode = AccessMode.MIRROR,
            available = mirror.isAvailable(),
            detail = mirror.activeRoot()?.absolutePath
                ?: "未发现镜像（可放到 /sdcard/Download/ETS_secondary）",
        ),
    )

    fun resolve(): ReadChannel? =
        listOf(direct, shizuku, root, mirror).firstOrNull { it.isAvailable() }
}
