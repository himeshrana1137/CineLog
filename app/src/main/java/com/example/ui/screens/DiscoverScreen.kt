package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.MovieSummary
import com.example.data.model.WatchedMovie
import com.example.ui.DiscoverMediaFilter
import com.example.ui.DiscoverUiState
import com.example.ui.components.CineEmptyState
import com.example.ui.components.DiscoverMovieCard
import com.example.ui.components.MovieSkeletonCard
import com.example.ui.theme.CineElevated
import com.example.ui.theme.CineGold

@Composable
fun DiscoverScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedMediaFilter: DiscoverMediaFilter,
    onMediaFilterSelected: (DiscoverMediaFilter) -> Unit,
    discoverState: DiscoverUiState,
    watchedMap: Map<String, WatchedMovie>,
    pendingToggleKeys: Set<String>,
    columnsCount: Int,
    onMovieClick: (MovieSummary) -> Unit,
    onToggleWatched: (MovieSummary) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier.fillMaxSize()) {
        // Persistent Search Bar for Movies & TV Shows
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

        // Media Type Filter Chips: All | Movies | TV Shows
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DiscoverMediaFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedMediaFilter == filter,
                    onClick = { onMediaFilterSelected(filter) },
                    label = { Text(filter.label) },
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

        when (discoverState) {
            is DiscoverUiState.Loading -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnsCount),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
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
                if (discoverState.movies.isEmpty()) {
                    CineEmptyState(
                        title = stringResource(R.string.empty_search_title),
                        subtitle = stringResource(R.string.empty_search_subtitle),
                        actionLabel = if (searchQuery.isNotEmpty()) stringResource(R.string.clear_search) else null,
                        onActionClick = if (searchQuery.isNotEmpty()) ({ onSearchQueryChange("") }) else null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val headerText = if (discoverState.isSearchResult) {
                        stringResource(R.string.search_results_title)
                    } else {
                        when (discoverState.mediaFilter) {
                            DiscoverMediaFilter.ALL -> stringResource(R.string.trending_all_week)
                            DiscoverMediaFilter.MOVIES -> stringResource(R.string.trending_this_week)
                            DiscoverMediaFilter.TV_SHOWS -> stringResource(R.string.trending_tv_week)
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnsCount),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (discoverState.isSearchResult) Icons.Rounded.Search else Icons.AutoMirrored.Rounded.TrendingUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = headerText,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }

                        items(
                            items = discoverState.movies,
                            key = { it.uniqueKey },
                        ) { movie ->
                            val watchedEntry = watchedMap[movie.uniqueKey]
                            DiscoverMovieCard(
                                movie = movie,
                                isWatched = watchedEntry != null,
                                isToggling = pendingToggleKeys.contains(movie.uniqueKey),
                                userRating = watchedEntry?.userRating ?: 0,
                                onCardClick = { onMovieClick(movie) },
                                onToggleWatched = { onToggleWatched(movie) },
                            )
                        }
                    }
                }
            }
        }
    }
}
