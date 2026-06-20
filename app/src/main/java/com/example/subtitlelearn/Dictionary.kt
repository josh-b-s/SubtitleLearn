package com.example.subtitlelearn

import android.content.Context
import android.util.Log

object Dictionary {
    private val entries = HashMap<String, Pair<String, String>>()
    private const val MAX_WORD_LEN = 6
    private const val DEFAULT_FILE = "zh-en.tsv"

    var currentFile: String = DEFAULT_FILE
        private set

    fun load(context: Context, fileName: String = DEFAULT_FILE) {
        if (entries.isNotEmpty() && fileName == currentFile) return
        entries.clear()
        currentFile = fileName
        try {
            context.assets.open("dictionaries/$fileName").bufferedReader().useLines { lines ->
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
            Log.i("DICT", "Loaded ${entries.size} entries from $fileName")
        } catch (e: Exception) {
            Log.e("DICT", "Failed to load dictionary $fileName", e)
        }
    }

    /** Force-reload a different dictionary file, replacing all current entries. */
    fun switchTo(context: Context, fileName: String) {
        entries.clear()
        load(context, fileName)
    }

    /** Lists all dictionary files available under assets/dictionaries/. */
    fun listAvailable(context: Context): List<String> =
        try {
            context.assets.list("dictionaries")?.toList().orEmpty()
        } catch (e: Exception) {
            Log.e("DICT", "Failed to list dictionaries", e)
            emptyList()
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