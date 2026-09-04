package com.qingsheng.ime.skill

import org.json.JSONObject

data class StrategyCard(
    val key: String,
    val name: String,
    val principle: String,
    val stages: List<Int>
)

class StrategyLibrary(private val source: String) {

    private val cards = mutableListOf<StrategyCard>()

    init {
        val root = JSONObject(source)
        for (key in root.keys()) {
            val o = root.getJSONObject(key)
            val stages = o.stringList("stages").mapNotNull { it.toIntOrNull() }
            cards.add(
                StrategyCard(
                    key = key,
                    name = o.optString("name", key),
                    principle = o.optString("principle", o.optString("detail", "")),
                    stages = stages
                )
            )
        }
    }

    fun forStage(stageId: Int, limit: Int = 3): List<StrategyCard> {
        val stageCards = cards.filter { stageId in it.stages && it.principle.isNotBlank() }
        val base = cards.filter { it.key == "low_neediness" || it.key == "topic_ladder" }
        return (stageCards + base.filter { it !in stageCards }).distinctBy { it.key }.take(limit)
    }

    fun find(key: String): StrategyCard? = cards.firstOrNull { it.key == key }

    fun stepOf(strategyKey: String, stepKey: String): String? {
        val o = runCatching { JSONObject(source).getJSONObject(strategyKey) }.getOrNull() ?: return null
        val steps = o.optJSONArray("steps") ?: return null
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            if (step.optString("key") == stepKey) return step.optString("detail")
        }
        return null
    }
}
