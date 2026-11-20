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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.LanguageChipCount
import com.example.data.model.QuickReaction
import com.example.data.model.TvWatchStatus
import com.example.data.model.WatchedMovie
import com.example.ui.WatchedFilterOption
import com.example.ui.WatchedSortOption
import com.example.ui.WatchedSubTab
import com.example.ui.WatchedUiState
import com.example.ui.components.CineEmptyState
import com.example.ui.components.LanguageFilterRow
import com.example.ui.components.LibrarySyncErrorState
import com.example.ui.components.MovieSkeletonCard
import com.example.ui.components.TvStatusPill
import com.example.ui.theme.CineCharcoal
import com.example.ui.theme.CineElevated
import com.example.ui.theme.CineEmerald
import com.example.ui.theme.CineGold
import com.example.ui.theme.CineSurfaceHigh
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchedScreen(
    watchedSubTab: WatchedSubTab,
    diaryMonthYm: String,
    diarySelectedDate: String,
    watchDiaryByDate: Map<String, List<WatchedMovie>>,
    watchedState: WatchedUiState,
    filteredMovies: List<WatchedMovie>,
    languageChips: List<LanguageChipCount>,
    selectedLanguage: String,
    selectedReaction: String,
    sortOption: WatchedSortOption,
    filterOption: WatchedFilterOption,
    watchedSearchQuery: String,
    columnsCount: Int,
    onSelectSubTab: (WatchedSubTab) -> Unit,
    onChangeDiaryMonth: (Int) -> Unit,
    onSelectDiaryDate: (String) -> Unit,
    onSortSelected: (WatchedSortOption) -> Unit,
    onFilterSelected: (WatchedFilterOption) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onReactionSelected: (String) -> Unit,
    onWatchedSearchQueryChange: (String) -> Unit,
    onResetFilters: () -> Unit,
    onMovieClick: (WatchedMovie) -> Unit,
    onLogRewatch: (WatchedMovie) -> Unit,
    onQuickReact: (WatchedMovie, String) -> Unit,
    onAddToList: (WatchedMovie) -> Unit,
    onShareCardClick: (WatchedMovie) -> Unit,
    onRemoveClick: (WatchedMovie) -> Unit,
    onExploreClick: () -> Unit,
    isSyncing: Boolean = false,
    onSyncNow: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showFiltersSheet by remember { mutableStateOf(false) }

    val activeFilterCount = remember(sortOption, filterOption, selectedReaction, selectedLanguage) {
        var count = 0
        if (sortOption != WatchedSortOption.RECENT) count++
        if (filterOption != WatchedFilterOption.ALL) count++
        if (selectedReaction.isNotBlank()) count++
        if (selectedLanguage != "all") count++
        count
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottomPadding = 84.dp + navBarBottom
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = onSyncNow,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isSyncing,
                containerColor = CineElevated,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("watched_pull_to_refresh"),
    ) {
        when (watchedState) {
            is WatchedUiState.Loading -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnsCount),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = listBottomPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(4) {
                        MovieSkeletonCard()
                    }
                }
            }

            is WatchedUiState.Error -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = listBottomPadding),
                ) {
                    LibrarySyncErrorState(
                        technicalDetails = watchedState.message,
                        isSyncing = isSyncing,
                        onSyncNow = onSyncNow,
                    )
                }
            }

            is WatchedUiState.Success -> {
                if (watchedState.movies.isEmpty()) {
                    CineEmptyState(
                        title = stringResource(R.string.empty_watched_title),
                        subtitle = stringResource(R.string.empty_watched_subtitle),
                        actionLabel = "Discover Movies & TV",
                        onActionClick = onExploreClick,
                        useReelArtwork = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val moviesCount = watchedState.movies.count { !it.isTv }
                    val tvCount = watchedState.movies.count { it.isTv }
                    val rated = watchedState.movies.filter { it.userRating > 0 }
                    val avgPersonal = if (rated.isNotEmpty()) {
                        String.format(Locale.US, "%.1f", rated.map { it.userRating }.average())
                    } else "—"

                    if (watchedSubTab == WatchedSubTab.DIARY) {
                        WatchDiaryView(
                            monthYm = diaryMonthYm,
                            selectedDate = diarySelectedDate,
                            diaryByDate = watchDiaryByDate,
                            onChangeMonth = onChangeDiaryMonth,
                            onSelectDate = onSelectDiaryDate,
                            onMovieClick = onMovieClick,
                            onLogRewatch = onLogRewatch,
                            headerContent = {
                                item(key = "diary_subtabs_row") {
                                    WatchedSubTabsAndFiltersRow(
                                        watchedSubTab = watchedSubTab,
                                        activeFilterCount = activeFilterCount,
                                        showFiltersButton = false,
                                        onSelectSubTab = onSelectSubTab,
                                        onOpenFilters = { showFiltersSheet = true },
                                    )
                                }
                                item(key = "diary_summary_header") {
                                    WatchedSummaryHeaderCard(
                                        totalWatched = watchedState.movies.size,
                                        moviesCount = moviesCount,
                                        tvCount = tvCount,
                                        avgPersonal = avgPersonal,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
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
                            // Sub-tabs + Filters button row inside scrollable list
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "watched_subtabs_row",
                            ) {
                                WatchedSubTabsAndFiltersRow(
                                    watchedSubTab = watchedSubTab,
                                    activeFilterCount = activeFilterCount,
                                    showFiltersButton = true,
                                    onSelectSubTab = onSelectSubTab,
                                    onOpenFilters = { showFiltersSheet = true },
                                )
                            }

                            // Scrollable Summary Header (scrolls away cleanly with the list)
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "watched_summary_header",
                            ) {
                                WatchedSummaryHeaderCard(
                                    totalWatched = watchedState.movies.size,
                                    moviesCount = moviesCount,
                                    tvCount = tvCount,
                                    avgPersonal = avgPersonal,
                                )
                            }

                            // Active top-bar search query banner if user is filtering by text
                            if (watchedSearchQuery.isNotBlank()) {
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "watched_active_search_bar",
                                ) {
                                    Surface(
                                        color = CineElevated,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Search,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Text(
                                                    text = "Search: \"$watchedSearchQuery\" (${filteredMovies.size})",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            TextButton(
                                                onClick = { onWatchedSearchQueryChange("") },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(36.dp),
                                            ) {
                                                Text("Clear", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Compact active filter indicator bar when filters are applied
                            if (activeFilterCount > 0) {
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "watched_active_filters_bar",
                                ) {
                                    Surface(
                                        color = CineElevated,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            val parts = buildList {
                                                if (filterOption != WatchedFilterOption.ALL) add(filterOption.label)
                                                if (sortOption != WatchedSortOption.RECENT) add("Sort: ${sortOption.label}")
                                                if (selectedReaction.isNotBlank()) {
                                                    QuickReaction.fromKey(selectedReaction)?.let { add("${it.emoji} ${it.label}") }
                                                }
                                                if (selectedLanguage != "all") {
                                                    val langLabel = languageChips.firstOrNull {
                                                        it.code.equals(selectedLanguage, ignoreCase = true)
                                                    }?.label ?: selectedLanguage.uppercase()
                                                    add(langLabel)
                                                }
                                            }
                                            Text(
                                                text = "${filteredMovies.size} shown • ${parts.joinToString(" • ")}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            TextButton(
                                                onClick = onResetFilters,
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(36.dp),
                                            ) {
                                                Text("Clear", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            if (filteredMovies.isEmpty()) {
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "watched_empty_filtered",
                                ) {
                                    CineEmptyState(
                                        title = "No matching watched titles",
                                        subtitle = "Try clearing your filters or search text.",
                                        actionLabel = "Reset Filters",
                                        onActionClick = {
                                            onWatchedSearchQueryChange("")
                                            onResetFilters()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                    )
                                }
                            } else {
                                items(
                                    items = filteredMovies,
                                    key = { it.uniqueKey },
                                ) { movie ->
                                    WatchedMovieListCard(
                                        movie = movie,
                                        onClick = { onMovieClick(movie) },
                                        onLogRewatch = { onLogRewatch(movie) },
                                        onQuickReact = { reactionKey -> onQuickReact(movie, reactionKey) },
                                        onAddToList = { onAddToList(movie) },
                                        onShareCardClick = { onShareCardClick(movie) },
                                        onRemoveClick = { onRemoveClick(movie) },
                                    )
                                }
                            }
                        }
                    }

                    if (showFiltersSheet) {
                        WatchedFiltersBottomSheet(
                            totalCount = watchedState.movies.size,
                            movies = watchedState.movies,
                            languageChips = languageChips,
                            selectedLanguage = selectedLanguage,
                            selectedReaction = selectedReaction,
                            sortOption = sortOption,
                            filterOption = filterOption,
                            activeFilterCount = activeFilterCount,
                            onSortSelected = onSortSelected,
                            onFilterSelected = onFilterSelected,
                            onLanguageSelected = onLanguageSelected,
                            onReactionSelected = onReactionSelected,
                            onResetFilters = onResetFilters,
                            onDismiss = { showFiltersSheet = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchedSubTabsAndFiltersRow(
    watchedSubTab: WatchedSubTab,
    activeFilterCount: Int,
    showFiltersButton: Boolean,
    onSelectSubTab: (WatchedSubTab) -> Unit,
    onOpenFilters: () -> Unit,
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
            items(WatchedSubTab.entries, key = { it.name }) { tab ->
                FilterChip(
                    selected = watchedSubTab == tab,
                    onClick = { onSelectSubTab(tab) },
                    label = {
                        Text(
                            text = tab.label,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (tab == WatchedSubTab.LIST) {
                                Icons.Rounded.Movie
                            } else {
                                Icons.Rounded.CalendarMonth
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.testTag("watched_subtab_${tab.name.lowercase()}"),
                )
            }

            if (showFiltersButton) {
                item(key = "watched_filters_chip") {
                    FilterChip(
                        selected = activeFilterCount > 0,
                        onClick = onOpenFilters,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Open filters",
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    text = "Filters",
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                if (activeFilterCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.testTag("watched_active_filter_badge"),
                                    ) {
                                        Text(activeFilterCount.toString())
                                    }
                                }
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.testTag("watched_filters_button"),
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

@Composable
private fun WatchedSummaryHeaderCard(
    totalWatched: Int,
    moviesCount: Int,
    tvCount: Int,
    avgPersonal: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.movies_watched_count, totalWatched),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier.testTag("watched_count_badge"),
                )
                Text(
                    text = "$moviesCount ${if (moviesCount == 1) "Movie" else "Movies"} • $tvCount Series",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                color = CineElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = CineGold,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "$avgPersonal avg",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchedFiltersBottomSheet(
    totalCount: Int,
    movies: List<WatchedMovie>,
    languageChips: List<LanguageChipCount>,
    selectedLanguage: String,
    selectedReaction: String,
    sortOption: WatchedSortOption,
    filterOption: WatchedFilterOption,
    activeFilterCount: Int,
    onSortSelected: (WatchedSortOption) -> Unit,
    onFilterSelected: (WatchedFilterOption) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onReactionSelected: (String) -> Unit,
    onResetFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CineCharcoal,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("watched_filters_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Filter & Sort Library",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
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

            // 1. Media Type & Status
            FilterSectionLabel("MEDIA TYPE & STATUS")
            ScrollableChipStrip(fadeColor = CineCharcoal) {
                items(WatchedFilterOption.entries, key = { it.name }) { filter ->
                    FilterChip(
                        selected = filterOption == filter,
                        onClick = { onFilterSelected(filter) },
                        label = {
                            Text(
                                text = filter.label,
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CineGold.copy(alpha = 0.2f),
                            selectedLabelColor = CineGold,
                        ),
                        modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}"),
                    )
                }
            }

            // 2. Sort By
            FilterSectionLabel("SORT BY")
            ScrollableChipStrip(fadeColor = CineCharcoal) {
                items(WatchedSortOption.entries, key = { it.name }) { option ->
                    FilterChip(
                        selected = sortOption == option,
                        onClick = { onSortSelected(option) },
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
                        modifier = Modifier.testTag("sort_chip_${option.name.lowercase()}"),
                    )
                }
            }

            // 3. Vibe / Quick Reaction
            FilterSectionLabel("VIBE")
            ScrollableChipStrip(fadeColor = CineCharcoal) {
                item(key = "vibe_all") {
                    FilterChip(
                        selected = selectedReaction.isEmpty(),
                        onClick = { if (selectedReaction.isNotEmpty()) onReactionSelected(selectedReaction) },
                        label = {
                            Text(
                                text = "All Vibes",
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        modifier = Modifier.testTag("reaction_filter_all"),
                    )
                }
                items(QuickReaction.entries, key = { it.key }) { reaction ->
                    val count = movies.count { it.reaction == reaction.key }
                    FilterChip(
                        selected = selectedReaction == reaction.key,
                        onClick = { onReactionSelected(reaction.key) },
                        label = {
                            Text(
                                text = if (count > 0) "${reaction.emoji} ${reaction.label} ($count)"
                                else "${reaction.emoji} ${reaction.label}",
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.testTag("reaction_filter_${reaction.key}"),
                    )
                }
            }

            // 4. Language
            if (languageChips.isNotEmpty()) {
                FilterSectionLabel("LANGUAGE")
                LanguageFilterRow(
                    chips = languageChips,
                    totalCount = totalCount,
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = onLanguageSelected,
                    testTagPrefix = "watched",
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    fadeColor = CineCharcoal,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onResetFilters()
                        onDismiss()
                    },
                    enabled = activeFilterCount > 0,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("watched_filters_reset_button"),
                ) {
                    Text(
                        text = "Reset all",
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("watched_filters_apply_button"),
                ) {
                    Text(
                        text = "Done",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun ScrollableChipStrip(
    fadeColor: Color,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
        if (listState.canScrollForward) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(32.dp)
                    .height(42.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, fadeColor)
                        )
                    )
            )
        }
    }
}

@Composable
private fun WatchedMovieListCard(
    movie: WatchedMovie,
    onClick: () -> Unit,
    onLogRewatch: () -> Unit,
    onQuickReact: (String) -> Unit,
    onAddToList: () -> Unit,
    onShareCardClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }
    val watchedDateText = movie.watchedAt?.toDate()?.let { dateFormatter.format(it) } ?: "Just now"
    var reactionMenuExpanded by remember(movie.uniqueKey) { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("watched_movie_card_${movie.uniqueKey}"),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(86.dp)
                        .height(128.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CineElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    val posterUrl = movie.fullPosterUrl()
                    if (posterUrl != null) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = "${movie.title} poster",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = if (movie.isTv) Icons.Rounded.LiveTv else Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (movie.isTv) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(5.dp),
                        ) {
                            Text(
                                text = "TV",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }

                    val reaction = movie.quickReaction
                    if (reaction != null) {
                        Surface(
                            color = ColorBlackOverlay,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(5.dp),
                        ) {
                            Text(
                                text = reaction.emoji,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = movie.title,
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
                                    text = String.format(Locale.US, "%.1f", movie.rating),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    val subtitle = buildString {
                        append(movie.releaseYear)
                        if (movie.runtime > 0) {
                            append(if (movie.isTv) " • ${movie.runtime}m/ep" else " • ${movie.runtime}m")
                        }
                        if (movie.isTv && movie.numberOfSeasons > 0) {
                            append(" • ${movie.numberOfSeasons} ${if (movie.numberOfSeasons == 1) "Season" else "Seasons"}")
                        }
                        append(" • $watchedDateText")
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (movie.isTv) {
                        val tvStatus = movie.resolvedTvStatus
                        val progressSummary = movie.formatTvProgressSummary()
                        val nextUpLabel = movie.formatNextUpLabel()
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TvStatusPill(
                                    status = tvStatus,
                                    modifier = Modifier.testTag("tv_status_badge_${movie.uniqueKey}"),
                                )
                                if (!nextUpLabel.isNullOrBlank()) {
                                    Surface(
                                        color = CineGold.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = nextUpLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = CineGold,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            if (!progressSummary.isNullOrBlank()) {
                                Text(
                                    text = progressSummary,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                LinearProgressIndicator(
                                    progress = { movie.tvProgressFraction() },
                                    color = if (tvStatus == TvWatchStatus.COMPLETED) {
                                        CineEmerald
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    trackColor = CineSurfaceHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .testTag("tv_card_progress_${movie.uniqueKey}"),
                                )
                            }
                        }
                    }

                    // Language, Rewatch Count & Reaction badges row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (movie.watchCount > 1) {
                            Surface(
                                color = CineGold.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, CineGold.copy(alpha = 0.45f)),
                            ) {
                                Text(
                                    text = "Watched ${movie.watchCount}×",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CineGold,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                        if (movie.languageName.isNotBlank()) {
                            Surface(
                                color = CineElevated,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                Text(
                                    text = movie.languageName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                        movie.quickReaction?.let { react ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = "${react.emoji} ${react.label}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    // User star rating row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.testTag("watched_user_rating_${movie.uniqueKey}"),
                    ) {
                        if (movie.userRating > 0) {
                            repeat(5) { idx ->
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = if (idx < movie.userRating) CineGold else CineSurfaceHigh,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${movie.userRating}/5",
                                style = MaterialTheme.typography.labelMedium,
                                color = CineGold,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            Text(
                                text = "Tap to rate & add note",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (movie.note.isNotBlank()) {
                        Text(
                            text = "“${movie.note}”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(2.dp))

            // Single clean action row: Reaction Picker popup, Rewatch, Add to List, Share, Remove (all >= 48dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    val activeReaction = movie.quickReaction
                    IconButton(
                        onClick = { reactionMenuExpanded = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(48.dp)
                            .testTag("quick_react_picker_${movie.uniqueKey}"),
                    ) {
                        if (activeReaction != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = activeReaction.emoji,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AddReaction,
                                contentDescription = "Pick reaction",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = reactionMenuExpanded,
                        onDismissRequest = { reactionMenuExpanded = false },
                        containerColor = CineCharcoal,
                    ) {
                        QuickReaction.entries.forEach { reaction ->
                            val selected = movie.reaction == reaction.key
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${reaction.emoji}  ${reaction.label}",
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                },
                                trailingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                } else null,
                                onClick = {
                                    reactionMenuExpanded = false
                                    onQuickReact(reaction.key)
                                },
                                modifier = Modifier.testTag("quick_react_${movie.uniqueKey}_${reaction.key}"),
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onLogRewatch,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                        .testTag("rewatch_button_${movie.uniqueKey}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = "Watched again",
                        tint = CineGold,
                    )
                }

                IconButton(
                    onClick = onAddToList,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                        .testTag("watched_add_to_list_${movie.uniqueKey}"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = "Add to custom list",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = onShareCardClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                        .testTag("share_card_button_${movie.uniqueKey}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share spoiler-free card",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                        .testTag("remove_watched_button_${movie.uniqueKey}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.remove_from_watched),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private val ColorBlackOverlay = Color(0xCC0B0B0F)
