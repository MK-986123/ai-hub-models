package com.qualcomm.aihub.diffusionlab.engine.pipeline

import android.graphics.Bitmap
import android.util.Log
import com.qualcomm.aihub.diffusionlab.data.repository.ModelRepository
import com.qualcomm.aihub.diffusionlab.domain.model.PipelineState
import com.qualcomm.aihub.diffusionlab.domain.model.UpscaleParams
import com.qualcomm.aihub.diffusionlab.engine.inference.OnnxInferenceEngine
import com.qualcomm.aihub.diffusionlab.engine.inference.QnnSessionManager
import com.qualcomm.aihub.diffusionlab.engine.inference.TensorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Upscaling pipeline supporting two models:
 *
 * 1. Real-ESRGAN x4plus: High quality 4× upscaling
 *    - 16.7M params, 16.7MB (w8a8 quantized)
 *    - Input: 128×128 → Output: 512×512
 *    - ~25ms inference on NPU
 *    - Premium feature
 *
 * 2. XLSR: Ultra-fast lightweight upscaling
 *    - 28K params, 115KB
 *    - Supports 2×/3×/4× scale
 *    - ~3ms inference on NPU
 *    - Free tier
 *
 * For images larger than the model's input size (128×128 for Real-ESRGAN),
 * the image is processed in tiles with overlap to avoid seam artifacts.
 */
@Singleton
class UpscalePipeline @Inject constructor(
    private val engine: OnnxInferenceEngine,
    private val sessionManager: QnnSessionManager,
    private val modelRepository: ModelRepository,
) {
    companion object {
        private const val TAG = "UpscalePipeline"
        private const val ESRGAN_INPUT_SIZE = 128
        private const val ESRGAN_SCALE = 4
        private const val TILE_OVERLAP = 8
    }

    private val _state = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val state: StateFlow<PipelineState> = _state

    /**
     * Upscales an image using Real-ESRGAN (premium) or XLSR (free).
     *
     * For Real-ESRGAN:
     * - If image is ≤128×128, runs in a single pass
     * - If larger, tiles the image with overlap and stitches results
     *
     * @param image Source bitmap to upscale
     * @param params Upscale parameters (scale factor, model choice)
     * @return Upscaled bitmap
     */
    suspend fun upscale(
        image: Bitmap,
        params: UpscaleParams,
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            _state.value = PipelineState.Upscaling(0f)

            val modelId = if (params.useRealEsrgan) "real_esrgan" else "xlsr"
            val modelFile = modelRepository.getModelFile(modelId)
                ?: throw IllegalStateException("Upscaler model ($modelId) not downloaded")

            val session = sessionManager.acquireSession(modelId, modelFile)

            val result = if (params.useRealEsrgan) {
                upscaleWithRealEsrgan(image, session)
            } else {
                upscaleWithXlsr(image, params.scaleFactor, session)
            }

            sessionManager.releaseSession(modelId)
            _state.value = PipelineState.Idle
            Log.i(TAG, "Upscale complete: ${image.width}x${image.height} → ${result.width}x${result.height}")

            result
        } catch (e: Exception) {
            Log.e(TAG, "Upscaling failed", e)
            _state.value = PipelineState.Error(e.message ?: "Upscaling failed")
            throw e
        }
    }

    /**
     * Real-ESRGAN upscaling with tiled processing for images > 128×128.
     */
    private suspend fun upscaleWithRealEsrgan(
        image: Bitmap,
        session: ai.onnxruntime.OrtSession,
    ): Bitmap {
        if (image.width <= ESRGAN_INPUT_SIZE && image.height <= ESRGAN_INPUT_SIZE) {
            // Single-pass for small images
            return upscaleTile(image, session, ESRGAN_SCALE)
        }

        // Tiled processing for larger images
        val tileSize = ESRGAN_INPUT_SIZE
        val outputScale = ESRGAN_SCALE

        val outputWidth = image.width * outputScale
        val outputHeight = image.height * outputScale
        val outputBitmap = Bitmap.createBitmap(
            outputWidth, outputHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(outputBitmap)

        val tilesX = (image.width + tileSize - TILE_OVERLAP - 1) / (tileSize - TILE_OVERLAP)
        val tilesY = (image.height + tileSize - TILE_OVERLAP - 1) / (tileSize - TILE_OVERLAP)
        val totalTiles = tilesX * tilesY
        var tileCount = 0

        for (ty in 0 until tilesY) {
            for (tx in 0 until tilesX) {
                val srcX = (tx * (tileSize - TILE_OVERLAP)).coerceAtMost(image.width - tileSize)
                    .coerceAtLeast(0)
                val srcY = (ty * (tileSize - TILE_OVERLAP)).coerceAtMost(image.height - tileSize)
                    .coerceAtLeast(0)

                val tileWidth = tileSize.coerceAtMost(image.width - srcX)
                val tileHeight = tileSize.coerceAtMost(image.height - srcY)

                val tile = Bitmap.createBitmap(image, srcX, srcY, tileWidth, tileHeight)
                val paddedTile = if (tileWidth < tileSize || tileHeight < tileSize) {
                    Bitmap.createScaledBitmap(tile, tileSize, tileSize, true)
                } else {
                    tile
                }

                val upscaledTile = upscaleTile(paddedTile, session, outputScale)

                // Place tile in output, accounting for overlap
                val dstX = srcX * outputScale
                val dstY = srcY * outputScale
                canvas.drawBitmap(
                    upscaledTile,
                    dstX.toFloat(),
                    dstY.toFloat(),
                    null,
                )

                tileCount++
                _state.value = PipelineState.Upscaling(tileCount.toFloat() / totalTiles)
            }
        }

        return outputBitmap
    }

    /**
     * XLSR upscaling — single pass for any size (model is tiny).
     */
    private suspend fun upscaleWithXlsr(
        image: Bitmap,
        scaleFactor: Int,
        session: ai.onnxruntime.OrtSession,
    ): Bitmap {
        _state.value = PipelineState.Upscaling(0.5f)
        return upscaleTile(image, session, scaleFactor)
    }

    /**
     * Runs upscaling inference on a single tile/image.
     */
    private suspend fun upscaleTile(
        tile: Bitmap,
        session: ai.onnxruntime.OrtSession,
        scaleFactor: Int,
    ): Bitmap {
        val inputTensor = TensorUtils.bitmapToTensor(tile)
        val inputOnnx = engine.createTensor(
            inputTensor,
            longArrayOf(1, 3, tile.height.toLong(), tile.width.toLong()),
        )

        val output = engine.runInference(
            session,
            mapOf("input" to inputOnnx),
        ).values.first()
        inputOnnx.close()

        val outWidth = tile.width * scaleFactor
        val outHeight = tile.height * scaleFactor
        return TensorUtils.tensorToBitmap(output, outWidth, outHeight)
    }
}
