package com.andy.etsviewer.data.fs

import android.content.Context
import com.andy.etsviewer.data.ets.EtsPaths
import java.io.File

/**
 * 镜像通道：读取 ETS_secondary 的本地副本。
 *
 * 存在意义：Android 15 上既没有 Root、Shizuku 又不可用时，这是唯一还能走通的路径。
 * 依次尝试三个位置（第一个存在的生效）：
 *
 * 1. `/sdcard/Download/ETS_secondary` —— 用户用任意文件管理器把作业目录拷进来即可，无需 adb；
 * 2. 应用外部目录 `<externalFilesDir>/ETS_secondary`；
 * 3. 应用内部目录 `<filesDir>/ETS_secondary`。
 *
 * 命令示例（电脑侧）：
 * ```
 * adb shell cp -r /sdcard/Android/data/com.ets100.secondary/files/Download/ETS_secondary \
 *     /sdcard/Download/
 * ```
 */
class MirrorChannel(context: Context) : ReadChannel {

    override val mode = AccessMode.MIRROR

    private val appContext = context.applicationContext

    private val candidates: List<File>
        get() = listOfNotNull(
            File("/sdcard/Download/ETS_secondary"),
            appContext.getExternalFilesDir(null)?.let { File(it, "ETS_secondary") },
            File(appContext.filesDir, "ETS_secondary"),
        )

    @Volatile
    private var resolvedRoot: File? = null

    @Volatile
    private var probed = false

    /** 当前生效的镜像根目录。 */
    fun activeRoot(): File? {
        if (probed) return resolvedRoot
        synchronized(this) {
            if (probed) return resolvedRoot
            resolvedRoot = candidates.firstOrNull { dir ->
                dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
            }
            probed = true
            return resolvedRoot
        }
    }

    private fun toRealPath(logicalPath: String): String? {
        val root = activeRoot() ?: return null
        if (!logicalPath.startsWith(EtsPaths.PARENT)) return null
        return root.absolutePath + logicalPath.removePrefix(EtsPaths.PARENT)
    }

    override fun isAvailable(): Boolean = activeRoot() != null

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

    override fun exists(path: String): Boolean = toRealPath(path)?.let { File(it).exists() } == true

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
}
