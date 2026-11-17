package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MediaType(val value: String, val label: String) {
    MOVIE("movie", "Movie"),
    TV("tv", "TV Series");

    companion object {
        fun fromValue(value: String?): MediaType =
            if (value == TV.value) TV else MOVIE
    }
}

enum class QuickReaction(
    val key: String,
    val emoji: String,
    val label: String,
) {
    MIND_BLOWN("mind_blown", "🤯", "Mind-blown"),
    EMOTIONAL("emotional", "😭", "Emotional"),
    MASTERPIECE("masterpiece", "🤩", "Masterpiece"),
    HILARIOUS("hilarious", "😂", "Hilarious"),
    BORING("boring", "😴", "Boring");

    companion object {
        fun fromKey(key: String?): QuickReaction? =
            entries.firstOrNull { it.key == key }
    }
}

enum class AccentPreset(
    val key: String,
    val label: String,
    val defaultHex: String,
) {
    RED("red", "Cinema Red", "#E50914"),
    YELLOW("yellow", "Gold Yellow", "#F5C518"),
    BLUE("blue", "Electric Blue", "#3B82F6"),
    PURPLE("purple", "Royal Purple", "#A855F7"),
    NEON_GREEN("neon_green", "Neon Green", "#22C55E"),
    CUSTOM("custom", "Custom Hex", "#E50914");

    companion object {
        fun fromKey(key: String?): AccentPreset =
            entries.firstOrNull { it.key == key } ?: RED
    }
}

fun firestoreWatchedDocId(mediaId: Long, mediaType: MediaType): String =
    if (mediaType == MediaType.TV) "tv_$mediaId" else mediaId.toString()

fun todayIsoDate(): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return fmt.format(Date())
}

@IgnoreExtraProperties
data class WatchedMovie(
    @DocumentId val id: String = "",
    val userId: String = "",
    val movieId: Long = 0L,
    val mediaType: String = MediaType.MOVIE.value,
    val title: String = "",
    val posterPath: String? = null,
    val releaseDate: String = "",
    val rating: Double = 0.0,
    val userRating: Int = 0,
    val note: String = "",
    val reaction: String = "",
    val originalLanguage: String = "",
    val overview: String = "",
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val runtime: Int = 0,
    val numberOfSeasons: Int = 0,
    val numberOfEpisodes: Int = 0,
    val watchedSeason: Int = 0,
    val watchLog: List<String> = emptyList(),
    @ServerTimestamp val watchedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val resolvedMediaType: MediaType
        get() = MediaType.fromValue(mediaType)

    val isTv: Boolean
        get() = resolvedMediaType == MediaType.TV

    val uniqueKey: String
        get() = "${resolvedMediaType.value}_$movieId"

    val releaseYear: String
        get() = releaseDate.take(4).ifBlank { "—" }

    val quickReaction: QuickReaction?
        get() = QuickReaction.fromKey(reaction)

    val languageName: String
        get() = LanguageUtils.displayName(originalLanguage)

    val resolvedWatchDates: List<String>
        get() {
            val cleanLog = watchLog.map { it.trim().take(10) }.filter { it.matches(ISO_DATE_REGEX) }
            if (cleanLog.isNotEmpty()) return cleanLog
            val fallback = watchedAt?.toDate()?.let { date ->
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
            }
            return listOfNotNull(fallback)
        }

    val watchCount: Int
        get() = resolvedWatchDates.size.coerceAtLeast(1)

    val needsMetadataBackfill: Boolean
        get() = runtime == 0 ||
            originalLanguage.isBlank() ||
            genres.isEmpty() ||
            (cast.isEmpty() && directors.isEmpty())

    fun fullPosterUrl(): String? = posterPath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w500$it"
    }

    fun toSummary(): MovieSummary = MovieSummary(
        movieId = movieId,
        mediaType = resolvedMediaType,
        title = title,
        posterPath = posterPath,
        backdropPath = null,
        releaseDate = releaseDate,
        rating = rating,
        overview = overview,
        originalLanguage = originalLanguage,
    )

    companion object {
        private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    }
}

@IgnoreExtraProperties
data class WatchlistItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val movieId: Long = 0L,
    val mediaType: String = MediaType.MOVIE.value,
    val title: String = "",
    val posterPath: String? = null,
    val releaseDate: String = "",
    val rating: Double = 0.0,
    val overview: String = "",
    val originalLanguage: String = "",
    val genres: List<String> = emptyList(),
    val runtime: Int = 0,
    val numberOfSeasons: Int = 0,
    val numberOfEpisodes: Int = 0,
    @ServerTimestamp val addedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val resolvedMediaType: MediaType
        get() = MediaType.fromValue(mediaType)

    val isTv: Boolean
        get() = resolvedMediaType == MediaType.TV

    val uniqueKey: String
        get() = "${resolvedMediaType.value}_$movieId"

    val releaseYear: String
        get() = releaseDate.take(4).ifBlank { "—" }

    val languageName: String
        get() = LanguageUtils.displayName(originalLanguage)

    fun fullPosterUrl(): String? = posterPath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w500$it"
    }

    fun toSummary(): MovieSummary = MovieSummary(
        movieId = movieId,
        mediaType = resolvedMediaType,
        title = title,
        posterPath = posterPath,
        backdropPath = null,
        releaseDate = releaseDate,
        rating = rating,
        overview = overview,
        originalLanguage = originalLanguage,
    )
}

@IgnoreExtraProperties
data class CustomListEntry(
    val movieId: Long = 0L,
    val mediaType: String = MediaType.MOVIE.value,
    val title: String = "",
    val posterPath: String? = null,
    val releaseDate: String = "",
    val rating: Double = 0.0,
    val originalLanguage: String = "",
) {
    val resolvedMediaType: MediaType
        get() = MediaType.fromValue(mediaType)

    val isTv: Boolean
        get() = resolvedMediaType == MediaType.TV

    val uniqueKey: String
        get() = "${resolvedMediaType.value}_$movieId"

    val releaseYear: String
        get() = releaseDate.take(4).ifBlank { "—" }

    val languageName: String
        get() = LanguageUtils.displayName(originalLanguage)

    fun fullPosterUrl(): String? = posterPath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w500$it"
    }

    fun toSummary(): MovieSummary = MovieSummary(
        movieId = movieId,
        mediaType = resolvedMediaType,
        title = title,
        posterPath = posterPath,
        backdropPath = null,
        releaseDate = releaseDate,
        rating = rating,
        overview = "",
        originalLanguage = originalLanguage,
    )

    fun toFirestoreMap(): Map<String, Any?> = buildMap {
        put("movieId", movieId)
        put("mediaType", resolvedMediaType.value)
        put("title", title.take(300).ifBlank { "Untitled" })
        posterPath?.takeIf { it.isNotBlank() }?.let { put("posterPath", it.take(500)) }
        put("releaseDate", releaseDate.take(32))
        put("rating", rating.coerceIn(0.0, 10.0))
        put("originalLanguage", originalLanguage.trim().lowercase(Locale.US).take(16))
    }
}

@IgnoreExtraProperties
data class CustomList(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val items: List<CustomListEntry> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val posterPreviews: List<String>
        get() = items.mapNotNull { it.fullPosterUrl() }.take(4)

    fun containsMedia(movieId: Long, mediaType: MediaType): Boolean {
        val key = "${mediaType.value}_$movieId"
        return items.any { it.uniqueKey == key }
    }
}

@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "preferences",
    val userId: String = "",
    val accentPreset: String = AccentPreset.RED.key,
    val accentHex: String = AccentPreset.RED.defaultHex,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class MovieSummary(
    val movieId: Long,
    val mediaType: MediaType = MediaType.MOVIE,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String,
    val rating: Double,
    val overview: String,
    val popularity: Double = 0.0,
    val originalLanguage: String = "",
) {
    val isTv: Boolean
        get() = mediaType == MediaType.TV

    val uniqueKey: String
        get() = "${mediaType.value}_$movieId"

    val releaseYear: String
        get() = releaseDate.take(4).ifBlank { "—" }

    val languageName: String
        get() = LanguageUtils.displayName(originalLanguage)

    fun fullPosterUrl(): String? = posterPath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w500$it"
    }

    fun fullBackdropUrl(): String? = backdropPath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w780$it"
    }

    fun toCustomListEntry(): CustomListEntry = CustomListEntry(
        movieId = movieId,
        mediaType = mediaType.value,
        title = title,
        posterPath = posterPath,
        releaseDate = releaseDate,
        rating = rating,
        originalLanguage = originalLanguage,
    )
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

data class MediaVideo(
    val id: String,
    val key: String,
    val name: String,
    val site: String,
    val type: String,
    val official: Boolean,
    val publishedAt: String = "",
) {
    val youtubeThumbnailUrl: String
        get() = "https://img.youtube.com/vi/$key/hqdefault.jpg"

    val youtubeWatchUrl: String
        get() = "https://www.youtube.com/watch?v=$key"

    val youtubeEmbedUrl: String
        get() = "https://www.youtube-nocookie.com/embed/$key?autoplay=1&rel=0&playsinline=1"
}

data class TmdbImageItem(
    val filePath: String,
    val width: Int,
    val height: Int,
    val aspectRatio: Double,
    val isBackdrop: Boolean,
) {
    fun previewUrl(): String =
        "https://image.tmdb.org/t/p/${if (isBackdrop) "w780" else "w500"}$filePath"

    fun originalUrl(): String =
        "https://image.tmdb.org/t/p/original$filePath"
}

data class MediaImages(
    val backdrops: List<TmdbImageItem> = emptyList(),
    val posters: List<TmdbImageItem> = emptyList(),
) {
    val isEmpty: Boolean
        get() = backdrops.isEmpty() && posters.isEmpty()
}

data class WatchProviderInfo(
    val providerId: Int,
    val providerName: String,
    val logoPath: String?,
) {
    fun fullLogoUrl(): String? = logoPath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w92$it"
    }
}

data class WatchProvidersRegion(
    val regionCode: String = "IN",
    val link: String? = null,
    val flatrate: List<WatchProviderInfo> = emptyList(),
    val rent: List<WatchProviderInfo> = emptyList(),
    val buy: List<WatchProviderInfo> = emptyList(),
) {
    val isEmpty: Boolean
        get() = flatrate.isEmpty() && rent.isEmpty() && buy.isEmpty()

    val badgeProviders: List<WatchProviderInfo>
        get() = (flatrate + rent + buy).distinctBy { it.providerId }.take(3)
}

data class MovieDetails(
    val movieId: Long,
    val mediaType: MediaType = MediaType.MOVIE,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String,
    val rating: Double,
    val overview: String,
    val runtime: Int,
    val genres: List<String>,
    val cast: List<CastMember>,
    val directors: List<String> = emptyList(),
    val originalLanguage: String = "",
    val tagline: String = "",
    val numberOfSeasons: Int = 0,
    val numberOfEpisodes: Int = 0,
    val images: MediaImages = MediaImages(),
    val watchProvidersIN: WatchProvidersRegion? = null,
    val trailerVideo: MediaVideo? = null,
) {
    val isTv: Boolean
        get() = mediaType == MediaType.TV

    val uniqueKey: String
        get() = "${mediaType.value}_$movieId"

    val releaseYear: String
        get() = releaseDate.take(4).ifBlank { "—" }

    val languageName: String
        get() = LanguageUtils.displayName(originalLanguage)

    fun fullPosterUrl(): String? = posterPath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w500$it"
    }

    fun fullBackdropUrl(): String? = backdropPath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w780$it"
    }

    fun toSummary(): MovieSummary = MovieSummary(
        movieId = movieId,
        mediaType = mediaType,
        title = title,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate,
        rating = rating,
        overview = overview,
        originalLanguage = originalLanguage,
    )
}
