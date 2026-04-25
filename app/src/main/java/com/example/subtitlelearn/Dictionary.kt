package com.example.subtitlelearn

import android.content.Context
import android.util.Log

object Dictionary {
    private val entries = HashMap<String, Pair<String, String>>()
    private const val MAX_WORD_LEN = 6

    fun load(context: Context) {
        if (entries.isNotEmpty()) return
        try {
            context.assets.open("dictionaries/zh-en.tsv").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val parts = line.split("\t")
                    if (parts.size < 2) return@forEach
                    val word = parts[0].trim()
                    if (entries.containsKey(word)) return@forEach
                    val meaning = shortMeaning(parts[1].trim())
                    val pinyin = parts.getOrElse(2) { "" }.trim()
                    if (meaning.isNotEmpty()) entries[word] = meaning to pinyin
                }
            }
            Log.i("DICT", "Loaded ${entries.size} entries")
        } catch (e: Exception) {
            Log.e("DICT", "Failed to load dictionary", e)
        }
    }

    fun getMeaning(word: String) = entries[word]?.first.orEmpty()
    fun getPinyin(word: String) = entries[word]?.second.orEmpty()

    private fun shortMeaning(def: String): String =
        def.split(Regex("""\s*[/;|,·•]\s*|\s+-\s+"""))
            .map {
                it.trim().lowercase(java.util.Locale.ROOT).removePrefix("to ")
                    .replace(Regex("\\(.*?\\)"), "").replace(Regex("\\s+"), " ")
            }
            .filter { it.isNotEmpty() }
            .let { LinkedHashSet(it).take(3).joinToString("/") }

    fun segment(text: String): List<String> {
        val trimmed = text.trim()

        // Space-delimited (e.g. Pinyin input or already-tokenised text)
        if (trimmed.contains(' ')) return trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }

        val result = mutableListOf<String>()
        var i = 0

        while (i < trimmed.length) {
            val end = minOf(i + MAX_WORD_LEN, trimmed.length)
            val match = (end downTo i + 1)
                .map { trimmed.substring(i, it) }
                .firstOrNull { getMeaning(it).isNotEmpty() }

            if (match != null) {
                result += match
                i += match.length
            } else {
                result += trimmed[i].toString()
                i++
            }
        }

        return result
    }
}