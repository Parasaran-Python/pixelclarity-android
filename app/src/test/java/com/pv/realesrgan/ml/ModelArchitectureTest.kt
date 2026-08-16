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
        assertEquals("models/realesrgan_x4plus.onnx", model.assetFileName)
        assertEquals(4, model.scaleFactor)
        assertEquals(256, model.defaultTileSize)
    }

    @Test
    fun testRealEsrganAnime6BProperties() {
        val model = ModelArchitecture.REAL_ESRGAN_ANIME_6B
        assertEquals("Real-ESRGAN Anime 6B", model.title)
        assertTrue(model.description.contains("6 Residual Blocks"))
        assertEquals("models/realesrgan_anime_6b.onnx", model.assetFileName)
        assertEquals(4, model.scaleFactor)
        assertEquals(256, model.defaultTileSize)
    }

    @Test
    fun testValueOf() {
        assertEquals(ModelArchitecture.REAL_ESRGAN_X4PLUS, ModelArchitecture.valueOf("REAL_ESRGAN_X4PLUS"))
        assertEquals(ModelArchitecture.REAL_ESRGAN_ANIME_6B, ModelArchitecture.valueOf("REAL_ESRGAN_ANIME_6B"))
    }
}
