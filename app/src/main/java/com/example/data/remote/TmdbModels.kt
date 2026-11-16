package com.example.data.remote

import com.example.data.model.CastMember
import com.example.data.model.MovieDetails
import com.example.data.model.MovieSummary
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TmdbSearchResponse(
    val page: Int = 1,
    val results: List<TmdbMovieDto> = emptyList(),
    @Json(name = "total_results") val totalResults: Int = 0,
)

@JsonClass(generateAdapter = true)
data class TmdbMovieDto(
    val id: Long,
    val title: String = "",
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double = 0.0,
    val overview: String? = null,
) {
    fun toSummary(): MovieSummary = MovieSummary(
        movieId = id,
        title = title.ifBlank { "Untitled" },
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate.orEmpty(),
        rating = voteAverage,
        overview = overview.orEmpty(),
    )
}

@JsonClass(generateAdapter = true)
data class TmdbMovieDetailDto(
    val id: Long,
    val title: String = "",
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double = 0.0,
    val overview: String? = null,
    val runtime: Int? = null,
    val tagline: String? = null,
    val genres: List<TmdbGenreDto> = emptyList(),
    val credits: TmdbCreditsDto? = null,
) {
    fun toDetails(): MovieDetails = MovieDetails(
        movieId = id,
        title = title.ifBlank { "Untitled" },
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate.orEmpty(),
        rating = voteAverage,
        overview = overview.orEmpty(),
        runtime = runtime ?: 0,
        genres = genres.map { it.name }.filter { it.isNotBlank() }.take(10),
        cast = credits?.cast.orEmpty()
            .sortedBy { it.order ?: Int.MAX_VALUE }
            .take(12)
            .map {
                CastMember(
                    id = it.id,
                    name = it.name,
                    character = it.character.orEmpty(),
                    profilePath = it.profilePath,
                )
            },
        tagline = tagline.orEmpty(),
    )
}

@JsonClass(generateAdapter = true)
data class TmdbGenreDto(
    val id: Int = 0,
    val name: String = "",
)

@JsonClass(generateAdapter = true)
data class TmdbCreditsDto(
    val cast: List<TmdbCastDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TmdbCastDto(
    val id: Long = 0L,
    val name: String = "",
    val character: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    val order: Int? = null,
)
