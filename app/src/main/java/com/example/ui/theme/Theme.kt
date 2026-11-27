package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(raw: String?): ThemeMode =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: DARK
    }
}

@Immutable
data class CineExtraColors(
    val cardScrimStart: Color,
    val cardScrimEnd: Color,
    val posterOverlayScrim: Color,
    val posterOverlayContent: Color,
    val posterBadgeBackground: Color,
    val shimmerBase: Color,
    val shimmerHighlight: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val gold: Color,
    val onGold: Color,
    val posterStarGold: Color,
    val chartPalette: List<Color>,
    val heatmapEmpty: Color,
    val isDark: Boolean,
)

val CineDarkExtraColors = CineExtraColors(
    cardScrimStart = Color.Transparent,
    cardScrimEnd = CineObsidian.copy(alpha = 0.90f),
    posterOverlayScrim = CineObsidian.copy(alpha = 0.84f),
    posterOverlayContent = Color.White,
    posterBadgeBackground = CineObsidian.copy(alpha = 0.86f),
    shimmerBase = CineElevated,
    shimmerHighlight = CineSurfaceHigh,
    success = CineEmerald,
    successContainer = CineEmeraldContainer,
    warning = CineGold,
    warningContainer = CineGold.copy(alpha = 0.16f),
    gold = CineGold,
    onGold = CineObsidian,
    posterStarGold = CineGold,
    chartPalette = listOf(
        CineGold,
        CineEmerald,
        Color(0xFF38BDF8),
        Color(0xFFA855F7),
        Color(0xFFFB7185),
        Color(0xFF94A3B8),
    ),
    heatmapEmpty = CineSurfaceHigh,
    isDark = true,
)

val CineLightExtraColors = CineExtraColors(
    cardScrimStart = Color.Transparent,
    cardScrimEnd = Color(0xCC0B0B0F),
    posterOverlayScrim = Color(0xD60B0B0F),
    posterOverlayContent = Color.White,
    posterBadgeBackground = Color(0xDB0B0B0F),
    shimmerBase = Color(0xFFE4E4EC),
    shimmerHighlight = Color(0xFFF3F3F8),
    success = CineLightEmerald,
    successContainer = CineLightEmeraldContainer,
    warning = CineLightGold,
    warningContainer = CineLightWarningContainer,
    gold = CineLightGold,
    onGold = Color.White,
    posterStarGold = CineGold,
    chartPalette = listOf(
        Color(0xFFD97706),
        Color(0xFF16A34A),
        Color(0xFF0284C7),
        Color(0xFF9333EA),
        Color(0xFFE11D48),
        Color(0xFF64748B),
    ),
    heatmapEmpty = Color(0xFFE4E4EC),
    isDark = false,
)

val LocalCineExtraColors = staticCompositionLocalOf { CineDarkExtraColors }

object CineTheme {
    val extraColors: CineExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCineExtraColors.current
}

fun contrastRatio(first: Color, second: Color): Float {
    val l1 = first.luminance()
    val l2 = second.luminance()
    return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
}

/**
 * Ensures the chosen accent color has at least [minContrast] (default 4.5:1)
 * against white surfaces in light mode by darkening RGB smoothly while keeping hue.
 */
fun darkenAccentForLightSurface(accent: Color, minContrast: Float = 4.5f): Color {
    if (contrastRatio(accent, CineLightSurface) >= minContrast) return accent
    var low = 0f
    var high = 1f
    var best = Color(0xFF14141A)
    repeat(16) {
        val factor = (low + high) * 0.5f
        val candidate = Color(
            red = (accent.red * factor).coerceIn(0f, 1f),
            green = (accent.green * factor).coerceIn(0f, 1f),
            blue = (accent.blue * factor).coerceIn(0f, 1f),
            alpha = 1f,
        )
        if (contrastRatio(candidate, CineLightSurface) >= minContrast) {
            best = candidate
            low = factor
        } else {
            high = factor
        }
    }
    return best
}

fun buildCineDarkColorScheme(accentColor: Color = CineCrimson): ColorScheme = darkColorScheme(
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

fun buildCineLightColorScheme(accentColor: Color = CineCrimson): ColorScheme {
    val safePrimary = darkenAccentForLightSurface(accentColor, minContrast = 4.5f)
    val onPrimaryColor = if (safePrimary.luminance() > 0.5f) CineLightInk else Color.White
    return lightColorScheme(
        primary = safePrimary,
        onPrimary = onPrimaryColor,
        primaryContainer = safePrimary.copy(alpha = 0.14f),
        onPrimaryContainer = safePrimary,
        secondary = CineLightGold,
        onSecondary = Color.White,
        secondaryContainer = CineLightSurfaceVariant,
        onSecondaryContainer = CineLightGold,
        tertiary = CineLightEmerald,
        onTertiary = Color.White,
        tertiaryContainer = CineLightEmeraldContainer,
        onTertiaryContainer = CineLightEmerald,
        background = CineLightBackground,
        onBackground = CineLightInk,
        surface = CineLightSurface,
        onSurface = CineLightInk,
        surfaceVariant = CineLightSurfaceVariant,
        onSurfaceVariant = CineLightMuted,
        surfaceContainerLowest = CineLightSurface,
        surfaceContainerLow = CineLightBackground,
        surfaceContainer = CineLightSurfaceVariant,
        surfaceContainerHigh = CineLightSurfaceHigh,
        outline = CineLightOutline,
        outlineVariant = CineLightSurfaceHigh,
        error = CineLightError,
        onError = Color.White,
        errorContainer = CineLightErrorContainer,
        onErrorContainer = CineLightInk,
    )
}

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
fun resolveDarkTheme(themeMode: ThemeMode, forceDark: Boolean = false): Boolean = when {
    forceDark -> true
    themeMode == ThemeMode.SYSTEM -> isSystemInDarkTheme()
    themeMode == ThemeMode.LIGHT -> false
    else -> true
}

@Composable
fun CineLogTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentColor: Color = CineCrimson,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = resolveDarkTheme(themeMode = themeMode, forceDark = forceDark)
    val colorScheme = if (isDark) {
        buildCineDarkColorScheme(accentColor)
    } else {
        buildCineLightColorScheme(accentColor)
    }
    val extraColors = if (isDark) CineDarkExtraColors else CineLightExtraColors

    CompositionLocalProvider(LocalCineExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CineTypography,
            shapes = CineShapes,
            content = content,
        )
    }
}
