package com.example.subtitlelearn

import android.content.Context
import android.util.Log

object CedictDictionary {

    private val dictionary = HashMap<String, String>()

    fun load(context: Context) {
        if (dictionary.isNotEmpty()) return

        try {
            context.assets.open("cedict_ts.u8").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("#")) return@forEach

                    val parts = line.split("/")
                    if (parts.size < 2) return@forEach

                    val head = parts[0]
                    val def = parts[1]

                    val words = head.split(" ")
                    if (words.size < 2) return@forEach

                    val simplified = words[1]
                    val shortMeaning = shortMeaning(def)

                    if (shortMeaning.isNotEmpty()) {
                        dictionary[simplified] = shortMeaning
                    }
                }
            }

            Log.i("CEDICT", "Loaded entries: ${dictionary.size}")
        } catch (e: Exception) {
            Log.e("CEDICT", "Failed loading dictionary", e)
        }
    }

    fun get(word: String): String {
        return dictionary[word] ?: ""
    }

    private fun shortMeaning(def: String): String {
        var result = def

        result = result.replace(Regex("\\(.*?\\)"), "")
        result = result.replace(Regex("\\p{IsHan}+"), "")
        result = result.replace(Regex("\\[[^\\]]*]"), "")

        val parts = result.split("/", ";").map { it.trim() }.filter { it.isNotEmpty() }
        val cleaned = parts.map { it.removePrefix("to ").trim() }.filter { it.isNotEmpty() }

        // Take first non-empty meaning
        return cleaned.firstOrNull() ?: ""
    }

    fun getMeaning(word: String): String {

        val w = get(word)

        if (w.isNotEmpty()) return w

        if (word.length > 1) {
            return get(word.substring(0, 1))
        }

        return ""
    }
}