package com.qualcomm.aihub.diffusionlab.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qualcomm.aihub.diffusionlab.domain.model.PipelineState

/**
 * Displays step-by-step progress during SD generation.
 * Shows which component is running and current step count.
 */
@Composable
fun GenerationProgress(
    state: PipelineState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedContent(targetState = state, label = "progress") { currentState ->
                when (currentState) {
                    is PipelineState.LoadingModel -> {
                        Column {
                            Text(
                                text = "Loading ${currentState.componentName}...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    is PipelineState.Generating -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Denoising (${currentState.componentName})",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "${currentState.currentStep}/${currentState.totalSteps}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            LinearProgressIndicator(
                                progress = {
                                    currentState.currentStep.toFloat() / currentState.totalSteps
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    is PipelineState.Upscaling -> {
                        Column {
                            Text(
                                text = "Upscaling...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            LinearProgressIndicator(
                                progress = { currentState.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    is PipelineState.Inpainting -> {
                        Column {
                            Text(
                                text = "Inpainting...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            LinearProgressIndicator(
                                progress = { currentState.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    is PipelineState.Error -> {
                        Text(
                            text = "Error: ${currentState.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    is PipelineState.Done -> {
                        Text(
                            text = "Done!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    PipelineState.Idle -> { /* Hidden */ }
                }
            }
        }
    }
}
