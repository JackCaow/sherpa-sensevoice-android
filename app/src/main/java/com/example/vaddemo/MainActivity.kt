package com.example.vaddemo

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.vaddemo.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var sileroVAD: SileroVAD? = null
    private var senseVoice: SenseVoice? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    // Audio buffer for ASR
    private val audioBuffer = mutableListOf<FloatArray>()
    private var isCollectingSpeech = false

    companion object {
        private const val TAG = "VADDemo"
        private const val PERMISSION_REQUEST_CODE = 100
        private const val SAMPLE_RATE = 16000
        private const val WINDOW_SIZE = 512  // 32ms at 16kHz

        // Maximum speech duration to buffer (10 seconds)
        private const val MAX_SPEECH_FRAMES = (10 * 1000 / 32)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkPermissionAndInit()
    }

    private fun setupUI() {
        binding.btnStartStop.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        binding.sliderThreshold.addOnChangeListener { _, value, _ ->
            sileroVAD?.threshold = value
            binding.tvThreshold.text = "Threshold: %.2f".format(value)
        }

        // Test WAV button
        binding.btnTest.setOnClickListener {
            testWithWavFile()
        }

        // Language selection
        binding.rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val lang = when (checkedId) {
                R.id.rbAuto -> "auto"
                R.id.rbChinese -> "zh"
                R.id.rbEnglish -> "en"
                else -> "auto"
            }
            Log.d(TAG, "Language selected: $lang")
        }

        // Model selection (INT8 / FP32)
        binding.toggleModel.check(R.id.btnInt8)
        binding.toggleModel.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val modelType = when (checkedId) {
                    R.id.btnFp32 -> SenseVoice.MODEL_FP32
                    else -> SenseVoice.MODEL_INT8
                }
                switchModel(modelType)
            }
        }
    }

    private fun switchModel(modelType: Int) {
        if (isRecording) {
            appendLog("Stop recording before switching model")
            return
        }

        val modelName = if (modelType == SenseVoice.MODEL_FP32) "FP32" else "INT8"
        binding.tvStatus.text = "Loading $modelName..."
        binding.btnStartStop.isEnabled = false
        binding.btnTest.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val success = senseVoice?.initialize(modelType) ?: false

                withContext(Dispatchers.Main) {
                    if (success) {
                        val info = if (modelType == SenseVoice.MODEL_FP32) {
                            "894MB / Accurate"
                        } else {
                            "228MB / Fast"
                        }
                        binding.tvModelInfo.text = info
                        binding.tvStatus.text = "Ready ($modelName)"
                        appendLog("Switched to $modelName model")
                    } else {
                        binding.tvStatus.text = "Model load failed"
                        appendLog("Failed to load $modelName model")
                    }
                    binding.btnStartStop.isEnabled = true
                    binding.btnTest.isEnabled = senseVoice != null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Switch model failed", e)
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "Error: ${e.message}"
                    binding.btnStartStop.isEnabled = true
                }
            }
        }
    }

    private fun checkPermissionAndInit() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        } else {
            initModels()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initModels()
            } else {
                binding.tvStatus.text = "Permission denied"
                binding.btnStartStop.isEnabled = false
            }
        }
    }

    private fun initModels() {
        binding.tvStatus.text = "Loading models..."
        binding.btnStartStop.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Init VAD
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "Loading VAD..."
                }
                sileroVAD = SileroVAD(this@MainActivity)
                Log.d(TAG, "VAD initialized")

                // Init SenseVoice (sherpa-onnx native)
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "Loading ASR (native)..."
                }
                try {
                    senseVoice = SenseVoice(this@MainActivity)
                    val success = senseVoice?.initialize() ?: false
                    if (success) {
                        Log.d(TAG, "SenseVoice initialized with sherpa-onnx native lib")
                    } else {
                        senseVoice = null
                        Log.w(TAG, "SenseVoice init failed")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SenseVoice not available: ${e.message}")
                    senseVoice = null
                    withContext(Dispatchers.Main) {
                        appendLog("ASR not available: ${e.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "Ready (INT8)"
                    binding.tvModelInfo.text = "228MB / Fast"
                    binding.btnStartStop.isEnabled = true
                    binding.btnTest.isEnabled = senseVoice != null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init models", e)
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "Init failed: ${e.message}"
                }
            }
        }
    }

    private fun startRecording() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            maxOf(minBufferSize, WINDOW_SIZE * 4)
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord init failed")
            binding.tvStatus.text = "Audio init failed"
            return
        }

        audioRecord?.startRecording()
        isRecording = true
        binding.btnStartStop.text = "Stop"
        binding.tvStatus.text = "Listening..."
        sileroVAD?.reset()
        audioBuffer.clear()
        isCollectingSpeech = false

        recordingJob = lifecycleScope.launch(Dispatchers.IO) {
            val buffer = FloatArray(WINDOW_SIZE)
            var speechFrameCount = 0
            var frameCount = 0

            while (isActive && isRecording) {
                val readCount = audioRecord?.read(buffer, 0, WINDOW_SIZE, AudioRecord.READ_BLOCKING)
                    ?: break

                if (readCount == WINDOW_SIZE) {
                    val result = sileroVAD?.processWithState(buffer) ?: continue

                    // Log VAD probability every 50 frames (~1.6s)
                    frameCount++
                    if (frameCount % 50 == 0) {
                        android.util.Log.d("VAD", "prob=${result.probability}, isSpeech=${result.isSpeech}, collecting=$isCollectingSpeech")
                    }

                    // Collect audio for ASR when speech is detected
                    if (result.isSpeech || isCollectingSpeech) {
                        if (result.speechStart) {
                            audioBuffer.clear()
                            isCollectingSpeech = true
                            speechFrameCount = 0
                        }

                        if (isCollectingSpeech && audioBuffer.size < MAX_SPEECH_FRAMES) {
                            audioBuffer.add(buffer.copyOf())
                            speechFrameCount++
                        }
                    }

                    // Debug VAD state
                    if (result.speechStart) {
                        Log.d(TAG, ">>> Speech START detected")
                    }
                    if (result.speechEnd) {
                        Log.d(TAG, ">>> Speech END detected, buffer size: ${audioBuffer.size}, frames: $speechFrameCount")
                    }

                    // When speech ends, run ASR
                    if (result.speechEnd && audioBuffer.isNotEmpty()) {
                        isCollectingSpeech = false
                        val speechDuration = speechFrameCount * 32

                        // Only transcribe if we have enough audio (>300ms)
                        Log.d(TAG, ">>> Checking ASR: duration=$speechDuration, senseVoice=${senseVoice != null}")
                        if (speechDuration > 300 && senseVoice != null) {
                            val audioData = flattenAudioBuffer()
                            withContext(Dispatchers.Main) {
                                binding.tvStatus.text = "Transcribing..."
                            }

                            try {
                                val language = getSelectedLanguage()
                                val asrResult = senseVoice?.transcribe(audioData, language)

                                withContext(Dispatchers.Main) {
                                    binding.tvStatus.text = "Listening..."
                                    if (!asrResult?.text.isNullOrBlank()) {
                                        binding.tvTranscription.text = asrResult?.text
                                        appendLog("[${speechDuration}ms] ${asrResult?.text}")
                                        Log.d(TAG, "ASR: ${asrResult?.text} (${asrResult?.inferenceTimeMs}ms)")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "ASR failed", e)
                                withContext(Dispatchers.Main) {
                                    binding.tvStatus.text = "Listening..."
                                    appendLog("ASR error: ${e.message}")
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                appendLog("Speech too short (${speechDuration}ms)")
                            }
                        }

                        audioBuffer.clear()
                        speechFrameCount = 0
                    }

                    withContext(Dispatchers.Main) {
                        // Update probability display
                        binding.tvProbability.text = "Prob: %.3f".format(result.probability)
                        binding.progressVAD.progress = (result.probability * 100).toInt()

                        // Update speech indicator
                        if (result.isSpeech) {
                            binding.viewSpeechIndicator.setBackgroundColor(
                                ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_light)
                            )
                            binding.tvSpeechState.text = "Speaking"
                        } else {
                            binding.viewSpeechIndicator.setBackgroundColor(
                                ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray)
                            )
                            binding.tvSpeechState.text = "Silence"
                        }

                        // Log speech events
                        if (result.speechStart) {
                            Log.d(TAG, "Speech started")
                        }
                    }
                }
            }
        }
    }

    private fun flattenAudioBuffer(): FloatArray {
        val totalSize = audioBuffer.sumOf { it.size }
        val result = FloatArray(totalSize)
        var offset = 0
        for (chunk in audioBuffer) {
            System.arraycopy(chunk, 0, result, offset, chunk.size)
            offset += chunk.size
        }
        return result
    }

    private fun getSelectedLanguage(): Int {
        // sherpa-onnx handles language detection internally
        // These values are placeholders, actual detection is automatic
        return when (binding.rgLanguage.checkedRadioButtonId) {
            R.id.rbChinese -> 3   // zh
            R.id.rbEnglish -> 4   // en
            else -> 0             // auto
        }
    }

    private fun stopRecording() {
        isRecording = false
        isCollectingSpeech = false
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        audioBuffer.clear()

        binding.btnStartStop.text = "Start"
        binding.tvStatus.text = "Stopped"
        binding.viewSpeechIndicator.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.darker_gray)
        )
    }

    private fun appendLog(message: String) {
        val currentText = binding.tvLog.text.toString()
        val lines = currentText.split("\n").takeLast(10)
        binding.tvLog.text = (lines + message).joinToString("\n")
    }

    private fun testWithWavFile() {
        binding.btnTest.isEnabled = false
        binding.tvStatus.text = "Testing WAV..."
        val testFile = "test.wav"
        appendLog("Loading $testFile...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Read WAV file from assets
                val audioData = readWavFromAssets(testFile)
                Log.d(TAG, "WAV loaded: ${audioData.size} samples, ${audioData.size / 16000f}s")

                withContext(Dispatchers.Main) {
                    appendLog("WAV: ${audioData.size} samples (${audioData.size / 16000f}s)")
                    appendLog("Note: Mobile inference may take 30-60s...")
                    binding.tvStatus.text = "Extracting features..."
                }

                // Run ASR
                val startTime = System.currentTimeMillis()
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "Running ONNX inference..."
                }
                val result = senseVoice?.transcribe(audioData, getSelectedLanguage())
                val totalTime = System.currentTimeMillis() - startTime

                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "Ready"
                    binding.btnTest.isEnabled = true

                    if (result != null) {
                        val modelName = senseVoice?.getModelName() ?: "?"
                        binding.tvTranscription.text = result.text
                        appendLog("[$modelName] ${result.text}")
                        appendLog("Time: ${result.inferenceTimeMs}ms")
                        Log.d(TAG, "Test result: ${result.text}")
                    } else {
                        appendLog("ASR returned null")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Test failed", e)
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "Ready"
                    binding.btnTest.isEnabled = true
                    appendLog("Error: ${e.message}")
                }
            }
        }
    }

    private fun readWavFromAssets(filename: String): FloatArray {
        val inputStream = assets.open(filename)
        val bytes = inputStream.readBytes()
        inputStream.close()

        // Skip WAV header (44 bytes for standard WAV)
        val headerSize = 44
        val audioBytes = bytes.copyOfRange(headerSize, bytes.size)

        // Convert Int16 PCM to Float32
        val samples = audioBytes.size / 2
        val floatData = FloatArray(samples)
        for (i in 0 until samples) {
            val low = audioBytes[i * 2].toInt() and 0xFF
            val high = audioBytes[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            floatData[i] = sample / 32768f
        }
        return floatData
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        sileroVAD?.close()
        senseVoice?.close()
    }
}
