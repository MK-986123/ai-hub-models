package com.qualcomm.aihub.diffusionlab.domain.model

import com.qualcomm.aihub.diffusionlab.util.Constants

/**
 * Parameters for text-to-image generation.
 */
data class GenerationParams(
    val prompt: String,
    val negativePrompt: String = "",
    val numSteps: Int = Constants.DEFAULT_NUM_STEPS,
    val guidanceScale: Float = Constants.DEFAULT_GUIDANCE_SCALE,
    val seed: Long = Constants.DEFAULT_SEED,
    val width: Int = Constants.SD_OUTPUT_WIDTH,
    val height: Int = Constants.SD_OUTPUT_HEIGHT,
)

/**
 * Parameters for inpainting/outpainting.
 */
data class InpaintParams(
    val prompt: String,
    val brushSizePx: Int = 24,
    val featherPercent: Float = 0f,
    val edgeMode: EdgeMode = EdgeMode.SHARP,
    val denoiseStrength: Float = 0.3f,
    val useQuickMode: Boolean = false,
)

enum class EdgeMode(val displayName: String) {
    SHARP("Sharp"),
    SMOOTH("Smooth"),
    SOFT("Soft"),
}

/**
 * Parameters for upscaling.
 */
data class UpscaleParams(
    val scaleFactor: Int = 4,
    val useRealEsrgan: Boolean = true,
)

/**
 * Overall pipeline execution state, observable by the UI.
 */
sealed interface PipelineState {
    data object Idle : PipelineState
    data class LoadingModel(val componentName: String) : PipelineState
    data class Generating(
        val currentStep: Int,
        val totalSteps: Int,
        val componentName: String,
    ) : PipelineState
    data class Upscaling(val progress: Float) : PipelineState
    data class Inpainting(val progress: Float) : PipelineState
    data class Done(val resultPath: String) : PipelineState
    data class Error(val message: String) : PipelineState
}
