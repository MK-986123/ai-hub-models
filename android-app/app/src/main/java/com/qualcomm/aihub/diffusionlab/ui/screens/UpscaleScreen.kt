package com.qualcomm.aihub.diffusionlab.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qualcomm.aihub.diffusionlab.R

/**
 * Image upscaling screen.
 *
 * Allows users to select an image and upscale it using either:
 * - Real-ESRGAN 4x (premium, high quality, ~25ms on NPU)
 * - XLSR (free, lightweight, real-time)
 *
 * Material 3 components:
 * - TopAppBar
 * - OutlinedCard (image before/after preview)
 * - FilterChip (model selection)
 * - Button (upscale action)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpscaleScreen() {
    var selectedModel by remember { mutableStateOf("xlsr") }
    var scaleFactor by remember { mutableIntStateOf(4) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Upscale") })

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Image upload/preview card
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                onClick = { /* TODO: Image picker */ },
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.Upload,
                        contentDescription = "Upload image",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap to select an image",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Model selection
            Text(
                text = "Upscaling Model",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedModel == "real_esrgan",
                    onClick = { selectedModel = "real_esrgan" },
                    label = { Text("Real-ESRGAN 4x") },
                )
                FilterChip(
                    selected = selectedModel == "xlsr",
                    onClick = { selectedModel = "xlsr" },
                    label = { Text("XLSR (Fast)") },
                )
            }

            // Scale factor (for XLSR)
            if (selectedModel == "xlsr") {
                Text(
                    text = "Scale Factor",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4).forEach { scale ->
                        FilterChip(
                            selected = scaleFactor == scale,
                            onClick = { scaleFactor = scale },
                            label = { Text("${scale}x") },
                        )
                    }
                }
            }

            // Upscale button
            Button(
                onClick = { /* TODO: Run UpscalePipeline */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.upscale_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
