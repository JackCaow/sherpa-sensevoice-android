package com.example.vaddemo

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*

/**
 * SenseVoice ASR wrapper using sherpa-onnx native library
 *
 * This uses the native C++ implementation which is 10-50x faster
 * than pure Java ONNX Runtime.
 */
class SenseVoice(private val context: Context) {
    companion object {
        private const val TAG = "SenseVoice"
        const val SAMPLE_RATE = 16000
    }

    private var recognizer: OfflineRecognizer? = null

    fun initialize(): Boolean {
        return try {
            Log.d(TAG, "Initializing SenseVoice with sherpa-onnx native lib...")

            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = 16000,
                    featureDim = 80
                ),
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = "sense_voice.onnx",
                        language = "",  // auto-detect
                        useInverseTextNormalization = true
                    ),
                    tokens = "tokens.txt",
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                ),
                decodingMethod = "greedy_search"
            )

            recognizer = OfflineRecognizer(
                assetManager = context.assets,
                config = config
            )

            Log.d(TAG, "SenseVoice initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SenseVoice: ${e.message}", e)
            false
        }
    }

    /**
     * Transcribe audio samples to text
     * @param audioSamples PCM audio samples (16kHz, mono, float [-1, 1])
     * @param language Language code ("zh", "en", "ja", "ko", "yue") or empty for auto
     * @return TranscribeResult with text and inference time
     */
    fun transcribe(audioSamples: FloatArray, language: Int = 0): TranscribeResult {
        val rec = recognizer ?: return TranscribeResult("", 0L)

        val startTime = System.currentTimeMillis()
        Log.d(TAG, ">>> Starting transcribe, samples: ${audioSamples.size}, duration: ${audioSamples.size / 16000f}s")

        try {
            val stream = rec.createStream()
            stream.acceptWaveform(audioSamples, sampleRate = 16000)
            rec.decode(stream)

            val result = rec.getResult(stream)
            stream.release()

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, ">>> Transcribe done in ${elapsed}ms")
            Log.d(TAG, ">>> Result: ${result.text}")
            Log.d(TAG, ">>> Language: ${result.lang}, Emotion: ${result.emotion}, Event: ${result.event}")

            return TranscribeResult(result.text, elapsed)
        } catch (e: Exception) {
            Log.e(TAG, "Transcribe error: ${e.message}", e)
            return TranscribeResult("", 0L)
        }
    }

    fun close() {
        recognizer?.release()
        recognizer = null
        Log.d(TAG, "SenseVoice released")
    }

    data class TranscribeResult(
        val text: String,
        val inferenceTimeMs: Long
    )
}
