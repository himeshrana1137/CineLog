package com.example.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.MediaType
import com.example.data.model.MovieSummary
import com.example.data.model.TvSeasonSummary
import com.example.data.remote.TmdbApiService
import com.example.data.remote.TmdbEpisodeDto
import com.example.data.remote.TmdbImagesResponse
import com.example.data.remote.TmdbMovieDetailDto
import com.example.data.remote.TmdbSearchResponse
import com.example.data.remote.TmdbSeasonDetailDto
import com.example.data.remote.TmdbSeasonSummaryDto
import com.example.data.remote.TmdbTrendingAllResponse
import com.example.data.remote.TmdbTvDetailDto
import com.example.data.remote.TmdbTvDto
import com.example.data.remote.TmdbTvSearchResponse
import com.example.data.remote.TmdbVideosResponse
import com.example.data.remote.TmdbWatchProvidersResponse
import com.example.data.repository.CustomListRepository
import com.example.data.repository.TmdbRepository
import com.example.data.repository.TvProgressRepository
import com.example.data.repository.UserSettingsRepository
import com.example.data.repository.WatchedMovieRepository
import com.example.data.repository.WatchlistRepository
import com.example.util.NetworkMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowNetworkCapabilities

class TvSeasonToggleViewModelTest : FirestoreEmulatorTestBase() {

    private class FakeTmdbApiService : TmdbApiService {
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
        ) = TmdbTvSearchResponse(
            results = listOf(
                TmdbTvDto(
                    id = 1396L,
                    name = "Breaking Bad",
                    firstAirDate = "2008-01-20",
                    voteAverage = 8.9,
                    originalLanguage = "en",
                )
            )
        )

        override suspend fun getTvShowDetails(
            tvId: Long,
            apiKey: String?,
            bearerAuth: String?,
            appendToResponse: String,
            language: String,
        ) = TmdbTvDetailDto(
            id = tvId,
            name = "Breaking Bad",
            firstAirDate = "2008-01-20",
            voteAverage = 8.9,
            overview = "Chemistry teacher turned kingpin.",
            episodeRunTime = listOf(47),
            originalLanguage = "en",
            numberOfSeasons = 2,
            numberOfEpisodes = 7,
            seasons = listOf(
                TmdbSeasonSummaryDto(
                    id = 1L,
                    seasonNumber = 1,
                    name = "Season 1",
                    episodeCount = 3,
                    airDate = "2008-01-20",
                ),
                TmdbSeasonSummaryDto(
                    id = 2L,
                    seasonNumber = 2,
                    name = "Season 2",
                    episodeCount = 4,
                    airDate = "2009-03-08",
                ),
            ),
        )

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

        override suspend fun getTrendingAllWeek(
            apiKey: String?,
            bearerAuth: String?,
            language: String,
            page: Int,
        ) = TmdbTrendingAllResponse()

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
        ) = TmdbSeasonDetailDto(
            id = seasonNumber.toLong(),
            seasonNumber = seasonNumber,
            name = "Season $seasonNumber",
            airDate = "2008-01-20",
            episodes = (1..(if (seasonNumber == 1) 3 else 4)).map { ep ->
                TmdbEpisodeDto(
                    id = ep.toLong(),
                    episodeNumber = ep,
                    seasonNumber = seasonNumber,
                    name = "Episode $ep",
                    runtime = 47,
                    airDate = "2008-01-20",
                )
            },
        )
    }

    @Test
    fun markingSeasonWatched_keepsItWatchedAfterSnapshotListenerFires() = runBlocking {
        val uid = signInTestUser("season_toggle_test@test.com")
        val context = ApplicationProvider.getApplicationContext<Context>()

        val tmdbRepo = TmdbRepository(
            apiService = FakeTmdbApiService(),
            apiKeyProvider = { "test_valid_api_key_12345" },
        )
        val watchedRepo = WatchedMovieRepository(firestore, auth)
        val tvProgressRepo = TvProgressRepository(firestore, auth)
        val watchlistRepo = WatchlistRepository(firestore, auth)
        val customListRepo = CustomListRepository(firestore, auth)
        val settingsRepo = UserSettingsRepository(
            db = firestore,
            auth = auth,
        )

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        if (activeNetwork != null) {
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: ShadowNetworkCapabilities.newInstance()
            Shadows.shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            Shadows.shadowOf(cm).setNetworkCapabilities(activeNetwork, caps)
        }
        val networkMonitor = NetworkMonitor(context)
        assertTrue("Expected NetworkMonitor isOnline in test", networkMonitor.isOnline.value)

        val viewModel = CineLogViewModel(
            tmdbRepository = tmdbRepo,
            watchedRepository = watchedRepo,
            tvProgressRepository = tvProgressRepo,
            watchlistRepository = watchlistRepo,
            customListRepository = customListRepo,
            settingsRepository = settingsRepo,
            networkMonitor = networkMonitor,
            currentUserId = uid,
        )

        // Actively subscribe to modalTvSeasons and watchedMap as Compose UI does
        val seasonsCollector = launch { viewModel.modalTvSeasons.collect { } }
        val watchedCollector = launch { viewModel.watchedMap.collect { } }

        try {
            val summary = MovieSummary(
                movieId = 1396L,
                mediaType = MediaType.TV,
                title = "Breaking Bad",
                posterPath = "/ggFHVNu6YYI5L9pCfOacjizRGt.jpg",
                backdropPath = null,
                releaseDate = "2008-01-20",
                rating = 8.9,
                overview = "Chemistry teacher turned kingpin.",
                originalLanguage = "en",
            )

            viewModel.openMovieDetails(summary)
            withTimeout(10_000L) {
                viewModel.movieModal.first { it?.details?.seasons?.isNotEmpty() == true }
            }

            val season1 = TvSeasonSummary(
                seasonNumber = 1,
                name = "Season 1",
                episodeCount = 3,
                airDate = "2008-01-20",
            )

            // Tap Season 1 Watched toggle
            viewModel.onToggleSeasonWatched(1396L, season1)

            // Wait for Firestore batch write and snapshot listener confirmation (hasPendingWrites == false)
            withTimeout(10_000L) {
                viewModel.isSavingTvProgress.first { !it }
                tvProgressRepo.observeSeasonsForShow(uid, 1396L).first { seasons ->
                    seasons.any { it.seasonNumber == 1 && it.watched && it.watchedEpisodes == listOf(1, 2, 3) }
                }
                viewModel.watchedMap.first { map ->
                    map["tv_1396"]?.status == "WATCHING" && map["tv_1396"]?.watchedEpisodesCount == 3
                }
            }

            // Allow extra time for any delayed snapshot events and verify Season 1 stays watched
            delay(500L)
            val finalSeason1 = viewModel.modalTvSeasons.value[1]
            assertTrue("Expected Season 1 doc to remain present", finalSeason1 != null)
            assertTrue("Expected Season 1 watched == true", finalSeason1!!.watched)
            assertTrue("Expected Season 1 isConsideredWatched == true", finalSeason1.isConsideredWatched(3))
            assertEquals(listOf(1, 2, 3), finalSeason1.watchedEpisodes)
            assertEquals("WATCHING", viewModel.watchedMap.value["tv_1396"]?.status)
            assertEquals(3, viewModel.watchedMap.value["tv_1396"]?.watchedEpisodesCount)
        } finally {
            seasonsCollector.cancel()
            watchedCollector.cancel()
            viewModel.dismissMovieDetails()
            watchedRepo.removeFromWatched(1396L, MediaType.TV)
        }
    }
}
