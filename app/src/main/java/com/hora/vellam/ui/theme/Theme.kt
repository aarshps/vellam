package com.hora.vellam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val WaterBlue = Color(0xFF2196F3)
val DeepWater = Color(0xFF0D47A1)

private val LightColorScheme = lightColorScheme(
    primary = WaterBlue,
    secondary = DeepWater,
    tertiary = Color(0xFF03A9F4)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    secondary = Color(0xFF2196F3),
    tertiary = Color(0xFF81D4FA)
)

@Composable
fun VellamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
