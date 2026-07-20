package com.example.subtitlelearn.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Plays a previously-stored word clip. Blocking — call from a background dispatcher. */
object AudioPlayer {

    fun playClip(context: Context, word: String) {
        val samples = AudioClipStore.loadClip(context, word) ?: return

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SttEngine.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(samples, 0, samples.size)
            val latch = CountDownLatch(1)
            track.notificationMarkerPosition = samples.size - 1
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) = latch.countDown()
                override fun onPeriodicNotification(t: AudioTrack) = Unit
            })
            track.play()
            val clipDurationMs = samples.size * 1000L / SttEngine.SAMPLE_RATE
            latch.await(clipDurationMs + 2000L, TimeUnit.MILLISECONDS)
        } finally {
            track.release()
        }
    }
}
