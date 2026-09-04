package com.qingsheng.ime.skill

data class StrategyContext(
    val stage: Stage,
    val forcedReason: String?,
    val emotion: EmotionRule,
    val emotionResult: EmotionResult
)

class StrategyEngine(private val data: StrategiesData) {

    fun resolve(stageResult: StageResult, emotionResult: EmotionResult): StrategyContext {
        val emotion = data.emotionOf(emotionResult.emotion)
        return StrategyContext(stageResult.stage, stageResult.forcedBySignal, emotion, emotionResult)
    }
}
