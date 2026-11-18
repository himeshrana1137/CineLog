package com.example.data.remote

import com.example.data.model.CastMember
import com.example.data.model.MediaImages
import com.example.data.model.MediaType
import com.example.data.model.MediaVideo
import com.example.data.model.MovieDetails
import com.example.data.model.MovieSummary
import com.example.data.model.TmdbImageItem
import com.example.data.model.TvEpisode
import com.example.data.model.TvSeasonDetail
import com.example.data.model.TvSeasonSummary
import com.example.data.model.WatchProviderInfo
import com.example.data.model.WatchProvidersRegion
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlin.math.roundToInt

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
    val popularity: Double = 0.0,
    @Json(name = "original_language") val originalLanguage: String? = null,
) {
    fun toSummary(): MovieSummary = MovieSummary(
        movieId = id,
        mediaType = MediaType.MOVIE,
        title = title.ifBlank { "Untitled" },
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate.orEmpty(),
        rating = voteAverage,
        overview = overview.orEmpty(),
        popularity = popularity,
        originalLanguage = originalLanguage.orEmpty().lowercase(),
    )
}

@JsonClass(generateAdapter = true)
data class TmdbTvSearchResponse(
    val page: Int = 1,
    val results: List<TmdbTvDto> = emptyList(),
    @Json(name = "total_results") val totalResults: Int = 0,
)

@JsonClass(generateAdapter = true)
data class TmdbTvDto(
    val id: Long,
    val name: String = "",
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double = 0.0,
    val overview: String? = null,
    val popularity: Double = 0.0,
    @Json(name = "original_language") val originalLanguage: String? = null,
) {
    fun toSummary(): MovieSummary = MovieSummary(
        movieId = id,
        mediaType = MediaType.TV,
        title = name.ifBlank { "Untitled Series" },
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = firstAirDate.orEmpty(),
        rating = voteAverage,
        overview = overview.orEmpty(),
        popularity = popularity,
        originalLanguage = originalLanguage.orEmpty().lowercase(),
    )
}

@JsonClass(generateAdapter = true)
data class TmdbTrendingAllResponse(
    val page: Int = 1,
    val results: List<TmdbTrendingAllItemDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TmdbTrendingAllItemDto(
    val id: Long,
    @Json(name = "media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double = 0.0,
    val overview: String? = null,
    val popularity: Double = 0.0,
    @Json(name = "original_language") val originalLanguage: String? = null,
) {
    fun toSummaryOrNull(): MovieSummary? {
        val resolvedType = when (mediaType?.lowercase()) {
            "movie" -> MediaType.MOVIE
            "tv" -> MediaType.TV
            else -> return null
        }
        val resolvedTitle = if (resolvedType == MediaType.TV) name else title
        val resolvedDate = if (resolvedType == MediaType.TV) firstAirDate else releaseDate
        return MovieSummary(
            movieId = id,
            mediaType = resolvedType,
            title = resolvedTitle?.takeIf { it.isNotBlank() } ?: "Untitled",
            posterPath = posterPath,
            backdropPath = backdropPath,
            releaseDate = resolvedDate.orEmpty(),
            rating = voteAverage,
            overview = overview.orEmpty(),
            popularity = popularity,
            originalLanguage = originalLanguage.orEmpty().lowercase(),
        )
    }
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
    @Json(name = "original_language") val originalLanguage: String? = null,
    val genres: List<TmdbGenreDto> = emptyList(),
    val credits: TmdbCreditsDto? = null,
) {
    fun toDetails(
        images: MediaImages = MediaImages(),
        watchProvidersIN: WatchProvidersRegion? = null,
        trailerVideo: MediaVideo? = null,
    ): MovieDetails {
        val directors = credits?.crew.orEmpty()
            .filter { it.job.equals("Director", ignoreCase = true) }
            .map { it.name.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)

        return MovieDetails(
            movieId = id,
            mediaType = MediaType.MOVIE,
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
                .take(10)
                .map {
                    CastMember(
                        id = it.id,
                        name = it.name,
                        character = it.character.orEmpty(),
                        profilePath = it.profilePath,
                    )
                },
            directors = directors,
            originalLanguage = originalLanguage.orEmpty().lowercase(),
            tagline = tagline.orEmpty(),
            images = images,
            watchProvidersIN = watchProvidersIN,
            trailerVideo = trailerVideo,
        )
    }
}

@JsonClass(generateAdapter = true)
data class TmdbTvDetailDto(
    val id: Long,
    val name: String = "",
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double = 0.0,
    val overview: String? = null,
    @Json(name = "episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val tagline: String? = null,
    @Json(name = "original_language") val originalLanguage: String? = null,
    val genres: List<TmdbGenreDto> = emptyList(),
    @Json(name = "number_of_seasons") val numberOfSeasons: Int? = null,
    @Json(name = "number_of_episodes") val numberOfEpisodes: Int? = null,
    @Json(name = "created_by") val createdBy: List<TmdbCreatorDto> = emptyList(),
    val seasons: List<TmdbSeasonSummaryDto> = emptyList(),
    val credits: TmdbCreditsDto? = null,
) {
    fun toDetails(
        images: MediaImages = MediaImages(),
        watchProvidersIN: WatchProvidersRegion? = null,
        trailerVideo: MediaVideo? = null,
    ): MovieDetails {
        val directorsFromCrew = credits?.crew.orEmpty()
            .filter {
                it.job.equals("Director", ignoreCase = true) ||
                    it.job.equals("Series Director", ignoreCase = true)
            }
            .map { it.name.trim() }
        val creators = createdBy.map { it.name.trim() }
        val combinedDirectors = (creators + directorsFromCrew)
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)

        val validEpisodeRuntimes = episodeRunTime.filter { it > 0 }
        val averageEpisodeRuntime = if (validEpisodeRuntimes.isNotEmpty()) {
            validEpisodeRuntimes.average().roundToInt()
        } else {
            0
        }

        val mappedSeasons = seasons
            .map { it.toModel() }
            .sortedBy { it.seasonNumber }

        return MovieDetails(
            movieId = id,
            mediaType = MediaType.TV,
            title = name.ifBlank { "Untitled Series" },
            posterPath = posterPath,
            backdropPath = backdropPath,
            releaseDate = firstAirDate.orEmpty(),
            rating = voteAverage,
            overview = overview.orEmpty(),
            runtime = averageEpisodeRuntime,
            genres = genres.map { it.name }.filter { it.isNotBlank() }.take(10),
            cast = credits?.cast.orEmpty()
                .sortedBy { it.order ?: Int.MAX_VALUE }
                .take(10)
                .map {
                    CastMember(
                        id = it.id,
                        name = it.name,
                        character = it.character.orEmpty(),
                        profilePath = it.profilePath,
                    )
                },
            directors = combinedDirectors,
            originalLanguage = originalLanguage.orEmpty().lowercase(),
            tagline = tagline.orEmpty(),
            numberOfSeasons = numberOfSeasons ?: mappedSeasons.count { !it.isSpecial },
            numberOfEpisodes = numberOfEpisodes ?: mappedSeasons.filter { !it.isSpecial }.sumOf { it.episodeCount },
            seasons = mappedSeasons,
            images = images,
            watchProvidersIN = watchProvidersIN,
            trailerVideo = trailerVideo,
        )
    }
}

@JsonClass(generateAdapter = true)
data class TmdbSeasonSummaryDto(
    val id: Long = 0L,
    @Json(name = "season_number") val seasonNumber: Int = 0,
    val name: String? = null,
    @Json(name = "episode_count") val episodeCount: Int? = null,
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
) {
    fun toModel(): TvSeasonSummary = TvSeasonSummary(
        seasonNumber = seasonNumber,
        name = name.orEmpty().ifBlank { if (seasonNumber == 0) "Specials" else "Season $seasonNumber" },
        episodeCount = (episodeCount ?: 0).coerceAtLeast(0),
        airDate = airDate.orEmpty(),
        posterPath = posterPath,
    )
}

@JsonClass(generateAdapter = true)
data class TmdbSeasonDetailDto(
    val id: Long = 0L,
    @Json(name = "season_number") val seasonNumber: Int = 0,
    val name: String? = null,
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    val episodes: List<TmdbEpisodeDto> = emptyList(),
) {
    fun toModel(tvId: Long): TvSeasonDetail = TvSeasonDetail(
        tvId = tvId,
        seasonNumber = seasonNumber,
        name = name.orEmpty().ifBlank { if (seasonNumber == 0) "Specials" else "Season $seasonNumber" },
        airDate = airDate.orEmpty(),
        posterPath = posterPath,
        episodes = episodes
            .sortedBy { it.episodeNumber }
            .map { it.toModel(fallbackSeason = seasonNumber) },
    )
}

@JsonClass(generateAdapter = true)
data class TmdbEpisodeDto(
    val id: Long = 0L,
    @Json(name = "episode_number") val episodeNumber: Int = 0,
    @Json(name = "season_number") val seasonNumber: Int? = null,
    val name: String? = null,
    val runtime: Int? = null,
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "still_path") val stillPath: String? = null,
    val overview: String? = null,
) {
    fun toModel(fallbackSeason: Int): TvEpisode = TvEpisode(
        episodeNumber = episodeNumber,
        seasonNumber = seasonNumber ?: fallbackSeason,
        name = name.orEmpty().ifBlank { "Episode $episodeNumber" },
        runtime = (runtime ?: 0).coerceAtLeast(0),
        airDate = airDate.orEmpty(),
        stillPath = stillPath,
        overview = overview.orEmpty(),
    )
}

@JsonClass(generateAdapter = true)
data class TmdbGenreDto(
    val id: Int = 0,
    val name: String = "",
)

@JsonClass(generateAdapter = true)
data class TmdbCreatorDto(
    val id: Long = 0L,
    val name: String = "",
)

@JsonClass(generateAdapter = true)
data class TmdbCreditsDto(
    val cast: List<TmdbCastDto> = emptyList(),
    val crew: List<TmdbCrewDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TmdbCastDto(
    val id: Long = 0L,
    val name: String = "",
    val character: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    val order: Int? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbCrewDto(
    val id: Long = 0L,
    val name: String = "",
    val job: String? = null,
    val department: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbImagesResponse(
    val id: Long = 0L,
    val backdrops: List<TmdbImageDto> = emptyList(),
    val posters: List<TmdbImageDto> = emptyList(),
) {
    fun toMediaImages(): MediaImages = MediaImages(
        backdrops = backdrops
            .filter { it.filePath.isNotBlank() }
            .sortedByDescending { it.voteAverage }
            .take(15)
            .map {
                TmdbImageItem(
                    filePath = it.filePath,
                    width = it.width,
                    height = it.height,
                    aspectRatio = it.aspectRatio,
                    isBackdrop = true,
                )
            },
        posters = posters
            .filter { it.filePath.isNotBlank() }
            .sortedByDescending { it.voteAverage }
            .take(15)
            .map {
                TmdbImageItem(
                    filePath = it.filePath,
                    width = it.width,
                    height = it.height,
                    aspectRatio = it.aspectRatio,
                    isBackdrop = false,
                )
            },
    )
}

@JsonClass(generateAdapter = true)
data class TmdbImageDto(
    @Json(name = "file_path") val filePath: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @Json(name = "aspect_ratio") val aspectRatio: Double = 1.0,
    @Json(name = "vote_average") val voteAverage: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class TmdbWatchProvidersResponse(
    val id: Long = 0L,
    val results: Map<String, TmdbWatchProviderRegionDto> = emptyMap(),
) {
    fun toIndiaProviders(): WatchProvidersRegion? {
        val inRegion = results["IN"] ?: return null
        val mapped = WatchProvidersRegion(
            regionCode = "IN",
            link = inRegion.link,
            flatrate = inRegion.flatrate.map { it.toModel() }.sortedBy { it.providerName },
            rent = inRegion.rent.map { it.toModel() }.sortedBy { it.providerName },
            buy = inRegion.buy.map { it.toModel() }.sortedBy { it.providerName },
        )
        return if (mapped.isEmpty) null else mapped
    }
}

@JsonClass(generateAdapter = true)
data class TmdbWatchProviderRegionDto(
    val link: String? = null,
    val flatrate: List<TmdbWatchProviderDto> = emptyList(),
    val rent: List<TmdbWatchProviderDto> = emptyList(),
    val buy: List<TmdbWatchProviderDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TmdbWatchProviderDto(
    @Json(name = "provider_id") val providerId: Int = 0,
    @Json(name = "provider_name") val providerName: String = "",
    @Json(name = "logo_path") val logoPath: String? = null,
    @Json(name = "display_priority") val displayPriority: Int = 999,
) {
    fun toModel(): WatchProviderInfo = WatchProviderInfo(
        providerId = providerId,
        providerName = providerName,
        logoPath = logoPath,
    )
}

@JsonClass(generateAdapter = true)
data class TmdbVideosResponse(
    val id: Long = 0L,
    val results: List<TmdbVideoDto> = emptyList(),
) {
    fun toBestYoutubeTrailer(): MediaVideo? {
        val youtubeVideos = results.filter {
            it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank()
        }
        if (youtubeVideos.isEmpty()) return null
        val best = youtubeVideos.sortedWith(
            compareByDescending<TmdbVideoDto> { it.type.equals("Trailer", ignoreCase = true) && it.official }
                .thenByDescending { it.type.equals("Trailer", ignoreCase = true) }
                .thenByDescending { it.type.equals("Teaser", ignoreCase = true) && it.official }
                .thenByDescending { it.type.equals("Teaser", ignoreCase = true) }
                .thenByDescending { it.official }
                .thenByDescending { it.size }
        ).first()
        return MediaVideo(
            id = best.id,
            key = best.key.trim(),
            name = best.name.ifBlank { "Official Trailer" },
            site = best.site,
            type = best.type,
            official = best.official,
            publishedAt = best.publishedAt.orEmpty(),
        )
    }
}

@JsonClass(generateAdapter = true)
data class TmdbVideoDto(
    val id: String = "",
    val key: String = "",
    val name: String = "",
    val site: String = "",
    val type: String = "",
    val official: Boolean = false,
    val size: Int = 0,
    @Json(name = "published_at") val publishedAt: String? = null,
)

