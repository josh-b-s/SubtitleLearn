package com.example.subtitlelearn.audio

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import androidx.annotation.RequiresPermission

/**
 * Builds an [AudioRecord] for either the mic or system-playback capture.
 *
 * The two genuinely need different construction paths: playback capture requires a
 * [MediaProjection] fed into an [AudioPlaybackCaptureConfiguration] via the newer `Builder` API,
 * while the mic just takes an `AudioSource` constant through the classic constructor — that
 * distinction is real and stays explicit as [Source]. Everything else (format, buffer sizing)
 * was identical between `CaptureService` and `MicTranscriber` and is shared here instead.
 */
object AudioRecordFactory {

    sealed class Source {
        object Mic : Source()
        data class PlaybackCapture(val projection: MediaProjection) : Source()
    }

    /**
     * @param minBufferFloor Some devices report a `getMinBufferSize` too small to keep up with
     *   real-time reads; callers that have seen this in practice (like the mic path) can set a floor.
     * @return null if the record failed to reach `STATE_INITIALIZED`.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun create(
        source: Source,
        sampleRate: Int = SttEngine.SAMPLE_RATE,
        minBufferFloor: Int = 0
    ): AudioRecord? {
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(minBufferFloor)

        val record = when (source) {
            is Source.Mic -> AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )

            is Source.PlaybackCapture -> {
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
                val captureConfig = AudioPlaybackCaptureConfiguration.Builder(source.projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .build()
                AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize * 2)
                    .setAudioPlaybackCaptureConfig(captureConfig)
                    .build()
            }
        }

        return record.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
    }
}
