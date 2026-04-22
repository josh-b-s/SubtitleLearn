package com.example.subtitlelearn

import android.content.Context
import android.util.Log

object Dictionary {

    private val dictionary = HashMap<String, Pair<String, String>>()

    fun load(context: Context) {
        if (dictionary.isNotEmpty()) return

        try {
            context.assets.open("dictionaries/zh-en.tsv")
                .bufferedReader()
                .useLines { lines ->

                    lines.forEach { line ->
                        if (line.isBlank()) return@forEach

                        val parts = line.split("\t")
                        if (parts.size < 2) return@forEach

                        val word = parts[0].trim()
                        val meaning = shortMeaning(parts[1].trim())

                        val pinyin = if (parts.size >= 3) parts[2].trim() else ""

                        if (meaning.isNotEmpty() && !dictionary.containsKey(word)) {
                            dictionary[word] = Pair(meaning, pinyin)
                        }
                    }
                }

            Log.i("DICT", "Loaded entries: ${dictionary.size}")

        } catch (e: Exception) {
            Log.e("DICT", "Failed loading dictionary", e)
        }
    }

    fun getMeaning(word: String): String {
        return dictionary[word]?.first ?: ""
    }

    fun getPinyin(word: String): String {
        return dictionary[word]?.second ?: ""
    }

    private fun shortMeaning(def: String): String {

        val parts = def.split(Regex("\\s*[/;|,•·]\\s*|\\s+-\\s+"))
            .map {
                it.trim()
                    .lowercase()
                    .removePrefix("to ")
                    .replace(Regex("\\(.*?\\)"), "")
            }
            .filter { it.isNotEmpty() }

        val unique = LinkedHashSet(parts)

        return unique.take(3).joinToString("\n")
    }
}