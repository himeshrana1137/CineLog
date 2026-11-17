package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.example.data.model.TmdbImageItem
import com.example.data.model.WatchProvidersRegion
import com.example.data.model.WatchedMovie
import com.example.data.model.WatchlistItem
import com.example.data.model.todayIsoDate
import com.example.data.repository.BatchWatchedImportItem
import com.example.data.repository.CustomListRepository
import com.example.data.repository.LocalAccentPreference
import com.example.data.repository.TmdbApiKeyMissingException
import com.example.data.repository.TmdbRepository
import com.example.data.repository.UserSettingsRepository
import com.example.data.repository.WatchedMovieRepository
import com.example.data.repository.WatchlistRepository
import com.example.util.ImportCandidateRow
import com.example.util.ImportExportHelper
import com.example.util.ImportSourceFormat
import com.example.util.NetworkMonitor
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
    RATED_ONLY("Rated"),
    FIVE_STARS("5★ Favorites"),
}

sealed interface DiscoverUiState {
    data object Loading : DiscoverUiState
    data class Success(
        val movies: List<MovieSummary>,
        val isSearchResult: Boolean,
        val mediaFilter: DiscoverMediaFilter,
    ) : DiscoverUiState
    data class Error(
        val message: String,
        val isMissingApiKey: Boolean = false,
    ) : DiscoverUiState
}

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
    private val watchlistRepository: WatchlistRepository,
    private val customListRepository: CustomListRepository,
    private val settingsRepository: UserSettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val currentUserId: String,
) : ViewModel() {

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

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    val isTmdbKeyConfigured: Boolean
        get() = tmdbRepository.isApiKeyConfigured()

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings(currentUserId)
                .catch { /* Rely on local cached preference when offline or missing */ }
                .collect { remote ->
                    if (remote != null) {
                        val preset = AccentPreset.fromKey(remote.accentPreset)
                        val hex = UserSettingsRepository.normalizeHexColor(remote.accentHex) ?: preset.defaultHex
                        val pref = LocalAccentPreference(preset = preset, hex = hex)
                        _accentPreference.value = pref
                        settingsRepository.saveLocalAccent(preset, hex)
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
                emit(DiscoverUiState.Loading)
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
                        emit(
                            DiscoverUiState.Success(
                                movies = items,
                                isSearchResult = query.isNotBlank(),
                                mediaFilter = mediaFilter,
                            )
                        )
                        prefetchProvidersForCards(items.take(8))
                    },
                    onFailure = { error ->
                        val isMissingKey = error is TmdbApiKeyMissingException
                        val message = error.localizedMessage ?: "Failed to load titles."
                        emit(
                            DiscoverUiState.Error(
                                message = message,
                                isMissingApiKey = isMissingKey,
                            )
                        )
                    }
                )
            }
        }
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

    // Real-time Firestore watched list
    val watchedState: StateFlow<WatchedUiState> = watchedRepository.observeWatchedMovies(currentUserId)
        .map<List<WatchedMovie>, WatchedUiState> { movies -> WatchedUiState.Success(movies) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing watched items", rootError)
            val msg = rootError.message ?: "Failed to sync watched library."
            _snackbarMessages.tryEmit(msg)
            emit(WatchedUiState.Error(msg))
        }
        .stateIn(
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

    // Real-time Firestore Watchlist
    val watchlistState: StateFlow<WatchlistUiState> = watchlistRepository.observeWatchlist(currentUserId)
        .map<List<WatchlistItem>, WatchlistUiState> { items ->
            WatchlistUiState.Success(
                items.sortedByDescending { it.addedAt ?: Timestamp(0, 0) }
            )
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing watchlist", rootError)
            val msg = rootError.message ?: "Failed to sync watchlist."
            _snackbarMessages.tryEmit(msg)
            emit(WatchlistUiState.Error(msg))
        }
        .stateIn(
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

    // Real-time Firestore Custom Lists
    val customListsState: StateFlow<CustomListsUiState> = customListRepository.observeLists(currentUserId)
        .map<List<CustomList>, CustomListsUiState> { lists ->
            CustomListsUiState.Success(
                lists.sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            )
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing custom lists", rootError)
            val msg = rootError.message ?: "Failed to sync custom lists."
            _snackbarMessages.tryEmit(msg)
            emit(CustomListsUiState.Error(msg))
        }
        .stateIn(
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

    // Stats computed live from watched list
    val statsSummary: StateFlow<CineStatsSummary> = watchedState
        .map { state ->
            when (state) {
                is WatchedUiState.Success -> StatsCalculator.computeStats(state.movies)
                else -> CineStatsSummary()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CineStatsSummary(),
        )

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
        _refreshTrigger.value += 1
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

                    watchedRepository.markAsWatched(movie, details).fold(
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

                watchedRepository.markAsWatched(summary, details).fold(
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
        viewModelScope.launch {
            tmdbRepository.getMediaDetails(movieId, mediaType, includeImagesAndProviders = true).fold(
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
                ).let { markRes ->
                    if (markRes.isSuccess) {
                        if (watchlistMap.value.containsKey(key)) {
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

    private companion object {
        const val TAG = "CineLogVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
