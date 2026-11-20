package com.example.ui.screens

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
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.WatchlistItem
import com.example.ui.DiscoverMediaFilter
import com.example.ui.ListsSubTab
import com.example.ui.WatchlistUiState
import com.example.ui.components.CineEmptyState
import com.example.ui.components.LibrarySyncErrorState
import com.example.ui.components.MovieSkeletonCard
import com.example.ui.theme.CineElevated
import com.example.ui.theme.CineGold
import com.example.ui.theme.CineSurfaceHigh
import java.util.Locale

@Composable
internal fun ListsSubTabsRow(
    listsSubTab: ListsSubTab,
    watchlistCount: Int,
    customListsCount: Int,
    onSelectListsSubTab: (ListsSubTab) -> Unit,
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
            items(ListsSubTab.entries, key = { it.name }) { tab ->
                val countSuffix = when (tab) {
                    ListsSubTab.WATCHLIST -> if (watchlistCount > 0) " ($watchlistCount)" else ""
                    ListsSubTab.CUSTOM_LISTS -> if (customListsCount > 0) " ($customListsCount)" else ""
                }
                FilterChip(
                    selected = listsSubTab == tab,
                    onClick = { onSelectListsSubTab(tab) },
                    label = {
                        Text(
                            text = "${tab.label}$countSuffix",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.testTag("lists_subtab_${tab.name.lowercase()}"),
                )
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

@Composable
fun WatchlistScreen(
    watchlistState: WatchlistUiState,
    listsSubTab: ListsSubTab,
    watchlistCount: Int,
    customListsCount: Int,
    onSelectListsSubTab: (ListsSubTab) -> Unit,
    pendingToggleKeys: Set<String>,
    columnsCount: Int,
    onItemClick: (WatchlistItem) -> Unit,
    onMoveToWatched: (WatchlistItem) -> Unit,
    onAddToList: (WatchlistItem) -> Unit,
    onSurpriseMeClick: () -> Unit,
    onRemoveClick: (WatchlistItem) -> Unit,
    onExploreClick: () -> Unit,
    isSyncing: Boolean = false,
    onSyncNow: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(DiscoverMediaFilter.ALL) }
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottomPadding = 84.dp + navBarBottom

    Box(modifier = modifier.fillMaxSize()) {
        when (watchlistState) {
            is WatchlistUiState.Loading -> {
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
                    item(span = { GridItemSpan(maxLineSpan) }, key = "lists_subtabs_row") {
                        ListsSubTabsRow(
                            listsSubTab = listsSubTab,
                            watchlistCount = watchlistCount,
                            customListsCount = customListsCount,
                            onSelectListsSubTab = onSelectListsSubTab,
                        )
                    }
                    items(4) { MovieSkeletonCard() }
                }
            }

            is WatchlistUiState.Error -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = listBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "lists_subtabs_row") {
                        ListsSubTabsRow(
                            listsSubTab = listsSubTab,
                            watchlistCount = watchlistCount,
                            customListsCount = customListsCount,
                            onSelectListsSubTab = onSelectListsSubTab,
                        )
                    }
                    item {
                        LibrarySyncErrorState(
                            technicalDetails = watchlistState.message,
                            isSyncing = isSyncing,
                            onSyncNow = onSyncNow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        )
                    }
                }
            }

            is WatchlistUiState.Success -> {
                if (watchlistState.items.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = listBottomPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "lists_subtabs_row") {
                            ListsSubTabsRow(
                                listsSubTab = listsSubTab,
                                watchlistCount = watchlistCount,
                                customListsCount = customListsCount,
                                onSelectListsSubTab = onSelectListsSubTab,
                            )
                        }
                        item {
                            CineEmptyState(
                                title = stringResource(R.string.empty_watchlist_title),
                                subtitle = stringResource(R.string.empty_watchlist_subtitle),
                                actionLabel = "Browse Discover",
                                onActionClick = onExploreClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                            )
                        }
                    }
                } else {
                    val filteredItems = watchlistState.items.filter { item ->
                        when (filter) {
                            DiscoverMediaFilter.ALL -> true
                            DiscoverMediaFilter.MOVIES -> !item.isTv
                            DiscoverMediaFilter.TV_SHOWS -> item.isTv
                        }
                    }

                    val gridCols = if (columnsCount >= 3) 2 else 1
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridCols),
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
                        item(span = { GridItemSpan(maxLineSpan) }, key = "lists_subtabs_row") {
                            ListsSubTabsRow(
                                listsSubTab = listsSubTab,
                                watchlistCount = watchlistCount,
                                customListsCount = customListsCount,
                                onSelectListsSubTab = onSelectListsSubTab,
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }, key = "watchlist_summary_header") {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "WATCH LATER QUEUE",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = "${watchlistState.items.size} Saved ${if (watchlistState.items.size == 1) "Title" else "Titles"}",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Button(
                                        onClick = onSurpriseMeClick,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("watchlist_surprise_me_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Casino,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Surprise Me", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }, key = "watchlist_media_filters") {
                            val filterRowState = rememberLazyListState()
                            Box(modifier = Modifier.fillMaxWidth()) {
                                LazyRow(
                                    state = filterRowState,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(end = 16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    items(DiscoverMediaFilter.entries, key = { it.name }) { option ->
                                        FilterChip(
                                            selected = filter == option,
                                            onClick = { filter = option },
                                            label = {
                                                Text(
                                                    text = option.label,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            ),
                                            modifier = Modifier.testTag("watchlist_filter_${option.name.lowercase()}"),
                                        )
                                    }
                                }
                                if (filterRowState.canScrollForward) {
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

                        if (filteredItems.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "watchlist_empty_filtered") {
                                CineEmptyState(
                                    title = "No matching saved titles",
                                    subtitle = "Switch back to All to view your full Watchlist.",
                                    actionLabel = "Show All",
                                    onActionClick = { filter = DiscoverMediaFilter.ALL },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                )
                            }
                        } else {
                            items(
                                items = filteredItems,
                                key = { it.uniqueKey },
                            ) { item ->
                                WatchlistCard(
                                    item = item,
                                    isMoving = pendingToggleKeys.contains(item.uniqueKey),
                                    onClick = { onItemClick(item) },
                                    onMoveToWatched = { onMoveToWatched(item) },
                                    onAddToList = { onAddToList(item) },
                                    onRemoveClick = { onRemoveClick(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistCard(
    item: WatchlistItem,
    isMoving: Boolean,
    onClick: () -> Unit,
    onMoveToWatched: () -> Unit,
    onAddToList: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("watchlist_card_${item.uniqueKey}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .height(124.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CineElevated),
                contentAlignment = Alignment.Center,
            ) {
                val posterUrl = item.fullPosterUrl()
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = "${item.title} poster",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = if (item.isTv) Icons.Rounded.LiveTv else Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (item.isTv) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp),
                    ) {
                        Text(
                            text = "TV",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = CineSurfaceHigh,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = CineGold,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f", item.rating),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                val meta = buildString {
                    append(item.releaseYear)
                    if (item.languageName.isNotBlank()) append(" • ${item.languageName}")
                    if (item.runtime > 0) append(" • ${item.runtime}m")
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (item.overview.isNotBlank()) {
                    Text(
                        text = item.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onMoveToWatched,
                        enabled = !isMoving,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("move_to_watched_${item.uniqueKey}"),
                    ) {
                        if (isMoving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.mark_as_watched))
                        }
                    }

                    IconButton(
                        onClick = onAddToList,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("watchlist_add_to_list_${item.uniqueKey}"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = "Add to custom list",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(
                        onClick = onRemoveClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("remove_watchlist_${item.uniqueKey}"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Remove from watchlist",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
