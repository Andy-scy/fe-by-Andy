package com.andy.etsviewer.data.fs

import android.os.Build
import android.util.Log
import java.io.File

/**
 * 直读通道。
 *
 * Android 11+ 起，普通应用无法用 File API 访问其它应用的 `/sdcard/Android/data/<pkg>`。
 * 这里复用一个历史悠久的路径规范化绕过：把 `Android/data` 写成 `Android/<零宽字符>data`，
 * 框架层的「是否属于 Android/data」前缀校验认不出该字符串，而 VFS 归一化后仍落到真实目录。
 * 是否仍然有效完全取决于系统版本与厂商补丁 —— 所以这里只做「试出来就用」，
 * 不可用就交给 Shizuku / Root 通道。
 */
class DirectChannel : ReadChannel {

    override val mode = AccessMode.DIRECT

    private val zeroWidthChars = listOf('\u200B', '\u200C', '\u200D', '\uFEFF')

    /** 探测并缓存的可用前缀；null 表示尚未探测。 */
    @Volatile
    private var resolvedPrefix: String? = null

    @Volatile
    private var probed = false

    private fun candidates(): List<String> {
        val normal = ETS_PARENT_NORMAL
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return listOf(normal)
        return buildList {
            // 先试常规路径（万一 OEM 放开了）
            add(normal)
            zeroWidthChars.forEach { add("/sdcard/Android/$it" + "data/com.ets100.secondary/files/Download/ETS_secondary") }
        }
    }

    private fun resolvePrefix(): String? {
        if (probed) return resolvedPrefix
        synchronized(this) {
            if (probed) return resolvedPrefix
            resolvedPrefix = candidates().firstOrNull { candidate ->
                runCatching {
                    val f = File(candidate)
                    f.canRead() && f.isDirectory
                }.getOrDefault(false)
            }
            probed = true
            Log.i(TAG, "direct prefix resolved = $resolvedPrefix")
            return resolvedPrefix
        }
    }

    /** 把逻辑路径（以 [ETS_PARENT_NORMAL] 开头）翻译成实际可用路径。 */
    fun toRealPath(logicalPath: String): String? {
        val prefix = resolvePrefix() ?: return null
        if (!logicalPath.startsWith(ETS_PARENT_NORMAL)) return null
        return prefix + logicalPath.removePrefix(ETS_PARENT_NORMAL)
    }

    override fun isAvailable(): Boolean = resolvePrefix() != null

    override fun list(path: String): List<Entry> {
        val real = toRealPath(path) ?: return emptyList()
        val dir = File(real)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()?.map { file ->
            Entry(
                path = path.trimEnd('/') + "/" + file.name,
                name = file.name,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0L,
                modifiedMs = file.lastModified(),
            )
        } ?: emptyList()
    }

    override fun readText(path: String): String? {
        val real = toRealPath(path) ?: return null
        return runCatching { File(real).readText() }.getOrNull()
    }

    override fun exists(path: String): Boolean {
        val real = toRealPath(path) ?: return false
        return File(real).exists()
    }

    override fun realPath(logicalPath: String): String? = toRealPath(logicalPath)

    override fun readBytes(path: String, maxBytes: Long): ByteArray? {
        val real = toRealPath(path) ?: return null
        val file = File(real)
        if (!file.isFile || file.length() > maxBytes) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    override fun walk(path: String, maxDepth: Int): List<Entry> {
        val out = mutableListOf<Entry>()
        fun recurse(current: String, depth: Int) {
            if (depth > maxDepth) return
            list(current).forEach { entry ->
                out += entry
                if (entry.isDirectory) recurse(entry.path, depth + 1)
            }
        }
        recurse(path, 0)
        return out
    }

    companion object {
        private const val TAG = "DirectChannel"
        const val ETS_PARENT_NORMAL =
            "/sdcard/Android/data/com.ets100.secondary/files/Download/ETS_secondary"
    }
}
