package com.andy.etsviewer.data.ets

import android.content.Context
import com.andy.etsviewer.data.fs.ChannelProbe
import com.andy.etsviewer.data.fs.ChannelResolver
import com.andy.etsviewer.data.model.Paper
import com.andy.etsviewer.data.model.PaperSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 仓库层：负责「选通道 → 扫描 → 组卷」，UI 只关心结果。
 *
 * 通道只提供文件读写能力，路径始终使用逻辑路径 [EtsPaths.PARENT]，
 * 镜像通道会在内部把逻辑路径映射到应用自己的外部目录。
 */
class EtsRepository(context: Context) {

    private val resolver = ChannelResolver(context.applicationContext)
    private val appContext = context.applicationContext

    /** 最近一次扫描实际使用的通道，供音频等素材按需导出。 */
    @Volatile
    private var activeChannel: com.andy.etsviewer.data.fs.ReadChannel? = null

    /**
     * 把素材（音频）从当前通道导出到应用缓存，返回可直接播放的绝对路径。
     *
     * 镜像/直读通道本来就是普通文件，[com.andy.etsviewer.data.fs.ReadChannel.realPath] 已能直接给出路径；
     * Shizuku / Root 通道则需要先 `cat` 出来落到缓存里。
     */
    suspend fun materializeAudio(logicalPath: String): String? = withContext(Dispatchers.IO) {
        val channel = activeChannel ?: return@withContext null
        val bytes = channel.readBytes(logicalPath) ?: return@withContext null
        runCatching {
            val dir = java.io.File(appContext.cacheDir, "material").apply { mkdirs() }
            // 文件名跨 section 会重复（都叫 content.mp3），带上所属目录名避免互相覆盖。
            val segments = logicalPath.trimEnd('/').split('/')
            val name = if (segments.size >= 3) {
                segments[segments.size - 3] + "_" + segments.last()
            } else {
                segments.last()
            }
            val out = java.io.File(dir, name)
            out.writeBytes(bytes)
            out.absolutePath
        }.getOrNull()
    }

    data class Loaded(
        val papers: List<Paper>,
        val summaries: List<PaperSummary>,
        val channelLabel: String,
        val diagnostics: List<String>,
        val probes: List<ChannelProbe>,
    )

    suspend fun load(): Result<Loaded> = withContext(Dispatchers.IO) {
        runCatching {
            val probes = resolver.probes()
            val channel = resolver.resolve()
                ?: return@runCatching Loaded(
                    papers = emptyList(),
                    summaries = emptyList(),
                    channelLabel = "不可用",
                    diagnostics = buildList {
                        add("没有可用的读取通道。")
                        probes.forEach { add("• ${it.mode.label}：${it.detail}") }
                        add("Android 15 已关闭 legacy 存储对其它应用 Android/data 的访问；")
                        add("请在 Shizuku 中授权本应用，或用 adb 把作业目录镜像到本应用目录。")
                    },
                    probes = probes,
                )

            val scanner = EtsScanner(channel, EtsPaths.PARENT)
            val (papers, diagnostics) = scanner.scan()
            activeChannel = channel
            Loaded(
                papers = papers,
                summaries = scanner.summaries(papers),
                channelLabel = channel.mode.label,
                diagnostics = diagnostics,
                probes = probes,
            )
        }
    }
}
