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
        @Query("year") year: Int? = null,
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

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("with_companies") withCompanies: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String = "en-US",
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
    ): TmdbSearchResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Long,
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("append_to_response") appendToResponse: String = "credits",
        @Query("language") language: String = "en-US",
    ): TmdbMovieDetailDto

    @GET("movie/{movie_id}/images")
    suspend fun getMovieImages(
        @Path("movie_id") movieId: Long,
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("include_image_language") includeImageLanguage: String = "en,null,hi",
    ): TmdbImagesResponse

    @GET("movie/{movie_id}/videos")
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Long,
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("language") language: String = "en-US",
        @Query("include_video_language") includeVideoLanguage: String = "en,null",
    ): TmdbVideosResponse

    @GET("movie/{movie_id}/recommendations")
    suspend fun getMovieRecommendations(
        @Path("movie_id") movieId: Long,
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
    ): TmdbSearchResponse

    @GET("movie/{movie_id}/watch/providers")
    suspend fun getMovieWatchProviders(
        @Path("movie_id") movieId: Long,
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
    ): TmdbWatchProvidersResponse

    @GET("search/tv")
    suspend fun searchTvShows(
        @Query("query") query: String,
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("first_air_date_year") firstAirDateYear: Int? = null,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
    ): TmdbTvSearchResponse

    @GET("trending/tv/week")
    suspend fun getTrendingTvShows(
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
    ): TmdbTvSearchResponse

    @GET("tv/{tv_id}")
    suspend fun getTvShowDetails(
        @Path("tv_id") tvId: Long,
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("append_to_response") appendToResponse: String = "credits",
        @Query("language") language: String = "en-US",
    ): TmdbTvDetailDto

    @GET("tv/{tv_id}/images")
    suspend fun getTvImages(
        @Path("tv_id") tvId: Long,
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("include_image_language") includeImageLanguage: String = "en,null,hi",
    ): TmdbImagesResponse

    @GET("tv/{tv_id}/videos")
    suspend fun getTvVideos(
        @Path("tv_id") tvId: Long,
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("language") language: String = "en-US",
        @Query("include_video_language") includeVideoLanguage: String = "en,null",
    ): TmdbVideosResponse

    @GET("tv/{tv_id}/season/{season_number}/videos")
    suspend fun getTvSeasonVideos(
        @Path("tv_id") tvId: Long,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("language") language: String = "en-US",
        @Query("include_video_language") includeVideoLanguage: String = "en,null",
    ): TmdbVideosResponse

    @GET("trending/all/week")
    suspend fun getTrendingAllWeek(
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
    ): TmdbTrendingAllResponse

    @GET("tv/{tv_id}/recommendations")
    suspend fun getTvRecommendations(
        @Path("tv_id") tvId: Long,
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
    ): TmdbTvSearchResponse

    @GET("tv/{tv_id}/watch/providers")
    suspend fun getTvWatchProviders(
        @Path("tv_id") tvId: Long,
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
    ): TmdbWatchProvidersResponse

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeasonDetails(
        @Path("tv_id") tvId: Long,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String? = null,
        @Header("Authorization") bearerAuth: String? = null,
        @Query("language") language: String = "en-US",
    ): TmdbSeasonDetailDto
}
