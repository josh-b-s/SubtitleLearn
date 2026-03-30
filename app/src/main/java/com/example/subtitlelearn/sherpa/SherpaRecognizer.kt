import android.content.Context
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.getEndpointConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig

class SherpaRecognizer(context: Context) {

    private val recognizer: OnlineRecognizer
    private val sampleRate = 16000

    init {
        val config = OnlineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = sampleRate, featureDim = 80),
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

        recognizer = OnlineRecognizer(context.assets, config)
    }

    fun createStream() = recognizer.createStream()

    fun decode(stream: OnlineStream) {
        while (recognizer.isReady(stream)) {
            recognizer.decode(stream)
        }
    }

    fun getText(stream: OnlineStream): String {
        return recognizer.getResult(stream).text
    }

    fun isEndpoint(stream: OnlineStream): Boolean {
        return recognizer.isEndpoint(stream)
    }

    fun reset(stream: OnlineStream) {
        recognizer.reset(stream)
    }
}