package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryNeon,
    secondary = SecondaryCyan,
    tertiary = AccentAccent,
    background = LightBg,
    surface = LightCard,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outlineVariant = OutlineVariantColor
)

@Composable
fun GitSyncAITheme(
    darkTheme: Boolean = false, // Force Light Mode as requested
    content: @Composable () -> Unit
) {
    // Enforce our clean, distinctive SaaS Light palette
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
