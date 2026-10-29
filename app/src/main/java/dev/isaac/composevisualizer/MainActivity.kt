package dev.isaac.composevisualizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.isaac.composevisualizer.ui.VisualizerApp
import dev.isaac.composevisualizer.ui.theme.VisualizerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VisualizerTheme {
                VisualizerApp()
            }
        }
    }
}