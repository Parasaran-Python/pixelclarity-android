package com.pv.realesrgan.ml

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object ModelManager {

    private const val BUFFER_SIZE = 64 * 1024 // 64 KB chunk

    fun getModelsDirectory(context: Context): File {
        val dir = File(context.noBackupFilesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getModelFile(context: Context, model: ModelArchitecture): File {
        return File(getModelsDirectory(context), model.fileName)
    }

    fun isModelDownloaded(context: Context, model: ModelArchitecture): Boolean {
        val file = getModelFile(context, model)
        return file.exists() && file.isFile && file.length() > 0L
    }

    fun isModelInAssets(context: Context, model: ModelArchitecture): Boolean {
        return try {
            context.assets.open(model.assetFileName).use { true }
        } catch (e: Exception) {
            false
        }
    }

    fun isModelAvailable(context: Context, model: ModelArchitecture): Boolean {
        return isModelDownloaded(context, model) || isModelInAssets(context, model)
    }

    fun deleteModel(context: Context, model: ModelArchitecture): Boolean {
        val file = getModelFile(context, model)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    suspend fun downloadModel(
        context: Context,
        model: ModelArchitecture,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = getModelFile(context, model)
        val modelsDir = getModelsDirectory(context)
        val tmpFile = File(modelsDir, "${model.fileName}.tmp_${System.currentTimeMillis()}")

        var connection: HttpURLConnection? = null
        try {
            var currentUrl = model.downloadUrl
            var redirects = 0
            val maxRedirects = 6

            while (true) {
                currentCoroutineContext().ensureActive()
                val url = URL(currentUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "PixelClarity-Android-ModelDownloader")
                    setRequestProperty("Accept-Encoding", "identity")
                }
                connection = conn
                val status = conn.responseCode

                if (status in 300..399) {
                    val newLocation = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (newLocation != null && redirects < maxRedirects) {
                        currentUrl = newLocation
                        redirects++
                        continue
                    } else {
                        throw IOException("Failed to follow redirect ($status) to $newLocation")
                    }
                }

                if (status !in 200..299) {
                    throw IOException("Server returned HTTP $status: ${conn.responseMessage}")
                }
                break
            }

            val conn = connection ?: throw IOException("Could not establish connection")
            val reportedLength = conn.contentLengthLong
            val totalBytes = if (reportedLength > 0) reportedLength else model.fileSizeBytes

            val digest = MessageDigest.getInstance("SHA-256")
            var downloadedBytes = 0L

            conn.inputStream.use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var lastReportedTime = 0L
                    var lastReportedPercent = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val percent = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }

                        val now = System.currentTimeMillis()
                        if (percent != lastReportedPercent || now - lastReportedTime > 64 || downloadedBytes == totalBytes) {
                            lastReportedPercent = percent
                            lastReportedTime = now
                            onProgress(percent, downloadedBytes, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            // Verify checksum if available
            if (model.sha256.isNotBlank()) {
                val hexString = digest.digest().joinToString("") { "%02x".format(it) }
                if (!hexString.equals(model.sha256, ignoreCase = true)) {
                    tmpFile.delete()
                    return@withContext Result.failure(
                        IllegalStateException("Model checksum verification failed! Expected ${model.sha256} but got $hexString")
                    )
                }
            }

            // Atomically replace target file
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tmpFile.renameTo(targetFile)) {
                tmpFile.copyTo(targetFile, overwrite = true)
                tmpFile.delete()
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
