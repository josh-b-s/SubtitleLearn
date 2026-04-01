package com.example.subtitlelearn

import android.content.Context
import android.util.Log

object Dictionary {

    private val dictionary = HashMap<String, Pair<String, String>>()

    fun load(context: Context) {
        if (dictionary.isNotEmpty()) return

        try {
            context.assets.open("PD-English-Definitions.tsv")
                .bufferedReader()
                .useLines { lines ->

                    lines.forEach { line ->
                        if (line.isBlank()) return@forEach

                        val parts = line.split("\t")

                        if (parts.size < 3) return@forEach

                        val word = parts[0].trim()
                        val meaning = shortMeaning(parts[1].trim())
                        val pinyin = parts[2].trim()

                        if (meaning.isNotEmpty()) {
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

        val parts = def.split("/", ";")
            .map {
                it.trim()
                    .lowercase()
                    .removePrefix("to ")
                    .replace(Regex("\\s+"), " ")
            }
            .filter { it.isNotEmpty() }

        // remove duplicates while keeping order
        val unique = LinkedHashSet(parts)

        // limit lines
        return unique.take(5).joinToString("\n")
    }
}