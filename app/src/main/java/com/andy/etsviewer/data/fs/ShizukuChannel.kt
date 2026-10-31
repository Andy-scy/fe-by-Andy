package com.andy.etsviewer.data.fs

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Shizuku 通道：以 shell（uid 2000）身份执行命令。
 *
 * shell 用户对 `/sdcard/Android/data` 有读取权限，因此这是非 Root 设备上最稳的一条路。
 * 用 `ls -la` / `cat` 而不是直接拼路径，是因为所有命令都在 shell 侧展开，
 * 应用侧不需要持有任何特殊权限。
 */
class ShizukuChannel : ReadChannel {

    override val mode = AccessMode.SHIZUKU

    /**
     * Shizuku API 13.1.5 把 `newProcess(String[], String[], String)` 收成了 private，
     * 但服务端依然提供该能力；通过反射取到方法后即可用 shell 身份 fork 进程。
     * 这是官方 API 之外唯一不改用 user service 的轻量做法。
     */
    private val newProcessMethod: java.lang.reflect.Method? by lazy {
        runCatching {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
        }.getOrNull()
    }

    override fun isAvailable(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private fun exec(vararg cmd: String): String? {
        if (!isAvailable()) return null
        val newProcess = newProcessMethod ?: return null
        // 单次 cat/ls 偶发失败会让上层组卷断链，因此这里重试一次再放弃。
        repeat(2) { attempt ->
            val result = runCatching {
                @Suppress("UNCHECKED_CAST")
                val process = newProcess.invoke(
                    null,
                    arrayOf("sh", "-c", cmd.joinToString(" ")),
                    null,
                    null,
                ) as Process
                // 注意：ShizukuRemoteProcess 的 exitValue() 在进程尚未回收时会抛
                // IllegalArgumentException("process hasn't exited")，所以这里完全不用退出码判断。
                // stdout 读到 EOF 即说明进程已把输出写完；再用 stderr 区分「命令成功但无输出」与「命令失败」。
                val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
                val err = process.errorStream.bufferedReader().use(BufferedReader::readText)
                runCatching { process.waitFor(2, TimeUnit.SECONDS) }
                if (output.isBlank() && err.isNotBlank()) {
                    Log.w(TAG, "cmd failed: ${cmd.joinToString(" ")} :: ${err.trim()}")
                    null
                } else {
                    output
                }
            }.onFailure { t ->
                Log.w(TAG, "exec exception: ${t.javaClass.simpleName}: ${t.message}", t)
            }.getOrNull()
            if (result != null) return result
            Log.w(TAG, "retry ${attempt + 1} after failure: ${cmd.joinToString(" ")}")
        }
        return null
    }

    /** 以字节流方式执行命令（音频/图片等二进制内容）。 */
    private fun execBytes(cmd: String, maxBytes: Long): ByteArray? {
        if (!isAvailable()) return null
        val newProcess = newProcessMethod ?: return null
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            val process = newProcess.invoke(
                null,
                arrayOf("sh", "-c", cmd),
                null,
                null,
            ) as Process
            val bytes = process.inputStream.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        process.destroyForcibly()
                        return@use null
                    }
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            }
            process.waitFor(4, TimeUnit.SECONDS)
            bytes
        }.getOrNull()
    }

    override fun readBytes(path: String, maxBytes: Long): ByteArray? =
        execBytes("cat " + quote(path), maxBytes)

    /**
     * 列目录。
     *
     * 用 `stat` 而不是 `ls -la`：Toybox 的 ls 不支持 --time-style，输出的 "MMM DD HH:MM"
     * 没法可靠还原成下载时间；stat 直接给 epoch 秒，正好用来做「按下载时间排序」。
     */
    override fun list(path: String): List<Entry> {
        val raw = exec("stat", "-c", "'%F|%Y|%s|%n'", "$path/*") ?: return emptyList()
        return raw.lineSequence()
            .mapNotNull { parseStatLine(it, path) }
            .toList()
    }

    private fun parseStatLine(line: String, parent: String): Entry? {
        val parts = line.split('|', limit = 4)
        if (parts.size < 4) return null
        val type = parts[0]
        val modified = parts[1].toLongOrNull() ?: 0L
        val size = parts[2].toLongOrNull() ?: 0L
        val full = parts[3].trim()
        if (full.isBlank()) return null
        val name = full.substringAfterLast('/')
        if (name.isBlank() || name == "." || name == "..") return null
        return Entry(
            path = full,
            name = name,
            isDirectory = type.contains("directory"),
            size = size,
            modifiedMs = modified * 1000L,
        )
    }

    override fun readText(path: String): String? = exec("cat", quote(path))

    override fun exists(path: String): Boolean = exec("ls", quote(path)) != null

    override fun find(dirPath: String, fileName: String, maxDepth: Int): List<Entry> {
        val raw = exec(
            "find", quote(dirPath), "-maxdepth", maxDepth.toString(), "-name", quote(fileName),
        ) ?: return emptyList()
        return raw.lineSequence()
            .filter { it.isNotBlank() }
            .map { full ->
                Entry(path = full, name = full.substringAfterLast('/'), isDirectory = false)
            }
            .toList()
    }

    override fun walk(path: String, maxDepth: Int): List<Entry> {
        val raw = exec("find", quote(path), "-maxdepth", maxDepth.toString()) ?: return emptyList()
        return raw.lineSequence()
            .filter { it.isNotBlank() && it != path }
            .map { full ->
                Entry(path = full, name = full.substringAfterLast('/'), isDirectory = false)
            }
            .toList()
    }

    private fun parseLsLine(line: String, parent: String): Entry? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 8) return null
        val perm = parts[0]
        if (perm.startsWith("total")) return null
        val name = parts.last()
        if (name == "." || name == "..") return null
        val isDir = perm.startsWith("d")
        val size = parts[4].toLongOrNull() ?: 0L
        return Entry(
            path = parent.trimEnd('/') + "/" + name,
            name = name,
            isDirectory = isDir,
            size = size,
            modifiedMs = 0L,
        )
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    companion object {
        private const val TAG = "ShizukuChannel"
    }
}
