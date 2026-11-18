package com.example.data.model

data class PersonStat(
    val name: String,
    val count: Int,
    val movies: List<WatchedMovie>,
)

data class ChartSlice(
    val label: String,
    val count: Int,
    val percentage: Float,
)

enum class StatsMediaFilter(val label: String, val pluralNoun: String) {
    ALL("All", "Titles"),
    MOVIES("Movies", "Movies"),
    TV_SERIES("TV Series", "TV Series"),
}

data class ChartPoint(
    val label: String,
    val count: Int,
)

data class CineStatsSummary(
    val totalTitles: Int = 0,
    val totalWatchEvents: Int = 0,
    val moviesCount: Int = 0,
    val tvCount: Int = 0,
    val totalMinutes: Long = 0L,
    val averageRuntimeMinutes: Int = 0,
    val formattedHoursAndDays: String = "0 hours = 0 days 0 hours",
    val averageUserRating: Double = 0.0,
    val ratedCount: Int = 0,
    val topActors: List<PersonStat> = emptyList(),
    val topDirectors: List<PersonStat> = emptyList(),
    val languageStats: List<LanguageChipCount> = emptyList(),
    val reactionCounts: Map<QuickReaction, Int> = emptyMap(),
    val genreStats: List<ChartSlice> = emptyList(),
    val totalGenreTags: Int = 0,
    val releaseYearStats: List<ChartPoint> = emptyList(),
    val userRatingDistribution: List<ChartPoint> = emptyList(),
    val monthlyWatchStats: List<ChartPoint> = emptyList(),
    val itemsMissingMetadataCount: Int = 0,
)

object StatsCalculator {
    fun filterItems(
        watched: List<WatchedMovie>,
        filter: StatsMediaFilter,
    ): List<WatchedMovie> = when (filter) {
        StatsMediaFilter.ALL -> watched
        StatsMediaFilter.MOVIES -> watched.filter { !it.isTv }
        StatsMediaFilter.TV_SERIES -> watched.filter { it.isTv }
    }

    fun normalizeGenreLabel(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.equals("Science Fiction", ignoreCase = true) -> "Sci-Fi"
            trimmed.equals("Action & Adventure", ignoreCase = true) -> "Action & Adv."
            trimmed.equals("Sci-Fi & Fantasy", ignoreCase = true) -> "Sci-Fi & Fantasy"
            else -> trimmed
        }
    }

    fun computeStats(
        watched: List<WatchedMovie>,
        filter: StatsMediaFilter = StatsMediaFilter.ALL,
    ): CineStatsSummary {
        val scoped = filterItems(watched, filter)
        if (scoped.isEmpty()) return CineStatsSummary()

        val moviesCount = scoped.count { !it.isTv }
        val tvCount = scoped.count { it.isTv }
        val totalWatchEvents = scoped.sumOf { it.watchCount }

        val totalMinutes = scoped.sumOf { item ->
            if (!item.isTv) {
                item.runtime.toLong().coerceAtLeast(0L) * item.watchCount.coerceAtLeast(1)
            } else {
                when {
                    item.watchedRuntimeMinutes > 0 -> item.watchedRuntimeMinutes.toLong()
                    item.watchedEpisodesCount > 0 -> {
                        item.watchedEpisodesCount.toLong() * item.runtime.toLong().coerceAtLeast(0L)
                    }
                    item.status.equals(TvWatchStatus.NOT_STARTED.key, ignoreCase = true) -> 0L
                    else -> {
                        val perEp = item.runtime.toLong().coerceAtLeast(0L)
                        val legacyEpisodes = when {
                            perEp == 0L -> 0L
                            item.watchedSeason > 0 && item.numberOfSeasons > 0 && item.numberOfEpisodes > 0 ->
                                (item.numberOfEpisodes / item.numberOfSeasons).coerceAtLeast(1).toLong()
                            item.numberOfEpisodes > 0 -> item.numberOfEpisodes.toLong()
                            else -> 1L
                        }
                        perEp * legacyEpisodes
                    }
                }
            }
        }

        val itemsWithRuntime = scoped.filter { it.runtime > 0 }
        val avgRuntime = if (itemsWithRuntime.isNotEmpty()) {
            (itemsWithRuntime.sumOf { it.runtime } / itemsWithRuntime.size)
        } else {
            0
        }

        val totalHours = totalMinutes / 60L
        val days = totalHours / 24L
        val remHours = totalHours % 24L
        val formattedTime = "$totalHours ${if (totalHours == 1L) "hour" else "hours"} = " +
            "$days ${if (days == 1L) "day" else "days"} $remHours ${if (remHours == 1L) "hour" else "hours"}"

        val rated = scoped.filter { it.userRating in 1..5 }
        val avgRating = if (rated.isNotEmpty()) rated.map { it.userRating }.average() else 0.0

        val actorMap = linkedMapOf<String, MutableList<WatchedMovie>>()
        val directorMap = linkedMapOf<String, MutableList<WatchedMovie>>()
        val genreCounts = linkedMapOf<String, Int>()
        val yearCounts = linkedMapOf<Int, Int>()
        var unknownYearCount = 0
        val monthCounts = linkedMapOf<String, Int>()

        scoped.forEach { item ->
            item.cast.distinct().forEach { actor ->
                if (actor.isNotBlank()) {
                    actorMap.getOrPut(actor) { mutableListOf() }.add(item)
                }
            }
            item.directors.distinct().forEach { director ->
                if (director.isNotBlank()) {
                    directorMap.getOrPut(director) { mutableListOf() }.add(item)
                }
            }
            item.genres.map { normalizeGenreLabel(it) }.distinct().forEach { genre ->
                if (genre.isNotBlank()) {
                    genreCounts[genre] = (genreCounts[genre] ?: 0) + 1
                }
            }
            val parsedYear = item.releaseDate.take(4).toIntOrNull()?.takeIf { it in 1888..2100 }
            if (parsedYear != null) {
                yearCounts[parsedYear] = (yearCounts[parsedYear] ?: 0) + 1
            } else {
                unknownYearCount += 1
            }
            item.resolvedWatchDates.forEach { isoDate ->
                if (isoDate.length >= 7) {
                    val ym = isoDate.take(7)
                    monthCounts[ym] = (monthCounts[ym] ?: 0) + 1
                }
            }
        }

        val topActors = actorMap.entries
            .map { (name, list) -> PersonStat(name = name, count = list.size, movies = list) }
            .sortedWith(compareByDescending<PersonStat> { it.count }.thenBy { it.name })
            .take(25)

        val topDirectors = directorMap.entries
            .map { (name, list) -> PersonStat(name = name, count = list.size, movies = list) }
            .sortedWith(compareByDescending<PersonStat> { it.count }.thenBy { it.name })
            .take(25)

        val langStats = buildLanguageChips(scoped.map { it.originalLanguage })

        val reactionCounts = QuickReaction.entries.associateWith { reaction ->
            scoped.count { it.reaction == reaction.key }
        }

        val totalGenreTags = genreCounts.values.sum()
        val safeGenreDenom = totalGenreTags.coerceAtLeast(1)
        val sortedGenres = genreCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        val topGenres = sortedGenres.take(6).map { (genre, count) ->
            ChartSlice(
                label = genre,
                count = count,
                percentage = count.toFloat() / safeGenreDenom.toFloat(),
            )
        }
        val otherGenreCount = sortedGenres.drop(6).sumOf { it.value }
        val genreStats = if (otherGenreCount > 0) {
            topGenres + ChartSlice(
                label = "Other",
                count = otherGenreCount,
                percentage = otherGenreCount.toFloat() / safeGenreDenom.toFloat(),
            )
        } else {
            topGenres
        }

        // Release Year: include ALL titles; if > 10 years, keep latest 8 years + "Older"; plus "Unknown"
        val sortedYearEntries = yearCounts.entries.sortedBy { it.key }
        val releaseYearStats = buildList {
            if (sortedYearEntries.size > 10) {
                val recent8 = sortedYearEntries.takeLast(8)
                val olderCount = sortedYearEntries.dropLast(8).sumOf { it.value }
                if (olderCount > 0) {
                    add(ChartPoint(label = "Older", count = olderCount))
                }
                recent8.forEach { (yr, count) ->
                    add(ChartPoint(label = yr.toString(), count = count))
                }
            } else {
                sortedYearEntries.forEach { (yr, count) ->
                    add(ChartPoint(label = yr.toString(), count = count))
                }
            }
            if (unknownYearCount > 0) {
                add(ChartPoint(label = "Unknown", count = unknownYearCount))
            }
        }

        val unratedCount = scoped.count { it.userRating !in 1..5 }
        val userRatingDistribution = (1..5).map { stars ->
            ChartPoint(
                label = "${stars}★",
                count = scoped.count { it.userRating == stars },
            )
        } + ChartPoint(
            label = "Unrated",
            count = unratedCount,
        )

        val monthlyWatchStats = monthCounts.entries
            .sortedBy { it.key }
            .takeLast(10)
            .map { (ym, count) -> ChartPoint(label = formatShortMonth(ym), count = count) }

        val missingCount = scoped.count { it.needsMetadataBackfill }

        return CineStatsSummary(
            totalTitles = scoped.size,
            totalWatchEvents = totalWatchEvents,
            moviesCount = moviesCount,
            tvCount = tvCount,
            totalMinutes = totalMinutes,
            averageRuntimeMinutes = avgRuntime,
            formattedHoursAndDays = formattedTime,
            averageUserRating = avgRating,
            ratedCount = rated.size,
            topActors = topActors,
            topDirectors = topDirectors,
            languageStats = langStats,
            reactionCounts = reactionCounts,
            genreStats = genreStats,
            totalGenreTags = totalGenreTags,
            releaseYearStats = releaseYearStats,
            userRatingDistribution = userRatingDistribution,
            monthlyWatchStats = monthlyWatchStats,
            itemsMissingMetadataCount = missingCount,
        )
    }

    private fun formatShortMonth(yyyyMm: String): String {
        val parts = yyyyMm.split('-')
        if (parts.size != 2) return yyyyMm
        val monthIdx = parts[1].toIntOrNull() ?: return yyyyMm
        val shortMonth = when (monthIdx) {
            1 -> "Jan"
            2 -> "Feb"
            3 -> "Mar"
            4 -> "Apr"
            5 -> "May"
            6 -> "Jun"
            7 -> "Jul"
            8 -> "Aug"
            9 -> "Sep"
            10 -> "Oct"
            11 -> "Nov"
            12 -> "Dec"
            else -> parts[1]
        }
        return "$shortMonth '${parts[0].takeLast(2)}"
    }

    fun buildLanguageChips(codes: List<String>): List<LanguageChipCount> {
        val counts = codes
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()

        return counts.entries
            .map { (code, count) ->
                LanguageChipCount(
                    code = code,
                    label = LanguageUtils.displayName(code),
                    count = count,
                )
            }
            .sortedWith(
                compareByDescending<LanguageChipCount> { it.isPrimary }
                    .thenByDescending { it.count }
                    .thenBy { it.label }
            )
    }
}

