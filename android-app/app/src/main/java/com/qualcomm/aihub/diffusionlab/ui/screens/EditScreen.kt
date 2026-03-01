package com.qualcomm.aihub.diffusionlab.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qualcomm.aihub.diffusionlab.R
import com.qualcomm.aihub.diffusionlab.engine.drawing.BrushConfig
import com.qualcomm.aihub.diffusionlab.engine.drawing.MaskRenderer
import com.qualcomm.aihub.diffusionlab.ui.components.BrushControls
import com.qualcomm.aihub.diffusionlab.ui.components.DrawingCanvas

/**
 * Inpainting / Outpainting editor screen with S-Pen drawing support.
 *
 * Material 3 components:
 * - TopAppBar with undo/redo actions
 * - DrawingCanvas (AndroidView wrapping MaskRenderer)
 * - BrushControls (Sliders + FilterChips for feather/edge/denoise)
 * - FilledTonalButton for auto-mask, OutlinedButton for clear
 * - Button for inpaint action
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen() {
    val maskRenderer = remember { MaskRenderer() }
    var brushConfig by remember { mutableStateOf(BrushConfig()) }
    var inpaintPrompt by remember { mutableStateOf("") }
    var currentMask by remember { mutableStateOf<Bitmap?>(null) }

    // TODO: Get source image from navigation args or shared state
    var sourceImage by remember { mutableStateOf<Bitmap?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(
            title = { Text("Inpaint") },
            actions = {
                IconButton(onClick = { maskRenderer.undo() }) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = { maskRenderer.redo() }) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
            },
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Drawing canvas
            DrawingCanvas(
                sourceImage = sourceImage,
                brushConfig = brushConfig,
                maskRenderer = maskRenderer,
                onMaskChanged = { currentMask = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )

            // Brush controls (feather, edge, denoise sliders)
            BrushControls(
                config = brushConfig,
                onConfigChange = { brushConfig = it },
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { /* TODO: Run FastSAM for auto-masking */ },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                    Text(
                        text = stringResource(R.string.auto_mask_button),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                FilledTonalButton(
                    onClick = { maskRenderer.clear() },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Clear, contentDescription = null)
                    Text(
                        text = stringResource(R.string.clear_mask_button),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            // Inpaint prompt
            OutlinedTextField(
                value = inpaintPrompt,
                onValueChange = { inpaintPrompt = it },
                label = { Text("Fill with...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Inpaint button
            Button(
                onClick = { /* TODO: Run InpaintPipeline */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = currentMask != null && sourceImage != null,
            ) {
                Text(
                    text = stringResource(R.string.inpaint_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
