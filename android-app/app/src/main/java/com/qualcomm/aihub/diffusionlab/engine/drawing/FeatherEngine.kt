package com.qualcomm.aihub.diffusionlab.engine.drawing

import android.graphics.Bitmap
import com.qualcomm.aihub.diffusionlab.domain.model.EdgeMode

/**
 * Processes the raw mask bitmap with feather, edge, and denoise settings
 * to produce the final mask tensor for the inpainting model.
 *
 * Pipeline:
 * 1. Raw mask (hard edges from brush strokes)
 * 2. Gaussian blur (feather radius)
 * 3. Edge threshold/curve (sharp/smooth/soft)
 * 4. Final mask ready for model input
 *
 * Denoise is applied post-inpainting (in InpaintPipeline), not here.
 */
object FeatherEngine {

    /**
     * Processes a raw mask bitmap into a feathered + edge-processed float array.
     *
     * @param mask Raw binary mask from MaskRenderer
     * @param featherPercent 0-100%, maps to Gaussian blur radius (0-20px at 512)
     * @param edgeMode How to threshold the feathered mask
     * @return Float array [height * width] with values in [0, 1]
     */
    fun processMask(
        mask: Bitmap,
        featherPercent: Float,
        edgeMode: EdgeMode,
    ): FloatArray {
        val width = mask.width
        val height = mask.height

        // Extract grayscale values from mask
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)
        var values = FloatArray(width * height) { i ->
            ((pixels[i] shr 16) and 0xFF) / 255f
        }

        // Step 1: Feather (Gaussian blur)
        val blurRadius = (featherPercent / 100f * 20f).toInt()
        if (blurRadius > 0) {
            values = gaussianBlur(values, width, height, blurRadius)
        }

        // Step 2: Edge mode
        values = applyEdgeMode(values, edgeMode)

        return values
    }

    private fun gaussianBlur(
        input: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
    ): FloatArray {
        val kernelSize = radius * 2 + 1
        val sigma = radius / 3f
        val kernel = FloatArray(kernelSize)
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
                    value += input[y * width + sx] * kernel[k]
                }
                temp[y * width + x] = value
            }
        }

        // Vertical pass
        val output = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var value = 0f
                for (k in 0 until kernelSize) {
                    val sy = (y + k - radius).coerceIn(0, height - 1)
                    value += temp[sy * width + x] * kernel[k]
                }
                output[y * width + x] = value
            }
        }

        return output
    }

    private fun applyEdgeMode(values: FloatArray, mode: EdgeMode): FloatArray {
        return when (mode) {
            EdgeMode.SHARP -> {
                // Hard threshold at 0.5
                FloatArray(values.size) { i ->
                    if (values[i] > 0.5f) 1f else 0f
                }
            }
            EdgeMode.SMOOTH -> {
                // Smoothstep interpolation between 0.3 and 0.7
                FloatArray(values.size) { i ->
                    smoothstep(0.3f, 0.7f, values[i])
                }
            }
            EdgeMode.SOFT -> {
                // No thresholding — raw feathered values
                values.copyOf()
            }
        }
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
