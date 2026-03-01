package com.qualcomm.aihub.diffusionlab.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qualcomm.aihub.diffusionlab.R
import com.qualcomm.aihub.diffusionlab.domain.model.EdgeMode
import com.qualcomm.aihub.diffusionlab.engine.drawing.BrushConfig

/**
 * Material 3 controls for brush configuration.
 * Displays sliders for brush size, feather, and denoise,
 * plus filter chips for edge mode selection.
 */
@Composable
fun BrushControls(
    config: BrushConfig,
    onConfigChange: (BrushConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Brush size
        Text(
            text = "${stringResource(R.string.brush_size_label)}: ${config.sizePx}px",
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = config.sizePx.toFloat(),
            onValueChange = { onConfigChange(config.copy(sizePx = it.toInt())) },
            valueRange = 1f..100f,
        )

        // Feather
        Text(
            text = "${stringResource(R.string.feather_label)}: ${config.featherPercent.toInt()}%",
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = config.featherPercent,
            onValueChange = { onConfigChange(config.copy(featherPercent = it)) },
            valueRange = 0f..100f,
        )

        // Edge mode chips
        Text(
            text = stringResource(R.string.edge_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EdgeMode.entries.forEach { mode ->
                FilterChip(
                    selected = config.edgeMode == mode,
                    onClick = { onConfigChange(config.copy(edgeMode = mode)) },
                    label = { Text(mode.displayName) },
                )
            }
        }

        // Denoise strength
        Text(
            text = "${stringResource(R.string.denoise_label)}: ${"%.1f".format(config.denoiseStrength)}",
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = config.denoiseStrength,
            onValueChange = { onConfigChange(config.copy(denoiseStrength = it)) },
            valueRange = 0f..1f,
        )
    }
}
