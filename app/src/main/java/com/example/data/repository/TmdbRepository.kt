package com.example.data.repository

import com.example.BuildConfig
import com.example.data.model.MediaImages
import com.example.data.model.MediaType
import com.example.data.model.MovieDetails
import com.example.data.model.MovieSummary
import com.example.data.model.WatchProvidersRegion
import com.example.data.remote.TmdbApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class TmdbApiKeyMissingException : Exception(
    "TMDB API key is not configured. Add TMDB_API_KEY in the AI Studio Secrets panel and rebuild."
)

private enum class AuthMode {
    API_KEY,
    BEARER_TOKEN,
}

class TmdbRepository(
    private val apiService: TmdbApiService = defaultApiService(),
    private val apiKeyProvider: () -> String = { BuildConfig.TMDB_API_KEY },
) {
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

    suspend fun getMediaDetails(
        mediaId: Long,
        mediaType: MediaType,
        includeImagesAndProviders: Boolean = true,
    ): Result<MovieDetails> =
        when (mediaType) {
            MediaType.MOVIE -> getMovieDetails(mediaId, includeImagesAndProviders)
            MediaType.TV -> getTvShowDetails(mediaId, includeImagesAndProviders)
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

    suspend fun getTvShowDetails(
        tvId: Long,
        includeImagesAndProviders: Boolean = true,
    ): Result<MovieDetails> =
        runTmdbCallWithFallback { credential, mode ->
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

                val trailerDeferred = if (includeImagesAndProviders) {
                    async {
                        runCatching {
                            when (mode) {
                                AuthMode.API_KEY -> apiService.getTvVideos(tvId = tvId, apiKey = credential)
                                AuthMode.BEARER_TOKEN -> apiService.getTvVideos(tvId = tvId, bearerAuth = "Bearer $credential")
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

    private suspend fun <T> runTmdbCallWithFallback(
        block: suspend (credential: String, mode: AuthMode) -> T,
    ): Result<T> {
        val (credential, primaryMode) = resolveCredential()
            ?: return Result.failure(TmdbApiKeyMissingException())

        return try {
            Result.success(block(credential, primaryMode))
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
                    return Result.success(block(credential, fallbackMode))
                } catch (fallbackEx: CancellationException) {
                    throw fallbackEx
                } catch (fallbackEx: HttpException) {
                    return Result.failure(Exception(formatHttpError(fallbackEx), fallbackEx))
                } catch (fallbackEx: IOException) {
                    return Result.failure(
                        Exception("No internet connection or TMDB unreachable. Please check your network.", fallbackEx)
                    )
                }
            }
            Result.failure(Exception(formatHttpError(e), e))
        } catch (e: IOException) {
            Result.failure(Exception("No internet connection or TMDB unreachable. Please check your network.", e))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Failed to reach TMDB.", e))
        }
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
        private const val BASE_URL = "https://api.themoviedb.org/3/"
        private const val PLACEHOLDER_KEY = "MY_TMDB_API_KEY"

        fun defaultApiService(): TmdbApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
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
