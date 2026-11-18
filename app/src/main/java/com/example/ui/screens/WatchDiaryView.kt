package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.WatchedMovie
import com.example.ui.theme.CineElevated
import com.example.ui.theme.CineGold
import com.example.ui.theme.CineSurfaceHigh
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val WEEKDAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
fun WatchDiaryView(
    monthYm: String,
    selectedDate: String,
    diaryByDate: Map<String, List<WatchedMovie>>,
    onChangeMonth: (Int) -> Unit,
    onSelectDate: (String) -> Unit,
    onMovieClick: (WatchedMovie) -> Unit,
    onLogRewatch: (WatchedMovie) -> Unit,
    modifier: Modifier = Modifier,
) {
    val calendarGrid = remember(monthYm) { buildCalendarCells(monthYm) }
    val monthHeaderLabel = remember(monthYm) { formatMonthYearTitle(monthYm) }
    val moviesOnSelectedDate = diaryByDate[selectedDate].orEmpty()
    val monthLogCount = remember(monthYm, diaryByDate) {
        diaryByDate.entries
            .filter { it.key.startsWith(monthYm) }
            .sumOf { it.value.size }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("watch_diary_view"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Monthly Calendar Card
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(
                            onClick = { onChangeMonth(-1) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("diary_prev_month"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "Previous month",
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = monthHeaderLabel,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "$monthLogCount ${if (monthLogCount == 1) "watch" else "watches"} this month",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        IconButton(
                            onClick = { onChangeMonth(1) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("diary_next_month"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Next month",
                            )
                        }
                    }

                    // Weekday headers
                    Row(modifier = Modifier.fillMaxWidth()) {
                        WEEKDAY_LABELS.forEach { day ->
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Calendar weeks
                    calendarGrid.chunked(7).forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            week.forEach { cell ->
                                if (cell == null) {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                    )
                                } else {
                                    val count = diaryByDate[cell.isoDate]?.size ?: 0
                                    val isSelected = cell.isoDate == selectedDate
                                    val hasWatches = count > 0

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    hasWatches -> MaterialTheme.colorScheme.primaryContainer
                                                    else -> CineElevated.copy(alpha = 0.45f)
                                                }
                                            )
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    hasWatches -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                                    else -> Color.Transparent
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                            )
                                            .clickable { onSelectDate(cell.isoDate) }
                                            .testTag("diary_day_${cell.isoDate}"),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = cell.dayNumber.toString(),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                                    hasWatches -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                                fontWeight = if (hasWatches || isSelected) FontWeight.Bold else FontWeight.Normal,
                                            )
                                            if (hasWatches) {
                                                Box(
                                                    modifier = Modifier
                                                        .padding(top = 2.dp)
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                            else MaterialTheme.colorScheme.primary
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected Date Entries Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "${formatFullDateLabel(selectedDate)} (${moviesOnSelectedDate.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        if (moviesOnSelectedDate.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "No movies or TV shows logged on this day. Highlighted dates show days you watched something.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(
                items = moviesOnSelectedDate,
                key = { "${selectedDate}_${it.uniqueKey}" },
            ) { movie ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onMovieClick(movie) }
                        .testTag("diary_entry_${movie.uniqueKey}"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(84.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CineElevated),
                            contentAlignment = Alignment.Center,
                        ) {
                            val posterUrl = movie.fullPosterUrl()
                            if (posterUrl != null) {
                                AsyncImage(
                                    model = posterUrl,
                                    contentDescription = movie.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Movie,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = movie.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = buildString {
                                    append(movie.releaseYear)
                                    if (movie.runtime > 0) append(" • ${movie.runtime}m")
                                    append(" • Watched ${movie.watchCount} ${if (movie.watchCount == 1) "time" else "times"}")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (movie.userRating > 0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = null,
                                        tint = CineGold,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = "${movie.userRating}/5",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CineGold,
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { onLogRewatch(movie) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("diary_rewatch_${movie.uniqueKey}"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Repeat,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+1",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class CalendarDayCell(
    val dayNumber: Int,
    val isoDate: String,
)

private fun buildCalendarCells(monthYm: String): List<CalendarDayCell?> {
    val parts = monthYm.split('-')
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return emptyList()
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return emptyList()

    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val cells = mutableListOf<CalendarDayCell?>()
    repeat(firstDayOfWeek.coerceAtLeast(0)) { cells.add(null) }
    for (day in 1..daysInMonth) {
        val iso = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
        cells.add(CalendarDayCell(dayNumber = day, isoDate = iso))
    }
    while (cells.size % 7 != 0) {
        cells.add(null)
    }
    return cells
}

private fun formatMonthYearTitle(monthYm: String): String {
    return try {
        val inFmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val outFmt = SimpleDateFormat("MMMM yyyy", Locale.US)
        inFmt.parse(monthYm)?.let { outFmt.format(it) } ?: monthYm
    } catch (_: Exception) {
        monthYm
    }
}

private fun formatFullDateLabel(isoDate: String): String {
    return try {
        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outFmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US)
        inFmt.parse(isoDate)?.let { outFmt.format(it) } ?: isoDate
    } catch (_: Exception) {
        isoDate
    }
}
