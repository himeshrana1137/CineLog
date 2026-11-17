package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.CineStatsSummary
import com.example.data.model.PersonStat
import com.example.data.model.QuickReaction
import com.example.data.model.WatchedMovie
import com.example.ui.components.CineEmptyState
import com.example.ui.components.GenreAnalyticsSection
import com.example.ui.theme.CineCharcoal
import com.example.ui.theme.CineElevated
import com.example.ui.theme.CineGold
import com.example.ui.theme.CineSurfaceHigh
import java.text.NumberFormat
import java.util.Locale

private enum class PeopleStatTab(val label: String) {
    ACTORS("Top Actors"),
    DIRECTORS("Top Directors"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(
    stats: CineStatsSummary,
    isMigrating: Boolean,
    onPersonClick: (roleLabel: String, stat: PersonStat) -> Unit,
    onRunMigrationClick: () -> Unit,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stats.totalTitles == 0) {
        CineEmptyState(
            title = "No stats yet",
            subtitle = "Mark movies or TV series as watched to unlock your Watch-Time Calculator, Top Actors, and Director Insights.",
            actionLabel = "Explore Movies & TV",
            onActionClick = onExploreClick,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    var peopleTab by remember { mutableStateOf(PeopleStatTab.ACTORS) }
    val numFormat = remember { NumberFormat.getIntegerInstance(Locale.US) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Watch-Time Calculator Hero Card
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("watch_time_stats_card"),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "WATCH-TIME CALCULATOR",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Text(
                        text = stats.formattedHoursAndDays,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                    )

                    Text(
                        text = "${numFormat.format(stats.totalMinutes)} total minutes logged",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatMetricTile(
                            label = "Titles",
                            value = stats.totalTitles.toString(),
                            subtitle = if (stats.totalWatchEvents > stats.totalTitles) {
                                "${stats.totalWatchEvents} watches"
                            } else {
                                "${stats.moviesCount}M • ${stats.tvCount}TV"
                            },
                            modifier = Modifier.weight(1f),
                        )
                        StatMetricTile(
                            label = "Avg Runtime",
                            value = if (stats.averageRuntimeMinutes > 0) "${stats.averageRuntimeMinutes}m" else "—",
                            subtitle = "per title",
                            modifier = Modifier.weight(1f),
                        )
                        StatMetricTile(
                            label = "Avg Rating",
                            value = if (stats.ratedCount > 0) {
                                String.format(Locale.US, "%.1f★", stats.averageUserRating)
                            } else "—",
                            subtitle = "${stats.ratedCount} rated",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Backfill prompt for older watched items missing runtime/cast/language
        if (stats.itemsMissingMetadataCount > 0) {
            item {
                Surface(
                    color = CineGold.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, CineGold.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${stats.itemsMissingMetadataCount} older ${if (stats.itemsMissingMetadataCount == 1) "entry needs" else "entries need"} sync",
                                style = MaterialTheme.typography.titleMedium,
                                color = CineGold,
                            )
                            Text(
                                text = "Fetch missing runtimes, cast, directors & languages from TMDB.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = onRunMigrationClick,
                            enabled = !isMigrating,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("stats_update_old_data_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoFixHigh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isMigrating) "Updating…" else "Update")
                        }
                    }
                }
            }
        }

        // Languages & Reactions summary
        if (stats.languageStats.isNotEmpty() || stats.reactionCounts.values.any { it > 0 }) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (stats.languageStats.isNotEmpty()) {
                            Text(
                                text = "LANGUAGES WATCHED",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                stats.languageStats.forEach { lang ->
                                    Surface(
                                        color = CineElevated,
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    ) {
                                        Text(
                                            text = "${lang.label} • ${lang.count}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }

                        val activeReactions = stats.reactionCounts.entries.filter { it.value > 0 }
                        if (activeReactions.isNotEmpty()) {
                            Text(
                                text = "QUICK REACTIONS",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                activeReactions.forEach { (reaction, count) ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(50),
                                    ) {
                                        Text(
                                            text = "${reaction.emoji} ${reaction.label} ($count)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Genre, Release Year, User Rating & Monthly Watch Analytics Charts
        item {
            GenreAnalyticsSection(
                stats = stats,
                isMigrating = isMigrating,
                onRunMigrationClick = onRunMigrationClick,
            )
        }

        // Actor / Director Stats Toggle Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ACTOR & DIRECTOR STATS",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeopleStatTab.entries.forEach { tab ->
                        FilterChip(
                            selected = peopleTab == tab,
                            onClick = { peopleTab = tab },
                            label = { Text(tab.label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (tab == PeopleStatTab.ACTORS) Icons.Rounded.Person else Icons.Rounded.Videocam,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            modifier = Modifier.testTag("stats_people_tab_${tab.name.lowercase()}"),
                        )
                    }
                }
            }
        }

        val activeList = if (peopleTab == PeopleStatTab.ACTORS) stats.topActors else stats.topDirectors
        val roleLabel = if (peopleTab == PeopleStatTab.ACTORS) "Actor" else "Director"

        if (activeList.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "No ${roleLabel.lowercase()} credits saved yet. Open title details or tap Update Old Data in Settings to sync credits.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(
                items = activeList,
                key = { "${roleLabel}_${it.name}" },
            ) { person ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onPersonClick(roleLabel, person) }
                        .testTag("person_stat_${person.name}"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = person.name.firstOrNull()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = person.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${person.count} watched ${if (person.count == 1) "title" else "titles"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                color = CineElevated,
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    text = person.count.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "View titles",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMetricTile(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = CineElevated,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonWatchedMoviesSheet(
    roleLabel: String,
    personStat: PersonStat,
    onMovieClick: (WatchedMovie) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CineCharcoal,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = roleLabel.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "You have watched ${personStat.count} ${personStat.name} ${if (personStat.count == 1) "title" else "titles"}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(personStat.movies, key = { it.uniqueKey }) { movie ->
                    Surface(
                        color = CineElevated,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                onDismiss()
                                onMovieClick(movie)
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CineSurfaceHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                val url = movie.fullPosterUrl()
                                if (url != null) {
                                    AsyncImage(
                                        model = url,
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
                            Column(modifier = Modifier.weight(1f)) {
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
                                        if (movie.languageName.isNotBlank()) append(" • ${movie.languageName}")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (movie.userRating > 0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
