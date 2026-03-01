package com.qualcomm.aihub.diffusionlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qualcomm.aihub.diffusionlab.ui.navigation.DiffusionLabNavHost
import com.qualcomm.aihub.diffusionlab.ui.theme.DiffusionLabTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DiffusionLabTheme {
                DiffusionLabNavHost()
            }
        }
    }
}
