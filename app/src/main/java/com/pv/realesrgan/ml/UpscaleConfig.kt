package com.pv.realesrgan.ml

data class UpscaleConfig(
    val model: ModelArchitecture = ModelArchitecture.REAL_ESRGAN_X4PLUS,
    val hardwareDelegate: HardwareDelegate = HardwareDelegate.AUTO,
    val tileSize: Int = 256,
    val tilePad: Int = 10,
    val customScaleMultiplier: Float = 4.0f
)

interface UpscaleProgressListener {
    fun onProgress(currentTile: Int, totalTiles: Int, progressPercent: Int, elapsedMs: Long)
    fun onStatusUpdate(statusText: String)
}
