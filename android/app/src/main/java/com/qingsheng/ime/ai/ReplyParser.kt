package com.qingsheng.ime.ai

import com.qingsheng.ime.skill.GeneratorIntent
import com.qingsheng.ime.skill.ReplyStyle

object ReplyParser {

    private val lineRegex = Regex("^\\s*([ABC])\\s*[：:.]\\s*(.+)$")
    private val recoveryRegex = Regex("^\\s*R\\s*[：:]\\s*(.+)$", RegexOption.MULTILINE)

    fun parse(raw: String, intent: GeneratorIntent): Map<ReplyStyle, String> {
        val result = LinkedHashMap<ReplyStyle, String>()
        when (intent) {
            GeneratorIntent.REPLY, GeneratorIntent.FAST -> {
                for (line in raw.lines()) {
                    val match = lineRegex.find(line) ?: continue
                    val style = ReplyStyle.fromLetter(match.groupValues[1]) ?: continue
                    val text = clean(match.groupValues[2], MAX_REPLY_LEN)
                    if (text.isNotEmpty() && style !in result.keys) result[style] = text
                }
            }
            GeneratorIntent.RECOVERY -> {
                val match = recoveryRegex.find(raw)
                val text = if (match != null) match.groupValues[1].trim() else raw.trim()
                if (text.isNotEmpty()) result[ReplyStyle.GENTLE] = clean(text, MAX_RECOVERY_LEN)
            }
            GeneratorIntent.AUTOPILOT, GeneratorIntent.CONSULT, GeneratorIntent.AUDIT -> {
                val text = raw.trim()
                if (text.isNotEmpty()) result[ReplyStyle.NATURAL] = text
            }
        }
        return result
    }

    fun clean(text: String, maxLen: Int): String {
        var t = text.trim().trim('"', '“', '”', '「', '」', '『', '』', '\'')
        if (t.length > maxLen) {
            val cutPoint = t.indexOf('\n')
            if (cutPoint in 1..maxLen) return t.substring(0, cutPoint).trim()
            val lastPunct = t.lastIndexOfAny(charArrayOf('，', '。', '？', '！', ',', '~'))
            t = if (lastPunct in (maxLen - 10)..(t.length - 1)) t.substring(0, lastPunct + 1) else t.substring(0, maxLen)
        }
        return t.trim()
    }

    private const val MAX_REPLY_LEN = 40
    private const val MAX_RECOVERY_LEN = 60
}
