package com.qingsheng.ime.skill

data class StageAssessment(
    val stageId: Int,
    val stageName: String,
    val confidence: Float,
    val evidence: List<String>
)

class StageDetector(private val stages: StagesData, private val signals: SignalsData) {

    fun assess(message: String, recentReplies: List<String> = emptyList(), baseStageId: Int = stages.defaultStage): StageAssessment {
        val combined = message + " " + recentReplies.joinToString(" ")

        val forced = signals.forcedStage.firstOrNull { signal ->
            signal.keywords.any { it in combined }
        }
        if (forced != null && forced.minStage >= baseStageId) {
            return StageAssessment(
                stageId = forced.minStage,
                stageName = stages.stageOf(forced.minStage).name,
                confidence = 0.9f,
                evidence = listOf(forced.reason)
            )
        }

        val upgradeSignals = stages.stages
            .filter { it.id > baseStageId }
            .flatMap { stage -> stage.strategies.map { stage.id to it } }

        val base = stages.stageOf(baseStageId)
        val evidence = mutableListOf<String>()
        for ((stageId, hint) in upgradeSignals) {
            if (hint.length >= MIN_HINT_LEN && hint in combined) evidence.add("阶段${stageId}信号：$hint")
        }
        if (evidence.isNotEmpty()) {
            val top = evidence.maxOf {
                it.substringAfter("阶段").substringBefore("信号").toIntOrNull() ?: baseStageId
            }
            return StageAssessment(top, stages.stageOf(top).name, 0.6f, evidence.take(3))
        }

        return StageAssessment(base.id, base.name, 0.3f, listOf("默认阶段，无明确信号"))
    }

    private companion object {
        const val MIN_HINT_LEN = 4
    }
}
