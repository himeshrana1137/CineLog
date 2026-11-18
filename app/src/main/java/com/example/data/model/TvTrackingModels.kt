package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TvWatchStatus(val key: String, val label: String) {
    NOT_STARTED("NOT_STARTED", "Not Started"),
    WATCHING("WATCHING", "Watching"),
    COMPLETED("COMPLETED", "Completed");

    companion object {
        fun fromKey(raw: String?): TvWatchStatus? =
            entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) }
    }
}

data class TvSeasonSummary(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
    val airDate: String = "",
    val posterPath: String? = null,
) {
    val isSpecial: Boolean
        get() = seasonNumber <= 0

    val airYear: String
        get() = airDate.trim().take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty()

    val isUpcoming: Boolean
        get() {
            val cleanDate = airDate.trim().take(10)
            if (cleanDate.length < 10) return true
            return cleanDate > todayIsoDate()
        }

    val displayTitle: String
        get() = if (seasonNumber == 0) {
            name.ifBlank { "Specials" }
        } else {
            "Season $seasonNumber"
        }

    fun fullPosterUrl(): String? = posterPath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w342$it"
    }
}

data class TvEpisode(
    val episodeNumber: Int,
    val seasonNumber: Int,
    val name: String,
    val runtime: Int = 0,
    val airDate: String = "",
    val stillPath: String? = null,
    val overview: String = "",
) {
    val isUpcoming: Boolean
        get() {
            val cleanDate = airDate.trim().take(10)
            if (cleanDate.isEmpty()) return false
            return cleanDate > todayIsoDate()
        }

    fun fullStillUrl(): String? = stillPath?.takeIf { it.isNotBlank() }?.let {
        "https://image.tmdb.org/t/p/w300$it"
    }
}

data class TvSeasonDetail(
    val tvId: Long,
    val seasonNumber: Int,
    val name: String,
    val airDate: String = "",
    val posterPath: String? = null,
    val episodes: List<TvEpisode> = emptyList(),
)

@IgnoreExtraProperties
data class WatchedSeason(
    @DocumentId val id: String = "",
    val userId: String = "",
    val tvId: Long = 0L,
    val seasonNumber: Int = 0,
    val episodeCount: Int = 0,
    val watched: Boolean = false,
    val watchedEpisodes: List<Int> = emptyList(),
    val watchedRuntimeMinutes: Int = 0,
    @ServerTimestamp val watchedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun isConsideredWatched(knownEpisodeCount: Int = episodeCount): Boolean {
        val resolvedEpCount = maxOf(knownEpisodeCount, episodeCount)
        val uniqueWatchedCount = watchedEpisodes.filter { it > 0 }.distinct().size
        return watched || (resolvedEpCount > 0 && uniqueWatchedCount >= resolvedEpCount)
    }
}

data class TvComputedProgress(
    val status: TvWatchStatus,
    val totalSeasons: Int,
    val totalEpisodes: Int,
    val watchedEpisodesCount: Int,
    val watchedSeasonsCount: Int,
    val watchedRuntimeMinutes: Int,
    val currentSeason: Int,
    val nextUpSeason: Int,
    val nextUpEpisode: Int,
) {
    fun progressLabel(): String {
        val totalS = totalSeasons.coerceAtLeast(1)
        val curS = currentSeason.coerceIn(1, totalS)
        return "S$curS of $totalS • Episodes $watchedEpisodesCount / $totalEpisodes"
    }

    fun nextUpLabel(): String? =
        if (status == TvWatchStatus.WATCHING && nextUpSeason > 0 && nextUpEpisode > 0) {
            "Next up: S$nextUpSeason E$nextUpEpisode"
        } else {
            null
        }
}

object TvProgressCalculator {
    fun compute(
        seasons: List<TvSeasonSummary>,
        seasonDocs: Map<Int, WatchedSeason>,
        fallbackTotalSeasons: Int = 0,
        fallbackTotalEpisodes: Int = 0,
        fallbackAvgEpisodeRuntime: Int = 0,
        episodeDetailsBySeason: Map<Int, TvSeasonDetail> = emptyMap(),
    ): TvComputedProgress {
        val regularSeasons = seasons.filter { !it.isSpecial }.sortedBy { it.seasonNumber }
        val airedRegularSeasons = regularSeasons.filter { !it.isUpcoming && it.episodeCount > 0 }
        val totalSeasons = maxOf(regularSeasons.size, fallbackTotalSeasons)
        val regularEpisodeSum = regularSeasons.sumOf { it.episodeCount.coerceAtLeast(0) }
        val totalEpisodes = if (regularEpisodeSum > 0) regularEpisodeSum else fallbackTotalEpisodes.coerceAtLeast(0)

        var watchedEpisodesCount = 0
        var watchedSeasonsCount = 0

        if (regularSeasons.isNotEmpty()) {
            regularSeasons.forEach { season ->
                val doc = seasonDocs[season.seasonNumber]
                if (doc != null) {
                    val countForSeason = resolveSeasonWatchedCount(doc, season.episodeCount)
                    watchedEpisodesCount += countForSeason
                    if (doc.isConsideredWatched(season.episodeCount)) {
                        watchedSeasonsCount += 1
                    }
                }
            }
        } else {
            seasonDocs.values.filter { it.seasonNumber > 0 }.forEach { doc ->
                val countForSeason = resolveSeasonWatchedCount(doc, doc.episodeCount)
                watchedEpisodesCount += countForSeason
                if (doc.isConsideredWatched()) watchedSeasonsCount += 1
            }
        }

        val watchedRuntimeMinutes = computeTotalRuntimeMinutes(
            seasonDocs = seasonDocs,
            fallbackAvgEpisodeRuntime = fallbackAvgEpisodeRuntime,
            episodeDetailsBySeason = episodeDetailsBySeason,
        )

        val status = when {
            watchedEpisodesCount <= 0 && seasonDocs.values.none { it.watchedEpisodes.isNotEmpty() || it.watched } ->
                TvWatchStatus.NOT_STARTED
            airedRegularSeasons.isNotEmpty() && airedRegularSeasons.all { season ->
                val doc = seasonDocs[season.seasonNumber]
                doc != null && doc.isConsideredWatched(season.episodeCount)
            } -> TvWatchStatus.COMPLETED
            airedRegularSeasons.isEmpty() && totalEpisodes > 0 && watchedEpisodesCount >= totalEpisodes ->
                TvWatchStatus.COMPLETED
            else -> TvWatchStatus.WATCHING
        }

        var nextUpSeason = 0
        var nextUpEpisode = 0
        var currentSeason = 1

        if (status == TvWatchStatus.COMPLETED) {
            currentSeason = totalSeasons.coerceAtLeast(1)
        } else if (status == TvWatchStatus.WATCHING) {
            val firstIncomplete = airedRegularSeasons.firstOrNull { season ->
                val doc = seasonDocs[season.seasonNumber]
                doc == null || !doc.isConsideredWatched(season.episodeCount)
            }
            if (firstIncomplete != null) {
                val doc = seasonDocs[firstIncomplete.seasonNumber]
                val watchedSet = doc?.watchedEpisodes?.toSet().orEmpty()
                val nextEp = (1..firstIncomplete.episodeCount).firstOrNull { !watchedSet.contains(it) } ?: 1
                nextUpSeason = firstIncomplete.seasonNumber
                nextUpEpisode = nextEp
                currentSeason = firstIncomplete.seasonNumber
            } else {
                val highestWatchedSeason = seasonDocs.values
                    .filter { it.seasonNumber > 0 && (it.watched || it.watchedEpisodes.isNotEmpty()) }
                    .maxOfOrNull { it.seasonNumber }
                    ?: 1
                currentSeason = highestWatchedSeason
            }
        } else {
            currentSeason = airedRegularSeasons.firstOrNull()?.seasonNumber ?: 1
        }

        return TvComputedProgress(
            status = status,
            totalSeasons = totalSeasons,
            totalEpisodes = totalEpisodes.coerceAtLeast(watchedEpisodesCount),
            watchedEpisodesCount = watchedEpisodesCount,
            watchedSeasonsCount = watchedSeasonsCount,
            watchedRuntimeMinutes = watchedRuntimeMinutes,
            currentSeason = currentSeason,
            nextUpSeason = nextUpSeason,
            nextUpEpisode = nextUpEpisode,
        )
    }

    fun resolveSeasonWatchedCount(doc: WatchedSeason, knownEpisodeCount: Int): Int {
        val uniqueEps = doc.watchedEpisodes.filter { it > 0 }.distinct()
        return when {
            uniqueEps.isNotEmpty() -> {
                if (knownEpisodeCount > 0) uniqueEps.count { it <= knownEpisodeCount } else uniqueEps.size
            }
            doc.watched && knownEpisodeCount > 0 -> knownEpisodeCount
            doc.watched && doc.episodeCount > 0 -> doc.episodeCount
            else -> 0
        }
    }

    fun computeSeasonRuntimeMinutes(
        watchedEpisodes: Collection<Int>,
        seasonDetail: TvSeasonDetail?,
        fallbackAvgEpisodeRuntime: Int,
    ): Int {
        if (watchedEpisodes.isEmpty()) return 0
        val avg = fallbackAvgEpisodeRuntime.coerceAtLeast(0)
        if (seasonDetail == null || seasonDetail.episodes.isEmpty()) {
            return watchedEpisodes.distinct().size * avg
        }
        val epRuntimeMap = seasonDetail.episodes.associate { it.episodeNumber to it.runtime }
        return watchedEpisodes.distinct().sumOf { epNum ->
            val epRuntime = epRuntimeMap[epNum] ?: 0
            if (epRuntime > 0) epRuntime else avg
        }
    }

    fun computeTotalRuntimeMinutes(
        seasonDocs: Map<Int, WatchedSeason>,
        fallbackAvgEpisodeRuntime: Int,
        episodeDetailsBySeason: Map<Int, TvSeasonDetail> = emptyMap(),
    ): Int {
        return seasonDocs.values.sumOf { doc ->
            when {
                doc.watchedRuntimeMinutes > 0 -> doc.watchedRuntimeMinutes
                doc.watchedEpisodes.isNotEmpty() -> computeSeasonRuntimeMinutes(
                    watchedEpisodes = doc.watchedEpisodes,
                    seasonDetail = episodeDetailsBySeason[doc.seasonNumber],
                    fallbackAvgEpisodeRuntime = fallbackAvgEpisodeRuntime,
                )
                doc.watched && doc.episodeCount > 0 -> doc.episodeCount * fallbackAvgEpisodeRuntime.coerceAtLeast(0)
                else -> 0
            }
        }
    }
}
