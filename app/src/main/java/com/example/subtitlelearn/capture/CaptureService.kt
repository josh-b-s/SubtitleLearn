package com.example.subtitlelearn.capture

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.subtitlelearn.R
import com.example.subtitlelearn.audio.AudioClipStore
import com.example.subtitlelearn.audio.AudioRecordFactory
import com.example.subtitlelearn.audio.AudioTranscriptionLoop
import com.example.subtitlelearn.audio.SttEngine
import com.example.subtitlelearn.audio.TranscriptionSession
import com.example.subtitlelearn.core.AppRepository
import com.example.subtitlelearn.core.WordTracker
import com.example.subtitlelearn.dictionary.Dictionary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

/** Captures system playback audio, transcribes it, and tracks word occurrences for the session. */
class CaptureService : Service() {

    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var sttEngine: SttEngine? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val rollingBuffer = ArrayDeque<ShortArray>()
    private val MAX_CHUNKS = 30  // 30 × 100ms = 3 seconds

    companion object {
        private const val TAG = "CaptureService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "capture_channel"
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (sttEngine != null) return START_STICKY

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "Missing projection permission")
            stopSelf()
            return START_NOT_STICKY
        }

        if (TranscriptionSession.acquire(TranscriptionSession.Owner.CAPTURE)) {
            Toast.makeText(
                this,
                "Mic translator is also running — performance may be reduced",
                Toast.LENGTH_LONG
            ).show()
        }

        startForeground(NOTIFICATION_ID, createNotification())
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        val record = buildAudioRecord() ?: run {
            Log.e(TAG, "AudioRecord failed to initialise")
            TranscriptionSession.release(TranscriptionSession.Owner.CAPTURE)
            stopSelf()
            return START_NOT_STICKY
        }

        audioRecord = record
        sttEngine = SttEngine(assets)
        record.startRecording()

        scope.launch { runCaptureLoop(record, sttEngine!!) }
        Log.i(TAG, "Started")
        return START_STICKY
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun buildAudioRecord(): AudioRecord? {
        val projection = mediaProjection ?: return null
        return AudioRecordFactory.create(AudioRecordFactory.Source.PlaybackCapture(projection))
    }

    private suspend fun runCaptureLoop(record: AudioRecord, engine: SttEngine) {
        try {
            AudioTranscriptionLoop(record, engine).run(
                onChunk = { chunk ->
                    rollingBuffer.addLast(chunk)
                    if (rollingBuffer.size > MAX_CHUNKS) rollingBuffer.removeFirst()
                },
                onResult = { text ->
                    AppRepository.emitTranscription(text)

                    val snapshot = flattenBuffer()
                    val words = Dictionary.segment(text).filter { it.isNotBlank() }
                    words.forEach { word ->
                        // storeIfAbsent means each word gets the clip from its first appearance
                        // — distinct audio per word since new words only appear in new contexts
                        if (!WordTracker.hasSeen(word)) {
                            AudioClipStore.storeIfAbsent(word, snapshot)
                        }
                        WordTracker.record(word)
                    }
                },
                onReadError = { code ->
                    Log.e(TAG, "AudioRecord.read error: $code")
                },
                onProcessError = { e ->
                    Log.w(TAG, "Dropped a bad chunk during STT processing: ${e.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Capture loop failed: ${e.message}")
            withContext(Dispatchers.Main) { stopSelf() }
        } finally {
            engine.release()
        }
    }

    private fun flattenBuffer(): ShortArray {
        val total = rollingBuffer.sumOf { it.size }
        val out = ShortArray(total)
        var pos = 0
        for (chunk in rollingBuffer) {
            chunk.copyInto(out, pos)
            pos += chunk.size
        }
        return out
    }

    private fun createNotification(): Notification {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Capture Service", NotificationManager.IMPORTANCE_LOW)
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Live Transcription")
            .setContentText("Recording audio…")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    override fun onDestroy() {
        TranscriptionSession.release(TranscriptionSession.Owner.CAPTURE)
        scope.cancel()
        rollingBuffer.clear()
        AudioClipStore.clearMemory()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        mediaProjection?.stop()
        mediaProjection = null
        sttEngine = null
        Log.i(TAG, "Destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
