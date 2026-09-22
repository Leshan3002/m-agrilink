package com.example.m_agrilink.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = DeepTerracotta,
    secondary = WarmSunGold,
    background = OffWhite,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = CharcoalLabels,
    onSurface = CharcoalLabels,
    surfaceVariant = ForestDarkSage,
    onSurfaceVariant = Color.White
)

@Composable
fun MAgriLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
