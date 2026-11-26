package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.remote.TmdbApiService
import com.example.data.remote.TmdbDohDns
import com.example.data.remote.TmdbImagesResponse
import com.example.data.remote.TmdbMovieDetailDto
import com.example.data.remote.TmdbSearchResponse
import com.example.data.remote.TmdbSeasonDetailDto
import com.example.data.remote.TmdbTrendingAllItemDto
import com.example.data.remote.TmdbTrendingAllResponse
import com.example.data.remote.TmdbTvDetailDto
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

class TmdbDiscoverFailureTest : FirestoreEmulatorTestBase() {

    private class ControllableTmdbApiService(
        var trendingAllHandler: (attempt: Int) -> TmdbTrendingAllResponse,
    ) : TmdbApiService {
        val trendingAllAttempts = AtomicInteger(0)

        override suspend fun getTrendingAllWeek(
            apiKey: String?,
            bearerAuth: String?,
            language: String,
            page: Int,
        ): TmdbTrendingAllResponse {
            val attempt = trendingAllAttempts.incrementAndGet()
            return trendingAllHandler(attempt)
        }

        override suspend fun searchMovies(
            query: String,
            apiKey: String?,
            bearerAuth: String?,
            year: Int?,
            includeAdult: Boolean,
            language: String,
            page: Int,
        ) = TmdbSearchResponse()

        override suspend fun getTrendingMovies(
            apiKey: String?,
            bearerAuth: String?,
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

        override suspend fun getTrendingTvShows(
            apiKey: String?,
            bearerAuth: String?,
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

    private fun createViewModel(tmdbRepository: TmdbRepository): CineLogViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
        )
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
    fun tmdbRepository_retriesTwiceWithBackoffOnNetworkFailure_thenFailsCleanly() = runBlocking {
        val fakeApi = ControllableTmdbApiService {
            throw UnknownHostException("Unable to resolve host \"api.themoviedb.org\"")
        }
        val recordedDelays = mutableListOf<Long>()
        val repo = TmdbRepository(
            apiService = fakeApi,
            apiKeyProvider = { "valid_test_tmdb_key_123456" },
            loginPosterCache = LoginPosterCache(),
            retryDelaysMs = listOf(100L, 250L),
            delayProvider = { ms -> recordedDelays.add(ms) },
        )

        val result = repo.getTrendingAllMedia()
        assertTrue("Expected failure after 2 retries", result.isFailure)
        assertEquals("Expected 1 initial attempt + 2 retries", 3, fakeApi.trendingAllAttempts.get())
        assertEquals(listOf(100L, 250L), recordedDelays)
        assertTrue(
            "Expected root UnknownHostException cause",
            result.exceptionOrNull()?.cause is UnknownHostException,
        )
    }

    @Test
    fun discoverViewModel_onTmdbFailureWithoutCache_stopsLoadingAndEmitsErrorCardState(): Unit = runBlocking {
        val fakeApi = ControllableTmdbApiService {
            throw UnknownHostException("Carrier DNS blocked api.themoviedb.org")
        }
        val repo = TmdbRepository(
            apiService = fakeApi,
            apiKeyProvider = { "valid_test_tmdb_key_123456" },
            loginPosterCache = LoginPosterCache(),
            retryDelaysMs = listOf(1L, 1L),
            delayProvider = { },
        )
        val viewModel = createViewModel(repo)

        val finalState = withTimeout(FLOW_TIMEOUT_MS) {
            viewModel.discoverState.first { it !is DiscoverUiState.Loading }
        }

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
        assertEquals("Expected 3 total attempts (1 + 2 retries)", 3, fakeApi.trendingAllAttempts.get())
    }

    @Test
    fun discoverViewModel_onTmdbFailureAfterPreviousSuccess_showsCachedListAsOfflineData(): Unit = runBlocking {
        var shouldFail = false
        val fakeApi = ControllableTmdbApiService {
            if (shouldFail) {
                throw SocketTimeoutException("Read timed out contacting api.themoviedb.org")
            }
            TmdbTrendingAllResponse(
                results = listOf(
                    TmdbTrendingAllItemDto(
                        id = 157336L,
                        mediaType = "movie",
                        title = "Interstellar",
                        posterPath = "/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
                        releaseDate = "2014-11-05",
                        voteAverage = 8.4,
                        overview = "A team of explorers travel through a wormhole in space.",
                        originalLanguage = "en",
                    ),
                    TmdbTrendingAllItemDto(
                        id = 1396L,
                        mediaType = "tv",
                        name = "Breaking Bad",
                        posterPath = "/ztkUQFLlC19CCMYHW9o1zWhJRNq.jpg",
                        firstAirDate = "2008-01-20",
                        voteAverage = 8.9,
                        overview = "A chemistry teacher diagnosed with inoperable lung cancer.",
                        originalLanguage = "en",
                    ),
                ),
            )
        }
        val repo = TmdbRepository(
            apiService = fakeApi,
            apiKeyProvider = { "valid_test_tmdb_key_123456" },
            loginPosterCache = LoginPosterCache(),
            retryDelaysMs = listOf(1L, 1L),
            delayProvider = { },
        )
        val viewModel = createViewModel(repo)

        val initialSuccess = withTimeout(FLOW_TIMEOUT_MS) {
            viewModel.discoverState.first { it is DiscoverUiState.Success } as DiscoverUiState.Success
        }
        assertFalse(initialSuccess.isOfflineData)
        assertEquals(2, initialSuccess.movies.size)

        // Now simulate carrier DNS/timeout outage and trigger a refresh
        shouldFail = true
        viewModel.retryDiscover()

        val offlineState = withTimeout(FLOW_TIMEOUT_MS) {
            viewModel.discoverState.first { state ->
                state is DiscoverUiState.Success && state.isOfflineData && !state.isRetrying
            } as DiscoverUiState.Success
        }
        assertTrue("Expected cached response marked as offline data", offlineState.isOfflineData)
        assertEquals(2, offlineState.movies.size)
        assertEquals("Interstellar", offlineState.movies.first().title)
    }

    private companion object {
        const val FLOW_TIMEOUT_MS = 10_000L
    }
}
