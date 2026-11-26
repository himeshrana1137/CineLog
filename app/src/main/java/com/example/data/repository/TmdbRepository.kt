package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.MediaImages
import com.example.data.model.MediaType
import com.example.data.model.MovieDetails
import com.example.data.model.MovieSummary
import com.example.data.model.TvSeasonDetail
import com.example.data.model.WatchProvidersRegion
import com.example.data.remote.TmdbApiService
import com.example.data.remote.TmdbNetworkClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TmdbApiKeyMissingException : Exception(
    "TMDB API key is not configured. Add TMDB_API_KEY in the AI Studio Secrets panel and rebuild."
)

class TmdbHttpStatusException(
    val statusCode: Int,
    message: String,
) : Exception(message)

private enum class AuthMode {
    API_KEY,
    BEARER_TOKEN,
}

class TmdbRepository internal constructor(
    private val apiService: TmdbApiService,
    private val apiKeyProvider: () -> String,
    private val loginPosterCache: LoginPosterCache,
    private val httpClient: OkHttpClient = TmdbNetworkClient.sharedOkHttpClient,
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
    private val delayProvider: suspend (Long) -> Unit = { delay(it) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val overallTimeoutMs: Long = DEFAULT_OVERALL_TIMEOUT_MS,
) {
    constructor(
        apiService: TmdbApiService = defaultApiService(),
        apiKeyProvider: () -> String = { BuildConfig.TMDB_API_KEY },
    ) : this(
        apiService = apiService,
        apiKeyProvider = apiKeyProvider,
        loginPosterCache = LoginPosterCache(),
        httpClient = TmdbNetworkClient.sharedOkHttpClient,
    )

    private val tvFullDetailsCache = ConcurrentHashMap<Long, MovieDetails>()
    private val tvBaseDetailsCache = ConcurrentHashMap<Long, MovieDetails>()
    private val seasonEpisodesCache = ConcurrentHashMap<String, TvSeasonDetail>()

    private fun sanitizeCredential(raw: String): String {
        var value = raw.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()
        if (value.startsWith("TMDB_API_KEY=", ignoreCase = true)) {
            value = value.substringAfter("=").trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
                .trim()
        }
        if (value.startsWith("Bearer ", ignoreCase = true)) {
            value = value.substring(7).trim()
        }
        return value
    }

    private fun resolveCredential(): Pair<String, AuthMode>? {
        val cleaned = sanitizeCredential(apiKeyProvider())
        if (cleaned.isEmpty() ||
            cleaned == PLACEHOLDER_KEY ||
            cleaned.startsWith("MY_TMDB_", ignoreCase = true) ||
            cleaned.startsWith("YOUR_TMDB_", ignoreCase = true)
        ) {
            return null
        }

        val primaryMode = if (cleaned.contains('.') || cleaned.length > 45) {
            AuthMode.BEARER_TOKEN
        } else {
            AuthMode.API_KEY
        }
        return cleaned to primaryMode
    }

    fun isApiKeyConfigured(): Boolean = resolveCredential() != null

    suspend fun searchMovies(query: String, year: Int? = null): Result<List<MovieSummary>> =
        runTmdbCallWithFallback { credential, mode ->
            when (mode) {
                AuthMode.API_KEY -> apiService.searchMovies(
                    query = query.trim(),
                    year = year,
                    apiKey = credential,
                )
                AuthMode.BEARER_TOKEN -> apiService.searchMovies(
                    query = query.trim(),
                    year = year,
                    bearerAuth = "Bearer $credential",
                )
            }.results.map { it.toSummary() }
        }

    suspend fun searchTvShows(query: String, year: Int? = null): Result<List<MovieSummary>> =
        runTmdbCallWithFallback { credential, mode ->
            when (mode) {
                AuthMode.API_KEY -> apiService.searchTvShows(
                    query = query.trim(),
                    firstAirDateYear = year,
                    apiKey = credential,
                )
                AuthMode.BEARER_TOKEN -> apiService.searchTvShows(
                    query = query.trim(),
                    firstAirDateYear = year,
                    bearerAuth = "Bearer $credential",
                )
            }.results.map { it.toSummary() }
        }

    suspend fun searchBestImportMatch(
        title: String,
        year: Int?,
        preferTv: Boolean = false,
    ): Result<MovieSummary?> {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return Result.success(null)

        val candidatesResult = if (preferTv) {
            val withYear = if (year != null) searchTvShows(cleanTitle, year) else Result.success(emptyList())
            val listWithYear = withYear.getOrNull().orEmpty()
            if (listWithYear.isNotEmpty()) Result.success(listWithYear) else searchTvShows(cleanTitle, null)
        } else {
            val withYear = if (year != null) searchMovies(cleanTitle, year) else Result.success(emptyList())
            val listWithYear = withYear.getOrNull().orEmpty()
            if (listWithYear.isNotEmpty()) Result.success(listWithYear) else searchMovies(cleanTitle, null)
        }

        return candidatesResult.map { candidates ->
            if (candidates.isEmpty()) return@map null
            val targetNorm = cleanTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
            candidates.maxByOrNull { item ->
                val itemNorm = item.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                val itemYear = item.releaseDate.take(4).toIntOrNull()
                var score = 0.0
                if (itemNorm == targetNorm) score += 100.0
                else if (itemNorm.contains(targetNorm) || targetNorm.contains(itemNorm)) score += 40.0
                if (year != null && itemYear != null) {
                    val diff = kotlin.math.abs(year - itemYear)
                    score += when (diff) {
                        0 -> 80.0
                        1 -> 30.0
                        else -> -diff.toDouble()
                    }
                }
                score + (item.popularity.coerceAtMost(50.0) * 0.2)
            }
        }
    }

    suspend fun searchAllMedia(query: String): Result<List<MovieSummary>> =
        runTmdbCallWithFallback { credential, mode ->
            coroutineScope {
                val moviesDeferred = async {
                    when (mode) {
                        AuthMode.API_KEY -> apiService.searchMovies(query = query.trim(), apiKey = credential)
                        AuthMode.BEARER_TOKEN -> apiService.searchMovies(query = query.trim(), bearerAuth = "Bearer $credential")
                    }.results.map { it.toSummary() }
                }
                val tvDeferred = async {
                    when (mode) {
                        AuthMode.API_KEY -> apiService.searchTvShows(query = query.trim(), apiKey = credential)
                        AuthMode.BEARER_TOKEN -> apiService.searchTvShows(query = query.trim(), bearerAuth = "Bearer $credential")
                    }.results.map { it.toSummary() }
                }
                (moviesDeferred.await() + tvDeferred.await())
                    .sortedByDescending { it.popularity }
            }
        }

    suspend fun getTrendingMovies(): Result<List<MovieSummary>> =
        runTmdbCallWithFallback { credential, mode ->
            when (mode) {
                AuthMode.API_KEY -> apiService.getTrendingMovies(apiKey = credential)
                AuthMode.BEARER_TOKEN -> apiService.getTrendingMovies(bearerAuth = "Bearer $credential")
            }.results.map { it.toSummary() }
        }

    suspend fun getTrendingTvShows(): Result<List<MovieSummary>> =
        runTmdbCallWithFallback { credential, mode ->
            when (mode) {
                AuthMode.API_KEY -> apiService.getTrendingTvShows(apiKey = credential)
                AuthMode.BEARER_TOKEN -> apiService.getTrendingTvShows(bearerAuth = "Bearer $credential")
            }.results.map { it.toSummary() }
        }

    suspend fun getTrendingAllMedia(): Result<List<MovieSummary>> =
        runTmdbCallWithFallback { credential, mode ->
            coroutineScope {
                val moviesDeferred = async {
                    when (mode) {
                        AuthMode.API_KEY -> apiService.getTrendingMovies(apiKey = credential)
                        AuthMode.BEARER_TOKEN -> apiService.getTrendingMovies(bearerAuth = "Bearer $credential")
                    }.results.map { it.toSummary() }
                }
                val tvDeferred = async {
                    when (mode) {
                        AuthMode.API_KEY -> apiService.getTrendingTvShows(apiKey = credential)
                        AuthMode.BEARER_TOKEN -> apiService.getTrendingTvShows(bearerAuth = "Bearer $credential")
                    }.results.map { it.toSummary() }
                }
                (moviesDeferred.await() + tvDeferred.await())
                    .sortedByDescending { it.popularity }
            }
        }

    suspend fun getTrendingPostersForLogin(
        posterWidth: String = defaultPosterWidth(DEFAULT_LOGIN_BACKGROUND_STYLE),
    ): List<String> {
        return runTmdbCallWithFallback { credential, mode ->
            when (mode) {
                AuthMode.API_KEY -> apiService.getTrendingAllWeek(apiKey = credential)
                AuthMode.BEARER_TOKEN -> apiService.getTrendingAllWeek(bearerAuth = "Bearer $credential")
            }.results
                .mapNotNull { item ->
                    item.posterPath?.takeIf { it.isNotBlank() }?.let {
                        "$TMDB_IMAGE_BASE_URL$posterWidth$it"
                    }
                }
                .distinct()
        }.getOrDefault(emptyList())
    }

    suspend fun getMarvelPosters(
        targetCount: Int = defaultPosterCount(DEFAULT_LOGIN_BACKGROUND_STYLE),
        minMarvelThreshold: Int = defaultMinMarvelThreshold(DEFAULT_LOGIN_BACKGROUND_STYLE),
        posterWidth: String = defaultPosterWidth(DEFAULT_LOGIN_BACKGROUND_STYLE),
    ): List<String> {
        val marvelPosters = coroutineScope {
            (1..MARVEL_POSTER_PAGES).map { page ->
                async {
                    runTmdbCallWithFallback { credential, mode ->
                        when (mode) {
                            AuthMode.API_KEY -> apiService.discoverMovies(
                                withCompanies = MARVEL_STUDIOS_COMPANY_ID,
                                sortBy = "popularity.desc",
                                page = page,
                                apiKey = credential,
                            )
                            AuthMode.BEARER_TOKEN -> apiService.discoverMovies(
                                withCompanies = MARVEL_STUDIOS_COMPANY_ID,
                                sortBy = "popularity.desc",
                                page = page,
                                bearerAuth = "Bearer $credential",
                            )
                        }.results
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }
            .flatten()
            .filter { !it.posterPath.isNullOrBlank() }
            .distinctBy { it.id }
            .map { "$TMDB_IMAGE_BASE_URL$posterWidth${it.posterPath}" }
            .distinct()
            .take(targetCount)

        val finalUrls = if (marvelPosters.size < minMarvelThreshold) {
            val existing = marvelPosters.toHashSet()
            val trendingFill = runCatching { getTrendingPostersForLogin(posterWidth = posterWidth) }
                .getOrDefault(emptyList())
                .filter { it !in existing }
            (marvelPosters + trendingFill).take(targetCount)
        } else {
            marvelPosters
        }

        return finalUrls.shuffled()
    }

    fun peekMemoryCachedLoginPosters(
        source: LoginPosterSource = DEFAULT_LOGIN_POSTER_SOURCE,
        style: LoginBackgroundStyle = DEFAULT_LOGIN_BACKGROUND_STYLE,
    ): List<String>? = loginPosterCache.peekMemory(source = source, style = style)

    suspend fun getLoginPosters(
        source: LoginPosterSource = DEFAULT_LOGIN_POSTER_SOURCE,
        style: LoginBackgroundStyle = DEFAULT_LOGIN_BACKGROUND_STYLE,
        context: Context? = null,
    ): List<String> {
        loginPosterCache.getCachedPosters(source = source, style = style, context = context)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val targetCount = defaultPosterCount(style)
        val minThreshold = defaultMinMarvelThreshold(style)
        val posterWidth = defaultPosterWidth(style)

        val fetched = runCatching {
            when (source) {
                LoginPosterSource.MARVEL -> getMarvelPosters(
                    targetCount = targetCount,
                    minMarvelThreshold = minThreshold,
                    posterWidth = posterWidth,
                )
                LoginPosterSource.TRENDING -> getTrendingPostersForLogin(
                    posterWidth = posterWidth,
                ).take(targetCount).shuffled()
            }
        }.getOrDefault(emptyList())

        if (fetched.isNotEmpty()) {
            loginPosterCache.savePosters(
                source = source,
                style = style,
                urls = fetched,
                context = context,
            )
        }
        return fetched
    }

    suspend fun getMediaDetails(
        mediaId: Long,
        mediaType: MediaType,
        includeImagesAndProviders: Boolean = true,
        preferredSeasonNumber: Int? = null,
    ): Result<MovieDetails> =
        when (mediaType) {
            MediaType.MOVIE -> getMovieDetails(mediaId, includeImagesAndProviders)
            MediaType.TV -> getTvShowDetails(
                tvId = mediaId,
                includeImagesAndProviders = includeImagesAndProviders,
                preferredSeasonNumber = preferredSeasonNumber,
            )
        }

    suspend fun getMovieDetails(
        movieId: Long,
        includeImagesAndProviders: Boolean = true,
    ): Result<MovieDetails> =
        runTmdbCallWithFallback { credential, mode ->
            coroutineScope {
                val detailDeferred = async {
                    when (mode) {
                        AuthMode.API_KEY -> apiService.getMovieDetails(movieId = movieId, apiKey = credential)
                        AuthMode.BEARER_TOKEN -> apiService.getMovieDetails(movieId = movieId, bearerAuth = "Bearer $credential")
                    }
                }
                val imagesDeferred = if (includeImagesAndProviders) {
                    async {
                        runCatching {
                            when (mode) {
                                AuthMode.API_KEY -> apiService.getMovieImages(movieId = movieId, apiKey = credential)
                                AuthMode.BEARER_TOKEN -> apiService.getMovieImages(movieId = movieId, bearerAuth = "Bearer $credential")
                            }.toMediaImages()
                        }.getOrDefault(MediaImages())
                    }
                } else null

                val providersDeferred = if (includeImagesAndProviders) {
                    async {
                        runCatching {
                            when (mode) {
                                AuthMode.API_KEY -> apiService.getMovieWatchProviders(movieId = movieId, apiKey = credential)
                                AuthMode.BEARER_TOKEN -> apiService.getMovieWatchProviders(movieId = movieId, bearerAuth = "Bearer $credential")
                            }.toIndiaProviders()
                        }.getOrNull()
                    }
                } else null

                val trailerDeferred = if (includeImagesAndProviders) {
                    async {
                        runCatching {
                            when (mode) {
                                AuthMode.API_KEY -> apiService.getMovieVideos(movieId = movieId, apiKey = credential)
                                AuthMode.BEARER_TOKEN -> apiService.getMovieVideos(movieId = movieId, bearerAuth = "Bearer $credential")
                            }.toBestYoutubeTrailer()
                        }.getOrNull()
                    }
                } else null

                detailDeferred.await().toDetails(
                    images = imagesDeferred?.await() ?: MediaImages(),
                    watchProvidersIN = providersDeferred?.await(),
                    trailerVideo = trailerDeferred?.await(),
                )
            }
        }

    suspend fun getTvDetails(tvId: Long): Result<MovieDetails> {
        tvFullDetailsCache[tvId]?.let { return Result.success(it) }
        tvBaseDetailsCache[tvId]?.let { return Result.success(it) }
        return getTvShowDetails(tvId = tvId, includeImagesAndProviders = false)
    }

    suspend fun getTvShowDetails(
        tvId: Long,
        includeImagesAndProviders: Boolean = true,
        preferredSeasonNumber: Int? = null,
    ): Result<MovieDetails> {
        if (preferredSeasonNumber == null) {
            if (includeImagesAndProviders) {
                tvFullDetailsCache[tvId]?.let { return Result.success(it) }
            } else {
                (tvFullDetailsCache[tvId] ?: tvBaseDetailsCache[tvId])?.let { return Result.success(it) }
            }
        }

        return runTmdbCallWithFallback { credential, mode ->
            coroutineScope {
                val detailDeferred = async {
                    when (mode) {
                        AuthMode.API_KEY -> apiService.getTvShowDetails(tvId = tvId, apiKey = credential)
                        AuthMode.BEARER_TOKEN -> apiService.getTvShowDetails(tvId = tvId, bearerAuth = "Bearer $credential")
                    }
                }
                val imagesDeferred = if (includeImagesAndProviders) {
                    async {
                        runCatching {
                            when (mode) {
                                AuthMode.API_KEY -> apiService.getTvImages(tvId = tvId, apiKey = credential)
                                AuthMode.BEARER_TOKEN -> apiService.getTvImages(tvId = tvId, bearerAuth = "Bearer $credential")
                            }.toMediaImages()
                        }.getOrDefault(MediaImages())
                    }
                } else null

                val providersDeferred = if (includeImagesAndProviders) {
                    async {
                        runCatching {
                            when (mode) {
                                AuthMode.API_KEY -> apiService.getTvWatchProviders(tvId = tvId, apiKey = credential)
                                AuthMode.BEARER_TOKEN -> apiService.getTvWatchProviders(tvId = tvId, bearerAuth = "Bearer $credential")
                            }.toIndiaProviders()
                        }.getOrNull()
                    }
                } else null

                val seriesTrailerDeferred = if (includeImagesAndProviders) {
                    async {
                        runCatching {
                            when (mode) {
                                AuthMode.API_KEY -> apiService.getTvVideos(tvId = tvId, apiKey = credential)
                                AuthMode.BEARER_TOKEN -> apiService.getTvVideos(tvId = tvId, bearerAuth = "Bearer $credential")
                            }.toBestYoutubeTrailer()
                        }.getOrNull()
                    }
                } else null

                val rawDto = detailDeferred.await()
                val targetSeasonNumber = if (includeImagesAndProviders) {
                    preferredSeasonNumber?.takeIf { it > 0 }
                        ?: rawDto.seasons
                            .filter { it.seasonNumber > 0 && (it.episodeCount ?: 0) > 0 }
                            .minByOrNull { it.seasonNumber }
                            ?.seasonNumber
                } else null

                val seasonTrailer = if (targetSeasonNumber != null) {
                    runCatching {
                        when (mode) {
                            AuthMode.API_KEY -> apiService.getTvSeasonVideos(
                                tvId = tvId,
                                seasonNumber = targetSeasonNumber,
                                apiKey = credential,
                            )
                            AuthMode.BEARER_TOKEN -> apiService.getTvSeasonVideos(
                                tvId = tvId,
                                seasonNumber = targetSeasonNumber,
                                bearerAuth = "Bearer $credential",
                            )
                        }.toBestYoutubeTrailer()
                    }.getOrNull()
                } else null

                val chosenTrailer = seasonTrailer ?: seriesTrailerDeferred?.await()

                val built = rawDto.toDetails(
                    images = imagesDeferred?.await() ?: MediaImages(),
                    watchProvidersIN = providersDeferred?.await(),
                    trailerVideo = chosenTrailer,
                )
                tvBaseDetailsCache[tvId] = built
                if (includeImagesAndProviders) {
                    tvFullDetailsCache[tvId] = built
                }
                built
            }
        }
    }

    suspend fun getSeasonEpisodes(
        tvId: Long,
        seasonNumber: Int,
    ): Result<TvSeasonDetail> {
        val cacheKey = "$tvId:$seasonNumber"
        seasonEpisodesCache[cacheKey]?.let { return Result.success(it) }
        return runTmdbCallWithFallback { credential, mode ->
            val dto = when (mode) {
                AuthMode.API_KEY -> apiService.getTvSeasonDetails(
                    tvId = tvId,
                    seasonNumber = seasonNumber,
                    apiKey = credential,
                )
                AuthMode.BEARER_TOKEN -> apiService.getTvSeasonDetails(
                    tvId = tvId,
                    seasonNumber = seasonNumber,
                    bearerAuth = "Bearer $credential",
                )
            }
            val model = dto.toModel(tvId)
            seasonEpisodesCache[cacheKey] = model
            model
        }
    }

    fun getCachedSeasonEpisodes(tvId: Long, seasonNumber: Int): TvSeasonDetail? =
        seasonEpisodesCache["$tvId:$seasonNumber"]

    suspend fun getRecommendations(
        mediaId: Long,
        mediaType: MediaType,
    ): Result<List<MovieSummary>> =
        runTmdbCallWithFallback { credential, mode ->
            when (mediaType) {
                MediaType.MOVIE -> when (mode) {
                    AuthMode.API_KEY -> apiService.getMovieRecommendations(movieId = mediaId, apiKey = credential)
                    AuthMode.BEARER_TOKEN -> apiService.getMovieRecommendations(movieId = mediaId, bearerAuth = "Bearer $credential")
                }.results.map { it.toSummary() }
                MediaType.TV -> when (mode) {
                    AuthMode.API_KEY -> apiService.getTvRecommendations(tvId = mediaId, apiKey = credential)
                    AuthMode.BEARER_TOKEN -> apiService.getTvRecommendations(tvId = mediaId, bearerAuth = "Bearer $credential")
                }.results.map { it.toSummary() }
            }
        }

    suspend fun getIndiaWatchProviders(mediaId: Long, mediaType: MediaType): Result<WatchProvidersRegion?> =
        runTmdbCallWithFallback { credential, mode ->
            when (mediaType) {
                MediaType.MOVIE -> when (mode) {
                    AuthMode.API_KEY -> apiService.getMovieWatchProviders(movieId = mediaId, apiKey = credential)
                    AuthMode.BEARER_TOKEN -> apiService.getMovieWatchProviders(movieId = mediaId, bearerAuth = "Bearer $credential")
                }
                MediaType.TV -> when (mode) {
                    AuthMode.API_KEY -> apiService.getTvWatchProviders(tvId = mediaId, apiKey = credential)
                    AuthMode.BEARER_TOKEN -> apiService.getTvWatchProviders(tvId = mediaId, bearerAuth = "Bearer $credential")
                }
            }.toIndiaProviders()
        }

    /**
     * Lightweight reachability check for `api.themoviedb.org` using the shared DoH [OkHttpClient].
     */
    suspend fun checkTmdbApiConnection(): Result<Unit> = withContext(ioDispatcher) {
        val credentialPair = resolveCredential()
        try {
            if (credentialPair == null) {
                val request = Request.Builder()
                    .url("${BASE_URL}configuration")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    // Even without a key, reaching TMDB cleanly (401/200) distinguishes missing key from DNS failure
                    if (response.isSuccessful) {
                        return@withContext Result.success(Unit)
                    }
                    val ex = TmdbApiKeyMissingException()
                    Log.e(TAG, "TMDB API check failed: ${ex::class.java.name}: ${ex.message}", ex)
                    return@withContext Result.failure(ex)
                }
            }

            val (credential, primaryMode) = credentialPair
            val primaryRequest = buildConfigProbeRequest(credential, primaryMode)
            httpClient.newCall(primaryRequest).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext Result.success(Unit)
                }
                if (response.code == 401) {
                    val fallbackMode = if (primaryMode == AuthMode.API_KEY) {
                        AuthMode.BEARER_TOKEN
                    } else {
                        AuthMode.API_KEY
                    }
                    val fallbackRequest = buildConfigProbeRequest(credential, fallbackMode)
                    httpClient.newCall(fallbackRequest).execute().use { fallbackResponse ->
                        if (fallbackResponse.isSuccessful) {
                            return@withContext Result.success(Unit)
                        }
                        val ex = TmdbHttpStatusException(
                            statusCode = fallbackResponse.code,
                            message = "HTTP ${fallbackResponse.code} ${fallbackResponse.message}".trim(),
                        )
                        Log.e(TAG, "TMDB API check failed: ${ex::class.java.name}: ${ex.message}", ex)
                        return@withContext Result.failure(ex)
                    }
                }
                val ex = TmdbHttpStatusException(
                    statusCode = response.code,
                    message = "HTTP ${response.code} ${response.message}".trim(),
                )
                Log.e(TAG, "TMDB API check failed: ${ex::class.java.name}: ${ex.message}", ex)
                Result.failure(ex)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "TMDB API check failed: ${e::class.java.name}: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Lightweight reachability check for `image.tmdb.org` using the shared DoH [OkHttpClient].
     */
    suspend fun checkTmdbImageConnection(): Result<Unit> = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(TMDB_IMAGE_PROBE_URL)
            .head()
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                // Any non-5xx response from image.tmdb.org proves DoH DNS + TLS + edge reachability
                if (response.isSuccessful || response.code in 300..499) {
                    Result.success(Unit)
                } else {
                    val ex = TmdbHttpStatusException(
                        statusCode = response.code,
                        message = "HTTP ${response.code} ${response.message}".trim(),
                    )
                    Log.e(TAG, "TMDB image host check failed: ${ex::class.java.name}: ${ex.message}", ex)
                    Result.failure(ex)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "TMDB image host check failed: ${e::class.java.name}: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun buildConfigProbeRequest(credential: String, mode: AuthMode): Request {
        val builder = Request.Builder()
        return when (mode) {
            AuthMode.API_KEY -> builder
                .url("${BASE_URL}configuration?api_key=$credential")
                .get()
                .build()
            AuthMode.BEARER_TOKEN -> builder
                .url("${BASE_URL}configuration")
                .header("Authorization", "Bearer $credential")
                .get()
                .build()
        }
    }

    private fun isUnknownHost(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { it is UnknownHostException }

    private suspend fun <T> runTmdbCallWithFallback(
        block: suspend (credential: String, mode: AuthMode) -> T,
    ): Result<T> {
        val (credential, primaryMode) = resolveCredential()
            ?: return Result.failure(TmdbApiKeyMissingException())

        val timedResult: Result<T>? = withTimeoutOrNull(overallTimeoutMs) {
            val totalAttempts = retryDelaysMs.size + 1
            var lastError: Throwable? = null

            for (attempt in 0 until totalAttempts) {
                try {
                    return@withTimeoutOrNull Result.success(block(credential, primaryMode))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: HttpException) {
                    if (e.code() == 401) {
                        val fallbackMode = if (primaryMode == AuthMode.API_KEY) {
                            AuthMode.BEARER_TOKEN
                        } else {
                            AuthMode.API_KEY
                        }
                        try {
                            return@withTimeoutOrNull Result.success(block(credential, fallbackMode))
                        } catch (fallbackEx: CancellationException) {
                            throw fallbackEx
                        } catch (fallbackEx: HttpException) {
                            Log.e(
                                TAG,
                                "TMDB fallback HTTP error (${fallbackEx.code()}): ${fallbackEx::class.java.name}: ${fallbackEx.message}",
                                fallbackEx,
                            )
                            return@withTimeoutOrNull Result.failure(Exception(formatHttpError(fallbackEx), fallbackEx))
                        } catch (fallbackEx: IOException) {
                            Log.e(
                                TAG,
                                "TMDB fallback IO error (attempt ${attempt + 1}/$totalAttempts): ${fallbackEx::class.java.name}: ${fallbackEx.message}",
                                fallbackEx,
                            )
                            lastError = fallbackEx
                            if (!isUnknownHost(fallbackEx) && attempt < retryDelaysMs.size) {
                                delayProvider(retryDelaysMs[attempt])
                                continue
                            }
                            return@withTimeoutOrNull Result.failure(
                                Exception(
                                    "Could not reach TMDB (${fallbackEx::class.java.simpleName}): ${fallbackEx.message ?: "Network error"}",
                                    fallbackEx,
                                )
                            )
                        } catch (fallbackEx: Exception) {
                            Log.e(
                                TAG,
                                "TMDB fallback error: ${fallbackEx::class.java.name}: ${fallbackEx.message}",
                                fallbackEx,
                            )
                            return@withTimeoutOrNull Result.failure(Exception(fallbackEx.localizedMessage ?: "Failed to reach TMDB.", fallbackEx))
                        }
                    }

                    val isRetryableHttp = e.code() == 429 || e.code() >= 500
                    Log.e(
                        TAG,
                        "TMDB HTTP error ${e.code()} (attempt ${attempt + 1}/$totalAttempts): ${e::class.java.name}: ${e.message}",
                        e,
                    )
                    lastError = e
                    if (isRetryableHttp && attempt < retryDelaysMs.size) {
                        delayProvider(retryDelaysMs[attempt])
                        continue
                    }
                    return@withTimeoutOrNull Result.failure(Exception(formatHttpError(e), e))
                } catch (e: IOException) {
                    Log.e(
                        TAG,
                        "TMDB network error (attempt ${attempt + 1}/$totalAttempts): ${e::class.java.name}: ${e.message}",
                        e,
                    )
                    lastError = e
                    // Fail immediately on UnknownHostException (DoH + system DNS already exhausted)
                    if (!isUnknownHost(e) && attempt < retryDelaysMs.size) {
                        delayProvider(retryDelaysMs[attempt])
                        continue
                    }
                    return@withTimeoutOrNull Result.failure(
                        Exception(
                            "Could not reach TMDB (${e::class.java.simpleName}): ${e.message ?: "Network error"}",
                            e,
                        )
                    )
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "TMDB unexpected error: ${e::class.java.name}: ${e.message}",
                        e,
                    )
                    return@withTimeoutOrNull Result.failure(Exception(e.localizedMessage ?: "Failed to reach TMDB.", e))
                }
            }

            val finalCause = lastError
            Result.failure(
                Exception(
                    finalCause?.let { "${it::class.java.simpleName}: ${it.message ?: "Network error"}" }
                        ?: "Failed to reach TMDB.",
                    finalCause,
                )
            )
        }

        if (timedResult != null) {
            return timedResult
        }

        val timeoutException = SocketTimeoutException("TMDB request timed out after ${overallTimeoutMs}ms")
        Log.e(TAG, "TMDB overall timeout: ${timeoutException::class.java.name}: ${timeoutException.message}", timeoutException)
        return Result.failure(
            Exception(
                "Could not reach TMDB (${timeoutException::class.java.simpleName}): ${timeoutException.message}",
                timeoutException,
            )
        )
    }

    private fun formatHttpError(e: HttpException): String {
        val serverReason = extractTmdbStatusMessage(e)
        return when (e.code()) {
            401 -> buildString {
                append("TMDB rejected your TMDB_API_KEY (HTTP 401)")
                if (!serverReason.isNullOrBlank()) {
                    append(": ")
                    append(serverReason)
                } else {
                    append(".")
                }
                append(" Open AI Studio → Secrets, verify TMDB_API_KEY has your active TMDB v3 API Key or Read Access Token (with no extra spaces or quotes), then rebuild.")
            }
            404 -> "Title not found on TMDB."
            429 -> "TMDB rate limit reached. Please wait a moment and try again."
            else -> "TMDB API error (${e.code()})${serverReason?.let { ": $it" } ?: "."}"
        }
    }

    private fun extractTmdbStatusMessage(e: HttpException): String? {
        return try {
            val rawBody = e.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
            JSONObject(rawBody).optString("status_message").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "TmdbRepository"
        private const val BASE_URL = "https://api.themoviedb.org/3/"
        private const val PLACEHOLDER_KEY = "MY_TMDB_API_KEY"
        private const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
        private const val TMDB_IMAGE_PROBE_URL = "https://image.tmdb.org/t/p/w92/wwemzKWzjKYJFfCeiB57q3r4Bcm.png"
        private const val POSTER_SIZE_SLIDESHOW = "w780"
        private const val POSTER_SIZE_WALL = "w342"
        private const val MARVEL_STUDIOS_COMPANY_ID = "420"
        private const val MARVEL_POSTER_PAGES = 3
        private const val SLIDESHOW_POSTER_TARGET_COUNT = 18
        private const val WALL_POSTER_TARGET_COUNT = 30
        private const val SLIDESHOW_MIN_MARVEL_THRESHOLD = 6
        private const val WALL_MIN_MARVEL_THRESHOLD = 9
        internal val DEFAULT_RETRY_DELAYS_MS: List<Long> = listOf(250L, 500L)
        internal const val DEFAULT_OVERALL_TIMEOUT_MS: Long = 10_000L

        internal fun defaultPosterCount(style: LoginBackgroundStyle): Int = when (style) {
            LoginBackgroundStyle.SLIDESHOW -> SLIDESHOW_POSTER_TARGET_COUNT
            LoginBackgroundStyle.POSTER_WALL -> WALL_POSTER_TARGET_COUNT
        }

        internal fun defaultMinMarvelThreshold(style: LoginBackgroundStyle): Int = when (style) {
            LoginBackgroundStyle.SLIDESHOW -> SLIDESHOW_MIN_MARVEL_THRESHOLD
            LoginBackgroundStyle.POSTER_WALL -> WALL_MIN_MARVEL_THRESHOLD
        }

        internal fun defaultPosterWidth(style: LoginBackgroundStyle): String = when (style) {
            LoginBackgroundStyle.SLIDESHOW -> POSTER_SIZE_SLIDESHOW
            LoginBackgroundStyle.POSTER_WALL -> POSTER_SIZE_WALL
        }

        fun defaultApiService(): TmdbApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = TmdbNetworkClient.sharedOkHttpClient.newBuilder()
                .addInterceptor(logging)
                .connectTimeout(TmdbNetworkClient.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TmdbNetworkClient.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(TmdbNetworkClient.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(TmdbApiService::class.java)
        }
    }
}
