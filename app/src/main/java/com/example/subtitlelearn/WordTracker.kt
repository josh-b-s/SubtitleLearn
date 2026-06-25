package com.example.subtitlelearn

import android.content.Context
import android.util.Log

object WordTracker {
    private val counts = HashMap<String, Int>()

    fun record(word: String) {
        if (word.isBlank()) return
        counts[word] = (counts[word] ?: 0) + 1
    }

    fun hasSeen(word: String) = counts.containsKey(word)

    fun reset() {
        counts.clear()
        Log.d("TRACKER", "reset")
    }

    fun topWords(context: Context, n: Int = 10): List<Pair<String, Int>> =
        counts.entries
            .filter { it.value > 0 && !KnownWordsStore.isKnown(context, it.key) }
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to it.value }

    fun hasData() = counts.isNotEmpty()
}