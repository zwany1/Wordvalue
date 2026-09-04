package com.qingsheng.ime.skill

data class StageResult(val stage: Stage, val forcedBySignal: String?)

class StageEngine(private val stages: StagesData, private val signals: SignalsData) {

    fun analyze(message: String, baseStageId: Int = stages.defaultStage): StageResult {
        val forced = signals.forcedStage.firstOrNull { signal ->
            signal.keywords.any { it in message }
        }
        val base = stages.stageOf(baseStageId)
        return if (forced != null && forced.minStage > base.id) {
            StageResult(stages.stageOf(forced.minStage), forced.reason)
        } else {
            StageResult(base, forced?.reason?.takeIf { forced.minStage == base.id })
        }
    }
}
