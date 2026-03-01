package com.qualcomm.aihub.diffusionlab.engine.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core ONNX Runtime inference engine with QNN Execution Provider support.
 *
 * Manages ONNX sessions for all model components, using Qualcomm's QNN EP
 * for NPU-accelerated inference on Snapdragon chipsets.
 *
 * Memory strategy: Sessions are created and destroyed per-component during
 * the SD pipeline to keep peak RAM under 4 GB. Only one large model session
 * is active at a time.
 */
@Singleton
class OnnxInferenceEngine @Inject constructor() {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    companion object {
        private const val TAG = "OnnxEngine"
    }

    /**
     * Creates an ONNX Runtime session with QNN EP for NPU acceleration.
     * Falls back to CPU EP if QNN is unavailable (non-Snapdragon devices).
     */
    fun createSession(modelPath: File): OrtSession {
        val options = OrtSession.SessionOptions().apply {
            // Attempt QNN EP for Qualcomm NPU acceleration
            try {
                val qnnOptions = mapOf(
                    "backend_path" to "libQnnHtp.so",
                    "enable_htp_fp16_precision" to "1",
                )
                addQnn(qnnOptions)
                Log.i(TAG, "QNN Execution Provider enabled for: ${modelPath.name}")
            } catch (e: Exception) {
                Log.w(TAG, "QNN EP unavailable, falling back to CPU: ${e.message}")
                // CPU fallback — still functional, just slower
            }

            // Optimize for sequential execution (one model at a time)
            setIntraOpNumThreads(4)
            setInterOpNumThreads(1)

            // Memory optimization: use memory-mapped model loading
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }

        return env.createSession(modelPath.absolutePath, options)
    }

    /**
     * Runs inference on a single session with named inputs.
     * Returns output tensors as a map of name → float array.
     */
    suspend fun runInference(
        session: OrtSession,
        inputs: Map<String, OnnxTensor>,
    ): Map<String, FloatArray> = withContext(Dispatchers.Default) {
        val results = session.run(inputs)
        val outputMap = mutableMapOf<String, FloatArray>()

        for ((name, value) in results) {
            val tensor = value as? OnnxTensor ?: continue
            val buffer = tensor.floatBuffer
            val array = FloatArray(buffer.remaining())
            buffer.get(array)
            outputMap[name] = array
            tensor.close()
        }

        results.close()
        outputMap
    }

    /**
     * Creates an OnnxTensor from a float array with given shape.
     */
    fun createTensor(data: FloatArray, shape: LongArray): OnnxTensor {
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)
    }

    /**
     * Creates an OnnxTensor from an int array with given shape.
     * Used for tokenized text inputs (CLIP text encoder).
     */
    fun createIntTensor(data: IntArray, shape: LongArray): OnnxTensor {
        return OnnxTensor.createTensor(env, java.nio.IntBuffer.wrap(data), shape)
    }

    /**
     * Releases a session and frees its memory.
     * Critical for the sequential-loading memory strategy.
     */
    fun releaseSession(session: OrtSession) {
        try {
            session.close()
            // Suggest GC after releasing a large model
            System.gc()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing session: ${e.message}")
        }
    }

    fun close() {
        env.close()
    }
}
