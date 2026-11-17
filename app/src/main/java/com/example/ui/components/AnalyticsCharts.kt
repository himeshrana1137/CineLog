package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChartPoint
import com.example.data.model.ChartSlice
import com.example.data.model.CineStatsSummary
import com.example.ui.theme.CineElevated
import com.example.ui.theme.CineEmerald
import com.example.ui.theme.CineGold
import com.example.ui.theme.CineSurfaceHigh
import kotlin.math.roundToInt

@Composable
fun GenreAnalyticsSection(
    stats: CineStatsSummary,
    isMigrating: Boolean,
    onRunMigrationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
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

        // 1. Movies by Genre (Pie / Donut Chart)
        ChartCard(
            title = "MOVIES BY GENRE",
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
                    palette = chartPalette,
                )
            }
        }

        // 2. Movies by Release Year (Bar Chart)
        if (stats.releaseYearStats.isNotEmpty()) {
            ChartCard(
                title = "MOVIES BY RELEASE YEAR",
                icon = Icons.Rounded.BarChart,
                modifier = Modifier.testTag("chart_release_year_bar"),
            ) {
                VerticalBarChart(
                    points = stats.releaseYearStats,
                    barColor = primary,
                )
            }
        }

        // 3. Movies by User Rating (Bar Chart)
        ChartCard(
            title = "MOVIES BY USER RATING",
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

        // 4. Movies Watched by Month (Line Chart)
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
                modifier = Modifier.height(44.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenrePieChart(
    slices: List<ChartSlice>,
    palette: List<Color>,
) {
    val total = slices.sumOf { it.count }.coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(144.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(140.dp)) {
                val strokeWidth = 26.dp.toPx()
                val inset = strokeWidth / 2f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                var startAngle = -90f
                val gapDegrees = if (slices.size > 1) 2.5f else 0f
                slices.forEachIndexed { index, slice ->
                    val rawSweep = (slice.count.toFloat() / total.toFloat()) * 360f
                    val sweep = (rawSweep - gapDegrees).coerceAtLeast(2f)
                    drawArc(
                        color = palette[index % palette.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                    )
                    startAngle += rawSweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = total.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "tags",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            slices.forEachIndexed { index, slice ->
                val color = palette[index % palette.size]
                val pct = (slice.percentage * 100f).roundToInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "${slice.count} ($pct%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalBarChart(
    points: List<ChartPoint>,
    barColor: Color,
) {
    val maxVal = points.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Count values row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            points.forEach { pt ->
                Text(
                    text = pt.count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
        ) {
            val count = points.size.coerceAtLeast(1)
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.54f).coerceIn(10.dp.toPx(), 34.dp.toPx())
            val maxBarHeight = size.height - 6.dp.toPx()

            // Horizontal guide lines
            listOf(0.33f, 0.66f, 1f).forEach { frac ->
                val y = size.height - (maxBarHeight * frac)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            points.forEachIndexed { idx, pt ->
                val ratio = pt.count.toFloat() / maxVal.toFloat()
                val h = (maxBarHeight * ratio).coerceAtLeast(if (pt.count > 0) 6.dp.toPx() else 2.dp.toPx())
                val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                val top = size.height - h
                drawRoundRect(
                    color = if (pt.count > 0) barColor else CineSurfaceHigh,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
            }
        }

        // X-axis labels row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            points.forEach { pt ->
                Text(
                    text = pt.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
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

            coords.forEach { pt ->
                drawCircle(
                    color = lineColor,
                    radius = 5.5.dp.toPx(),
                    center = pt,
                )
                drawCircle(
                    color = pointCenterColor,
                    radius = 2.5.dp.toPx(),
                    center = pt,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            points.forEach { pt ->
                Text(
                    text = pt.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
