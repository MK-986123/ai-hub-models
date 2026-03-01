package com.qualcomm.aihub.diffusionlab.engine.pipeline

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Kotlin port of the PNDMScheduler from HuggingFace diffusers.
 * This is the default scheduler used with Stable Diffusion v1.5.
 *
 * The scheduler controls the denoising process by determining the noise schedule
 * and computing the previous latent sample at each step.
 *
 * Reference: qai_hub_models/models/_shared/stable_diffusion/app.py
 */
class PndmScheduler(
    private val numTrainTimesteps: Int = 1000,
    private val betaStart: Float = 0.00085f,
    private val betaEnd: Float = 0.012f,
) {
    private val betas: FloatArray
    private val alphas: FloatArray
    private val alphasCumprod: FloatArray

    var timesteps: IntArray = intArrayOf()
        private set

    private var numInferenceSteps: Int = 0
    private val ets = mutableListOf<FloatArray>()
    private var counter = 0

    // Initial noise sigma for scaling latents
    val initNoiseSigma: Float = 1.0f

    init {
        // Scaled linear beta schedule (same as diffusers default for SD v1.5)
        betas = FloatArray(numTrainTimesteps) { i ->
            val t = i.toFloat() / (numTrainTimesteps - 1)
            val betaSqrt = sqrt(betaStart) + t * (sqrt(betaEnd) - sqrt(betaStart))
            betaSqrt * betaSqrt
        }

        alphas = FloatArray(numTrainTimesteps) { 1f - betas[it] }

        alphasCumprod = FloatArray(numTrainTimesteps)
        alphasCumprod[0] = alphas[0]
        for (i in 1 until numTrainTimesteps) {
            alphasCumprod[i] = alphasCumprod[i - 1] * alphas[i]
        }
    }

    /**
     * Sets the number of inference steps and computes the timestep schedule.
     */
    fun setTimesteps(numSteps: Int) {
        numInferenceSteps = numSteps
        ets.clear()
        counter = 0

        // Linspace from numTrainTimesteps-1 to 0
        val stepRatio = numTrainTimesteps / numSteps
        timesteps = IntArray(numSteps) { i ->
            ((numTrainTimesteps - 1) - i * stepRatio).coerceIn(0, numTrainTimesteps - 1)
        }
    }

    /**
     * Scales the initial random latents by init_noise_sigma.
     */
    fun scaleModelInput(sample: FloatArray, @Suppress("UNUSED_PARAMETER") timestep: Int): FloatArray {
        // PNDM doesn't scale inputs per-step (unlike DDIM)
        return sample
    }

    /**
     * Computes the previous noisy sample given the model output (noise prediction).
     *
     * Uses PNDM (Pseudo Numerical methods for Diffusion Models) multi-step approach:
     * - First 3 steps: single-step Euler-like update
     * - Steps 4+: 4th-order linear multi-step method
     */
    fun step(
        modelOutput: FloatArray,
        timestep: Int,
        sample: FloatArray,
    ): FloatArray {
        // Store model outputs for multi-step
        ets.add(modelOutput.copyOf())
        counter++

        val prevTimestep = if (counter < timesteps.size) {
            timesteps[counter]
        } else {
            0
        }

        return if (counter <= 3) {
            // Single step for first iterations
            getPrevSample(sample, timestep, prevTimestep, modelOutput)
        } else {
            // Linear multi-step (4th order)
            val n = ets.size
            val e1 = ets[n - 1]
            val e2 = ets[n - 2]
            val e3 = ets[n - 3]
            val e4 = ets[n - 4]

            // 4th order Adams-Bashforth coefficients
            val combinedOutput = FloatArray(modelOutput.size) { i ->
                (55f * e1[i] - 59f * e2[i] + 37f * e3[i] - 9f * e4[i]) / 24f
            }

            getPrevSample(sample, timestep, prevTimestep, combinedOutput)
        }
    }

    private fun getPrevSample(
        sample: FloatArray,
        timestep: Int,
        prevTimestep: Int,
        modelOutput: FloatArray,
    ): FloatArray {
        val alphaProdT = alphasCumprod[timestep]
        val alphaProdTPrev = if (prevTimestep >= 0) alphasCumprod[prevTimestep] else 1f

        val betaProdT = 1f - alphaProdT
        val betaProdTPrev = 1f - alphaProdTPrev

        val sqrtAlphaProdT = sqrt(alphaProdT)
        val sqrtBetaProdT = sqrt(betaProdT)
        val sqrtAlphaProdTPrev = sqrt(alphaProdTPrev)
        val sqrtBetaProdTPrev = sqrt(betaProdTPrev)

        // Predict x0 from noise
        // x0 = (sample - sqrt(1 - alpha_t) * noise) / sqrt(alpha_t)
        // prev_sample = sqrt(alpha_{t-1}) * x0 + sqrt(1 - alpha_{t-1}) * noise

        return FloatArray(sample.size) { i ->
            val predOriginalSample = (sample[i] - sqrtBetaProdT * modelOutput[i]) / sqrtAlphaProdT
            val predSampleDirection = sqrtBetaProdTPrev * modelOutput[i]
            sqrtAlphaProdTPrev * predOriginalSample + predSampleDirection
        }
    }
}
