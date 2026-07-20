package com.example.subtitlelearn.dictionary

import android.content.Context
import android.util.Log
import org.json.JSONObject

object Dictionary {
    private val entries = HashMap<String, Pair<String, String>>()
    // Tracks which words in `entries` came from the user (vs. the bundled asset file),
    // so we know what to persist and re-merge on load/switch.
    private val customWords = HashSet<String>()
    private const val MAX_WORD_LEN = 6
    private const val DEFAULT_FILE = "zh-en.tsv"

    private const val PREFS = "dictionary_custom_prefs"
    private fun customKey(fileName: String) = "custom_$fileName"

    var currentFile: String = DEFAULT_FILE
        private set

    private var appContext: Context? = null

    fun load(context: Context, fileName: String = DEFAULT_FILE) {
        appContext = context.applicationContext
        if (entries.isNotEmpty() && fileName == currentFile) return
        entries.clear()
        customWords.clear()
        currentFile = fileName
        try {
            context.assets.open("dictionaries/$fileName").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val parts = line.split("\t")
                    if (parts.size < 2) return@forEach
                    val word = parts[0].trim()
                    if (entries.containsKey(word)) return@forEach
                    val meaning = shortMeaning(parts[1].trim())
                    val pinyin = parts.getOrElse(2) { "" }.trim()
                    if (meaning.isNotEmpty()) entries[word] = meaning to pinyin
                }
            }
            Log.i("DICT", "Loaded ${entries.size} entries from $fileName")
        } catch (e: Exception) {
            Log.e("DICT", "Failed to load dictionary $fileName", e)
        }
        loadCustomEntries(context, fileName)
    }

    /** Force-reload a different dictionary file, replacing all current entries. */
    fun switchTo(context: Context, fileName: String) {
        entries.clear()
        customWords.clear()
        currentFile = "" // force reload below even if fileName equals the old currentFile
        load(context, fileName)
    }

    // ── Custom entry persistence ──────────────────────────────────────────

    private fun loadCustomEntries(context: Context, fileName: String) {
        val raw = prefs(context).getString(customKey(fileName), null) ?: return
        try {
            val obj = JSONObject(raw)
            obj.keys().forEach { word ->
                val entry = obj.getJSONObject(word)
                val meaning = entry.optString("m", "")
                val pinyin = entry.optString("p", "")
                if (meaning.isNotEmpty()) {
                    entries[word] = meaning to pinyin
                    customWords.add(word)
                }
            }
            Log.i("DICT", "Restored ${obj.length()} custom entries for $fileName")
        } catch (e: Exception) {
            Log.e("DICT", "Failed to load custom entries for $fileName", e)
        }
    }

    private fun persistCustomEntries() {
        val context = appContext ?: return
        val obj = JSONObject()
        customWords.forEach { word ->
            entries[word]?.let { (meaning, pinyin) ->
                obj.put(word, JSONObject().apply {
                    put("m", meaning)
                    put("p", pinyin)
                })
            }
        }
        prefs(context).edit().putString(customKey(currentFile), obj.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Lists all dictionary files available under assets/dictionaries/. */
    fun listAvailable(context: Context): List<String> =
        try {
            context.assets.list("dictionaries")?.toList().orEmpty()
        } catch (e: Exception) {
            Log.e("DICT", "Failed to list dictionaries", e)
            emptyList()
        }

    fun getMeaning(word: String) = entries[word]?.first.orEmpty()
    fun getPinyin(word: String) = entries[word]?.second.orEmpty()

    /**
     * Per-character gloss for a multi-char word, e.g. "你·you  好·good".
     * Returns "" for single-char words (nothing to break down).
     */
    fun breakdown(word: String): String {
        if (word.length <= 1) return ""
        return word.map { ch ->
            val m = getMeaning(ch.toString())
            if (m.isNotEmpty()) "$ch·$m" else ch.toString()
        }.joinToString("  ")
    }

    private fun shortMeaning(def: String): String =
        def.split(Regex("""\s*[/;|,·•]\s*|\s+-\s+"""))
            .map {
                it.trim().lowercase(java.util.Locale.ROOT).removePrefix("to ")
                    .replace(Regex("\\(.*?\\)"), "").replace(Regex("\\s+"), " ")
            }
            .filter { it.isNotEmpty() }
            .let { LinkedHashSet(it).take(3).joinToString("/") }

    fun segment(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.contains(' ')) return trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }

        val result = mutableListOf<String>()
        var i = 0
        while (i < trimmed.length) {
            val end = minOf(i + MAX_WORD_LEN, trimmed.length)
            val match = (end downTo i + 1)
                .map { trimmed.substring(i, it) }
                .firstOrNull { getMeaning(it).isNotEmpty() }

            if (match != null) {
                result += match
                i += match.length
            } else {
                result += trimmed[i].toString()
                i++
            }
        }
        return result
    }

    /** Search loaded entries by word or meaning substring. Used by the Manage Words screen. */
    fun search(query: String, limit: Int = 50): List<Triple<String, String, String>> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase(java.util.Locale.ROOT)
        return entries.entries
            .filter { (word, pair) -> word.contains(q) || pair.first.contains(q) }
            .take(limit)
            .map { (word, pair) -> Triple(word, pair.first, pair.second) }
    }

    /** Adds or overwrites a custom dictionary entry (e.g. user-added word). Persists to disk. */
    fun addCustomEntry(word: String, meaning: String, pinyin: String = "") {
        if (word.isBlank() || meaning.isBlank()) return
        entries[word] = meaning to pinyin
        customWords.add(word)
        persistCustomEntries()
    }

    fun allWords(): List<String> = entries.keys.toList()
}
