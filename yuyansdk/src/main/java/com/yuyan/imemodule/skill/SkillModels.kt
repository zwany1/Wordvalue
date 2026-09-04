package com.yuyan.imemodule.skill

import org.json.JSONObject

enum class ReplyStyle(val label: String, val tag: String, val letter: String) {
    NATURAL("自然", "🌿", "A"),
    GENTLE("温柔", "❤️", "B"),
    FUNNY("幽默", "😂", "C");

    companion object {
        fun fromLetter(letter: String): ReplyStyle? =
            entries.firstOrNull { it.letter.equals(letter.trim(), ignoreCase = true) }
    }
}

data class Stage(
    val id: Int,
    val name: String,
    val goal: String,
    val strategies: List<String>,
    val taboos: List<String>,
    val upgrade: String
)

data class StagesData(val defaultStage: Int, val stages: List<Stage>) {
    fun stageOf(id: Int): Stage = stages.firstOrNull { it.id == id } ?: stages.first { it.id == defaultStage }
}

data class ForcedStageSignal(val keywords: List<String>, val minStage: Int, val reason: String)

data class StopRule(val keywords: List<String>, val action: String)

data class ColdReading(val openers: List<String>, val principle: String)

data class SignalsData(
    val ioi: List<String>,
    val iod: List<String>,
    val judgement: String,
    val forcedStage: List<ForcedStageSignal>,
    val stop: StopRule,
    val coldReading: ColdReading
)

data class CommonRules(val principles: List<String>, val avoid: List<String>)

data class EmotionRule(
    val name: String,
    val keywords: List<String>,
    val chain: String,
    val hint: String,
    val avoid: List<String>
)

data class TopicPush(val shallowTopics: List<String>, val pushDirections: List<String>, val rule: String)

data class StrategiesData(
    val common: CommonRules,
    val emotions: List<EmotionRule>,
    val topicPush: TopicPush
) {
    fun emotionOf(name: String): EmotionRule = emotions.firstOrNull { it.name == name }
        ?: emotions.first { it.name == EMOTION_NEUTRAL }

    companion object { const val EMOTION_NEUTRAL = "普通" }
}

data class Example(val stage: Int, val scenario: String, val her: String, val reply: String, val why: String)

data class ExamplesData(val examples: List<Example>)

data class Platform(val name: String, val feature: String, val chatStyle: String, val opening: String, val taboos: List<String>)

data class PlatformsData(val platforms: List<Platform>)

data class Concept(val name: String, val point: String)

data class MindsetData(val concepts: List<Concept>)

data class StyleTemplates(val natural: List<String>, val gentle: List<String>, val funny: List<String>) {
    fun of(style: ReplyStyle): List<String> = when (style) {
        ReplyStyle.NATURAL -> natural
        ReplyStyle.GENTLE -> gentle
        ReplyStyle.FUNNY -> funny
    }
}

data class TemplateData(val entries: Map<String, StyleTemplates>)

fun JSONObject.stringList(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return List(array.length()) { array.optString(it) }
}
