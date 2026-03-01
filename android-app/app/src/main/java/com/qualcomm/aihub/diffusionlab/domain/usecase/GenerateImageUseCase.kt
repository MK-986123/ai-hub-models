package com.qualcomm.aihub.diffusionlab.domain.usecase

import android.graphics.Bitmap
import com.qualcomm.aihub.diffusionlab.data.local.GenerationDao
import com.qualcomm.aihub.diffusionlab.data.models.GenerationEntity
import com.qualcomm.aihub.diffusionlab.data.repository.ImageRepository
import com.qualcomm.aihub.diffusionlab.domain.model.GenerationParams
import com.qualcomm.aihub.diffusionlab.engine.pipeline.ClipTokenizer
import com.qualcomm.aihub.diffusionlab.engine.pipeline.StableDiffusionPipeline
import javax.inject.Inject

/**
 * Orchestrates the full text-to-image generation flow:
 * 1. Runs the SD pipeline
 * 2. Saves result to gallery
 * 3. Records metadata in Room database
 */
class GenerateImageUseCase @Inject constructor(
    private val sdPipeline: StableDiffusionPipeline,
    private val imageRepository: ImageRepository,
    private val generationDao: GenerationDao,
    private val tokenizer: ClipTokenizer,
) {
    suspend operator fun invoke(params: GenerationParams): Result<Bitmap> {
        return try {
            val startTime = System.currentTimeMillis()
            val bitmap = sdPipeline.generateImage(params, tokenizer)
            val duration = System.currentTimeMillis() - startTime

            val imagePath = imageRepository.saveToGallery(bitmap, "gen")

            generationDao.insertGeneration(
                GenerationEntity(
                    prompt = params.prompt,
                    imagePath = imagePath,
                    numSteps = params.numSteps,
                    guidanceScale = params.guidanceScale,
                    seed = params.seed,
                    modelId = "stable_diffusion_v1_5",
                    durationMs = duration,
                )
            )

            Result.success(bitmap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
