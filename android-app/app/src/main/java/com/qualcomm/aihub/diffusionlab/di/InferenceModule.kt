package com.qualcomm.aihub.diffusionlab.di

import android.content.Context
import androidx.room.Room
import com.qualcomm.aihub.diffusionlab.data.local.AppDatabase
import com.qualcomm.aihub.diffusionlab.data.local.GenerationDao
import com.qualcomm.aihub.diffusionlab.data.local.ModelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "diffusionlab.db",
        ).build()
    }

    @Provides
    fun provideModelDao(database: AppDatabase): ModelDao = database.modelDao()

    @Provides
    fun provideGenerationDao(database: AppDatabase): GenerationDao = database.generationDao()
}
