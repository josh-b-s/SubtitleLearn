package com.example.subtitlelearn.audio

import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.getEndpointConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig

/**
 * Loads the STT model exactly once and keeps it resident for the life of the process.
 *
 * The model weights are the expensive part of running STT (RAM + load time). The per-caller
 * state — feature buffer, decoder state, endpoint tracking — lives in an `OnlineStream`, which
 * is cheap to create. So `CaptureService` and `MicTranscriber` can each hold their own
 * [SttEngine] (and therefore their own stream) at the same time without paying for a second
 * copy of the model. `SttEngine` wraps a stream from this shared recognizer.
 */
object SharedRecognizer {
    private const val TAG = "SharedRecognizer"
    const val SAMPLE_RATE = 16000

    @Volatile
    private var recognizer: OnlineRecognizer? = null

    /** Returns the shared recognizer, loading it on first call. Blocks the caller while loading. */
    @Synchronized
    fun getOrCreate(assets: AssetManager): OnlineRecognizer {
        recognizer?.let { return it }

        Log.i(TAG, "Loading STT model…")
        val loaded = OnlineRecognizer(
            assets,
            OnlineRecognizerConfig(
                featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
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
        )
        recognizer = loaded
        Log.i(TAG, "STT model loaded")
        return loaded
    }

    /**
     * Warms up the model ahead of time so the first capture/mic session doesn't pay the load
     * cost. Call from a background thread at app start (e.g. `MainActivity.onCreate`); a no-op
     * if already loaded.
     *
     * FUTURE (not implemented — just leaving the seam): if you add model switching later, this
     * is the place for a `reload(assets, modelPath)` that releases the current `OnlineRecognizer`
     * and swaps in a new one. Every live `OnlineStream` created from the old recognizer would need
     * its owning `SttEngine` recreated at that point — streams don't survive a recognizer swap.
     */
    fun preload(assets: AssetManager) {
        getOrCreate(assets)
    }

    fun isLoaded(): Boolean = recognizer != null
}
