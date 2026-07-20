package com.example.subtitlelearn.audio

/**
 * Tracks which transcription sessions (screen capture / mic) are currently active.
 *
 * Both sessions share one loaded model via [SharedRecognizer], so running them together no
 * longer means two model instances in memory — but they still compete for the same CPU threads
 * while decoding, so it's worth telling the user rather than staying silent. [acquire] never
 * blocks the caller; it just reports whether another owner was already running, so the caller
 * can show a heads-up and proceed anyway.
 */
object TranscriptionSession {
    enum class Owner { CAPTURE, MIC }

    private val active = mutableSetOf<Owner>()

    /** Marks [owner] active. Returns true if a *different* owner was already running. */
    @Synchronized
    fun acquire(owner: Owner): Boolean {
        val concurrentWithAnother = active.any { it != owner }
        active += owner
        return concurrentWithAnother
    }

    /** Safe to call even if [owner] doesn't currently hold a session. */
    @Synchronized
    fun release(owner: Owner) {
        active -= owner
    }

    @Synchronized
    fun activeOwners(): Set<Owner> = active.toSet()
}
