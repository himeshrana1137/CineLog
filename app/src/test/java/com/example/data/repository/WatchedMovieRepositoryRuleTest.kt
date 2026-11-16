package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
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

    private fun sampleSummary(movieId: Long = 550L) = MovieSummary(
        movieId = movieId,
        title = "Fight Club",
        posterPath = "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
        backdropPath = "/hZkgoQYus5vegHoetLkCJzb17zJ.jpg",
        releaseDate = "1999-10-15",
        rating = 8.4,
        overview = "An insomniac office worker forms an underground fight club."
    )

    @Test
    fun markAsWatched_andUpdateAndRemove_succeedsForAuthenticatedOwner() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = WatchedMovieRepository(firestore, auth)

        val markResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.markAsWatched(
                movie = sampleSummary(550L),
                details = MovieDetails(
                    movieId = 550L,
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
        assertTrue("Expected markAsWatched to succeed: ${markResult.exceptionOrNull()}", markResult.isSuccess)

        val watchedList = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeWatchedMovies(aliceUid).first { list -> list.any { it.movieId == 550L } }
        }
        val item = watchedList.first { it.movieId == 550L }
        assertEquals("550", item.id)
        assertEquals(aliceUid, item.userId)
        assertNotNull(item.watchedAt)

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateRatingAndNote(550L, userRating = 5, note = "All-time classic")
        }
        assertTrue("Expected updateRatingAndNote to succeed: ${updateResult.exceptionOrNull()}", updateResult.isSuccess)

        val updated = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getWatchedMovie(550L).getOrThrow()
        }
        assertNotNull(updated)
        assertEquals(5, updated!!.userRating)
        assertEquals("All-time classic", updated.note)

        val removeResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.removeFromWatched(550L)
        }
        assertTrue(removeResult.isSuccess)

        val afterDelete = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getWatchedMovie(550L).getOrThrow()
        }
        assertNull(afterDelete)
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
            aliceRepo.markAsWatched(sampleSummary(603L)).getOrThrow()
        }

        // Switch to Bob and try to read or overwrite Alice's document directly
        signInTestUser(BOB_EMAIL)
        var readException: FirebaseFirestoreException? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("watched").document("603")
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
