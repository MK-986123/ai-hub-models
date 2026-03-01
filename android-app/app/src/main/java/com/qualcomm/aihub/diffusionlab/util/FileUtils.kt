package com.qualcomm.aihub.diffusionlab.util

import android.content.Context
import java.io.File

/**
 * File and storage path helpers.
 */
object FileUtils {

    fun getModelsDir(context: Context): File {
        return File(context.filesDir, Constants.MODELS_DIR).also { it.mkdirs() }
    }

    fun getSideloadDir(context: Context): File {
        return context.getExternalFilesDir(Constants.SIDELOAD_DIR)?.also { it.mkdirs() }
            ?: File(context.filesDir, Constants.SIDELOAD_DIR).also { it.mkdirs() }
    }

    fun getGalleryDir(context: Context): File {
        return File(context.filesDir, Constants.GALLERY_DIR).also { it.mkdirs() }
    }

    fun getCacheDir(context: Context): File {
        return File(context.cacheDir, Constants.CACHE_DIR).also { it.mkdirs() }
    }

    fun formatFileSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
