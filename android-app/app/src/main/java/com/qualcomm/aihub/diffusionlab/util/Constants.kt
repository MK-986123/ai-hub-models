package com.qualcomm.aihub.diffusionlab.util

object Constants {
    // Stable Diffusion v1.5 output resolution
    const val SD_OUTPUT_WIDTH = 512
    const val SD_OUTPUT_HEIGHT = 512
    const val SD_LATENT_CHANNELS = 4
    const val SD_LATENT_SIZE = 64 // 512 / 8

    // CLIP tokenizer
    const val CLIP_MAX_LENGTH = 77

    // Default generation params
    const val DEFAULT_NUM_STEPS = 20
    const val DEFAULT_GUIDANCE_SCALE = 7.5f
    const val DEFAULT_SEED = 0L
    const val MAX_STEPS_FREE = 5
    const val MAX_STEPS_PREMIUM = 50

    // Model file names (as downloaded from model registry)
    const val TEXT_ENCODER_MODEL = "text_encoder.onnx"
    const val UNET_MODEL = "unet.onnx"
    const val VAE_DECODER_MODEL = "vae_decoder.onnx"
    const val REAL_ESRGAN_MODEL = "real_esrgan_x4plus.onnx"
    const val XLSR_MODEL = "xlsr.onnx"
    const val LAMA_MODEL = "lama_dilated.onnx"
    const val AOTGAN_MODEL = "aotgan.onnx"
    const val FASTSAM_MODEL = "fastsam_s.onnx"

    // Model download base URL (replace with actual hosting)
    const val MODEL_BASE_URL = "https://github.com/quic/ai-hub-models/releases/download/v1.0.0/"

    // Storage paths
    const val MODELS_DIR = "models"
    const val SIDELOAD_DIR = "sideloaded"
    const val GALLERY_DIR = "gallery"
    const val CACHE_DIR = "cache"

    // Free tier limits
    const val FREE_GENERATIONS_PER_MINUTE = 1
    const val FREE_UPSCALES_PER_DAY = 1

    // VAE tiling for memory optimization
    const val VAE_TILE_SIZE = 256
    const val VAE_TILE_OVERLAP = 32
}
