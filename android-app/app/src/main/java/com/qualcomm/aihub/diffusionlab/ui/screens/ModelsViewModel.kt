package com.qualcomm.aihub.diffusionlab.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qualcomm.aihub.diffusionlab.data.repository.ModelRepository
import com.qualcomm.aihub.diffusionlab.domain.model.ModelInfo
import com.qualcomm.aihub.diffusionlab.domain.model.ModelState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
) : ViewModel() {

    val modelStates: StateFlow<Map<String, ModelState>> = modelRepository.modelStates

    init {
        modelRepository.refreshStates()
    }

    fun getModels(): List<ModelInfo> = modelRepository.getModelRegistry()

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.downloadModel(modelId)
        }
    }

    fun deleteModel(modelId: String) {
        val file = modelRepository.getModelFile(modelId)
        file?.delete()
        modelRepository.refreshStates()
    }
}
