package com.andy.etsviewer.data.ets

import com.andy.etsviewer.data.fs.ReadChannel
import com.andy.etsviewer.data.model.Paper
import com.andy.etsviewer.data.model.PaperSource
import com.andy.etsviewer.data.model.PaperSummary
import com.andy.etsviewer.data.model.Question
import com.andy.etsviewer.data.model.Section
import com.andy.etsviewer.data.model.SectionKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * 扫描 + 组装试卷。
 *
 * 两条装配线，优先级从高到低：
 *
 * 1. **data 索引线**（[PaperSource.DATA_INDEX]）
 *    `data/` 下每个 JSON 描述一份作业：`paperId` + `fileNameData[]`（引用的 resource 目录）
 *    + `sectionData[].caption`（题型标题，如「短文朗读」）。这条线最可信。
 *
 * 2. **res 模板线**（[PaperSource.TEMPLATE]）
 *    带 `res.json` 的目录是整卷模板包：`exam_type_list[].exam_list[].exam_id` 里的
 *    `{{stN}}` 定义顺序。content 目录按 `info.stid` 升序排名 → st1..stN，
 *    再按模板把每个包分配给对应题型。（北京初中样本中 10 个 content 与 10 段模板完全对齐）
 *
 * 剩下的孤立 content 目录退化为单节试卷（[PaperSource.LOOSE]），保证不丢数据。
 */
class EtsScanner(
    private val channel: ReadChannel,
    /** 逻辑根目录；镜像通道会把它映射到应用自己的外部目录。 */
    private val root: String = EtsPaths.PARENT,
) {

    private val diagnostics = mutableListOf<String>()

    private fun resourcePath() = "$root/resource"
    private fun dataPath() = "$root/data"
    private fun contentJson(dirName: String) = "$root/resource/$dirName/content.json"
    private fun resJson(dirName: String) = "$root/resource/$dirName/res.json"

    private data class ContentDir(
        val dirName: String,
        val stid: Long,
        val section: Section,
        val modifiedMs: Long,
    )

    private data class TemplateEntry(val orderName: String, val typeName: String, val kind: SectionKind)

    private data class TemplatePack(
        val dirName: String,
        val score: String,
        val entries: List<TemplateEntry>,
        /** 试卷地区，来自 res.json 的 engine_area（beijing / guangdong / 空）。 */
        val area: String = "",
    ) {
        /** 用「地区 + 学段 + 总分」拼一个可读标题，避免直接暴露 8 位目录名。 */
        val displayName: String
            get() {
                val stage = when (entries.size) {
                    3 -> "广东高中"
                    7 -> "广东初中"
                    10 -> "北京初中"
                    13 -> "北京高中"
                    else -> when (area.lowercase()) {
                        "beijing" -> "北京卷"
                        "guangdong" -> "广东卷"
                        else -> "整卷"
                    }
                }
                val scoreValue = score.toIntOrNull()
                return if (scoreValue != null && scoreValue > 0) "$stage · $scoreValue 分卷" else "$stage"
            }
    }

    private data class DataEntry(
        val fileName: String,
        val paperId: String,
        val captions: Map<String, String>,
        val baseDirs: List<String>,
        val updatedMs: Long = 0L,
    ) {
        /** 合并同一 paperId 的重复索引：目录取并集并保序，caption 以先到者为准，时间取最新。 */
        fun mergedWith(other: DataEntry): DataEntry = DataEntry(
            fileName = fileName,
            paperId = paperId,
            captions = other.captions + captions,
            baseDirs = (baseDirs + other.baseDirs).distinct(),
            updatedMs = maxOf(updatedMs, other.updatedMs),
        )
    }

    fun scan(): Pair<List<Paper>, List<String>> {
        diagnostics.clear()

        if (!channel.isAvailable()) {
            diagnostics += "读取通道不可用（${channel.mode.label}）"
            return emptyList<Paper>() to diagnostics.toList()
        }

        val resourceDirs = channel.list(resourcePath())
            .filter { it.isDirectory && it.name != "common" }
        if (resourceDirs.isEmpty()) {
            diagnostics += "未能列出 ${resourcePath()}"
            return emptyList<Paper>() to diagnostics.toList()
        }
        diagnostics += "resource 目录：${resourceDirs.size} 个"

        // 逐目录 `ls` 判断文件是否真的存在，再按需 `cat`。
        // 不用 `find`：实测在 OPPO + Android 15 的 FUSE 上 `find -maxdepth` 会超时返回空结果。
        val contents = mutableListOf<ContentDir>()
        val packs = mutableListOf<TemplatePack>()
        resourceDirs.forEach { dir ->
            val names = channel.list("$root/resource/${dir.name}").mapTo(mutableSetOf()) { it.name }
            if ("content.json" in names) {
                readContent(dir.name, dir.modifiedMs)?.let { contents += it }
            }
            if ("res.json" in names) {
                readTemplate(dir.name)?.let { packs += it }
            }
        }
        diagnostics += "成功解析 content：${contents.size} 个，res 模板：${packs.size} 个"

        var unparsableData = 0
        val dataEntries = channel.list(dataPath())
            .filter { !it.isDirectory }
            .sortedBy { it.name }
            .mapNotNull { fileName ->
                val entry = readDataEntry(fileName)
                if (entry == null) unparsableData++
                entry
            }
            .filter { it.paperId.isNotBlank() }
            // 同一份作业在 data/ 下常存两份（一份带 answerIDMap、一份不带）。
            // 这里按 paperId 合并而不是简单去重：baseDirs 取并集、caption 取先到者，
            // 这样结果与文件列举顺序无关（否则 Shizuku/镜像两条通道可能给出不同答案）。
            .fold(linkedMapOf<String, DataEntry>()) { acc, entry ->
                acc[entry.paperId] = acc[entry.paperId]?.mergedWith(entry) ?: entry
                acc
            }
            .values
            .toList()
        diagnostics += "data 索引条目：${dataEntries.size} 个（按 paperId 合并后；另有 $unparsableData 个占位/空文件已忽略）"

        val usedDirs = mutableSetOf<String>()
        val papers = mutableListOf<Paper>()

        // ---------- 1. data 索引线 ----------
        dataEntries.forEach { entry ->
            val sections = entry.baseDirs.mapNotNull { baseDir ->
                val content = contents.firstOrNull { it.dirName == baseDir } ?: return@mapNotNull null
                val caption = entry.captions[baseDir].orEmpty()
                content.section.copy(title = caption.ifBlank { content.section.title })
            }
            if (sections.isNotEmpty()) {
                usedDirs += entry.baseDirs
                val sectionTimes = entry.baseDirs.mapNotNull { dir ->
                    contents.firstOrNull { it.dirName == dir }?.modifiedMs
                }
                papers += Paper(
                    id = "data:${entry.paperId}:${entry.fileName}",
                    title = "作业 ${entry.paperId}",
                    subtitle = sections.joinToString(" · ") { it.title }.ifBlank { "已下载作业" },
                    source = PaperSource.DATA_INDEX,
                    sections = sections.mapIndexed { index, section -> section.withRenumberedQuestions(index) },
                    updatedMs = maxOf(entry.updatedMs, sectionTimes.maxOrNull() ?: 0L),
                )
            }
        }

        // ---------- 2. res 模板线 ----------
        // 先按「下载时间」把 content 目录聚类：一次下载产生的一组 section，mtime 只差几秒。
        // 再让每个模板包去认领「题型序列完全对齐」的那一簇。
        // 只按 stid 排序匹配会让旧卷抢走模板（新下载的卷反而落到未归类里），必须按批次。
        val freeContents = contents.filter { it.dirName !in usedDirs }.sortedBy { it.stid }
        val clusters = clusterByDownloadTime(freeContents).toMutableList()
        val assigned = mutableSetOf<String>()
        packs.sortedByDescending { it.entries.size }.forEach { pack ->
            val candidate = clusters.firstOrNull { matchesPack(pack, it) } ?: return@forEach
            clusters.remove(candidate)
            if (candidate.isNotEmpty()) {
                candidate.forEach { assigned += it.dirName }
                val sections = candidate.mapIndexed { index, content ->
                    content.section
                        .copy(title = pack.entries.getOrNull(index)?.typeName ?: content.section.title)
                        .withRenumberedQuestions(index)
                }
                val totalScore = pack.score.toIntOrNull()
                papers += Paper(
                    id = "pack:${pack.dirName}",
                    title = pack.displayName,
                    subtitle = buildString {
                        append(sections.joinToString(" · ") { it.title })
                        if (totalScore != null && totalScore > 0) append("（共 $totalScore 分）")
                    },
                    source = PaperSource.TEMPLATE,
                    sections = sections,
                    updatedMs = candidate.maxOfOrNull { it.modifiedMs } ?: 0L,
                )
            }
        }

        // ---------- 3. 孤立内容 ----------
        clusters.flatten().filter { it.dirName !in assigned }.forEach { content ->
            papers += Paper(
                id = "loose:${content.dirName}",
                title = "独立内容 ${content.dirName.take(8)}",
                subtitle = content.section.kind.label,
                source = PaperSource.LOOSE,
                sections = listOf(content.section.copy(title = content.section.kind.label)),
                updatedMs = content.modifiedMs,
            )
        }

        diagnostics += "组装试卷：${papers.size} 份"
        // 按下载/更新时间倒序：最近下载的排最前，首页给它打「最新」标记。
        return papers.sortedByDescending { it.updatedMs } to diagnostics.toList()
    }

    // ---------------------------------------------------------------- 读取

    private fun readContent(dirName: String, modifiedMs: Long): ContentDir? {
        val raw = channel.readText(contentJson(dirName)) ?: return null
        val stid = runCatching {
            JSONObject(raw).optJSONObject("info")?.optString("stid")?.toLongOrNull()
        }.getOrNull() ?: return null
        val fallbackTitle = runCatching {
            JSONObject(raw).optString("structure_type").removePrefix("collector.")
        }.getOrDefault("")
        val section = ContentParser.parse(raw, fallbackTitle) ?: return null
        // 补全素材信息：目录名 + 音频可播放的真实路径（仅本地可读通道能给出）。
        val logicalAudio = section.audio?.let { "$root/resource/$dirName/material/$it" }
        val withMedia = section.copy(
            dirName = dirName,
            audioLogicalPath = logicalAudio,
            audioPath = logicalAudio?.let { channel.realPath(it) },
        )
        return ContentDir(dirName = dirName, stid = stid, section = withMedia, modifiedMs = modifiedMs)
    }

    private fun readTemplate(dirName: String): TemplatePack? {
        val raw = channel.readText(resJson(dirName)) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val types = root.optJSONArray("exam_type_list") ?: return null
            val entries = mutableListOf<TemplateEntry>()
            var area = ""
            for (i in 0 until types.length()) {
                val type = types.optJSONObject(i) ?: continue
                val typeName = type.optString("exam_type_name")
                val collect = type.optString("exam_type_collect")
                val exams = type.optJSONArray("exam_list") ?: continue
                for (k in 0 until exams.length()) {
                    val exam = exams.optJSONObject(k) ?: continue
                    if (area.isBlank()) {
                        // engine_area 藏在 question_list 里，用来判断北京/广东口音区
                        val questions = exam.optJSONArray("question_list")
                        if (questions != null && questions.length() > 0) {
                            area = questions.optJSONObject(0)?.optString("engine_area").orEmpty()
                        }
                    }
                    entries += TemplateEntry(
                        orderName = exam.optString("exam_id"),
                        typeName = typeName,
                        kind = kindOf(collect),
                    )
                }
            }
            TemplatePack(dirName, root.optString("set_score"), entries, area)
        }.getOrNull()
    }

    private fun readDataEntry(file: com.andy.etsviewer.data.fs.Entry): DataEntry? {
        val raw = channel.readText("${dataPath()}/${file.name}") ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val paperId = root.optString("paperId")
            if (paperId.isBlank()) return null

            val fileNames = root.optJSONArray("fileNameData")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
            } ?: emptyList()

            // baseDir → caption（题型标题）
            val captions = mutableMapOf<String, String>()
            val sections = root.optJSONArray("sectionData") ?: JSONArray()
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i) ?: continue
                val caption = section.optString("caption")
                val items = section.optJSONArray("sectionItemData") ?: continue
                for (k in 0 until items.length()) {
                    val item = items.optJSONObject(k) ?: continue
                    val sets = item.optJSONObject("childPaperBean")?.optJSONArray("set") ?: continue
                    for (m in 0 until sets.length()) {
                        val set = sets.optJSONObject(m) ?: continue
                        val baseDir = set.optString("baseDirName")
                        if (baseDir.isNotBlank() && caption.isNotBlank()) {
                            captions.putIfAbsent(baseDir, caption)
                        }
                    }
                }
            }

            val baseDirs = linkedSetOf<String>()
            captions.keys.forEach { baseDirs += it }
            fileNames.forEach { baseDirs += it }
            DataEntry(file.name, paperId, captions, baseDirs.toList(), file.modifiedMs)
        }.getOrNull()
    }

    // ---------------------------------------------------------------- 匹配

    /** 一次下载产生的 section 目录 mtime 只差几秒，按 60s 阈值聚成一簇。 */
    private fun clusterByDownloadTime(contents: List<ContentDir>): List<List<ContentDir>> {
        if (contents.isEmpty()) return emptyList()
        val byTime = contents.sortedBy { it.modifiedMs }
        val clusters = mutableListOf<MutableList<ContentDir>>()
        byTime.forEach { content ->
            val last = clusters.lastOrNull()
            if (last != null && content.modifiedMs - last.last().modifiedMs <= 60_000L) {
                last += content
            } else {
                clusters += mutableListOf(content)
            }
        }
        // 簇内按 stid 升序，保证与模板顺序一致
        return clusters.map { it.sortedBy { content -> content.stid } }
    }

    /** 模板题型序列与该簇是否完全对齐。 */
    private fun matchesPack(pack: TemplatePack, cluster: List<ContentDir>): Boolean {
        if (pack.entries.isEmpty() || cluster.size != pack.entries.size) return false
        return pack.entries.indices.all { i ->
            kindMatches(pack.entries[i].kind, cluster[i].section.kind)
        }
    }

    private fun kindMatches(template: SectionKind, actual: SectionKind): Boolean {
        if (template == SectionKind.UNKNOWN || actual == SectionKind.UNKNOWN) return true
        return template == actual
    }

    private fun kindOf(collect: String): SectionKind = when (collect) {
        "collector.choose" -> SectionKind.CHOICE
        "collector.role", "collector.dialogue", "collector.3q5a" -> SectionKind.QA
        "collector.picture" -> SectionKind.PICTURE
        "collector.fill" -> SectionKind.FILL
        "collector.read" -> SectionKind.READING
        else -> SectionKind.UNKNOWN
    }

    /** 同一套卷里题号常常从 1 重新开始，连续化后展示才不会出现 1,2,1,2。 */
    private fun Section.withRenumberedQuestions(index: Int): Section {
        if (questions.isEmpty()) return this
        val numbers = questions.map { it.number }
        if (numbers.size == numbers.toSet().size) return this
        val fixed = questions.mapIndexed { i, q -> q.copy(number = (i + 1).toString()) }
        return copy(questions = fixed)
    }

    fun summaries(papers: List<Paper>): List<PaperSummary> = papers.map { paper ->
        PaperSummary(
            id = paper.id,
            title = paper.title,
            subtitle = paper.subtitle,
            sectionCount = paper.sections.size,
            questionCount = paper.sections.sumOf { section ->
                if (section.questions.isEmpty()) 1 else section.questions.size
            },
            source = paper.source,
            updatedMs = paper.updatedMs,
        )
    }
}
