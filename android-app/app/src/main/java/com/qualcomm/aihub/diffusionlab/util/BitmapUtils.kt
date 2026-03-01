package com.qualcomm.aihub.diffusionlab.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * Utility functions for Bitmap operations.
 */
object BitmapUtils {

    /**
     * Loads a bitmap from a content URI, downscaling to fit within maxDimension.
     */
    fun loadBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = 512,
    ): Bitmap? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null

        // First pass: read dimensions only
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        // Calculate inSampleSize
        val width = options.outWidth
        val height = options.outHeight
        var sampleSize = 1
        while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        // Second pass: decode with downsampling
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val stream2 = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = BitmapFactory.decodeStream(stream2, null, decodeOptions)
        stream2.close()

        return bitmap
    }

    /**
     * Resizes a bitmap to exactly the given dimensions.
     */
    fun resize(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /**
     * Creates a center-cropped square bitmap.
     */
    fun centerCropSquare(bitmap: Bitmap, size: Int): Bitmap {
        val minDim = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - minDim) / 2
        val y = (bitmap.height - minDim) / 2
        val cropped = Bitmap.createBitmap(bitmap, x, y, minDim, minDim)
        return resize(cropped, size, size)
    }
}
