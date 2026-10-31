package com.andy.etsviewer.data.ets

/**
 * 文本清洗：E听说 的题干/答案里混着 `ets_thN` 前缀、HTML 片段与零宽字符，
 * 直接展示会很难看，这里统一处理成纯文本。
 */
object TextClean {

    private val ETS_PREFIX = Regex("^\\s*ets_(th|sm|st)\\d+\\s*", RegexOption.IGNORE_CASE)
    private val TAG = Regex("<[^>]+>")
    private val MULTI_NEWLINE = Regex("\n{3,}")

    /** 题干/答案通用清洗。 */
    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var text = raw.removePrefix("") // 防 BOM
        text = ETS_PREFIX.replace(text, "")
        text = text.replace("\u200B", "").replace("\u200C", "")
            .replace("\u200D", "").replace("\uFEFF", "")
        text = brToNewline(text)
        text = TAG.replace(text, "")
        text = unescape(text)
        text = MULTI_NEWLINE.replace(text, "\n\n")
        return text.trim()
    }

    /** 只做 HTML → 纯文本，保留原有换行结构（用于听力原文、朗读原文）。 */
    fun cleanHtml(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var text = raw.replace("\u200B", "").replace("\u200C", "")
            .replace("\u200D", "").replace("\uFEFF", "")
        text = brToNewline(text)
        text = TAG.replace(text, "")
        text = unescape(text)
        text = MULTI_NEWLINE.replace(text, "\n\n")
        return text.lines().joinToString("\n") { it.trim() }.trim()
    }

    /** `</p><p>`、`<br>`、`</br>` 统一成换行。 */
    private fun brToNewline(text: String): String = text
        .replace(Regex("(?i)</p>\\s*<p>"), "\n")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</br>"), "\n")
        .replace(Regex("(?i)</p>"), "\n")

    private fun unescape(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    /** 题号规范化：`ets_th3` / `3.` / `3` → `3`。 */
    fun normalizeNumber(raw: String?, fallbackIndex: Int): String {
        val candidate = raw?.trim().orEmpty()
        if (candidate.isEmpty()) return (fallbackIndex + 1).toString()
        ETS_PREFIX.replace(candidate, "").trim()
            .removeSuffix(".").removeSuffix("、").trim()
            .takeIf { it.isNotEmpty() && it.length <= 4 }
            ?.let { return it }
        return (fallbackIndex + 1).toString()
    }
}
