package com.andy.etsviewer.data.fs

import android.util.Log
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * Root 通道：`su -c` 直读。设备已 root（Magisk / KernelSU / APatch）时权限最彻底。
 */
class RootChannel : ReadChannel {

    override val mode = AccessMode.ROOT

    @Volatile
    private var cachedAvailability: Boolean? = null

    override fun isAvailable(): Boolean {
        cachedAvailability?.let { return it }
        val ok = runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val out = p.inputStream.bufferedReader().use(BufferedReader::readText)
            p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0 && out.contains("uid=0")
        }.getOrDefault(false)
        cachedAvailability = ok
        return ok
    }

    private fun exec(command: String): String? {
        if (cachedAvailability == false) return null
        return runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val out = p.inputStream.bufferedReader().use(BufferedReader::readText)
            if (!p.waitFor(8, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                return null
            }
            if (p.exitValue() != 0) return null
            out
        }.getOrNull()
    }

    override fun list(path: String): List<Entry> {
        val raw = exec("stat -c '%F|%Y|%s|%n' $path/*") ?: return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('|', limit = 4)
            if (parts.size < 4) return@mapNotNull null
            val full = parts[3].trim()
            val name = full.substringAfterLast('/')
            if (name.isBlank() || name == "." || name == "..") return@mapNotNull null
            Entry(
                path = full,
                name = name,
                isDirectory = parts[0].contains("directory"),
                size = parts[2].toLongOrNull() ?: 0L,
                modifiedMs = (parts[1].toLongOrNull() ?: 0L) * 1000L,
            )
        }.toList()
    }

    override fun readText(path: String): String? = exec("cat '${path.replace("'", "'\\''")}'")

    override fun exists(path: String): Boolean = exec("ls '$path'") != null

    override fun find(dirPath: String, fileName: String, maxDepth: Int): List<Entry> {
        val raw = exec("find '$dirPath' -maxdepth $maxDepth -name '$fileName'") ?: return emptyList()
        return raw.lineSequence()
            .filter { it.isNotBlank() }
            .map { Entry(path = it, name = it.substringAfterLast('/'), isDirectory = false) }
            .toList()
    }

    override fun walk(path: String, maxDepth: Int): List<Entry> {
        val raw = exec("find '$path' -maxdepth $maxDepth") ?: return emptyList()
        return raw.lineSequence()
            .filter { it.isNotBlank() && it != path }
            .map { Entry(path = it, name = it.substringAfterLast('/'), isDirectory = false) }
            .toList()
    }

    companion object {
        private const val TAG = "RootChannel"
    }
}
