package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.TvProgressCalculator
import com.example.data.model.TvSeasonSummary
import com.example.data.model.TvWatchStatus
import com.example.data.model.WatchedMovie
import com.example.data.model.WatchedSeason
import com.example.ui.EarlierSeasonsPrompt
import com.example.ui.SeasonEpisodesLoadState
import com.example.ui.theme.CineCharcoal
import com.example.ui.theme.CineElevated
import com.example.ui.theme.CineEmerald
import com.example.ui.theme.CineEmeraldContainer
import com.example.ui.theme.CineGold
import com.example.ui.theme.CineSurfaceHigh

@Composable
fun TvSeasonEpisodeSection(
    tvId: Long,
    seasons: List<TvSeasonSummary>,
    watchedMovie: WatchedMovie?,
    watchedSeasonsMap: Map<Int, WatchedSeason>,
    episodeStates: Map<Int, SeasonEpisodesLoadState>,
    fallbackSeasonsCount: Int,
    fallbackEpisodesCount: Int,
    avgEpisodeRuntime: Int,
    isSavingProgress: Boolean,
    earlierSeasonsPrompt: EarlierSeasonsPrompt?,
    onExpandSeason: (Int) -> Unit,
    onRetrySeason: (Int) -> Unit,
    onToggleSeasonWatched: (TvSeasonSummary) -> Unit,
    onToggleEpisodeWatched: (TvSeasonSummary, Int) -> Unit,
    onMarkAllSeasonsWatched: () -> Unit,
    onResetProgress: () -> Unit,
    onConfirmEarlierSeasons: (Boolean) -> Unit,
    onDismissEarlierPrompt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSpecials by remember(tvId) { mutableStateOf(false) }
    val expandedSeasons = remember(tvId) { mutableStateMapOf<Int, Boolean>() }

    val loadedEpisodeDetails = remember(episodeStates) {
        episodeStates.mapNotNull { (sNum, state) ->
            (state as? SeasonEpisodesLoadState.Success)?.detail?.let { sNum to it }
        }.toMap()
    }

    val computedProgress = remember(
        seasons,
        watchedSeasonsMap,
        fallbackSeasonsCount,
        fallbackEpisodesCount,
        avgEpisodeRuntime,
        loadedEpisodeDetails,
    ) {
        TvProgressCalculator.compute(
            seasons = seasons,
            seasonDocs = watchedSeasonsMap,
            fallbackTotalSeasons = fallbackSeasonsCount,
            fallbackTotalEpisodes = fallbackEpisodesCount,
            fallbackAvgEpisodeRuntime = avgEpisodeRuntime,
            episodeDetailsBySeason = loadedEpisodeDetails,
        )
    }

    val effectiveStatus = when {
        watchedSeasonsMap.isNotEmpty() -> computedProgress.status
        watchedMovie != null && watchedMovie.status.isNotBlank() -> watchedMovie.resolvedTvStatus
        watchedMovie != null -> TvWatchStatus.COMPLETED
        else -> TvWatchStatus.NOT_STARTED
    }

    val progressFraction = when {
        watchedSeasonsMap.isNotEmpty() && computedProgress.totalEpisodes > 0 -> {
            (computedProgress.watchedEpisodesCount.toFloat() / computedProgress.totalEpisodes.toFloat())
                .coerceIn(0f, 1f)
        }
        watchedMovie != null -> watchedMovie.tvProgressFraction()
        else -> 0f
    }

    val progressSummaryText = when {
        watchedSeasonsMap.isNotEmpty() || computedProgress.totalSeasons > 0 -> computedProgress.progressLabel()
        else -> watchedMovie?.formatTvProgressSummary()
    }

    val nextUpText = when {
        watchedSeasonsMap.isNotEmpty() -> computedProgress.nextUpLabel()
        else -> watchedMovie?.formatNextUpLabel()
    }

    val hasSpecials = remember(seasons) { seasons.any { it.isSpecial } }
    val visibleSeasons = remember(seasons, showSpecials) {
        seasons.filter { showSpecials || !it.isSpecial }
            .sortedWith(compareBy<TvSeasonSummary> { it.isSpecial }.thenBy { it.seasonNumber })
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tv_seasons_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Progress Overview Header Card
        Surface(
            color = CineElevated,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LiveTv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "SEASONS & EPISODES",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    TvStatusPill(status = effectiveStatus)
                }

                if (!progressSummaryText.isNullOrBlank()) {
                    Text(
                        text = progressSummaryText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("tv_progress_summary_text"),
                    )
                }

                LinearProgressIndicator(
                    progress = { progressFraction },
                    color = if (effectiveStatus == TvWatchStatus.COMPLETED) {
                        CineEmerald
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = CineSurfaceHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("tv_progress_bar"),
                )

                if (!nextUpText.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("tv_next_up_badge"),
                    ) {
                        Text(
                            text = nextUpText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onMarkAllSeasonsWatched,
                        enabled = !isSavingProgress && seasons.any { !it.isSpecial && it.episodeCount > 0 },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("tv_mark_all_seasons_button"),
                    ) {
                        if (isSavingProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Mark all",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onResetProgress,
                        enabled = !isSavingProgress && (watchedSeasonsMap.isNotEmpty() || effectiveStatus != TvWatchStatus.NOT_STARTED),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("tv_reset_progress_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }

        // Show Specials toggle
        if (hasSpecials) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showSpecials = !showSpecials }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Show specials (Season 0)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(
                    checked = showSpecials,
                    onCheckedChange = { showSpecials = it },
                    modifier = Modifier.testTag("tv_show_specials_switch"),
                )
            }
        }

        // Seasons List
        if (visibleSeasons.isEmpty()) {
            Surface(
                color = CineElevated,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Season details are loading or unavailable offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleSeasons.forEach { season ->
                    val seasonDoc = watchedSeasonsMap[season.seasonNumber]
                    val isSeasonWatched = seasonDoc?.isConsideredWatched(season.episodeCount) == true
                    val watchedInSeason = if (seasonDoc != null) {
                        TvProgressCalculator.resolveSeasonWatchedCount(seasonDoc, season.episodeCount)
                    } else 0
                    val isExpanded = expandedSeasons[season.seasonNumber] == true
                    val epState = episodeStates[season.seasonNumber] ?: SeasonEpisodesLoadState.Idle

                    SeasonAccordionCard(
                        season = season,
                        isSeasonWatched = isSeasonWatched,
                        watchedEpisodesInSeason = watchedInSeason,
                        watchedEpisodeNumbers = if (isSeasonWatched && seasonDoc?.watchedEpisodes.isNullOrEmpty()) {
                            (1..season.episodeCount).toSet()
                        } else {
                            seasonDoc?.watchedEpisodes?.toSet().orEmpty()
                        },
                        isExpanded = isExpanded,
                        episodeLoadState = epState,
                        isSavingProgress = isSavingProgress,
                        onHeaderClick = {
                            if (!season.isUpcoming) {
                                val nextExpanded = !isExpanded
                                expandedSeasons[season.seasonNumber] = nextExpanded
                                if (nextExpanded) {
                                    onExpandSeason(season.seasonNumber)
                                }
                            }
                        },
                        onToggleSeasonWatched = { onToggleSeasonWatched(season) },
                        onRetryEpisodes = { onRetrySeason(season.seasonNumber) },
                        onToggleEpisode = { epNum -> onToggleEpisodeWatched(season, epNum) },
                    )
                }
            }
        }
    }

    if (earlierSeasonsPrompt != null) {
        AlertDialog(
            onDismissRequest = onDismissEarlierPrompt,
            containerColor = CineCharcoal,
            title = {
                Text(
                    text = "Mark Season 1 to ${earlierSeasonsPrompt.targetSeason.seasonNumber} as watched?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                val count = earlierSeasonsPrompt.earlierSeasonNumbers.size
                Text(
                    text = "$count earlier ${if (count == 1) "season is" else "seasons are"} currently unwatched. Would you like to mark Seasons 1–${earlierSeasonsPrompt.targetSeason.seasonNumber} as watched together?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmEarlierSeasons(true) },
                    modifier = Modifier.testTag("earlier_seasons_yes_button"),
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onConfirmEarlierSeasons(false) },
                    modifier = Modifier.testTag("earlier_seasons_only_this_button"),
                ) {
                    Text("Only this season")
                }
            },
        )
    }
}

@Composable
fun TvStatusPill(
    status: TvWatchStatus,
    modifier: Modifier = Modifier,
) {
    val (bgColor, textColor) = when (status) {
        TvWatchStatus.COMPLETED -> CineEmeraldContainer to CineEmerald
        TvWatchStatus.WATCHING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        TvWatchStatus.NOT_STARTED -> CineSurfaceHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SeasonAccordionCard(
    season: TvSeasonSummary,
    isSeasonWatched: Boolean,
    watchedEpisodesInSeason: Int,
    watchedEpisodeNumbers: Set<Int>,
    isExpanded: Boolean,
    episodeLoadState: SeasonEpisodesLoadState,
    isSavingProgress: Boolean,
    onHeaderClick: () -> Unit,
    onToggleSeasonWatched: () -> Unit,
    onRetryEpisodes: () -> Unit,
    onToggleEpisode: (Int) -> Unit,
) {
    val isDisabled = season.isUpcoming

    Surface(
        color = CineElevated,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSeasonWatched) CineEmerald.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDisabled) 0.55f else 1f)
            .testTag("season_row_${season.seasonNumber}"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isDisabled, onClick = onHeaderClick)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(62.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CineSurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    val posterUrl = season.fullPosterUrl()
                    if (posterUrl != null) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = "${season.displayTitle} poster",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.LiveTv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = season.displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isDisabled) {
                            Surface(
                                color = CineGold.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = "Upcoming",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CineGold,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    val metaParts = buildList {
                        if (season.airYear.isNotBlank()) add(season.airYear)
                        if (season.episodeCount > 0) {
                            add("${season.episodeCount} ${if (season.episodeCount == 1) "episode" else "episodes"}")
                        }
                        if (!isDisabled && watchedEpisodesInSeason > 0 && season.episodeCount > 0) {
                            add("$watchedEpisodesInSeason/${season.episodeCount} watched")
                        }
                    }.joinToString(" • ")

                    if (metaParts.isNotBlank()) {
                        Text(
                            text = metaParts,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (!isDisabled) {
                    IconButton(
                        onClick = onToggleSeasonWatched,
                        enabled = !isSavingProgress,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("season_watched_toggle_${season.seasonNumber}"),
                    ) {
                        Icon(
                            imageVector = if (isSeasonWatched) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = if (isSeasonWatched) {
                                "Mark ${season.displayTitle} unwatched"
                            } else {
                                "Mark ${season.displayTitle} watched"
                            },
                            tint = if (isSeasonWatched) CineEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse episodes" else "Expand episodes",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded && !isDisabled,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CineCharcoal.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (episodeLoadState) {
                        is SeasonEpisodesLoadState.Idle,
                        is SeasonEpisodesLoadState.Loading -> {
                            EpisodeSkeletonList()
                        }

                        is SeasonEpisodesLoadState.Error -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = episodeLoadState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = onRetryEpisodes,
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("retry_season_${season.seasonNumber}"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry")
                                }
                            }
                        }

                        is SeasonEpisodesLoadState.Success -> {
                            val episodes = episodeLoadState.detail.episodes
                            if (episodes.isEmpty()) {
                                Text(
                                    text = "No episode metadata available for this season.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                episodes.forEachIndexed { idx, ep ->
                                    val epWatched = isSeasonWatched || watchedEpisodeNumbers.contains(ep.episodeNumber)
                                    val epDisabled = ep.isUpcoming
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = !epDisabled && !isSavingProgress) {
                                                onToggleEpisode(ep.episodeNumber)
                                            }
                                            .padding(vertical = 4.dp)
                                            .alpha(if (epDisabled) 0.5f else 1f)
                                            .testTag("episode_row_${season.seasonNumber}_${ep.episodeNumber}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Surface(
                                            color = if (epWatched) CineEmeraldContainer else CineSurfaceHigh,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.width(42.dp),
                                        ) {
                                            Text(
                                                text = "E${ep.episodeNumber}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (epWatched) CineEmerald else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                                maxLines = 1,
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = ep.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val epMeta = buildList {
                                                if (ep.runtime > 0) add("${ep.runtime}m")
                                                if (ep.airDate.isNotBlank()) add(ep.airDate)
                                                if (epDisabled) add("Upcoming")
                                            }.joinToString(" • ")
                                            if (epMeta.isNotBlank()) {
                                                Text(
                                                    text = epMeta,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (epDisabled) CineGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }

                                        Checkbox(
                                            checked = epWatched,
                                            enabled = !epDisabled && !isSavingProgress,
                                            onCheckedChange = { onToggleEpisode(ep.episodeNumber) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = CineEmerald,
                                                checkmarkColor = CineCharcoal,
                                            ),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("episode_checkbox_${season.seasonNumber}_${ep.episodeNumber}"),
                                        )
                                    }
                                    if (idx < episodes.lastIndex) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
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

@Composable
private fun EpisodeSkeletonList() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CineSurfaceHigh)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(CineSurfaceHigh)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(CineSurfaceHigh)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CineSurfaceHigh)
                )
            }
        }
    }
}
