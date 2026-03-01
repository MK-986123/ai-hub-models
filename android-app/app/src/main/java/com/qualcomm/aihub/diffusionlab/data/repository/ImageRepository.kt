package com.qualcomm.aihub.diffusionlab.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.qualcomm.aihub.diffusionlab.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages generated images: saving to internal gallery and exporting to MediaStore.
 */
@Singleton
class ImageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val galleryDir: File
        get() = File(context.filesDir, Constants.GALLERY_DIR).also { it.mkdirs() }

    /**
     * Saves a generated bitmap to the internal gallery.
     * Returns the file path for display.
     */
    suspend fun saveToGallery(bitmap: Bitmap, prefix: String = "gen"): String =
        withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${prefix}_${timestamp}.png"
            val file = File(galleryDir, fileName)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            file.absolutePath
        }

    /**
     * Exports an image to the device's shared Pictures folder via MediaStore.
     * Returns the content URI for sharing.
     */
    suspend fun exportToDevice(bitmap: Bitmap, displayName: String = "DiffusionLab"): Uri? =
        withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${displayName}_${timestamp}.png"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/DiffusionLab"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext null

            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            uri
        }

    /**
     * Lists all images in the internal gallery, newest first.
     */
    fun listGalleryImages(): List<File> {
        return galleryDir.listFiles()
            ?.filter { it.extension == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?.toList()
            ?: emptyList()
    }

    /**
     * Deletes an image from the internal gallery.
     */
    fun deleteImage(path: String): Boolean {
        return File(path).delete()
    }
}
