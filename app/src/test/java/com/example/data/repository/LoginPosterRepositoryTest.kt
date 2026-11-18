package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.TmdbApiService
import com.example.data.remote.TmdbImagesResponse
import com.example.data.remote.TmdbMovieDetailDto
import com.example.data.remote.TmdbMovieDto
import com.example.data.remote.TmdbSearchResponse
import com.example.data.remote.TmdbSeasonDetailDto
import com.example.data.remote.TmdbTrendingAllItemDto
import com.example.data.remote.TmdbTrendingAllResponse
import com.example.data.remote.TmdbTvDetailDto
import com.example.data.remote.TmdbTvSearchResponse
import com.example.data.remote.TmdbVideosResponse
import com.example.data.remote.TmdbWatchProvidersResponse
import com.example.ui.screens.distributePostersAcrossColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LoginPosterRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeLoginTmdbApiService(
        private val discoverPages: Map<Int, List<TmdbMovieDto>> = emptyMap(),
        private val trendingResults: List<TmdbTrendingAllItemDto> = emptyList(),
        private val throwOnDiscover: Boolean = false,
        private val throwOnTrending: Boolean = false,
    ) : TmdbApiService {
        val discoverCalls = mutableListOf<Triple<String, String, Int>>()
        var trendingCalls = 0

        override suspend fun discoverMovies(
            withCompanies: String,
            sortBy: String,
            page: Int,
            includeAdult: Boolean,
            language: String,
            apiKey: String?,
            bearerAuth: String?,
        ): TmdbSearchResponse {
            discoverCalls.add(Triple(withCompanies, sortBy, page))
            if (throwOnDiscover) throw IOException("Network offline")
            return TmdbSearchResponse(page = page, results = discoverPages[page].orEmpty())
        }

        override suspend fun getTrendingAllWeek(
            apiKey: String?,
            bearerAuth: String?,
            language: String,
            page: Int,
        ): TmdbTrendingAllResponse {
            trendingCalls++
            if (throwOnTrending) throw IOException("Network offline")
            return TmdbTrendingAllResponse(page = page, results = trendingResults)
        }

        override suspend fun searchMovies(query: String, apiKey: String?, bearerAuth: String?, year: Int?, includeAdult: Boolean, language: String, page: Int) = TmdbSearchResponse()
        override suspend fun getTrendingMovies(apiKey: String?, bearerAuth: String?, language: String, page: Int) = TmdbSearchResponse()
        override suspend fun getMovieDetails(movieId: Long, apiKey: String?, bearerAuth: String?, appendToResponse: String, language: String) = TmdbMovieDetailDto(id = movieId)
        override suspend fun getMovieImages(movieId: Long, apiKey: String?, bearerAuth: String?, includeImageLanguage: String) = TmdbImagesResponse()
        override suspend fun getMovieVideos(movieId: Long, apiKey: String?, bearerAuth: String?, language: String, includeVideoLanguage: String) = TmdbVideosResponse()
        override suspend fun getMovieRecommendations(movieId: Long, apiKey: String?, bearerAuth: String?, language: String, page: Int) = TmdbSearchResponse()
        override suspend fun getMovieWatchProviders(movieId: Long, apiKey: String?, bearerAuth: String?) = TmdbWatchProvidersResponse()
        override suspend fun searchTvShows(query: String, apiKey: String?, bearerAuth: String?, firstAirDateYear: Int?, includeAdult: Boolean, language: String, page: Int) = TmdbTvSearchResponse()
        override suspend fun getTrendingTvShows(apiKey: String?, bearerAuth: String?, language: String, page: Int) = TmdbTvSearchResponse()
        override suspend fun getTvShowDetails(tvId: Long, apiKey: String?, bearerAuth: String?, appendToResponse: String, language: String) = TmdbTvDetailDto(id = tvId)
        override suspend fun getTvImages(tvId: Long, apiKey: String?, bearerAuth: String?, includeImageLanguage: String) = TmdbImagesResponse()
        override suspend fun getTvVideos(tvId: Long, apiKey: String?, bearerAuth: String?, language: String, includeVideoLanguage: String) = TmdbVideosResponse()
        override suspend fun getTvSeasonVideos(tvId: Long, seasonNumber: Int, apiKey: String?, bearerAuth: String?, language: String, includeVideoLanguage: String) = TmdbVideosResponse()
        override suspend fun getTvRecommendations(tvId: Long, apiKey: String?, bearerAuth: String?, language: String, page: Int) = TmdbTvSearchResponse()
        override suspend fun getTvWatchProviders(tvId: Long, apiKey: String?, bearerAuth: String?) = TmdbWatchProvidersResponse()
        override suspend fun getTvSeasonDetails(tvId: Long, seasonNumber: Int, apiKey: String?, bearerAuth: String?, language: String) = TmdbSeasonDetailDto()
    }

    @Test
    fun `default login poster source is MARVEL and style is SLIDESHOW`() {
        assertEquals(LoginPosterSource.MARVEL, DEFAULT_LOGIN_POSTER_SOURCE)
        assertEquals(LoginBackgroundStyle.SLIDESHOW, DEFAULT_LOGIN_BACKGROUND_STYLE)
    }

    @Test
    fun `getMarvelPosters fetches pages 1 to 3 deduplicates filters nulls and caps at 18 w780 for slideshow`() = runBlocking {
        val page1 = (1..15).map { idx ->
            TmdbMovieDto(
                id = idx.toLong(),
                title = "Marvel $idx",
                posterPath = if (idx == 3) null else "/m$idx.jpg",
            )
        }
        val page2 = (14..28).map { idx -> // id 14, 15 duplicate page 1
            TmdbMovieDto(
                id = idx.toLong(),
                title = "Marvel $idx",
                posterPath = if (idx == 20) "" else "/m$idx.jpg",
            )
        }
        val page3 = (28..40).map { idx -> // id 28 duplicates page 2
            TmdbMovieDto(
                id = idx.toLong(),
                title = "Marvel $idx",
                posterPath = "/m$idx.jpg",
            )
        }
        val fakeApi = FakeLoginTmdbApiService(
            discoverPages = mapOf(1 to page1, 2 to page2, 3 to page3)
        )
        val repo = TmdbRepository(
            apiService = fakeApi,
            apiKeyProvider = { "test_tmdb_api_key_12345" },
            loginPosterCache = LoginPosterCache(memoryStore = LoginPosterMemoryStore()),
        )

        val slideshowPosters = repo.getMarvelPosters()

        assertEquals(3, fakeApi.discoverCalls.size)
        assertTrue(fakeApi.discoverCalls.all { it.first == "420" && it.second == "popularity.desc" })
        assertEquals(setOf(1, 2, 3), fakeApi.discoverCalls.map { it.third }.toSet())
        assertEquals(0, fakeApi.trendingCalls)
        assertEquals(18, slideshowPosters.size)
        assertEquals(18, slideshowPosters.distinct().size)
        assertTrue(slideshowPosters.all { it.startsWith("https://image.tmdb.org/t/p/w780/m") })
        assertFalse(slideshowPosters.any { it.endsWith("/m3.jpg") || it.endsWith("/m20.jpg") })

        val wallPosters = repo.getLoginPosters(
            source = LoginPosterSource.MARVEL,
            style = LoginBackgroundStyle.POSTER_WALL,
            context = null,
        )
        assertEquals(30, wallPosters.size)
        assertTrue(wallPosters.all { it.startsWith("https://image.tmdb.org/t/p/w342/m") })
    }

    @Test
    fun `getMarvelPosters fills from trending all week when fewer than 6 Marvel posters returned`() = runBlocking {
        val marvelSmall = (1..5).map { idx ->
            TmdbMovieDto(id = idx.toLong(), title = "Marvel $idx", posterPath = "/marvel_$idx.jpg")
        }
        val trendingFill = (1..10).map { idx ->
            TmdbTrendingAllItemDto(
                id = 100L + idx,
                mediaType = "movie",
                title = "Trending $idx",
                posterPath = if (idx == 1) "/marvel_1.jpg" else "/trending_$idx.jpg",
            )
        }
        val fakeApi = FakeLoginTmdbApiService(
            discoverPages = mapOf(1 to marvelSmall),
            trendingResults = trendingFill,
        )
        val repo = TmdbRepository(
            apiService = fakeApi,
            apiKeyProvider = { "test_tmdb_api_key_12345" },
            loginPosterCache = LoginPosterCache(memoryStore = LoginPosterMemoryStore()),
        )

        val posters = repo.getMarvelPosters()

        assertEquals(1, fakeApi.trendingCalls)
        assertEquals(14, posters.size) // 5 marvel + 9 non-duplicate trending
        assertEquals(14, posters.distinct().size)
        assertTrue(posters.contains("https://image.tmdb.org/t/p/w780/marvel_1.jpg"))
        assertTrue(posters.contains("https://image.tmdb.org/t/p/w780/trending_2.jpg"))
    }

    @Test
    fun `getLoginPosters returns empty list silently when network fails`() = runBlocking {
        val fakeApi = FakeLoginTmdbApiService(
            throwOnDiscover = true,
            throwOnTrending = true,
        )
        val repo = TmdbRepository(
            apiService = fakeApi,
            apiKeyProvider = { "test_tmdb_api_key_12345" },
            loginPosterCache = LoginPosterCache(memoryStore = LoginPosterMemoryStore()),
        )

        val posters = repo.getLoginPosters(source = LoginPosterSource.MARVEL, context = null)
        assertTrue(posters.isEmpty())
    }

    @Test
    fun `getLoginPosters caches in memory and DataStore for 24 hours`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheFile = java.io.File(tempFolder.root, "test_login_posters.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { cacheFile },
        )
        var nowMs = 1_000_000L
        val marvelMovies = (1..12).map { idx ->
            TmdbMovieDto(id = idx.toLong(), title = "Marvel $idx", posterPath = "/m_$idx.jpg")
        }
        val fakeApi = FakeLoginTmdbApiService(discoverPages = mapOf(1 to marvelMovies))

        // 1. Cold fetch saves to memory & DataStore
        val repo1 = TmdbRepository(
            apiService = fakeApi,
            apiKeyProvider = { "test_tmdb_api_key_12345" },
            loginPosterCache = LoginPosterCache(
                memoryStore = LoginPosterMemoryStore(),
                clock = { nowMs },
                dataStoreProvider = { dataStore },
            ),
        )
        val first = repo1.getLoginPosters(source = LoginPosterSource.MARVEL, context = context)
        assertEquals(12, first.size)
        assertEquals(3, fakeApi.discoverCalls.size)
        assertEquals(first, repo1.peekMemoryCachedLoginPosters(LoginPosterSource.MARVEL))

        // 2. New repository instance (empty memory) within 24h reads from DataStore without network
        nowMs += TimeUnit.HOURS.toMillis(23)
        val repo2 = TmdbRepository(
            apiService = fakeApi,
            apiKeyProvider = { "test_tmdb_api_key_12345" },
            loginPosterCache = LoginPosterCache(
                memoryStore = LoginPosterMemoryStore(),
                clock = { nowMs },
                dataStoreProvider = { dataStore },
            ),
        )
        val fromDataStore = repo2.getLoginPosters(source = LoginPosterSource.MARVEL, context = context)
        assertEquals(first.toSet(), fromDataStore.toSet())
        assertEquals(3, fakeApi.discoverCalls.size) // No new network calls!

        // 3. Past 24 hours -> cache expired -> refreshes from network
        nowMs += TimeUnit.HOURS.toMillis(2)
        val refreshed = repo2.getLoginPosters(source = LoginPosterSource.MARVEL, context = context)
        assertEquals(12, refreshed.size)
        assertEquals(6, fakeApi.discoverCalls.size)
    }

    @Test
    fun `distributePostersAcrossColumns keeps columns disjoint and halves aligned`() {
        val urls = (1..30).map { "https://image.tmdb.org/t/p/w342/p$it.jpg" }
        val columns = distributePostersAcrossColumns(urls, columnCount = 3)

        assertEquals(3, columns.size)
        val halfSets = columns.map { col ->
            val half = col.size / 2
            assertEquals(col.subList(0, half), col.subList(half, col.size))
            col.toSet()
        }
        assertTrue(halfSets[0].intersect(halfSets[1]).isEmpty())
        assertTrue(halfSets[1].intersect(halfSets[2]).isEmpty())
        assertTrue(halfSets[0].intersect(halfSets[2]).isEmpty())

        // Even with only 2 posters, adjacent columns never show the same poster at row i
        val twoPosterCols = distributePostersAcrossColumns(listOf("A", "B"), columnCount = 3)
        for (i in twoPosterCols[0].indices) {
            assertNotEquals(twoPosterCols[0][i], twoPosterCols[1][i])
            assertNotEquals(twoPosterCols[1][i], twoPosterCols[2][i])
        }
    }
}
