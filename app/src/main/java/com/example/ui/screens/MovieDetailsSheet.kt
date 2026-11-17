package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.CastMember
import com.example.data.model.MediaType
import com.example.data.model.QuickReaction
import com.example.data.model.TmdbImageItem
import com.example.data.model.WatchProviderInfo
import com.example.data.model.WatchProvidersRegion
import com.example.data.model.WatchedMovie
import com.example.ui.MovieModalState
import com.example.ui.components.InteractiveStarRatingBar
import com.example.ui.components.YouTubeTrailerPlayer
import com.example.ui.theme.CineCharcoal
import com.example.ui.theme.CineElevated
import com.example.ui.theme.CineEmerald
import com.example.ui.theme.CineEmeraldContainer
import com.example.ui.theme.CineGold
import com.example.ui.theme.CineObsidian
import com.example.ui.theme.CineSurfaceHigh
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MovieDetailsSheet(
    modalState: MovieModalState,
    watchedMovie: WatchedMovie?,
    isInWatchlist: Boolean,
    isTogglingWatched: Boolean,
    isTogglingWatchlist: Boolean,
    onDismiss: () -> Unit,
    onToggleWatched: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onAddToList: () -> Unit,
    onLogRewatch: (WatchedMovie) -> Unit,
    onRemoveWatchDate: (WatchedMovie, Int) -> Unit,
    onShareCard: ((WatchedMovie) -> Unit)?,
    onOpenImageGalleryItem: (String, TmdbImageItem) -> Unit,
    onSaveReview: (userRating: Int, note: String, watchedSeason: Int, reaction: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val summary = modalState.summary
    val details = modalState.details
    val isWatched = watchedMovie != null
    val isTv = modalState.mediaType == MediaType.TV

    var draftRating by remember(modalState.uniqueKey, watchedMovie?.userRating) {
        mutableIntStateOf(watchedMovie?.userRating ?: 0)
    }
    var draftNote by remember(modalState.uniqueKey, watchedMovie?.note) {
        mutableStateOf(watchedMovie?.note.orEmpty())
    }
    var draftSeason by remember(modalState.uniqueKey, watchedMovie?.watchedSeason) {
        mutableIntStateOf(watchedMovie?.watchedSeason ?: 0)
    }
    var draftReaction by remember(modalState.uniqueKey, watchedMovie?.reaction) {
        mutableStateOf(watchedMovie?.reaction.orEmpty())
    }

    LaunchedEffect(
        watchedMovie?.userRating,
        watchedMovie?.note,
        watchedMovie?.watchedSeason,
        watchedMovie?.reaction,
    ) {
        if (watchedMovie != null) {
            draftRating = watchedMovie.userRating
            draftNote = watchedMovie.note
            draftSeason = watchedMovie.watchedSeason
            draftReaction = watchedMovie.reaction
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CineCharcoal,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // Hero Backdrop / Poster header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(CineElevated),
            ) {
                val heroUrl = details?.fullBackdropUrl()
                    ?: summary.fullBackdropUrl()
                    ?: summary.fullPosterUrl()
                if (heroUrl != null) {
                    AsyncImage(
                        model = heroUrl,
                        contentDescription = "${summary.title} backdrop",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    CineObsidian.copy(alpha = 0.25f),
                                    CineCharcoal.copy(alpha = 0.78f),
                                    CineCharcoal,
                                )
                            )
                        )
                )

                // TV Series pill (top-left)
                if (isTv) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LiveTv,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "TV SERIES",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Close button
                Surface(
                    color = CineObsidian.copy(alpha = 0.7f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp),
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_details_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White,
                        )
                    }
                }

                // Title & tagline pinned at bottom of hero
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!details?.tagline.isNullOrBlank()) {
                        Text(
                            text = details!!.tagline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (modalState.isLoadingDetails) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = CineElevated,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Year • Seasons/Episodes • Runtime • Language • TMDB Score
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        color = CineElevated,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = summary.releaseYear,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }

                    val languageName = details?.languageName?.takeIf { it.isNotBlank() }
                        ?: summary.languageName.takeIf { it.isNotBlank() }
                        ?: watchedMovie?.languageName.orEmpty()
                    if (languageName.isNotBlank()) {
                        Surface(
                            color = CineElevated,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = languageName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    val totalSeasons = details?.numberOfSeasons ?: watchedMovie?.numberOfSeasons ?: 0
                    val totalEpisodes = details?.numberOfEpisodes ?: watchedMovie?.numberOfEpisodes ?: 0
                    if (isTv && totalSeasons > 0) {
                        Surface(
                            color = CineElevated,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            val epSuffix = if (totalEpisodes > 0) " • $totalEpisodes Eps" else ""
                            Text(
                                text = "$totalSeasons ${if (totalSeasons == 1) "Season" else "Seasons"}$epSuffix",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }

                    val runtime = details?.runtime ?: watchedMovie?.runtime ?: 0
                    if (runtime > 0) {
                        val hours = runtime / 60
                        val mins = runtime % 60
                        val runtimeText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                        Surface(
                            color = CineElevated,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = if (isTv) "$runtimeText / ep" else runtimeText,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Surface(
                        color = CineGold.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = CineGold,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.1f", summary.rating)} TMDB",
                                style = MaterialTheme.typography.labelLarge,
                                color = CineGold,
                            )
                        }
                    }
                }

                // Directors / Creators line
                val directors = details?.directors?.takeIf { it.isNotEmpty() }
                    ?: watchedMovie?.directors.orEmpty()
                if (directors.isNotEmpty()) {
                    Text(
                        text = "${if (isTv) "Created by" else "Directed by"} ${directors.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Genre pills
                val genres = details?.genres?.takeIf { it.isNotEmpty() }
                    ?: watchedMovie?.genres.orEmpty()
                if (genres.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        genres.forEach { genre ->
                            Surface(
                                color = CineSurfaceHigh,
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                // Mark as Watched / Watchlist / Share Card Actions
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isWatched) {
                            Surface(
                                color = CineEmeraldContainer,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CineEmerald.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = CineEmerald,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "In Watched",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CineEmerald,
                                    )
                                }
                            }
                            if (onShareCard != null) {
                                Button(
                                    onClick = { onShareCard(watchedMovie!!) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("modal_share_card_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share Card")
                                }
                            }
                            OutlinedButton(
                                onClick = onToggleWatched,
                                enabled = !isTogglingWatched,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("modal_remove_watched_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.remove_from_watched),
                                )
                            }
                        } else {
                            Button(
                                onClick = onToggleWatched,
                                enabled = !isTogglingWatched,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("modal_mark_watched_button"),
                            ) {
                                if (isTogglingWatched) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.mark_as_watched))
                                }
                            }

                            OutlinedButton(
                                onClick = onToggleWatchlist,
                                enabled = !isTogglingWatchlist,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("modal_watchlist_button"),
                            ) {
                                if (isTogglingWatchlist) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isInWatchlist) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isInWatchlist) stringResource(R.string.in_watchlist)
                                        else "Watchlist"
                                    )
                                }
                            }
                        }
                    }

                    // Add to Custom List button & Rewatch Counter when watched
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onAddToList,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("modal_add_to_list_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add to List")
                        }

                        if (watchedMovie != null) {
                            Button(
                                onClick = { onLogRewatch(watchedMovie) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CineElevated,
                                    contentColor = CineGold,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("modal_watched_again_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Repeat,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Watched again (${watchedMovie.watchCount}×)")
                            }
                        }
                    }

                    // Watch Diary date chips when watched
                    if (watchedMovie != null && watchedMovie.resolvedWatchDates.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Watch dates:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                            val dates = watchedMovie.resolvedWatchDates
                            dates.forEachIndexed { idx, iso ->
                                Surface(
                                    color = CineElevated,
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            start = 10.dp,
                                            top = 4.dp,
                                            end = if (dates.size > 1) 4.dp else 10.dp,
                                            bottom = 4.dp,
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = iso,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        if (dates.size > 1) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Remove watch date $iso",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { onRemoveWatchDate(watchedMovie, idx) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Personal Rating, Quick Emoji Reaction, Season Watched (for TV) & Watch Note card
                Surface(
                    color = CineElevated,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isTv) {
                            val availableSeasons = maxOf(
                                details?.numberOfSeasons ?: 0,
                                watchedMovie?.numberOfSeasons ?: 0,
                                draftSeason,
                            ).coerceIn(0, 50)
                            Text(
                                text = "Season Watched",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = draftSeason == 0,
                                    onClick = { draftSeason = 0 },
                                    label = { Text("All / Full Series") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                    modifier = Modifier.testTag("season_chip_all"),
                                )
                                val chipCount = if (availableSeasons > 0) availableSeasons else 5
                                for (season in 1..chipCount) {
                                    FilterChip(
                                        selected = draftSeason == season,
                                        onClick = { draftSeason = season },
                                        label = { Text("S$season") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                        modifier = Modifier.testTag("season_chip_$season"),
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.your_rating_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            InteractiveStarRatingBar(
                                rating = draftRating,
                                onRatingSelected = { draftRating = it },
                            )
                            Text(
                                text = if (draftRating > 0) "$draftRating / 5" else "Unrated",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (draftRating > 0) CineGold else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Quick Emoji Reactions selector
                        Text(
                            text = "Quick Reaction",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            QuickReaction.entries.forEach { reaction ->
                                val selected = draftReaction == reaction.key
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        draftReaction = if (selected) "" else reaction.key
                                    },
                                    label = { Text("${reaction.emoji} ${reaction.label}") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                    modifier = Modifier.testTag("modal_reaction_${reaction.key}"),
                                )
                            }
                        }

                        OutlinedTextField(
                            value = draftNote,
                            onValueChange = { if (it.length <= 1000) draftNote = it },
                            label = { Text(stringResource(R.string.your_note_label)) },
                            placeholder = { Text(stringResource(R.string.your_note_placeholder)) },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CineCharcoal,
                                unfocusedContainerColor = CineCharcoal,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("personal_note_input"),
                        )

                        Button(
                            onClick = {
                                onSaveReview(draftRating, draftNote, draftSeason, draftReaction)
                            },
                            enabled = !modalState.isSavingReview,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_review_button"),
                        ) {
                            if (modalState.isSavingReview) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.saving_review))
                            } else {
                                Text(
                                    text = if (isWatched) {
                                        stringResource(R.string.save_review)
                                    } else {
                                        "Mark Watched & Save Review"
                                    },
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                // Lazy-loaded YouTube Official Trailer
                val trailer = details?.trailerVideo
                if (trailer != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    YouTubeTrailerPlayer(trailer = trailer)
                }

                // India OTT Watch Providers Section
                val providersIN = details?.watchProvidersIN
                if (providersIN != null && !providersIN.isEmpty) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    IndiaWatchProvidersSection(providersIN = providersIN)
                }

                // Synopsis
                val overview = details?.overview?.takeIf { it.isNotBlank() } ?: summary.overview
                if (overview.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.overview_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        )
                    }
                }

                // High-Quality Backdrops & Posters Gallery (Wallpaper / Download)
                val images = details?.images
                if (images != null && !images.isEmpty) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "BACKDROPS & POSTERS GALLERY",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = "Tap any artwork to download original quality or crop a 9:16 phone wallpaper.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val galleryItems = (images.backdrops + images.posters).distinctBy { it.filePath }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(end = 8.dp),
                        ) {
                            items(galleryItems, key = { it.filePath }) { img ->
                                Box(
                                    modifier = Modifier
                                        .width(if (img.isBackdrop) 200.dp else 104.dp)
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CineElevated)
                                        .clickable { onOpenImageGalleryItem(summary.title, img) }
                                        .testTag("gallery_image_${img.filePath}"),
                                ) {
                                    AsyncImage(
                                        model = img.previewUrl(),
                                        contentDescription = "${summary.title} artwork",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Surface(
                                        color = CineObsidian.copy(alpha = 0.72f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp),
                                    ) {
                                        Text(
                                            text = if (img.isBackdrop) "Backdrop" else "Poster",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Top Cast
                val cast = details?.cast.orEmpty()
                if (cast.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.cast_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(end = 8.dp),
                        ) {
                            items(cast, key = { it.id }) { member ->
                                CastChipCard(member = member)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IndiaWatchProvidersSection(providersIN: WatchProvidersRegion) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("india_watch_providers_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "WHERE TO WATCH (INDIA)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (providersIN.flatrate.isNotEmpty()) {
            ProviderCategoryRow(label = "Stream", providers = providersIN.flatrate)
        }
        if (providersIN.rent.isNotEmpty()) {
            ProviderCategoryRow(label = "Rent", providers = providersIN.rent)
        }
        if (providersIN.buy.isNotEmpty()) {
            ProviderCategoryRow(label = "Buy", providers = providersIN.buy)
        }

        Text(
            text = stringResource(R.string.justwatch_attribution),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderCategoryRow(
    label: String,
    providers: List<WatchProviderInfo>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            providers.forEach { provider ->
                Surface(
                    color = CineElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val logoUrl = provider.fullLogoUrl()
                        if (logoUrl != null) {
                            AsyncImage(
                                model = logoUrl,
                                contentDescription = provider.providerName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                            )
                        }
                        Text(
                            text = provider.providerName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CastChipCard(member: CastMember) {
    Column(
        modifier = Modifier.width(84.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(CineElevated),
            contentAlignment = Alignment.Center,
        ) {
            val url = member.fullProfileUrl()
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            text = member.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (member.character.isNotBlank()) {
            Text(
                text = member.character,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
