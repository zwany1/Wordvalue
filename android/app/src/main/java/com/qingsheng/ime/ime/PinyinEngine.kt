package com.qingsheng.ime.ime

import android.content.Context

class PinyinEngine(context: Context) {

    private val dict = LinkedHashMap<String, List<String>>()

    init {
        context.assets.open(DICT_ASSET).bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val separator = line.indexOf(':')
                if (separator <= 0) continue
                val key = line.substring(0, separator).lowercase()
                val words = line.substring(separator + 1)
                    .split('|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (key.isNotEmpty() && words.isNotEmpty()) {
                    dict[key] = words
                }
            }
        }
    }

    fun candidates(input: String, limit: Int = 9): List<String> {
        val lower = input.lowercase().trim()
        if (lower.isEmpty() || lower.any { it !in 'a'..'z' }) return emptyList()

        val exact = dict[lower].orEmpty()
        if (exact.isNotEmpty()) return exact.take(limit)

        val prefixes = dict.entries.asSequence()
            .filter { it.key.length > lower.length && it.key.startsWith(lower) }
            .sortedBy { it.key.length }
            .flatMap { entry -> entry.value.asSequence() }
            .toList()

        return prefixes.ifEmpty { listOf(lower) }.distinct().take(limit)
    }

    private companion object {
        const val DICT_ASSET = "pinyin_dict.txt"
    }
}
