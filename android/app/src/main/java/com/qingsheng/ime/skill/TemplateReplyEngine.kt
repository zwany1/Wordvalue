package com.qingsheng.ime.skill

import kotlin.random.Random

class TemplateReplyEngine(private val data: TemplateData) {

    private val cursors = mutableMapOf<String, Int>()
    private val random = Random.Default

    fun generate(emotionName: String, exclude: Collection<String> = emptyList()): Map<ReplyStyle, String> {
        val templates = data.entries[emotionName] ?: data.entries[StrategiesData.EMOTION_NEUTRAL]
        return ReplyStyle.entries.associateWith { style ->
            pick(emotionName, style, templates?.of(style).orEmpty(), exclude)
        }
    }

    private fun pick(emotionName: String, style: ReplyStyle, pool: List<String>, exclude: Collection<String>): String {
        if (pool.isEmpty()) return ""
        val candidates = pool.filter { it !in exclude }
        if (candidates.isEmpty()) return advance(emotionName, style, pool)

        val cursorKey = cursorKey(emotionName, style)
        val cursor = cursors.getOrDefault(cursorKey, random.nextInt(candidates.size))
        val next = (cursor + 1) % candidates.size
        cursors[cursorKey] = next
        return candidates[next % candidates.size]
    }

    private fun advance(emotionName: String, style: ReplyStyle, pool: List<String>): String {
        val cursorKey = cursorKey(emotionName, style)
        val cursor = cursors.getOrDefault(cursorKey, 0) + 1
        cursors[cursorKey] = cursor
        return pool[Math.floorMod(cursor, pool.size)]
    }

    private fun cursorKey(emotionName: String, style: ReplyStyle) = "$emotionName:${style.name}"
}
