package com.pv.realesrgan.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UpscaleConfigTest {

    @Test
    fun testDefaultConfig() {
        val config = UpscaleConfig()
        assertEquals(ModelArchitecture.REAL_ESRGAN_X4PLUS, config.model)
        assertEquals(HardwareDelegate.AUTO, config.hardwareDelegate)
        assertEquals(256, config.tileSize)
        assertEquals(10, config.tilePad)
        assertEquals(4.0f, config.customScaleMultiplier, 0.001f)
    }

    @Test
    fun testCustomConfig() {
        val config = UpscaleConfig(
            model = ModelArchitecture.REAL_ESRGAN_ANIME_6B,
            hardwareDelegate = HardwareDelegate.NPU_NNAPI,
            tileSize = 512,
            tilePad = 16,
            customScaleMultiplier = 2.0f
        )
        assertEquals(ModelArchitecture.REAL_ESRGAN_ANIME_6B, config.model)
        assertEquals(HardwareDelegate.NPU_NNAPI, config.hardwareDelegate)
        assertEquals(512, config.tileSize)
        assertEquals(16, config.tilePad)
        assertEquals(2.0f, config.customScaleMultiplier, 0.001f)
    }

    @Test
    fun testConfigCopy() {
        val original = UpscaleConfig()
        val modified = original.copy(tileSize = 128, hardwareDelegate = HardwareDelegate.CPU)

        assertEquals(128, modified.tileSize)
        assertEquals(HardwareDelegate.CPU, modified.hardwareDelegate)
        assertEquals(original.model, modified.model)
        assertEquals(original.tilePad, modified.tilePad)
        assertNotEquals(original, modified)
    }
}
