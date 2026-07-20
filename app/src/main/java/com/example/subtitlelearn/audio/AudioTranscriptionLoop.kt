package com.example.subtitlelearn.audio

import android.media.AudioRecord
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * The "read 100ms of PCM from an AudioRecord, feed it to an SttEngine, report results" loop.
 *
 * Previously this exact loop was written twice — once in CaptureService (system audio) and
 * once in MicTranscriber (mic audio) — differing only in what each did with the results.
 * Both now delegate here; only the AudioRecord source and the callbacks differ.
 */
class AudioTranscriptionLoop(
    private val record: AudioRecord,
    private val engine: SttEngine
) {
    /**
     * Runs until the coroutine is cancelled or a read error occurs.
     *
     * @param onChunk raw PCM chunk read this iteration, before STT processing. Lets callers
     *   do their own thing with the audio (e.g. CaptureService's rolling clip buffer)
     *   without a second read loop.
     * @param onResult called whenever the decoded partial/final text changes.
     * @param onEndpoint called when the engine detects an utterance boundary, with the last
     *   recognized text for that utterance. The engine is reset right before this fires.
     * @param onReadError called if AudioRecord.read returns a negative error code. Fatal — stops the loop.
     * @param onProcessError called if a single chunk fails STT processing (e.g. a bad frame).
     *   Non-fatal — the engine is reset and the loop keeps going on the next chunk, so one
     *   bad chunk doesn't take down the whole session.
     */
    suspend fun run(
        onChunk: (ShortArray) -> Unit = {},
        onResult: (String) -> Unit,
        onEndpoint: (String) -> Unit = {},
        onReadError: (Int) -> Unit = {},
        onProcessError: (Exception) -> Unit = {}
    ) {
        val chunkSize = (0.1 * SttEngine.SAMPLE_RATE).toInt()
        val buffer = ShortArray(chunkSize)
        var lastText = ""

        while (currentCoroutineContext().isActive) {
            val read = record.read(buffer, 0, buffer.size)
            if (read < 0) {
                onReadError(read)
                break
            }
            if (read == 0) continue

            val chunk = buffer.copyOf(read)
            onChunk(chunk)

            try {
                val samples = FloatArray(read) { chunk[it] / 32768f }
                val text = engine.process(samples)

                if (text.isNotBlank() && text != lastText) {
                    lastText = text
                    onResult(text)
                }

                if (engine.isEndpoint()) {
                    val finalText = lastText
                    engine.reset()
                    lastText = ""
                    onEndpoint(finalText)
                }
            } catch (e: Exception) {
                onProcessError(e)
                // Recover rather than propagate — reset engine state and pick back up next chunk.
                try { engine.reset() } catch (_: Exception) { /* best effort */ }
                lastText = ""
            }
        }
    }
}
