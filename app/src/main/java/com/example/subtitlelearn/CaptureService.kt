package com.example.subtitlelearn

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class CaptureService : Service() {

    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e("CaptureService", "Projection permission missing")
            stopSelf()
            return START_NOT_STICKY
        }

        // REQUIRED: start foreground FIRST
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
            sampleRate,
            channelConfig,
            audioFormat
        )

        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()

        audioRecord = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        Log.i(
            "CaptureService",
            "AudioRecord state=${audioRecord?.state}"
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("CaptureService", "AudioRecord failed to init")
            return
        }

        audioRecord?.startRecording()
        isRecording = true

        Log.i(
            "CaptureService",
            "Recording started state=${audioRecord?.recordingState}"
        )

        // 🔥 THIS IS WHERE YOU PUT THE READ LOOP
        Thread {
            val buffer = ByteArray(bufferSize)

            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                // 👇 THIS IS THE LINE YOU ASKED ABOUT
                Log.i("CaptureService", "Audio read bytes=$read")
                var sum = 0.0
                for (i in buffer.indices) {
                    sum += buffer[i] * buffer[i]
                }
                val rms = sqrt(sum / buffer.size)
                Log.i("CaptureService", "RMS volume=$rms")

            }
        }.start()
    }

    private fun createNotification(): Notification {
        val channelId = "capture_channel"

        val channel = NotificationChannel(
            channelId,
            "Capture Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Live Transcription")
            .setContentText("Recording audio...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    override fun onDestroy() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        mediaProjection?.stop()
        mediaProjection = null

        Log.i("CaptureService", "Service destroyed, recording stopped")

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
