package com.example.subtitlelearn

import android.content.Context
import androidx.core.content.edit

object SuppressionSettings {
    private const val PREFS = "suppression_prefs"
    private const val KEY_ENABLED = "suppression_enabled"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true) // default ON

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}