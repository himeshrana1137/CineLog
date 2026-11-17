package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AccentPreset
import com.example.data.model.CastMember
import com.example.data.model.MediaType
import com.example.data.model.MovieDetails
import com.example.data.model.MovieSummary
import com.example.data.model.QuickReaction
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchedMovieRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun sampleMovieSummary(movieId: Long = 550L) = MovieSummary(
        movieId = movieId,
        mediaType = MediaType.MOVIE,
        title = "Fight Club",
        posterPath = "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
        backdropPath = "/hZkgoQYus5vegHoetLkCJzb17zJ.jpg",
        releaseDate = "1999-10-15",
        rating = 8.4,
        overview = "An insomniac office worker forms an underground fight club.",
        originalLanguage = "en",
    )

    private fun sampleTvSummary(tvId: Long = 1396L) = MovieSummary(
        movieId = tvId,
        mediaType = MediaType.TV,
        title = "Breaking Bad",
        posterPath = "/ggFHVNu6YYI5L9pCfOacjizRGt.jpg",
        backdropPath = null,
        releaseDate = "2008-01-20",
        rating = 8.9,
        overview = "A high school chemistry teacher turned methamphetamine manufacturer.",
        originalLanguage = "en",
    )

    @Test
    fun markAsWatched_rewatch_batchImport_watchlist_customLists_and_settings_succeed() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val watchedRepo = WatchedMovieRepository(firestore, auth)
        val watchlistRepo = WatchlistRepository(firestore, auth)
        val customListRepo = CustomListRepository(firestore, auth)
        val settingsRepo = UserSettingsRepository(firestore, auth)

        val movieDetails = MovieDetails(
            movieId = 550L,
            mediaType = MediaType.MOVIE,
            title = "Fight Club",
            posterPath = "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
            backdropPath = null,
            releaseDate = "1999-10-15",
            rating = 8.4,
            overview = "An insomniac office worker forms an underground fight club.",
            runtime = 139,
            genres = listOf("Drama", "Thriller"),
            cast = listOf(CastMember(819L, "Edward Norton", "The Narrator", null)),
            directors = listOf("David Fincher"),
            originalLanguage = "en",
        )

        val movieResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            watchedRepo.markAsWatched(
                movie = sampleMovieSummary(550L),
                details = movieDetails,
                reaction = QuickReaction.MIND_BLOWN.key,
                watchDate = "2026-01-10",
            )
        }
        assertTrue("Expected movie markAsWatched to succeed: ${movieResult.exceptionOrNull()}", movieResult.isSuccess)

        // Log rewatch
        val rewatchRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            watchedRepo.logRewatch(
                movieId = 550L,
                mediaType = MediaType.MOVIE,
                watchDate = "2026-02-14",
                currentLog = listOf("2026-01-10"),
            )
        }
        assertTrue("Expected logRewatch to succeed: ${rewatchRes.exceptionOrNull()}", rewatchRes.isSuccess)
        assertEquals(listOf("2026-01-10", "2026-02-14"), rewatchRes.getOrNull())

        val tvResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            watchedRepo.markAsWatched(
                movie = sampleTvSummary(550L),
                details = MovieDetails(
                    movieId = 550L,
                    mediaType = MediaType.TV,
                    title = "Breaking Bad",
                    posterPath = "/ggFHVNu6YYI5L9pCfOacjizRGt.jpg",
                    backdropPath = null,
                    releaseDate = "2008-01-20",
                    rating = 8.9,
                    overview = "Chemistry teacher turned kingpin.",
                    runtime = 47,
                    genres = listOf("Drama", "Crime"),
                    cast = listOf(CastMember(17419L, "Bryan Cranston", "Walter White", null)),
                    directors = listOf("Vince Gilligan"),
                    originalLanguage = "en",
                    numberOfSeasons = 5,
                    numberOfEpisodes = 62,
                ),
                watchedSeason = 3,
                reaction = QuickReaction.MASTERPIECE.key,
            )
        }
        assertTrue("Expected TV markAsWatched to succeed: ${tvResult.exceptionOrNull()}", tvResult.isSuccess)

        // Batch import
        val batchItems = listOf(
            BatchWatchedImportItem(
                summary = MovieSummary(
                    movieId = 603L,
                    mediaType = MediaType.MOVIE,
                    title = "The Matrix",
                    posterPath = "/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg",
                    backdropPath = null,
                    releaseDate = "1999-03-31",
                    rating = 8.2,
                    overview = "Simulation theory.",
                    originalLanguage = "en",
                ),
                userRating = 5,
                watchLog = listOf("2025-11-20"),
            )
        )
        val batchRes = withTimeout(DEFAULT_TIMEOUT_MS) { watchedRepo.importBatch(batchItems) }
        assertTrue("Expected importBatch to succeed: ${batchRes.exceptionOrNull()}", batchRes.isSuccess)
        assertEquals(1, batchRes.getOrNull())

        val watchedList = withTimeout(FLOW_TIMEOUT_MS) {
            watchedRepo.observeWatchedMovies(aliceUid).first { list -> list.size >= 3 }
        }
        val fightClub = watchedList.first { it.id == "550" }
        assertEquals(2, fightClub.watchCount)
        assertTrue(watchedList.any { it.id == "tv_550" && it.isTv && it.watchedSeason == 3 && it.cast.contains("Bryan Cranston") })
        assertTrue(watchedList.any { it.id == "603" && it.userRating == 5 })

        val reactRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            watchedRepo.updateReaction(550L, MediaType.MOVIE, QuickReaction.EMOTIONAL.key)
        }
        assertTrue("Expected updateReaction to succeed: ${reactRes.exceptionOrNull()}", reactRes.isSuccess)

        val updatedMovie = withTimeout(DEFAULT_TIMEOUT_MS) {
            watchedRepo.getWatchedMovie(550L, MediaType.MOVIE).getOrThrow()
        }
        assertEquals("emotional", updatedMovie?.reaction)

        // Custom Lists CRUD
        val createListRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            customListRepo.createList(
                name = "Mind Benders",
                description = "Movies to rewatch",
                initialItem = sampleMovieSummary(550L).toCustomListEntry(),
            )
        }
        assertTrue("Expected createList to succeed: ${createListRes.exceptionOrNull()}", createListRes.isSuccess)
        val listId = createListRes.getOrThrow()

        val observedLists = withTimeout(FLOW_TIMEOUT_MS) {
            customListRepo.observeLists(aliceUid).first { lists -> lists.any { it.id == listId } }
        }
        val createdList = observedLists.first { it.id == listId }
        assertEquals("Mind Benders", createdList.name)
        assertEquals(1, createdList.items.size)

        assertTrue(
            withTimeout(DEFAULT_TIMEOUT_MS) {
                customListRepo.renameList(listId, "Top Mind Benders")
            }.isSuccess
        )

        val toggleAdd = withTimeout(DEFAULT_TIMEOUT_MS) {
            customListRepo.toggleMovieInList(createdList, sampleTvSummary(1396L).toCustomListEntry())
        }
        assertTrue(toggleAdd.isSuccess)
        assertTrue(toggleAdd.getOrThrow())

        val refreshedLists = withTimeout(FLOW_TIMEOUT_MS) {
            customListRepo.observeLists(aliceUid).first { lists ->
                lists.firstOrNull { it.id == listId }?.name == "Top Mind Benders" &&
                    lists.firstOrNull { it.id == listId }?.items?.size == 2
            }
        }
        val updatedList = refreshedLists.first { it.id == listId }
        assertTrue(updatedList.containsMedia(1396L, MediaType.TV))

        val toggleRemove = withTimeout(DEFAULT_TIMEOUT_MS) {
            customListRepo.toggleMovieInList(updatedList, sampleTvSummary(1396L).toCustomListEntry())
        }
        assertTrue(toggleRemove.isSuccess)
        assertFalse(toggleRemove.getOrThrow())

        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { customListRepo.deleteList(listId) }.isSuccess)

        // Watchlist add, observe, remove
        val wlSummary = MovieSummary(
            movieId = 27205L,
            mediaType = MediaType.MOVIE,
            title = "Inception",
            posterPath = "/9gk7adHYeDvHkCSEqAvQNLV5Uge.jpg",
            backdropPath = null,
            releaseDate = "2010-07-16",
            rating = 8.4,
            overview = "Dreams within dreams.",
            originalLanguage = "en",
        )
        val addWl = withTimeout(DEFAULT_TIMEOUT_MS) { watchlistRepo.addToWatchlist(wlSummary) }
        assertTrue("Expected addToWatchlist to succeed: ${addWl.exceptionOrNull()}", addWl.isSuccess)

        val wlItems = withTimeout(FLOW_TIMEOUT_MS) {
            watchlistRepo.observeWatchlist(aliceUid).first { list -> list.any { it.movieId == 27205L } }
        }
        assertEquals(1, wlItems.count { it.movieId == 27205L })
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { watchlistRepo.removeFromWatchlist(27205L, MediaType.MOVIE) }.isSuccess)

        // Settings save and observe
        val savePref = withTimeout(DEFAULT_TIMEOUT_MS) {
            settingsRepo.saveAccentPreference(AccentPreset.BLUE, "#3B82F6")
        }
        assertTrue("Expected saveAccentPreference to succeed: ${savePref.exceptionOrNull()}", savePref.isSuccess)
        val observedPref = withTimeout(FLOW_TIMEOUT_MS) {
            settingsRepo.observeSettings(aliceUid).first { it != null }
        }
        assertEquals("blue", observedPref?.accentPreset)
        assertEquals("#3B82F6", observedPref?.accentHex)

        // Cleanup
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { watchedRepo.removeFromWatched(550L, MediaType.MOVIE) }.isSuccess)
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { watchedRepo.removeFromWatched(550L, MediaType.TV) }.isSuccess)
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { watchedRepo.removeFromWatched(603L, MediaType.MOVIE) }.isSuccess)
        assertNull(withTimeout(DEFAULT_TIMEOUT_MS) { watchedRepo.getWatchedMovie(550L, MediaType.TV).getOrThrow() })
    }

    @Test
    fun observeCollections_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val watchedRepo = WatchedMovieRepository(firestore, auth)
        val watchlistRepo = WatchlistRepository(firestore, auth)
        val customListRepo = CustomListRepository(firestore, auth)
        val settingsRepo = UserSettingsRepository(firestore, auth)

        var caughtWatched: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) { watchedRepo.observeWatchedMovies("some_uid").first() }
        } catch (e: Exception) {
            caughtWatched = e
        }
        assertNotNull(caughtWatched)
        assertEquals(
            "Unexpected exception: ${caughtWatched!!::class.simpleName}: ${caughtWatched.message}",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            caughtWatched.unwrapFirestoreException()?.code
        )

        var caughtWatchlist: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) { watchlistRepo.observeWatchlist("some_uid").first() }
        } catch (e: Exception) {
            caughtWatchlist = e
        }
        assertNotNull(caughtWatchlist)
        assertEquals(
            "Unexpected exception: ${caughtWatchlist!!::class.simpleName}: ${caughtWatchlist.message}",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            caughtWatchlist.unwrapFirestoreException()?.code
        )

        var caughtLists: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) { customListRepo.observeLists("some_uid").first() }
        } catch (e: Exception) {
            caughtLists = e
        }
        assertNotNull(caughtLists)
        assertEquals(
            "Unexpected exception: ${caughtLists!!::class.simpleName}: ${caughtLists.message}",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            caughtLists.unwrapFirestoreException()?.code
        )

        var caughtSettings: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) { settingsRepo.observeSettings("some_uid").first() }
        } catch (e: Exception) {
            caughtSettings = e
        }
        assertNotNull(caughtSettings)
        assertEquals(
            "Unexpected exception: ${caughtSettings!!::class.simpleName}: ${caughtSettings.message}",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            caughtSettings.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun crossUserReadAndWrite_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = WatchedMovieRepository(firestore, auth)
        val aliceWatchlist = WatchlistRepository(firestore, auth)
        val aliceCustomList = CustomListRepository(firestore, auth)
        val aliceSettings = UserSettingsRepository(firestore, auth)

        val aliceListId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.markAsWatched(sampleTvSummary(1396L)).getOrThrow()
            aliceWatchlist.addToWatchlist(sampleMovieSummary(550L)).getOrThrow()
            aliceSettings.saveAccentPreference(AccentPreset.PURPLE, "#A855F7").getOrThrow()
            aliceCustomList.createList("Alice Private List").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        var readException: FirebaseFirestoreException? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("watched").document("tv_1396")
                    .get()
                    .await()
            }
        } catch (e: FirebaseFirestoreException) {
            readException = e
        }
        assertNotNull("Expected cross-user read to fail", readException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, readException!!.code)

        var wlReadException: FirebaseFirestoreException? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("watchlist").document("550")
                    .get()
                    .await()
            }
        } catch (e: FirebaseFirestoreException) {
            wlReadException = e
        }
        assertNotNull("Expected cross-user watchlist read to fail", wlReadException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, wlReadException!!.code)

        var listReadException: FirebaseFirestoreException? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("lists").document(aliceListId)
                    .get()
                    .await()
            }
        } catch (e: FirebaseFirestoreException) {
            listReadException = e
        }
        assertNotNull("Expected cross-user custom list read to fail", listReadException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, listReadException!!.code)

        var settingsReadException: FirebaseFirestoreException? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("settings").document("preferences")
                    .get()
                    .await()
            }
        } catch (e: FirebaseFirestoreException) {
            settingsReadException = e
        }
        assertNotNull("Expected cross-user settings read to fail", settingsReadException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, settingsReadException!!.code)

        // Clean up Alice's documents so subsequent runs start cleanly
        signInTestUser(ALICE_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.removeFromWatched(1396L, MediaType.TV)
            aliceWatchlist.removeFromWatchlist(550L, MediaType.MOVIE)
            aliceCustomList.deleteList(aliceListId)
            Unit
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_cinelog@test.com"
        const val BOB_EMAIL = "bob_cinelog@test.com"
        const val DEFAULT_TIMEOUT_MS = 10000L
        const val FLOW_TIMEOUT_MS = 10000L
    }
}
