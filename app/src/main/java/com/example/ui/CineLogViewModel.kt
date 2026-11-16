package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MediaType
import com.example.data.model.MovieDetails
import com.example.data.model.MovieSummary
import com.example.data.model.WatchedMovie
import com.example.data.repository.TmdbApiKeyMissingException
import com.example.data.repository.TmdbRepository
import com.example.data.repository.WatchedMovieRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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

enum class AppTab {
    DISCOVER,
    WATCHED,
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

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CineLogViewModel(
    private val tmdbRepository: TmdbRepository,
    private val watchedRepository: WatchedMovieRepository,
    private val currentUserId: String,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(AppTab.DISCOVER)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _discoverMediaFilter = MutableStateFlow(DiscoverMediaFilter.ALL)
    val discoverMediaFilter: StateFlow<DiscoverMediaFilter> = _discoverMediaFilter.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)

    private val _sortOption = MutableStateFlow(WatchedSortOption.RECENT)
    val sortOption: StateFlow<WatchedSortOption> = _sortOption.asStateFlow()

    private val _filterOption = MutableStateFlow(WatchedFilterOption.ALL)
    val filterOption: StateFlow<WatchedFilterOption> = _filterOption.asStateFlow()

    private val _watchedSearchQuery = MutableStateFlow("")
    val watchedSearchQuery: StateFlow<String> = _watchedSearchQuery.asStateFlow()

    private val _pendingToggleKeys = MutableStateFlow<Set<String>>(emptySet())
    val pendingToggleKeys: StateFlow<Set<String>> = _pendingToggleKeys.asStateFlow()

    private val _movieModal = MutableStateFlow<MovieModalState?>(null)
    val movieModal: StateFlow<MovieModalState?> = _movieModal.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    val isTmdbKeyConfigured: Boolean
        get() = tmdbRepository.isApiKeyConfigured()

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

    // Real-time Firestore watched list with two-tier Flow error unwrapping
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

    val filteredWatchedMovies: StateFlow<List<WatchedMovie>> = combine(
        watchedState,
        _sortOption,
        _filterOption,
        _watchedSearchQuery,
    ) { state, sort, filter, query ->
        val raw = (state as? WatchedUiState.Success)?.movies.orEmpty()
        val filtered = raw.filter { item ->
            val matchesFilter = when (filter) {
                WatchedFilterOption.ALL -> true
                WatchedFilterOption.MOVIES -> !item.isTv
                WatchedFilterOption.TV_SHOWS -> item.isTv
                WatchedFilterOption.RATED_ONLY -> item.userRating > 0
                WatchedFilterOption.FIVE_STARS -> item.userRating == 5
            }
            val q = query.trim()
            val matchesQuery = q.isEmpty() ||
                item.title.contains(q, ignoreCase = true) ||
                item.note.contains(q, ignoreCase = true)
            matchesFilter && matchesQuery
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

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onDiscoverMediaFilterSelected(filter: DiscoverMediaFilter) {
        _discoverMediaFilter.value = filter
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

    fun onWatchedSearchQueryChanged(query: String) {
        _watchedSearchQuery.value = query
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
                    val details = _movieModal.value?.takeIf { it.uniqueKey == key }?.details
                    watchedRepository.markAsWatched(movie, details).fold(
                        onSuccess = {
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
            isLoadingDetails = isTmdbKeyConfigured,
        )
        if (isTmdbKeyConfigured) {
            fetchMediaDetails(movie.movieId, movie.mediaType)
        }
    }

    fun openWatchedDetails(watched: WatchedMovie) {
        val mediaType = watched.resolvedMediaType
        val summary = MovieSummary(
            movieId = watched.movieId,
            mediaType = mediaType,
            title = watched.title,
            posterPath = watched.posterPath,
            backdropPath = null,
            releaseDate = watched.releaseDate,
            rating = watched.rating,
            overview = watched.overview,
        )
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
                numberOfSeasons = watched.numberOfSeasons,
                numberOfEpisodes = watched.numberOfEpisodes,
            )
        } else null

        _movieModal.value = MovieModalState(
            uniqueKey = watched.uniqueKey,
            movieId = watched.movieId,
            mediaType = mediaType,
            summary = summary,
            details = cachedDetails,
            isLoadingDetails = isTmdbKeyConfigured,
        )
        if (isTmdbKeyConfigured) {
            fetchMediaDetails(watched.movieId, mediaType)
        }
    }

    private fun fetchMediaDetails(movieId: Long, mediaType: MediaType) {
        val targetKey = "${mediaType.value}_$movieId"
        viewModelScope.launch {
            tmdbRepository.getMediaDetails(movieId, mediaType).fold(
                onSuccess = { details ->
                    if (_movieModal.value?.uniqueKey == targetKey) {
                        _movieModal.value = _movieModal.value?.copy(
                            summary = details.toSummary(),
                            details = details,
                            isLoadingDetails = false,
                            detailsError = null,
                        )
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

    fun dismissMovieDetails() {
        _movieModal.value = null
    }

    fun saveReview(
        movieId: Long,
        mediaType: MediaType,
        userRating: Int,
        note: String,
        watchedSeason: Int,
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
                ).let { markRes ->
                    if (markRes.isSuccess) {
                        watchedRepository.updateRatingAndNote(
                            movieId = movieId,
                            userRating = userRating,
                            note = note,
                            mediaType = mediaType,
                            watchedSeason = watchedSeason,
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

    private companion object {
        const val TAG = "CineLogVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
