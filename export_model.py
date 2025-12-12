#!/usr/bin/env python3
"""
Export SenseVoice Small model to ONNX format for Android

Usage:
    pip install funasr onnx
    python export_model.py

Output:
    app/src/main/assets/sense_voice.onnx
    app/src/main/assets/tokens.txt
"""

import os
import shutil

def export_sensevoice():
    try:
        from funasr import AutoModel
    except ImportError:
        print("Installing funasr...")
        os.system("pip install -U funasr onnx onnxruntime")
        from funasr import AutoModel

    print("Loading SenseVoice Small model...")
    model = AutoModel(
        model="iic/SenseVoiceSmall",
        trust_remote_code=True,
    )

    print("Exporting to ONNX...")
    # Export with quantization for smaller size
    export_dir = model.export(
        quantize=True,
        opset_version=14,
    )

    print(f"Exported to: {export_dir}")

    # Copy to assets folder
    assets_dir = "app/src/main/assets"
    os.makedirs(assets_dir, exist_ok=True)

    # Find and copy the ONNX model
    for f in os.listdir(export_dir):
        if f.endswith(".onnx"):
            src = os.path.join(export_dir, f)
            dst = os.path.join(assets_dir, "sense_voice.onnx")
            shutil.copy(src, dst)
            print(f"Copied model to {dst}")
            size_mb = os.path.getsize(dst) / (1024 * 1024)
            print(f"Model size: {size_mb:.1f} MB")

        if f == "tokens.txt" or f == "vocab.txt":
            src = os.path.join(export_dir, f)
            dst = os.path.join(assets_dir, "tokens.txt")
            shutil.copy(src, dst)
            print(f"Copied vocab to {dst}")

    print("\nDone! You can now build the Android project.")


def download_prebuilt():
    """Alternative: Download pre-built ONNX model"""
    import urllib.request

    assets_dir = "app/src/main/assets"
    os.makedirs(assets_dir, exist_ok=True)

    # URLs for pre-converted models
    urls = [
        # Try sherpa-onnx version (well-tested for mobile)
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2",
    ]

    print("Downloading pre-built model...")
    for url in urls:
        try:
            filename = url.split("/")[-1]
            print(f"Trying {filename}...")
            urllib.request.urlretrieve(url, filename)
            print(f"Downloaded {filename}")

            if filename.endswith(".tar.bz2"):
                import tarfile
                with tarfile.open(filename, "r:bz2") as tar:
                    tar.extractall(".")
                os.remove(filename)
                print("Extracted model files")
            break
        except Exception as e:
            print(f"Failed: {e}")
            continue


if __name__ == "__main__":
    import sys

    if len(sys.argv) > 1 and sys.argv[1] == "--download":
        download_prebuilt()
    else:
        export_sensevoice()
