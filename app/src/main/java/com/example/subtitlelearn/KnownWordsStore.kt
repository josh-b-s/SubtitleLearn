package com.example.subtitlelearn

import android.content.Context

object KnownWordsStore {
    private const val PREFS = "known_words_prefs"
    private const val KEY = "known_words_set"

    fun isKnown(context: Context, word: String): Boolean =
        prefs(context).getStringSet(KEY, emptySet())!!.contains(word)

    fun markKnown(context: Context, word: String) {
        val set = prefs(context).getStringSet(KEY, emptySet())!!.toMutableSet()
        set += word
        prefs(context).edit().putStringSet(KEY, set).apply()
    }

    fun markUnknown(context: Context, word: String) {
        val set = prefs(context).getStringSet(KEY, emptySet())!!.toMutableSet()
        set -= word
        prefs(context).edit().putStringSet(KEY, set).apply()
    }

    fun allKnown(context: Context): List<String> =
        prefs(context).getStringSet(KEY, emptySet())!!.toList().sorted()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}