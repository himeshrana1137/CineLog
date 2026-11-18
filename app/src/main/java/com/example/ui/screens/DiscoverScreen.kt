package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
import com.example.ui.components.LanguageFilterRow
import com.example.ui.components.MovieSkeletonCard
import com.example.ui.theme.CineElevated
import com.example.ui.theme.CineGold

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
    val focusManager = LocalFocusManager.current
    val mediaChipsRowState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        // Search Bar for Movies & TV Shows
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_search_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.clear_search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CineElevated,
                unfocusedContainerColor = CineElevated,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)
                .testTag("movie_search_input"),
        )

        // Media Type Filter Chips: All | Movies | TV Shows (edge contentPadding + right fade)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            LazyRow(
                state = mediaChipsRowState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
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
            }
            if (mediaChipsRowState.canScrollForward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(28.dp)
                        .height(42.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                            )
                        )
                )
            }
        }

        // Dynamic Language Filter Chips built from Discover results
        if (discoverState is DiscoverUiState.Success && languageChips.isNotEmpty()) {
            LanguageFilterRow(
                chips = languageChips,
                totalCount = discoverState.movies.size,
                selectedLanguage = selectedLanguage,
                onLanguageSelected = onLanguageSelected,
                testTagPrefix = "discover",
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }

        when (discoverState) {
            is DiscoverUiState.Loading -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnsCount),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    items(6) {
                        MovieSkeletonCard()
                    }
                }
            }

            is DiscoverUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
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
                        modifier = Modifier.fillMaxWidth(),
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
                                        "Couldn't Load Titles"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = if (discoverState.isMissingApiKey) {
                                    stringResource(R.string.tmdb_key_banner_body)
                                } else {
                                    discoverState.message
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onRetry,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("retry_discover_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Try Again")
                            }
                        }
                    }
                }
            }

            is DiscoverUiState.Success -> {
                if (filteredMovies.isEmpty()) {
                    CineEmptyState(
                        title = stringResource(R.string.empty_search_title),
                        subtitle = stringResource(R.string.empty_search_subtitle),
                        actionLabel = if (selectedLanguage != "all") "Show All Languages"
                        else if (searchQuery.isNotEmpty()) stringResource(R.string.clear_search) else null,
                        onActionClick = {
                            if (selectedLanguage != "all") onLanguageSelected("all")
                            else if (searchQuery.isNotEmpty()) onSearchQueryChange("")
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
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
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                    ) {
                        // What to Watch Today? "Surprise Me" banner
                        if (!discoverState.isSearchResult) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "WHAT TO WATCH TODAY?",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                text = if (watchlistCount > 0) {
                                                    "Pick randomly from $watchlistCount saved Watchlist titles"
                                                } else {
                                                    "Add titles to Watchlist to unlock Surprise Me"
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Button(
                                            onClick = onOpenSurpriseMe,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp),
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("discover_surprise_me_button"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Casino,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Surprise Me")
                                        }
                                    }
                                }
                            }
                        }

                        // "For You" personalized recommendations from 4-5 star watched items
                        if (!discoverState.isSearchResult && forYouState is RecommendationsUiState.Success && forYouState.items.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .testTag("for_you_recommendations_section"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                                )
                                            }
                                        },
                                    )

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                                modifier = Modifier.width(172.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // "Popular TV Shows" horizontal carousel when viewing All
                        if (popularTvShows.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .testTag("popular_tv_shows_section"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    DiscoverSectionHeader(
                                        title = "POPULAR TV SHOWS",
                                        icon = Icons.Rounded.LiveTv,
                                        iconTint = MaterialTheme.colorScheme.primary,
                                    )

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                                modifier = Modifier.width(172.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            DiscoverSectionHeader(
                                title = headerText,
                                icon = if (discoverState.isSearchResult) Icons.Rounded.Search else Icons.AutoMirrored.Rounded.TrendingUp,
                                iconTint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp),
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
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
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
