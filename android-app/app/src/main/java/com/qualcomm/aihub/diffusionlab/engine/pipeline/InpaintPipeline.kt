package com.qualcomm.aihub.diffusionlab.engine.pipeline

import android.graphics.Bitmap
import android.util.Log
import com.qualcomm.aihub.diffusionlab.data.repository.ModelRepository
import com.qualcomm.aihub.diffusionlab.domain.model.InpaintParams
import com.qualcomm.aihub.diffusionlab.domain.model.PipelineState
import com.qualcomm.aihub.diffusionlab.engine.inference.OnnxInferenceEngine
import com.qualcomm.aihub.diffusionlab.engine.inference.QnnSessionManager
import com.qualcomm.aihub.diffusionlab.engine.inference.TensorUtils
import com.qualcomm.aihub.diffusionlab.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inpainting pipeline using LaMa-Dilated (high quality) or AOT-GAN (fast mode).
 *
 * Takes an input image + binary mask (drawn by user via S-Pen) and fills
 * the masked region with generated content.
 *
 * Models from the repo:
 * - LaMa-Dilated: 512×512, 45.6M params, Fourier convolutions, high quality
 * - AOT-GAN: 512×512, 15.2M params, faster but lower quality
 *
 * Outpainting is implemented by:
 * 1. Expanding canvas (pad image with black + mask the padded region)
 * 2. Running inpainting on the expanded canvas
 */
@Singleton
class InpaintPipeline @Inject constructor(
    private val engine: OnnxInferenceEngine,
    private val sessionManager: QnnSessionManager,
    private val modelRepository: ModelRepository,
) {
    companion object {
        private const val TAG = "InpaintPipeline"
        private const val INPAINT_SIZE = 512
    }

    private val _state = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val state: StateFlow<PipelineState> = _state

    /**
     * Inpaints the masked region of an image.
     *
     * @param image Source image (will be resized to 512×512 for inference)
     * @param mask Binary mask bitmap (white = inpaint, black = keep)
     * @param params Inpainting parameters (feather, edge, denoise)
     * @return Inpainted bitmap at original resolution
     */
    suspend fun inpaint(
        image: Bitmap,
        mask: Bitmap,
        params: InpaintParams,
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            _state.value = PipelineState.Inpainting(0f)

            // Choose model based on quick mode preference
            val modelId = if (params.useQuickMode) "aotgan" else "lama"
            val modelFile = modelRepository.getModelFile(modelId)
                ?: throw IllegalStateException("Inpainting model ($modelId) not downloaded")

            // Resize to model input size
            val resizedImage = Bitmap.createScaledBitmap(
                image, INPAINT_SIZE, INPAINT_SIZE, true
            )
            val resizedMask = Bitmap.createScaledBitmap(
                mask, INPAINT_SIZE, INPAINT_SIZE, true
            )

            // Convert to tensors
            val imageTensor = TensorUtils.bitmapToTensor(resizedImage)
            val maskTensor = TensorUtils.bitmapToMaskTensor(resizedMask)

            // Apply feathering to mask
            val featherRadius = (params.featherPercent / 100f * 20f).toInt()
            val featheredMask = TensorUtils.applyFeather(
                maskTensor, INPAINT_SIZE, INPAINT_SIZE, featherRadius
            )

            _state.value = PipelineState.Inpainting(0.3f)

            // Load and run inpainting model
            val session = sessionManager.acquireSession(modelId, modelFile)

            val imageOnnx = engine.createTensor(
                imageTensor,
                longArrayOf(1, 3, INPAINT_SIZE.toLong(), INPAINT_SIZE.toLong()),
            )
            val maskOnnx = engine.createTensor(
                featheredMask,
                longArrayOf(1, 1, INPAINT_SIZE.toLong(), INPAINT_SIZE.toLong()),
            )

            _state.value = PipelineState.Inpainting(0.5f)

            val output = engine.runInference(
                session,
                mapOf("image" to imageOnnx, "mask" to maskOnnx),
            ).values.first()

            imageOnnx.close()
            maskOnnx.close()
            sessionManager.releaseSession(modelId)

            _state.value = PipelineState.Inpainting(0.8f)

            // Convert output to bitmap
            var resultBitmap = TensorUtils.tensorToBitmap(
                output, INPAINT_SIZE, INPAINT_SIZE
            )

            // Apply denoise blending at mask boundaries
            if (params.denoiseStrength > 0f) {
                resultBitmap = blendAtBoundaries(
                    original = resizedImage,
                    inpainted = resultBitmap,
                    mask = featheredMask,
                    denoiseStrength = params.denoiseStrength,
                    width = INPAINT_SIZE,
                    height = INPAINT_SIZE,
                )
            }

            // Scale back to original resolution if needed
            if (image.width != INPAINT_SIZE || image.height != INPAINT_SIZE) {
                resultBitmap = Bitmap.createScaledBitmap(
                    resultBitmap, image.width, image.height, true
                )
            }

            _state.value = PipelineState.Idle
            Log.i(TAG, "Inpainting complete with $modelId")

            resultBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Inpainting failed", e)
            _state.value = PipelineState.Error(e.message ?: "Inpainting failed")
            throw e
        }
    }

    /**
     * Outpaints by expanding the canvas in the given direction.
     *
     * @param image Source image
     * @param expandPx Pixels to expand in each direction
     * @param params Inpainting parameters for filling expanded area
     * @return Outpainted bitmap with expanded canvas
     */
    suspend fun outpaint(
        image: Bitmap,
        expandPx: Int,
        params: InpaintParams,
    ): Bitmap = withContext(Dispatchers.Default) {
        val newWidth = image.width + expandPx * 2
        val newHeight = image.height + expandPx * 2

        // Create expanded canvas (black border)
        val expandedImage = Bitmap.createBitmap(
            newWidth, newHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(expandedImage)
        canvas.drawBitmap(image, expandPx.toFloat(), expandPx.toFloat(), null)

        // Create mask for expanded region (white border = needs inpainting)
        val maskBitmap = Bitmap.createBitmap(
            newWidth, newHeight, Bitmap.Config.ARGB_8888
        )
        val maskCanvas = android.graphics.Canvas(maskBitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
        }
        maskCanvas.drawRect(0f, 0f, newWidth.toFloat(), newHeight.toFloat(), paint)
        paint.color = android.graphics.Color.BLACK
        maskCanvas.drawRect(
            expandPx.toFloat(),
            expandPx.toFloat(),
            (expandPx + image.width).toFloat(),
            (expandPx + image.height).toFloat(),
            paint,
        )

        // Run inpainting on the expanded canvas
        inpaint(expandedImage, maskBitmap, params)
    }

    /**
     * Blends original and inpainted images at mask boundaries for seamless transitions.
     */
    private fun blendAtBoundaries(
        original: Bitmap,
        inpainted: Bitmap,
        mask: FloatArray,
        denoiseStrength: Float,
        width: Int,
        height: Int,
    ): Bitmap {
        val originalPixels = IntArray(width * height)
        val inpaintedPixels = IntArray(width * height)
        original.getPixels(originalPixels, 0, width, 0, 0, width, height)
        inpainted.getPixels(inpaintedPixels, 0, width, 0, 0, width, height)

        val resultPixels = IntArray(width * height)
        for (i in 0 until width * height) {
            val maskVal = mask[i]
            // Blend factor: in fully masked area use inpainted, at edges blend
            val blend = maskVal * denoiseStrength + (1f - denoiseStrength) * maskVal

            val origR = (originalPixels[i] shr 16) and 0xFF
            val origG = (originalPixels[i] shr 8) and 0xFF
            val origB = originalPixels[i] and 0xFF

            val inpR = (inpaintedPixels[i] shr 16) and 0xFF
            val inpG = (inpaintedPixels[i] shr 8) and 0xFF
            val inpB = inpaintedPixels[i] and 0xFF

            val r = (origR * (1f - blend) + inpR * blend).toInt().coerceIn(0, 255)
            val g = (origG * (1f - blend) + inpG * blend).toInt().coerceIn(0, 255)
            val b = (origB * (1f - blend) + inpB * blend).toInt().coerceIn(0, 255)

            resultPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        return Bitmap.createBitmap(resultPixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
