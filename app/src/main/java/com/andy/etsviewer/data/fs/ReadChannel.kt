package com.andy.etsviewer.data.fs

/**
 * 读取通道抽象。
 *
 * 三条实现（Direct / Shizuku / Root）行为完全一致，上层扫描器与解析器只依赖本接口，
 * 这样「换一种授权方式」不会影响任何业务代码。
 */
interface ReadChannel {

    val mode: AccessMode

    /** 轻量可用性判断，不做目录列举。 */
    fun isAvailable(): Boolean

    /** 列目录；失败返回空列表并记录原因。 */
    fun list(path: String): List<Entry>

    /** 读文本文件（UTF-8）；失败返回 null。 */
    fun readText(path: String): String?

    fun exists(path: String): Boolean

    /** 递归列出文件（用于统计与调试）。 */
    fun walk(path: String, maxDepth: Int = 3): List<Entry>

    /**
     * 在 [dirPath] 下按文件名查找（一次调用拿全量结果）。
     *
     * 关键性能点：Shizuku/Root 通道每次 `cat` 都是一次跨进程调用，
     * 若对每个目录盲目尝试读 `res.json`，会产生大量「文件不存在」的失败调用。
     * 先用一次 `find` 拿到真实存在的文件名集合，再决定读哪些。
     */
    fun find(dirPath: String, fileName: String, maxDepth: Int = 2): List<Entry> =
        walk(dirPath, maxDepth).filter { !it.isDirectory && it.name == fileName }

    /**
     * 若该通道能以普通文件路径直接访问（供 MediaPlayer 等原生组件使用），返回真实路径；
     * Shizuku / Root 这类需要跨进程读的通道返回 null。
     */
    fun realPath(logicalPath: String): String? = null

    /** 读取二进制内容（音频等），默认不支持；上限用于防止误读超大文件。 */
    fun readBytes(path: String, maxBytes: Long = 16L * 1024 * 1024): ByteArray? = null
}

data class Entry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val modifiedMs: Long = 0L,
)

enum class AccessMode(val label: String) {
    DIRECT("直读（零宽字符绕过）"),
    SHIZUKU("Shizuku（shell 身份）"),
    ROOT("Root"),
    MIRROR("本地镜像"),
    NONE("不可用"),
}

/** 探测结果，直接喂给首页的诊断卡片。 */
data class ChannelProbe(
    val mode: AccessMode,
    val available: Boolean,
    val detail: String,
)
