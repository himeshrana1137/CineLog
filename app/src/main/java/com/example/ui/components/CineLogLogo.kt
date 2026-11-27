package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import com.example.ui.theme.CineBrandFont
import com.example.ui.theme.CineLogTheme
import com.example.ui.theme.CineLogoGradientBottom
import com.example.ui.theme.CineLogoGradientTop
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class CineLogLogoLayout {
    Horizontal,
    Vertical,
}

@Composable
fun CineLogLogo(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    showWordmark: Boolean = true,
    animated: Boolean = false,
    wordmarkFontSize: TextUnit? = null,
    layout: CineLogLogoLayout = CineLogLogoLayout.Horizontal,
    showGlow: Boolean = false,
) {
    val iconScale = remember(animated) { Animatable(if (animated) 0.8f else 1f) }
    val iconAlpha = remember(animated) { Animatable(if (animated) 0f else 1f) }
    val playPulse = remember(animated) { Animatable(1f) }
    val wordmarkAlpha = remember(animated) { Animatable(if (animated) 0f else 1f) }
    val wordmarkOffsetFraction = remember(animated) { Animatable(if (animated) 1f else 0f) }

    LaunchedEffect(animated) {
        if (!animated) {
            iconScale.snapTo(1f)
            iconAlpha.snapTo(1f)
            playPulse.snapTo(1f)
            wordmarkAlpha.snapTo(1f)
            wordmarkOffsetFraction.snapTo(0f)
            return@LaunchedEffect
        }

        // 1. Icon scales from 0.8 to 1 with spring and fades in
        launch {
            iconScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
        launch {
            iconAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            )
        }

        // 2. Wordmark slides up and fades in shortly after icon appears
        launch {
            delay(110L)
            launch {
                wordmarkAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                )
            }
            launch {
                wordmarkOffsetFraction.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
        }

        // 3. Gentle one-time pulse on the play triangle
        delay(220L)
        playPulse.animateTo(
            targetValue = 1.12f,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        )
        playPulse.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    val resolvedWordmarkSize = wordmarkFontSize ?: (size.value * 0.714f).coerceIn(14f, 44f).sp
    val density = LocalDensity.current
    val slideDistancePx = with(density) { 14.dp.toPx() }
    val glowColor = MaterialTheme.colorScheme.primary
    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val accentTextColor = MaterialTheme.colorScheme.primary

    val wordmarkAnnotated = remember(primaryTextColor, accentTextColor) {
        buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = primaryTextColor,
                    fontWeight = FontWeight.ExtraBold,
                )
            ) {
                append("Cine")
            }
            withStyle(
                SpanStyle(
                    color = accentTextColor,
                    fontWeight = FontWeight.ExtraBold,
                )
            ) {
                append("Log")
            }
        }
    }

    val iconComposable: @Composable () -> Unit = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                    alpha = iconAlpha.value
                }
                .then(
                    if (showGlow) {
                        Modifier.drawBehind {
                            val glowRadius = this.size.minDimension * 1.15f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        glowColor.copy(alpha = 0.35f),
                                        Color.Transparent,
                                    ),
                                    center = center,
                                    radius = glowRadius,
                                ),
                                radius = glowRadius,
                                center = center,
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            CineLogMarkCanvas(
                size = size,
                playTriangleScale = playPulse.value,
            )
        }
    }

    val wordmarkComposable: @Composable () -> Unit = {
        Text(
            text = wordmarkAnnotated,
            fontFamily = CineBrandFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = resolvedWordmarkSize,
            lineHeight = resolvedWordmarkSize * 1.08f,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                alpha = wordmarkAlpha.value
                translationY = wordmarkOffsetFraction.value * slideDistancePx
            },
        )
    }

    val a11yModifier = modifier.semantics(mergeDescendants = true) {
        contentDescription = "CineLog"
    }

    when (layout) {
        CineLogLogoLayout.Horizontal -> {
            Row(
                modifier = a11yModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                iconComposable()
                if (showWordmark) {
                    Spacer(modifier = Modifier.width((size * 0.32f).coerceAtLeast(8.dp)))
                    wordmarkComposable()
                }
            }
        }

        CineLogLogoLayout.Vertical -> {
            Column(
                modifier = a11yModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                iconComposable()
                if (showWordmark) {
                    Spacer(modifier = Modifier.height((size * 0.18f).coerceAtLeast(10.dp)))
                    wordmarkComposable()
                }
            }
        }
    }
}

@Composable
private fun CineLogMarkCanvas(
    size: Dp,
    playTriangleScale: Float,
) {
    val whiteMarkColor = Color.White
    val layerPaint = remember { Paint() }
    val trianglePath = remember { Path() }
    val checkPath = remember { Path() }

    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.minDimension
        val scale = s / 100f
        val cornerRadius = s * 0.28f

        // 1. Rounded squircle filled with vertical gradient #FF2D3A -> #B0000F
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(CineLogoGradientTop, CineLogoGradientBottom),
            ),
            topLeft = Offset.Zero,
            size = Size(s, s),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        )

        // 2. Subtle inner highlight on the top edge
        val highlightStroke = (2.2f * scale).coerceAtLeast(1f)
        val inset = highlightStroke / 2f
        val innerCorner = (cornerRadius - inset).coerceAtLeast(0f)
        drawRoundRect(
            brush = Brush.verticalGradient(
                0.0f to whiteMarkColor.copy(alpha = 0.38f),
                0.45f to Color.Transparent,
                startY = 0f,
                endY = s * 0.45f,
            ),
            topLeft = Offset(inset, inset),
            size = Size(s - highlightStroke, s - highlightStroke),
            cornerRadius = CornerRadius(innerCorner, innerCorner),
            style = Stroke(width = highlightStroke),
        )

        // 3. White rounded play triangle with lower-right checkmark cutout
        trianglePath.reset()
        trianglePath.moveTo(31f * scale, 30.5f * scale)
        trianglePath.quadraticTo(31f * scale, 25f * scale, 35.8f * scale, 27.7f * scale)
        trianglePath.lineTo(68.2f * scale, 46.3f * scale)
        trianglePath.quadraticTo(73f * scale, 49f * scale, 68.2f * scale, 51.7f * scale)
        trianglePath.lineTo(35.8f * scale, 70.3f * scale)
        trianglePath.quadraticTo(31f * scale, 73f * scale, 31f * scale, 67.5f * scale)
        trianglePath.close()

        checkPath.reset()
        checkPath.moveTo(51.5f * scale, 66.5f * scale)
        checkPath.lineTo(57.5f * scale, 72.5f * scale)
        checkPath.lineTo(69.5f * scale, 59.5f * scale)

        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, s, s), layerPaint)

            scale(
                scale = playTriangleScale,
                pivot = Offset(49f * scale, 49f * scale),
            ) {
                drawPath(
                    path = trianglePath,
                    color = whiteMarkColor,
                )
            }

            // Cut clean circular pocket into lower-right of triangle so gradient shows through
            drawCircle(
                color = Color.Transparent,
                radius = 14.5f * scale,
                center = Offset(59f * scale, 66f * scale),
                blendMode = BlendMode.Clear,
            )

            canvas.restore()
        }

        // 4. Crisp white check mark inside the cutout pocket
        drawPath(
            path = checkPath,
            color = whiteMarkColor,
            style = Stroke(
                width = (5.8f * scale).coerceAtLeast(1.4f),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Preview(name = "CineLog Logo 24dp", showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun CineLogLogoPreview24() {
    CineLogTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CineLogLogo(size = 24.dp, animated = false)
        }
    }
}

@Preview(name = "CineLog Logo 48dp", showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun CineLogLogoPreview48() {
    CineLogTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CineLogLogo(size = 48.dp, animated = false)
        }
    }
}

@Preview(name = "CineLog Logo 48dp - Light", showBackground = true, backgroundColor = 0xFFF7F7FA)
@Composable
private fun CineLogLogoPreview48Light() {
    CineLogTheme(themeMode = ThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                CineLogLogo(size = 48.dp, animated = false)
            }
        }
    }
}

@Preview(name = "CineLog Logo 96dp", showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun CineLogLogoPreview96() {
    CineLogTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.padding(20.dp)) {
            CineLogLogo(
                size = 96.dp,
                animated = false,
                layout = CineLogLogoLayout.Vertical,
                showGlow = true,
            )
        }
    }
}

@Preview(name = "CineLog Logo 96dp - Light", showBackground = true, backgroundColor = 0xFFF7F7FA)
@Composable
private fun CineLogLogoPreview96Light() {
    CineLogTheme(themeMode = ThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(20.dp)) {
                CineLogLogo(
                    size = 96.dp,
                    animated = false,
                    layout = CineLogLogoLayout.Vertical,
                    showGlow = true,
                )
            }
        }
    }
}
