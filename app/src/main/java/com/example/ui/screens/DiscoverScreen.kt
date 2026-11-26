package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.LanguageChipCount
import com.example.data.model.MovieSummary
import com.example.data.model.WatchProvidersRegion
import com.example.data.model.WatchedMovie
import com.example.data.model.WatchlistItem
import com.example.ui.DiscoverMediaFilter
import com.example.ui.DiscoverUiState
import com.example.ui.RecommendationsUiState
import com.example.ui.components.CineEmptyState
import com.example.ui.components.DiscoverMovieCard
import com.example.ui.components.MovieSkeletonCard
import com.example.ui.theme.CineCharcoal
import com.example.ui.theme.CineElevated
import com.example.ui.theme.CineGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedMediaFilter: DiscoverMediaFilter,
    onMediaFilterSelected: (DiscoverMediaFilter) -> Unit,
    languageChips: List<LanguageChipCount>,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    discoverState: DiscoverUiState,
    filteredMovies: List<MovieSummary>,
    forYouState: RecommendationsUiState,
    watchlistCount: Int,
    watchedMap: Map<String, WatchedMovie>,
    watchlistMap: Map<String, WatchlistItem>,
    watchProvidersCache: Map<String, WatchProvidersRegion>,
    pendingToggleKeys: Set<String>,
    pendingWatchlistKeys: Set<String>,
    columnsCount: Int,
    onMovieClick: (MovieSummary) -> Unit,
    onToggleWatched: (MovieSummary) -> Unit,
    onToggleWatchlist: (MovieSummary) -> Unit,
    onAddToList: (MovieSummary) -> Unit,
    onOpenSurpriseMe: () -> Unit,
    onRefreshRecommendations: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLanguageSheet by remember { mutableStateOf(false) }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottomPadding = 84.dp + navBarBottom
    val totalResultsCount = (discoverState as? DiscoverUiState.Success)?.movies?.size ?: 0
    val selectedLanguageLabel = remember(selectedLanguage, languageChips) {
        if (selectedLanguage == "all") {
            "Language"
        } else {
            languageChips.firstOrNull {
                it.code.equals(selectedLanguage, ignoreCase = true)
            }?.label ?: selectedLanguage.uppercase()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (discoverState) {
            is DiscoverUiState.Loading -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnsCount),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = listBottomPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "discover_filters_row") {
                        DiscoverMergedFilterRow(
                            selectedMediaFilter = selectedMediaFilter,
                            onMediaFilterSelected = onMediaFilterSelected,
                            selectedLanguage = selectedLanguage,
                            selectedLanguageLabel = selectedLanguageLabel,
                            hasLanguages = languageChips.isNotEmpty(),
                            onOpenLanguagePicker = { showLanguageSheet = true },
                        )
                    }
                    items(6) {
                        MovieSkeletonCard()
                    }
                }
            }

            is DiscoverUiState.Error -> {
                var showDetails by remember(discoverState.technicalDetails) { mutableStateOf(false) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = listBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "discover_filters_row") {
                        DiscoverMergedFilterRow(
                            selectedMediaFilter = selectedMediaFilter,
                            onMediaFilterSelected = onMediaFilterSelected,
                            selectedLanguage = selectedLanguage,
                            selectedLanguageLabel = selectedLanguageLabel,
                            hasLanguages = languageChips.isNotEmpty(),
                            onOpenLanguagePicker = { showLanguageSheet = true },
                        )
                    }
                    item(key = "discover_error_card_item") {
                        Surface(
                            color = if (discoverState.isMissingApiKey) {
                                CineElevated
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            shape = MaterialTheme.shapes.large,
                            border = BorderStroke(
                                1.dp,
                                if (discoverState.isMissingApiKey) CineGold.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .testTag("discover_error_card"),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = if (discoverState.isMissingApiKey) {
                                            Icons.Rounded.Key
                                        } else {
                                            Icons.Rounded.WarningAmber
                                        },
                                        contentDescription = null,
                                        tint = if (discoverState.isMissingApiKey) CineGold else MaterialTheme.colorScheme.error,
                                    )
                                    Text(
                                        text = if (discoverState.isMissingApiKey) {
                                            stringResource(R.string.tmdb_key_banner_title)
                                        } else {
                                            stringResource(R.string.discover_error_message)
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                if (discoverState.isMissingApiKey) {
                                    Text(
                                        text = stringResource(R.string.tmdb_key_banner_body),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Button(
                                    onClick = onRetry,
                                    enabled = !discoverState.isRetrying,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("retry_discover_button"),
                                ) {
                                    if (discoverState.isRetrying) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.discover_retrying_button),
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.discover_retry_button),
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }

                                if (discoverState.technicalDetails.isNotBlank()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showDetails = !showDetails }
                                            .padding(horizontal = 4.dp)
                                            .testTag("discover_error_details_toggle"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.sync_details_toggle),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Icon(
                                            imageVector = if (showDetails) {
                                                Icons.Rounded.ExpandLess
                                            } else {
                                                Icons.Rounded.ExpandMore
                                            },
                                            contentDescription = if (showDetails) "Hide details" else "Show details",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    AnimatedVisibility(visible = showDetails) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = discoverState.technicalDetails,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .padding(12.dp)
                                                    .testTag("discover_error_details_text"),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is DiscoverUiState.Success -> {
                if (filteredMovies.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = listBottomPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item(key = "discover_filters_row") {
                            DiscoverMergedFilterRow(
                                selectedMediaFilter = selectedMediaFilter,
                                onMediaFilterSelected = onMediaFilterSelected,
                                selectedLanguage = selectedLanguage,
                                selectedLanguageLabel = selectedLanguageLabel,
                                hasLanguages = languageChips.isNotEmpty(),
                                onOpenLanguagePicker = { showLanguageSheet = true },
                            )
                        }
                        if (discoverState.isOfflineData) {
                            item(key = "discover_offline_data_banner_empty") {
                                DiscoverOfflineDataBanner(
                                    isRetrying = discoverState.isRetrying,
                                    onRetry = onRetry,
                                )
                            }
                        }
                        item {
                            CineEmptyState(
                                title = stringResource(R.string.empty_search_title),
                                subtitle = stringResource(R.string.empty_search_subtitle),
                                actionLabel = if (selectedLanguage != "all") "Show All Languages"
                                else if (searchQuery.isNotEmpty()) stringResource(R.string.clear_search) else null,
                                onActionClick = {
                                    if (selectedLanguage != "all") onLanguageSelected("all")
                                    else if (searchQuery.isNotEmpty()) onSearchQueryChange("")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                            )
                        }
                    }
                } else {
                    val trimmedQuery = searchQuery.trim()
                    val headerText = if (discoverState.isSearchResult && trimmedQuery.isNotEmpty()) {
                        "SEARCH RESULTS FOR \"${trimmedQuery.uppercase()}\""
                    } else if (discoverState.isSearchResult) {
                        stringResource(R.string.search_results_title)
                    } else {
                        when (discoverState.mediaFilter) {
                            DiscoverMediaFilter.ALL -> stringResource(R.string.trending_all_week)
                            DiscoverMediaFilter.MOVIES -> stringResource(R.string.trending_this_week)
                            DiscoverMediaFilter.TV_SHOWS -> stringResource(R.string.trending_tv_week)
                        }
                    }

                    val popularTvShows = remember(discoverState, filteredMovies) {
                        if (!discoverState.isSearchResult && discoverState.mediaFilter == DiscoverMediaFilter.ALL) {
                            filteredMovies.filter { it.isTv }.take(12)
                        } else {
                            emptyList()
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnsCount),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = listBottomPadding,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // Single scrollable merged filter row: All | Movies | TV Shows | Language ▾
                        item(span = { GridItemSpan(maxLineSpan) }, key = "discover_filters_row") {
                            DiscoverMergedFilterRow(
                                selectedMediaFilter = selectedMediaFilter,
                                onMediaFilterSelected = onMediaFilterSelected,
                                selectedLanguage = selectedLanguage,
                                selectedLanguageLabel = selectedLanguageLabel,
                                hasLanguages = languageChips.isNotEmpty(),
                                onOpenLanguagePicker = { showLanguageSheet = true },
                            )
                        }

                        if (discoverState.isOfflineData) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "discover_offline_data_banner") {
                                DiscoverOfflineDataBanner(
                                    isRetrying = discoverState.isRetrying,
                                    onRetry = onRetry,
                                )
                            }
                        }

                        // Compact single-row "What to watch today?" card (shown once at top)
                        if (!discoverState.isSearchResult) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "what_to_watch_today_card") {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Text(
                                                text = if (watchlistCount > 0) {
                                                    "What to watch today?"
                                                } else {
                                                    "Pick from Watchlist"
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = onOpenSurpriseMe,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("discover_surprise_me_button"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Casino,
                                                contentDescription = null,
                                                modifier = Modifier.size(17.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Surprise Me",
                                                maxLines = 1,
                                                softWrap = false,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // "For You" personalized recommendations (compact horizontal posters 132dp wide)
                        if (!discoverState.isSearchResult && forYouState is RecommendationsUiState.Success && forYouState.items.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "for_you_section") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("for_you_recommendations_section"),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    DiscoverSectionHeader(
                                        title = "FOR YOU",
                                        icon = Icons.Rounded.AutoAwesome,
                                        iconTint = CineGold,
                                        subtitle = if (forYouState.seedTitles.isNotEmpty()) {
                                            "Because you loved ${forYouState.seedTitles.take(2).joinToString(" & ")}"
                                        } else null,
                                        trailing = {
                                            IconButton(
                                                onClick = onRefreshRecommendations,
                                                modifier = Modifier.minimumInteractiveComponentSize(),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Refresh,
                                                    contentDescription = "Refresh recommendations",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        },
                                    )

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(end = 8.dp),
                                    ) {
                                        items(
                                            items = forYouState.items,
                                            key = { "rec_${it.uniqueKey}" },
                                        ) { rec ->
                                            val watchedEntry = watchedMap[rec.uniqueKey]
                                            val inWatchlist = watchlistMap.containsKey(rec.uniqueKey)
                                            DiscoverMovieCard(
                                                movie = rec,
                                                isWatched = watchedEntry != null,
                                                isInWatchlist = inWatchlist,
                                                isToggling = pendingToggleKeys.contains(rec.uniqueKey),
                                                isWatchlistToggling = pendingWatchlistKeys.contains(rec.uniqueKey),
                                                userRating = watchedEntry?.userRating ?: 0,
                                                providers = watchProvidersCache[rec.uniqueKey],
                                                onCardClick = { onMovieClick(rec) },
                                                onToggleWatched = { onToggleWatched(rec) },
                                                onToggleWatchlist = { onToggleWatchlist(rec) },
                                                onAddToList = { onAddToList(rec) },
                                                compact = true,
                                                modifier = Modifier.width(134.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // "Popular TV Shows" horizontal carousel (compact horizontal posters 134dp wide)
                        if (popularTvShows.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "popular_tv_section") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("popular_tv_shows_section"),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    DiscoverSectionHeader(
                                        title = "POPULAR TV SHOWS",
                                        icon = Icons.Rounded.LiveTv,
                                        iconTint = MaterialTheme.colorScheme.primary,
                                    )

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(end = 8.dp),
                                    ) {
                                        items(
                                            items = popularTvShows,
                                            key = { "pop_tv_${it.uniqueKey}" },
                                        ) { show ->
                                            val watchedEntry = watchedMap[show.uniqueKey]
                                            val inWatchlist = watchlistMap.containsKey(show.uniqueKey)
                                            DiscoverMovieCard(
                                                movie = show,
                                                isWatched = watchedEntry != null,
                                                isInWatchlist = inWatchlist,
                                                isToggling = pendingToggleKeys.contains(show.uniqueKey),
                                                isWatchlistToggling = pendingWatchlistKeys.contains(show.uniqueKey),
                                                userRating = watchedEntry?.userRating ?: 0,
                                                providers = watchProvidersCache[show.uniqueKey],
                                                onCardClick = { onMovieClick(show) },
                                                onToggleWatched = { onToggleWatched(show) },
                                                onToggleWatchlist = { onToggleWatchlist(show) },
                                                onAddToList = { onAddToList(show) },
                                                compact = true,
                                                modifier = Modifier.width(134.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }, key = "trending_header") {
                            DiscoverSectionHeader(
                                title = headerText,
                                icon = if (discoverState.isSearchResult) Icons.Rounded.Search else Icons.AutoMirrored.Rounded.TrendingUp,
                                iconTint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }

                        items(
                            items = filteredMovies,
                            key = { it.uniqueKey },
                        ) { movie ->
                            val watchedEntry = watchedMap[movie.uniqueKey]
                            val inWatchlist = watchlistMap.containsKey(movie.uniqueKey)
                            DiscoverMovieCard(
                                movie = movie,
                                isWatched = watchedEntry != null,
                                isInWatchlist = inWatchlist,
                                isToggling = pendingToggleKeys.contains(movie.uniqueKey),
                                isWatchlistToggling = pendingWatchlistKeys.contains(movie.uniqueKey),
                                userRating = watchedEntry?.userRating ?: 0,
                                providers = watchProvidersCache[movie.uniqueKey],
                                onCardClick = { onMovieClick(movie) },
                                onToggleWatched = { onToggleWatched(movie) },
                                onToggleWatchlist = { onToggleWatchlist(movie) },
                                onAddToList = { onAddToList(movie) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLanguageSheet) {
        DiscoverLanguagePickerSheet(
            languageChips = languageChips,
            totalCount = totalResultsCount,
            selectedLanguage = selectedLanguage,
            onLanguageSelected = { code ->
                onLanguageSelected(code)
                showLanguageSheet = false
            },
            onDismiss = { showLanguageSheet = false },
        )
    }
}

@Composable
private fun DiscoverMergedFilterRow(
    selectedMediaFilter: DiscoverMediaFilter,
    onMediaFilterSelected: (DiscoverMediaFilter) -> Unit,
    selectedLanguage: String,
    selectedLanguageLabel: String,
    hasLanguages: Boolean,
    onOpenLanguagePicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowState = rememberLazyListState()
    Box(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(end = 16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(DiscoverMediaFilter.entries, key = { it.name }) { filter ->
                FilterChip(
                    selected = selectedMediaFilter == filter,
                    onClick = { onMediaFilterSelected(filter) },
                    label = {
                        Text(
                            text = filter.label,
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                    leadingIcon = when (filter) {
                        DiscoverMediaFilter.ALL -> null
                        DiscoverMediaFilter.MOVIES -> ({
                            Icon(
                                imageVector = Icons.Rounded.Movie,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        })
                        DiscoverMediaFilter.TV_SHOWS -> ({
                            Icon(
                                imageVector = Icons.Rounded.LiveTv,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        })
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.testTag("discover_filter_${filter.name.lowercase()}"),
                )
            }

            if (hasLanguages || selectedLanguage != "all") {
                item(key = "discover_language_chip") {
                    val isLangActive = selectedLanguage != "all"
                    FilterChip(
                        selected = isLangActive,
                        onClick = onOpenLanguagePicker,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = {
                            Text(
                                text = selectedLanguageLabel,
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = "Pick language",
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.testTag("discover_language_chip"),
                    )
                }
            }
        }

        if (rowState.canScrollForward) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(24.dp)
                    .height(40.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                        )
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverLanguagePickerSheet(
    languageChips: List<LanguageChipCount>,
    totalCount: Int,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CineCharcoal,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("discover_language_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Filter by Language",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.close),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "lang_all") {
                    val isSelected = selectedLanguage == "all"
                    LanguagePickerRowItem(
                        label = "All Languages",
                        count = totalCount,
                        isSelected = isSelected,
                        onClick = { onLanguageSelected("all") },
                        testTag = "discover_lang_all",
                    )
                }
                items(languageChips, key = { it.code }) { item ->
                    val isSelected = selectedLanguage.equals(item.code, ignoreCase = true)
                    LanguagePickerRowItem(
                        label = item.label,
                        count = item.count,
                        isSelected = isSelected,
                        onClick = {
                            onLanguageSelected(if (isSelected) "all" else item.code)
                        },
                        testTag = "discover_lang_${item.code}",
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguagePickerRowItem(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else CineElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (count > 0) "$label ($count)" else label,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun DiscoverSectionHeader(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun DiscoverOfflineDataBanner(
    isRetrying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = CineElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CineGold.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("discover_offline_data_banner"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = CineGold,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.discover_offline_data_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = CineGold,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = onRetry,
                enabled = !isRetrying,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("discover_offline_retry_button"),
            ) {
                if (isRetrying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.discover_retrying_button),
                        maxLines = 1,
                        softWrap = false,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.discover_retry_button),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

