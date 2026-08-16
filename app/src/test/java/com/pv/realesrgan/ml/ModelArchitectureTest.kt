package com.pv.realesrgan.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelArchitectureTest {

    @Test
    fun testModelArchitectureCount() {
        val models = ModelArchitecture.values()
        assertEquals(2, models.size)
    }

    @Test
    fun testRealEsrganX4PlusProperties() {
        val model = ModelArchitecture.REAL_ESRGAN_X4PLUS
        assertEquals("Real-ESRGAN x4+", model.title)
        assertTrue(model.description.contains("23 Residual Blocks"))
        assertEquals("realesrgan_x4plus.onnx", model.fileName)
        assertEquals("models/realesrgan_x4plus.onnx", model.assetFileName)
        assertEquals("https://github.com/Parasaran-Python/pixelclarity-android/releases/download/v1.0.0-models/realesrgan_x4plus.onnx", model.downloadUrl)
        assertEquals("f8719e77074a76f7bd152715e936443fe5ef86ceb7933e022a9d08c6a81567f8", model.sha256)
        assertEquals("68.6 MB", model.sizeFormatted)
        assertEquals(68622543L, model.fileSizeBytes)
        assertEquals(4, model.scaleFactor)
        assertEquals(256, model.defaultTileSize)
    }

    @Test
    fun testRealEsrganAnime6BProperties() {
        val model = ModelArchitecture.REAL_ESRGAN_ANIME_6B
        assertEquals("Real-ESRGAN Anime 6B", model.title)
        assertTrue(model.description.contains("6 Residual Blocks"))
        assertEquals("realesrgan_anime_6b.onnx", model.fileName)
        assertEquals("models/realesrgan_anime_6b.onnx", model.assetFileName)
        assertEquals("https://github.com/Parasaran-Python/pixelclarity-android/releases/download/v1.0.0-models/realesrgan_anime_6b.onnx", model.downloadUrl)
        assertEquals("65052d897fedacd7e8be956b4f40c3af8ff49b8e7a20dde3ba611c41f5cf63fe", model.sha256)
        assertEquals("18.3 MB", model.sizeFormatted)
        assertEquals(18357011L, model.fileSizeBytes)
        assertEquals(4, model.scaleFactor)
        assertEquals(256, model.defaultTileSize)
    }

    @Test
    fun testValueOf() {
        assertEquals(ModelArchitecture.REAL_ESRGAN_X4PLUS, ModelArchitecture.valueOf("REAL_ESRGAN_X4PLUS"))
        assertEquals(ModelArchitecture.REAL_ESRGAN_ANIME_6B, ModelArchitecture.valueOf("REAL_ESRGAN_ANIME_6B"))
    }
}
