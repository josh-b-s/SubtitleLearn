package com.example.subtitlelearn

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
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

    fun start(onPartial: (String) -> Unit, onError: () -> Unit) {
        if (job?.isActive == true) return

        job = scope.launch {
            val engine = SttEngine(context.assets)
            val bufferSize = AudioRecord.getMinBufferSize(
                SttEngine.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3200)

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SttEngine.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize * 2
                ).also {
                    if (it.state != AudioRecord.STATE_INITIALIZED) {
                        Log.e("MicTranscriber", "AudioRecord not initialized")
                        onError()
                        return@launch
                    }
                }
            } catch (e: SecurityException) {
                Log.e("MicTranscriber", "RECORD_AUDIO permission missing", e)
                onError()
                return@launch
            }

            audioRecord = record
            record.startRecording()

            val buffer = ShortArray((0.1 * SttEngine.SAMPLE_RATE).toInt())
            var lastText = ""
            var accumulatedText = ""

            try {
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) continue

                    val samples = FloatArray(read) { buffer[it] / 32768f }
                    val partial = engine.process(samples)

                    if (partial.isNotBlank() && partial != lastText) {
                        lastText = partial
                        onPartial(accumulatedText + partial)
                    }

                    if (engine.isEndpoint()) {
                        if (lastText.isNotBlank()) {
                            accumulatedText += lastText
                            if (!accumulatedText.endsWith(" ")) accumulatedText += " "
                        }
                        engine.reset()
                        lastText = ""
                        onPartial(accumulatedText)
                    }
                }
            } finally {
                record.stop()
                record.release()
                engine.release()
                audioRecord = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun isRunning(): Boolean = job?.isActive == true
}