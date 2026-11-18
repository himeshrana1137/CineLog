package com.example

import com.example.data.model.MediaType
import com.example.data.model.QuickReaction
import com.example.data.model.StatsCalculator
import com.example.data.model.TvWatchStatus
import com.example.data.model.WatchedMovie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {

    @Test
    fun computeStats_calculatesWatchTimeActorsDirectorsAndLanguages() {
        val watched = listOf(
            WatchedMovie(
                movieId = 1L,
                mediaType = MediaType.MOVIE.value,
                title = "3 Idiots",
                runtime = 170,
                userRating = 5,
                reaction = QuickReaction.MASTERPIECE.key,
                originalLanguage = "hi",
                genres = listOf("Comedy", "Drama"),
                cast = listOf("Aamir Khan", "R. Madhavan", "Sharman Joshi"),
                directors = listOf("Rajkumar Hirani"),
            ),
            WatchedMovie(
                movieId = 2L,
                mediaType = MediaType.MOVIE.value,
                title = "PK",
                runtime = 153,
                userRating = 4,
                reaction = QuickReaction.HILARIOUS.key,
                originalLanguage = "hi",
                genres = listOf("Comedy", "Sci-Fi"),
                cast = listOf("Aamir Khan", "Anushka Sharma"),
                directors = listOf("Rajkumar Hirani"),
            ),
            WatchedMovie(
                movieId = 3L,
                mediaType = MediaType.MOVIE.value,
                title = "Chhello Show",
                runtime = 110,
                userRating = 5,
                reaction = QuickReaction.EMOTIONAL.key,
                originalLanguage = "gu",
                genres = listOf("Drama"),
                cast = listOf("Bhavin Rabari"),
                directors = listOf("Pan Nalin"),
            ),
        )

        val stats = StatsCalculator.computeStats(watched)

        assertEquals(3, stats.totalTitles)
        assertEquals(3, stats.moviesCount)
        assertEquals(0, stats.tvCount)
        assertEquals(433L, stats.totalMinutes)
        assertEquals(144, stats.averageRuntimeMinutes)
        assertEquals("7 hours = 0 days 7 hours", stats.formattedHoursAndDays)
        assertEquals("Aamir Khan", stats.topActors.first().name)
        assertEquals(2, stats.topActors.first().count)
        assertEquals("Rajkumar Hirani", stats.topDirectors.first().name)
        assertEquals(2, stats.topDirectors.first().count)
        assertEquals(1, stats.reactionCounts[QuickReaction.MASTERPIECE])
        assertEquals(0, stats.itemsMissingMetadataCount)
        assertTrue(stats.languageStats.any { it.code == "hi" && it.count == 2 })
        assertTrue(stats.languageStats.any { it.code == "gu" && it.count == 1 })
    }

    @Test
    fun computeStats_calculatesRewatchEventsAndGenreAnalyticsCharts() {
        val watched = listOf(
            WatchedMovie(
                movieId = 10L,
                mediaType = MediaType.MOVIE.value,
                title = "Inception",
                releaseDate = "2010-07-16",
                runtime = 148,
                userRating = 5,
                originalLanguage = "en",
                genres = listOf("Sci-Fi", "Action"),
                cast = listOf("Leonardo DiCaprio"),
                directors = listOf("Christopher Nolan"),
                watchLog = listOf("2026-01-10", "2026-03-14"),
            ),
            WatchedMovie(
                movieId = 11L,
                mediaType = MediaType.MOVIE.value,
                title = "Interstellar",
                releaseDate = "2014-11-05",
                runtime = 169,
                userRating = 5,
                originalLanguage = "en",
                genres = listOf("Sci-Fi", "Drama"),
                cast = listOf("Matthew McConaughey"),
                directors = listOf("Christopher Nolan"),
                watchLog = listOf("2026-03-20"),
            ),
        )

        val stats = StatsCalculator.computeStats(watched)

        assertEquals(2, stats.totalTitles)
        assertEquals(3, stats.totalWatchEvents)
        // Inception watched twice (148 * 2) + Interstellar (169) = 465 minutes
        assertEquals(465L, stats.totalMinutes)
        assertEquals("Sci-Fi", stats.genreStats.first().label)
        assertEquals(2, stats.genreStats.first().count)
        assertEquals(listOf("2010", "2014"), stats.releaseYearStats.map { it.label })
        assertEquals(2, stats.userRatingDistribution.first { it.label == "5★" }.count)
        assertEquals(0, stats.userRatingDistribution.first { it.label == "4★" }.count)
        assertTrue(stats.monthlyWatchStats.any { it.label == "Jan '26" && it.count == 1 })
        assertTrue(stats.monthlyWatchStats.any { it.label == "Mar '26" && it.count == 2 })
    }

    @Test
    fun computeStats_calculatesTvWatchTimeFromEpisodesAndAvgFallback() {
        val watched = listOf(
            WatchedMovie(
                movieId = 1396L,
                mediaType = MediaType.TV.value,
                title = "Breaking Bad",
                runtime = 47,
                status = TvWatchStatus.WATCHING.key,
                watchedEpisodesCount = 10,
                watchedRuntimeMinutes = 485,
            ),
            WatchedMovie(
                movieId = 1399L,
                mediaType = MediaType.TV.value,
                title = "Game of Thrones",
                runtime = 55,
                status = TvWatchStatus.WATCHING.key,
                watchedEpisodesCount = 4,
                watchedRuntimeMinutes = 0, // falls back to 4 * 55 = 220
            ),
            WatchedMovie(
                movieId = 9999L,
                mediaType = MediaType.TV.value,
                title = "Reset Show",
                runtime = 45,
                status = TvWatchStatus.NOT_STARTED.key,
                watchedEpisodesCount = 0,
                watchedRuntimeMinutes = 0,
            ),
        )

        val stats = StatsCalculator.computeStats(watched)

        assertEquals(3, stats.tvCount)
        assertEquals(705L, stats.totalMinutes)
    }

    @Test
    fun computeStats_allItemsSumInYearAndRatingChartsAndSupportsFilter() {
        val watched = (1..12).map { idx ->
            WatchedMovie(
                movieId = idx.toLong(),
                mediaType = if (idx <= 10) MediaType.MOVIE.value else MediaType.TV.value,
                title = "Title $idx",
                releaseDate = if (idx == 12) "" else "${2000 + idx}-01-01",
                runtime = 100,
                userRating = if (idx % 2 == 0) 5 else 0,
                genres = listOf("Science Fiction"),
            )
        }

        val allStats = StatsCalculator.computeStats(watched, com.example.data.model.StatsMediaFilter.ALL)
        assertEquals(12, allStats.totalTitles)
        assertEquals(12, allStats.releaseYearStats.sumOf { it.count })
        assertTrue(allStats.releaseYearStats.any { it.label == "Older" })
        assertTrue(allStats.releaseYearStats.any { it.label == "Unknown" && it.count == 1 })
        assertEquals(12, allStats.userRatingDistribution.sumOf { it.count })
        assertEquals(6, allStats.userRatingDistribution.first { it.label == "Unrated" }.count)
        assertEquals("Sci-Fi", allStats.genreStats.first().label)

        val tvStats = StatsCalculator.computeStats(watched, com.example.data.model.StatsMediaFilter.TV_SERIES)
        assertEquals(2, tvStats.totalTitles)
        assertEquals(0, tvStats.moviesCount)
        assertEquals(2, tvStats.tvCount)
        assertEquals(2, tvStats.releaseYearStats.sumOf { it.count })
        assertEquals(2, tvStats.userRatingDistribution.sumOf { it.count })
    }
}
