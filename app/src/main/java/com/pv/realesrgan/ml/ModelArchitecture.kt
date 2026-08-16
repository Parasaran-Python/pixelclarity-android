package com.pv.realesrgan.ml

enum class ModelArchitecture(
    val title: String,
    val description: String,
    val fileName: String,
    val downloadUrl: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val sizeFormatted: String,
    val scaleFactor: Int = 4,
    val defaultTileSize: Int = 256
) {
    REAL_ESRGAN_X4PLUS(
        title = "Real-ESRGAN x4+",
        description = "High detail • 23 Residual Blocks • Best for Photos/Art",
        fileName = "realesrgan_x4plus.onnx",
        downloadUrl = "https://github.com/Parasaran-Python/pixelclarity-android/releases/download/v1.0.0-models/realesrgan_x4plus.onnx",
        fileSizeBytes = 68622543L,
        sha256 = "f8719e77074a76f7bd152715e936443fe5ef86ceb7933e022a9d08c6a81567f8",
        sizeFormatted = "68.6 MB",
        scaleFactor = 4,
        defaultTileSize = 256
    ),
    REAL_ESRGAN_ANIME_6B(
        title = "Real-ESRGAN Anime 6B",
        description = "Ultra fast • 6 Residual Blocks • Optimized for Mobile",
        fileName = "realesrgan_anime_6b.onnx",
        downloadUrl = "https://github.com/Parasaran-Python/pixelclarity-android/releases/download/v1.0.0-models/realesrgan_anime_6b.onnx",
        fileSizeBytes = 18357011L,
        sha256 = "65052d897fedacd7e8be956b4f40c3af8ff49b8e7a20dde3ba611c41f5cf63fe",
        sizeFormatted = "18.3 MB",
        scaleFactor = 4,
        defaultTileSize = 256
    );

    val assetFileName: String
        get() = "models/$fileName"
}
