package com.example.subtitlelearn.translate

import android.content.Context
import android.media.AudioRecord
import android.util.Log
import com.example.subtitlelearn.audio.AudioRecordFactory
import com.example.subtitlelearn.audio.AudioTranscriptionLoop
import com.example.subtitlelearn.audio.SttEngine
import com.example.subtitlelearn.audio.TranscriptionSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lightweight mic-based transcriber for the Translate screen.
 * Uses AudioRecord(MIC) + SttEngine directly — no MediaProjection needed.
 * Calls onPartial with each new result; call stop() to end.
 */
class MicTranscriber(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    private var audioRecord: AudioRecord? = null

    companion object {
        // Some devices report a getMinBufferSize() too small to reliably keep up with mic reads.
        private const val MIN_BUFFER_FLOOR = 3200
    }

    /**
     * @param onConcurrentWarning called (session still starts) if CaptureService is already
     *   running — both share one loaded model, but still compete for CPU while decoding.
     */
    fun start(onPartial: (String) -> Unit, onError: () -> Unit, onConcurrentWarning: () -> Unit = {}): Boolean {
        if (job?.isActive == true) return true

        if (TranscriptionSession.acquire(TranscriptionSession.Owner.MIC)) {
            Log.w("MicTranscriber", "Starting while screen capture transcription is already active")
            onConcurrentWarning()
        }

        job = scope.launch {
            try {
                val engine = SttEngine(context.assets)

                val record = try {
                    AudioRecordFactory.create(
                        AudioRecordFactory.Source.Mic,
                        minBufferFloor = MIN_BUFFER_FLOOR
                    ) ?: run {
                        Log.e("MicTranscriber", "AudioRecord not initialized")
                        onError()
                        engine.release()
                        return@launch
                    }
                } catch (e: SecurityException) {
                    Log.e("MicTranscriber", "RECORD_AUDIO permission missing", e)
                    onError()
                    engine.release()
                    return@launch
                }

                audioRecord = record
                record.startRecording()

                var accumulatedText = ""

                try {
                    AudioTranscriptionLoop(record, engine).run(
                        onResult = { partial -> onPartial(accumulatedText + partial) },
                        onEndpoint = { finalText ->
                            if (finalText.isNotBlank()) {
                                accumulatedText += finalText
                                if (!accumulatedText.endsWith(" ")) accumulatedText += " "
                            }
                            onPartial(accumulatedText)
                        },
                        onReadError = { code ->
                            Log.e("MicTranscriber", "AudioRecord.read error: $code")
                            onError()
                        },
                        onProcessError = { e ->
                            Log.w("MicTranscriber", "Dropped a bad chunk during STT processing: ${e.message}")
                        }
                    )
                } finally {
                    record.stop()
                    record.release()
                    engine.release()
                    audioRecord = null
                }
            } finally {
                TranscriptionSession.release(TranscriptionSession.Owner.MIC)
            }
        }
        return true
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun isRunning(): Boolean = job?.isActive == true
}
