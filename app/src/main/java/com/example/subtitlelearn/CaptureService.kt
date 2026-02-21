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
import org.vosk.Model
import org.vosk.Recognizer

class CaptureService : Service() {

    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var voskModel: Model? = null
    private var recognizer: Recognizer? = null
    private var lastText = ""

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (isRecording) {
            Log.i("CaptureService", "Already recording")
            return START_STICKY
        }

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val resultCode =
            intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e("CaptureService", "Projection permission missing")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(1, createNotification())

        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        Log.i("CaptureService", "MediaProjection started")

        startAudioCapture()

        return START_STICKY
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startAudioCapture() {

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate, channelConfig, audioFormat
        )

        val projection = mediaProjection ?: return
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME).build()

        audioRecord = AudioRecord.Builder().setAudioFormat(
            AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
        ).setBufferSizeInBytes(bufferSize * 2).setAudioPlaybackCaptureConfig(config).build()

        Log.i(
            "CaptureService", "AudioRecord state=${audioRecord?.state}"
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("CaptureService", "AudioRecord failed to init")
            return
        }

        try {
            voskModel = ModelLoader.loadModel(this)
            recognizer = Recognizer(voskModel, 16000.0f)
            Log.i("VOSK", "Model loaded")
        } catch (e: Exception) {
            Log.e("VOSK", "Model failed: ${e.message}")
        }

        audioRecord?.startRecording()
        isRecording = true

        Log.i(
            "CaptureService", "Recording started state=${audioRecord?.recordingState}"
        )

        recordingThread = Thread {
            val shortBuffer = ShortArray(bufferSize * 2)

            while (isRecording) {
                try {
                    val read = audioRecord!!.read(shortBuffer, 0, shortBuffer.size)

                    if (read > 0) {
                        if (System.currentTimeMillis() % 2000 < 50) {
                            Log.i("AudioDebug", "audio alive size=$read sample=${shortBuffer[0]}")
                        }

                        val byteBuffer = ByteArray(read * 2)

                        var i = 0
                        for (index in 0 until read) {
                            val s = shortBuffer[index]
                            byteBuffer[i++] = (s.toInt() and 0xFF).toByte()
                            byteBuffer[i++] = ((s.toInt() shr 8) and 0xFF).toByte()
                        }

                        sendToSpeech(byteBuffer)
                    }
                } catch (e: Exception) {
                    Log.e("CaptureService", "Audio read crash: ${e.message}")
                    break
                }
            }
        }
        recordingThread?.start()
    }

    private fun createNotification(): Notification {
        val channelId = "capture_channel"

        val channel = NotificationChannel(
            channelId, "Capture Service", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId).setContentTitle("Live Transcription")
            .setContentText("Recording audio...").setSmallIcon(R.mipmap.ic_launcher).build()
    }

    private fun sendToSpeech(byteBuffer: ByteArray) {
        val rec = recognizer
        if (rec == null) {
            //Log.e("PIPELINE", "Recognizer is NULL")
            return
        }

        val isFinal = rec.acceptWaveForm(byteBuffer, byteBuffer.size)
        val json = if (isFinal) rec.result else rec.partialResult

        //Log.i("PIPELINE_RAW", json)

        val text = extractText(json)

        //Log.i("PIPELINE_TEXT", "extracted='$text'")

        if (text.isNotBlank() && text != lastText) {
            lastText = text
            Log.i("PIPELINE_SEND", "sending='$text'")
            OverlayBridge.update?.invoke(text)
        }
    }

    private fun extractText(json: String): String {
        return try {
            val obj = org.json.JSONObject(json)
            when {
                obj.has("text") -> obj.getString("text")
                obj.has("partial") -> obj.getString("partial")
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    override fun onDestroy() {
        isRecording = false

        recordingThread?.interrupt()
        recordingThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        mediaProjection?.stop()
        mediaProjection = null

        recognizer?.close()
        recognizer = null

        voskModel?.close()
        voskModel = null

        Log.i("CaptureService", "Service destroyed, recording stopped")

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
