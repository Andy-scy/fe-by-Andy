package com.andy.etsviewer.ui.paper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andy.etsviewer.data.model.Paper
import com.andy.etsviewer.data.model.Question
import com.andy.etsviewer.data.model.SectionKind

/**
 * 答案速览（置顶、可折叠）。
 *
 * 刻意做成**纯文本排版**，不分组、不使用色块：
 *
 * ```
 * 1、a  2、c  3、b  4、c  5、b  6、b
 *
 * 7、Thirteen.
 * 8、In the school library.
 * …
 * 13、I know about Tom's family. Tom is from England. …
 * ```
 *
 * 规则：
 * - 选择题：字母小写、连续编号、**同一行**排开（跨多个选择小节也合并成一行）；
 * - 问答题 / 信息转述 / 听后转述 / 填空：默认取**第一条参考答案**，每题一行；
 *   超过一行时省略，点击该行可展开/收起全文；
 * - 朗读类没有答案，单独一行提示。
 */
@Composable
fun AnswerSheet(
    paper: Paper,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalQuestions = paper.sections.sumOf { it.questions.size }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.FormatListBulleted,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "答案速览",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (totalQuestions > 0) "$totalQuestions 题" else "无题目",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "收起答案速览" else "展开答案速览",
                    )
                }
            }

            if (!expanded) {
                Text(
                    text = oneLineSummary(paper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                return@Column
            }

            Spacer(Modifier.size(8.dp))

            Column(
                modifier = Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                AnswerLines(paper)
            }
        }
    }
}

@Composable
private fun AnswerLines(paper: Paper) {
    var running = 0
    var index = 0
    val sections = paper.sections

    while (index < sections.size) {
        val section = sections[index]

        if (section.kind == SectionKind.CHOICE && section.questions.isNotEmpty()) {
            // 连续的选择小节合并成一行：1、a  2、c  3、b …
            val parts = mutableListOf<String>()
            while (index < sections.size && sections[index].kind == SectionKind.CHOICE) {
                sections[index].questions.forEach { question ->
                    running += 1
                    parts += "$running、${choiceAnswer(question)}"
                }
                index += 1
            }
            Text(
                text = parts.joinToString("  "),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 2.dp),
            )
            Spacer(Modifier.size(6.dp))
            continue
        }

        if (section.questions.isEmpty()) {
            Text(
                text = "${section.title.ifBlank { section.kind.label }}：朗读原文（无答案）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
            index += 1
            Spacer(Modifier.size(6.dp))
            continue
        }

        section.questions.forEach { question ->
            running += 1
            AnswerTextLine(number = running, question = question)
        }
        index += 1
        Spacer(Modifier.size(6.dp))
    }
}

@Composable
private fun AnswerTextLine(number: Int, question: Question) {
    var expanded by remember { mutableStateOf(false) }
    val fullText = question.answers.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: question.options.joinToString(" / ") { "${it.label}.${it.text}" }.takeIf { it.isNotBlank() }
        ?: "—"
    val expandable = fullText.length > SHORT_ANSWER_CHARS || fullText.contains('\n')

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = expandable) { expanded = !expanded }
            .padding(vertical = 1.dp),
    ) {
        Text(
            text = "$number、$fullText",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
        if (expandable && !expanded) {
            Text(
                text = "展开",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 选择题答案：小写字母，多答案保持原分隔符。 */
private fun choiceAnswer(question: Question): String =
    question.answers.firstOrNull().orEmpty().ifBlank { "—" }.lowercase()

/** 折叠态一行摘要，同样保持连续编号。 */
private fun oneLineSummary(paper: Paper): String {
    val parts = mutableListOf<String>()
    var running = 0
    paper.sections.forEach { section ->
        section.questions.forEach { question ->
            running += 1
            parts += when (section.kind) {
                SectionKind.CHOICE -> "$running${choiceAnswer(question)}"
                else -> "$running、${shortAnswer(question)}"
            }
        }
    }
    return if (parts.isEmpty()) "暂无可用答案" else parts.joinToString(" ")
}

/** 默认取第一条参考答案，压成单行。 */
internal fun shortAnswer(question: Question): String {
    val first = question.answers.firstOrNull()?.takeIf { it.isNotBlank() }
    return when {
        first != null -> first.replace(Regex("\\s+"), " ").trim()
        question.options.isNotEmpty() -> question.options.joinToString(" / ") { "${it.label}.${it.text}" }
        else -> "—"
    }
}

private const val SHORT_ANSWER_CHARS = 80
