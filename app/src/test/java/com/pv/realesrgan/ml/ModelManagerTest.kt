package com.pv.realesrgan.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class ModelManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testModelArchitectureIntegrityProperties() {
        for (model in ModelArchitecture.values()) {
            assertNotNull(model.title)
            assertNotNull(model.fileName)
            assertTrue(model.fileName.endsWith(".onnx"))
            assertTrue(model.downloadUrl.startsWith("https://"))
            assertTrue(model.sha256.length == 64)
            assertTrue(model.fileSizeBytes > 0)
            assertTrue(model.sizeFormatted.endsWith("MB"))
            assertEquals(4, model.scaleFactor)
            assertEquals(256, model.defaultTileSize)
        }
    }

    @Test
    fun testChecksumCalculationLogic() {
        val testContent = "Test content for SHA-256 verification"
        val testFile = tempFolder.newFile("test_model.onnx")
        testFile.writeText(testContent)

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(testFile.readBytes())
        val calculatedHex = hashBytes.joinToString("") { "%02x".format(it) }

        assertEquals(64, calculatedHex.length)
        assertTrue(calculatedHex.matches(Regex("^[a-f0-9]{64}$")))
    }
}
