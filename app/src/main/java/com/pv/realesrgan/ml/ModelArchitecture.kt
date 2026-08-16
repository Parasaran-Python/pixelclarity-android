package com.pv.realesrgan.ml

enum class ModelArchitecture(
    val title: String,
    val description: String,
    val assetFileName: String,
    val scaleFactor: Int = 4,
    val defaultTileSize: Int = 256
) {
    REAL_ESRGAN_X4PLUS(
        title = "Real-ESRGAN x4+",
        description = "High detail • 23 Residual Blocks • Best for Photos/Art",
        assetFileName = "models/realesrgan_x4plus.onnx",
        scaleFactor = 4,
        defaultTileSize = 256
    ),
    REAL_ESRGAN_ANIME_6B(
        title = "Real-ESRGAN Anime 6B",
        description = "Ultra fast • 6 Residual Blocks • Optimized for Mobile",
        assetFileName = "models/realesrgan_anime_6b.onnx",
        scaleFactor = 4,
        defaultTileSize = 256
    )
}
