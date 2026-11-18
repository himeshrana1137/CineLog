package com.example

import com.example.data.model.TvEpisode
import com.example.data.model.TvProgressCalculator
import com.example.data.model.TvSeasonDetail
import com.example.data.model.TvSeasonSummary
import com.example.data.model.TvWatchStatus
import com.example.data.model.WatchedSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvProgressCalculatorTest {

    private val sampleSeasons = listOf(
        TvSeasonSummary(
            seasonNumber = 0,
            name = "Specials",
            episodeCount = 3,
            airDate = "2020-01-01",
        ),
        TvSeasonSummary(
            seasonNumber = 1,
            name = "Season 1",
            episodeCount = 3,
            airDate = "2021-01-01",
        ),
        TvSeasonSummary(
            seasonNumber = 2,
            name = "Season 2",
            episodeCount = 3,
            airDate = "2022-01-01",
        ),
        TvSeasonSummary(
            seasonNumber = 3,
            name = "Season 3",
            episodeCount = 4,
            airDate = "2099-01-01", // Upcoming season
        ),
    )

    @Test
    fun compute_notStarted_whenNoEpisodesOrSeasonsWatched() {
        val progress = TvProgressCalculator.compute(
            seasons = sampleSeasons,
            seasonDocs = emptyMap(),
            fallbackAvgEpisodeRuntime = 45,
        )

        assertEquals(TvWatchStatus.NOT_STARTED, progress.status)
        assertEquals(3, progress.totalSeasons)
        assertEquals(10, progress.totalEpisodes)
        assertEquals(0, progress.watchedEpisodesCount)
        assertEquals(0, progress.watchedRuntimeMinutes)
        assertNull(progress.nextUpLabel())
    }

    @Test
    fun compute_watching_calculatesNextUpAndPartialRuntimeWithFallback() {
        val season1Doc = WatchedSeason(
            seasonNumber = 1,
            episodeCount = 3,
            watched = true,
            watchedEpisodes = listOf(1, 2, 3),
            watchedRuntimeMinutes = 140,
        )
        val season2Detail = TvSeasonDetail(
            tvId = 100L,
            seasonNumber = 2,
            name = "Season 2",
            episodes = listOf(
                TvEpisode(episodeNumber = 1, seasonNumber = 2, name = "Ep 1", runtime = 52),
                TvEpisode(episodeNumber = 2, seasonNumber = 2, name = "Ep 2", runtime = 0), // missing runtime -> fallback
                TvEpisode(episodeNumber = 3, seasonNumber = 2, name = "Ep 3", runtime = 48),
            ),
        )
        val season2Runtime = TvProgressCalculator.computeSeasonRuntimeMinutes(
            watchedEpisodes = listOf(1, 2),
            seasonDetail = season2Detail,
            fallbackAvgEpisodeRuntime = 45,
        )
        assertEquals(97, season2Runtime) // 52 + 45 fallback

        val season2Doc = WatchedSeason(
            seasonNumber = 2,
            episodeCount = 3,
            watched = false,
            watchedEpisodes = listOf(1, 2),
            watchedRuntimeMinutes = season2Runtime,
        )

        val progress = TvProgressCalculator.compute(
            seasons = sampleSeasons,
            seasonDocs = mapOf(1 to season1Doc, 2 to season2Doc),
            fallbackAvgEpisodeRuntime = 45,
        )

        assertEquals(TvWatchStatus.WATCHING, progress.status)
        assertEquals(5, progress.watchedEpisodesCount)
        assertEquals(1, progress.watchedSeasonsCount)
        assertEquals(237, progress.watchedRuntimeMinutes) // 140 + 97
        assertEquals(2, progress.currentSeason)
        assertEquals(2, progress.nextUpSeason)
        assertEquals(3, progress.nextUpEpisode)
        assertEquals("Next up: S2 E3", progress.nextUpLabel())
        assertEquals("S2 of 3 • Episodes 5 / 10", progress.progressLabel())
    }

    @Test
    fun compute_completed_whenAllAiredRegularSeasonsWatched_ignoringUpcomingAndSpecials() {
        val seasonDocs = mapOf(
            1 to WatchedSeason(
                seasonNumber = 1,
                episodeCount = 3,
                watched = true,
                watchedEpisodes = listOf(1, 2, 3),
                watchedRuntimeMinutes = 135,
            ),
            2 to WatchedSeason(
                seasonNumber = 2,
                episodeCount = 3,
                watched = true,
                watchedEpisodes = listOf(1, 2, 3),
                watchedRuntimeMinutes = 150,
            ),
        )

        val progress = TvProgressCalculator.compute(
            seasons = sampleSeasons,
            seasonDocs = seasonDocs,
            fallbackAvgEpisodeRuntime = 45,
        )

        assertEquals(TvWatchStatus.COMPLETED, progress.status)
        assertEquals(6, progress.watchedEpisodesCount)
        assertEquals(2, progress.watchedSeasonsCount)
        assertEquals(285, progress.watchedRuntimeMinutes)
        assertNull(progress.nextUpLabel())
    }
}
