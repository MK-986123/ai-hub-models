package com.qualcomm.aihub.diffusionlab.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity storing generation history for the gallery.
 */
@Entity(tableName = "generations")
data class GenerationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val imagePath: String,
    val numSteps: Int,
    val guidanceScale: Float,
    val seed: Long,
    val modelId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
)
