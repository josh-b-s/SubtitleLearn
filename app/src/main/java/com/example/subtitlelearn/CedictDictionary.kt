package com.example.subtitlelearn

import android.content.Context
import android.util.Log

object CedictDictionary {

    private val dictionary = HashMap<String, String>()

    fun load(context: Context) {
        if (dictionary.isNotEmpty()) return

        try {
            context.assets.open("PD-English-Definitions.tsv")
                .bufferedReader()
                .useLines { lines ->

                    lines.forEach { line ->
                        if (line.isBlank()) return@forEach

                        val parts = line.split("\t")

                        if (parts.size < 2) return@forEach

                        val word = parts[0].trim()
                        val defRaw = parts.last().trim()

                        val shortMeaning = shortMeaning(defRaw)

                        if (shortMeaning.isNotEmpty()) {
                            dictionary[word] = shortMeaning
                        }
                    }
                }

            Log.i("DICT", "Loaded entries: ${dictionary.size}")

        } catch (e: Exception) {
            Log.e("DICT", "Failed loading dictionary", e)
        }
    }

    fun get(word: String): String {
        return dictionary[word] ?: ""
    }

    fun getMeaning(word: String): String {

        // exact match first
        val direct = get(word)
        if (direct.isNotEmpty()) return direct

        // try shorter substrings
        for (i in word.length - 1 downTo 1) {
            val sub = word.substring(0, i)
            val m = get(sub)
            if (m.isNotEmpty()) return m
        }

        return ""
    }

    private fun shortMeaning(def: String): String {
        // Example: "talk over/to discuss/consult"

        val parts = def.split("/")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // remove "to " for verbs
        val cleaned = parts.map {
            it.removePrefix("to ").trim()
        }

        // take 1–2 meanings max (prevents overflow)
        return cleaned.take(2).joinToString("; ")
    }
}