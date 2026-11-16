package com.example.data.repository

import com.example.BuildConfig
import com.example.data.model.MediaType
import com.example.data.model.MovieDetails
import com.example.data.model.MovieSummary
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

    suspend fun searchMovies(query: String): Result<List<MovieSummary>> =
        runTmdbCallWithFallback { credential, mode ->
            when (mode) {
                AuthMode.API_KEY -> apiService.searchMovies(
                    query = query.trim(),
                    apiKey = credential,
                )
                AuthMode.BEARER_TOKEN -> apiService.searchMovies(
                    query = query.trim(),
                    bearerAuth = "Bearer $credential",
                )
            }.results.map { it.toSummary() }
        }

    suspend fun searchTvShows(query: String): Result<List<MovieSummary>> =
        runTmdbCallWithFallback { credential, mode ->
            when (mode) {
                AuthMode.API_KEY -> apiService.searchTvShows(
                    query = query.trim(),
                    apiKey = credential,
                )
                AuthMode.BEARER_TOKEN -> apiService.searchTvShows(
                    query = query.trim(),
                    bearerAuth = "Bearer $credential",
                )
            }.results.map { it.toSummary() }
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

    suspend fun getMediaDetails(mediaId: Long, mediaType: MediaType): Result<MovieDetails> =
        when (mediaType) {
            MediaType.MOVIE -> getMovieDetails(mediaId)
            MediaType.TV -> getTvShowDetails(mediaId)
        }

    suspend fun getMovieDetails(movieId: Long): Result<MovieDetails> =
        runTmdbCallWithFallback { credential, mode ->
            when (mode) {
                AuthMode.API_KEY -> apiService.getMovieDetails(
                    movieId = movieId,
                    apiKey = credential,
                )
                AuthMode.BEARER_TOKEN -> apiService.getMovieDetails(
                    movieId = movieId,
                    bearerAuth = "Bearer $credential",
                )
            }.toDetails()
        }

    suspend fun getTvShowDetails(tvId: Long): Result<MovieDetails> =
        runTmdbCallWithFallback { credential, mode ->
            when (mode) {
                AuthMode.API_KEY -> apiService.getTvShowDetails(
                    tvId = tvId,
                    apiKey = credential,
                )
                AuthMode.BEARER_TOKEN -> apiService.getTvShowDetails(
                    tvId = tvId,
                    bearerAuth = "Bearer $credential",
                )
            }.toDetails()
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
