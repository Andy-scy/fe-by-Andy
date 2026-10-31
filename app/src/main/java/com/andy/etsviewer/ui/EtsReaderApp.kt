package com.andy.etsviewer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andy.etsviewer.ui.home.HomeScreen
import com.andy.etsviewer.ui.paper.PaperScreen

/**
 * 应用内极简返回栈：只有「首页 → 试卷详情」两级，没必要引入 Navigation 库。
 * 自己实现的好处是完全掌控预测性返回的跟手动画。
 */
sealed interface Screen {
    data object Home : Screen

    data class Paper(val paperId: String) : Screen
}

@Composable
fun EtsReaderApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }

    AnimatedContent(
        targetState = stack.lastOrNull() ?: Screen.Home,
        transitionSpec = {
            (slideInHorizontally { it / 8 } + fadeIn()) togetherWith fadeOut()
        },
        label = "screen",
    ) { screen ->
        when (screen) {
            is Screen.Home -> HomeScreen(
                state = state,
                onRefresh = viewModel::refresh,
                onRequestShizuku = viewModel::requestShizukuPermission,
                onOpenPaper = { summary ->
                    stack.add(Screen.Paper(summary.id))
                },
            )

            is Screen.Paper -> PaperScreen(
                paper = viewModel.paper(screen.paperId),
                onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                onPrepareAudio = viewModel::prepareAudio,
            )
        }
    }
}
