package com.example.subtitlelearn

object WordTracker {
    private val counts = HashMap<String, Int>()

    fun record(word: String) {
        if (word.isBlank()) return
        counts[word] = (counts[word] ?: 0) + 1
    }

    fun reset() = counts.clear()

    /** Top N words by frequency this session, excluding already-known words. */
    fun topWords(context: android.content.Context, n: Int = 10): List<Pair<String, Int>> =
        counts.entries
            .filter { it.value > 0 && !KnownWordsStore.isKnown(context, it.key) }
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to it.value }

    fun hasData(): Boolean = counts.isNotEmpty()
}