package com.example.subtitlelearn.audio

import android.content.Context
import android.util.Log
import android.util.LruCache
import java.io.File

/** Stores one short audio clip per word (first occurrence wins), keyed directly by word. */
object AudioClipStore {
    private const val TAG = "AudioClipStore"
    private const val CLIP_DIR = "audio_clips"

    // 50 words × ~96KB each ≈ 4.8MB max
    private val memoryClips = LruCache<String, ShortArray>(50)

    // ── During capture ────────────────────────────────────────────────────────

    /** Store a clip for a word. Only stores if word not already present (first occurrence wins). */
    fun storeIfAbsent(word: String, samples: ShortArray) {
        if (samples.isEmpty() || memoryClips[word] != null) return
        memoryClips.put(word, samples)
        Log.d(TAG, "stored clip for '$word' samples=${samples.size}")
    }

    fun clearMemory() = memoryClips.evictAll()

    // ── On Stop ───────────────────────────────────────────────────────────────

    /** Persist in-memory clips for the given words to disk. Clears previous session clips first. */
    fun persistWords(context: Context, words: List<String>) {
        File(context.filesDir, CLIP_DIR).listFiles()?.forEach { it.delete() }

        for (word in words) {
            val samples = memoryClips[word]
            if (samples == null) {
                Log.w(TAG, "No in-memory clip for '$word'")
                continue
            }
            try {
                fileFor(context, word).outputStream().use { it.write(PcmCodec.shortsToBytes(samples)) }
                Log.d(TAG, "persisted '$word'")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist '$word'", e)
            }
        }
    }

    // ── Quiz ──────────────────────────────────────────────────────────────────

    fun hasClip(context: Context, word: String) = fileFor(context, word).exists()

    fun loadClip(context: Context, word: String): ShortArray? {
        return try {
            val file = fileFor(context, word)
            if (!file.exists()) return null
            PcmCodec.bytesToShorts(file.readBytes())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load clip for '$word'", e)
            null
        }
    }

    private fun fileFor(context: Context, word: String): File {
        val dir = File(context.filesDir, CLIP_DIR).also { it.mkdirs() }
        return File(dir, "${word.hashCode()}.pcm")
    }
}
