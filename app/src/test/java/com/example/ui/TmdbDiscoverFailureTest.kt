package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.remote.TmdbApiService
import com.example.data.remote.TmdbDohDns
import com.example.data.remote.TmdbImagesResponse
import com.example.data.remote.TmdbMovieDetailDto
import com.example.data.remote.TmdbMovieDto
import com.example.data.remote.TmdbSearchResponse
import com.example.data.remote.TmdbSeasonDetailDto
import com.example.data.remote.TmdbTrendingAllResponse
import com.example.data.remote.TmdbTvDetailDto
import com.example.data.remote.TmdbTvDto
import com.example.data.remote.TmdbTvSearchResponse
import com.example.data.remote.TmdbVideosResponse
import com.example.data.remote.TmdbWatchProvidersResponse
import com.example.data.repository.CustomListRepository
import com.example.data.repository.LoginPosterCache
import com.example.data.repository.TmdbRepository
import com.example.data.repository.TvProgressRepository
import com.example.data.repository.UserSettingsRepository
import com.example.data.repository.WatchedMovieRepository
import com.example.data.repository.WatchlistRepository
import com.example.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Dns
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbDiscoverFailureTest : FirestoreEmulatorTestBase() {

    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setUpMainDispatcher() {
        testScheduler = TestCoroutineScheduler()
        testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private class ControllableTmdbApiService(
        var trendingMoviesHandler: suspend (attempt: Int) -> TmdbSearchResponse = { TmdbSearchResponse() },
        var trendingTvHandler: suspend (attempt: Int) -> TmdbTvSearchResponse = { TmdbTvSearchResponse() },
    ) : TmdbApiService {
        val trendingMoviesAttempts = AtomicInteger(0)
        val trendingTvAttempts = AtomicInteger(0)

        override suspend fun getTrendingMovies(
            apiKey: String?,
            bearerAuth: String?,
            language: String,
            page: Int,
        ): TmdbSearchResponse {
            val attempt = trendingMoviesAttempts.incrementAndGet()
            return trendingMoviesHandler(attempt)
        }

        override suspend fun getTrendingTvShows(
            apiKey: String?,
            bearerAuth: String?,
            language: String,
            page: Int,
        ): TmdbTvSearchResponse {
            val attempt = trendingTvAttempts.incrementAndGet()
            return trendingTvHandler(attempt)
        }

        override suspend fun getTrendingAllWeek(
            apiKey: String?,
            bearerAuth: String?,
            language: String,
            page: Int,
        ) = TmdbTrendingAllResponse()

        override suspend fun searchMovies(
            query: String,
            apiKey: String?,
            bearerAuth: String?,
            year: Int?,
            includeAdult: Boolean,
            language: String,
            page: Int,
        ) = TmdbSearchResponse()

        override suspend fun discoverMovies(
            withCompanies: String,
            sortBy: String,
            page: Int,
            includeAdult: Boolean,
            language: String,
            apiKey: String?,
            bearerAuth: String?,
        ) = TmdbSearchResponse()

        override suspend fun getMovieDetails(
            movieId: Long,
            apiKey: String?,
            bearerAuth: String?,
            appendToResponse: String,
            language: String,
        ) = TmdbMovieDetailDto(id = movieId, title = "Movie")

        override suspend fun getMovieImages(
            movieId: Long,
            apiKey: String?,
            bearerAuth: String?,
            includeImageLanguage: String,
        ) = TmdbImagesResponse()

        override suspend fun getMovieVideos(
            movieId: Long,
            apiKey: String?,
            bearerAuth: String?,
            language: String,
            includeVideoLanguage: String,
        ) = TmdbVideosResponse()

        override suspend fun getMovieRecommendations(
            movieId: Long,
            apiKey: String?,
            bearerAuth: String?,
            language: String,
            page: Int,
        ) = TmdbSearchResponse()

        override suspend fun getMovieWatchProviders(
            movieId: Long,
            apiKey: String?,
            bearerAuth: String?,
        ) = TmdbWatchProvidersResponse()

        override suspend fun searchTvShows(
            query: String,
            apiKey: String?,
            bearerAuth: String?,
            firstAirDateYear: Int?,
            includeAdult: Boolean,
            language: String,
            page: Int,
        ) = TmdbTvSearchResponse()

        override suspend fun getTvShowDetails(
            tvId: Long,
            apiKey: String?,
            bearerAuth: String?,
            appendToResponse: String,
            language: String,
        ) = TmdbTvDetailDto(id = tvId, name = "Show")

        override suspend fun getTvImages(
            tvId: Long,
            apiKey: String?,
            bearerAuth: String?,
            includeImageLanguage: String,
        ) = TmdbImagesResponse()

        override suspend fun getTvVideos(
            tvId: Long,
            apiKey: String?,
            bearerAuth: String?,
            language: String,
            includeVideoLanguage: String,
        ) = TmdbVideosResponse()

        override suspend fun getTvSeasonVideos(
            tvId: Long,
            seasonNumber: Int,
            apiKey: String?,
            bearerAuth: String?,
            language: String,
            includeVideoLanguage: String,
        ) = TmdbVideosResponse()

        override suspend fun getTvRecommendations(
            tvId: Long,
            apiKey: String?,
            bearerAuth: String?,
            language: String,
            page: Int,
        ) = TmdbTvSearchResponse()

        override suspend fun getTvWatchProviders(
            tvId: Long,
            apiKey: String?,
            bearerAuth: String?,
        ) = TmdbWatchProvidersResponse()

        override suspend fun getTvSeasonDetails(
            tvId: Long,
            seasonNumber: Int,
            apiKey: String?,
            bearerAuth: String?,
            language: String,
        ) = TmdbSeasonDetailDto(id = 1L, seasonNumber = seasonNumber, name = "Season $seasonNumber")
    }

    private fun createManagedViewModel(
        tmdbRepository: TmdbRepository,
        isOnlineFlow: StateFlow<Boolean> = MutableStateFlow(true),
    ): Pair<ViewModelStore, CineLogViewModel> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CineLogViewModel(
                    tmdbRepository = tmdbRepository,
                    watchedRepository = WatchedMovieRepository(firestore, auth),
                    tvProgressRepository = TvProgressRepository(firestore, auth),
                    watchlistRepository = WatchlistRepository(firestore, auth),
                    customListRepository = CustomListRepository(firestore, auth),
                    settingsRepository = UserSettingsRepository(db = firestore, auth = auth),
                    networkMonitor = NetworkMonitor(context),
                    currentUserId = "discover_test_uid",
                    auth = auth,
                    isOnlineOverride = isOnlineFlow,
                ) as T
            }
        }
        val viewModel = ViewModelProvider(store, factory)[CineLogViewModel::class.java]
        return store to viewModel
    }

    private fun fakeDns(block: (String) -> List<InetAddress>): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = block(hostname)
    }

    @Test
    fun tmdbDohDns_appliesCloudflareThenGoogleThenSystemOnlyToTmdbHosts() {
        assertTrue(TmdbDohDns.isTmdbHost("api.themoviedb.org"))
        assertTrue(TmdbDohDns.isTmdbHost("image.tmdb.org"))
        assertFalse(TmdbDohDns.isTmdbHost("firestore.googleapis.com"))
        assertFalse(TmdbDohDns.isTmdbHost("example.com"))

        val cfAddr = InetAddress.getByAddress("api.themoviedb.org", byteArrayOf(13, 224.toByte(), 161.toByte(), 90))
        val googleAddr = InetAddress.getByAddress("image.tmdb.org", byteArrayOf(13, 224.toByte(), 161.toByte(), 91))
        val sysAddr = InetAddress.getByAddress("firestore.googleapis.com", byteArrayOf(142.toByte(), 250.toByte(), 180.toByte(), 10))

        var cfCalls = 0
        var googleCalls = 0
        var sysCalls = 0

        // 1. Non-TMDB host bypasses Cloudflare & Google DoH completely
        val dnsBypass = TmdbDohDns(
            cloudflareDns = fakeDns {
                cfCalls++
                listOf(cfAddr)
            },
            googleDns = fakeDns {
                googleCalls++
                listOf(googleAddr)
            },
            systemDns = fakeDns {
                sysCalls++
                listOf(sysAddr)
            },
            errorLogger = { _, _ -> },
        )
        assertEquals(listOf(sysAddr), dnsBypass.lookup("firestore.googleapis.com"))
        assertEquals(0, cfCalls)
        assertEquals(0, googleCalls)
        assertEquals(1, sysCalls)

        // 2. TMDB host falls back Cloudflare -> Google when Cloudflare fails
        val dnsGoogleFallback = TmdbDohDns(
            cloudflareDns = fakeDns {
                cfCalls++
                throw UnknownHostException("Cloudflare DoH blocked")
            },
            googleDns = fakeDns {
                googleCalls++
                listOf(googleAddr)
            },
            systemDns = fakeDns {
                sysCalls++
                listOf(sysAddr)
            },
            errorLogger = { _, _ -> },
        )
        assertEquals(listOf(googleAddr), dnsGoogleFallback.lookup("image.tmdb.org"))
        assertEquals(1, cfCalls)
        assertEquals(1, googleCalls)
        assertEquals(1, sysCalls)

        // 3. TMDB host falls back to system DNS when both Cloudflare & Google fail
        val dnsSystemFallback = TmdbDohDns(
            cloudflareDns = fakeDns { throw UnknownHostException("CF fail") },
            googleDns = fakeDns { throw UnknownHostException("Google fail") },
            systemDns = fakeDns { listOf(cfAddr) },
            errorLogger = { _, _ -> },
        )
        assertEquals(listOf(cfAddr), dnsSystemFallback.lookup("api.themoviedb.org"))

        // 4. All three failing throws UnknownHostException
        val dnsAllFail = TmdbDohDns(
            cloudflareDns = fakeDns { throw UnknownHostException("CF fail") },
            googleDns = fakeDns { throw UnknownHostException("Google fail") },
            systemDns = fakeDns { throw UnknownHostException("System fail") },
            errorLogger = { _, _ -> },
        )
        var thrown: Throwable? = null
        try {
            dnsAllFail.lookup("api.themoviedb.org")
        } catch (e: UnknownHostException) {
            thrown = e
        }
        assertTrue(thrown is UnknownHostException)
    }

    @Test
    fun tmdbRepository_retriesTwiceWithBackoffOnNetworkFailure_thenFailsCleanly() = runTest(testDispatcher) {
        val fakeApi = ControllableTmdbApiService(
            trendingMoviesHandler = {
                throw SocketTimeoutException("Read timed out contacting api.themoviedb.org")
            },
            trendingTvHandler = {
                throw SocketTimeoutException("Read timed out contacting api.themoviedb.org")
            },
        )
        val recordedDelays = mutableListOf<Long>()
        val repo = TmdbRepository(
            apiService = fakeApi,
            apiKeyProvider = { "valid_test_tmdb_key_123456" },
            loginPosterCache = LoginPosterCache(),
            retryDelaysMs = listOf(250L, 500L),
            delayProvider = { ms ->
                recordedDelays.add(ms)
                delay(ms)
            },
            ioDispatcher = testDispatcher,
        )

        val result = repo.getTrendingMovies()
        assertTrue("Expected failure after 2 retries", result.isFailure)
        assertEquals("Expected 1 initial attempt + 2 retries", 3, fakeApi.trendingMoviesAttempts.get())
        assertEquals(listOf(250L, 500L), recordedDelays)
        assertEquals(750L, currentTime)
        assertTrue(
            "Expected root SocketTimeoutException cause",
            result.exceptionOrNull()?.cause is SocketTimeoutException,
        )
    }

    @Test
    fun tmdbRepository_failsImmediatelyOnUnknownHostException_withoutRetrying() = runTest(testDispatcher) {
        val fakeApi = ControllableTmdbApiService(
            trendingMoviesHandler = {
                throw UnknownHostException("Unable to resolve host \"api.themoviedb.org\"")
            },
            trendingTvHandler = {
                throw UnknownHostException("Unable to resolve host \"api.themoviedb.org\"")
            },
        )
        val recordedDelays = mutableListOf<Long>()
        val repo = TmdbRepository(
            apiService = fakeApi,
            apiKeyProvider = { "valid_test_tmdb_key_123456" },
            loginPosterCache = LoginPosterCache(),
            retryDelaysMs = listOf(250L, 500L),
            delayProvider = { ms ->
                recordedDelays.add(ms)
                delay(ms)
            },
            ioDispatcher = testDispatcher,
        )

        val result = repo.getTrendingMovies()
        assertTrue("Expected immediate failure on UnknownHostException", result.isFailure)
        assertEquals("Expected no retries on UnknownHostException", 1, fakeApi.trendingMoviesAttempts.get())
        assertTrue("Expected zero backoff delays", recordedDelays.isEmpty())
        assertEquals(0L, currentTime)
        assertTrue(
            "Expected root UnknownHostException cause",
            result.exceptionOrNull()?.cause is UnknownHostException,
        )
    }

    @Test
    fun discoverViewModel_onTmdbFailureWithoutCache_stopsLoadingAndEmitsErrorCardState() = runTest(testDispatcher) {
        val fakeApi = ControllableTmdbApiService(
            trendingMoviesHandler = {
                throw UnknownHostException("Carrier DNS blocked api.themoviedb.org")
            },
            trendingTvHandler = {
                throw UnknownHostException("Carrier DNS blocked api.themoviedb.org")
            },
        )
        val repo = TmdbRepository(
            apiService = fakeApi,
            apiKeyProvider = { "valid_test_tmdb_key_123456" },
            loginPosterCache = LoginPosterCache(),
            retryDelaysMs = listOf(250L, 500L),
            delayProvider = { ms -> delay(ms) },
            ioDispatcher = testDispatcher,
        )
        val (store, viewModel) = createManagedViewModel(repo)
        try {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.discoverState.collect {}
            }

            advanceTimeBy(CineLogViewModel.DEFAULT_DISCOVER_DEBOUNCE_MILLIS)
            runCurrent()

            val finalState = viewModel.discoverState.value
            assertTrue("Expected DiscoverUiState.Error, got $finalState", finalState is DiscoverUiState.Error)
            val errorState = finalState as DiscoverUiState.Error
            assertEquals(
                "Could not reach TMDB. Check your internet and try again.",
                errorState.message,
            )
            assertFalse(errorState.isMissingApiKey)
            assertFalse(errorState.isRetrying)
            assertTrue(
                "Expected technical details to include exception type, got: ${errorState.technicalDetails}",
                errorState.technicalDetails.contains("UnknownHostException"),
            )
            // UnknownHostException fails immediately on attempt 1 (right after the 500ms search debounce)
            assertEquals(1, fakeApi.trendingMoviesAttempts.get())
            assertEquals(CineLogViewModel.DEFAULT_DISCOVER_DEBOUNCE_MILLIS, currentTime)
        } finally {
            store.clear()
        }
    }

    @Test
    fun discoverViewModel_whenOfflineWithoutCache_emitsErrorCardImmediately() = runTest(testDispatcher) {
        val fakeApi = ControllableTmdbApiService(
            trendingMoviesHandler = {
                error("TMDB API should not be called when device is offline")
            },
            trendingTvHandler = {
                error("TMDB API should not be called when device is offline")
            },
        )
        val repo = TmdbRepository(
            apiService = fakeApi,
            apiKeyProvider = { "valid_test_tmdb_key_123456" },
            loginPosterCache = LoginPosterCache(),
            ioDispatcher = testDispatcher,
        )
        val (store, viewModel) = createManagedViewModel(
            tmdbRepository = repo,
            isOnlineFlow = MutableStateFlow(false),
        )
        try {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.discoverState.collect {}
            }

            advanceUntilIdle()

            val finalState = viewModel.discoverState.value
            assertTrue("Expected DiscoverUiState.Error when offline, got $finalState", finalState is DiscoverUiState.Error)
            val errorState = finalState as DiscoverUiState.Error
            assertEquals(
                "Could not reach TMDB. Check your internet and try again.",
                errorState.message,
            )
            assertEquals(0, fakeApi.trendingMoviesAttempts.get())
            assertEquals(0, fakeApi.trendingTvAttempts.get())
        } finally {
            store.clear()
        }
    }

    @Test
    fun discoverViewModel_whenTmdbHangs_stopsLoadingWithinTenSecondOverallLimit() = runTest(testDispatcher) {
        val fakeApi = ControllableTmdbApiService(
            trendingMoviesHandler = {
                delay(60_000L)
                TmdbSearchResponse()
            },
            trendingTvHandler = {
                delay(60_000L)
                TmdbTvSearchResponse()
            },
        )
        val repo = TmdbRepository(
            apiService = fakeApi,
            apiKeyProvider = { "valid_test_tmdb_key_123456" },
            loginPosterCache = LoginPosterCache(),
            retryDelaysMs = listOf(250L, 500L),
            delayProvider = { ms -> delay(ms) },
            ioDispatcher = testDispatcher,
            overallTimeoutMs = 10_000L,
        )
        val (store, viewModel) = createManagedViewModel(repo)
        try {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.discoverState.collect {}
            }

            // Advance past the 500ms initial debounce: Discover enters Loading
            advanceTimeBy(CineLogViewModel.DEFAULT_DISCOVER_DEBOUNCE_MILLIS)
            runCurrent()
            assertTrue(viewModel.discoverState.value is DiscoverUiState.Loading)

            // Advance virtual time to the 10s overall cap (zero real sleeping)
            advanceTimeBy(CineLogViewModel.DEFAULT_DISCOVER_TIMEOUT_MILLIS)
            runCurrent()

            val finalState = viewModel.discoverState.value
            assertTrue("Expected DiscoverUiState.Error after 10s limit, got $finalState", finalState is DiscoverUiState.Error)
            val errorState = finalState as DiscoverUiState.Error
            assertEquals(
                "Could not reach TMDB. Check your internet and try again.",
                errorState.message,
            )
            assertTrue(
                "Expected SocketTimeoutException details, got: ${errorState.technicalDetails}",
                errorState.technicalDetails.contains("SocketTimeoutException"),
            )
        } finally {
            store.clear()
        }
    }

    @Test
    fun discoverViewModel_onTmdbFailureAfterPreviousSuccess_showsCachedListAsOfflineData() = runTest(testDispatcher) {
        var shouldFail = false
        val fakeApi = ControllableTmdbApiService(
            trendingMoviesHandler = {
                if (shouldFail) {
                    throw SocketTimeoutException("Read timed out contacting api.themoviedb.org")
                }
                TmdbSearchResponse(
                    results = listOf(
                        TmdbMovieDto(
                            id = 157336L,
                            title = "Interstellar",
                            posterPath = "/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
                            releaseDate = "2014-11-05",
                            voteAverage = 8.4,
                            overview = "A team of explorers travel through a wormhole in space.",
                            originalLanguage = "en",
                            popularity = 95.0,
                        ),
                    ),
                )
            },
            trendingTvHandler = {
                if (shouldFail) {
                    throw SocketTimeoutException("Read timed out contacting api.themoviedb.org")
                }
                TmdbTvSearchResponse(
                    results = listOf(
                        TmdbTvDto(
                            id = 1396L,
                            name = "Breaking Bad",
                            posterPath = "/ztkUQFLlC19CCMYHW9o1zWhJRNq.jpg",
                            firstAirDate = "2008-01-20",
                            voteAverage = 8.9,
                            overview = "A chemistry teacher diagnosed with inoperable lung cancer.",
                            originalLanguage = "en",
                            popularity = 90.0,
                        ),
                    ),
                )
            },
        )
        val repo = TmdbRepository(
            apiService = fakeApi,
            apiKeyProvider = { "valid_test_tmdb_key_123456" },
            loginPosterCache = LoginPosterCache(),
            retryDelaysMs = listOf(250L, 500L),
            delayProvider = { ms -> delay(ms) },
            ioDispatcher = testDispatcher,
        )
        val (store, viewModel) = createManagedViewModel(repo)
        try {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.discoverState.collect {}
            }

            advanceUntilIdle()

            val initialSuccess = viewModel.discoverState.value as? DiscoverUiState.Success
            assertTrue("Expected initial DiscoverUiState.Success, got ${viewModel.discoverState.value}", initialSuccess != null)
            assertFalse(initialSuccess!!.isOfflineData)
            assertEquals(2, initialSuccess.movies.size)

            // Simulate carrier timeout outage and trigger a refresh
            shouldFail = true
            viewModel.retryDiscover()
            advanceUntilIdle()

            val offlineState = viewModel.discoverState.value as? DiscoverUiState.Success
            assertTrue("Expected cached DiscoverUiState.Success, got ${viewModel.discoverState.value}", offlineState != null)
            assertTrue("Expected cached response marked as offline data", offlineState!!.isOfflineData)
            assertFalse(offlineState.isRetrying)
            assertEquals(2, offlineState.movies.size)
            assertEquals("Interstellar", offlineState.movies.first().title)
        } finally {
            store.clear()
        }
    }
}
