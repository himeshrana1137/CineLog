package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.signOutUser
import com.example.data.repository.TmdbRepository
import com.example.data.repository.WatchedMovieRepository
import com.example.ui.AppTab
import com.example.ui.CineLogViewModel
import com.example.ui.components.CineTopNavbar
import com.example.ui.theme.CineCharcoal
import com.example.ui.theme.CineElevated
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun MainCinemaScreen(
    currentUser: FirebaseUser,
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
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                CineLogViewModel(
                    tmdbRepository = TmdbRepository(),
                    watchedRepository = WatchedMovieRepository(db),
                    currentUserId = currentUser.uid,
                )
            }
        }
    )

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val discoverState by viewModel.discoverState.collectAsStateWithLifecycle()
    val watchedState by viewModel.watchedState.collectAsStateWithLifecycle()
    val watchedMap by viewModel.watchedMap.collectAsStateWithLifecycle()
    val watchedCount by viewModel.watchedCount.collectAsStateWithLifecycle()
    val filteredWatched by viewModel.filteredWatchedMovies.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val filterOption by viewModel.filterOption.collectAsStateWithLifecycle()
    val watchedSearchQuery by viewModel.watchedSearchQuery.collectAsStateWithLifecycle()
    val pendingToggleIds by viewModel.pendingToggleIds.collectAsStateWithLifecycle()
    val movieModal by viewModel.movieModal.collectAsStateWithLifecycle()

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
        val useNavigationRail = maxWidth >= 840.dp
        val columnsCount = when {
            maxWidth >= 900.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
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
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = CineCharcoal,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.navigationBarsPadding(),
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == AppTab.DISCOVER,
                            onClick = { viewModel.selectTab(AppTab.DISCOVER) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == AppTab.DISCOVER) {
                                        Icons.Rounded.Explore
                                    } else {
                                        Icons.Outlined.Explore
                                    },
                                    contentDescription = stringResource(R.string.tab_discover),
                                )
                            },
                            label = { Text(stringResource(R.string.tab_discover)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            modifier = Modifier.testTag("nav_tab_discover"),
                        )
                        NavigationBarItem(
                            selected = selectedTab == AppTab.WATCHED,
                            onClick = { viewModel.selectTab(AppTab.WATCHED) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (watchedCount > 0) {
                                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                                Text(watchedCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selectedTab == AppTab.WATCHED) {
                                            Icons.Rounded.BookmarkAdded
                                        } else {
                                            Icons.Outlined.BookmarkBorder
                                        },
                                        contentDescription = stringResource(R.string.tab_watched),
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.tab_watched)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            modifier = Modifier.testTag("nav_tab_watched"),
                        )
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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = CineCharcoal,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Spacer(modifier = Modifier.weight(0.1f))
                        NavigationRailItem(
                            selected = selectedTab == AppTab.DISCOVER,
                            onClick = { viewModel.selectTab(AppTab.DISCOVER) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == AppTab.DISCOVER) {
                                        Icons.Rounded.Explore
                                    } else {
                                        Icons.Outlined.Explore
                                    },
                                    contentDescription = stringResource(R.string.tab_discover),
                                )
                            },
                            label = { Text(stringResource(R.string.tab_discover)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            modifier = Modifier.testTag("rail_tab_discover"),
                        )
                        NavigationRailItem(
                            selected = selectedTab == AppTab.WATCHED,
                            onClick = { viewModel.selectTab(AppTab.WATCHED) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (watchedCount > 0) {
                                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                                Text(watchedCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selectedTab == AppTab.WATCHED) {
                                            Icons.Rounded.BookmarkAdded
                                        } else {
                                            Icons.Outlined.BookmarkBorder
                                        },
                                        contentDescription = stringResource(R.string.tab_watched),
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.tab_watched)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            modifier = Modifier.testTag("rail_tab_watched"),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        AppTab.DISCOVER -> {
                            DiscoverScreen(
                                searchQuery = searchQuery,
                                onSearchQueryChange = viewModel::onSearchQueryChanged,
                                discoverState = discoverState,
                                watchedMap = watchedMap,
                                pendingToggleIds = pendingToggleIds,
                                columnsCount = columnsCount,
                                onMovieClick = viewModel::openMovieDetails,
                                onToggleWatched = viewModel::toggleWatched,
                                onRetry = viewModel::retryDiscover,
                            )
                        }

                        AppTab.WATCHED -> {
                            WatchedScreen(
                                watchedState = watchedState,
                                filteredMovies = filteredWatched,
                                sortOption = sortOption,
                                filterOption = filterOption,
                                watchedSearchQuery = watchedSearchQuery,
                                columnsCount = columnsCount,
                                onSortSelected = viewModel::onSortOptionSelected,
                                onFilterSelected = viewModel::onFilterOptionSelected,
                                onWatchedSearchQueryChange = viewModel::onWatchedSearchQueryChanged,
                                onMovieClick = viewModel::openWatchedDetails,
                                onRemoveClick = { watched ->
                                    viewModel.removeFromWatched(watched.movieId, watched.title)
                                },
                                onExploreClick = { viewModel.selectTab(AppTab.DISCOVER) },
                            )
                        }
                    }
                }
            }
        }
    }

    movieModal?.let { modal ->
        MovieDetailsSheet(
            modalState = modal,
            watchedMovie = watchedMap[modal.movieId],
            isTogglingWatched = pendingToggleIds.contains(modal.movieId),
            onDismiss = viewModel::dismissMovieDetails,
            onToggleWatched = { viewModel.toggleWatched(modal.summary) },
            onSaveReview = { rating, note ->
                viewModel.saveReview(modal.movieId, rating, note)
            },
        )
    }
}
