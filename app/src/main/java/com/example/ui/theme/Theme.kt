package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CineDarkColorScheme = darkColorScheme(
    primary = CineCrimson,
    onPrimary = CineInk,
    primaryContainer = CineCrimsonContainer,
    onPrimaryContainer = CineInk,
    secondary = CineGold,
    onSecondary = CineObsidian,
    secondaryContainer = CineElevated,
    onSecondaryContainer = CineGold,
    tertiary = CineEmerald,
    onTertiary = CineObsidian,
    tertiaryContainer = CineEmeraldContainer,
    onTertiaryContainer = CineEmerald,
    background = CineObsidian,
    onBackground = CineInk,
    surface = CineCharcoal,
    onSurface = CineInk,
    surfaceVariant = CineElevated,
    onSurfaceVariant = CineMuted,
    surfaceContainerLowest = CineObsidian,
    surfaceContainerLow = CineCharcoal,
    surfaceContainer = CineElevated,
    surfaceContainerHigh = CineSurfaceHigh,
    outline = CineOutline,
    outlineVariant = CineSurfaceHigh,
    error = CineError,
    onError = CineInk,
    errorContainer = CineErrorContainer,
    onErrorContainer = CineInk,
)

private val CineShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CineLogTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CineDarkColorScheme,
        typography = CineTypography,
        shapes = CineShapes,
        content = content,
    )
}
