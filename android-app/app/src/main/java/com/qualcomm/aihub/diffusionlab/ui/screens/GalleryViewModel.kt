package com.qualcomm.aihub.diffusionlab.ui.screens

import androidx.lifecycle.ViewModel
import com.qualcomm.aihub.diffusionlab.data.repository.ImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
) : ViewModel() {

    fun getImages(): List<File> = imageRepository.listGalleryImages()
}
