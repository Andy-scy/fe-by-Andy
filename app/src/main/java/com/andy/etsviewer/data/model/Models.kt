package com.andy.etsviewer.data.model

/** 试卷列表项：首页只需要这些信息，不必把全部题目读进来。 */
data class PaperSummary(
    val id: String,
    val title: String,
    val subtitle: String,
    val sectionCount: Int,
    val questionCount: Int,
    val source: PaperSource,
    val updatedMs: Long = 0L,
)

enum class PaperSource(val label: String) {
    /** 来自 `data/` 索引：能拿到作业号与题型标题，最可信。 */
    DATA_INDEX("作业索引"),

    /** 来自 `res.json` 模板包：用模板顺序 + stid 排名拼出的整卷。 */
    TEMPLATE("整卷模板"),

    /** 既没索引也没模板，只能单独展示。 */
    LOOSE("独立内容"),
}

/** 完整试卷。 */
data class Paper(
    val id: String,
    val title: String,
    val subtitle: String,
    val source: PaperSource,
    val sections: List<Section>,
    /** 下载/更新时间（取数据索引与各 section 目录 mtime 的最大值），用于排序与「最新」标记。 */
    val updatedMs: Long = 0L,
)

data class Section(
    val title: String,
    val kind: SectionKind,
    /** 听力原文 / 朗读原文（已清洗）。 */
    val passage: String? = null,
    /** 音频文件名，如 content.mp3。 */
    val audio: String? = null,
    /** 音频逻辑路径（扫描时补全，Shizuku/Root 通道需要先导出到缓存才能播放）。 */
    val audioLogicalPath: String? = null,
    /** 音频真实可读路径（本地通道可直接播放时给出）。 */
    val audioPath: String? = null,
    /** 该 section 对应的 resource 目录名，便于回溯素材。 */
    val dirName: String? = null,
    val questions: List<Question> = emptyList(),
    /** 信息转述/听后转述的要点。 */
    val keyPoints: List<String> = emptyList(),
    val topic: String? = null,
    val introHtml: String? = null,
)

enum class SectionKind(val label: String) {
    CHOICE("选择题"),
    QA("问答题"),
    PICTURE("转述"),
    FILL("填空"),
    READING("朗读"),
    UNKNOWN("未知题型"),
}

data class Question(
    /** 官方题号，如 "1"；没有时用组内序号。 */
    val number: String,
    val prompt: String,
    val answers: List<String> = emptyList(),
    val options: List<AnswerOption> = emptyList(),
    val keywords: String? = null,
)

data class AnswerOption(
    val label: String,
    val text: String,
)

/** 一次扫描的总产出。 */
data class ScanResult(
    val papers: List<PaperSummary>,
    val diagnostics: List<String>,
    val channelLabel: String,
    val scannedAtMs: Long = System.currentTimeMillis(),
)
