package com.qualcomm.aihub.diffusionlab.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.qualcomm.aihub.diffusionlab.data.models.GenerationEntity
import com.qualcomm.aihub.diffusionlab.data.models.ModelEntity

@Database(
    entities = [ModelEntity::class, GenerationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun generationDao(): GenerationDao
}
