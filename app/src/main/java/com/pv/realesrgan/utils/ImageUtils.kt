package com.pv.realesrgan.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageUtils {

    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val rawBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            inputStream.close()

            // Handle EXIF orientation
            val exifStream = context.contentResolver.openInputStream(uri)
            val rotation = if (exifStream != null) {
                val exif = ExifInterface(exifStream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                exifStream.close()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } else 0f

            return if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            inputStream?.close()
        }
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, baseFileName: String = "RealESRGAN_HD"): Uri? {
        val filename = "${baseFileName}_${System.currentTimeMillis()}.png"
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RealESRGAN")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null

        try {
            resolver.openOutputStream(imageUri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }

            return imageUri
        } catch (e: Exception) {
            e.printStackTrace()
            resolver.delete(imageUri, null, null)
            return null
        }
    }

    fun getShareableUri(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "shared_upscaled_${System.currentTimeMillis()}.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
