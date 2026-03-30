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
import com.example.subtitlelearn.overlay.OverlayBridge
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.getEndpointConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig

class CaptureService : Service() {

    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    // 🔥 Sherpa
    private lateinit var recognizer: OnlineRecognizer
    private lateinit var stream: OnlineStream

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

        initSherpa()
        startAudioCapture()

        return START_STICKY
    }

    // =========================
    // 🔥 INIT SHERPA
    // =========================
    private fun initSherpa() {

        val config = OnlineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                paraformer = OnlineParaformerModelConfig(
                    encoder = "model/encoder.int8.onnx",
                    decoder = "model/decoder.int8.onnx"
                ),
                tokens = "model/tokens.txt",
                numThreads = 2,
                provider = "cpu"
            ),
            endpointConfig = getEndpointConfig(),
            enableEndpoint = true
        )

        recognizer = OnlineRecognizer(assets, config)
        stream = recognizer.createStream()

        Log.i("SHERPA", "Model initialized")
    }

    // =========================
    // 🎤 AUDIO CAPTURE
    // =========================
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startAudioCapture() {

        val sampleRate = 16000

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val projection = mediaProjection ?: return

        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
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

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("CaptureService", "AudioRecord failed to init")
            return
        }

        audioRecord?.startRecording()
        isRecording = true

        Log.i("CaptureService", "Recording started")

        recordingThread = Thread {
            processAudio()
        }
        recordingThread?.start()
    }

    // =========================
    // 🧠 SHERPA PROCESSING LOOP
    // =========================
    private fun processAudio() {

        val sampleRate = 16000
        val buffer = ShortArray((0.1 * sampleRate).toInt()) // 100ms chunks

        while (isRecording) {
            try {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (read > 0) {

                    // Convert to float
                    val samples = FloatArray(read) {
                        buffer[it] / 32768f
                    }

                    stream.acceptWaveform(samples, sampleRate)

                    while (recognizer.isReady(stream)) {
                        recognizer.decode(stream)
                    }

                    val text = recognizer.getResult(stream).text

                    // 🔥 Only send if changed
                    if (text.isNotBlank() && text != lastText) {
                        lastText = text
                        OverlayBridge.update?.invoke(text)
                    }

                    if (recognizer.isEndpoint(stream)) {
                        recognizer.reset(stream)
                    }
                }

            } catch (e: Exception) {
                Log.e("CaptureService", "Audio loop crash: ${e.message}")
                break
            }
        }

        stream.release()
    }

    // =========================
    // 🔔 NOTIFICATION
    // =========================
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

    // =========================
    // 🧹 CLEANUP
    // =========================
    override fun onDestroy() {

        isRecording = false

        recordingThread?.interrupt()
        recordingThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        mediaProjection?.stop()
        mediaProjection = null

        Log.i("CaptureService", "Service destroyed")

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}