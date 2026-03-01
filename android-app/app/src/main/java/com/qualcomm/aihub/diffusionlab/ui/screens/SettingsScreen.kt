package com.qualcomm.aihub.diffusionlab.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * App settings screen.
 *
 * Material 3 components:
 * - TopAppBar
 * - ListItem (settings rows with switches/text)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var autoSaveEnabled by remember { mutableStateOf(true) }
    var dynamicColorEnabled by remember { mutableStateOf(true) }
    var darkModeEnabled by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings") })

        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = "General",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text("Auto-save to gallery") },
                supportingContent = { Text("Automatically save generated images") },
                trailingContent = {
                    Switch(
                        checked = autoSaveEnabled,
                        onCheckedChange = { autoSaveEnabled = it },
                    )
                },
            )

            Text(
                text = "Appearance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text("Dynamic color") },
                supportingContent = { Text("Use Material You colors from wallpaper") },
                trailingContent = {
                    Switch(
                        checked = dynamicColorEnabled,
                        onCheckedChange = { dynamicColorEnabled = it },
                    )
                },
            )

            ListItem(
                headlineContent = { Text("Dark mode") },
                supportingContent = { Text("Use dark theme") },
                trailingContent = {
                    Switch(
                        checked = darkModeEnabled,
                        onCheckedChange = { darkModeEnabled = it },
                    )
                },
            )

            Text(
                text = "About",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text("DiffusionLab v1.0.0") },
                supportingContent = {
                    Text("On-device AI image generation powered by Qualcomm AI Hub")
                },
            )
        }
    }
}
