package com.qualcomm.aihub.diffusionlab.domain.usecase

import android.graphics.Bitmap
import com.qualcomm.aihub.diffusionlab.data.repository.ImageRepository
import com.qualcomm.aihub.diffusionlab.domain.model.UpscaleParams
import com.qualcomm.aihub.diffusionlab.engine.pipeline.UpscalePipeline
import javax.inject.Inject

/**
 * Orchestrates image upscaling:
 * 1. Runs Real-ESRGAN (premium) or XLSR (free) upscaler
 * 2. Saves result to gallery
 */
class UpscaleImageUseCase @Inject constructor(
    private val upscalePipeline: UpscalePipeline,
    private val imageRepository: ImageRepository,
) {
    suspend operator fun invoke(
        image: Bitmap,
        params: UpscaleParams,
    ): Result<Bitmap> {
        return try {
            val result = upscalePipeline.upscale(image, params)
            imageRepository.saveToGallery(result, "upscale")
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
