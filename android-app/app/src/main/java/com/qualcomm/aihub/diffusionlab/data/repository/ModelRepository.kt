package com.qualcomm.aihub.diffusionlab.data.repository

import android.content.Context
import android.util.Log
import com.qualcomm.aihub.diffusionlab.domain.model.ModelInfo
import com.qualcomm.aihub.diffusionlab.domain.model.ModelRole
import com.qualcomm.aihub.diffusionlab.domain.model.ModelState
import com.qualcomm.aihub.diffusionlab.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages model lifecycle: downloading, caching, sideloading, and discovery.
 *
 * Models are stored in the app's internal files directory:
 *   /data/data/<pkg>/files/models/<model_file>
 *
 * Sideloaded models go into:
 *   /sdcard/Android/data/<pkg>/files/models/sideloaded/<model_file>
 */
@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "ModelRepository"
    }

    private val modelsDir: File
        get() = File(context.filesDir, Constants.MODELS_DIR).also { it.mkdirs() }

    private val sideloadDir: File
        get() = context.getExternalFilesDir(Constants.SIDELOAD_DIR)?.also { it.mkdirs() }
            ?: File(context.filesDir, Constants.SIDELOAD_DIR).also { it.mkdirs() }

    private val _modelStates = MutableStateFlow<Map<String, ModelState>>(emptyMap())
    val modelStates: StateFlow<Map<String, ModelState>> = _modelStates

    /**
     * Returns the complete model registry: all models the app knows about.
     */
    fun getModelRegistry(): List<ModelInfo> = listOf(
        // -- Stable Diffusion v1.5 Core Pipeline --
        ModelInfo(
            id = "text_encoder",
            displayName = "CLIP Text Encoder",
            role = ModelRole.TEXT_ENCODER,
            fileName = Constants.TEXT_ENCODER_MODEL,
            downloadUrl = "${Constants.MODEL_BASE_URL}${Constants.TEXT_ENCODER_MODEL}",
            fileSizeBytes = 170L * 1024 * 1024,
            description = "Converts text prompts to embeddings. Required for generation.",
            isPremium = false,
            isRequired = true,
        ),
        ModelInfo(
            id = "unet",
            displayName = "U-Net Denoiser",
            role = ModelRole.UNET,
            fileName = Constants.UNET_MODEL,
            downloadUrl = "${Constants.MODEL_BASE_URL}${Constants.UNET_MODEL}",
            fileSizeBytes = 450L * 1024 * 1024,
            description = "Core denoising network (865M params, w8a16 quantized).",
            isPremium = false,
            isRequired = true,
        ),
        ModelInfo(
            id = "vae_decoder",
            displayName = "VAE Decoder",
            role = ModelRole.VAE_DECODER,
            fileName = Constants.VAE_DECODER_MODEL,
            downloadUrl = "${Constants.MODEL_BASE_URL}${Constants.VAE_DECODER_MODEL}",
            fileSizeBytes = 45L * 1024 * 1024,
            description = "Decodes latent space to RGB image.",
            isPremium = false,
            isRequired = true,
        ),
        // -- Upscaling --
        ModelInfo(
            id = "real_esrgan",
            displayName = "Real-ESRGAN 4x",
            role = ModelRole.UPSCALER,
            fileName = Constants.REAL_ESRGAN_MODEL,
            downloadUrl = "${Constants.MODEL_BASE_URL}${Constants.REAL_ESRGAN_MODEL}",
            fileSizeBytes = 17L * 1024 * 1024,
            description = "High-quality 4x upscaling (16.7M params, w8a8). ~25ms on NPU.",
            isPremium = true,
            isRequired = false,
        ),
        ModelInfo(
            id = "xlsr",
            displayName = "XLSR (Fast Upscale)",
            role = ModelRole.UPSCALER,
            fileName = Constants.XLSR_MODEL,
            downloadUrl = "${Constants.MODEL_BASE_URL}${Constants.XLSR_MODEL}",
            fileSizeBytes = 115L * 1024,
            description = "Ultra-lightweight upscaler (28K params, 115KB). Real-time.",
            isPremium = false,
            isRequired = false,
        ),
        // -- Inpainting --
        ModelInfo(
            id = "lama",
            displayName = "LaMa Inpainter",
            role = ModelRole.INPAINTER,
            fileName = Constants.LAMA_MODEL,
            downloadUrl = "${Constants.MODEL_BASE_URL}${Constants.LAMA_MODEL}",
            fileSizeBytes = 174L * 1024 * 1024,
            description = "High-quality inpainting with Fourier convolutions (45.6M params).",
            isPremium = true,
            isRequired = false,
        ),
        ModelInfo(
            id = "aotgan",
            displayName = "AOT-GAN (Fast Inpaint)",
            role = ModelRole.INPAINTER,
            fileName = Constants.AOTGAN_MODEL,
            downloadUrl = "${Constants.MODEL_BASE_URL}${Constants.AOTGAN_MODEL}",
            fileSizeBytes = 58L * 1024 * 1024,
            description = "Fast inpainting (15.2M params). Good for quick edits.",
            isPremium = true,
            isRequired = false,
        ),
        // -- Segmentation (auto-masking) --
        ModelInfo(
            id = "fastsam",
            displayName = "FastSAM-S",
            role = ModelRole.SEGMENTER,
            fileName = Constants.FASTSAM_MODEL,
            downloadUrl = "${Constants.MODEL_BASE_URL}${Constants.FASTSAM_MODEL}",
            fileSizeBytes = 45L * 1024 * 1024,
            description = "Fast segment-anything for auto-masking (11.8M params). ~10-15ms.",
            isPremium = true,
            isRequired = false,
        ),
    )

    /**
     * Returns the local file path for a model. Null if not downloaded.
     */
    fun getModelFile(modelId: String): File? {
        val info = getModelRegistry().find { it.id == modelId } ?: return null
        val file = File(modelsDir, info.fileName)
        if (file.exists() && file.length() > 0) return file

        // Check sideloaded directory
        val sideloaded = File(sideloadDir, info.fileName)
        if (sideloaded.exists() && sideloaded.length() > 0) return sideloaded

        return null
    }

    /**
     * Checks if all required (free-tier) models are downloaded.
     */
    fun areRequiredModelsReady(): Boolean {
        return getModelRegistry()
            .filter { it.isRequired }
            .all { getModelFile(it.id) != null }
    }

    /**
     * Downloads a model file with progress reporting.
     */
    suspend fun downloadModel(modelId: String) = withContext(Dispatchers.IO) {
        val info = getModelRegistry().find { it.id == modelId }
            ?: throw IllegalArgumentException("Unknown model: $modelId")

        val targetFile = File(modelsDir, info.fileName)
        if (targetFile.exists() && targetFile.length() == info.fileSizeBytes) {
            updateState(modelId, ModelState.Ready)
            return@withContext
        }

        updateState(modelId, ModelState.Downloading(0f))

        try {
            val request = Request.Builder().url(info.downloadUrl).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: info.fileSizeBytes

            // Write to temp file first, then rename for atomicity
            val tempFile = File(modelsDir, "${info.fileName}.tmp")
            FileOutputStream(tempFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0
                    var len: Int

                    while (input.read(buffer).also { len = it } != -1) {
                        output.write(buffer, 0, len)
                        bytesRead += len
                        val progress = bytesRead.toFloat() / totalBytes
                        updateState(modelId, ModelState.Downloading(progress))
                    }
                }
            }

            tempFile.renameTo(targetFile)
            updateState(modelId, ModelState.Ready)
            Log.i(TAG, "Downloaded $modelId: ${targetFile.length()} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download $modelId", e)
            updateState(modelId, ModelState.Error(e.message ?: "Download failed"))
            throw e
        }
    }

    /**
     * Scans the sideload directory for user-provided models.
     * Returns list of file names found.
     */
    fun discoverSideloadedModels(): List<File> {
        return sideloadDir.listFiles()
            ?.filter { it.extension in listOf("onnx", "bin", "tflite") }
            ?.toList()
            ?: emptyList()
    }

    /**
     * Refreshes the state of all models based on file presence.
     */
    fun refreshStates() {
        val states = mutableMapOf<String, ModelState>()
        for (model in getModelRegistry()) {
            states[model.id] = when {
                getModelFile(model.id) != null -> ModelState.Ready
                else -> ModelState.NotDownloaded
            }
        }
        // Add sideloaded models
        for (file in discoverSideloadedModels()) {
            states["sideload_${file.nameWithoutExtension}"] = ModelState.Sideloaded
        }
        _modelStates.value = states
    }

    private fun updateState(modelId: String, state: ModelState) {
        _modelStates.value = _modelStates.value.toMutableMap().apply {
            put(modelId, state)
        }
    }
}
