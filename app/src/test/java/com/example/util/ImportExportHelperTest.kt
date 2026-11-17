package com.example.util

import com.example.data.model.CustomList
import com.example.data.model.CustomListEntry
import com.example.data.model.MediaType
import com.example.data.model.WatchedMovie
import com.example.data.model.WatchlistItem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImportExportHelperTest {

    @Test
    fun parseCsv_imdbFormat_scalesTenPointRatingAndDetectsTv() {
        val imdbCsv = """
            Const,Your Rating,Date Rated,Title,URL,Title Type,IMDb Rating,Runtime (mins),Year,Genres,Num Votes,Release Date,Directors
            tt1375666,9,2026-02-14,"Inception",https://www.imdb.com/title/tt1375666/,movie,8.8,148,2010,"Action, Sci-Fi",2500000,2010-07-16,Christopher Nolan
            tt0903747,10,2026-03-01,"Breaking Bad",https://www.imdb.com/title/tt0903747/,tvSeries,9.5,49,2008,"Crime, Drama",2100000,2008-01-20,Vince Gilligan
        """.trimIndent()

        val parsed = ImportExportHelper.parseCsv(imdbCsv)

        assertEquals(ImportSourceFormat.IMDB, parsed.format)
        assertEquals(2, parsed.rows.size)

        val movie = parsed.rows[0]
        assertEquals("Inception", movie.sourceTitle)
        assertEquals(2010, movie.year)
        assertFalse(movie.isTv)
        assertEquals(5, movie.userRating) // 9 / 2 = 4.5 -> rounds to 5
        assertEquals("2026-02-14", movie.watchDate)

        val tv = parsed.rows[1]
        assertEquals("Breaking Bad", tv.sourceTitle)
        assertEquals(2008, tv.year)
        assertTrue(tv.isTv)
        assertEquals(5, tv.userRating)
    }

    @Test
    fun parseCsv_letterboxdFormat_handlesQuotedTitlesAndRatings() {
        val letterboxdCsv = """
            Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date,Review
            2026-04-02,"Dune: Part Two",2024,https://boxd.it/123,4.5,,sci-fi,2026-04-01,"Incredible sound, IMAX!"
            2026-04-05,"Parasite",2019,https://boxd.it/456,5,Yes,,2026-04-04,Masterpiece
        """.trimIndent()

        val parsed = ImportExportHelper.parseCsv(letterboxdCsv)

        assertEquals(ImportSourceFormat.LETTERBOXD, parsed.format)
        assertEquals(2, parsed.rows.size)

        assertEquals("Dune: Part Two", parsed.rows[0].sourceTitle)
        assertEquals(2024, parsed.rows[0].year)
        assertEquals(5, parsed.rows[0].userRating)
        assertEquals("2026-04-01", parsed.rows[0].watchDate)
        assertEquals("Incredible sound, IMAX!", parsed.rows[0].note)

        assertEquals("Parasite", parsed.rows[1].sourceTitle)
        assertEquals(2019, parsed.rows[1].year)
        assertEquals(5, parsed.rows[1].userRating)
    }

    @Test
    fun exportToCsvAndJson_includesWatchedWatchlistAndCustomLists() {
        val watched = listOf(
            WatchedMovie(
                movieId = 27205L,
                mediaType = MediaType.MOVIE.value,
                title = "Inception, \"2010\"",
                releaseDate = "2010-07-16",
                rating = 8.4,
                userRating = 5,
                note = "Spinning top",
                originalLanguage = "en",
                runtime = 148,
                genres = listOf("Sci-Fi", "Action"),
                watchLog = listOf("2026-01-10", "2026-02-11"),
            )
        )
        val watchlist = listOf(
            WatchlistItem(
                movieId = 157336L,
                mediaType = MediaType.MOVIE.value,
                title = "Interstellar",
                releaseDate = "2014-11-05",
                rating = 8.5,
                originalLanguage = "en",
                runtime = 169,
                genres = listOf("Sci-Fi"),
            )
        )
        val customLists = listOf(
            CustomList(
                id = "nolan",
                name = "Nolan Favorites",
                description = "Top picks",
                items = listOf(
                    CustomListEntry(
                        movieId = 27205L,
                        mediaType = MediaType.MOVIE.value,
                        title = "Inception",
                        releaseDate = "2010-07-16",
                        rating = 8.4,
                        originalLanguage = "en",
                    )
                ),
            )
        )

        val csv = ImportExportHelper.exportToCsv(watched, watchlist, customLists)
        assertTrue(csv.contains("watched"))
        assertTrue(csv.contains("watchlist"))
        assertTrue(csv.contains("custom_list"))
        assertTrue(csv.contains("Nolan Favorites"))

        // Round-trip CineLog CSV back into parser (only watched rows are imported)
        val roundTrip = ImportExportHelper.parseCsv(csv)
        assertEquals(ImportSourceFormat.CINELOG, roundTrip.format)
        assertEquals(1, roundTrip.rows.size)
        assertEquals("Inception, \"2010\"", roundTrip.rows.first().sourceTitle)

        val jsonStr = ImportExportHelper.exportToJson(watched, watchlist, customLists)
        val json = JSONObject(jsonStr)
        assertEquals("CineLog", json.getString("app"))
        assertEquals(1, json.getJSONObject("counts").getInt("watched"))
        assertEquals(1, json.getJSONObject("counts").getInt("watchlist"))
        assertEquals(1, json.getJSONObject("counts").getInt("customLists"))
    }
}
