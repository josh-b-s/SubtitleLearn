package com.example.subtitlelearn

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

object SrsStore {
    private const val PREFS = "srs_prefs"
    private const val KEY = "srs_state_json"

    data class CardState(
        val word: String,
        val repetitions: Int,
        val easeFactor: Double,
        val intervalDays: Int,
        val dueEpochDay: Long,
        val lastQuality: Int
    )

    fun gradeLabel(quality: Int): String = when (quality) {
        0, 1, 2 -> "Forgot"
        3 -> "Hard"
        4 -> "Good"
        5 -> "Easy"
        else -> "Unrated"
    }

    private fun today(): Long = LocalDate.now().toEpochDay()

    fun getState(context: Context, word: String): CardState? {
        val obj = loadAll(context).optJSONObject(word) ?: return null
        return CardState(
            word = word,
            repetitions = obj.optInt("rep", 0),
            easeFactor = obj.optDouble("ef", 2.5),
            intervalDays = obj.optInt("iv", 0),
            dueEpochDay = obj.optLong("due", today()),
            lastQuality = obj.optInt("q", -1)
        )
    }

    fun isSuppressed(context: Context, word: String): Boolean {
        val state = getState(context, word) ?: return false
        return state.dueEpochDay > today()
    }

    fun dueWords(context: Context): List<String> {
        val all = loadAll(context)
        val t = today()
        return all.keys().asSequence().filter { word ->
            all.getJSONObject(word).optLong("due", t) <= t
        }.toList()
    }

    fun allTracked(context: Context): List<String> = loadAll(context).keys().asSequence().toList()

    /** Full card list, newest review first — backs the Known Words history screen. */
    fun allCards(context: Context): List<CardState> =
        allTracked(context).mapNotNull { getState(context, it) }
            .sortedByDescending { it.dueEpochDay - it.intervalDays } // approximates last-reviewed order

    fun removeCard(context: Context, word: String) {
        val all = loadAll(context)
        all.remove(word)
        saveAll(context, all)
    }

    fun review(context: Context, word: String, quality: Int) {
        val all = loadAll(context)
        val existing = all.optJSONObject(word)
        var reps = existing?.optInt("rep", 0) ?: 0
        var ef = existing?.optDouble("ef", 2.5) ?: 2.5
        var interval = existing?.optInt("iv", 0) ?: 0

        if (quality < 3) {
            reps = 0
            interval = 1
        } else {
            interval = when (reps) {
                0 -> 1
                1 -> 6
                else -> max(1, (interval * ef).roundToInt())
            }
            reps += 1
        }

        ef = max(1.3, ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)))

        val obj = JSONObject().apply {
            put("rep", reps)
            put("ef", ef)
            put("iv", interval)
            put("due", today() + interval)
            put("q", quality)
        }
        all.put(word, obj)
        saveAll(context, all)
    }

    private fun loadAll(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY, null) ?: return JSONObject()
        return try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
    }

    private fun saveAll(context: Context, obj: JSONObject) {
        prefs(context).edit().putString(KEY, obj.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}