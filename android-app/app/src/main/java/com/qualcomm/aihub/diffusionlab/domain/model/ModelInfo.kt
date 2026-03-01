package com.qualcomm.aihub.diffusionlab.domain.model

/**
 * Represents a downloadable/sideloadable AI model.
 */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val role: ModelRole,
    val fileName: String,
    val downloadUrl: String,
    val fileSizeBytes: Long,
    val description: String,
    val isPremium: Boolean,
    val isRequired: Boolean,
)

/**
 * The role a model plays in the inference pipeline.
 * Used to validate sideloaded models against expected input/output shapes.
 */
enum class ModelRole(val displayName: String) {
    TEXT_ENCODER("Text Encoder"),
    UNET("U-Net Denoiser"),
    VAE_DECODER("VAE Decoder"),
    UPSCALER("Upscaler"),
    INPAINTER("Inpainter"),
    SEGMENTER("Segmenter"),
    CONTROLNET("ControlNet"),
}

/**
 * Download/availability state for a model.
 */
sealed interface ModelState {
    data object NotDownloaded : ModelState
    data class Downloading(val progress: Float) : ModelState
    data object Ready : ModelState
    data class Error(val message: String) : ModelState
    data object Sideloaded : ModelState
}
