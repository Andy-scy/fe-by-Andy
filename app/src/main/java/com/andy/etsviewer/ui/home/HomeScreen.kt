package com.andy.etsviewer.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.andy.etsviewer.R
import com.andy.etsviewer.data.fs.ChannelProbe
import com.andy.etsviewer.data.model.PaperSource
import com.andy.etsviewer.data.model.PaperSummary
import com.andy.etsviewer.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenPaper: (PaperSummary) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showDiagnostics by remember { mutableStateOf(false) }
    var showLoose by remember { mutableStateOf(false) }
    // 未归类内容（散装 content）默认折叠，避免把真正的作业淹掉；
    // 但 24 小时内新下载的内容一律显示，防止「刚下的卷被折叠掉」。
    val recentCutoff = System.currentTimeMillis() - 24 * 3_600_000L
    val visible = state.papers.filter { paper ->
        showLoose || paper.source != PaperSource.LOOSE || paper.updatedMs >= recentCutoff
    }
    val hiddenLooseCount = state.papers.count { paper ->
        paper.source == PaperSource.LOOSE && paper.updatedMs < recentCutoff
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.app_author),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "重新扫描")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ChannelCard(state, showDiagnostics, onRequestShizuku) { showDiagnostics = !showDiagnostics } }

            if (state.loading) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Text("正在读取本地作业…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            state.error?.let { message ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
                            Spacer(Modifier.size(12.dp))
                            Text(message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (!state.loading && visible.isEmpty()) {
                item { EmptyHint() }
            }

            val newestId = visible.firstOrNull()?.id
            items(visible, key = { it.id }) { paper ->
                PaperCard(
                    paper = paper,
                    isNewest = paper.id == newestId,
                    onClick = { onOpenPaper(paper) },
                )
            }

            if (!showLoose && hiddenLooseCount > 0) {
                item {
                    TextButton(onClick = { showLoose = true }) {
                        Text("显示 $hiddenLooseCount 条未归类内容（散装题包）")
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ChannelCard(
    state: UiState,
    expanded: Boolean,
    onRequestShizuku: () -> Unit,
    onToggle: () -> Unit,
) {
    val ok = state.channel.isNotBlank() && state.channel != "不可用"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Sensors,
                    contentDescription = null,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = if (ok) "读取通道：${state.channel}" else "读取通道不可用",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (ok) {
                    "共 ${state.papers.size} 份本地试卷/作业"
                } else {
                    val shizuku = state.probes.firstOrNull {
                        it.mode == com.andy.etsviewer.data.fs.AccessMode.SHIZUKU
                    }
                    when {
                        shizuku?.detail?.contains("待授权") == true ->
                            "Shizuku 服务已在运行，点下方按钮授权后即可直读原始目录"
                        shizuku?.detail?.contains("未运行") == true ->
                            "Shizuku 服务未运行：请先启动 Shizuku（或用一条 adb 命令激活）"
                        else -> "请先激活 Shizuku 并授权，或授予 Root 权限"
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.probes.isNotEmpty()) {
                TextButton(onClick = onToggle) {
                    Text(if (expanded) "收起诊断信息" else "查看读取通道诊断")
                }
            }
            val shizukuReady = state.probes
                .firstOrNull { it.mode == com.andy.etsviewer.data.fs.AccessMode.SHIZUKU }
                ?.available == true
            if (!shizukuReady) {
                TextButton(onClick = onRequestShizuku) {
                    Text("授权 Shizuku（直读原始目录）")
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    state.probes.forEach { probe -> ProbeRow(probe) }
                    if (state.diagnostics.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        state.diagnostics.forEach { line ->
                            Text(
                                text = "• $line",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbeRow(probe: ChannelProbe) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (probe.available) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (probe.available) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "${probe.mode.label} · ${probe.detail}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PaperCard(paper: PaperSummary, isNewest: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = paper.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (isNewest) {
                    NewestChip()
                    Spacer(Modifier.size(6.dp))
                }
                SourceChip(paper.source)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = paper.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${paper.sectionCount} 个小节 · ${paper.questionCount} 道题",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            val timeText = downloadTimeText(paper.updatedMs)
            if (timeText.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SourceChip(source: PaperSource) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = source.label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "还没有扫描到已下载的作业",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "先在 E听说 里打开并下载一套作业，再回到这里刷新",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

/** 「最新」标记：给排序后第一份（最近下载）的作业。 */
@Composable
private fun NewestChip() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = "最新",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** 下载时间：刚刚 / N 分钟前 / 今天 HH:mm / 昨天 HH:mm / MM-dd HH:mm。 */
internal fun downloadTimeText(ms: Long): String {
    if (ms <= 0L) return ""
    val diff = System.currentTimeMillis() - ms
    return when {
        diff in 0 until 60_000L -> "刚刚下载"
        diff in 0 until 3_600_000L -> "${diff / 60_000} 分钟前下载"
        diff in 0 until 24 * 3_600_000L -> "今天 ${formatTime(ms, "HH:mm")} 下载"
        diff in 0 until 48 * 3_600_000L -> "昨天 ${formatTime(ms, "HH:mm")} 下载"
        diff < 0 -> "刚刚下载"
        else -> "${formatTime(ms, "MM-dd HH:mm")} 下载"
    }
}

private fun formatTime(ms: Long, pattern: String): String =
    java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        .format(java.util.Date(ms))
