# sherpa-sensevoice-android

基于 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 原生库的 Android 离线语音识别，集成 **SenseVoice** + **Silero VAD**。

## ✨ 特性

- 🚀 **极速推理**：110ms 识别 5 秒音频（原生 C++ 实现）
- 📴 **完全离线**：无需网络，隐私安全
- 🌍 **多语言**：中文、英文、日语、韩语、粤语
- 🎙️ **实时 VAD**：语音端点检测，自动分句
- 📱 **移动优化**：ARM64 原生库，低延迟

## 📊 性能对比

| 方案 | 5秒音频推理 | 提升 |
|------|------------|------|
| 纯 Java ONNX Runtime | 60-120 秒 | - |
| **sherpa-onnx 原生库** | **110 ms** | **~600x** |

## 🚀 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/JackCaow/sherpa-sensevoice-android.git
cd sherpa-sensevoice-android
```

### 2. 下载模型

```bash
# SenseVoice 模型
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2
tar -xjf sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2

# INT8 模型 (228MB, 快速)
cp sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/model.int8.onnx app/src/main/assets/sense_voice_int8.onnx

# FP32 模型 (894MB, 高精度)
cp sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/model.onnx app/src/main/assets/sense_voice_fp32.onnx

cp sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/tokens.txt app/src/main/assets/

# Silero VAD 模型 (629KB)
wget -O app/src/main/assets/silero_vad.onnx \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
```

### 3. 编译运行

用 Android Studio 打开项目，点击 Run。

## 📁 项目结构

```
app/src/main/
├── assets/
│   ├── sense_voice_int8.onnx # INT8 模型 (228MB)
│   ├── sense_voice_fp32.onnx # FP32 模型 (894MB)
│   ├── silero_vad.onnx       # Silero VAD 模型 (629KB)
│   └── tokens.txt            # 词表
├── jniLibs/arm64-v8a/        # sherpa-onnx 原生库
│   ├── libsherpa-onnx-jni.so
│   └── libonnxruntime.so
└── java/
    ├── com/example/vaddemo/
    │   ├── MainActivity.kt   # 主界面
    │   ├── SenseVoice.kt     # ASR 封装
    │   └── SileroVAD.kt      # VAD 封装
    └── com/k2fsa/sherpa/onnx/
        └── *.kt              # sherpa-onnx Kotlin API
```

## 💻 代码示例

### ASR 语音识别

```kotlin
val senseVoice = SenseVoice(context)

// 使用 INT8 模型 (快速)
senseVoice.initialize(SenseVoice.MODEL_INT8)

// 或 FP32 模型 (高精度)
senseVoice.initialize(SenseVoice.MODEL_FP32)

// 识别音频 (16kHz, mono, float)
val result = senseVoice.transcribe(audioSamples)
println(result.text)           // "你好世界"
println("${result.inferenceTimeMs}ms")  // "110ms"
```

### VAD 语音检测

```kotlin
val vad = SileroVAD(context)

// 处理 512 样本 (32ms @ 16kHz)
val result = vad.processWithState(audioChunk)
if (result.speechStart) println("开始说话")
if (result.speechEnd) println("说话结束，触发识别")
```

## 🔧 技术参数

| 组件 | 模型大小 | 延迟 |
|------|----------|------|
| Silero VAD | 629 KB | ~5ms |
| SenseVoice INT8 | 228 MB | ~110ms |
| SenseVoice FP32 | 894 MB | ~200ms |

## 🙏 致谢

- [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) - 原生推理引擎
- [FunAudioLLM/SenseVoice](https://github.com/FunAudioLLM/SenseVoice) - 语音识别模型
- [snakers4/silero-vad](https://github.com/snakers4/silero-vad) - VAD 模型

## 📄 License

MIT
