package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.model.WatchedMovie
import com.example.ui.WatchedFilterOption
import com.example.ui.WatchedSortOption
import com.example.ui.WatchedSubTab
import com.example.ui.WatchedUiState
import com.example.ui.components.CineEmptyState
import com.example.ui.components.LanguageFilterRow
import com.example.ui.components.MovieSkeletonCard
import com.example.ui.theme.CineElevated
import com.example.ui.theme.CineGold
import com.example.ui.theme.CineSurfaceHigh
import java.text.SimpleDateFormat
import java.util.Locale

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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        when (watchedState) {
            is WatchedUiState.Loading -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnsCount),
                    contentPadding = PaddingValues(16.dp),
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
                CineEmptyState(
                    title = "Unable to sync your library",
                    subtitle = watchedState.message,
                    modifier = Modifier.fillMaxSize(),
                )
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

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                                Column {
                                    Text(
                                        text = "MY WATCHED LIBRARY",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "$moviesCount ${if (moviesCount == 1) "Movie" else "Movies"} • $tvCount ${if (tvCount == 1) "Series" else "Series"}",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Surface(
                                    color = CineElevated,
                                    shape = RoundedCornerShape(12.dp),
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
                                        )
                                    }
                                }
                            }
                        }

                        // Sub-tabs: Watched List | Watch Diary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WatchedSubTab.entries.forEach { tab ->
                                FilterChip(
                                    selected = watchedSubTab == tab,
                                    onClick = { onSelectSubTab(tab) },
                                    label = { Text(tab.label, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (tab == WatchedSubTab.LIST) Icons.Rounded.Movie else Icons.Rounded.CalendarMonth,
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
                        }
                    }

                    if (watchedSubTab == WatchedSubTab.DIARY) {
                        WatchDiaryView(
                            monthYm = diaryMonthYm,
                            selectedDate = diarySelectedDate,
                            diaryByDate = watchDiaryByDate,
                            onChangeMonth = onChangeDiaryMonth,
                            onSelectDate = onSelectDiaryDate,
                            onMovieClick = onMovieClick,
                            onLogRewatch = onLogRewatch,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Filter within watched library
                        OutlinedTextField(
                            value = watchedSearchQuery,
                            onValueChange = onWatchedSearchQueryChange,
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = "Filter watched titles or notes…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingIcon = {
                                if (watchedSearchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onWatchedSearchQueryChange("") },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Clear filter",
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CineElevated,
                                unfocusedContainerColor = CineElevated,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("watched_filter_input"),
                        )

                        // Sort & Type Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = "Sort options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            WatchedSortOption.entries.forEach { option ->
                                FilterChip(
                                    selected = sortOption == option,
                                    onClick = { onSortSelected(option) },
                                    label = { Text(option.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                    modifier = Modifier.testTag("sort_chip_${option.name.lowercase()}"),
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            WatchedFilterOption.entries.forEach { filter ->
                                FilterChip(
                                    selected = filterOption == filter,
                                    onClick = { onFilterSelected(filter) },
                                    label = { Text(filter.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CineGold.copy(alpha = 0.2f),
                                        selectedLabelColor = CineGold,
                                    ),
                                    modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}"),
                                )
                            }
                        }

                        // Quick Emoji Reaction Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilterChip(
                                selected = selectedReaction.isEmpty(),
                                onClick = { if (selectedReaction.isNotEmpty()) onReactionSelected(selectedReaction) },
                                label = { Text("All Vibes") },
                                modifier = Modifier.testTag("reaction_filter_all"),
                            )
                            QuickReaction.entries.forEach { reaction ->
                                val count = watchedState.movies.count { it.reaction == reaction.key }
                                FilterChip(
                                    selected = selectedReaction == reaction.key,
                                    onClick = { onReactionSelected(reaction.key) },
                                    label = {
                                        Text(
                                            if (count > 0) "${reaction.emoji} ${reaction.label} ($count)"
                                            else "${reaction.emoji} ${reaction.label}"
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

                        // Dynamic Language Filter Chips built from watched library
                        if (languageChips.isNotEmpty()) {
                            LanguageFilterRow(
                                chips = languageChips,
                                totalCount = watchedState.movies.size,
                                selectedLanguage = selectedLanguage,
                                onLanguageSelected = onLanguageSelected,
                                testTagPrefix = "watched",
                            )
                        }
                        } // End filter header column

                        if (filteredMovies.isEmpty()) {
                            CineEmptyState(
                                title = "No matching watched titles",
                                subtitle = "Try clearing your filter chips or search text.",
                                actionLabel = "Reset Filters",
                                onActionClick = onResetFilters,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            val gridCols = if (columnsCount >= 3) 2 else 1
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridCols),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
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
                }
            }
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
    val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
    val watchedDateText = movie.watchedAt?.toDate()?.let { dateFormatter.format(it) } ?: "Just now"

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
                            append(" • ${movie.runtime}m")
                        }
                        if (movie.isTv) {
                            if (movie.watchedSeason > 0) append(" • S${movie.watchedSeason}")
                            else if (movie.numberOfSeasons > 0) append(" • ${movie.numberOfSeasons} Seasons")
                        }
                        append(" • $watchedDateText")
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

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
                                    text = "Watched ${movie.watchCount} times",
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

            Spacer(modifier = Modifier.height(8.dp))

            // One-tap Emoji Reaction bar + Share Card / Edit / Remove actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuickReaction.entries.forEach { reaction ->
                        val active = movie.reaction == reaction.key
                        Surface(
                            onClick = { onQuickReact(reaction.key) },
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else CineElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("quick_react_${movie.uniqueKey}_${reaction.key}"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = reaction.emoji)
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onLogRewatch,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
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
                            .testTag("share_card_button_${movie.uniqueKey}"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share spoiler-free card",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(
                        onClick = onClick,
                        modifier = Modifier.height(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Details")
                    }
                    IconButton(
                        onClick = onRemoveClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
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
}

private val ColorBlackOverlay = androidx.compose.ui.graphics.Color(0xCC0B0B0F)
