package com.qualcomm.aihub.diffusionlab.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qualcomm.aihub.diffusionlab.R
import com.qualcomm.aihub.diffusionlab.domain.model.PipelineState
import com.qualcomm.aihub.diffusionlab.ui.components.GenerationProgress

/**
 * Text-to-image generation screen.
 *
 * Material 3 components used:
 * - TopAppBar (center-aligned)
 * - OutlinedTextField (prompt input)
 * - Slider (steps, guidance, seed)
 * - OutlinedCard (image preview)
 * - Button (FilledButton for generate)
 * - LinearProgressIndicator (step progress)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(
    viewModel: GenerateViewModel = hiltViewModel(),
) {
    val pipelineState by viewModel.pipelineState.collectAsState()
    val generatedImage by viewModel.generatedImage.collectAsState()

    var prompt by remember { mutableStateOf("") }
    var numSteps by remember { mutableFloatStateOf(20f) }
    var guidanceScale by remember { mutableFloatStateOf(7.5f) }
    var seed by remember { mutableLongStateOf(0L) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(
            title = { Text("DiffusionLab") },
            actions = {
                IconButton(onClick = { /* TODO: Settings */ }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            },
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Image preview card
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                if (generatedImage != null) {
                    Image(
                        bitmap = generatedImage!!.asImageBitmap(),
                        contentDescription = "Generated image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "512 × 512",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Your image will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Pipeline progress
            if (pipelineState !is PipelineState.Idle) {
                GenerationProgress(state = pipelineState)
            }

            // Prompt input
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text(stringResource(R.string.prompt_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )

            // Steps slider
            Text(
                text = "${stringResource(R.string.steps_label)}: ${numSteps.toInt()}",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = numSteps,
                onValueChange = { numSteps = it },
                valueRange = 1f..50f,
                steps = 49,
            )

            // Guidance scale slider
            Text(
                text = "${stringResource(R.string.guidance_label)}: ${"%.1f".format(guidanceScale)}",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = guidanceScale,
                onValueChange = { guidanceScale = it },
                valueRange = 0f..20f,
            )

            // Seed
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = seed.toString(),
                    onValueChange = { it.toLongOrNull()?.let { v -> seed = v } },
                    label = { Text(stringResource(R.string.seed_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(onClick = { seed = (Math.random() * Long.MAX_VALUE).toLong() }) {
                    Icon(Icons.Filled.Casino, contentDescription = "Random seed")
                }
            }

            // Generate button
            Button(
                onClick = {
                    viewModel.generate(
                        prompt = prompt,
                        numSteps = numSteps.toInt(),
                        guidanceScale = guidanceScale,
                        seed = seed,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = pipelineState is PipelineState.Idle && prompt.isNotBlank(),
            ) {
                Text(
                    text = stringResource(R.string.generate_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
