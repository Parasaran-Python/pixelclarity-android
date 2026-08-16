package com.pv.realesrgan.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageUtilsTest {

    @Test
    fun testInvalidUrlSchemeFailsGracefully() = runBlocking {
        val result = ImageUtils.loadBitmapFromUrl("ftp://invalid-url-domain.xyz/image.png")
        assertTrue(result.isFailure)
    }

    @Test
    fun testNonExistentUrlFailsGracefully() = runBlocking {
        val result = ImageUtils.loadBitmapFromUrl("https://localhost:59999/nonexistent.png")
        assertTrue(result.isFailure)
    }

    @Test
    fun testEmptyUrlFailsGracefully() = runBlocking {
        val result = ImageUtils.loadBitmapFromUrl("")
        assertTrue(result.isFailure)
    }
}
