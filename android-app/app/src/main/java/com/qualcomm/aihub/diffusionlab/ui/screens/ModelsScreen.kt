package com.qualcomm.aihub.diffusionlab.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qualcomm.aihub.diffusionlab.ui.components.ModelCard

/**
 * Model manager screen showing all available models with download status.
 *
 * Material 3 components:
 * - TopAppBar
 * - LazyColumn of ModelCard components
 * - Each ModelCard shows: name, size, role, download progress, status
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = hiltViewModel(),
) {
    val modelStates by viewModel.modelStates.collectAsState()
    val models = viewModel.getModels()

    TopAppBar(title = { Text("Models") })

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(models, key = { it.id }) { model ->
            ModelCard(
                model = model,
                state = modelStates[model.id],
                onDownload = { viewModel.downloadModel(model.id) },
                onDelete = { viewModel.deleteModel(model.id) },
            )
        }
    }
}
