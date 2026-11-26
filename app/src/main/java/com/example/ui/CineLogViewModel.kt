package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirestoreSnapshotState
import com.example.data.firebase.REAUTH_REQUIRED_MESSAGE
import com.example.data.firebase.formatTechnicalFirestoreError
import com.example.data.firebase.isAuthSessionInvalidError
import com.example.data.model.AccentPreset
import com.example.data.model.CineStatsSummary
import com.example.data.model.CustomList
import com.example.data.model.LanguageChipCount
import com.example.data.model.MediaType
import com.example.data.model.MovieDetails
import com.example.data.model.MovieSummary
import com.example.data.model.PersonStat
import com.example.data.model.QuickReaction
import com.example.data.model.StatsCalculator
import com.example.data.model.StatsMediaFilter
import com.example.data.model.TmdbImageItem
import com.example.data.model.TvComputedProgress
import com.example.data.model.TvProgressCalculator
import com.example.data.model.TvSeasonDetail
import com.example.data.model.TvSeasonSummary
import com.example.data.model.TvWatchStatus
import com.example.data.model.WatchProvidersRegion
import com.example.data.model.WatchedMovie
import com.example.data.model.WatchedSeason
import com.example.data.model.WatchlistItem
import com.example.data.model.todayIsoDate
import com.example.data.repository.BatchWatchedImportItem
import com.example.data.repository.CustomListRepository
import com.example.data.repository.LocalAccentPreference
import com.example.data.repository.SeasonBatchWrite
import com.example.data.repository.TmdbApiKeyMissingException
import com.example.data.repository.TmdbHttpStatusException
import com.example.data.repository.TmdbRepository
import com.example.data.repository.TvProgressRepository
import com.example.data.repository.UserSettingsRepository
import com.example.data.repository.WatchedMovieRepository
import com.example.data.repository.WatchlistRepository
import retrofit2.HttpException
import com.example.util.ImportCandidateRow
import com.example.util.ImportExportHelper
import com.example.util.ImportSourceFormat
import com.example.util.NetworkMonitor
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

enum class AppTab {
    DISCOVER,
    WATCHED,
    WATCHLIST,
    STATS,
    SETTINGS,
}

enum class WatchedSubTab(val label: String) {
    LIST("Watched List"),
    DIARY("Watch Diary"),
}

enum class ListsSubTab(val label: String) {
    WATCHLIST("Watchlist"),
    CUSTOM_LISTS("Custom Lists"),
}

enum class DiscoverMediaFilter(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    TV_SHOWS("TV Shows"),
}

enum class WatchedSortOption(val label: String) {
    RECENT("Recent"),
    TITLE("Title"),
    USER_RATING("My Rating"),
    TMDB_RATING("TMDB Score"),
}

enum class WatchedFilterOption(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    TV_SHOWS("TV Shows"),
    WATCHING("Watching"),
    COMPLETED("Completed"),
    RATED_ONLY("Rated"),
    FIVE_STARS("5★ Favorites"),
}

sealed interface SeasonEpisodesLoadState {
    data object Idle : SeasonEpisodesLoadState
    data object Loading : SeasonEpisodesLoadState
    data class Success(val detail: TvSeasonDetail) : SeasonEpisodesLoadState
    data class Error(val message: String) : SeasonEpisodesLoadState
}

data class EarlierSeasonsPrompt(
    val tvId: Long,
    val targetSeason: TvSeasonSummary,
    val earlierSeasonNumbers: List<Int>,
)

data class CineSnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

sealed interface DiscoverUiState {
    data object Loading : DiscoverUiState
    data class Success(
        val movies: List<MovieSummary>,
        val isSearchResult: Boolean,
        val mediaFilter: DiscoverMediaFilter,
        val isOfflineData: Boolean = false,
        val isRetrying: Boolean = false,
    ) : DiscoverUiState
    data class Error(
        val message: String,
        val technicalDetails: String = message,
        val isMissingApiKey: Boolean = false,
        val isRetrying: Boolean = false,
    ) : DiscoverUiState
}

sealed interface ConnectionTargetStatus {
    data object Idle : ConnectionTargetStatus
    data object Checking : ConnectionTargetStatus
    data object Ok : ConnectionTargetStatus
    data class Error(
        val errorType: String,
        val detail: String = "",
    ) : ConnectionTargetStatus
}

data class ConnectionCheckUiState(
    val isChecking: Boolean = false,
    val hasRun: Boolean = false,
    val tmdbApi: ConnectionTargetStatus = ConnectionTargetStatus.Idle,
    val tmdbImages: ConnectionTargetStatus = ConnectionTargetStatus.Idle,
    val firestore: ConnectionTargetStatus = ConnectionTargetStatus.Idle,
)

sealed interface WatchedUiState {
    data object Loading : WatchedUiState
    data class Success(val movies: List<WatchedMovie>) : WatchedUiState
    data class Error(val message: String) : WatchedUiState
}

sealed interface WatchlistUiState {
    data object Loading : WatchlistUiState
    data class Success(val items: List<WatchlistItem>) : WatchlistUiState
    data class Error(val message: String) : WatchlistUiState
}

sealed interface CustomListsUiState {
    data object Loading : CustomListsUiState
    data class Success(val lists: List<CustomList>) : CustomListsUiState
    data class Error(val message: String) : CustomListsUiState
}

sealed interface RecommendationsUiState {
    data object Idle : RecommendationsUiState
    data object Loading : RecommendationsUiState
    data class Success(
        val items: List<MovieSummary>,
        val seedTitles: List<String>,
    ) : RecommendationsUiState
    data class Error(val message: String) : RecommendationsUiState
}

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class Matching(
        val format: ImportSourceFormat,
        val completed: Int,
        val total: Int,
    ) : ImportUiState
    data class Preview(
        val format: ImportSourceFormat,
        val rows: List<ImportCandidateRow>,
        val isSaving: Boolean = false,
    ) : ImportUiState
}

data class SurpriseMeState(
    val isOpen: Boolean = false,
    val languageFilter: String = "all",
    val genreFilter: String = "all",
    val maxRuntime: Int = 0,
    val matchingCount: Int = 0,
    val pickedItem: WatchlistItem? = null,
)

data class MovieModalState(
    val uniqueKey: String,
    val movieId: Long,
    val mediaType: MediaType,
    val summary: MovieSummary,
    val details: MovieDetails? = null,
    val isLoadingDetails: Boolean = true,
    val detailsError: String? = null,
    val isSavingReview: Boolean = false,
)

data class MigrationProgressState(
    val isRunning: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val updated: Int = 0,
    val failed: Int = 0,
    val message: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CineLogViewModel(
    private val tmdbRepository: TmdbRepository,
    private val watchedRepository: WatchedMovieRepository,
    private val tvProgressRepository: TvProgressRepository,
    private val watchlistRepository: WatchlistRepository,
    private val customListRepository: CustomListRepository,
    private val settingsRepository: UserSettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val currentUserId: String,
    private val auth: FirebaseAuth = Firebase.auth,
    private val tokenRefresher: suspend (FirebaseUser) -> Unit = { user ->
        user.getIdToken(true).await()
    },
) : ViewModel() {

    private class StreamRetryTracker {
        var activeUid: String? = null
        var autoRetryUsedForSession: Boolean = false
        var inFlightRetry: Boolean = false
        var lastHandledErrorRetryCount: Int = -1
        var lastNotifiedErrorKey: String? = null

        fun resetForUid(uid: String?) {
            activeUid = uid
            autoRetryUsedForSession = false
            inFlightRetry = false
            lastHandledErrorRetryCount = -1
            lastNotifiedErrorKey = null
        }
    }

    private val _selectedTab = MutableStateFlow(AppTab.DISCOVER)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _watchedSubTab = MutableStateFlow(WatchedSubTab.LIST)
    val watchedSubTab: StateFlow<WatchedSubTab> = _watchedSubTab.asStateFlow()

    private val _listsSubTab = MutableStateFlow(ListsSubTab.WATCHLIST)
    val listsSubTab: StateFlow<ListsSubTab> = _listsSubTab.asStateFlow()

    private val _diaryMonthYm = MutableStateFlow(todayIsoDate().take(7))
    val diaryMonthYm: StateFlow<String> = _diaryMonthYm.asStateFlow()

    private val _diarySelectedDate = MutableStateFlow(todayIsoDate())
    val diarySelectedDate: StateFlow<String> = _diarySelectedDate.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _discoverMediaFilter = MutableStateFlow(DiscoverMediaFilter.ALL)
    val discoverMediaFilter: StateFlow<DiscoverMediaFilter> = _discoverMediaFilter.asStateFlow()

    private val _discoverLanguageFilter = MutableStateFlow("all")
    val discoverLanguageFilter: StateFlow<String> = _discoverLanguageFilter.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)

    private val _sortOption = MutableStateFlow(WatchedSortOption.RECENT)
    val sortOption: StateFlow<WatchedSortOption> = _sortOption.asStateFlow()

    private val _filterOption = MutableStateFlow(WatchedFilterOption.ALL)
    val filterOption: StateFlow<WatchedFilterOption> = _filterOption.asStateFlow()

    private val _watchedLanguageFilter = MutableStateFlow("all")
    val watchedLanguageFilter: StateFlow<String> = _watchedLanguageFilter.asStateFlow()

    private val _watchedReactionFilter = MutableStateFlow("")
    val watchedReactionFilter: StateFlow<String> = _watchedReactionFilter.asStateFlow()

    private val _watchedSearchQuery = MutableStateFlow("")
    val watchedSearchQuery: StateFlow<String> = _watchedSearchQuery.asStateFlow()

    private val _pendingToggleKeys = MutableStateFlow<Set<String>>(emptySet())
    val pendingToggleKeys: StateFlow<Set<String>> = _pendingToggleKeys.asStateFlow()

    private val _pendingWatchlistKeys = MutableStateFlow<Set<String>>(emptySet())
    val pendingWatchlistKeys: StateFlow<Set<String>> = _pendingWatchlistKeys.asStateFlow()

    private val _movieModal = MutableStateFlow<MovieModalState?>(null)
    val movieModal: StateFlow<MovieModalState?> = _movieModal.asStateFlow()

    private val _socialCardMovie = MutableStateFlow<WatchedMovie?>(null)
    val socialCardMovie: StateFlow<WatchedMovie?> = _socialCardMovie.asStateFlow()

    private val _wallpaperEditorImage = MutableStateFlow<Pair<String, TmdbImageItem>?>(null)
    val wallpaperEditorImage: StateFlow<Pair<String, TmdbImageItem>?> = _wallpaperEditorImage.asStateFlow()

    private val _selectedPersonStat = MutableStateFlow<Pair<String, PersonStat>?>(null)
    val selectedPersonStat: StateFlow<Pair<String, PersonStat>?> = _selectedPersonStat.asStateFlow()

    private val _addToListTarget = MutableStateFlow<MovieSummary?>(null)
    val addToListTarget: StateFlow<MovieSummary?> = _addToListTarget.asStateFlow()

    private val _selectedCustomListId = MutableStateFlow<String?>(null)

    private val _surpriseMeState = MutableStateFlow(SurpriseMeState())
    val surpriseMeState: StateFlow<SurpriseMeState> = _surpriseMeState.asStateFlow()

    private val _showImportExportDialog = MutableStateFlow(false)
    val showImportExportDialog: StateFlow<Boolean> = _showImportExportDialog.asStateFlow()

    private val _importUiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importUiState: StateFlow<ImportUiState> = _importUiState.asStateFlow()

    private val _recommendationsState = MutableStateFlow<RecommendationsUiState>(RecommendationsUiState.Idle)
    val recommendationsState: StateFlow<RecommendationsUiState> = _recommendationsState.asStateFlow()
    private var lastRecommendationSeedKey: String = ""

    private val _migrationState = MutableStateFlow(MigrationProgressState())
    val migrationState: StateFlow<MigrationProgressState> = _migrationState.asStateFlow()

    private val _watchProvidersCache = MutableStateFlow<Map<String, WatchProvidersRegion>>(emptyMap())
    val watchProvidersCache: StateFlow<Map<String, WatchProvidersRegion>> = _watchProvidersCache.asStateFlow()
    private val attemptedProviderKeys = mutableSetOf<String>()

    private val _accentPreference = MutableStateFlow(settingsRepository.loadLocalAccent())
    val accentPreference: StateFlow<LocalAccentPreference> = _accentPreference.asStateFlow()

    private val _tvMigrationCompleted = MutableStateFlow<Boolean?>(null)
    private var tvMigrationStartedThisSession = false

    private val _seasonEpisodesStates = MutableStateFlow<Map<Int, SeasonEpisodesLoadState>>(emptyMap())
    val seasonEpisodesStates: StateFlow<Map<Int, SeasonEpisodesLoadState>> = _seasonEpisodesStates.asStateFlow()

    private val _earlierSeasonsPrompt = MutableStateFlow<EarlierSeasonsPrompt?>(null)
    val earlierSeasonsPrompt: StateFlow<EarlierSeasonsPrompt?> = _earlierSeasonsPrompt.asStateFlow()

    private val _isSavingTvProgress = MutableStateFlow(false)
    val isSavingTvProgress: StateFlow<Boolean> = _isSavingTvProgress.asStateFlow()

    private val _optimisticTvOverrides =
        MutableStateFlow<Map<Long, Map<Int, WatchedSeason?>>>(emptyMap())

    private val _statsMediaFilter = MutableStateFlow(StatsMediaFilter.ALL)
    val statsMediaFilter: StateFlow<StatsMediaFilter> = _statsMediaFilter.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val _snackbarEvents = MutableSharedFlow<CineSnackbarEvent>(extraBufferCapacity = 16)
    val snackbarEvents: SharedFlow<CineSnackbarEvent> = _snackbarEvents.asSharedFlow()

    private val _reAuthRequiredEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val reAuthRequiredEvents: SharedFlow<String> = _reAuthRequiredEvents.asSharedFlow()

    private val activeSyncOpsCount = AtomicInteger(0)
    private val _isSyncingLibrary = MutableStateFlow(false)
    val isSyncingLibrary: StateFlow<Boolean> = _isSyncingLibrary.asStateFlow()

    private val _lastSyncedAtMillis = MutableStateFlow(settingsRepository.loadLastSyncedAtMillis())
    val lastSyncedAtMillis: StateFlow<Long?> = _lastSyncedAtMillis.asStateFlow()

    // Per-stream retry trackers & server-verification failure overrides (prevents endless loops)
    private val watchedRetryTracker = StreamRetryTracker()
    private val watchlistRetryTracker = StreamRetryTracker()
    private val customListsRetryTracker = StreamRetryTracker()
    private val seasonsRetryTracker = StreamRetryTracker()
    private val settingsRetryTracker = StreamRetryTracker()

    private val _watchedRetryFailure = MutableStateFlow<Pair<Int, String>?>(null)
    private val _watchlistRetryFailure = MutableStateFlow<Pair<Int, String>?>(null)
    private val _customListsRetryFailure = MutableStateFlow<Pair<Int, String>?>(null)

    // Last known good cached lists in memory so offline mode always shows cached data instead of an error screen
    @Volatile
    private var lastCachedWatched: List<WatchedMovie>? = null

    @Volatile
    private var lastCachedWatchlist: List<WatchlistItem>? = null

    @Volatile
    private var lastCachedCustomLists: List<CustomList>? = null

    private data class CachedDiscoverResponse(
        val movies: List<MovieSummary>,
        val isSearchResult: Boolean,
        val mediaFilter: DiscoverMediaFilter,
    )

    @Volatile
    private var lastSuccessfulDiscover: CachedDiscoverResponse? = null

    @Volatile
    private var latestDiscoverState: DiscoverUiState = DiscoverUiState.Loading

    @Volatile
    private var lastDiscoverRequestKey: Pair<String, DiscoverMediaFilter>? = null

    private val _connectionCheckState = MutableStateFlow(ConnectionCheckUiState())
    val connectionCheckState: StateFlow<ConnectionCheckUiState> = _connectionCheckState.asStateFlow()

    private fun markSyncSucceeded() {
        val now = System.currentTimeMillis()
        _lastSyncedAtMillis.value = now
        settingsRepository.saveLastSyncedAtMillis(now)
    }

    private fun beginSyncOperation() {
        val count = activeSyncOpsCount.incrementAndGet()
        if (count > 0) {
            _isSyncingLibrary.value = true
        }
    }

    private fun endSyncOperation() {
        val count = activeSyncOpsCount.decrementAndGet().coerceAtLeast(0)
        if (count == 0) {
            _isSyncingLibrary.value = false
        }
    }

    private fun emitReAuthRequired(reason: String) {
        Log.e(TAG, "Auth required ($reason): opening login screen with \"$REAUTH_REQUIRED_MESSAGE\"")
        _reAuthRequiredEvents.tryEmit(REAUTH_REQUIRED_MESSAGE)
    }

    /**
     * Executes the 3-step Firestore listener retry:
     * 1. Refresh the Firebase ID token (`getIdToken(true)`).
     * 2. Re-attach the snapshot listener via the repository retry trigger.
     * 3. Run one read-only `Source.SERVER` fetch to confirm access (never deletes or overwrites data).
     */
    private fun launchSingleStreamRetry(
        streamName: String,
        tracker: StreamRetryTracker,
        currentRetryCount: Int,
        retryFailureState: MutableStateFlow<Pair<Int, String>?>?,
        reattachListener: () -> Int,
        verifyServerAccess: suspend (uid: String) -> Result<Unit>,
        onUnrecoverableError: ((String) -> Unit)? = null,
    ) {
        if (tracker.inFlightRetry) return
        tracker.inFlightRetry = true
        beginSyncOperation()

        viewModelScope.launch {
            try {
                val user = auth.currentUser
                if (user == null) {
                    emitReAuthRequired("$streamName retry with null FirebaseUser")
                    retryFailureState?.value = currentRetryCount to REAUTH_REQUIRED_MESSAGE
                    onUnrecoverableError?.invoke(REAUTH_REQUIRED_MESSAGE)
                    return@launch
                }

                // Step 1: Refresh ID token
                try {
                    tokenRefresher(user)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "ID token refresh failed for [$streamName]: ${e::class.java.name}: ${e.message}",
                        e,
                    )
                    val techError = formatTechnicalFirestoreError(e, "Failed to refresh authentication token.")
                    if (auth.currentUser == null || isAuthSessionInvalidError(e)) {
                        emitReAuthRequired("$streamName token refresh invalid session")
                        retryFailureState?.value = currentRetryCount to REAUTH_REQUIRED_MESSAGE
                        onUnrecoverableError?.invoke(REAUTH_REQUIRED_MESSAGE)
                    } else {
                        retryFailureState?.value = currentRetryCount to techError
                        onUnrecoverableError?.invoke(techError)
                    }
                    return@launch
                }

                // Step 2: Re-attach the snapshot listener via repository retry trigger
                val newRetryCount = reattachListener()

                // Step 3: Do one read-only server fetch to confirm access (never modifies data)
                val verifyResult = verifyServerAccess(user.uid)
                verifyResult.fold(
                    onSuccess = {
                        retryFailureState?.value = null
                        markSyncSucceeded()
                    },
                    onFailure = { error ->
                        Log.e(
                            TAG,
                            "Server access verification failed for [$streamName]: ${error::class.java.name}: ${error.message}",
                            error,
                        )
                        if (auth.currentUser == null || isAuthSessionInvalidError(error)) {
                            emitReAuthRequired("$streamName server verification unauthenticated")
                            retryFailureState?.value = newRetryCount to REAUTH_REQUIRED_MESSAGE
                            onUnrecoverableError?.invoke(REAUTH_REQUIRED_MESSAGE)
                        } else if (isOnline.value) {
                            val techError = formatTechnicalFirestoreError(error, "Unable to confirm server access.")
                            retryFailureState?.value = newRetryCount to techError
                            onUnrecoverableError?.invoke(techError)
                        } else {
                            // Offline: clear error override so cached data + Offline banner remain visible
                            retryFailureState?.value = null
                        }
                    },
                )
            } finally {
                tracker.inFlightRetry = false
                endSyncOperation()
            }
        }
    }

    /**
     * Manual full library sync trigger (used by Top Bar sync icon, Settings Sync button,
     * Pull-to-refresh on Watched, and "Sync now" on error states).
     * Never deletes or overwrites any user data.
     */
    fun syncNow() {
        if (_isSyncingLibrary.value) return
        if (!isOnline.value) {
            emitSnackbar("Offline — showing cached data. Sync will resume when back online.")
            return
        }
        val user = auth.currentUser
        if (user == null) {
            emitReAuthRequired("manual syncNow with null FirebaseUser")
            return
        }

        // Mark auto-retry as used so a manual retry won't silently cascade into a second automatic retry
        listOf(
            watchedRetryTracker,
            watchlistRetryTracker,
            customListsRetryTracker,
            seasonsRetryTracker,
            settingsRetryTracker,
        ).forEach { tracker ->
            tracker.autoRetryUsedForSession = true
            tracker.inFlightRetry = true
        }
        _watchedRetryFailure.value = null
        _watchlistRetryFailure.value = null
        _customListsRetryFailure.value = null

        beginSyncOperation()
        viewModelScope.launch {
            try {
                // Step 1: Refresh ID token once
                try {
                    tokenRefresher(user)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Manual syncNow getIdToken(true) failed: ${e::class.java.name}: ${e.message}",
                        e,
                    )
                    if (auth.currentUser == null || isAuthSessionInvalidError(e)) {
                        emitReAuthRequired("manual syncNow token refresh invalid session")
                        return@launch
                    }
                    val techError = formatTechnicalFirestoreError(e, "Failed to refresh authentication token.")
                    _watchedRetryFailure.value = watchedRepository.retryTrigger.value to techError
                    _watchlistRetryFailure.value = watchlistRepository.retryTrigger.value to techError
                    _customListsRetryFailure.value = customListRepository.retryTrigger.value to techError
                    return@launch
                }

                // Step 2: Re-attach all Firestore snapshot listeners via repository retry triggers
                watchedRepository.triggerRetry()
                val watchedRetryGen = watchedRepository.retryTrigger.value
                watchlistRepository.triggerRetry()
                val watchlistRetryGen = watchlistRepository.retryTrigger.value
                customListRepository.triggerRetry()
                val listsRetryGen = customListRepository.retryTrigger.value
                settingsRepository.triggerRetry()
                val activeTvId = _movieModal.value?.takeIf { it.mediaType == MediaType.TV }?.movieId
                if (activeTvId != null) {
                    tvProgressRepository.triggerRetry()
                }

                // Step 3: Run one read-only Source.SERVER fetch per collection to confirm access
                val uid = user.uid
                val results = coroutineScope {
                    val watchedDeferred = async { watchedRepository.verifyServerAccess(uid) }
                    val watchlistDeferred = async { watchlistRepository.verifyServerAccess(uid) }
                    val listsDeferred = async { customListRepository.verifyServerAccess(uid) }
                    val settingsDeferred = async { settingsRepository.verifyServerAccess(uid) }
                    val seasonsDeferred = activeTvId?.let { tvId ->
                        async { tvProgressRepository.verifyServerAccess(uid, tvId) }
                    }
                    listOfNotNull(
                        "watched" to watchedDeferred.await(),
                        "watchlist" to watchlistDeferred.await(),
                        "lists" to listsDeferred.await(),
                        "settings" to settingsDeferred.await(),
                        seasonsDeferred?.let { "seasons" to it.await() },
                    )
                }

                var anySuccess = false
                var authLost = false
                results.forEach { (streamName, result) ->
                    result.fold(
                        onSuccess = { anySuccess = true },
                        onFailure = { error ->
                            Log.e(
                                TAG,
                                "Manual syncNow server fetch [$streamName] failed: ${error::class.java.name}: ${error.message}",
                                error,
                            )
                            if (auth.currentUser == null || isAuthSessionInvalidError(error)) {
                                authLost = true
                            } else if (isOnline.value) {
                                val techError = formatTechnicalFirestoreError(error, "Unable to sync $streamName.")
                                when (streamName) {
                                    "watched" -> _watchedRetryFailure.value = watchedRetryGen to techError
                                    "watchlist" -> _watchlistRetryFailure.value = watchlistRetryGen to techError
                                    "lists" -> _customListsRetryFailure.value = listsRetryGen to techError
                                }
                            }
                        },
                    )
                }

                if (authLost || auth.currentUser == null) {
                    emitReAuthRequired("manual syncNow auth lost during server fetch")
                } else if (anySuccess) {
                    markSyncSucceeded()
                    val failedStreams = results.count { it.second.isFailure }
                    if (failedStreams == 0) {
                        emitSnackbar("Library synced.")
                    }
                }
            } finally {
                listOf(
                    watchedRetryTracker,
                    watchlistRetryTracker,
                    customListsRetryTracker,
                    seasonsRetryTracker,
                    settingsRetryTracker,
                ).forEach { it.inFlightRetry = false }
                endSyncOperation()
            }
        }
    }

    fun retryWatchedSync() {
        if (!isOnline.value) {
            emitSnackbar("Offline — showing cached data.")
            return
        }
        watchedRetryTracker.autoRetryUsedForSession = true
        _watchedRetryFailure.value = null
        launchSingleStreamRetry(
            streamName = "watched",
            tracker = watchedRetryTracker,
            currentRetryCount = watchedRepository.retryTrigger.value,
            retryFailureState = _watchedRetryFailure,
            reattachListener = {
                watchedRepository.triggerRetry()
                watchedRepository.retryTrigger.value
            },
            verifyServerAccess = { uid -> watchedRepository.verifyServerAccess(uid) },
        )
    }

    fun retryWatchlistSync() {
        if (!isOnline.value) {
            emitSnackbar("Offline — showing cached data.")
            return
        }
        watchlistRetryTracker.autoRetryUsedForSession = true
        _watchlistRetryFailure.value = null
        launchSingleStreamRetry(
            streamName = "watchlist",
            tracker = watchlistRetryTracker,
            currentRetryCount = watchlistRepository.retryTrigger.value,
            retryFailureState = _watchlistRetryFailure,
            reattachListener = {
                watchlistRepository.triggerRetry()
                watchlistRepository.retryTrigger.value
            },
            verifyServerAccess = { uid -> watchlistRepository.verifyServerAccess(uid) },
        )
    }

    fun retryCustomListsSync() {
        if (!isOnline.value) {
            emitSnackbar("Offline — showing cached data.")
            return
        }
        customListsRetryTracker.autoRetryUsedForSession = true
        _customListsRetryFailure.value = null
        launchSingleStreamRetry(
            streamName = "lists",
            tracker = customListsRetryTracker,
            currentRetryCount = customListRepository.retryTrigger.value,
            retryFailureState = _customListsRetryFailure,
            reattachListener = {
                customListRepository.triggerRetry()
                customListRepository.retryTrigger.value
            },
            verifyServerAccess = { uid -> customListRepository.verifyServerAccess(uid) },
        )
    }

    fun retrySeasonsSync(tvId: Long) {
        if (!isOnline.value) {
            emitSnackbar("Offline — showing cached data.")
            return
        }
        seasonsRetryTracker.autoRetryUsedForSession = true
        launchSingleStreamRetry(
            streamName = "seasons($tvId)",
            tracker = seasonsRetryTracker,
            currentRetryCount = tvProgressRepository.retryTrigger.value,
            retryFailureState = null,
            reattachListener = {
                tvProgressRepository.triggerRetry()
                tvProgressRepository.retryTrigger.value
            },
            verifyServerAccess = { uid -> tvProgressRepository.verifyServerAccess(uid, tvId) },
            onUnrecoverableError = { msg -> emitSnackbar(msg) },
        )
    }

    fun retrySettingsSync() {
        if (!isOnline.value) return
        settingsRetryTracker.autoRetryUsedForSession = true
        launchSingleStreamRetry(
            streamName = "settings",
            tracker = settingsRetryTracker,
            currentRetryCount = settingsRepository.retryTrigger.value,
            retryFailureState = null,
            reattachListener = {
                settingsRepository.triggerRetry()
                settingsRepository.retryTrigger.value
            },
            verifyServerAccess = { uid -> settingsRepository.verifyServerAccess(uid) },
        )
    }

    private fun emitSnackbar(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        _snackbarMessages.tryEmit(message)
        _snackbarEvents.tryEmit(
            CineSnackbarEvent(
                message = message,
                actionLabel = actionLabel,
                onAction = onAction,
            )
        )
    }

    val isTmdbKeyConfigured: Boolean
        get() = tmdbRepository.isApiKeyConfigured()

    init {
        viewModelScope.launch {
            settingsRepository.observeSettingsForAuthUser()
                .collect { state ->
                    when (state) {
                        is FirestoreSnapshotState.Unauthenticated -> {
                            settingsRetryTracker.resetForUid(null)
                        }
                        is FirestoreSnapshotState.Loading -> {
                            if (settingsRetryTracker.activeUid != state.uid) {
                                settingsRetryTracker.resetForUid(state.uid)
                            }
                        }
                        is FirestoreSnapshotState.Data -> {
                            if (settingsRetryTracker.activeUid != state.uid) {
                                settingsRetryTracker.resetForUid(state.uid)
                            }
                            if (!state.isFromCache) {
                                settingsRetryTracker.autoRetryUsedForSession = false
                                markSyncSucceeded()
                            }
                            val remote = state.value
                            if (remote != null) {
                                val preset = AccentPreset.fromKey(remote.accentPreset)
                                val hex = UserSettingsRepository.normalizeHexColor(remote.accentHex) ?: preset.defaultHex
                                val pref = LocalAccentPreference(preset = preset, hex = hex)
                                _accentPreference.value = pref
                                settingsRepository.saveLocalAccent(preset, hex)
                                _tvMigrationCompleted.value = remote.tvMigrationCompleted
                            } else if (_tvMigrationCompleted.value == null) {
                                _tvMigrationCompleted.value = false
                            }
                        }
                        is FirestoreSnapshotState.Error -> {
                            if (auth.currentUser == null) {
                                emitReAuthRequired("settings listener error with null user")
                            } else if (
                                isOnline.value &&
                                !settingsRetryTracker.autoRetryUsedForSession &&
                                !settingsRetryTracker.inFlightRetry &&
                                settingsRetryTracker.lastHandledErrorRetryCount != state.retryCount
                            ) {
                                settingsRetryTracker.autoRetryUsedForSession = true
                                settingsRetryTracker.lastHandledErrorRetryCount = state.retryCount
                                launchSingleStreamRetry(
                                    streamName = "settings",
                                    tracker = settingsRetryTracker,
                                    currentRetryCount = state.retryCount,
                                    retryFailureState = null,
                                    reattachListener = {
                                        settingsRepository.triggerRetry()
                                        settingsRepository.retryTrigger.value
                                    },
                                    verifyServerAccess = { uid -> settingsRepository.verifyServerAccess(uid) },
                                )
                            }
                        }
                    }
                }
        }
    }

    // 500ms debounced online search across Movies & TV Shows via TMDB API
    val discoverState: StateFlow<DiscoverUiState> = combine(
        _searchQuery
            .map { it.trim() }
            .debounce(500L)
            .distinctUntilChanged(),
        _discoverMediaFilter,
        _refreshTrigger,
    ) { query, mediaFilter, _ -> query to mediaFilter }
        .flatMapLatest { (query, mediaFilter) ->
            flow {
                val requestKey = query to mediaFilter
                val isRetryOfSameRequest = lastDiscoverRequestKey == requestKey
                lastDiscoverRequestKey = requestKey

                when (val current = latestDiscoverState) {
                    is DiscoverUiState.Error -> {
                        if (isRetryOfSameRequest) {
                            emit(current.copy(isRetrying = true))
                        } else {
                            emit(DiscoverUiState.Loading)
                        }
                    }
                    is DiscoverUiState.Success -> {
                        if (current.isOfflineData && isRetryOfSameRequest) {
                            emit(current.copy(isRetrying = true))
                        } else {
                            emit(DiscoverUiState.Loading)
                        }
                    }
                    is DiscoverUiState.Loading -> emit(DiscoverUiState.Loading)
                }

                val result = if (query.isBlank()) {
                    when (mediaFilter) {
                        DiscoverMediaFilter.ALL -> tmdbRepository.getTrendingAllMedia()
                        DiscoverMediaFilter.MOVIES -> tmdbRepository.getTrendingMovies()
                        DiscoverMediaFilter.TV_SHOWS -> tmdbRepository.getTrendingTvShows()
                    }
                } else {
                    when (mediaFilter) {
                        DiscoverMediaFilter.ALL -> tmdbRepository.searchAllMedia(query)
                        DiscoverMediaFilter.MOVIES -> tmdbRepository.searchMovies(query)
                        DiscoverMediaFilter.TV_SHOWS -> tmdbRepository.searchTvShows(query)
                    }
                }
                result.fold(
                    onSuccess = { items ->
                        val isSearch = query.isNotBlank()
                        lastSuccessfulDiscover = CachedDiscoverResponse(
                            movies = items,
                            isSearchResult = isSearch,
                            mediaFilter = mediaFilter,
                        )
                        emit(
                            DiscoverUiState.Success(
                                movies = items,
                                isSearchResult = isSearch,
                                mediaFilter = mediaFilter,
                                isOfflineData = false,
                                isRetrying = false,
                            )
                        )
                        prefetchProvidersForCards(items.take(8))
                    },
                    onFailure = { error ->
                        Log.e(
                            TAG,
                            "Discover TMDB load failed (query='$query', filter=$mediaFilter): " +
                                "${error::class.java.name}: ${error.message}",
                            error,
                        )
                        val isMissingKey = error is TmdbApiKeyMissingException
                        val cached = lastSuccessfulDiscover
                        if (!isMissingKey && cached != null) {
                            emit(
                                DiscoverUiState.Success(
                                    movies = cached.movies,
                                    isSearchResult = cached.isSearchResult,
                                    mediaFilter = cached.mediaFilter,
                                    isOfflineData = true,
                                    isRetrying = false,
                                )
                            )
                        } else {
                            val technicalDetails = formatTmdbTechnicalError(error)
                            val displayMessage = if (isMissingKey) {
                                error.localizedMessage ?: "TMDB API key is not configured."
                            } else {
                                DISCOVER_ERROR_MESSAGE
                            }
                            emit(
                                DiscoverUiState.Error(
                                    message = displayMessage,
                                    technicalDetails = technicalDetails,
                                    isMissingApiKey = isMissingKey,
                                    isRetrying = false,
                                )
                            )
                        }
                    }
                )
            }
        }
        .onEach { latestDiscoverState = it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DiscoverUiState.Loading,
        )

    val discoverLanguageChips: StateFlow<List<LanguageChipCount>> = discoverState
        .map { state ->
            when (state) {
                is DiscoverUiState.Success ->
                    StatsCalculator.buildLanguageChips(state.movies.map { it.originalLanguage })
                else -> emptyList()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    val filteredDiscoverMovies: StateFlow<List<MovieSummary>> = combine(
        discoverState,
        _discoverLanguageFilter,
    ) { state, langFilter ->
        val raw = (state as? DiscoverUiState.Success)?.movies.orEmpty()
        if (langFilter == "all") raw
        else raw.filter { it.originalLanguage.equals(langFilter, ignoreCase = true) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
    )

    // Real-time auth-gated Firestore watched list with single auto-retry and offline cached fallback
    val watchedState: StateFlow<WatchedUiState> = combine(
        watchedRepository.observeWatchedMoviesForAuthUser(),
        _watchedRetryFailure,
        isOnline,
    ) { snapshotState, retryFailure, online ->
        when (snapshotState) {
            is FirestoreSnapshotState.Unauthenticated -> {
                watchedRetryTracker.resetForUid(null)
                _watchedRetryFailure.value = null
                if (auth.currentUser == null) {
                    emitReAuthRequired("watched stream unauthenticated")
                }
                WatchedUiState.Loading
            }
            is FirestoreSnapshotState.Loading -> {
                if (watchedRetryTracker.activeUid != snapshotState.uid) {
                    watchedRetryTracker.resetForUid(snapshotState.uid)
                    _watchedRetryFailure.value = null
                }
                val failureForGen = retryFailure?.takeIf { it.first == snapshotState.retryCount }
                if (failureForGen != null && online) {
                    WatchedUiState.Error(failureForGen.second)
                } else if (!online && lastCachedWatched != null) {
                    WatchedUiState.Success(lastCachedWatched.orEmpty())
                } else {
                    WatchedUiState.Loading
                }
            }
            is FirestoreSnapshotState.Data -> {
                if (watchedRetryTracker.activeUid != snapshotState.uid) {
                    watchedRetryTracker.resetForUid(snapshotState.uid)
                }
                lastCachedWatched = snapshotState.value
                if (!snapshotState.isFromCache) {
                    watchedRetryTracker.autoRetryUsedForSession = false
                    _watchedRetryFailure.value = null
                    markSyncSucceeded()
                }
                val failureForGen = retryFailure?.takeIf { it.first == snapshotState.retryCount }
                if (online && snapshotState.isFromCache && failureForGen != null) {
                    WatchedUiState.Error(failureForGen.second)
                } else {
                    WatchedUiState.Success(snapshotState.value)
                }
            }
            is FirestoreSnapshotState.Error -> {
                if (!online) {
                    // Offline: show cached data with Offline banner instead of an error screen
                    WatchedUiState.Success(lastCachedWatched.orEmpty())
                } else if (auth.currentUser == null) {
                    emitReAuthRequired("watched listener error with null FirebaseUser")
                    WatchedUiState.Error(REAUTH_REQUIRED_MESSAGE)
                } else {
                    val failureForGen = retryFailure?.takeIf { it.first == snapshotState.retryCount }
                    if (failureForGen != null) {
                        WatchedUiState.Error(failureForGen.second)
                    } else if (
                        !watchedRetryTracker.autoRetryUsedForSession &&
                        !watchedRetryTracker.inFlightRetry &&
                        watchedRetryTracker.lastHandledErrorRetryCount != snapshotState.retryCount
                    ) {
                        watchedRetryTracker.autoRetryUsedForSession = true
                        watchedRetryTracker.lastHandledErrorRetryCount = snapshotState.retryCount
                        launchSingleStreamRetry(
                            streamName = "watched",
                            tracker = watchedRetryTracker,
                            currentRetryCount = snapshotState.retryCount,
                            retryFailureState = _watchedRetryFailure,
                            reattachListener = {
                                watchedRepository.triggerRetry()
                                watchedRepository.retryTrigger.value
                            },
                            verifyServerAccess = { uid -> watchedRepository.verifyServerAccess(uid) },
                        )
                        WatchedUiState.Loading
                    } else if (watchedRetryTracker.inFlightRetry) {
                        WatchedUiState.Loading
                    } else {
                        val techMsg = formatTechnicalFirestoreError(
                            snapshotState.error,
                            "Failed to sync watched library.",
                        )
                        val errorKey = "${snapshotState.uid}:${snapshotState.retryCount}:$techMsg"
                        if (watchedRetryTracker.lastNotifiedErrorKey != errorKey) {
                            watchedRetryTracker.lastNotifiedErrorKey = errorKey
                            _snackbarMessages.tryEmit("Couldn't sync your library.")
                        }
                        WatchedUiState.Error(techMsg)
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = WatchedUiState.Loading,
    )

    val watchedMap: StateFlow<Map<String, WatchedMovie>> = watchedState
        .map { state ->
            when (state) {
                is WatchedUiState.Success -> state.movies.associateBy { it.uniqueKey }
                else -> emptyMap()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap(),
        )

    val watchedCount: StateFlow<Int> = watchedMap
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0,
        )

    val watchedLanguageChips: StateFlow<List<LanguageChipCount>> = watchedState
        .map { state ->
            when (state) {
                is WatchedUiState.Success ->
                    StatsCalculator.buildLanguageChips(state.movies.map { it.originalLanguage })
                else -> emptyList()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    val filteredWatchedMovies: StateFlow<List<WatchedMovie>> = combine(
        watchedState,
        _sortOption,
        _filterOption,
        _watchedLanguageFilter,
        _watchedReactionFilter,
        _watchedSearchQuery,
    ) { values ->
        val state = values[0] as WatchedUiState
        val sort = values[1] as WatchedSortOption
        val filter = values[2] as WatchedFilterOption
        val langFilter = values[3] as String
        val reactionFilter = values[4] as String
        val query = values[5] as String

        val raw = (state as? WatchedUiState.Success)?.movies.orEmpty()
        val filtered = raw.filter { item ->
            val matchesFilter = when (filter) {
                WatchedFilterOption.ALL -> true
                WatchedFilterOption.MOVIES -> !item.isTv
                WatchedFilterOption.TV_SHOWS -> item.isTv
                WatchedFilterOption.WATCHING -> item.isTv && item.resolvedTvStatus == TvWatchStatus.WATCHING
                WatchedFilterOption.COMPLETED -> item.isTv && item.resolvedTvStatus == TvWatchStatus.COMPLETED
                WatchedFilterOption.RATED_ONLY -> item.userRating > 0
                WatchedFilterOption.FIVE_STARS -> item.userRating == 5
            }
            val matchesLang = langFilter == "all" || item.originalLanguage.equals(langFilter, ignoreCase = true)
            val matchesReaction = reactionFilter.isEmpty() || item.reaction == reactionFilter
            val q = query.trim()
            val matchesQuery = q.isEmpty() ||
                item.title.contains(q, ignoreCase = true) ||
                item.note.contains(q, ignoreCase = true)
            matchesFilter && matchesLang && matchesReaction && matchesQuery
        }
        when (sort) {
            WatchedSortOption.RECENT -> filtered.sortedByDescending {
                it.watchedAt ?: Timestamp(0, 0)
            }
            WatchedSortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
            WatchedSortOption.USER_RATING -> filtered.sortedWith(
                compareByDescending<WatchedMovie> { it.userRating }
                    .thenByDescending { it.watchedAt ?: Timestamp(0, 0) }
            )
            WatchedSortOption.TMDB_RATING -> filtered.sortedByDescending { it.rating }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
    )

    // Watch Diary index: yyyy-MM-dd -> List<WatchedMovie>
    val watchDiaryByDate: StateFlow<Map<String, List<WatchedMovie>>> = watchedState
        .map { state ->
            val movies = (state as? WatchedUiState.Success)?.movies.orEmpty()
            val map = linkedMapOf<String, MutableList<WatchedMovie>>()
            movies.forEach { item ->
                item.resolvedWatchDates.distinct().forEach { date ->
                    map.getOrPut(date) { mutableListOf() }.add(item)
                }
            }
            map
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap(),
        )

    // Real-time auth-gated Firestore Watchlist with single auto-retry and offline cached fallback
    val watchlistState: StateFlow<WatchlistUiState> = combine(
        watchlistRepository.observeWatchlistForAuthUser(),
        _watchlistRetryFailure,
        isOnline,
    ) { snapshotState, retryFailure, online ->
        when (snapshotState) {
            is FirestoreSnapshotState.Unauthenticated -> {
                watchlistRetryTracker.resetForUid(null)
                _watchlistRetryFailure.value = null
                WatchlistUiState.Loading
            }
            is FirestoreSnapshotState.Loading -> {
                if (watchlistRetryTracker.activeUid != snapshotState.uid) {
                    watchlistRetryTracker.resetForUid(snapshotState.uid)
                    _watchlistRetryFailure.value = null
                }
                val failureForGen = retryFailure?.takeIf { it.first == snapshotState.retryCount }
                if (failureForGen != null && online) {
                    WatchlistUiState.Error(failureForGen.second)
                } else if (!online && lastCachedWatchlist != null) {
                    WatchlistUiState.Success(lastCachedWatchlist.orEmpty())
                } else {
                    WatchlistUiState.Loading
                }
            }
            is FirestoreSnapshotState.Data -> {
                if (watchlistRetryTracker.activeUid != snapshotState.uid) {
                    watchlistRetryTracker.resetForUid(snapshotState.uid)
                }
                val sorted = snapshotState.value.sortedByDescending { it.addedAt ?: Timestamp(0, 0) }
                lastCachedWatchlist = sorted
                if (!snapshotState.isFromCache) {
                    watchlistRetryTracker.autoRetryUsedForSession = false
                    _watchlistRetryFailure.value = null
                    markSyncSucceeded()
                }
                val failureForGen = retryFailure?.takeIf { it.first == snapshotState.retryCount }
                if (online && snapshotState.isFromCache && failureForGen != null) {
                    WatchlistUiState.Error(failureForGen.second)
                } else {
                    WatchlistUiState.Success(sorted)
                }
            }
            is FirestoreSnapshotState.Error -> {
                if (!online) {
                    WatchlistUiState.Success(lastCachedWatchlist.orEmpty())
                } else if (auth.currentUser == null) {
                    emitReAuthRequired("watchlist listener error with null FirebaseUser")
                    WatchlistUiState.Error(REAUTH_REQUIRED_MESSAGE)
                } else {
                    val failureForGen = retryFailure?.takeIf { it.first == snapshotState.retryCount }
                    if (failureForGen != null) {
                        WatchlistUiState.Error(failureForGen.second)
                    } else if (
                        !watchlistRetryTracker.autoRetryUsedForSession &&
                        !watchlistRetryTracker.inFlightRetry &&
                        watchlistRetryTracker.lastHandledErrorRetryCount != snapshotState.retryCount
                    ) {
                        watchlistRetryTracker.autoRetryUsedForSession = true
                        watchlistRetryTracker.lastHandledErrorRetryCount = snapshotState.retryCount
                        launchSingleStreamRetry(
                            streamName = "watchlist",
                            tracker = watchlistRetryTracker,
                            currentRetryCount = snapshotState.retryCount,
                            retryFailureState = _watchlistRetryFailure,
                            reattachListener = {
                                watchlistRepository.triggerRetry()
                                watchlistRepository.retryTrigger.value
                            },
                            verifyServerAccess = { uid -> watchlistRepository.verifyServerAccess(uid) },
                        )
                        WatchlistUiState.Loading
                    } else if (watchlistRetryTracker.inFlightRetry) {
                        WatchlistUiState.Loading
                    } else {
                        val techMsg = formatTechnicalFirestoreError(
                            snapshotState.error,
                            "Failed to sync watchlist.",
                        )
                        WatchlistUiState.Error(techMsg)
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = WatchlistUiState.Loading,
    )

    val watchlistMap: StateFlow<Map<String, WatchlistItem>> = watchlistState
        .map { state ->
            when (state) {
                is WatchlistUiState.Success -> state.items.associateBy { it.uniqueKey }
                else -> emptyMap()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap(),
        )

    val watchlistCount: StateFlow<Int> = watchlistMap
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0,
        )

    // Real-time auth-gated Firestore Custom Lists with single auto-retry and offline cached fallback
    val customListsState: StateFlow<CustomListsUiState> = combine(
        customListRepository.observeListsForAuthUser(),
        _customListsRetryFailure,
        isOnline,
    ) { snapshotState, retryFailure, online ->
        when (snapshotState) {
            is FirestoreSnapshotState.Unauthenticated -> {
                customListsRetryTracker.resetForUid(null)
                _customListsRetryFailure.value = null
                CustomListsUiState.Loading
            }
            is FirestoreSnapshotState.Loading -> {
                if (customListsRetryTracker.activeUid != snapshotState.uid) {
                    customListsRetryTracker.resetForUid(snapshotState.uid)
                    _customListsRetryFailure.value = null
                }
                val failureForGen = retryFailure?.takeIf { it.first == snapshotState.retryCount }
                if (failureForGen != null && online) {
                    CustomListsUiState.Error(failureForGen.second)
                } else if (!online && lastCachedCustomLists != null) {
                    CustomListsUiState.Success(lastCachedCustomLists.orEmpty())
                } else {
                    CustomListsUiState.Loading
                }
            }
            is FirestoreSnapshotState.Data -> {
                if (customListsRetryTracker.activeUid != snapshotState.uid) {
                    customListsRetryTracker.resetForUid(snapshotState.uid)
                }
                val sorted = snapshotState.value.sortedByDescending {
                    it.updatedAt ?: it.createdAt ?: Timestamp(0, 0)
                }
                lastCachedCustomLists = sorted
                if (!snapshotState.isFromCache) {
                    customListsRetryTracker.autoRetryUsedForSession = false
                    _customListsRetryFailure.value = null
                    markSyncSucceeded()
                }
                val failureForGen = retryFailure?.takeIf { it.first == snapshotState.retryCount }
                if (online && snapshotState.isFromCache && failureForGen != null) {
                    CustomListsUiState.Error(failureForGen.second)
                } else {
                    CustomListsUiState.Success(sorted)
                }
            }
            is FirestoreSnapshotState.Error -> {
                if (!online) {
                    CustomListsUiState.Success(lastCachedCustomLists.orEmpty())
                } else if (auth.currentUser == null) {
                    emitReAuthRequired("lists listener error with null FirebaseUser")
                    CustomListsUiState.Error(REAUTH_REQUIRED_MESSAGE)
                } else {
                    val failureForGen = retryFailure?.takeIf { it.first == snapshotState.retryCount }
                    if (failureForGen != null) {
                        CustomListsUiState.Error(failureForGen.second)
                    } else if (
                        !customListsRetryTracker.autoRetryUsedForSession &&
                        !customListsRetryTracker.inFlightRetry &&
                        customListsRetryTracker.lastHandledErrorRetryCount != snapshotState.retryCount
                    ) {
                        customListsRetryTracker.autoRetryUsedForSession = true
                        customListsRetryTracker.lastHandledErrorRetryCount = snapshotState.retryCount
                        launchSingleStreamRetry(
                            streamName = "lists",
                            tracker = customListsRetryTracker,
                            currentRetryCount = snapshotState.retryCount,
                            retryFailureState = _customListsRetryFailure,
                            reattachListener = {
                                customListRepository.triggerRetry()
                                customListRepository.retryTrigger.value
                            },
                            verifyServerAccess = { uid -> customListRepository.verifyServerAccess(uid) },
                        )
                        CustomListsUiState.Loading
                    } else if (customListsRetryTracker.inFlightRetry) {
                        CustomListsUiState.Loading
                    } else {
                        val techMsg = formatTechnicalFirestoreError(
                            snapshotState.error,
                            "Failed to sync custom lists.",
                        )
                        CustomListsUiState.Error(techMsg)
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CustomListsUiState.Loading,
    )

    val customLists: StateFlow<List<CustomList>> = customListsState
        .map { state ->
            when (state) {
                is CustomListsUiState.Success -> state.lists
                else -> emptyList()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    val selectedCustomList: StateFlow<CustomList?> = combine(
        customLists,
        _selectedCustomListId,
    ) { lists, selectedId ->
        if (selectedId == null) null else lists.firstOrNull { it.id == selectedId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null,
    )

    // "For You" recommendations excluding already watched titles
    val forYouRecommendations: StateFlow<RecommendationsUiState> = combine(
        _recommendationsState,
        watchedMap,
    ) { recState, watched ->
        when (recState) {
            is RecommendationsUiState.Success -> {
                val filtered = recState.items.filterNot { watched.containsKey(it.uniqueKey) }
                recState.copy(items = filtered)
            }
            else -> recState
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = RecommendationsUiState.Idle,
    )

    // Stats computed live from watched list and segmented media filter
    val statsSummary: StateFlow<CineStatsSummary> = combine(
        watchedState,
        _statsMediaFilter,
    ) { state, filter ->
        when (state) {
            is WatchedUiState.Success -> StatsCalculator.computeStats(state.movies, filter)
            else -> CineStatsSummary()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CineStatsSummary(),
    )

    // Confirmed server-synced seasons subcollection observer for currently open TV modal
    private val activeModalTvIdFlow: Flow<Long?> = _movieModal
        .map { modal -> modal?.takeIf { it.mediaType == MediaType.TV }?.movieId }
        .distinctUntilChanged()

    private val confirmedModalTvSeasons: Flow<Map<Int, WatchedSeason>> = activeModalTvIdFlow
        .flatMapLatest { tvId ->
            if (tvId == null) {
                flowOf(emptyMap())
            } else {
                tvProgressRepository.observeSeasonsForAuthUser(tvId)
                    .map { snapshotState ->
                        when (snapshotState) {
                            is FirestoreSnapshotState.Unauthenticated -> {
                                seasonsRetryTracker.resetForUid(null)
                                emptyMap()
                            }
                            is FirestoreSnapshotState.Loading -> {
                                if (seasonsRetryTracker.activeUid != snapshotState.uid) {
                                    seasonsRetryTracker.resetForUid(snapshotState.uid)
                                }
                                emptyMap()
                            }
                            is FirestoreSnapshotState.Data -> {
                                if (seasonsRetryTracker.activeUid != snapshotState.uid) {
                                    seasonsRetryTracker.resetForUid(snapshotState.uid)
                                }
                                if (!snapshotState.isFromCache) {
                                    seasonsRetryTracker.autoRetryUsedForSession = false
                                    markSyncSucceeded()
                                }
                                val confirmedMap = snapshotState.value.associateBy { it.seasonNumber }
                                if (!_isSavingTvProgress.value) {
                                    reconcileOptimisticOverrides(tvId, confirmedMap)
                                }
                                confirmedMap
                            }
                            is FirestoreSnapshotState.Error -> {
                                if (auth.currentUser == null) {
                                    emitReAuthRequired("seasons($tvId) listener error with null user")
                                } else if (
                                    isOnline.value &&
                                    !seasonsRetryTracker.autoRetryUsedForSession &&
                                    !seasonsRetryTracker.inFlightRetry &&
                                    seasonsRetryTracker.lastHandledErrorRetryCount != snapshotState.retryCount
                                ) {
                                    seasonsRetryTracker.autoRetryUsedForSession = true
                                    seasonsRetryTracker.lastHandledErrorRetryCount = snapshotState.retryCount
                                    launchSingleStreamRetry(
                                        streamName = "seasons($tvId)",
                                        tracker = seasonsRetryTracker,
                                        currentRetryCount = snapshotState.retryCount,
                                        retryFailureState = null,
                                        reattachListener = {
                                            tvProgressRepository.triggerRetry()
                                            tvProgressRepository.retryTrigger.value
                                        },
                                        verifyServerAccess = { uid ->
                                            tvProgressRepository.verifyServerAccess(uid, tvId)
                                        },
                                        onUnrecoverableError = { msg -> emitSnackbar(msg) },
                                    )
                                } else if (isOnline.value) {
                                    emitSnackbar(TvProgressRepository.formatSaveError(snapshotState.error))
                                }
                                emptyMap()
                            }
                        }
                    }
            }
        }

    // Combined ViewModel UI state: confirmed server seasons + optimistic pending/committed overrides
    val modalTvSeasons: StateFlow<Map<Int, WatchedSeason>> = combine(
        activeModalTvIdFlow,
        confirmedModalTvSeasons,
        _optimisticTvOverrides,
    ) { tvId, confirmed, overridesByShow ->
        if (tvId == null) {
            emptyMap()
        } else {
            val overrides = overridesByShow[tvId].orEmpty()
            if (overrides.isEmpty()) {
                confirmed
            } else {
                val merged = confirmed.toMutableMap()
                overrides.forEach { (seasonNum, optDoc) ->
                    if (optDoc == null) {
                        merged.remove(seasonNum)
                    } else {
                        merged[seasonNum] = optDoc
                    }
                }
                merged
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyMap(),
    )

    private fun reconcileOptimisticOverrides(
        tvId: Long,
        confirmedMap: Map<Int, WatchedSeason>,
    ) {
        val currentOverrides = _optimisticTvOverrides.value[tvId] ?: return
        if (currentOverrides.isEmpty()) return
        val remaining = currentOverrides.filterNot { (seasonNum, optDoc) ->
            val confirmed = confirmedMap[seasonNum]
            if (optDoc == null) {
                confirmed == null
            } else {
                confirmed != null &&
                    confirmed.watched == optDoc.watched &&
                    confirmed.watchedEpisodes.sorted() == optDoc.watchedEpisodes.sorted()
            }
        }
        if (remaining.size != currentOverrides.size) {
            val updatedMap = _optimisticTvOverrides.value.toMutableMap()
            if (remaining.isEmpty()) {
                updatedMap.remove(tvId)
            } else {
                updatedMap[tvId] = remaining
            }
            _optimisticTvOverrides.value = updatedMap
        }
    }

    init {
        viewModelScope.launch {
            watchedState.collect { state ->
                if (state is WatchedUiState.Success) {
                    val topSeeds = state.movies
                        .filter { it.userRating >= 4 }
                        .sortedWith(
                            compareByDescending<WatchedMovie> { it.userRating }
                                .thenByDescending { it.watchedAt ?: Timestamp(0, 0) }
                        )
                        .take(3)
                    val seedKey = topSeeds.joinToString(",") { it.uniqueKey }
                    if (topSeeds.isNotEmpty() && seedKey != lastRecommendationSeedKey) {
                        lastRecommendationSeedKey = seedKey
                        loadRecommendationsFromSeeds(topSeeds)
                    } else if (topSeeds.isEmpty() && _recommendationsState.value !is RecommendationsUiState.Idle) {
                        lastRecommendationSeedKey = ""
                        _recommendationsState.value = RecommendationsUiState.Idle
                    }
                }
            }
        }

        // One-time background migration for legacy watched TV shows
        viewModelScope.launch {
            combine(
                watchedState,
                _tvMigrationCompleted,
                isOnline,
            ) { wState, migrationDone, online ->
                Triple(wState, migrationDone, online)
            }.collect { (wState, migrationDone, online) ->
                if (
                    !tvMigrationStartedThisSession &&
                    migrationDone == false &&
                    wState is WatchedUiState.Success
                ) {
                    val legacyShows = wState.movies.filter { it.isTv && it.status.isBlank() }
                    if (legacyShows.isEmpty()) {
                        tvMigrationStartedThisSession = true
                        settingsRepository.markTvMigrationCompleted()
                    } else if (online && isTmdbKeyConfigured) {
                        tvMigrationStartedThisSession = true
                        runOneTimeLegacyTvMigration(legacyShows)
                    }
                }
            }
        }
    }

    fun refreshRecommendations() {
        val movies = (watchedState.value as? WatchedUiState.Success)?.movies.orEmpty()
        val topSeeds = movies
            .filter { it.userRating >= 4 }
            .sortedWith(
                compareByDescending<WatchedMovie> { it.userRating }
                    .thenByDescending { it.watchedAt ?: Timestamp(0, 0) }
            )
            .take(3)
        if (topSeeds.isEmpty()) {
            _recommendationsState.value = RecommendationsUiState.Idle
            return
        }
        loadRecommendationsFromSeeds(topSeeds)
    }

    private fun loadRecommendationsFromSeeds(seeds: List<WatchedMovie>) {
        if (!isTmdbKeyConfigured || !isOnline.value) return
        viewModelScope.launch {
            _recommendationsState.value = RecommendationsUiState.Loading
            val watchedKeys = watchedMap.value.keys
            val results = coroutineScope {
                seeds.map { seed ->
                    async {
                        tmdbRepository.getRecommendations(seed.movieId, seed.resolvedMediaType)
                            .getOrNull()
                            .orEmpty()
                    }
                }.awaitAll()
            }
            val merged = results.flatten()
                .distinctBy { it.uniqueKey }
                .filterNot { watchedKeys.contains(it.uniqueKey) }
                .sortedByDescending { it.popularity }
                .take(16)

            _recommendationsState.value = RecommendationsUiState.Success(
                items = merged,
                seedTitles = seeds.map { it.title },
            )
        }
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun selectStatsMediaFilter(filter: StatsMediaFilter) {
        _statsMediaFilter.value = filter
    }

    fun selectWatchedSubTab(subTab: WatchedSubTab) {
        _watchedSubTab.value = subTab
    }

    fun selectListsSubTab(subTab: ListsSubTab) {
        _listsSubTab.value = subTab
    }

    fun changeDiaryMonth(deltaMonths: Int) {
        val parts = _diaryMonthYm.value.split('-')
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, deltaMonths)
        }
        val fmt = SimpleDateFormat("yyyy-MM", Locale.US)
        _diaryMonthYm.value = fmt.format(cal.time)
    }

    fun selectDiaryDate(isoDate: String) {
        _diarySelectedDate.value = isoDate
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onDiscoverMediaFilterSelected(filter: DiscoverMediaFilter) {
        _discoverMediaFilter.value = filter
        _discoverLanguageFilter.value = "all"
    }

    fun onDiscoverLanguageSelected(code: String) {
        _discoverLanguageFilter.value = code
    }

    fun retryDiscover() {
        val current = latestDiscoverState
        if ((current is DiscoverUiState.Error && current.isRetrying) ||
            (current is DiscoverUiState.Success && current.isRetrying)
        ) {
            return
        }
        _refreshTrigger.value += 1
    }

    /**
     * Tests connectivity to `api.themoviedb.org`, `image.tmdb.org`, and Firestore
     * and exposes OK or the error type for each target in Settings.
     */
    fun runConnectionCheck() {
        if (_connectionCheckState.value.isChecking) return
        _connectionCheckState.value = ConnectionCheckUiState(
            isChecking = true,
            hasRun = true,
            tmdbApi = ConnectionTargetStatus.Checking,
            tmdbImages = ConnectionTargetStatus.Checking,
            firestore = ConnectionTargetStatus.Checking,
        )

        viewModelScope.launch {
            coroutineScope {
                val apiDeferred = async {
                    val result = tmdbRepository.checkTmdbApiConnection()
                    result.fold(
                        onSuccess = { ConnectionTargetStatus.Ok },
                        onFailure = { error ->
                            Log.e(
                                TAG,
                                "Connection check [api.themoviedb.org] failed: ${error::class.java.name}: ${error.message}",
                                error,
                            )
                            ConnectionTargetStatus.Error(
                                errorType = classifyConnectionErrorType(error),
                                detail = error.message.orEmpty(),
                            )
                        },
                    )
                }

                val imagesDeferred = async {
                    val result = tmdbRepository.checkTmdbImageConnection()
                    result.fold(
                        onSuccess = { ConnectionTargetStatus.Ok },
                        onFailure = { error ->
                            Log.e(
                                TAG,
                                "Connection check [image.tmdb.org] failed: ${error::class.java.name}: ${error.message}",
                                error,
                            )
                            ConnectionTargetStatus.Error(
                                errorType = classifyConnectionErrorType(error),
                                detail = error.message.orEmpty(),
                            )
                        },
                    )
                }

                val firestoreDeferred = async {
                    val user = auth.currentUser
                    if (user == null) {
                        ConnectionTargetStatus.Error(
                            errorType = "Unauthenticated",
                            detail = REAUTH_REQUIRED_MESSAGE,
                        )
                    } else {
                        val result = watchedRepository.verifyServerAccess(user.uid)
                        result.fold(
                            onSuccess = { ConnectionTargetStatus.Ok },
                            onFailure = { error ->
                                Log.e(
                                    TAG,
                                    "Connection check [Firestore] failed: ${error::class.java.name}: ${error.message}",
                                    error,
                                )
                                ConnectionTargetStatus.Error(
                                    errorType = classifyConnectionErrorType(error),
                                    detail = error.message.orEmpty(),
                                )
                            },
                        )
                    }
                }

                _connectionCheckState.value = ConnectionCheckUiState(
                    isChecking = false,
                    hasRun = true,
                    tmdbApi = apiDeferred.await(),
                    tmdbImages = imagesDeferred.await(),
                    firestore = firestoreDeferred.await(),
                )
            }
        }
    }

    private fun formatTmdbTechnicalError(error: Throwable): String {
        val root = error.cause ?: error
        val className = root::class.java.simpleName.ifBlank { root::class.java.name }
        val rawMessage = root.message?.takeIf { it.isNotBlank() }
            ?: error.message?.takeIf { it.isNotBlank() }
            ?: "Unknown network error"
        return when (root) {
            is HttpException -> "HttpException (HTTP ${root.code()}): $rawMessage"
            is TmdbHttpStatusException -> "HTTP ${root.statusCode}: $rawMessage"
            else -> if (rawMessage.startsWith(className)) rawMessage else "$className: $rawMessage"
        }
    }

    private fun classifyConnectionErrorType(error: Throwable): String {
        val root = error.cause ?: error
        return when (root) {
            is TmdbApiKeyMissingException -> "Missing API key"
            is TmdbHttpStatusException -> "HTTP ${root.statusCode}"
            is HttpException -> "HTTP ${root.code()}"
            is FirebaseFirestoreException -> "Firestore (${root.code.name})"
            else -> root::class.java.simpleName.ifBlank { "Error" }
        }
    }

    fun onSortOptionSelected(option: WatchedSortOption) {
        _sortOption.value = option
    }

    fun onFilterOptionSelected(option: WatchedFilterOption) {
        _filterOption.value = option
    }

    fun onWatchedLanguageSelected(code: String) {
        _watchedLanguageFilter.value = code
    }

    fun onWatchedReactionSelected(reactionKey: String) {
        _watchedReactionFilter.value = if (_watchedReactionFilter.value == reactionKey) "" else reactionKey
    }

    fun onWatchedSearchQueryChanged(query: String) {
        _watchedSearchQuery.value = query
    }

    fun resetWatchedFilters() {
        _filterOption.value = WatchedFilterOption.ALL
        _watchedLanguageFilter.value = "all"
        _watchedReactionFilter.value = ""
        _watchedSearchQuery.value = ""
    }

    fun toggleWatched(movie: MovieSummary) {
        val key = movie.uniqueKey
        if (_pendingToggleKeys.value.contains(key)) return
        val currentlyWatched = watchedMap.value.containsKey(key)
        viewModelScope.launch {
            _pendingToggleKeys.value += key
            try {
                if (currentlyWatched) {
                    watchedRepository.removeFromWatched(movie.movieId, movie.mediaType).fold(
                        onSuccess = {
                            _snackbarMessages.tryEmit("Removed \"${movie.title}\" from watched.")
                        },
                        onFailure = { err ->
                            _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to remove item.")
                        }
                    )
                } else {
                    val modalDetails = _movieModal.value?.takeIf { it.uniqueKey == key }?.details
                    val details = modalDetails ?: if (isTmdbKeyConfigured && isOnline.value) {
                        tmdbRepository.getMediaDetails(
                            mediaId = movie.movieId,
                            mediaType = movie.mediaType,
                            includeImagesAndProviders = false,
                        ).getOrNull()
                    } else null

                    val result = if (movie.mediaType == MediaType.TV && details != null && details.seasons.isNotEmpty()) {
                        markTvShowFullyWatchedInternal(
                            summary = movie,
                            details = details,
                            existingWatched = null,
                        )
                    } else {
                        watchedRepository.markAsWatched(movie, details)
                    }
                    result.fold(
                        onSuccess = {
                            if (watchlistMap.value.containsKey(key)) {
                                watchlistRepository.removeFromWatchlist(movie.movieId, movie.mediaType)
                            }
                            _snackbarMessages.tryEmit("Marked \"${movie.title}\" as watched.")
                        },
                        onFailure = { err ->
                            _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to save watched item.")
                        }
                    )
                }
            } finally {
                _pendingToggleKeys.value -= key
            }
        }
    }

    fun logRewatch(
        movieId: Long,
        mediaType: MediaType,
        title: String,
        date: String = todayIsoDate(),
    ) {
        val key = "${mediaType.value}_$movieId"
        val current = watchedMap.value[key] ?: return
        viewModelScope.launch {
            watchedRepository.logRewatch(
                movieId = movieId,
                mediaType = mediaType,
                watchDate = date,
                currentLog = current.resolvedWatchDates,
            ).fold(
                onSuccess = { updatedLog ->
                    _snackbarMessages.tryEmit("Logged rewatch for \"$title\" (${updatedLog.size}× total).")
                },
                onFailure = { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to log rewatch.")
                }
            )
        }
    }

    fun removeWatchDateAt(
        movieId: Long,
        mediaType: MediaType,
        index: Int,
    ) {
        val key = "${mediaType.value}_$movieId"
        val current = watchedMap.value[key] ?: return
        val dates = current.resolvedWatchDates.toMutableList()
        if (dates.size <= 1 || index !in dates.indices) return
        dates.removeAt(index)
        viewModelScope.launch {
            watchedRepository.updateWatchLog(movieId, mediaType, dates).fold(
                onSuccess = {
                    _snackbarMessages.tryEmit("Updated watch log (${dates.size}×).")
                },
                onFailure = { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to update watch log.")
                }
            )
        }
    }

    fun toggleWatchlist(movie: MovieSummary) {
        val key = movie.uniqueKey
        if (_pendingWatchlistKeys.value.contains(key)) return
        val inWatchlist = watchlistMap.value.containsKey(key)
        viewModelScope.launch {
            _pendingWatchlistKeys.value += key
            try {
                if (inWatchlist) {
                    watchlistRepository.removeFromWatchlist(movie.movieId, movie.mediaType).fold(
                        onSuccess = {
                            _snackbarMessages.tryEmit("Removed \"${movie.title}\" from Watchlist.")
                        },
                        onFailure = { err ->
                            _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to update Watchlist.")
                        }
                    )
                } else {
                    val modalDetails = _movieModal.value?.takeIf { it.uniqueKey == key }?.details
                    val details = modalDetails ?: if (isTmdbKeyConfigured && isOnline.value) {
                        tmdbRepository.getMediaDetails(
                            mediaId = movie.movieId,
                            mediaType = movie.mediaType,
                            includeImagesAndProviders = false,
                        ).getOrNull()
                    } else null
                    watchlistRepository.addToWatchlist(movie, details).fold(
                        onSuccess = {
                            _snackbarMessages.tryEmit("Added \"${movie.title}\" to Watchlist.")
                        },
                        onFailure = { err ->
                            _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to add to Watchlist.")
                        }
                    )
                }
            } finally {
                _pendingWatchlistKeys.value -= key
            }
        }
    }

    fun moveWatchlistToWatched(item: WatchlistItem) {
        val key = item.uniqueKey
        if (_pendingToggleKeys.value.contains(key)) return
        viewModelScope.launch {
            _pendingToggleKeys.value += key
            try {
                val summary = item.toSummary()
                val details = if (isTmdbKeyConfigured && isOnline.value) {
                    tmdbRepository.getMediaDetails(
                        mediaId = item.movieId,
                        mediaType = item.resolvedMediaType,
                        includeImagesAndProviders = false,
                    ).getOrNull()
                } else null

                val res = if (item.resolvedMediaType == MediaType.TV && details != null && details.seasons.isNotEmpty()) {
                    markTvShowFullyWatchedInternal(
                        summary = summary,
                        details = details,
                        existingWatched = watchedMap.value[item.uniqueKey],
                    )
                } else {
                    watchedRepository.markAsWatched(summary, details)
                }
                res.fold(
                    onSuccess = {
                        watchlistRepository.removeFromWatchlist(item.movieId, item.resolvedMediaType)
                        _snackbarMessages.tryEmit("Moved \"${item.title}\" to Watched!")
                    },
                    onFailure = { err ->
                        _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to move item.")
                    }
                )
            } finally {
                _pendingToggleKeys.value -= key
            }
        }
    }

    fun removeFromWatchlist(item: WatchlistItem) {
        val key = item.uniqueKey
        if (_pendingWatchlistKeys.value.contains(key)) return
        viewModelScope.launch {
            _pendingWatchlistKeys.value += key
            try {
                watchlistRepository.removeFromWatchlist(item.movieId, item.resolvedMediaType).fold(
                    onSuccess = {
                        _snackbarMessages.tryEmit("Removed \"${item.title}\" from Watchlist.")
                    },
                    onFailure = { err ->
                        _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to remove item.")
                    }
                )
            } finally {
                _pendingWatchlistKeys.value -= key
            }
        }
    }

    // Custom Lists actions
    fun openAddToListSheet(movie: MovieSummary) {
        _addToListTarget.value = movie
    }

    fun dismissAddToListSheet() {
        _addToListTarget.value = null
    }

    fun openCustomListDetails(listId: String) {
        _selectedCustomListId.value = listId
    }

    fun dismissCustomListDetails() {
        _selectedCustomListId.value = null
    }

    fun createCustomList(
        name: String,
        description: String = "",
        initialMovie: MovieSummary? = null,
    ) {
        viewModelScope.launch {
            customListRepository.createList(
                name = name,
                description = description,
                initialItem = initialMovie?.toCustomListEntry(),
            ).fold(
                onSuccess = {
                    val suffix = if (initialMovie != null) " & added \"${initialMovie.title}\"" else ""
                    _snackbarMessages.tryEmit("Created list \"${name.trim()}\"$suffix.")
                },
                onFailure = { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to create list.")
                }
            )
        }
    }

    fun renameCustomList(listId: String, newName: String, newDescription: String? = null) {
        viewModelScope.launch {
            customListRepository.renameList(listId, newName, newDescription).fold(
                onSuccess = {
                    _snackbarMessages.tryEmit("Updated list \"${newName.trim()}\".")
                },
                onFailure = { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to rename list.")
                }
            )
        }
    }

    fun toggleMovieInList(list: CustomList, movie: MovieSummary) {
        viewModelScope.launch {
            customListRepository.toggleMovieInList(list, movie.toCustomListEntry()).fold(
                onSuccess = { added ->
                    val verb = if (added) "Added to" else "Removed from"
                    _snackbarMessages.tryEmit("$verb \"${list.name}\".")
                },
                onFailure = { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to update list.")
                }
            )
        }
    }

    fun removeMovieFromList(list: CustomList, movieId: Long, mediaType: MediaType, title: String) {
        viewModelScope.launch {
            customListRepository.removeMovieFromList(list, movieId, mediaType).fold(
                onSuccess = {
                    _snackbarMessages.tryEmit("Removed \"$title\" from \"${list.name}\".")
                },
                onFailure = { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to remove item.")
                }
            )
        }
    }

    fun deleteCustomList(list: CustomList) {
        viewModelScope.launch {
            customListRepository.deleteList(list.id).fold(
                onSuccess = {
                    if (_selectedCustomListId.value == list.id) {
                        _selectedCustomListId.value = null
                    }
                    _snackbarMessages.tryEmit("Deleted list \"${list.name}\".")
                },
                onFailure = { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to delete list.")
                }
            )
        }
    }

    // Surprise Me ("What to Watch Today")
    fun openSurpriseMe() {
        val current = _surpriseMeState.value
        val picked = computeSurprisePick(
            languageFilter = current.languageFilter,
            genreFilter = current.genreFilter,
            maxRuntime = current.maxRuntime,
            excludeKey = null,
        )
        _surpriseMeState.value = picked.copy(isOpen = true)
    }

    fun dismissSurpriseMe() {
        _surpriseMeState.value = _surpriseMeState.value.copy(isOpen = false)
    }

    fun pickSurpriseMovie(
        languageFilter: String = _surpriseMeState.value.languageFilter,
        genreFilter: String = _surpriseMeState.value.genreFilter,
        maxRuntime: Int = _surpriseMeState.value.maxRuntime,
    ) {
        val excludeKey = _surpriseMeState.value.pickedItem?.uniqueKey
        val updated = computeSurprisePick(
            languageFilter = languageFilter,
            genreFilter = genreFilter,
            maxRuntime = maxRuntime,
            excludeKey = excludeKey,
        )
        _surpriseMeState.value = updated.copy(isOpen = true)
    }

    private fun computeSurprisePick(
        languageFilter: String,
        genreFilter: String,
        maxRuntime: Int,
        excludeKey: String?,
    ): SurpriseMeState {
        val allWatchlist = watchlistMap.value.values.toList()
        val matching = allWatchlist.filter { item ->
            val langOk = languageFilter == "all" || item.originalLanguage.equals(languageFilter, ignoreCase = true)
            val genreOk = genreFilter == "all" || item.genres.any { it.equals(genreFilter, ignoreCase = true) }
            val runtimeOk = maxRuntime <= 0 || item.runtime == 0 || item.runtime <= maxRuntime
            langOk && genreOk && runtimeOk
        }
        val pool = if (matching.size > 1 && excludeKey != null) {
            matching.filterNot { it.uniqueKey == excludeKey }
        } else {
            matching
        }
        val chosen = pool.randomOrNull()
        return SurpriseMeState(
            isOpen = true,
            languageFilter = languageFilter,
            genreFilter = genreFilter,
            maxRuntime = maxRuntime,
            matchingCount = matching.size,
            pickedItem = chosen,
        )
    }

    // Import / Export
    fun openImportExportDialog() {
        _showImportExportDialog.value = true
    }

    fun dismissImportExportDialog() {
        _showImportExportDialog.value = false
    }

    fun clearImportPreview() {
        _importUiState.value = ImportUiState.Idle
    }

    fun buildExportCsv(): String {
        val watched = (watchedState.value as? WatchedUiState.Success)?.movies.orEmpty()
        val watchlist = (watchlistState.value as? WatchlistUiState.Success)?.items.orEmpty()
        val lists = customLists.value
        return ImportExportHelper.exportToCsv(watched, watchlist, lists)
    }

    fun buildExportJson(): String {
        val watched = (watchedState.value as? WatchedUiState.Success)?.movies.orEmpty()
        val watchlist = (watchlistState.value as? WatchlistUiState.Success)?.items.orEmpty()
        val lists = customLists.value
        return ImportExportHelper.exportToJson(watched, watchlist, lists)
    }

    fun parseAndMatchImportCsv(csvContent: String) {
        if (!isTmdbKeyConfigured) {
            _snackbarMessages.tryEmit("Add TMDB_API_KEY in AI Studio Secrets first to match imported movies.")
            return
        }
        if (!isOnline.value) {
            _snackbarMessages.tryEmit("Connect to the internet to match CSV titles on TMDB.")
            return
        }
        val parsed = ImportExportHelper.parseCsv(csvContent)
        if (parsed.rows.isEmpty()) {
            _snackbarMessages.tryEmit("No valid movie/show rows found in CSV.")
            return
        }
        val rowsToMatch = parsed.rows.take(150)
        viewModelScope.launch {
            _importUiState.value = ImportUiState.Matching(
                format = parsed.format,
                completed = 0,
                total = rowsToMatch.size,
            )
            val candidates = mutableListOf<ImportCandidateRow>()
            var done = 0
            for (chunk in rowsToMatch.chunked(3)) {
                coroutineScope {
                    val matchedChunk = chunk.map { row ->
                        async {
                            val match = tmdbRepository.searchBestImportMatch(
                                title = row.sourceTitle,
                                year = row.year,
                                preferTv = row.isTv,
                            ).getOrNull()
                            ImportCandidateRow(
                                rowIndex = row.rowIndex,
                                sourceTitle = row.sourceTitle,
                                year = row.year,
                                isTv = row.isTv,
                                userRating = row.userRating,
                                watchDate = row.watchDate,
                                note = row.note,
                                matchedMovie = match,
                            )
                        }
                    }.awaitAll()
                    candidates.addAll(matchedChunk)
                    done += chunk.size
                    _importUiState.value = ImportUiState.Matching(
                        format = parsed.format,
                        completed = done,
                        total = rowsToMatch.size,
                    )
                }
            }
            _importUiState.value = ImportUiState.Preview(
                format = parsed.format,
                rows = candidates,
                isSaving = false,
            )
        }
    }

    suspend fun searchTmdbForImportFix(query: String): Result<List<MovieSummary>> =
        tmdbRepository.searchAllMedia(query)

    fun fixImportRowMatch(rowIndex: Int, matchedMovie: MovieSummary) {
        val current = _importUiState.value as? ImportUiState.Preview ?: return
        val updatedRows = current.rows.map { row ->
            if (row.rowIndex == rowIndex) {
                row.copy(matchedMovie = matchedMovie, isSkipped = false)
            } else row
        }
        _importUiState.value = current.copy(rows = updatedRows)
    }

    fun toggleSkipImportRow(rowIndex: Int) {
        val current = _importUiState.value as? ImportUiState.Preview ?: return
        val updatedRows = current.rows.map { row ->
            if (row.rowIndex == rowIndex) row.copy(isSkipped = !row.isSkipped) else row
        }
        _importUiState.value = current.copy(rows = updatedRows)
    }

    fun commitMatchedImportToFirestore() {
        val current = _importUiState.value as? ImportUiState.Preview ?: return
        if (current.isSaving) return
        val matchedRows = current.rows.filter { it.isMatched && it.matchedMovie != null }
        if (matchedRows.isEmpty()) {
            _snackbarMessages.tryEmit("No matched rows selected to import.")
            return
        }
        viewModelScope.launch {
            _importUiState.value = current.copy(isSaving = true)
            val batchItems = mutableListOf<BatchWatchedImportItem>()
            for (chunk in matchedRows.chunked(3)) {
                coroutineScope {
                    val enriched = chunk.map { row ->
                        async {
                            val summary = row.matchedMovie!!
                            val details = tmdbRepository.getMediaDetails(
                                mediaId = summary.movieId,
                                mediaType = summary.mediaType,
                                includeImagesAndProviders = false,
                            ).getOrNull()
                            BatchWatchedImportItem(
                                summary = summary,
                                details = details,
                                userRating = row.userRating,
                                note = row.note,
                                watchLog = listOfNotNull(row.watchDate).ifEmpty { listOf(todayIsoDate()) },
                            )
                        }
                    }.awaitAll()
                    batchItems.addAll(enriched)
                }
            }

            watchedRepository.importBatch(batchItems).fold(
                onSuccess = { count ->
                    _importUiState.value = ImportUiState.Idle
                    _showImportExportDialog.value = false
                    _snackbarMessages.tryEmit("Imported $count ${if (count == 1) "title" else "titles"} into CineLog!")
                },
                onFailure = { err ->
                    _importUiState.value = current.copy(isSaving = false)
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to save imported titles.")
                }
            )
        }
    }

    fun quickReact(movieId: Long, mediaType: MediaType, reactionKey: String) {
        val current = watchedMap.value["${mediaType.value}_$movieId"] ?: return
        val nextReaction = if (current.reaction == reactionKey) "" else reactionKey
        viewModelScope.launch {
            watchedRepository.updateReaction(movieId, mediaType, nextReaction).fold(
                onSuccess = {
                    val chosen = QuickReaction.fromKey(nextReaction)
                    val msg = if (chosen != null) "${chosen.emoji} ${chosen.label} saved" else "Reaction cleared"
                    _snackbarMessages.tryEmit(msg)
                },
                onFailure = { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to update reaction.")
                }
            )
        }
    }

    fun removeFromWatched(movieId: Long, mediaType: MediaType, title: String) {
        val key = "${mediaType.value}_$movieId"
        if (_pendingToggleKeys.value.contains(key)) return
        viewModelScope.launch {
            _pendingToggleKeys.value += key
            try {
                watchedRepository.removeFromWatched(movieId, mediaType).fold(
                    onSuccess = {
                        _snackbarMessages.tryEmit("Removed \"$title\" from watched.")
                    },
                    onFailure = { err ->
                        _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to remove item.")
                    }
                )
            } finally {
                _pendingToggleKeys.value -= key
            }
        }
    }

    fun openMovieDetails(movie: MovieSummary) {
        _seasonEpisodesStates.value = emptyMap()
        _earlierSeasonsPrompt.value = null
        _movieModal.value = MovieModalState(
            uniqueKey = movie.uniqueKey,
            movieId = movie.movieId,
            mediaType = movie.mediaType,
            summary = movie,
            details = null,
            isLoadingDetails = isTmdbKeyConfigured && isOnline.value,
        )
        if (isTmdbKeyConfigured && isOnline.value) {
            fetchMediaDetails(movie.movieId, movie.mediaType)
        }
    }

    fun openWatchedDetails(watched: WatchedMovie) {
        _seasonEpisodesStates.value = emptyMap()
        _earlierSeasonsPrompt.value = null
        val mediaType = watched.resolvedMediaType
        val summary = watched.toSummary()
        val cachedDetails = if (
            watched.overview.isNotBlank() ||
            watched.genres.isNotEmpty() ||
            watched.runtime > 0 ||
            watched.numberOfSeasons > 0
        ) {
            MovieDetails(
                movieId = watched.movieId,
                mediaType = mediaType,
                title = watched.title,
                posterPath = watched.posterPath,
                backdropPath = null,
                releaseDate = watched.releaseDate,
                rating = watched.rating,
                overview = watched.overview,
                runtime = watched.runtime,
                genres = watched.genres,
                cast = emptyList(),
                directors = watched.directors,
                originalLanguage = watched.originalLanguage,
                numberOfSeasons = watched.numberOfSeasons,
                numberOfEpisodes = watched.numberOfEpisodes,
                watchProvidersIN = _watchProvidersCache.value[watched.uniqueKey],
            )
        } else null

        _movieModal.value = MovieModalState(
            uniqueKey = watched.uniqueKey,
            movieId = watched.movieId,
            mediaType = mediaType,
            summary = summary,
            details = cachedDetails,
            isLoadingDetails = isTmdbKeyConfigured && isOnline.value,
        )
        if (isTmdbKeyConfigured && isOnline.value) {
            fetchMediaDetails(watched.movieId, mediaType)
        }
    }

    fun openWatchlistDetails(item: WatchlistItem) {
        openMovieDetails(item.toSummary())
    }

    private fun fetchMediaDetails(movieId: Long, mediaType: MediaType) {
        val targetKey = "${mediaType.value}_$movieId"
        val existingForSeason = watchedMap.value[targetKey]
        val preferredSeason = if (mediaType == MediaType.TV && existingForSeason != null) {
            existingForSeason.currentSeason.takeIf { it > 0 }
                ?: existingForSeason.nextUpSeason.takeIf { it > 0 }
                ?: existingForSeason.watchedSeason.takeIf { it > 0 }
        } else null
        viewModelScope.launch {
            tmdbRepository.getMediaDetails(
                mediaId = movieId,
                mediaType = mediaType,
                includeImagesAndProviders = true,
                preferredSeasonNumber = preferredSeason,
            ).fold(
                onSuccess = { details ->
                    details.watchProvidersIN?.let { providers ->
                        _watchProvidersCache.value += (targetKey to providers)
                    }
                    if (_movieModal.value?.uniqueKey == targetKey) {
                        _movieModal.value = _movieModal.value?.copy(
                            summary = details.toSummary(),
                            details = details,
                            isLoadingDetails = false,
                            detailsError = null,
                        )
                    }
                    val existingWatched = watchedMap.value[targetKey]
                    if (existingWatched != null && existingWatched.needsMetadataBackfill) {
                        watchedRepository.enrichWatchedMetadata(movieId, mediaType, details)
                    }
                },
                onFailure = { error ->
                    if (_movieModal.value?.uniqueKey == targetKey) {
                        _movieModal.value = _movieModal.value?.copy(
                            isLoadingDetails = false,
                            detailsError = error.localizedMessage,
                        )
                    }
                }
            )
        }
    }

    private fun prefetchProvidersForCards(items: List<MovieSummary>) {
        if (!isTmdbKeyConfigured || !isOnline.value) return
        val toFetch = items.filter { !attemptedProviderKeys.contains(it.uniqueKey) }.take(6)
        if (toFetch.isEmpty()) return
        toFetch.forEach { attemptedProviderKeys.add(it.uniqueKey) }
        viewModelScope.launch {
            val loaded = coroutineScope {
                toFetch.map { item ->
                    async {
                        val res = tmdbRepository.getIndiaWatchProviders(item.movieId, item.mediaType).getOrNull()
                        if (res != null && !res.isEmpty) item.uniqueKey to res else null
                    }
                }.awaitAll().filterNotNull()
            }
            if (loaded.isNotEmpty()) {
                _watchProvidersCache.value = _watchProvidersCache.value + loaded.toMap()
            }
        }
    }

    fun dismissMovieDetails() {
        _movieModal.value = null
        _seasonEpisodesStates.value = emptyMap()
        _earlierSeasonsPrompt.value = null
    }

    // TV Season & Episode Tracking Actions
    fun loadSeasonEpisodes(tvId: Long, seasonNumber: Int, forceRetry: Boolean = false) {
        if (!forceRetry) {
            val current = _seasonEpisodesStates.value[seasonNumber]
            if (current is SeasonEpisodesLoadState.Success || current is SeasonEpisodesLoadState.Loading) return
        }
        val cached = tmdbRepository.getCachedSeasonEpisodes(tvId, seasonNumber)
        if (cached != null) {
            _seasonEpisodesStates.value += (seasonNumber to SeasonEpisodesLoadState.Success(cached))
            return
        }
        if (!isTmdbKeyConfigured) {
            _seasonEpisodesStates.value += (seasonNumber to SeasonEpisodesLoadState.Error("Add TMDB_API_KEY in AI Studio Secrets."))
            return
        }
        viewModelScope.launch {
            _seasonEpisodesStates.value += (seasonNumber to SeasonEpisodesLoadState.Loading)
            tmdbRepository.getSeasonEpisodes(tvId, seasonNumber).fold(
                onSuccess = { detail ->
                    if (_movieModal.value?.movieId == tvId && _movieModal.value?.mediaType == MediaType.TV) {
                        _seasonEpisodesStates.value += (seasonNumber to SeasonEpisodesLoadState.Success(detail))
                    }
                },
                onFailure = { err ->
                    if (_movieModal.value?.movieId == tvId && _movieModal.value?.mediaType == MediaType.TV) {
                        _seasonEpisodesStates.value += (
                            seasonNumber to SeasonEpisodesLoadState.Error(
                                err.localizedMessage ?: "Failed to load episodes."
                            )
                        )
                    }
                }
            )
        }
    }

    fun toggleEpisodeWatched(tvId: Long, season: TvSeasonSummary, episodeNumber: Int) {
        val modal = _movieModal.value?.takeIf { it.mediaType == MediaType.TV && it.movieId == tvId } ?: return
        val details = modal.details ?: return
        if (_isSavingTvProgress.value) return

        val seasonDocsMap = modalTvSeasons.value.toMutableMap()
        val existingDoc = seasonDocsMap[season.seasonNumber]
        val cachedDetail = tmdbRepository.getCachedSeasonEpisodes(tvId, season.seasonNumber)
        val totalEpCount = maxOf(
            season.episodeCount,
            cachedDetail?.episodes?.size ?: 0,
            existingDoc?.episodeCount ?: 0,
        )
        val allEpNumbers = if (cachedDetail != null && cachedDetail.episodes.isNotEmpty()) {
            cachedDetail.episodes.map { it.episodeNumber }.distinct().sorted()
        } else {
            (1..totalEpCount).toList()
        }

        val currentWatched = when {
            existingDoc == null -> mutableSetOf()
            existingDoc.isConsideredWatched(season.episodeCount) && existingDoc.watchedEpisodes.isEmpty() ->
                allEpNumbers.toMutableSet()
            else -> existingDoc.watchedEpisodes.filter { it > 0 }.toMutableSet()
        }

        if (!currentWatched.remove(episodeNumber)) {
            currentWatched.add(episodeNumber)
        }

        val sortedWatched = currentWatched.sorted()
        val seasonWatched = (allEpNumbers.isNotEmpty() && allEpNumbers.all { currentWatched.contains(it) }) ||
            (totalEpCount > 0 && sortedWatched.size >= totalEpCount)
        val seasonRuntime = TvProgressCalculator.computeSeasonRuntimeMinutes(
            watchedEpisodes = sortedWatched,
            seasonDetail = cachedDetail,
            fallbackAvgEpisodeRuntime = details.runtime,
        )

        val updatedDoc = WatchedSeason(
            id = season.seasonNumber.toString(),
            userId = currentUserId,
            tvId = tvId,
            seasonNumber = season.seasonNumber,
            episodeCount = totalEpCount,
            watched = seasonWatched,
            watchedEpisodes = sortedWatched,
            watchedRuntimeMinutes = seasonRuntime,
        )
        seasonDocsMap[season.seasonNumber] = updatedDoc

        val previousOverrides = _optimisticTvOverrides.value[tvId]
        val nextOverrides = (previousOverrides ?: emptyMap()).toMutableMap().apply {
            put(season.seasonNumber, updatedDoc)
        }
        _optimisticTvOverrides.value = _optimisticTvOverrides.value + (tvId to nextOverrides)

        Log.d(
            TAG,
            "toggleEpisodeWatched tvId=$tvId seasonNumber=${season.seasonNumber} ep=$episodeNumber seasonWatched=$seasonWatched count=${sortedWatched.size}/$totalEpCount"
        )

        val cachedDetailsBySeason = details.seasons.mapNotNull { s ->
            tmdbRepository.getCachedSeasonEpisodes(tvId, s.seasonNumber)?.let { s.seasonNumber to it }
        }.toMap()

        val progress = TvProgressCalculator.compute(
            seasons = details.seasons,
            seasonDocs = seasonDocsMap,
            fallbackTotalSeasons = details.numberOfSeasons,
            fallbackTotalEpisodes = details.numberOfEpisodes,
            fallbackAvgEpisodeRuntime = details.runtime,
            episodeDetailsBySeason = cachedDetailsBySeason,
        )

        val seasonWrite = SeasonBatchWrite(
            seasonNumber = season.seasonNumber,
            episodeCount = totalEpCount,
            watched = seasonWatched,
            watchedEpisodes = sortedWatched,
            watchedRuntimeMinutes = seasonRuntime,
        )

        commitTvProgressBatch(
            tvId = tvId,
            seasonNumbersForLog = listOf(season.seasonNumber),
            summary = modal.summary,
            details = details,
            existingWatched = watchedMap.value[modal.uniqueKey],
            seasonWrites = listOf(seasonWrite),
            deleteSeasonNumbers = emptyList(),
            progress = progress,
            existingSeasonIds = modalTvSeasons.value.keys,
            previousOverrides = previousOverrides,
            successToast = null,
            retryAction = { toggleEpisodeWatched(tvId, season, episodeNumber) },
        )
    }

    fun onToggleSeasonWatched(tvId: Long, season: TvSeasonSummary) {
        if (season.isUpcoming || _isSavingTvProgress.value) return
        val modal = _movieModal.value?.takeIf { it.mediaType == MediaType.TV && it.movieId == tvId } ?: return
        val details = modal.details ?: return
        val seasonDocsMap = modalTvSeasons.value
        val existingDoc = seasonDocsMap[season.seasonNumber]
        val isCurrentlyWatched = existingDoc?.isConsideredWatched(season.episodeCount) == true

        Log.d(
            TAG,
            "onToggleSeasonWatched tvId=$tvId seasonNumber=${season.seasonNumber} currentlyWatched=$isCurrentlyWatched"
        )

        if (!isCurrentlyWatched && !season.isSpecial && season.seasonNumber > 1) {
            val earlierUnwatched = details.seasons
                .filter { !it.isSpecial && !it.isUpcoming && it.episodeCount > 0 && it.seasonNumber < season.seasonNumber }
                .filter { earlier ->
                    val doc = seasonDocsMap[earlier.seasonNumber]
                    doc == null || !doc.isConsideredWatched(earlier.episodeCount)
                }
                .map { it.seasonNumber }
            if (earlierUnwatched.isNotEmpty()) {
                _earlierSeasonsPrompt.value = EarlierSeasonsPrompt(
                    tvId = tvId,
                    targetSeason = season,
                    earlierSeasonNumbers = earlierUnwatched,
                )
                return
            }
        }

        applySeasonsWatchedState(
            tvId = tvId,
            targetSeasons = listOf(season),
            markWatched = !isCurrentlyWatched,
        )
    }

    fun dismissEarlierSeasonsPrompt() {
        _earlierSeasonsPrompt.value = null
    }

    fun confirmMarkEarlierSeasons(markAllUpToTarget: Boolean) {
        val prompt = _earlierSeasonsPrompt.value ?: return
        _earlierSeasonsPrompt.value = null
        val modal = _movieModal.value?.takeIf { it.mediaType == MediaType.TV && it.movieId == prompt.tvId } ?: return
        val details = modal.details ?: return
        val targets = if (markAllUpToTarget) {
            details.seasons.filter {
                !it.isSpecial && !it.isUpcoming && it.episodeCount > 0 &&
                    it.seasonNumber <= prompt.targetSeason.seasonNumber
            }
        } else {
            listOf(prompt.targetSeason)
        }
        applySeasonsWatchedState(
            tvId = prompt.tvId,
            targetSeasons = targets,
            markWatched = true,
        )
    }

    fun markAllSeasonsWatched(tvId: Long) {
        if (_isSavingTvProgress.value) return
        val modal = _movieModal.value?.takeIf { it.mediaType == MediaType.TV && it.movieId == tvId } ?: return
        val details = modal.details ?: return
        val airedRegular = details.seasons.filter { !it.isSpecial && !it.isUpcoming && it.episodeCount > 0 }
            .ifEmpty { details.seasons.filter { !it.isSpecial && it.episodeCount > 0 } }
        if (airedRegular.isEmpty()) return
        applySeasonsWatchedState(
            tvId = tvId,
            targetSeasons = airedRegular,
            markWatched = true,
            customToast = "Marked all seasons of \"${modal.summary.title}\" watched!",
        )
    }

    fun resetTvProgress(tvId: Long) {
        if (_isSavingTvProgress.value) return
        val modal = _movieModal.value?.takeIf { it.mediaType == MediaType.TV && it.movieId == tvId } ?: return
        val details = modal.details ?: return
        val existingSeasonNumbers = modalTvSeasons.value.keys.toList()
        val previousOverrides = _optimisticTvOverrides.value[tvId]
        val deletedOverrides = (previousOverrides ?: emptyMap()).toMutableMap().apply {
            existingSeasonNumbers.forEach { put(it, null) }
        }
        _optimisticTvOverrides.value = _optimisticTvOverrides.value + (tvId to deletedOverrides)

        Log.d(TAG, "resetTvProgress tvId=$tvId seasonNumber=$existingSeasonNumbers")

        val progress = TvProgressCalculator.compute(
            seasons = details.seasons,
            seasonDocs = emptyMap(),
            fallbackTotalSeasons = details.numberOfSeasons,
            fallbackTotalEpisodes = details.numberOfEpisodes,
            fallbackAvgEpisodeRuntime = details.runtime,
        )
        commitTvProgressBatch(
            tvId = tvId,
            seasonNumbersForLog = existingSeasonNumbers,
            summary = modal.summary,
            details = details,
            existingWatched = watchedMap.value[modal.uniqueKey],
            seasonWrites = emptyList(),
            deleteSeasonNumbers = existingSeasonNumbers,
            progress = progress,
            existingSeasonIds = existingSeasonNumbers.toSet(),
            previousOverrides = previousOverrides,
            successToast = "Reset watch progress for \"${modal.summary.title}\".",
            retryAction = { resetTvProgress(tvId) },
        )
    }

    private fun applySeasonsWatchedState(
        tvId: Long,
        targetSeasons: List<TvSeasonSummary>,
        markWatched: Boolean,
        customToast: String? = null,
    ) {
        val modal = _movieModal.value?.takeIf { it.mediaType == MediaType.TV && it.movieId == tvId } ?: return
        val details = modal.details ?: return
        if (targetSeasons.isEmpty()) return
        if (_isSavingTvProgress.value) return

        val previousOverrides = _optimisticTvOverrides.value[tvId]
        val initialSeasonDocs = modalTvSeasons.value.toMutableMap()
        val initialOverrides = (previousOverrides ?: emptyMap()).toMutableMap()

        // Apply immediate optimistic state so UI stays ticked without flicker
        targetSeasons.forEach { season ->
            val cached = tmdbRepository.getCachedSeasonEpisodes(tvId, season.seasonNumber)
            val totalEpCount = maxOf(season.episodeCount, cached?.episodes?.size ?: 0)
            val epNums = if (markWatched) {
                if (cached != null && cached.episodes.isNotEmpty()) {
                    cached.episodes.map { it.episodeNumber }.distinct().sorted()
                } else {
                    (1..totalEpCount).toList()
                }
            } else {
                emptyList()
            }
            val runtimeMins = if (markWatched) {
                TvProgressCalculator.computeSeasonRuntimeMinutes(
                    watchedEpisodes = epNums,
                    seasonDetail = cached,
                    fallbackAvgEpisodeRuntime = details.runtime,
                )
            } else 0
            val optDoc = WatchedSeason(
                id = season.seasonNumber.toString(),
                userId = currentUserId,
                tvId = tvId,
                seasonNumber = season.seasonNumber,
                episodeCount = totalEpCount,
                watched = markWatched,
                watchedEpisodes = epNums,
                watchedRuntimeMinutes = runtimeMins,
            )
            initialSeasonDocs[season.seasonNumber] = optDoc
            initialOverrides[season.seasonNumber] = optDoc
        }
        _optimisticTvOverrides.value = _optimisticTvOverrides.value + (tvId to initialOverrides)

        viewModelScope.launch {
            _isSavingTvProgress.value = true
            val seasonNumbersLog = targetSeasons.map { it.seasonNumber }
            Log.d(
                TAG,
                "applySeasonsWatchedState start tvId=$tvId seasonNumber=$seasonNumbersLog markWatched=$markWatched"
            )
            try {
                // Always fetch /tv/{id}/season/{n} if needed when marking watched so watchedEpisodes has all episodes
                val loadedDetails = mutableMapOf<Int, TvSeasonDetail>()
                details.seasons.forEach { s ->
                    tmdbRepository.getCachedSeasonEpisodes(tvId, s.seasonNumber)?.let {
                        loadedDetails[s.seasonNumber] = it
                    }
                }
                if (markWatched && isTmdbKeyConfigured) {
                    val missingTargets = targetSeasons.filter { !loadedDetails.containsKey(it.seasonNumber) }
                    missingTargets.chunked(3).forEach { chunk ->
                        coroutineScope {
                            chunk.map { s ->
                                async {
                                    tmdbRepository.getSeasonEpisodes(tvId, s.seasonNumber).getOrNull()?.let { detail ->
                                        s.seasonNumber to detail
                                    }
                                }
                            }.awaitAll().filterNotNull().forEach { (sNum, detail) ->
                                loadedDetails[sNum] = detail
                                if (_movieModal.value?.movieId == tvId && _movieModal.value?.mediaType == MediaType.TV) {
                                    _seasonEpisodesStates.value += (sNum to SeasonEpisodesLoadState.Success(detail))
                                }
                            }
                        }
                    }
                }

                val seasonDocsMap = initialSeasonDocs.toMutableMap()
                val finalizedOverrides = (_optimisticTvOverrides.value[tvId] ?: initialOverrides).toMutableMap()
                val writes = mutableListOf<SeasonBatchWrite>()

                targetSeasons.forEach { season ->
                    val cached = loadedDetails[season.seasonNumber]
                    val epNums = if (markWatched) {
                        if (cached != null && cached.episodes.isNotEmpty()) {
                            cached.episodes.map { it.episodeNumber }.distinct().sorted()
                        } else {
                            (1..season.episodeCount.coerceAtLeast(0)).toList()
                        }
                    } else {
                        emptyList()
                    }
                    val totalEpCount = maxOf(season.episodeCount, epNums.size, cached?.episodes?.size ?: 0)
                    val runtimeMins = if (markWatched) {
                        TvProgressCalculator.computeSeasonRuntimeMinutes(
                            watchedEpisodes = epNums,
                            seasonDetail = cached,
                            fallbackAvgEpisodeRuntime = details.runtime,
                        )
                    } else 0

                    val seasonModel = WatchedSeason(
                        id = season.seasonNumber.toString(),
                        userId = currentUserId,
                        tvId = tvId,
                        seasonNumber = season.seasonNumber,
                        episodeCount = totalEpCount,
                        watched = markWatched,
                        watchedEpisodes = epNums,
                        watchedRuntimeMinutes = runtimeMins,
                    )
                    seasonDocsMap[season.seasonNumber] = seasonModel
                    finalizedOverrides[season.seasonNumber] = seasonModel
                    writes.add(
                        SeasonBatchWrite(
                            seasonNumber = season.seasonNumber,
                            episodeCount = totalEpCount,
                            watched = markWatched,
                            watchedEpisodes = epNums,
                            watchedRuntimeMinutes = runtimeMins,
                        )
                    )
                }
                _optimisticTvOverrides.value = _optimisticTvOverrides.value + (tvId to finalizedOverrides)

                val progress = TvProgressCalculator.compute(
                    seasons = details.seasons,
                    seasonDocs = seasonDocsMap,
                    fallbackTotalSeasons = details.numberOfSeasons,
                    fallbackTotalEpisodes = details.numberOfEpisodes,
                    fallbackAvgEpisodeRuntime = details.runtime,
                    episodeDetailsBySeason = loadedDetails,
                )

                val toast = customToast ?: if (targetSeasons.size == 1) {
                    val label = targetSeasons.first().displayTitle
                    if (markWatched) "Marked $label watched." else "Marked $label unwatched."
                } else {
                    "Marked ${targetSeasons.size} seasons watched."
                }

                tvProgressRepository.saveTvProgressBatch(
                    summary = modal.summary,
                    details = details,
                    existingWatched = watchedMap.value[modal.uniqueKey],
                    seasonWrites = writes,
                    deleteSeasonNumbers = emptyList(),
                    progress = progress,
                ).fold(
                    onSuccess = {
                        Log.d(
                            TAG,
                            "applySeasonsWatchedState success tvId=$tvId seasonNumber=$seasonNumbersLog markWatched=$markWatched status=${progress.status}"
                        )
                        if (progress.status != TvWatchStatus.NOT_STARTED && watchlistMap.value.containsKey(modal.uniqueKey)) {
                            watchlistRepository.removeFromWatchlist(modal.movieId, MediaType.TV)
                        }
                        emitSnackbar(toast)
                    },
                    onFailure = { err ->
                        Log.e(
                            TAG,
                            "applySeasonsWatchedState failure tvId=$tvId seasonNumber=$seasonNumbersLog exception=${err.message}",
                            err
                        )
                        rollbackTvOverrides(tvId, previousOverrides)
                        emitSnackbar(
                            message = TvProgressRepository.formatSaveError(err),
                            actionLabel = "Retry",
                            onAction = { applySeasonsWatchedState(tvId, targetSeasons, markWatched, customToast) },
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "applySeasonsWatchedState exception tvId=$tvId seasonNumber=$seasonNumbersLog exception=${e.message}",
                    e
                )
                rollbackTvOverrides(tvId, previousOverrides)
                emitSnackbar(
                    message = TvProgressRepository.formatSaveError(e),
                    actionLabel = "Retry",
                    onAction = { applySeasonsWatchedState(tvId, targetSeasons, markWatched, customToast) },
                )
            } finally {
                _isSavingTvProgress.value = false
            }
        }
    }

    private fun rollbackTvOverrides(
        tvId: Long,
        previousOverrides: Map<Int, WatchedSeason?>?,
    ) {
        val current = _optimisticTvOverrides.value.toMutableMap()
        if (previousOverrides == null) {
            current.remove(tvId)
        } else {
            current[tvId] = previousOverrides
        }
        _optimisticTvOverrides.value = current
    }

    private fun commitTvProgressBatch(
        tvId: Long,
        seasonNumbersForLog: List<Int>,
        summary: MovieSummary,
        details: MovieDetails,
        existingWatched: WatchedMovie?,
        seasonWrites: List<SeasonBatchWrite>,
        deleteSeasonNumbers: List<Int>,
        progress: TvComputedProgress,
        existingSeasonIds: Set<Int>,
        previousOverrides: Map<Int, WatchedSeason?>?,
        successToast: String?,
        retryAction: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            _isSavingTvProgress.value = true
            try {
                tvProgressRepository.saveTvProgressBatch(
                    summary = summary,
                    details = details,
                    existingWatched = existingWatched,
                    seasonWrites = seasonWrites,
                    deleteSeasonNumbers = deleteSeasonNumbers,
                    progress = progress,
                    existingSeasonIds = existingSeasonIds,
                ).fold(
                    onSuccess = {
                        Log.d(
                            TAG,
                            "commitTvProgressBatch success tvId=$tvId seasonNumber=$seasonNumbersForLog status=${progress.status}"
                        )
                        if (progress.status != TvWatchStatus.NOT_STARTED && watchlistMap.value.containsKey(summary.uniqueKey)) {
                            watchlistRepository.removeFromWatchlist(summary.movieId, MediaType.TV)
                        }
                        successToast?.let { emitSnackbar(it) }
                    },
                    onFailure = { err ->
                        Log.e(
                            TAG,
                            "commitTvProgressBatch failure tvId=$tvId seasonNumber=$seasonNumbersForLog exception=${err.message}",
                            err
                        )
                        rollbackTvOverrides(tvId, previousOverrides)
                        emitSnackbar(
                            message = TvProgressRepository.formatSaveError(err),
                            actionLabel = if (retryAction != null) "Retry" else null,
                            onAction = retryAction,
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "commitTvProgressBatch exception tvId=$tvId seasonNumber=$seasonNumbersForLog exception=${e.message}",
                    e
                )
                rollbackTvOverrides(tvId, previousOverrides)
                emitSnackbar(
                    message = TvProgressRepository.formatSaveError(e),
                    actionLabel = if (retryAction != null) "Retry" else null,
                    onAction = retryAction,
                )
            } finally {
                _isSavingTvProgress.value = false
            }
        }
    }

    private suspend fun markTvShowFullyWatchedInternal(
        summary: MovieSummary,
        details: MovieDetails,
        existingWatched: WatchedMovie?,
    ): Result<Unit> {
        val airedRegular = details.seasons
            .filter { !it.isSpecial && !it.isUpcoming && it.episodeCount > 0 }
            .ifEmpty { details.seasons.filter { !it.isSpecial && it.episodeCount > 0 } }
        if (airedRegular.isEmpty()) {
            return watchedRepository.markAsWatched(summary, details)
        }
        val loadedDetails = mutableMapOf<Int, TvSeasonDetail>()
        airedRegular.chunked(3).forEach { chunk ->
            coroutineScope {
                chunk.map { s ->
                    async {
                        tmdbRepository.getSeasonEpisodes(summary.movieId, s.seasonNumber).getOrNull()?.let { detail ->
                            s.seasonNumber to detail
                        }
                    }
                }.awaitAll().filterNotNull().forEach { (sNum, detail) ->
                    loadedDetails[sNum] = detail
                }
            }
        }
        val seasonDocsMap = mutableMapOf<Int, WatchedSeason>()
        val writes = airedRegular.map { season ->
            val cached = loadedDetails[season.seasonNumber]
            val epCount = maxOf(season.episodeCount, cached?.episodes?.size ?: 0)
            val epNums = if (cached != null && cached.episodes.isNotEmpty()) {
                cached.episodes.filter { !it.isUpcoming }.map { it.episodeNumber }.ifEmpty { (1..epCount).toList() }
            } else {
                (1..epCount).toList()
            }
            val runtimeMins = TvProgressCalculator.computeSeasonRuntimeMinutes(
                watchedEpisodes = epNums,
                seasonDetail = cached,
                fallbackAvgEpisodeRuntime = details.runtime,
            )
            seasonDocsMap[season.seasonNumber] = WatchedSeason(
                id = season.seasonNumber.toString(),
                userId = currentUserId,
                tvId = summary.movieId,
                seasonNumber = season.seasonNumber,
                episodeCount = epCount,
                watched = true,
                watchedEpisodes = epNums,
                watchedRuntimeMinutes = runtimeMins,
            )
            SeasonBatchWrite(
                seasonNumber = season.seasonNumber,
                episodeCount = epCount,
                watched = true,
                watchedEpisodes = epNums,
                watchedRuntimeMinutes = runtimeMins,
            )
        }
        val progress = TvProgressCalculator.compute(
            seasons = details.seasons,
            seasonDocs = seasonDocsMap,
            fallbackTotalSeasons = details.numberOfSeasons,
            fallbackTotalEpisodes = details.numberOfEpisodes,
            fallbackAvgEpisodeRuntime = details.runtime,
            episodeDetailsBySeason = loadedDetails,
        )
        return tvProgressRepository.saveTvProgressBatch(
            summary = summary,
            details = details,
            existingWatched = existingWatched,
            seasonWrites = writes,
            deleteSeasonNumbers = emptyList(),
            progress = progress,
        )
    }

    private suspend fun runOneTimeLegacyTvMigration(legacyShows: List<WatchedMovie>) {
        var allSucceeded = true
        for (item in legacyShows) {
            val details = tmdbRepository.getTvDetails(item.movieId).getOrNull()
            if (details == null) {
                allSucceeded = false
                continue
            }
            val res = markTvShowFullyWatchedInternal(
                summary = item.toSummary(),
                details = details,
                existingWatched = item,
            )
            if (res.isFailure) {
                allSucceeded = false
            }
        }
        if (allSucceeded) {
            settingsRepository.markTvMigrationCompleted()
            _tvMigrationCompleted.value = true
        }
    }

    fun saveReview(
        movieId: Long,
        mediaType: MediaType,
        userRating: Int,
        note: String,
        watchedSeason: Int,
        reaction: String,
    ) {
        val modal = _movieModal.value ?: return
        val key = "${mediaType.value}_$movieId"
        if (modal.uniqueKey != key) return
        viewModelScope.launch {
            _movieModal.value = modal.copy(isSavingReview = true)
            val isAlreadyWatched = watchedMap.value.containsKey(key)
            val result = if (!isAlreadyWatched) {
                watchedRepository.markAsWatched(
                    movie = modal.summary,
                    details = modal.details,
                    watchedSeason = watchedSeason,
                    reaction = reaction,
                    initialTvStatus = if (mediaType == MediaType.TV) {
                        TvWatchStatus.NOT_STARTED.key
                    } else {
                        TvWatchStatus.COMPLETED.key
                    },
                ).let { markRes ->
                    if (markRes.isSuccess) {
                        if (mediaType != MediaType.TV && watchlistMap.value.containsKey(key)) {
                            watchlistRepository.removeFromWatchlist(movieId, mediaType)
                        }
                        watchedRepository.updateRatingAndNote(
                            movieId = movieId,
                            userRating = userRating,
                            note = note,
                            mediaType = mediaType,
                            watchedSeason = watchedSeason,
                            reaction = reaction,
                            details = modal.details,
                        )
                    } else {
                        markRes
                    }
                }
            } else {
                watchedRepository.updateRatingAndNote(
                    movieId = movieId,
                    userRating = userRating,
                    note = note,
                    mediaType = mediaType,
                    watchedSeason = watchedSeason,
                    reaction = reaction,
                    details = modal.details,
                )
            }
            _movieModal.value = _movieModal.value?.copy(isSavingReview = false)
            result.fold(
                onSuccess = {
                    _snackbarMessages.tryEmit("Saved your CineLog review.")
                },
                onFailure = { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to save review.")
                }
            )
        }
    }

    fun openSocialCard(movie: WatchedMovie) {
        _socialCardMovie.value = movie
    }

    fun dismissSocialCard() {
        _socialCardMovie.value = null
    }

    fun openWallpaperEditor(title: String, image: TmdbImageItem) {
        _wallpaperEditorImage.value = title to image
    }

    fun dismissWallpaperEditor() {
        _wallpaperEditorImage.value = null
    }

    fun openPersonDetails(roleLabel: String, stat: PersonStat) {
        _selectedPersonStat.value = roleLabel to stat
    }

    fun dismissPersonDetails() {
        _selectedPersonStat.value = null
    }

    fun notifyMessage(message: String) {
        _snackbarMessages.tryEmit(message)
    }

    fun selectAccentPreset(preset: AccentPreset) {
        val pref = LocalAccentPreference(preset = preset, hex = preset.defaultHex)
        _accentPreference.value = pref
        viewModelScope.launch {
            settingsRepository.saveAccentPreference(preset, preset.defaultHex).onFailure { err ->
                _snackbarMessages.tryEmit("Theme updated locally (sync pending: ${err.localizedMessage})")
            }
        }
    }

    fun saveCustomAccentHex(rawHex: String) {
        val normalized = UserSettingsRepository.normalizeHexColor(rawHex)
        if (normalized == null) {
            _snackbarMessages.tryEmit("Enter a valid 6-digit hex color like #E50914.")
            return
        }
        val matchingPreset = AccentPreset.entries
            .firstOrNull { it != AccentPreset.CUSTOM && it.defaultHex.equals(normalized, ignoreCase = true) }
            ?: AccentPreset.CUSTOM
        _accentPreference.value = LocalAccentPreference(preset = matchingPreset, hex = normalized)
        viewModelScope.launch {
            settingsRepository.saveAccentPreference(matchingPreset, normalized).fold(
                onSuccess = {
                    _snackbarMessages.tryEmit("Theme accent saved ($normalized).")
                },
                onFailure = { err ->
                    _snackbarMessages.tryEmit("Theme saved locally (sync pending: ${err.localizedMessage})")
                }
            )
        }
    }

    fun runOldDataMigration() {
        if (_migrationState.value.isRunning) return
        if (!isTmdbKeyConfigured) {
            _snackbarMessages.tryEmit("Add TMDB_API_KEY in AI Studio Secrets first.")
            return
        }
        if (!isOnline.value) {
            _snackbarMessages.tryEmit("Connect to the internet to update old watched items.")
            return
        }
        val allWatched = (watchedState.value as? WatchedUiState.Success)?.movies.orEmpty()
        val candidates = allWatched.filter { it.needsMetadataBackfill }
        if (candidates.isEmpty()) {
            _snackbarMessages.tryEmit("All watched entries already have genres, runtime, cast & language!")
            return
        }

        viewModelScope.launch {
            _migrationState.value = MigrationProgressState(
                isRunning = true,
                completed = 0,
                total = candidates.size,
                updated = 0,
                failed = 0,
                message = "Fetching missing genres, runtimes, cast, directors & languages…",
            )
            var updated = 0
            var failed = 0
            var done = 0
            val batches = candidates.chunked(3)
            for (batch in batches) {
                coroutineScope {
                    val results = batch.map { item ->
                        async {
                            val detailsRes = tmdbRepository.getMediaDetails(
                                mediaId = item.movieId,
                                mediaType = item.resolvedMediaType,
                                includeImagesAndProviders = false,
                            )
                            detailsRes.fold(
                                onSuccess = { details ->
                                    val saveRes = watchedRepository.enrichWatchedMetadata(
                                        movieId = item.movieId,
                                        mediaType = item.resolvedMediaType,
                                        details = details,
                                    )
                                    saveRes.isSuccess
                                },
                                onFailure = { false }
                            )
                        }
                    }.awaitAll()
                    results.forEach { ok ->
                        done += 1
                        if (ok) updated += 1 else failed += 1
                    }
                    _migrationState.value = MigrationProgressState(
                        isRunning = true,
                        completed = done,
                        total = candidates.size,
                        updated = updated,
                        failed = failed,
                        message = "Updated $done of ${candidates.size} titles…",
                    )
                }
            }
            val summaryMsg = if (failed == 0) {
                "Updated $updated watched ${if (updated == 1) "entry" else "entries"} cleanly!"
            } else {
                "Updated $updated entries ($failed failed — tap to retry)."
            }
            _migrationState.value = MigrationProgressState(
                isRunning = false,
                completed = done,
                total = candidates.size,
                updated = updated,
                failed = failed,
                message = summaryMsg,
            )
            _snackbarMessages.tryEmit(summaryMsg)
        }
    }

    override fun onCleared() {
        super.onCleared()
        networkMonitor.unregister()
    }

    internal companion object {
        const val TAG = "CineLogVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val DISCOVER_ERROR_MESSAGE = "Could not reach TMDB. Check your internet and try again."
    }
}
