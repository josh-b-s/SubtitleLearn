package com.example.subtitlelearn.srs

import android.content.Context
import com.example.subtitlelearn.dictionary.Dictionary
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

object SrsStore {
    private const val PREFS = "srs_prefs"

    // Each dictionary gets its own JSON blob: "srs_state_zh-en.tsv" etc.
    private fun stateKey(dict: String) = "srs_state_$dict"
    private fun historyKey(dict: String) = "srs_history_$dict"

    data class CardState(
        val word: String,
        val repetitions: Int,
        val easeFactor: Double,
        val intervalDays: Int,
        val dueEpochDay: Long,
        val lastQuality: Int
    )

    data class ReviewRecord(
        val epochDay: Long,
        val quality: Int
    )

    fun gradeLabel(quality: Int): String = when (quality) {
        1    -> "No idea"
        3    -> "Roughly"
        5    -> "Got it"
        else -> "Unrated"
    }

    private fun today(): Long = LocalDate.now().toEpochDay()

    // ── Card ops ──────────────────────────────────────────────────────────────

    fun getState(context: Context, word: String, dict: String = Dictionary.currentFile): CardState? {
        val obj = loadCards(context, dict).optJSONObject(word) ?: return null
        return CardState(
            word         = word,
            repetitions  = obj.optInt("rep", 0),
            easeFactor   = obj.optDouble("ef", 2.5),
            intervalDays = obj.optInt("iv", 0),
            dueEpochDay  = obj.optLong("due", today()),
            lastQuality  = obj.optInt("q", -1)
        )
    }

    fun isSuppressed(context: Context, word: String, dict: String = Dictionary.currentFile): Boolean {
        val state = getState(context, word, dict) ?: return false
        return state.dueEpochDay > today()
    }

    fun dueWords(context: Context, dict: String = Dictionary.currentFile): List<String> {
        val all = loadCards(context, dict)
        val t = today()
        return all.keys().asSequence()
            .filter { all.getJSONObject(it).optLong("due", t) <= t }
            .toList()
    }

    fun allTracked(context: Context, dict: String = Dictionary.currentFile): List<String> =
        loadCards(context, dict).keys().asSequence().toList()

    fun allCards(context: Context, dict: String = Dictionary.currentFile): List<CardState> =
        allTracked(context, dict).mapNotNull { getState(context, it, dict) }
            .sortedByDescending { it.dueEpochDay - it.intervalDays }

    fun removeCard(context: Context, word: String, dict: String = Dictionary.currentFile) {
        val all = loadCards(context, dict)
        all.remove(word)
        saveCards(context, dict, all)
    }

    fun review(context: Context, word: String, quality: Int, dict: String = Dictionary.currentFile) {
        val all = loadCards(context, dict)
        val existing = all.optJSONObject(word)
        var reps     = existing?.optInt("rep", 0) ?: 0
        var ef       = existing?.optDouble("ef", 2.5) ?: 2.5
        var interval = existing?.optInt("iv", 0) ?: 0

        if (quality < 3) {
            reps = 0; interval = 1
        } else {
            interval = when (reps) {
                0    -> 1
                1    -> 6
                else -> max(1, (interval * ef).roundToInt())
            }
            reps += 1
        }
        ef = max(1.3, ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)))

        all.put(word, JSONObject().apply {
            put("rep", reps); put("ef", ef); put("iv", interval)
            put("due", today() + interval); put("q", quality)
        })
        saveCards(context, dict, all)

        // Append to history for stats
        appendHistory(context, dict, ReviewRecord(today(), quality))
    }

    // ── Stats queries ─────────────────────────────────────────────────────────

    data class DailyCount(val epochDay: Long, val count: Int)

    /** Reviews per day for the last N days — used for the heatmap. */
    fun reviewsPerDay(context: Context, dict: String = Dictionary.currentFile, days: Int = 90): List<DailyCount> {
        val cutoff = today() - days
        val records = loadHistory(context, dict).filter { it.epochDay >= cutoff }
        return records.groupBy { it.epochDay }
            .map { (day, reviews) -> DailyCount(day, reviews.size) }
            .sortedBy { it.epochDay }
    }

    /** Words learned (at least 1 successful review) per week. */
    fun wordsLearnedPerWeek(context: Context, dict: String = Dictionary.currentFile, weeks: Int = 12): List<Pair<Long, Int>> {
        val cutoff = today() - (weeks * 7L)
        val cards = allCards(context, dict)
        // Approximate "first learned" as dueEpochDay - intervalDays
        return cards
            .map { card -> card.dueEpochDay - card.intervalDays }
            .filter { it >= cutoff && it <= today() }
            .groupBy { epochDay -> epochDay / 7 } // week bucket
            .map { (weekBucket, days) -> (weekBucket * 7) to days.size }
            .sortedBy { it.first }
    }

    /** Retention rate: Got it (quality >= 3) / total reviews in window. */
    fun retentionRate(context: Context, dict: String = Dictionary.currentFile, days: Int): Float {
        val cutoff = today() - days
        val records = loadHistory(context, dict).filter { it.epochDay >= cutoff }
        if (records.isEmpty()) return 0f
        return records.count { it.quality >= 3 }.toFloat() / records.size
    }

    /** Current streak — consecutive days with at least one review. */
    fun currentStreak(context: Context, dict: String = Dictionary.currentFile): Int {
        val reviewDays = loadHistory(context, dict)
            .map { it.epochDay }.toSortedSet()
        var streak = 0
        var day = today()
        // Allow today to not yet have a review (streak doesn't break until tomorrow)
        if (!reviewDays.contains(day)) day -= 1
        while (reviewDays.contains(day)) {
            streak++
            day--
        }
        return streak
    }

    fun totalReviews(context: Context, dict: String = Dictionary.currentFile): Int =
        loadHistory(context, dict).size

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun loadCards(context: Context, dict: String): JSONObject {
        val raw = prefs(context).getString(stateKey(dict), null) ?: return JSONObject()
        return try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
    }

    private fun saveCards(context: Context, dict: String, obj: JSONObject) {
        prefs(context).edit().putString(stateKey(dict), obj.toString()).apply()
    }

    private fun appendHistory(context: Context, dict: String, record: ReviewRecord) {
        val history = loadHistory(context, dict).toMutableList()
        history.add(record)
        // Trim to last 365 days to keep prefs size sane
        val cutoff = today() - 365
        val trimmed = history.filter { it.epochDay >= cutoff }
        val arr = org.json.JSONArray()
        trimmed.forEach { r ->
            arr.put(JSONObject().apply { put("d", r.epochDay); put("q", r.quality) })
        }
        prefs(context).edit().putString(historyKey(dict), arr.toString()).apply()
    }

    private fun loadHistory(context: Context, dict: String): List<ReviewRecord> {
        val raw = prefs(context).getString(historyKey(dict), null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ReviewRecord(obj.getLong("d"), obj.getInt("q"))
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Dev/seed only — reviews a word as if it happened on a specific past day.
     * Does not apply SM-2 interval logic correctly across multiple backdated reviews;
     * just injects plausible state + history for UI testing.
     */
    fun reviewOnDay(context: Context, word: String, quality: Int, epochDay: Long, dict: String = Dictionary.currentFile) {
        val all = loadCards(context, dict)
        val existing = all.optJSONObject(word)
        var reps     = (existing?.optInt("rep", 0) ?: 0) + 1
        val ef       = max(1.3, (existing?.optDouble("ef", 2.5) ?: 2.5) +
                (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)))
        val interval = if (quality < 3) 1 else when (reps) { 1 -> 1; 2 -> 6; else -> max(1, (6 * ef).roundToInt()) }

        all.put(word, JSONObject().apply {
            put("rep", reps); put("ef", ef); put("iv", interval)
            put("due", epochDay + interval); put("q", quality)
        })
        saveCards(context, dict, all)

        // Inject history record at the backdated day
        val history = loadHistory(context, dict).toMutableList()
        history.add(ReviewRecord(epochDay, quality))
        val arr = org.json.JSONArray()
        history.filter { it.epochDay >= epochDay - 365 }
            .forEach { r -> arr.put(JSONObject().apply { put("d", r.epochDay); put("q", r.quality) }) }
        prefs(context).edit().putString(historyKey(dict), arr.toString()).apply()
    }
}
