package com.example.vaddemo

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*

/**
 * Silero VAD wrapper using sherpa-onnx native library
 *
 * This uses the native C++ implementation which is much faster
 * than pure Java ONNX Runtime.
 */
class SileroVAD(private val context: Context) {
    companion object {
        private const val TAG = "SileroVAD"
        const val SAMPLE_RATE = 16000
        const val WINDOW_SIZE_SAMPLES = 512
    }

    private var vad: Vad? = null

    // VAD parameters
    var threshold: Float = 0.5f
        set(value) {
            field = value
            // Note: sherpa-onnx doesn't support changing threshold after init
        }

    var minSilenceDurationMs: Int = 300

    // State tracking for speech detection
    private var triggeredState = false
    private var tempEndSampleCount = 0
    private var currentSampleCount = 0

    init {
        Log.d(TAG, "Initializing SileroVAD with sherpa-onnx native lib...")

        val config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "silero_vad.onnx",
                threshold = threshold,
                minSilenceDuration = minSilenceDurationMs / 1000f,
                minSpeechDuration = 0.25f,
                windowSize = WINDOW_SIZE_SAMPLES,
                maxSpeechDuration = 10.0f
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = 1,
            provider = "cpu",
            debug = false
        )

        vad = Vad(
            assetManager = context.assets,
            config = config
        )

        Log.d(TAG, "SileroVAD initialized successfully")
    }

    /**
     * Process an audio chunk and return speech probability
     *
     * @param audioChunk Float array of 512 samples (32ms at 16kHz)
     * @return Speech probability [0, 1]
     */
    fun process(audioChunk: FloatArray): Float {
        require(audioChunk.size == WINDOW_SIZE_SAMPLES) {
            "Audio chunk must be $WINDOW_SIZE_SAMPLES samples, got ${audioChunk.size}"
        }

        val v = vad ?: return 0f
        return v.compute(audioChunk)
    }

    /**
     * Process audio chunk with speech segment detection
     */
    fun processWithState(audioChunk: FloatArray): VADResult {
        val prob = process(audioChunk)
        val isSpeech = prob >= threshold

        currentSampleCount += WINDOW_SIZE_SAMPLES

        val speechStart = !triggeredState && isSpeech
        var speechEnd = false

        if (isSpeech) {
            triggeredState = true
            tempEndSampleCount = 0
        } else if (triggeredState) {
            if (tempEndSampleCount == 0) {
                tempEndSampleCount = currentSampleCount
            }
            val silenceDurationMs = (currentSampleCount - tempEndSampleCount) * 1000 / SAMPLE_RATE
            if (silenceDurationMs >= minSilenceDurationMs) {
                triggeredState = false
                speechEnd = true
                tempEndSampleCount = 0
            }
        }

        return VADResult(
            probability = prob,
            isSpeech = isSpeech,
            speechStart = speechStart,
            speechEnd = speechEnd
        )
    }

    /**
     * Reset VAD state
     */
    fun reset() {
        vad?.reset()
        triggeredState = false
        tempEndSampleCount = 0
        currentSampleCount = 0
    }

    fun close() {
        vad?.release()
        vad = null
        Log.d(TAG, "SileroVAD released")
    }

    data class VADResult(
        val probability: Float,
        val isSpeech: Boolean,
        val speechStart: Boolean,
        val speechEnd: Boolean
    )
}
