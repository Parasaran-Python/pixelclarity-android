# Real-ESRGAN Image Upscaler (Android)

[![CI Pipeline](https://github.com/Parasaran-Python/pixelclarity-android/actions/workflows/ci.yml/badge.svg)](https://github.com/Parasaran-Python/pixelclarity-android/actions/workflows/ci.yml)

A high-performance Android application for on-device **4x AI Super-Resolution Image Enhancement** using **Real-ESRGAN** deep learning models with **ONNX Runtime**, supporting hardware acceleration across **NPU (Hexagon / MediaTek / Google Tensor)**, **GPU**, and **multi-threaded CPU**.


---

## ✨ Features

- ⚡ **On-Device Local Inference**: 100% offline, zero cloud dependencies, private and fast.
- 🧠 **Hardware Acceleration**:
  - **NPU / NNAPI**: Leverages dedicated mobile Neural Processing Units (Qualcomm Hexagon, MediaTek NeuroPilot, Google Tensor TPU).
  - **Auto**: Intelligently selects the fastest available hardware delegate.
  - **CPU**: Multi-threaded execution with ARM NEON / XNNPACK optimizations.
- 🧩 **Seam-Free Tiling Engine**:
  - Implements overlapping padded tile inference (`128x128` or `256x256` with `10px` border padding) to eliminate seam artifacts and prevent `OutOfMemoryError` on large images.
- 🎚️ **Interactive Before/After Slider**:
  - Real-time touch split slider allowing users to inspect original vs 4x enhanced razor-sharp details.
- 📐 **Configurable Output Scale**:
  - **4x (Ultra HD)**: Native 4x super-resolution for maximum detail and ultra-sharp outputs.
  - **2x (Balanced)**: High-quality downscaled enhancement to save device storage and memory on large photos.
- 📦 **Multiple Pre-Loaded Models**:
  - **Real-ESRGAN x4+**: 23 Residual-in-Residual Dense Blocks (RRDB) for photographic fidelity and intricate textures.
  - **Real-ESRGAN Anime 6B**: 6 Residual Blocks optimized for speed and illustrations/anime art.
- 🌐 **Flexible Image Input (Gallery & URL)**:
  - Select photos from local device storage or directly download images from any web URL (HTTP/HTTPS) with 1-click sample presets and clipboard auto-paste.
- 💾 **Save & Instant Sharing**:
  - Save full-resolution enhanced PNGs directly to device Gallery (`Pictures/RealESRGAN`) with MediaStore scoped storage, or share directly to other apps via the Android Share Sheet.
- 🚀 **16 KB Page Size Compatible**:
  - Fully compliant with Android 15+ 16 KB memory page size requirements, with 16 KB ELF-aligned native binaries and uncompressed zip packaging for maximum speed and zero compatibility warnings.

---

## 🏗️ Architecture

```
RealESRGANUpscaler/
├── app/
│   ├── src/main/
│   │   ├── assets/models/
│   │   │   ├── realesrgan_x4plus.onnx    # 23 RRDB block model (68.6 MB)
│   │   │   └── realesrgan_anime_6b.onnx   # 6 RRDB block model (18.3 MB)
│   │   ├── java/com/pv/realesrgan/
│   │   │   ├── ml/
│   │   │   │   ├── HardwareDelegate.kt        # Auto, NPU/NNAPI, CPU selectors
│   │   │   │   ├── ModelArchitecture.kt       # Model configurations & metadata
│   │   │   │   ├── RealESRGANUpscalerEngine.kt # Tiled ONNX Runtime inference engine
│   │   │   │   └── UpscaleConfig.kt           # Parameter encapsulation
│   │   │   ├── ui/
│   │   │   │   ├── BeforeAfterSliderView.kt   # Custom interactive split-view
│   │   │   │   └── MainActivity.kt            # App controller & UI coordinator
│   │   │   └── utils/
│   │   │       └── ImageUtils.kt              # EXIF rotation, MediaStore & caching
│   │   └── res/
│   │       ├── layout/activity_main.xml
│   │       └── values/ (colors, strings, themes)
│   └── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 How to Build and Run

### Prerequisites
- Android SDK (API Level 26–35)
- JDK 17 or 21
- Android Studio Ladybug / Koala or CLI Gradle

### Command-Line Build
```bash
./gradlew assembleDebug
```
The generated APK will be at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🧪 Exporting Custom PyTorch Models to ONNX

To export another ESRGAN or custom super-resolution model from PyTorch `.pth` checkpoint to ONNX:

```python
import torch
import onnx
from upscale import RRDBNet

model = RRDBNet(in_nc=3, out_nc=3, nf=64, nb=23, gc=32)
state_dict = torch.load("RealESRGAN_x4plus.pth", map_location="cpu")
keyname = "params_ema" if "params_ema" in state_dict else "params"
model.load_state_dict(state_dict.get(keyname, state_dict))
model.eval()

dummy_input = torch.randn(1, 3, 256, 256)
dynamic_axes = {"input": {0: "batch", 2: "height", 3: "width"}, "output": {0: "batch", 2: "out_height", 3: "out_width"}}

torch.onnx.export(
    model,
    dummy_input,
    "realesrgan_custom.onnx",
    export_params=True,
    opset_version=18,
    do_constant_folding=True,
    input_names=["input"],
    output_names=["output"],
    dynamic_axes=dynamic_axes
)
```

---

## 📜 Credits & Acknowledgments

This application is built upon the breakthrough research and open-source models created by **Xintao Wang** and colleagues at ARC Lab, Tencent PCG, and SIAT:

* **Paper**: [Real-ESRGAN: Training Real-World Blind Super-Resolution with Pure Synthetic Data](https://arxiv.org/abs/2107.10833) (ICCVW 2021)
* **Authors**: Xintao Wang, Liangbin Xie, Chao Dong, Ying Shan
* **Official Repository**: [xinntao/Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN)
* **Model License**: [BSD 3-Clause License](https://github.com/xinntao/Real-ESRGAN/blob/master/LICENSE)

### BibTeX Citation
```bibtex
@inproceedings{wang2021realesrgan,
    author    = {Xintao Wang and Liangbin Xie and Chao Dong and Ying Shan},
    title     = {Real-ESRGAN: Training Real-World Blind Super-Resolution with Pure Synthetic Data},
    booktitle = {International Conference on Computer Vision Workshops (ICCVW)},
    year      = {2021}
}
```

### Additional Libraries & Tooling
* **[ONNX Runtime Mobile](https://github.com/microsoft/onnxruntime)** (Microsoft) - Licensed under MIT.
* **[Real-ESRGAN-ncnn-vulkan](https://github.com/xinntao/Real-ESRGAN-ncnn-vulkan)** (Nihui & Xintao Wang) - Reference for mobile/Vulkan shaders.

