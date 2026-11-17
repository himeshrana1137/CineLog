package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

fun buildCineDarkColorScheme(accentColor: Color = CineCrimson) = darkColorScheme(
    primary = accentColor,
    onPrimary = if (accentColor.luminance() > 0.5f) CineObsidian else CineInk,
    primaryContainer = accentColor.copy(alpha = 0.22f),
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

fun parseHexColorOrDefault(hex: String, fallback: Color = CineCrimson): Color {
    return try {
        val cleaned = hex.trim().let { if (it.startsWith("#")) it else "#$it" }
        Color(android.graphics.Color.parseColor(cleaned))
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun CineLogTheme(
    accentColor: Color = CineCrimson,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = buildCineDarkColorScheme(accentColor),
        typography = CineTypography,
        shapes = CineShapes,
        content = content,
    )
}
