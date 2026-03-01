package com.qualcomm.aihub.diffusionlab.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qualcomm.aihub.diffusionlab.ui.screens.EditScreen
import com.qualcomm.aihub.diffusionlab.ui.screens.GalleryScreen
import com.qualcomm.aihub.diffusionlab.ui.screens.GenerateScreen
import com.qualcomm.aihub.diffusionlab.ui.screens.ModelsScreen
import com.qualcomm.aihub.diffusionlab.ui.screens.UpscaleScreen

/**
 * Bottom navigation destinations for the app.
 */
enum class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Generate("generate", "Generate", Icons.Filled.AutoAwesome),
    Edit("edit", "Edit", Icons.Filled.Edit),
    Upscale("upscale", "Upscale", Icons.Filled.ZoomIn),
    Gallery("gallery", "Gallery", Icons.Filled.Collections),
    Models("models", "Models", Icons.Filled.Hub),
}

/**
 * Main navigation host with Material 3 bottom NavigationBar.
 */
@Composable
fun DiffusionLabNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Generate.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Generate.route) { GenerateScreen() }
            composable(Screen.Edit.route) { EditScreen() }
            composable(Screen.Upscale.route) { UpscaleScreen() }
            composable(Screen.Gallery.route) { GalleryScreen() }
            composable(Screen.Models.route) { ModelsScreen() }
        }
    }
}
