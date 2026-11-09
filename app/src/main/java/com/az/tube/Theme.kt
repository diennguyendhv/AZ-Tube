package com.az.tube

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** AZ Tube orange, from the running-track logo. */
val Brand = Color(0xFFFF6B2C)

private val Dark = darkColorScheme(
    primary = Color(0xFFF1F1F1),
    onPrimary = Color(0xFF0F0F0F),
    secondary = Color(0xFF3EA6FF),
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF0F0F0F),
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF272727),
    onSurfaceVariant = Color(0xFFAAAAAA),
    secondaryContainer = Color(0xFF272727),
    onSecondaryContainer = Color(0xFFF1F1F1),
    surfaceContainerLowest = Color(0xFF0F0F0F),
    surfaceContainerLow = Color(0xFF212121),
    surfaceContainer = Color(0xFF212121),
    surfaceContainerHigh = Color(0xFF282828),
    surfaceContainerHighest = Color(0xFF303030),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFF3F3F3F),
    error = Color(0xFFFF6B6B),
)

private val Light = lightColorScheme(
    primary = Color(0xFF0F0F0F),
    onPrimary = Color.White,
    secondary = Color(0xFF065FD4),
    background = Color.White,
    onBackground = Color(0xFF0F0F0F),
    surface = Color.White,
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF606060),
    secondaryContainer = Color(0xFFF2F2F2),
    onSecondaryContainer = Color(0xFF0F0F0F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF2F2F2),
    surfaceContainerHighest = Color(0xFFE5E5E5),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFFE5E5E5),
    error = Color(0xFFCC0000),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
