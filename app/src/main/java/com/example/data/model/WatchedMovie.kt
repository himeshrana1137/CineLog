package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class WatchedMovie(
    @DocumentId val id: String = "",
    val userId: String = "",
    val movieId: Long = 0L,
    val title: String = "",
    val posterPath: String? = null,
    val releaseDate: String = "",
    val rating: Double = 0.0,
    val userRating: Int = 0,
    val note: String = "",
    val overview: String = "",
    val genres: List<String> = emptyList(),
    val runtime: Int = 0,
    @ServerTimestamp val watchedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val releaseYear: String
        get() = releaseDate.take(4).ifBlank { "—" }

    fun fullPosterUrl(): String? = posterPath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w500$it"
    }
}

data class MovieSummary(
    val movieId: Long,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String,
    val rating: Double,
    val overview: String,
) {
    val releaseYear: String
        get() = releaseDate.take(4).ifBlank { "—" }

    fun fullPosterUrl(): String? = posterPath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w500$it"
    }

    fun fullBackdropUrl(): String? = backdropPath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w780$it"
    }
}

data class CastMember(
    val id: Long,
    val name: String,
    val character: String,
    val profilePath: String?,
) {
    fun fullProfileUrl(): String? = profilePath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w185$it"
    }
}

data class MovieDetails(
    val movieId: Long,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String,
    val rating: Double,
    val overview: String,
    val runtime: Int,
    val genres: List<String>,
    val cast: List<CastMember>,
    val tagline: String = "",
) {
    val releaseYear: String
        get() = releaseDate.take(4).ifBlank { "—" }

    fun fullPosterUrl(): String? = posterPath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w500$it"
    }

    fun fullBackdropUrl(): String? = backdropPath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w780$it"
    }

    fun toSummary(): MovieSummary = MovieSummary(
        movieId = movieId,
        title = title,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate,
        rating = rating,
        overview = overview,
    )
}
