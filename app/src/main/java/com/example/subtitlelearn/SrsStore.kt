package com.example.subtitlelearn

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * SM-2 spaced repetition store, keyed by word.
 * Quality scale (Anki-style, 0-5):
 *   0-2 = forgot / didn't know -> reset interval
 *   3   = hard but recalled
 *   4   = good
 *   5   = easy
 */
object SrsStore {
    private const val PREFS = "srs_prefs"
    private const val KEY = "srs_state_json"

    data class CardState(
        val word: String,
        val repetitions: Int,
        val easeFactor: Double,
        val intervalDays: Int,
        val dueEpochDay: Long
    )

    private fun today(): Long = LocalDate.now().toEpochDay()

    fun getState(context: Context, word: String): CardState? {
        val obj = loadAll(context).optJSONObject(word) ?: return null
        return CardState(
            word = word,
            repetitions = obj.optInt("rep", 0),
            easeFactor = obj.optDouble("ef", 2.5),
            intervalDays = obj.optInt("iv", 0),
            dueEpochDay = obj.optLong("due", today())
        )
    }

    /** True if the word has a card AND it's not yet due (i.e. comfortably "known" right now). */
    fun isSuppressed(context: Context, word: String): Boolean {
        val state = getState(context, word) ?: return false
        return state.dueEpochDay > today()
    }

    /** Words whose due date has arrived (or have never been reviewed) — candidates for quizzing. */
    fun dueWords(context: Context): List<String> {
        val all = loadAll(context)
        val t = today()
        return all.keys().asSequence().filter { word ->
            val obj = all.getJSONObject(word)
            obj.optLong("due", t) <= t
        }.toList()
    }

    fun allTracked(context: Context): List<String> = loadAll(context).keys().asSequence().toList()

    fun removeCard(context: Context, word: String) {
        val all = loadAll(context)
        all.remove(word)
        saveAll(context, all)
    }

    /**
     * Apply a review result using SM-2. quality is 0-5.
     * New cards start at repetitions=0, ease=2.5, interval=0.
     */
    fun review(context: Context, word: String, quality: Int) {
        val all = loadAll(context)
        val existing = all.optJSONObject(word)
        var reps = existing?.optInt("rep", 0) ?: 0
        var ef = existing?.optDouble("ef", 2.5) ?: 2.5
        var interval = existing?.optInt("iv", 0) ?: 0

        if (quality < 3) {
            // Forgot it — reset repetitions, short interval, ease takes a hit
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

        // SM-2 ease factor update formula
        ef = max(1.3, ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)))

        val obj = JSONObject().apply {
            put("rep", reps)
            put("ef", ef)
            put("iv", interval)
            put("due", today() + interval)
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