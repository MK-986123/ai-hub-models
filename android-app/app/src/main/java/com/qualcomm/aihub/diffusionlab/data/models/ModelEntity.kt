package com.qualcomm.aihub.diffusionlab.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity tracking model download and sideload state.
 */
@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val filePath: String?,
    val fileSizeBytes: Long,
    val downloadedAt: Long? = null,
    val isSideloaded: Boolean = false,
)
