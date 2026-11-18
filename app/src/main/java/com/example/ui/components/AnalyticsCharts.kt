package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.ChartPoint
import com.example.data.model.ChartSlice
import com.example.data.model.CineStatsSummary
import com.example.data.model.StatsMediaFilter
import com.example.ui.theme.CineElevated
import com.example.ui.theme.CineEmerald
import com.example.ui.theme.CineGold
import com.example.ui.theme.CineSurfaceHigh
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun GenreAnalyticsSection(
    stats: CineStatsSummary,
    mediaFilter: StatsMediaFilter = StatsMediaFilter.ALL,
    isMigrating: Boolean,
    onRunMigrationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val nounUpper = mediaFilter.pluralNoun.uppercase()
    val chartPalette = remember(primary) {
        listOf(
            primary,
            CineGold,
            CineEmerald,
            Color(0xFF38BDF8),
            Color(0xFFA855F7),
            Color(0xFFFB7185),
            Color(0xFF94A3B8),
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("genre_analytics_section"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.BarChart,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "GENRE & WATCH ANALYTICS",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // 1. Titles / Movies / TV Series by Genre (Donut Chart)
        ChartCard(
            title = "$nounUpper BY GENRE",
            icon = Icons.Rounded.PieChart,
            modifier = Modifier.testTag("chart_genre_pie"),
        ) {
            if (stats.genreStats.isEmpty()) {
                EmptyChartHint(
                    text = "Genre tags are missing on older entries. Sync metadata from TMDB to unlock genre charts.",
                    actionLabel = if (stats.itemsMissingMetadataCount > 0) {
                        if (isMigrating) "Updating…" else "Update Old Data"
                    } else null,
                    isActionEnabled = !isMigrating,
                    onActionClick = onRunMigrationClick,
                )
            } else {
                GenrePieChart(
                    slices = stats.genreStats,
                    totalTitles = stats.totalTitles,
                    palette = chartPalette,
                )
            }
        }

        // 2. Titles / Movies / TV Series by Release Year (Bar Chart)
        if (stats.releaseYearStats.isNotEmpty()) {
            ChartCard(
                title = "$nounUpper BY RELEASE YEAR",
                icon = Icons.Rounded.BarChart,
                modifier = Modifier.testTag("chart_release_year_bar"),
            ) {
                VerticalBarChart(
                    points = stats.releaseYearStats,
                    barColor = primary,
                )
            }
        }

        // 3. Titles / Movies / TV Series by User Rating (Bar Chart)
        ChartCard(
            title = "$nounUpper BY USER RATING",
            icon = Icons.Rounded.BarChart,
            modifier = Modifier.testTag("chart_user_rating_bar"),
        ) {
            if (stats.userRatingDistribution.all { it.count == 0 }) {
                EmptyChartHint(text = "Rate watched titles (1★–5★) to visualize your rating distribution.")
            } else {
                VerticalBarChart(
                    points = stats.userRatingDistribution,
                    barColor = CineGold,
                )
            }
        }

        // 4. Watched by Month (Line Chart)
        if (stats.monthlyWatchStats.isNotEmpty()) {
            ChartCard(
                title = "WATCHED BY MONTH",
                icon = Icons.AutoMirrored.Rounded.ShowChart,
                modifier = Modifier.testTag("chart_monthly_line"),
            ) {
                MonthlyLineChart(
                    points = stats.monthlyWatchStats,
                    lineColor = primary,
                )
            }
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            content()
        }
    }
}

@Composable
private fun EmptyChartHint(
    text: String,
    actionLabel: String? = null,
    isActionEnabled: Boolean = true,
    onActionClick: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onActionClick != null) {
            Button(
                onClick = onActionClick,
                enabled = isActionEnabled,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoFixHigh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun GenrePieChart(
    slices: List<ChartSlice>,
    totalTitles: Int,
    palette: List<Color>,
) {
    val totalTags = slices.sumOf { it.count }.coerceAtLeast(1)
    var selectedSliceIndex by remember(slices) { mutableIntStateOf(-1) }
    val sweepAnim = remember(slices) { Animatable(0f) }

    LaunchedEffect(slices) {
        sweepAnim.snapTo(0f)
        sweepAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        )
    }

    val sliceAngles = remember(slices, totalTags) {
        var cursor = 0f
        slices.map { slice ->
            val sweep = (slice.count.toFloat() / totalTags.toFloat()) * 360f
            val range = cursor to (cursor + sweep)
            cursor += sweep
            range
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(144.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .size(140.dp)
                        .pointerInput(sliceAngles) {
                            detectTapGestures { offset ->
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val dx = offset.x - cx
                                val dy = offset.y - cy
                                val radius = hypot(dx, dy)
                                val outerR = size.width / 2f
                                val innerR = outerR - 34.dp.toPx()
                                if (radius in innerR..outerR) {
                                    // Convert touch coordinates relative to 12 o'clock (-90 deg) clockwise 0..360
                                    val angleDeg = ((atan2(dy, dx) * (180.0 / PI)).toFloat() + 450f) % 360f
                                    val hitIdx = sliceAngles.indexOfFirst { (start, end) ->
                                        angleDeg in start..end
                                    }
                                    if (hitIdx >= 0) {
                                        selectedSliceIndex = if (selectedSliceIndex == hitIdx) -1 else hitIdx
                                    }
                                }
                            }
                        }
                ) {
                    val baseStrokeWidth = 25.dp.toPx()
                    val selectedStrokeWidth = 30.dp.toPx()
                    val inset = selectedStrokeWidth / 2f
                    val arcSize = Size(size.width - selectedStrokeWidth, size.height - selectedStrokeWidth)
                    var startAngle = -90f
                    val gapDegrees = if (slices.size > 1) 2.5f else 0f
                    val progress = sweepAnim.value

                    slices.forEachIndexed { index, slice ->
                        val rawSweep = (slice.count.toFloat() / totalTags.toFloat()) * 360f * progress
                        val sweep = (rawSweep - gapDegrees).coerceAtLeast(if (progress > 0.05f) 2f else 0f)
                        val isSelected = selectedSliceIndex == index
                        if (sweep > 0f) {
                            drawArc(
                                color = palette[index % palette.size],
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(
                                    width = if (isSelected) selectedStrokeWidth else baseStrokeWidth,
                                    cap = StrokeCap.Butt,
                                ),
                            )
                        }
                        startAngle += (slice.count.toFloat() / totalTags.toFloat()) * 360f * progress
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$totalTitles ${if (totalTitles == 1) "title" else "titles"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "by genre",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                slices.forEachIndexed { index, slice ->
                    val color = palette[index % palette.size]
                    val pct = (slice.percentage * 100f).roundToInt()
                    val isSelected = selectedSliceIndex == index
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) CineElevated else Color.Transparent
                            )
                            .clickable {
                                selectedSliceIndex = if (selectedSliceIndex == index) -1 else index
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Text(
                                text = slice.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                softWrap = true,
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${slice.count} ($pct%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = selectedSliceIndex in slices.indices) {
            val selected = slices.getOrNull(selectedSliceIndex)
            if (selected != null) {
                val pct = (selected.percentage * 100f).roundToInt()
                Surface(
                    color = CineElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette[selectedSliceIndex % palette.size].copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "${selected.label}: ${selected.count} tagged entries ($pct% of genre tags)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Text(
            text = "A title can have multiple genres",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VerticalBarChart(
    points: List<ChartPoint>,
    barColor: Color,
) {
    val maxVal = points.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    var selectedIdx by remember(points) { mutableIntStateOf(-1) }
    val heightAnim = remember(points) { Animatable(0f) }

    LaunchedEffect(points) {
        heightAnim.snapTo(0f)
        heightAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        )
    }

    val maxBarAreaDp = 126.dp

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val minSlotDp = 56.dp
            val neededWidth = minSlotDp * points.size
            val needsScroll = neededWidth > maxWidth && points.size > 5
            val contentWidth = if (needsScroll) neededWidth else maxWidth
            val slotWidth = if (points.isNotEmpty()) contentWidth / points.size else maxWidth

            Box(
                modifier = if (needsScroll) {
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
                Box(modifier = Modifier.width(contentWidth)) {
                    // Subtle guide lines positioned behind the bar region
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 22.dp)
                            .height(maxBarAreaDp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(gridColor)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        points.forEachIndexed { idx, pt ->
                            val isSelected = selectedIdx == idx
                            val ratio = (pt.count.toFloat() / maxVal.toFloat()) * heightAnim.value
                            val targetBarDp = if (pt.count > 0) {
                                (114f * ratio).coerceAtLeast(6f).dp
                            } else {
                                2.dp
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .width(slotWidth)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedIdx = if (selectedIdx == idx) -1 else idx
                                    }
                                    .padding(horizontal = 2.dp, vertical = 2.dp),
                            ) {
                                Box(
                                    modifier = Modifier.height(maxBarAreaDp + 22.dp),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom,
                                    ) {
                                        Text(
                                            text = pt.count.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) barColor else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(if (isSelected) 28.dp else 24.dp)
                                                .height(targetBarDp)
                                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                                .background(
                                                    when {
                                                        pt.count == 0 -> CineSurfaceHigh
                                                        isSelected -> barColor
                                                        else -> barColor.copy(alpha = 0.88f)
                                                    }
                                                )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = pt.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = selectedIdx in points.indices) {
            val pt = points.getOrNull(selectedIdx)
            if (pt != null) {
                Surface(
                    color = CineElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, barColor.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "${pt.label}: ${pt.count} ${if (pt.count == 1) "title" else "titles"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlyLineChart(
    points: List<ChartPoint>,
    lineColor: Color,
) {
    val maxVal = points.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val pointCenterColor = CineElevated
    var selectedIdx by remember(points) { mutableIntStateOf(-1) }
    val revealAnim = remember(points) { Animatable(0f) }

    LaunchedEffect(points) {
        revealAnim.snapTo(0f)
        revealAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Values row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            points.forEach { pt ->
                Text(
                    text = pt.count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = lineColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.isNotEmpty()) {
                            val slot = size.width / points.size.toFloat()
                            val hit = (offset.x / slot).toInt().coerceIn(0, points.lastIndex)
                            selectedIdx = if (selectedIdx == hit) -1 else hit
                        }
                    }
                }
        ) {
            val topPad = 12.dp.toPx()
            val bottomPad = 10.dp.toPx()
            val usableHeight = (size.height - topPad - bottomPad).coerceAtLeast(10f)

            listOf(0f, 0.5f, 1f).forEach { frac ->
                val y = size.height - bottomPad - usableHeight * frac
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val coords = if (points.size == 1) {
                val ratio = points.first().count.toFloat() / maxVal.toFloat()
                val y = size.height - bottomPad - usableHeight * ratio
                listOf(Offset(size.width / 2f, y))
            } else {
                val slot = size.width / points.size.toFloat()
                points.mapIndexed { index, pt ->
                    val x = slot * index + slot / 2f
                    val ratio = pt.count.toFloat() / maxVal.toFloat()
                    val y = size.height - bottomPad - usableHeight * ratio
                    Offset(x, y)
                }
            }

            clipRect(right = size.width * revealAnim.value) {
                if (coords.size >= 2) {
                    val linePath = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        for (i in 1 until coords.size) {
                            lineTo(coords[i].x, coords[i].y)
                        }
                    }
                    val areaPath = Path().apply {
                        addPath(linePath)
                        lineTo(coords.last().x, size.height - bottomPad)
                        lineTo(coords.first().x, size.height - bottomPad)
                        close()
                    }
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.32f), Color.Transparent),
                            startY = topPad,
                            endY = size.height - bottomPad,
                        )
                    )
                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        )
                    )
                }

                coords.forEachIndexed { idx, pt ->
                    val radius = if (selectedIdx == idx) 7.5.dp.toPx() else 5.5.dp.toPx()
                    drawCircle(
                        color = lineColor,
                        radius = radius,
                        center = pt,
                    )
                    drawCircle(
                        color = pointCenterColor,
                        radius = 2.5.dp.toPx(),
                        center = pt,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            points.forEachIndexed { idx, pt ->
                Text(
                    text = pt.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selectedIdx == idx) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (selectedIdx == idx) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedIdx = if (selectedIdx == idx) -1 else idx
                        },
                )
            }
        }

        AnimatedVisibility(visible = selectedIdx in points.indices) {
            val pt = points.getOrNull(selectedIdx)
            if (pt != null) {
                Surface(
                    color = CineElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, lineColor.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "${pt.label}: ${pt.count} watched",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
