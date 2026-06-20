package com.example.subtitlelearn

import android.content.Context

/**
 * Thin wrapper kept for call-site compatibility.
 * "Known" now means "has an SRS card and isn't due for review yet."
 * Marking known/unknown grades the card directly (good/forgot).
 */
object KnownWordsStore {
    fun isKnown(context: Context, word: String): Boolean = SrsStore.isSuppressed(context, word)

    fun markKnown(context: Context, word: String) = SrsStore.review(context, word, quality = 4)

    fun markUnknown(context: Context, word: String) = SrsStore.review(context, word, quality = 1)

    fun allKnown(context: Context): List<String> =
        SrsStore.allTracked(context).filter { SrsStore.isSuppressed(context, it) }.sorted()
}