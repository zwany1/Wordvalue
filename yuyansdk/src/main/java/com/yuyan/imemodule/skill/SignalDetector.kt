package com.yuyan.imemodule.skill

data class SignalReport(
    val ioiCount: Int,
    val iodCount: Int,
    val trend: String,
    val evidence: List<String>
) {
    val summary: String
        get() = "IOI $ioiCount / IOD $iodCount，趋势$trend"

    val direction: String
        get() = when {
            ioiCount >= iodCount * 2 -> "正向"
            iodCount >= ioiCount * 2 -> "负向"
            else -> "平稳"
        }
}

class SignalDetector(private val signals: SignalsData) {

    private val ioiHints = listOf(
        "主动" to "主动找话题/发起互动",
        "问" to "对你的事表现出好奇",
        "哈哈" to "情绪投入，笑点共鸣",
        "想你" to "明确好感表达",
        "周末" to "谈及时间安排，可能有空窗",
        "上次" to "记得之前的话题，有延续性",
        "下次" to "提及未来，潜在的见面信号"
    )

    private val iodHints = listOf(
        "嗯" to "回复敷衍",
        "哦" to "回复敷衍",
        "在忙" to "回避互动",
        "再说吧" to "延迟回应",
        "随便" to "投入度低",
        "不用了" to "拒绝互动"
    )

    fun analyze(message: String, recentHistory: List<String> = emptyList()): SignalReport {
        val corpus = listOf(message) + recentHistory
        val evidence = mutableListOf<String>()
        var ioi = 0
        var iod = 0

        for (text in corpus) {
            for ((keyword, desc) in ioiHints) {
                if (keyword in text && desc !in evidence) {
                    ioi++
                    evidence.add(desc)
                }
            }
            for ((keyword, desc) in iodHints) {
                if (text.length <= IOD_SHORT_LIMIT && keyword in text && desc !in evidence) {
                    iod++
                    evidence.add(desc)
                }
            }
        }

        val trend = when {
            ioi > iod -> "正向（信号密度倾向兴趣）"
            iod > ioi -> "负向（信号密度倾向冷淡）"
            else -> "平稳"
        }
        return SignalReport(ioi, iod, trend, evidence.take(5))
    }

    private companion object {
        const val IOD_SHORT_LIMIT = 6
    }
}
