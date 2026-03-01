package com.qualcomm.aihub.diffusionlab.engine.inference

import ai.onnxruntime.OrtSession
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages ONNX Runtime session lifecycle with memory-aware loading.
 *
 * Key strategy: Only one "large" session (U-Net, ~450MB) is loaded at a time.
 * Smaller sessions (text encoder, VAE, upscaler) can coexist but are released
 * aggressively after use.
 *
 * This keeps peak RAM under 4GB during Stable Diffusion generation:
 * - Load TextEncoder → run → release (~170MB freed)
 * - Load UNet → run N steps → release (~450MB freed)
 * - Load VAE → run → release (~45MB freed)
 */
@Singleton
class QnnSessionManager @Inject constructor(
    private val engine: OnnxInferenceEngine,
) {
    private val mutex = Mutex()
    private val activeSessions = mutableMapOf<String, OrtSession>()

    companion object {
        private const val TAG = "QnnSessionMgr"

        // Models larger than this threshold trigger release of other large sessions
        private const val LARGE_MODEL_THRESHOLD_MB = 100
    }

    /**
     * Acquires a session for the given model. If the model file is large,
     * releases any other large sessions first to conserve memory.
     */
    suspend fun acquireSession(modelId: String, modelFile: File): OrtSession = mutex.withLock {
        // Return existing session if already loaded
        activeSessions[modelId]?.let { return it }

        val fileSizeMb = modelFile.length() / (1024 * 1024)
        Log.i(TAG, "Loading model: $modelId (${fileSizeMb}MB)")

        // If loading a large model, release other large sessions first
        if (fileSizeMb > LARGE_MODEL_THRESHOLD_MB) {
            val toRelease = activeSessions.keys.toList()
            for (id in toRelease) {
                Log.i(TAG, "Releasing $id to make room for $modelId")
                activeSessions.remove(id)?.let { engine.releaseSession(it) }
            }
        }

        val session = engine.createSession(modelFile)
        activeSessions[modelId] = session
        session
    }

    /**
     * Explicitly releases a session when it's no longer needed.
     */
    suspend fun releaseSession(modelId: String) = mutex.withLock {
        activeSessions.remove(modelId)?.let { session ->
            Log.i(TAG, "Releasing session: $modelId")
            engine.releaseSession(session)
        }
    }

    /**
     * Releases all active sessions. Called during app background or memory pressure.
     */
    suspend fun releaseAll() = mutex.withLock {
        Log.i(TAG, "Releasing all ${activeSessions.size} sessions")
        for ((id, session) in activeSessions) {
            engine.releaseSession(session)
        }
        activeSessions.clear()
    }

    /**
     * Returns whether a session is currently loaded.
     */
    fun isLoaded(modelId: String): Boolean = activeSessions.containsKey(modelId)
}
