package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.example.R
import com.example.auth.signOutUser
import com.example.data.firebase.FirestoreProvider
import com.example.data.repository.CustomListRepository
import com.example.data.repository.TmdbRepository
import com.example.data.repository.UserSettingsRepository
import com.example.data.repository.WatchedMovieRepository
import com.example.data.repository.WatchlistRepository
import com.example.ui.AppTab
import com.example.ui.CineLogViewModel
import com.example.ui.ListsSubTab
import com.example.ui.components.CineTopNavbar
import com.example.ui.components.OfflineModeBanner
import com.example.ui.theme.CineCharcoal
import com.example.ui.theme.CineElevated
import com.example.util.NetworkMonitor
import com.google.firebase.auth.FirebaseUser

private data class NavTabMeta(
    val tab: AppTab,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val NAV_TABS = listOf(
    NavTabMeta(
        tab = AppTab.DISCOVER,
        labelRes = R.string.tab_discover,
        selectedIcon = Icons.Rounded.Explore,
        unselectedIcon = Icons.Outlined.Explore,
    ),
    NavTabMeta(
        tab = AppTab.WATCHED,
        labelRes = R.string.tab_watched,
        selectedIcon = Icons.Rounded.BookmarkAdded,
        unselectedIcon = Icons.Outlined.BookmarkBorder,
    ),
    NavTabMeta(
        tab = AppTab.WATCHLIST,
        labelRes = R.string.tab_lists,
        selectedIcon = Icons.Rounded.Bookmarks,
        unselectedIcon = Icons.Outlined.Bookmarks,
    ),
    NavTabMeta(
        tab = AppTab.STATS,
        labelRes = R.string.tab_stats,
        selectedIcon = Icons.Rounded.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
    ),
    NavTabMeta(
        tab = AppTab.SETTINGS,
        labelRes = R.string.tab_settings,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
@Composable
fun MainCinemaScreen(
    currentUser: FirebaseUser,
    onAccentHexChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: CineLogViewModel = viewModel(
        key = currentUser.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val db = FirestoreProvider.getInstance(app)
                CineLogViewModel(
                    tmdbRepository = TmdbRepository(),
                    watchedRepository = WatchedMovieRepository(db),
                    watchlistRepository = WatchlistRepository(db),
                    customListRepository = CustomListRepository(db),
                    settingsRepository = UserSettingsRepository(app),
                    networkMonitor = NetworkMonitor(app),
                    currentUserId = currentUser.uid,
                )
            }
        }
    )

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val watchedSubTab by viewModel.watchedSubTab.collectAsStateWithLifecycle()
    val listsSubTab by viewModel.listsSubTab.collectAsStateWithLifecycle()
    val diaryMonthYm by viewModel.diaryMonthYm.collectAsStateWithLifecycle()
    val diarySelectedDate by viewModel.diarySelectedDate.collectAsStateWithLifecycle()
    val watchDiaryByDate by viewModel.watchDiaryByDate.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val discoverMediaFilter by viewModel.discoverMediaFilter.collectAsStateWithLifecycle()
    val discoverLanguageFilter by viewModel.discoverLanguageFilter.collectAsStateWithLifecycle()
    val discoverLanguageChips by viewModel.discoverLanguageChips.collectAsStateWithLifecycle()
    val discoverState by viewModel.discoverState.collectAsStateWithLifecycle()
    val filteredDiscoverMovies by viewModel.filteredDiscoverMovies.collectAsStateWithLifecycle()
    val forYouRecommendations by viewModel.forYouRecommendations.collectAsStateWithLifecycle()
    val watchedState by viewModel.watchedState.collectAsStateWithLifecycle()
    val watchedMap by viewModel.watchedMap.collectAsStateWithLifecycle()
    val watchedCount by viewModel.watchedCount.collectAsStateWithLifecycle()
    val watchedLanguageFilter by viewModel.watchedLanguageFilter.collectAsStateWithLifecycle()
    val watchedLanguageChips by viewModel.watchedLanguageChips.collectAsStateWithLifecycle()
    val watchedReactionFilter by viewModel.watchedReactionFilter.collectAsStateWithLifecycle()
    val filteredWatched by viewModel.filteredWatchedMovies.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val filterOption by viewModel.filterOption.collectAsStateWithLifecycle()
    val watchedSearchQuery by viewModel.watchedSearchQuery.collectAsStateWithLifecycle()
    val watchlistState by viewModel.watchlistState.collectAsStateWithLifecycle()
    val watchlistMap by viewModel.watchlistMap.collectAsStateWithLifecycle()
    val watchlistCount by viewModel.watchlistCount.collectAsStateWithLifecycle()
    val customListsState by viewModel.customListsState.collectAsStateWithLifecycle()
    val customLists by viewModel.customLists.collectAsStateWithLifecycle()
    val selectedCustomList by viewModel.selectedCustomList.collectAsStateWithLifecycle()
    val addToListTarget by viewModel.addToListTarget.collectAsStateWithLifecycle()
    val surpriseMeState by viewModel.surpriseMeState.collectAsStateWithLifecycle()
    val showImportExportDialog by viewModel.showImportExportDialog.collectAsStateWithLifecycle()
    val importUiState by viewModel.importUiState.collectAsStateWithLifecycle()
    val statsSummary by viewModel.statsSummary.collectAsStateWithLifecycle()
    val migrationState by viewModel.migrationState.collectAsStateWithLifecycle()
    val watchProvidersCache by viewModel.watchProvidersCache.collectAsStateWithLifecycle()
    val pendingToggleKeys by viewModel.pendingToggleKeys.collectAsStateWithLifecycle()
    val pendingWatchlistKeys by viewModel.pendingWatchlistKeys.collectAsStateWithLifecycle()
    val accentPreference by viewModel.accentPreference.collectAsStateWithLifecycle()
    val movieModal by viewModel.movieModal.collectAsStateWithLifecycle()
    val socialCardMovie by viewModel.socialCardMovie.collectAsStateWithLifecycle()
    val wallpaperEditorImage by viewModel.wallpaperEditorImage.collectAsStateWithLifecycle()
    val selectedPersonStat by viewModel.selectedPersonStat.collectAsStateWithLifecycle()

    LaunchedEffect(accentPreference.hex) {
        onAccentHexChanged(accentPreference.hex)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val displayName = currentUser.displayName?.takeIf { it.isNotBlank() }
        ?: currentUser.email?.substringBefore("@")
        ?: "Cinephile"
    val photoUrl = currentUser.photoUrl?.toString()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useTopTabs = maxWidth >= 840.dp
        val columnsCount = when {
            maxWidth >= 900.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CineTopNavbar(
                        userName = displayName,
                        userPhotoUrl = photoUrl,
                        watchedCount = watchedCount,
                        onWatchedBadgeClick = { viewModel.selectTab(AppTab.WATCHED) },
                        onLogoutClick = {
                            signOutUser(
                                credentialManager = credentialManager,
                                scope = scope,
                            )
                        },
                    )

                    if (useTopTabs) {
                        val selectedIndex = NAV_TABS.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)
                        PrimaryTabRow(
                            selectedTabIndex = selectedIndex,
                            containerColor = CineCharcoal,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            NAV_TABS.forEach { meta ->
                                val badgeCount = when (meta.tab) {
                                    AppTab.WATCHED -> watchedCount
                                    AppTab.WATCHLIST -> watchlistCount
                                    else -> 0
                                }
                                val isSelected = selectedTab == meta.tab
                                Tab(
                                    selected = isSelected,
                                    onClick = { viewModel.selectTab(meta.tab) },
                                    selectedContentColor = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("top_tab_${meta.tab.name.lowercase()}"),
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) meta.selectedIcon else meta.unselectedIcon,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (badgeCount > 0) {
                                                    "${stringResource(meta.labelRes)} ($badgeCount)"
                                                } else {
                                                    stringResource(meta.labelRes)
                                                },
                                                maxLines = 1,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }

                    OfflineModeBanner(isOffline = !isOnline)
                }
            },
            bottomBar = {
                if (!useTopTabs) {
                    NavigationBar(
                        containerColor = CineCharcoal,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.navigationBarsPadding(),
                    ) {
                        NAV_TABS.forEach { meta ->
                            val isSelected = selectedTab == meta.tab
                            val badgeCount = when (meta.tab) {
                                AppTab.WATCHED -> watchedCount
                                AppTab.WATCHLIST -> watchlistCount
                                else -> 0
                            }
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { viewModel.selectTab(meta.tab) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                                    Text(badgeCount.toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) meta.selectedIcon else meta.unselectedIcon,
                                            contentDescription = stringResource(meta.labelRes),
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = stringResource(meta.labelRes),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                                modifier = Modifier.testTag("nav_tab_${meta.tab.name.lowercase()}"),
                            )
                        }
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = CineElevated,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (selectedTab) {
                    AppTab.DISCOVER -> {
                        DiscoverScreen(
                            searchQuery = searchQuery,
                            onSearchQueryChange = viewModel::onSearchQueryChanged,
                            selectedMediaFilter = discoverMediaFilter,
                            onMediaFilterSelected = viewModel::onDiscoverMediaFilterSelected,
                            languageChips = discoverLanguageChips,
                            selectedLanguage = discoverLanguageFilter,
                            onLanguageSelected = viewModel::onDiscoverLanguageSelected,
                            discoverState = discoverState,
                            filteredMovies = filteredDiscoverMovies,
                            forYouState = forYouRecommendations,
                            watchlistCount = watchlistCount,
                            watchedMap = watchedMap,
                            watchlistMap = watchlistMap,
                            watchProvidersCache = watchProvidersCache,
                            pendingToggleKeys = pendingToggleKeys,
                            pendingWatchlistKeys = pendingWatchlistKeys,
                            columnsCount = columnsCount,
                            onMovieClick = viewModel::openMovieDetails,
                            onToggleWatched = viewModel::toggleWatched,
                            onToggleWatchlist = viewModel::toggleWatchlist,
                            onAddToList = viewModel::openAddToListSheet,
                            onOpenSurpriseMe = viewModel::openSurpriseMe,
                            onRefreshRecommendations = viewModel::refreshRecommendations,
                            onRetry = viewModel::retryDiscover,
                        )
                    }

                    AppTab.WATCHED -> {
                        WatchedScreen(
                            watchedSubTab = watchedSubTab,
                            diaryMonthYm = diaryMonthYm,
                            diarySelectedDate = diarySelectedDate,
                            watchDiaryByDate = watchDiaryByDate,
                            watchedState = watchedState,
                            filteredMovies = filteredWatched,
                            languageChips = watchedLanguageChips,
                            selectedLanguage = watchedLanguageFilter,
                            selectedReaction = watchedReactionFilter,
                            sortOption = sortOption,
                            filterOption = filterOption,
                            watchedSearchQuery = watchedSearchQuery,
                            columnsCount = columnsCount,
                            onSelectSubTab = viewModel::selectWatchedSubTab,
                            onChangeDiaryMonth = viewModel::changeDiaryMonth,
                            onSelectDiaryDate = viewModel::selectDiaryDate,
                            onSortSelected = viewModel::onSortOptionSelected,
                            onFilterSelected = viewModel::onFilterOptionSelected,
                            onLanguageSelected = viewModel::onWatchedLanguageSelected,
                            onReactionSelected = viewModel::onWatchedReactionSelected,
                            onWatchedSearchQueryChange = viewModel::onWatchedSearchQueryChanged,
                            onResetFilters = viewModel::resetWatchedFilters,
                            onMovieClick = viewModel::openWatchedDetails,
                            onLogRewatch = { watched ->
                                viewModel.logRewatch(
                                    movieId = watched.movieId,
                                    mediaType = watched.resolvedMediaType,
                                    title = watched.title,
                                )
                            },
                            onQuickReact = { watched, reactionKey ->
                                viewModel.quickReact(
                                    movieId = watched.movieId,
                                    mediaType = watched.resolvedMediaType,
                                    reactionKey = reactionKey,
                                )
                            },
                            onAddToList = { watched ->
                                viewModel.openAddToListSheet(watched.toSummary())
                            },
                            onShareCardClick = viewModel::openSocialCard,
                            onRemoveClick = { watched ->
                                viewModel.removeFromWatched(
                                    movieId = watched.movieId,
                                    mediaType = watched.resolvedMediaType,
                                    title = watched.title,
                                )
                            },
                            onExploreClick = { viewModel.selectTab(AppTab.DISCOVER) },
                        )
                    }

                    AppTab.WATCHLIST -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            ) {
                                ListsSubTab.entries.forEach { tab ->
                                    val countSuffix = when (tab) {
                                        ListsSubTab.WATCHLIST -> if (watchlistCount > 0) " ($watchlistCount)" else ""
                                        ListsSubTab.CUSTOM_LISTS -> if (customLists.isNotEmpty()) " (${customLists.size})" else ""
                                    }
                                    FilterChip(
                                        selected = listsSubTab == tab,
                                        onClick = { viewModel.selectListsSubTab(tab) },
                                        label = { Text("${tab.label}$countSuffix", fontWeight = FontWeight.SemiBold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                        modifier = Modifier.testTag("lists_subtab_${tab.name.lowercase()}"),
                                    )
                                }
                            }

                            when (listsSubTab) {
                                ListsSubTab.WATCHLIST -> {
                                    WatchlistScreen(
                                        watchlistState = watchlistState,
                                        pendingToggleKeys = pendingToggleKeys,
                                        columnsCount = columnsCount,
                                        onItemClick = viewModel::openWatchlistDetails,
                                        onMoveToWatched = viewModel::moveWatchlistToWatched,
                                        onAddToList = { item ->
                                            viewModel.openAddToListSheet(item.toSummary())
                                        },
                                        onSurpriseMeClick = viewModel::openSurpriseMe,
                                        onRemoveClick = viewModel::removeFromWatchlist,
                                        onExploreClick = { viewModel.selectTab(AppTab.DISCOVER) },
                                    )
                                }

                                ListsSubTab.CUSTOM_LISTS -> {
                                    CustomListsTabContent(
                                        customListsState = customListsState,
                                        columnsCount = columnsCount,
                                        onCreateList = { name, desc ->
                                            viewModel.createCustomList(name = name, description = desc)
                                        },
                                        onRenameList = { list, newName, newDesc ->
                                            viewModel.renameCustomList(
                                                listId = list.id,
                                                newName = newName,
                                                newDescription = newDesc,
                                            )
                                        },
                                        onDeleteList = viewModel::deleteCustomList,
                                        onOpenListDetails = { list ->
                                            viewModel.openCustomListDetails(list.id)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    AppTab.STATS -> {
                        StatsScreen(
                            stats = statsSummary,
                            isMigrating = migrationState.isRunning,
                            onPersonClick = viewModel::openPersonDetails,
                            onRunMigrationClick = viewModel::runOldDataMigration,
                            onExploreClick = { viewModel.selectTab(AppTab.DISCOVER) },
                        )
                    }

                    AppTab.SETTINGS -> {
                        SettingsScreen(
                            userName = displayName,
                            userEmail = currentUser.email,
                            isOnline = isOnline,
                            accentPreference = accentPreference,
                            itemsMissingMetadataCount = statsSummary.itemsMissingMetadataCount,
                            migrationState = migrationState,
                            onSelectPreset = viewModel::selectAccentPreset,
                            onApplyCustomHex = viewModel::saveCustomAccentHex,
                            onRunMigration = viewModel::runOldDataMigration,
                            onOpenImportExport = viewModel::openImportExportDialog,
                            onClearImageCache = {
                                context.imageLoader.memoryCache?.clear()
                                context.imageLoader.diskCache?.clear()
                                viewModel.notifyMessage("Cleared cached poster images.")
                            },
                            onLogout = {
                                signOutUser(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    movieModal?.let { modal ->
        MovieDetailsSheet(
            modalState = modal,
            watchedMovie = watchedMap[modal.uniqueKey],
            isInWatchlist = watchlistMap.containsKey(modal.uniqueKey),
            isTogglingWatched = pendingToggleKeys.contains(modal.uniqueKey),
            isTogglingWatchlist = pendingWatchlistKeys.contains(modal.uniqueKey),
            onDismiss = viewModel::dismissMovieDetails,
            onToggleWatched = { viewModel.toggleWatched(modal.summary) },
            onToggleWatchlist = { viewModel.toggleWatchlist(modal.summary) },
            onAddToList = { viewModel.openAddToListSheet(modal.summary) },
            onLogRewatch = { watched ->
                viewModel.logRewatch(
                    movieId = watched.movieId,
                    mediaType = watched.resolvedMediaType,
                    title = watched.title,
                )
            },
            onRemoveWatchDate = { watched, idx ->
                viewModel.removeWatchDateAt(
                    movieId = watched.movieId,
                    mediaType = watched.resolvedMediaType,
                    index = idx,
                )
            },
            onShareCard = { watched ->
                viewModel.openSocialCard(watched)
            },
            onOpenImageGalleryItem = viewModel::openWallpaperEditor,
            onSaveReview = { rating, note, watchedSeason, reaction ->
                viewModel.saveReview(
                    movieId = modal.movieId,
                    mediaType = modal.mediaType,
                    userRating = rating,
                    note = note,
                    watchedSeason = watchedSeason,
                    reaction = reaction,
                )
            },
        )
    }

    addToListTarget?.let { target ->
        AddToListSheet(
            target = target,
            lists = customLists,
            onToggleMovieInList = viewModel::toggleMovieInList,
            onCreateListAndAdd = { listName, movie ->
                viewModel.createCustomList(name = listName, initialMovie = movie)
            },
            onDismiss = viewModel::dismissAddToListSheet,
        )
    }

    selectedCustomList?.let { list ->
        CustomListDetailsSheet(
            list = list,
            onMovieClick = viewModel::openMovieDetails,
            onRemoveItem = { parent, entry ->
                viewModel.removeMovieFromList(
                    list = parent,
                    movieId = entry.movieId,
                    mediaType = entry.resolvedMediaType,
                    title = entry.title,
                )
            },
            onDismiss = viewModel::dismissCustomListDetails,
        )
    }

    SurpriseMeDialog(
        state = surpriseMeState,
        watchlistItems = watchlistMap.values.toList(),
        onPickAgain = viewModel::pickSurpriseMovie,
        onOpenDetails = viewModel::openWatchlistDetails,
        onMarkWatched = viewModel::moveWatchlistToWatched,
        onDismiss = viewModel::dismissSurpriseMe,
    )

    ImportExportDialog(
        isOpen = showImportExportDialog,
        importUiState = importUiState,
        watchedCount = watchedCount,
        watchlistCount = watchlistCount,
        customListsCount = customLists.size,
        buildExportCsv = viewModel::buildExportCsv,
        buildExportJson = viewModel::buildExportJson,
        onStartImportCsv = viewModel::parseAndMatchImportCsv,
        onSearchFixQuery = viewModel::searchTmdbForImportFix,
        onFixRow = viewModel::fixImportRowMatch,
        onToggleSkipRow = viewModel::toggleSkipImportRow,
        onCommitImport = viewModel::commitMatchedImportToFirestore,
        onClearPreview = viewModel::clearImportPreview,
        onNotify = viewModel::notifyMessage,
        onDismiss = viewModel::dismissImportExportDialog,
    )

    selectedPersonStat?.let { (roleLabel, personStat) ->
        PersonWatchedMoviesSheet(
            roleLabel = roleLabel,
            personStat = personStat,
            onMovieClick = viewModel::openWatchedDetails,
            onDismiss = viewModel::dismissPersonDetails,
        )
    }

    socialCardMovie?.let { movie ->
        SocialCardSheet(
            movie = movie,
            onDismiss = viewModel::dismissSocialCard,
            onNotify = viewModel::notifyMessage,
        )
    }

    wallpaperEditorImage?.let { (mediaTitle, imageItem) ->
        WallpaperCropDialog(
            mediaTitle = mediaTitle,
            imageItem = imageItem,
            onDismiss = viewModel::dismissWallpaperEditor,
            onNotify = viewModel::notifyMessage,
        )
    }
}
