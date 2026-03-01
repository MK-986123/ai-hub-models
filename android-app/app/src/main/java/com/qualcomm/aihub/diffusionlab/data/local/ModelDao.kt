package com.qualcomm.aihub.diffusionlab.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qualcomm.aihub.diffusionlab.data.models.GenerationEntity
import com.qualcomm.aihub.diffusionlab.data.models.ModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelEntity)

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun getModel(id: String): ModelEntity?

    @Query("SELECT * FROM models")
    fun getAllModels(): Flow<List<ModelEntity>>

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun deleteModel(id: String)
}

@Dao
interface GenerationDao {
    @Insert
    suspend fun insertGeneration(generation: GenerationEntity): Long

    @Query("SELECT * FROM generations ORDER BY createdAt DESC")
    fun getAllGenerations(): Flow<List<GenerationEntity>>

    @Query("SELECT * FROM generations WHERE id = :id")
    suspend fun getGeneration(id: Long): GenerationEntity?

    @Query("DELETE FROM generations WHERE id = :id")
    suspend fun deleteGeneration(id: Long)
}
