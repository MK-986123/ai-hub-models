package com.qualcomm.aihub.diffusionlab.domain.usecase

import android.graphics.Bitmap
import com.qualcomm.aihub.diffusionlab.data.repository.ImageRepository
import com.qualcomm.aihub.diffusionlab.domain.model.InpaintParams
import com.qualcomm.aihub.diffusionlab.engine.pipeline.InpaintPipeline
import javax.inject.Inject

/**
 * Orchestrates inpainting:
 * 1. Processes the mask with feather/edge settings
 * 2. Runs LaMa or AOT-GAN inpainting
 * 3. Saves result to gallery
 */
class InpaintImageUseCase @Inject constructor(
    private val inpaintPipeline: InpaintPipeline,
    private val imageRepository: ImageRepository,
) {
    suspend operator fun invoke(
        image: Bitmap,
        mask: Bitmap,
        params: InpaintParams,
    ): Result<Bitmap> {
        return try {
            val result = inpaintPipeline.inpaint(image, mask, params)
            imageRepository.saveToGallery(result, "inpaint")
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
