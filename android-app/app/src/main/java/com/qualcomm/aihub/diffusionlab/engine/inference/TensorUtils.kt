package com.qualcomm.aihub.diffusionlab.engine.inference

import android.graphics.Bitmap
import com.qualcomm.aihub.diffusionlab.util.Constants.SD_OUTPUT_HEIGHT
import com.qualcomm.aihub.diffusionlab.util.Constants.SD_OUTPUT_WIDTH
import kotlin.math.max
import kotlin.math.min

/**
 * Utilities for converting between Android Bitmaps and ONNX tensors (float arrays).
 */
object TensorUtils {

    /**
     * Converts a Bitmap to NCHW float tensor normalized to [0, 1].
     * Shape: [1, 3, height, width]
     */
    fun bitmapToTensor(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val tensor = FloatArray(3 * height * width)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = ((pixel shr 16) and 0xFF) / 255f
                val g = ((pixel shr 8) and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f
                tensor[0 * height * width + y * width + x] = r
                tensor[1 * height * width + y * width + x] = g
                tensor[2 * height * width + y * width + x] = b
            }
        }
        return tensor
    }

    /**
     * Converts NCHW float tensor [1, 3, H, W] in [0, 1] range to a Bitmap.
     */
    fun tensorToBitmap(tensor: FloatArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        val planeSize = width * height

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val r = clampToByte(tensor[0 * planeSize + idx])
                val g = clampToByte(tensor[1 * planeSize + idx])
                val b = clampToByte(tensor[2 * planeSize + idx])
                pixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Converts SD VAE decoder output (range approx [-1, 1]) to [0, 1] then to Bitmap.
     * The VAE decoder outputs in NCHW format [1, 3, 512, 512].
     */
    fun vaeOutputToBitmap(
        output: FloatArray,
        width: Int = SD_OUTPUT_WIDTH,
        height: Int = SD_OUTPUT_HEIGHT,
    ): Bitmap {
        val pixels = IntArray(width * height)
        val planeSize = width * height

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                // VAE output is approximately in [-1, 1], remap to [0, 255]
                val r = clampToByte((output[0 * planeSize + idx] + 1f) * 0.5f)
                val g = clampToByte((output[1 * planeSize + idx] + 1f) * 0.5f)
                val b = clampToByte((output[2 * planeSize + idx] + 1f) * 0.5f)
                pixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Creates a binary mask tensor from a Bitmap.
     * White (> 128) = 1.0 (masked/inpaint area), Black = 0.0 (keep area).
     * Shape: [1, 1, height, width]
     */
    fun bitmapToMaskTensor(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        return FloatArray(height * width) { idx ->
            val pixel = pixels[idx]
            val gray = ((pixel shr 16) and 0xFF) / 255f
            if (gray > 0.5f) 1f else 0f
        }
    }

    /**
     * Applies Gaussian blur to a mask for feathering.
     * Operates on a 2D float array (height × width).
     */
    fun applyFeather(mask: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        if (radius <= 0) return mask.copyOf()

        // Build 1D Gaussian kernel
        val kernelSize = radius * 2 + 1
        val kernel = FloatArray(kernelSize)
        val sigma = radius / 3f
        var sum = 0f
        for (i in 0 until kernelSize) {
            val x = (i - radius).toFloat()
            kernel[i] = kotlin.math.exp(-(x * x) / (2 * sigma * sigma))
            sum += kernel[i]
        }
        for (i in 0 until kernelSize) kernel[i] /= sum

        // Horizontal pass
        val temp = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var value = 0f
                for (k in 0 until kernelSize) {
                    val sx = (x + k - radius).coerceIn(0, width - 1)
                    value += mask[y * width + sx] * kernel[k]
                }
                temp[y * width + x] = value
            }
        }

        // Vertical pass
        val result = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var value = 0f
                for (k in 0 until kernelSize) {
                    val sy = (y + k - radius).coerceIn(0, height - 1)
                    value += temp[sy * width + x] * kernel[k]
                }
                result[y * width + x] = value
            }
        }

        return result
    }

    private fun clampToByte(value: Float): Int {
        return (max(0f, min(1f, value)) * 255f).toInt()
    }
}
