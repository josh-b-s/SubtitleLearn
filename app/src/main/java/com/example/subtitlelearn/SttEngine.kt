package com.example.subtitlelearn

import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.getEndpointConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig

/**
 * Owns the Sherpa-ONNX recognizer and stream lifecycle.
 * Call process() with raw PCM samples, then check isEndpoint() and reset() as needed.
 */
class SttEngine(assets: AssetManager) {

    private val recognizer: OnlineRecognizer
    private val stream: OnlineStream

    init {
        recognizer = OnlineRecognizer(
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
        stream = recognizer.createStream()
        Log.i(TAG, "Initialized")
    }

    /** Feed PCM samples and return the current partial/final result text. */
    fun process(samples: FloatArray): String {
        stream.acceptWaveform(samples, SAMPLE_RATE)
        while (recognizer.isReady(stream)) recognizer.decode(stream)
        return recognizer.getResult(stream).text
    }

    fun isEndpoint(): Boolean = recognizer.isEndpoint(stream)

    fun reset() = recognizer.reset(stream)

    fun release() {
        stream.release()
        Log.i(TAG, "Released")
    }

    companion object {
        const val SAMPLE_RATE = 16000
        private const val TAG = "SttEngine"
    }
}