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
    val releaseYearStats: List<ChartPoint> = emptyList(),
    val userRatingDistribution: List<ChartPoint> = emptyList(),
    val monthlyWatchStats: List<ChartPoint> = emptyList(),
    val itemsMissingMetadataCount: Int = 0,
)

object StatsCalculator {
    fun computeStats(watched: List<WatchedMovie>): CineStatsSummary {
        if (watched.isEmpty()) return CineStatsSummary()

        val moviesCount = watched.count { !it.isTv }
        val tvCount = watched.count { it.isTv }
        val totalWatchEvents = watched.sumOf { it.watchCount }

        val totalMinutes = watched.sumOf { item ->
            val baseRuntime = if (!item.isTv) {
                item.runtime.toLong().coerceAtLeast(0L)
            } else {
                val perEp = item.runtime.toLong().coerceAtLeast(0L)
                if (perEp == 0L) 0L
                else if (item.watchedSeason > 0 && item.numberOfSeasons > 0 && item.numberOfEpisodes > 0) {
                    val avgEpsPerSeason = (item.numberOfEpisodes / item.numberOfSeasons).coerceAtLeast(1)
                    perEp * avgEpsPerSeason
                } else if (item.numberOfEpisodes > 0) {
                    perEp * item.numberOfEpisodes
                } else {
                    perEp
                }
            }
            baseRuntime * item.watchCount.coerceAtLeast(1)
        }

        val itemsWithRuntime = watched.filter { it.runtime > 0 }
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

        val rated = watched.filter { it.userRating > 0 }
        val avgRating = if (rated.isNotEmpty()) rated.map { it.userRating }.average() else 0.0

        val actorMap = linkedMapOf<String, MutableList<WatchedMovie>>()
        val directorMap = linkedMapOf<String, MutableList<WatchedMovie>>()
        val genreCounts = linkedMapOf<String, Int>()
        val yearCounts = linkedMapOf<Int, Int>()
        val monthCounts = linkedMapOf<String, Int>()

        watched.forEach { item ->
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
            item.genres.distinct().forEach { genre ->
                val clean = genre.trim()
                if (clean.isNotBlank()) {
                    genreCounts[clean] = (genreCounts[clean] ?: 0) + 1
                }
            }
            item.releaseDate.take(4).toIntOrNull()?.let { yr ->
                if (yr in 1888..2100) {
                    yearCounts[yr] = (yearCounts[yr] ?: 0) + 1
                }
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

        val langStats = buildLanguageChips(watched.map { it.originalLanguage })

        val reactionCounts = QuickReaction.entries.associateWith { reaction ->
            watched.count { it.reaction == reaction.key }
        }

        val totalGenreTags = genreCounts.values.sum().coerceAtLeast(1)
        val sortedGenres = genreCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        val topGenres = sortedGenres.take(6).map { (genre, count) ->
            ChartSlice(
                label = genre,
                count = count,
                percentage = count.toFloat() / totalGenreTags.toFloat(),
            )
        }
        val otherGenreCount = sortedGenres.drop(6).sumOf { it.value }
        val genreStats = if (otherGenreCount > 0) {
            topGenres + ChartSlice(
                label = "Other",
                count = otherGenreCount,
                percentage = otherGenreCount.toFloat() / totalGenreTags.toFloat(),
            )
        } else {
            topGenres
        }

        val releaseYearStats = yearCounts.entries
            .sortedBy { it.key }
            .takeLast(10)
            .map { (yr, count) -> ChartPoint(label = yr.toString(), count = count) }

        val userRatingDistribution = (1..5).map { stars ->
            ChartPoint(
                label = "${stars}★",
                count = watched.count { it.userRating == stars },
            )
        }

        val monthlyWatchStats = monthCounts.entries
            .sortedBy { it.key }
            .takeLast(10)
            .map { (ym, count) -> ChartPoint(label = formatShortMonth(ym), count = count) }

        val missingCount = watched.count { it.needsMetadataBackfill }

        return CineStatsSummary(
            totalTitles = watched.size,
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

