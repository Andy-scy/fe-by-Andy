package com.andy.etsviewer.data.ets

import com.andy.etsviewer.data.model.AnswerOption
import com.andy.etsviewer.data.model.Question
import com.andy.etsviewer.data.model.Section
import com.andy.etsviewer.data.model.SectionKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * `content.json` 解析器。
 *
 * 真实字段（v7.0.71 实测）：
 * - collector.choose : info.st_nr(听力原文) / info.audio / info.xtlist[]{
 *     xt_xh 题号, xt_nr 题干, answer 答案字母, xxlist[]{xx_mc, xx_nr} }
 * - collector.role   : info.value(对话原文) / info.question[]{
 *     xh 题号, ask 题干, std[]{value, ai}, keywords }
 * - collector.picture: info.std[].value(转述范文) / info.keypoint(要点) / info.topic
 * - collector.read   : info.value(朗读原文) / info.translate / info.ai
 * - collector.fill   : info.std[]{xth, value}
 */
object ContentParser {

    fun parse(raw: String, fallbackTitle: String): Section? = runCatching {
        val root = JSONObject(raw)
        val type = root.optString("structure_type")
        val info = root.optJSONObject("info") ?: return null
        when (type) {
            "collector.choose" -> parseChoose(info)
            "collector.role", "collector.dialogue", "collector.3q5a" -> parseQa(info, type)
            "collector.picture" -> parsePicture(info)
            "collector.fill" -> parseFill(info)
            "collector.read" -> parseRead(info)
            else -> parseGeneric(info, type)
        }?.let { section ->
            if (section.title.isBlank()) section.copy(title = fallbackTitle) else section
        }
    }.getOrNull()

    private fun parseChoose(info: JSONObject): Section {
        val list = info.optJSONArray("xtlist") ?: JSONArray()
        val questions = (0 until list.length()).mapNotNull { i ->
            val item = list.optJSONObject(i) ?: return@mapNotNull null
            val options = item.optJSONArray("xxlist")?.let { arr ->
                (0 until arr.length()).mapNotNull { k ->
                    val opt = arr.optJSONObject(k) ?: return@mapNotNull null
                    AnswerOption(
                        label = opt.optString("xx_mc").ifBlank { ('A' + k).toString() },
                        text = TextClean.clean(opt.optString("xx_nr")),
                    )
                }
            } ?: emptyList()
            Question(
                number = TextClean.normalizeNumber(item.optString("xt_xh"), i),
                prompt = TextClean.clean(item.optString("xt_nr")),
                answers = splitAnswer(item.optString("answer")),
                options = options,
            )
        }
        return Section(
            title = "",
            kind = SectionKind.CHOICE,
            passage = TextClean.cleanHtml(info.optString("st_nr")),
            audio = info.optString("audio").ifBlank { null },
            questions = questions,
        )
    }

    private fun parseQa(info: JSONObject, type: String): Section {
        val list = info.optJSONArray("question") ?: JSONArray()
        val questions = (0 until list.length()).mapNotNull { i ->
            val item = list.optJSONObject(i) ?: return@mapNotNull null
            val std = item.optJSONArray("std")
            val answers = if (std == null) emptyList() else (0 until std.length())
                .mapNotNull { k -> std.optJSONObject(k)?.optString("value")?.let(TextClean::clean) }
                .filter { it.isNotBlank() }
                .distinct()
            Question(
                number = TextClean.normalizeNumber(item.optString("xh"), i),
                prompt = TextClean.clean(item.optString("ask")),
                answers = answers,
                keywords = TextClean.clean(item.optString("keywords")).ifBlank { null },
            )
        }
        return Section(
            title = if (type == "collector.3q5a") "3 问 5 答" else "",
            kind = if (type == "collector.3q5a") SectionKind.QA else SectionKind.QA,
            passage = TextClean.cleanHtml(info.optString("value")),
            audio = info.optString("audio").ifBlank { null },
            questions = questions,
        )
    }

    private fun parsePicture(info: JSONObject): Section {
        val std = info.optJSONArray("std")
        val answers = if (std == null) emptyList() else (0 until std.length())
            .mapNotNull { k -> std.optJSONObject(k)?.optString("value")?.let(TextClean::clean) }
            .filter { it.isNotBlank() }
            .distinct()
        val keyPoints = TextClean.cleanHtml(info.optString("keypoint"))
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return Section(
            title = "",
            kind = SectionKind.PICTURE,
            passage = TextClean.cleanHtml(info.optString("value")).ifBlank { null },
            audio = info.optString("audio").ifBlank { null },
            questions = listOf(
                Question(
                    number = "1",
                    prompt = TextClean.clean(info.optString("topic")).ifBlank { "信息转述" },
                    answers = answers,
                ),
            ),
            keyPoints = keyPoints,
            topic = TextClean.clean(info.optString("topic")).ifBlank { null },
        )
    }

    private fun parseFill(info: JSONObject): Section {
        val std = info.optJSONArray("std")
        val questions = if (std == null) emptyList() else (0 until std.length()).mapNotNull { k ->
            val item = std.optJSONObject(k) ?: return@mapNotNull null
            Question(
                number = TextClean.normalizeNumber(item.optString("xth"), k),
                prompt = "第 ${item.optString("xth").ifBlank { (k + 1).toString() }} 空",
                answers = listOf(TextClean.clean(item.optString("value"))).filter { it.isNotBlank() },
            )
        }
        return Section(
            title = "",
            kind = SectionKind.FILL,
            passage = TextClean.cleanHtml(info.optString("value")).ifBlank { null },
            audio = info.optString("audio").ifBlank { null },
            questions = questions,
        )
    }

    private fun parseRead(info: JSONObject): Section = Section(
        title = "",
        kind = SectionKind.READING,
        passage = TextClean.cleanHtml(info.optString("value")).ifBlank { null },
        audio = info.optString("audio").ifBlank { null },
        questions = emptyList(),
    )

    private fun parseGeneric(info: JSONObject, type: String): Section {
        val passage = TextClean.cleanHtml(
            info.optString("value").ifBlank { info.optString("st_nr") },
        ).ifBlank { null }
        val std = info.optJSONArray("std")
        val answers = if (std == null) emptyList() else (0 until std.length())
            .mapNotNull { k -> std.optJSONObject(k)?.optString("value")?.let(TextClean::clean) }
            .filter { it.isNotBlank() }
        return Section(
            title = type.ifBlank { "未知题型" },
            kind = SectionKind.UNKNOWN,
            passage = passage,
            audio = info.optString("audio").ifBlank { null },
            questions = if (answers.isEmpty()) emptyList() else listOf(
                Question(number = "1", prompt = "参考答案", answers = answers),
            ),
        )
    }

    /** 选择题答案可能是 `"A"`、`"A,C"`、`"AC"` 三种写法。 */
    internal fun splitAnswer(raw: String?): List<String> {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return emptyList()
        return when {
            value.contains(',') || value.contains('，') ->
                value.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }

            value.length > 1 && value.all { it.isLetter() } -> value.map { it.toString() }
            else -> listOf(value)
        }
    }
}
