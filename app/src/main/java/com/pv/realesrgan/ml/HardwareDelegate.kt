package com.pv.realesrgan.ml

enum class HardwareDelegate(val displayName: String, val description: String) {
    AUTO("⚡ Auto", "Best available (NPU / GPU with CPU fallback)"),
    NPU_NNAPI("🧠 NPU / NNAPI", "Dedicated Neural Processing Unit / NNAPI"),
    CPU("💻 CPU (Multi-threaded)", "Reliable multi-threaded CPU execution")
}
