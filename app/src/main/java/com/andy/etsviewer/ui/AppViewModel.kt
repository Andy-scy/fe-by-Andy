package com.andy.etsviewer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andy.etsviewer.data.ets.EtsRepository
import com.andy.etsviewer.data.fs.ChannelProbe
import com.andy.etsviewer.data.model.Paper
import com.andy.etsviewer.data.model.PaperSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = true,
    val papers: List<PaperSummary> = emptyList(),
    val channel: String = "",
    val diagnostics: List<String> = emptyList(),
    val probes: List<ChannelProbe> = emptyList(),
    val error: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EtsRepository(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 完整试卷对象只留在内存里，列表页不需要。 */
    private var papersById: Map<String, Paper> = emptyMap()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repository.load()
                .onSuccess { loaded ->
                    papersById = loaded.papers.associateBy { it.id }
                    _state.update {
                        it.copy(
                            loading = false,
                            papers = loaded.summaries,
                            channel = loaded.channelLabel,
                            diagnostics = loaded.diagnostics,
                            probes = loaded.probes,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: "扫描失败",
                        )
                    }
                }
        }
    }

    fun paper(id: String): Paper? = papersById[id]

    /** 拿到可播放的音频绝对路径：本地通道直接给路径，跨进程通道先导出到缓存。 */
    suspend fun prepareAudio(section: com.andy.etsviewer.data.model.Section): String? {
        section.audioPath?.let { return it }
        val logical = section.audioLogicalPath ?: return null
        return repository.materializeAudio(logical)
    }

    /**
     * 手动申请 Shizuku 授权。
     *
     * 走手动而不是自动的原因：部分厂商（实测 OPPO/ColorOS）用 adb 启动的 Shizuku 会直接
     * 弹「adb 权限受限」并拒绝授权，自动申请会反复抢焦点。用户在这些机器上需要先打开
     * 开发者选项里的「禁止权限监控」，再回来点这个按钮。
     */
    fun requestShizukuPermission() {
        com.andy.etsviewer.data.fs.ShizukuBridge.requestPermission { granted ->
            if (granted) refresh()
        }
    }
}
