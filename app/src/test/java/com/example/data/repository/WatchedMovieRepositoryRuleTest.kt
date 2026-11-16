package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.MediaType
import com.example.data.model.MovieDetails
import com.example.data.model.MovieSummary
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
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
        overview = "An insomniac office worker forms an underground fight club."
    )

    private fun sampleTvSummary(tvId: Long = 1396L) = MovieSummary(
        movieId = tvId,
        mediaType = MediaType.TV,
        title = "Breaking Bad",
        posterPath = "/ggFHVNu6YYI5L9pCfOacjizRGt.jpg",
        backdropPath = null,
        releaseDate = "2008-01-20",
        rating = 8.9,
        overview = "A high school chemistry teacher turned methamphetamine manufacturer."
    )

    @Test
    fun markAsWatched_movieAndTvShow_coexistAndUpdateCleanly() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = WatchedMovieRepository(firestore, auth)

        // Same numeric ID (550) for a Movie and a TV show must not collide
        val movieResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.markAsWatched(
                movie = sampleMovieSummary(550L),
                details = MovieDetails(
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
                    cast = emptyList()
                )
            )
        }
        assertTrue("Expected movie markAsWatched to succeed: ${movieResult.exceptionOrNull()}", movieResult.isSuccess)

        val tvResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.markAsWatched(
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
                    cast = emptyList(),
                    numberOfSeasons = 5,
                    numberOfEpisodes = 62
                ),
                watchedSeason = 3
            )
        }
        assertTrue("Expected TV markAsWatched to succeed: ${tvResult.exceptionOrNull()}", tvResult.isSuccess)

        val watchedList = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeWatchedMovies(aliceUid).first { list -> list.size >= 2 }
        }
        assertTrue(watchedList.any { it.id == "550" && !it.isTv })
        assertTrue(watchedList.any { it.id == "tv_550" && it.isTv && it.watchedSeason == 3 })

        val updateTvResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateRatingAndNote(
                movieId = 550L,
                userRating = 5,
                note = "Season 4 & 5 are peak television.",
                mediaType = MediaType.TV,
                watchedSeason = 5
            )
        }
        assertTrue("Expected TV update to succeed: ${updateTvResult.exceptionOrNull()}", updateTvResult.isSuccess)

        val updatedTv = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getWatchedMovie(550L, MediaType.TV).getOrThrow()
        }
        assertNotNull(updatedTv)
        assertEquals(5, updatedTv!!.userRating)
        assertEquals(5, updatedTv.watchedSeason)
        assertEquals("tv", updatedTv.mediaType)

        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { repository.removeFromWatched(550L, MediaType.MOVIE) }.isSuccess)
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { repository.removeFromWatched(550L, MediaType.TV) }.isSuccess)
        assertNull(withTimeout(DEFAULT_TIMEOUT_MS) { repository.getWatchedMovie(550L, MediaType.TV).getOrThrow() })
    }

    @Test
    fun observeWatchedMovies_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = WatchedMovieRepository(firestore, auth)

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeWatchedMovies("some_uid").first()
            }
        } catch (e: Exception) {
            caught = e
        }
        assertNotNull("Expected unauthenticated query to throw", caught)
        val firestoreException = checkNotNull(caught!!.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun crossUserReadAndWrite_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = WatchedMovieRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.markAsWatched(sampleTvSummary(1396L)).getOrThrow()
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

        var listException: Exception? = null
        val bobRepo = WatchedMovieRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeWatchedMovies(aliceUid).first()
            }
        } catch (e: Exception) {
            listException = e
        }
        assertNotNull("Expected cross-user list to fail", listException)
        val fsEx = checkNotNull(listException!!.unwrapFirestoreException())
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_cinelog@test.com"
        const val BOB_EMAIL = "bob_cinelog@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
