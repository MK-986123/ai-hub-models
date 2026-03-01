package com.qualcomm.aihub.diffusionlab.engine.pipeline

import android.graphics.Bitmap
import android.util.Log
import com.qualcomm.aihub.diffusionlab.data.repository.ModelRepository
import com.qualcomm.aihub.diffusionlab.domain.model.GenerationParams
import com.qualcomm.aihub.diffusionlab.domain.model.PipelineState
import com.qualcomm.aihub.diffusionlab.engine.inference.OnnxInferenceEngine
import com.qualcomm.aihub.diffusionlab.engine.inference.QnnSessionManager
import com.qualcomm.aihub.diffusionlab.engine.inference.TensorUtils
import com.qualcomm.aihub.diffusionlab.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Complete Stable Diffusion v1.5 text-to-image pipeline running on-device.
 *
 * Architecture mirrors the Python reference in:
 *   qai_hub_models/models/_shared/stable_diffusion/app.py (StableDiffusionApp)
 *
 * Memory strategy:
 *   Components are loaded sequentially (text encoder → U-Net → VAE decoder).
 *   Only one large model is in memory at a time, keeping peak RAM < 4GB.
 *
 * Pipeline:
 *   1. Tokenize prompt → CLIP token IDs [1, 77]
 *   2. Text Encoder: token IDs → text embeddings [1, 77, 768]
 *   3. Initialize random latent noise [1, 4, 64, 64]
 *   4. U-Net denoising loop (N steps):
 *      - Predict noise conditioned on text embeddings
 *      - Apply classifier-free guidance
 *      - Scheduler step (PNDM)
 *   5. VAE Decoder: latent → RGB image [1, 3, 512, 512]
 */
@Singleton
class StableDiffusionPipeline @Inject constructor(
    private val engine: OnnxInferenceEngine,
    private val sessionManager: QnnSessionManager,
    private val modelRepository: ModelRepository,
) {
    companion object {
        private const val TAG = "SDPipeline"
    }

    private val _state = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val state: StateFlow<PipelineState> = _state

    private val scheduler = PndmScheduler()

    /**
     * Generates an image from a text prompt using Stable Diffusion v1.5.
     *
     * @param params Generation parameters (prompt, steps, guidance, seed)
     * @param tokenizer Pre-initialized CLIP tokenizer
     * @return Generated bitmap (512×512 RGB)
     */
    suspend fun generateImage(
        params: GenerationParams,
        tokenizer: ClipTokenizer,
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            // --- Step 1: Tokenize ---
            Log.i(TAG, "Tokenizing prompt: ${params.prompt}")
            val condTokens = tokenizer.encode(params.prompt)
            val uncondTokens = tokenizer.encodeEmpty()

            // --- Step 2: Text Encoding ---
            _state.value = PipelineState.LoadingModel("Text Encoder")
            val textEncoderFile = modelRepository.getModelFile("text_encoder")
                ?: throw IllegalStateException("Text encoder model not downloaded")

            val textSession = sessionManager.acquireSession("text_encoder", textEncoderFile)

            Log.i(TAG, "Running text encoder (conditional)")
            val condInput = engine.createIntTensor(condTokens, longArrayOf(1, 77))
            val condEmbedding = engine.runInference(
                textSession,
                mapOf("input_ids" to condInput),
            ).values.first()
            condInput.close()

            Log.i(TAG, "Running text encoder (unconditional)")
            val uncondInput = engine.createIntTensor(uncondTokens, longArrayOf(1, 77))
            val uncondEmbedding = engine.runInference(
                textSession,
                mapOf("input_ids" to uncondInput),
            ).values.first()
            uncondInput.close()

            // Release text encoder to free memory before loading U-Net
            sessionManager.releaseSession("text_encoder")
            Log.i(TAG, "Text encoder released, embeddings captured")

            // --- Step 3: Initialize latents ---
            val latentSize = Constants.SD_LATENT_CHANNELS *
                Constants.SD_LATENT_SIZE * Constants.SD_LATENT_SIZE
            val random = Random(params.seed)
            var latents = FloatArray(latentSize) {
                (random.nextGaussian() * scheduler.initNoiseSigma).toFloat()
            }

            // --- Step 4: U-Net denoising loop ---
            _state.value = PipelineState.LoadingModel("U-Net")
            val unetFile = modelRepository.getModelFile("unet")
                ?: throw IllegalStateException("U-Net model not downloaded")

            val unetSession = sessionManager.acquireSession("unet", unetFile)

            scheduler.setTimesteps(params.numSteps)

            for ((stepIdx, timestep) in scheduler.timesteps.withIndex()) {
                _state.value = PipelineState.Generating(
                    currentStep = stepIdx + 1,
                    totalSteps = params.numSteps,
                    componentName = "U-Net",
                )
                Log.i(TAG, "Step ${stepIdx + 1}/${params.numSteps}, timestep=$timestep")

                val latentInput = scheduler.scaleModelInput(latents, timestep)
                val timeInput = floatArrayOf(timestep.toFloat())

                val latentShape = longArrayOf(
                    1L,
                    Constants.SD_LATENT_CHANNELS.toLong(),
                    Constants.SD_LATENT_SIZE.toLong(),
                    Constants.SD_LATENT_SIZE.toLong(),
                )
                val embeddingShape = longArrayOf(1L, 77L, 768L)

                // Conditional noise prediction
                val condLatentTensor = engine.createTensor(latentInput, latentShape)
                val condEmbTensor = engine.createTensor(condEmbedding, embeddingShape)
                val timeTensor = engine.createTensor(timeInput, longArrayOf(1, 1))

                val condNoise = engine.runInference(
                    unetSession,
                    mapOf(
                        "latent" to condLatentTensor,
                        "timestep" to timeTensor,
                        "text_emb" to condEmbTensor,
                    ),
                ).values.first()

                condLatentTensor.close()
                condEmbTensor.close()

                if (params.guidanceScale != 0f) {
                    // Unconditional noise prediction
                    val uncondLatentTensor = engine.createTensor(latentInput, latentShape)
                    val uncondEmbTensor = engine.createTensor(uncondEmbedding, embeddingShape)
                    val timeTensor2 = engine.createTensor(timeInput, longArrayOf(1, 1))

                    val uncondNoise = engine.runInference(
                        unetSession,
                        mapOf(
                            "latent" to uncondLatentTensor,
                            "timestep" to timeTensor2,
                            "text_emb" to uncondEmbTensor,
                        ),
                    ).values.first()

                    uncondLatentTensor.close()
                    uncondEmbTensor.close()
                    timeTensor2.close()

                    // Classifier-free guidance: noise = uncond + scale * (cond - uncond)
                    val guidedNoise = FloatArray(condNoise.size) { i ->
                        uncondNoise[i] + params.guidanceScale * (condNoise[i] - uncondNoise[i])
                    }

                    latents = scheduler.step(guidedNoise, timestep, latents)
                } else {
                    latents = scheduler.step(condNoise, timestep, latents)
                }

                timeTensor.close()
            }

            // Release U-Net to free memory before VAE
            sessionManager.releaseSession("unet")
            Log.i(TAG, "U-Net released, latents ready for decoding")

            // --- Step 5: VAE Decoding ---
            _state.value = PipelineState.LoadingModel("VAE Decoder")
            val vaeFile = modelRepository.getModelFile("vae_decoder")
                ?: throw IllegalStateException("VAE decoder model not downloaded")

            val vaeSession = sessionManager.acquireSession("vae_decoder", vaeFile)

            val latentTensor = engine.createTensor(
                latents,
                longArrayOf(
                    1L,
                    Constants.SD_LATENT_CHANNELS.toLong(),
                    Constants.SD_LATENT_SIZE.toLong(),
                    Constants.SD_LATENT_SIZE.toLong(),
                ),
            )

            Log.i(TAG, "Running VAE decoder")
            val imageOutput = engine.runInference(
                vaeSession,
                mapOf("latent" to latentTensor),
            ).values.first()

            latentTensor.close()
            sessionManager.releaseSession("vae_decoder")

            // Convert to bitmap
            val bitmap = TensorUtils.vaeOutputToBitmap(imageOutput)

            _state.value = PipelineState.Idle
            Log.i(TAG, "Generation complete: ${bitmap.width}x${bitmap.height}")

            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            _state.value = PipelineState.Error(e.message ?: "Generation failed")
            throw e
        }
    }
}
