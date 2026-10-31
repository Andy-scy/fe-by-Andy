package com.andy.etsviewer.ui.nav

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.material3.Surface

/**
 * 一次返回手势的实时状态，交给页面自行做「跟手」变换。
 *
 * @param progress 0f = 手势起点，1f = 达到触发返回的阈值。
 * @param active   手势是否正在进行（用于决定要不要叠加遮罩/缩放）。
 */
data class BackGestureState(
    val progress: Float = 0f,
    val active: Boolean = false,
)

/**
 * 预测性返回（Predictive Back）宿主。
 *
 * 设计要点（对齐 Android 官方指引）：
 * 1. 只做「跟手动画」，不在手势中途执行任何副作用；
 * 2. 用户松手越过阈值才真正 [onBack]，被取消则平滑回弹；
 * 3. 完全依赖 `androidx.activity` 的 `PredictiveBackHandler`，
 *    这样在 Android 16/17 上，系统跨 Activity / 跨任务预览也能拿到同一份回调。
 *
 * Android 15 上只能看到应用内跟手效果；跨应用预览需 Android 16+（后续在 Android 17 机器上验证）。
 */
@Composable
fun PredictiveBackHost(
    enabled: Boolean,
    onBack: () -> Unit,
    content: @Composable (state: BackGestureState) -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var active by remember { mutableStateOf(false) }
    val settle = remember { Animatable(0f) }

    PredictiveBackHandler(enabled = enabled) { backEvents ->
        active = true
        var completed = false
        try {
            backEvents.collect { event ->
                progress = event.progress.coerceIn(0f, 1f)
            }
            completed = true
        } finally {
            if (completed) {
                progress = 0f
                active = false
                onBack()
            } else {
                // 手势被取消：把当前进度回弹到 0，避免页面卡在半透明状态。
                settle.snapTo(progress)
                settle.animateTo(0f, tween(160))
                progress = 0f
                active = false
            }
        }
    }

    content(BackGestureState(progress = progress, active = active))
}

/**
 * 给内容套上「后退时缩小 + 变暗」的标准预测性返回视觉效果。
 * 数值参考 Material 3 容器变换规范：最大缩放 0.90、最大压暗 20%。
 */
@Composable
fun PredictiveBackContent(
    state: BackGestureState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scale = 1f - (0.10f * state.progress)
    val alpha = 1f - (0.20f * state.progress)
    Surface(modifier = modifier.scale(scale).alpha(alpha)) {
        content()
    }
}
