package com.example.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {
    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
    ): TmdbSearchResponse

    @GET("trending/movie/week")
    suspend fun getTrendingMovies(
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
    ): TmdbSearchResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Long,
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("append_to_response") appendToResponse: String = "credits",
        @Query("language") language: String = "en-US",
    ): TmdbMovieDetailDto
}
