package com.scanforge3d.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = ScanBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = ScanGreen,
    error = ScanRed,
    background = ScanLightGray,
    surface = androidx.compose.ui.graphics.Color.White
)

@Composable
fun ScanForge3DTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
