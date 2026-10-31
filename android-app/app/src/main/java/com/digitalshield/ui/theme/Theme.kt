package com.digitalshield.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Cyan, onPrimary = Navy,
    primaryContainer = BlueDeep, onPrimaryContainer = Color.White,
    secondary = Blue, onSecondary = Color.White,
    background = Navy, onBackground = Color(0xFFF8FAFC),
    surface = NavySurface, onSurface = Color(0xFFF8FAFC),
    surfaceVariant = NavyCard, onSurfaceVariant = SlateLight,
    surfaceContainerLowest = Navy, surfaceContainerLow = NavySurface,
    surfaceContainer = NavySurface, surfaceContainerHigh = NavyCard, surfaceContainerHighest = NavyCard,
    outline = NavyLine, error = Danger
)

private val LightColors = lightColorScheme(
    primary = BlueDeep, onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE), onPrimaryContainer = Ink,
    secondary = CyanDeep, onSecondary = Color.White,
    background = Mist, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFE2E8F0), onSurfaceVariant = Slate,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF1F5F9), surfaceContainerHighest = Color(0xFFF1F5F9),
    outline = Color(0xFFCBD5E1), error = Color(0xFFDC2626)
)

@Composable
fun DigitalShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}