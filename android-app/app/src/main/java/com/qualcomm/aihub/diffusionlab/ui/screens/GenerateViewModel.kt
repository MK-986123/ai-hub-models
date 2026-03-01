package com.qualcomm.aihub.diffusionlab.ui.screens

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qualcomm.aihub.diffusionlab.data.repository.ImageRepository
import com.qualcomm.aihub.diffusionlab.domain.model.GenerationParams
import com.qualcomm.aihub.diffusionlab.domain.model.PipelineState
import com.qualcomm.aihub.diffusionlab.engine.pipeline.ClipTokenizer
import com.qualcomm.aihub.diffusionlab.engine.pipeline.StableDiffusionPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val sdPipeline: StableDiffusionPipeline,
    private val imageRepository: ImageRepository,
    private val tokenizer: ClipTokenizer,
) : ViewModel() {

    val pipelineState: StateFlow<PipelineState> = sdPipeline.state

    private val _generatedImage = MutableStateFlow<Bitmap?>(null)
    val generatedImage: StateFlow<Bitmap?> = _generatedImage

    fun generate(
        prompt: String,
        numSteps: Int,
        guidanceScale: Float,
        seed: Long,
    ) {
        viewModelScope.launch {
            val params = GenerationParams(
                prompt = prompt,
                numSteps = numSteps,
                guidanceScale = guidanceScale,
                seed = seed,
            )

            val bitmap = sdPipeline.generateImage(params, tokenizer)
            _generatedImage.value = bitmap

            // Auto-save to gallery
            imageRepository.saveToGallery(bitmap, "gen")
        }
    }
}
