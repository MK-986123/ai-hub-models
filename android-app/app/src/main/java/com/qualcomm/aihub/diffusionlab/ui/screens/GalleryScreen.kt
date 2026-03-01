package com.qualcomm.aihub.diffusionlab.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qualcomm.aihub.diffusionlab.ui.components.ImagePreview
import java.io.File

/**
 * Gallery screen showing all generated images in a grid.
 *
 * Material 3 components:
 * - TopAppBar
 * - LazyVerticalGrid (adaptive columns)
 * - ElevatedCard per image (via ImagePreview component)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val images = remember { viewModel.getImages() }

    TopAppBar(title = { Text("Gallery") })

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(images, key = { it.absolutePath }) { file ->
            ImagePreview(
                file = file,
                onClick = { /* TODO: Full-screen view */ },
                onLongClick = { /* TODO: Delete/share options */ },
            )
        }
    }
}
