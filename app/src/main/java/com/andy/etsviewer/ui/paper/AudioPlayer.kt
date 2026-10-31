package com.andy.etsviewer.ui.paper

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.util.Log

/**
 * 极简音频播放器：同一时刻只播一段（试卷里的听力材料本来就是逐段播放的），
 * 用系统 MediaPlayer 而不是引入 ExoPlayer，避免为一个 mp3 增加 5 MB 依赖。
 */
class AudioPlayerHandle {

    var playingKey by mutableStateOf<String?>(null)
        private set
    var playingPath by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var player: MediaPlayer? = null

    fun toggle(path: String, key: String = path) {
        if (playingKey == key && player != null) {
            if (isPlaying) pause() else resume()
            return
        }
        releaseCurrent()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(path)
            // 注意：lambda 里不能用 apply 的接收者隐式解析，否则 isPlaying 会命中
            // MediaPlayer 的只读属性；这里显式使用外层 AudioPlayerHandle 的属性。
            mp.setOnCompletionListener {
                isPlaying = false
                playingPath = null
                playingKey = null
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer error what=$what extra=$extra path=$path")
                error = "无法播放（$what/$extra）"
                isPlaying = false
                playingPath = null
                playingKey = null
                true
            }
            mp.prepare()
            mp.start()
            player = mp
            playingKey = key
            playingPath = path
            isPlaying = true
            error = null
        } catch (t: Throwable) {
            Log.w(TAG, "play failed: ${t.message}", t)
            error = t.message ?: "播放失败"
            releaseCurrent()
        }
    }

    private fun pause() {
        runCatching { player?.pause() }
        isPlaying = false
    }

    private fun resume() {
        runCatching { player?.start() }
        isPlaying = true
    }

    private fun releaseCurrent() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        playingPath = null
        isPlaying = false
    }

    fun release() = releaseCurrent()

    companion object {
        private const val TAG = "AudioPlayerHandle"
    }
}

@Composable
fun rememberAudioPlayer(): AudioPlayerHandle {
    val handle = remember { AudioPlayerHandle() }
    DisposableEffect(Unit) {
        onDispose { handle.release() }
    }
    return handle
}
