package com.example.vaddemo

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*

/**
 * SenseVoice ASR wrapper using sherpa-onnx native library
 * Supports switching between INT8 and FP32 models
 */
class SenseVoice(private val context: Context) {
    companion object {
        private const val TAG = "SenseVoice"
        const val SAMPLE_RATE = 16000

        const val MODEL_INT8 = 0
        const val MODEL_FP32 = 1
    }

    private var recognizer: OfflineRecognizer? = null
    private var currentModelType: Int = MODEL_INT8

    fun initialize(modelType: Int = MODEL_INT8): Boolean {
        return try {
            // Release existing recognizer if switching models
            recognizer?.release()
            recognizer = null

            currentModelType = modelType
            val modelFile = when (modelType) {
                MODEL_FP32 -> "sense_voice_fp32.onnx"
                else -> "sense_voice_int8.onnx"
            }

            Log.d(TAG, "Initializing SenseVoice with $modelFile...")

            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = 16000,
                    featureDim = 80
                ),
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = modelFile,
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

            Log.d(TAG, "SenseVoice initialized with ${getModelName()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SenseVoice: ${e.message}", e)
            false
        }
    }

    fun getModelType(): Int = currentModelType

    fun getModelName(): String = when (currentModelType) {
        MODEL_FP32 -> "FP32 (894MB)"
        else -> "INT8 (228MB)"
    }

    /**
     * Transcribe audio samples to text
     */
    fun transcribe(audioSamples: FloatArray, language: Int = 0): TranscribeResult {
        val rec = recognizer ?: return TranscribeResult("", 0L)

        val startTime = System.currentTimeMillis()
        Log.d(TAG, ">>> Transcribe [${getModelName()}], samples: ${audioSamples.size}")

        try {
            val stream = rec.createStream()
            stream.acceptWaveform(audioSamples, sampleRate = 16000)
            rec.decode(stream)

            val result = rec.getResult(stream)
            stream.release()

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, ">>> Done in ${elapsed}ms: ${result.text}")

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
