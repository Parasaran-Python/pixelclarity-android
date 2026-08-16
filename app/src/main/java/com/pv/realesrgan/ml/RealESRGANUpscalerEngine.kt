package com.pv.realesrgan.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.nio.FloatBuffer
import java.util.EnumSet
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class RealESRGANUpscalerEngine(private val context: Context) : AutoCloseable {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private var currentModel: ModelArchitecture? = null
    private var currentDelegate: HardwareDelegate? = null

    @Synchronized
    fun initialize(model: ModelArchitecture, delegate: HardwareDelegate) {
        if (ortSession != null && currentModel == model && currentDelegate == delegate) {
            return
        }

        closeSession()

        val sessionOptions = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(max(1, min(4, Runtime.getRuntime().availableProcessors())))
            
            when (delegate) {
                HardwareDelegate.NPU_NNAPI -> {
                    try {
                        addNnapi()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                HardwareDelegate.AUTO -> {
                    try {
                        addNnapi()
                    } catch (e: Exception) {
                        // Silently fallback to CPU
                    }
                }
                HardwareDelegate.CPU -> {
                    // Standard multi-threaded CPU
                }
            }
        }

        val modelBytes = context.assets.open(model.assetFileName).use { it.readBytes() }
        ortSession = ortEnv.createSession(modelBytes, sessionOptions)
        currentModel = model
        currentDelegate = delegate
    }

    suspend fun upscale(
        inputBitmap: Bitmap,
        config: UpscaleConfig,
        listener: UpscaleProgressListener? = null
    ): Bitmap {
        initialize(config.model, config.hardwareDelegate)
        val session = ortSession ?: throw IllegalStateException("ONNX Session could not be initialized")

        val startTime = System.currentTimeMillis()
        val inWidth = inputBitmap.width
        val inHeight = inputBitmap.height
        val scale = config.model.scaleFactor // 4x

        val outWidth = inWidth * scale
        val outHeight = inHeight * scale

        val outputBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)

        val tileSize = config.tileSize
        val tilePad = config.tilePad

        val tilesX = ceil(inWidth.toDouble() / tileSize).toInt()
        val tilesY = ceil(inHeight.toDouble() / tileSize).toInt()
        val totalTiles = tilesX * tilesY

        var currentTile = 0

        listener?.onStatusUpdate("Starting upscale (${inWidth}x${inHeight} -> ${outWidth}x${outHeight}) across $totalTiles tiles...")

        for (y in 0 until tilesY) {
            for (x in 0 until tilesX) {
                if (!currentCoroutineContext().isActive) {
                    outputBitmap.recycle()
                    throw java.util.concurrent.CancellationException("Upscaling was cancelled")
                }

                currentTile++

                val inputStartX = x * tileSize
                val inputEndX = min(inputStartX + tileSize, inWidth)
                val inputStartY = y * tileSize
                val inputEndY = min(inputStartY + tileSize, inHeight)

                val inputStartXPad = max(inputStartX - tilePad, 0)
                val inputEndXPad = min(inputEndX + tilePad, inWidth)
                val inputStartYPad = max(inputStartY - tilePad, 0)
                val inputEndYPad = min(inputEndY + tilePad, inHeight)

                val padLeft = inputStartX - inputStartXPad
                val padTop = inputStartY - inputStartYPad

                val tileW = inputEndXPad - inputStartXPad
                val tileH = inputEndYPad - inputStartYPad

                // Extract pixels from padded tile
                val tilePixels = IntArray(tileW * tileH)
                inputBitmap.getPixels(tilePixels, 0, tileW, inputStartXPad, inputStartYPad, tileW, tileH)

                // Convert to NCHW FloatBuffer (1 x 3 x tileH x tileW)
                val floatBuffer = FloatBuffer.allocate(1 * 3 * tileH * tileW)
                val planeSize = tileW * tileH

                // Red channel
                for (i in 0 until planeSize) {
                    val c = tilePixels[i]
                    floatBuffer.put(i, ((c shr 16) and 0xFF) / 255.0f)
                }
                // Green channel
                for (i in 0 until planeSize) {
                    val c = tilePixels[i]
                    floatBuffer.put(planeSize + i, ((c shr 8) and 0xFF) / 255.0f)
                }
                // Blue channel
                for (i in 0 until planeSize) {
                    val c = tilePixels[i]
                    floatBuffer.put(2 * planeSize + i, (c and 0xFF) / 255.0f)
                }
                floatBuffer.rewind()

                val tensorShape = longArrayOf(1, 3, tileH.toLong(), tileW.toLong())
                val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, tensorShape)

                val outTileW = tileW * scale
                val outTileH = tileH * scale

                inputTensor.use { tensor ->
                    val results = session.run(mapOf("input" to tensor))
                    results.use { outputMap ->
                        val outputValue = outputMap[0].value
                        
                        // Extract output tensor pixels
                        val outCropStartX = padLeft * scale
                        val outCropStartY = padTop * scale
                        val cropW = (inputEndX - inputStartX) * scale
                        val cropH = (inputEndY - inputStartY) * scale

                        val croppedPixels = IntArray(cropW * cropH)
                        val outPlaneSize = outTileW * outTileH

                        when (outputValue) {
                            is Array<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                val outArray = outputValue as Array<Array<Array<FloatArray>>>
                                val outR = outArray[0][0]
                                val outG = outArray[0][1]
                                val outB = outArray[0][2]

                                for (row in 0 until cropH) {
                                    val srcRow = outCropStartY + row
                                    for (col in 0 until cropW) {
                                        val srcCol = outCropStartX + col
                                        val r = (outR[srcRow][srcCol].coerceIn(0f, 1f) * 255.0f).roundToInt()
                                        val g = (outG[srcRow][srcCol].coerceIn(0f, 1f) * 255.0f).roundToInt()
                                        val b = (outB[srcRow][srcCol].coerceIn(0f, 1f) * 255.0f).roundToInt()
                                        croppedPixels[row * cropW + col] = Color.rgb(r, g, b)
                                    }
                                }
                            }
                            is OnnxTensor -> {
                                val buf = outputValue.floatBuffer
                                for (row in 0 until cropH) {
                                    val srcRow = outCropStartY + row
                                    for (col in 0 until cropW) {
                                        val srcCol = outCropStartX + col
                                        val offset = srcRow * outTileW + srcCol
                                        val r = (buf.get(offset).coerceIn(0f, 1f) * 255.0f).roundToInt()
                                        val g = (buf.get(outPlaneSize + offset).coerceIn(0f, 1f) * 255.0f).roundToInt()
                                        val b = (buf.get(2 * outPlaneSize + offset).coerceIn(0f, 1f) * 255.0f).roundToInt()
                                        croppedPixels[row * cropW + col] = Color.rgb(r, g, b)
                                    }
                                }
                            }
                            else -> throw IllegalStateException("Unexpected output format from ONNX model: ${outputValue?.javaClass}")
                        }

                        val outDestX = inputStartX * scale
                        val outDestY = inputStartY * scale
                        outputBitmap.setPixels(croppedPixels, 0, cropW, outDestX, outDestY, cropW, cropH)
                    }
                }

                val elapsed = System.currentTimeMillis() - startTime
                val percent = ((currentTile.toFloat() / totalTiles) * 100).toInt()
                listener?.onProgress(currentTile, totalTiles, percent, elapsed)
            }
        }

        // Check if custom scaling is requested (e.g. 2x)
        if (config.customScaleMultiplier > 0 && config.customScaleMultiplier != 4.0f) {
            val finalW = (inWidth * config.customScaleMultiplier).roundToInt()
            val finalH = (inHeight * config.customScaleMultiplier).roundToInt()
            val rescaled = Bitmap.createScaledBitmap(outputBitmap, finalW, finalH, true)
            outputBitmap.recycle()
            return rescaled
        }

        return outputBitmap
    }

    private fun closeSession() {
        ortSession?.close()
        ortSession = null
        currentModel = null
        currentDelegate = null
    }

    override fun close() {
        closeSession()
        ortEnv.close()
    }
}
