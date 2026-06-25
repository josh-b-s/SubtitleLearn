package com.example.subtitlelearn

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

class CaptureService : Service() {

    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var sttEngine: SttEngine? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val rollingBuffer = ArrayDeque<ShortArray>()
    private val MAX_CHUNKS = 30  // 30 × 100ms = 3 seconds
    private var utteranceCounter = 0

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

        startForeground(NOTIFICATION_ID, createNotification())
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        val record = buildAudioRecord() ?: run {
            Log.e(TAG, "AudioRecord failed to initialise")
            stopSelf()
            return START_NOT_STICKY
        }

        audioRecord = record
        sttEngine = SttEngine(assets)
        record.startRecording()

        scope.launch { runCaptureLoop(record) }
        Log.i(TAG, "Started")
        return START_STICKY
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun buildAudioRecord(): AudioRecord? {
        val projection = mediaProjection ?: return null
        val bufferSize = AudioRecord.getMinBufferSize(
            SttEngine.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()
        return AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SttEngine.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()
            .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
    }

    private suspend fun runCaptureLoop(record: AudioRecord) {
        val engine = sttEngine ?: return
        val chunkSize = (0.1 * SttEngine.SAMPLE_RATE).toInt()
        val buffer = ShortArray(chunkSize)
        var lastText = ""

        try {
            while (currentCoroutineContext().isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                val chunk = buffer.copyOf(read)
                rollingBuffer.addLast(chunk)
                if (rollingBuffer.size > MAX_CHUNKS) rollingBuffer.removeFirst()

                val samples = FloatArray(read) { buffer[it] / 32768f }
                val text = engine.process(samples)

                if (text.isNotBlank() && text != lastText) {
                    lastText = text
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
                }

                if (engine.isEndpoint()) {
                    engine.reset()
                    lastText = ""
                }
            }
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