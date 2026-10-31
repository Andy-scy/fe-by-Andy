package com.andy.etsviewer.ui.paper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.andy.etsviewer.data.model.AnswerOption
import com.andy.etsviewer.data.model.Paper
import com.andy.etsviewer.data.model.Question
import com.andy.etsviewer.data.model.Section
import com.andy.etsviewer.data.model.SectionKind
import com.andy.etsviewer.ui.nav.PredictiveBackContent
import com.andy.etsviewer.ui.nav.PredictiveBackHost

/**
 * 试卷详情：预测性返回真正生效的地方（首页是栈底，没有返回手势）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperScreen(
    paper: Paper?,
    onBack: () -> Unit,
    onPrepareAudio: suspend (Section) -> String? = { null },
) {
    val scope = rememberCoroutineScope()
    var audioError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var sheetExpanded by rememberSaveable { mutableStateOf(true) }
    // 全卷连续题号：每个 section 的起始编号（详情卡片与速览面板保持一致）
    val numberOffsets = remember(paper) {
        val offsets = mutableListOf<Int>()
        var acc = 0
        paper?.sections?.forEach { section ->
            offsets += acc
            acc += section.questions.size
        }
        offsets
    }
    LaunchedEffect(audioError) {
        audioError?.let {
            snackbarHostState.showSnackbar(it)
            audioError = null
        }
    }
    PredictiveBackHost(enabled = true, onBack = onBack) { state ->
        PredictiveBackContent(state = state, modifier = Modifier.fillMaxSize()) {
            val audioPlayer = rememberAudioPlayer()
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = paper?.title ?: "加载中",
                                    maxLines = 1,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                if (paper != null) {
                                    Text(
                                        text = paper.subtitle,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                            }
                        },
                    )
                },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { padding ->
                if (paper == null) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text("没有找到这份试卷", style = MaterialTheme.typography.bodyLarge)
                    }
                    return@Scaffold
                }
                Column(Modifier.fillMaxSize().padding(padding)) {
                    // 答案速览固定在顶部（不随列表滚动），折叠后只占一行
                    AnswerSheet(
                        paper = paper,
                        expanded = sheetExpanded,
                        onToggle = { sheetExpanded = !sheetExpanded },
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(paper.sections) { index, section ->
                        SectionCard(
                            index = index + 1,
                            section = section,
                            audioPlayer = audioPlayer,
                            numberStart = numberOffsets.getOrElse(index) { 0 },
                            onPlay = { target ->
                                scope.launch {
                                    val path = target.audioPath ?: onPrepareAudio(target)
                                    if (path != null) {
                                        audioPlayer.toggle(
                                            path,
                                            key = (target.dirName ?: "") + "#" + (target.audio ?: ""),
                                        )
                                    } else {
                                        audioError = "音频导出失败，可能通道无法读取素材"
                                    }
                                }
                            },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    index: Int,
    section: Section,
    audioPlayer: AudioPlayerHandle,
    numberStart: Int,
    onPlay: (Section) -> Unit,
) {
    var showPassage by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = index.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = section.title.ifBlank { section.kind.label },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = buildString {
                            append(section.kind.label)
                            if (section.audio != null) append(" · 含音频")
                            if (section.questions.isNotEmpty()) {
                                append(" · ${section.questions.size} 题")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (section.audioLogicalPath != null) {
                    val key = (section.dirName ?: "") + "#" + (section.audio ?: "")
                    val isCurrent = audioPlayer.playingKey == key
                    val playing = isCurrent && audioPlayer.isPlaying
                    IconButton(onClick = {
                        if (isCurrent) audioPlayer.toggle("", key) else onPlay(section)
                    }) {
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "暂停音频" else "播放音频 ${section.audio}",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (!section.passage.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { showPassage = !showPassage }) {
                    Icon(
                        imageVector = if (showPassage) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(if (showPassage) "收起原文" else "查看听力/朗读原文")
                }
                AnimatedVisibility(visible = showPassage) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = section.passage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }

            if (section.keyPoints.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("要点提示", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        section.keyPoints.forEach { point ->
                            Text(
                                text = "· $point",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (section.questions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
            }

            section.questions.forEachIndexed { i, question ->
                QuestionBlock(question, section.kind, numberStart + i + 1)
            }
        }
    }
}

@Composable
private fun QuestionBlock(question: Question, kind: SectionKind, displayNumber: Int) {
    var expanded by remember { mutableStateOf(false) }
    val hasManyAnswers = question.answers.size > 3

    Column(Modifier.padding(top = 14.dp)) {
        Row {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = displayNumber.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
        }

        if (question.options.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            question.options.forEach { option -> OptionRow(option, question.answers) }
        }

        if (kind != SectionKind.CHOICE && question.answers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "参考答案",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            val shown = if (expanded) question.answers else question.answers.take(3)
            shown.forEach { answer ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                ) {
                    Text(
                        text = answer,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            if (hasManyAnswers) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        if (expanded) {
                            "收起"
                        } else {
                            "还有 ${question.answers.size - 3} 条同义参考答案"
                        },
                    )
                }
            }
        }

        question.keywords?.let { keywords ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = "关键词：$keywords",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OptionRow(option: AnswerOption, answers: List<String>) {
    val isCorrect = answers.any { it.equals(option.label, ignoreCase = true) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isCorrect) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isCorrect) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = option.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}