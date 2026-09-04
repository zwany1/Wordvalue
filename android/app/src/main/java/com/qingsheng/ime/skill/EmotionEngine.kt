package com.qingsheng.ime.skill

data class EmotionResult(val emotion: String, val matchedKeywords: List<String>)

class EmotionEngine(private val data: StrategiesData) {

    private val neutralName = StrategiesData.EMOTION_NEUTRAL
    private val shortMessageWords by lazy {
        data.emotions.firstOrNull { it.name == "冷淡" }?.keywords.orEmpty()
    }
    private val phraseOnlyWords by lazy { listOf("在忙", "先不聊", "再说吧") }

    fun analyze(message: String): EmotionResult {
        val text = message.trim()
        if (text.isEmpty()) return EmotionResult(neutralName, emptyList())

        if (text.length <= 4 && shortMessageWords.any { it in text }) {
            return EmotionResult("冷淡", listOf(text))
        }

        var best: String? = null
        var bestCount = 0
        val bestMatches = mutableListOf<String>()

        for (rule in data.emotions) {
            if (rule.name == "冷淡") continue
            val matched = rule.keywords.filter { it in text }
            if (matched.size > bestCount) {
                bestCount = matched.size
                best = rule.name
                bestMatches.clear()
                bestMatches.addAll(matched)
            }
        }

        if (best == null && phraseOnlyWords.any { it in text }) {
            return EmotionResult("冷淡", phraseOnlyWords.filter { it in text })
        }

        return if (best != null) EmotionResult(best, bestMatches)
        else EmotionResult(neutralName, emptyList())
    }
}
