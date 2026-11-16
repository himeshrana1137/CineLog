package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.MediaType
import com.example.data.model.MovieDetails
import com.example.data.model.MovieSummary
import com.example.data.model.WatchedMovie
import com.example.data.model.firestoreWatchedDocId
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WatchedMovieRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun watchedCollection(uid: String) =
        db.collection("users").document(uid).collection("watched")

    fun observeWatchedMovies(userId: String): Flow<List<WatchedMovie>> = flow {
        val path = "users/$userId/watched"
        emitAll(
            watchedCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WatchedMovie::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw (firestoreError ?: error)
                }
        )
    }

    suspend fun getWatchedMovie(
        movieId: Long,
        mediaType: MediaType = MediaType.MOVIE,
    ): Result<WatchedMovie?> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docId = firestoreWatchedDocId(movieId, mediaType)
        val docRef = watchedCollection(uid).document(docId)
        return try {
            val snap = docRef.get().await()
            val movie = snap.toObject(
                WatchedMovie::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(movie)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun markAsWatched(
        movie: MovieSummary,
        details: MovieDetails? = null,
        watchedSeason: Int = 0,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docId = firestoreWatchedDocId(movie.movieId, movie.mediaType)
        val docRef = watchedCollection(uid).document(docId)
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("movieId", movie.movieId)
            put("mediaType", movie.mediaType.value)
            put("title", movie.title.take(300).ifBlank { "Untitled" })
            movie.posterPath?.takeIf { it.isNotBlank() }?.let { put("posterPath", it.take(500)) }
            put("releaseDate", movie.releaseDate.take(32))
            put("rating", movie.rating.coerceIn(0.0, 10.0))
            put("userRating", 0)
            put("note", "")
            put("overview", (details?.overview ?: movie.overview).take(4000))
            if (details != null) {
                put("genres", details.genres.take(10))
                put("runtime", details.runtime.coerceIn(0, 2000))
                put("numberOfSeasons", details.numberOfSeasons.coerceIn(0, 200))
                put("numberOfEpisodes", details.numberOfEpisodes.coerceIn(0, 50000))
            } else {
                put("numberOfSeasons", 0)
                put("numberOfEpisodes", 0)
            }
            put("watchedSeason", watchedSeason.coerceIn(0, 200))
            put("watchedAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateRatingAndNote(
        movieId: Long,
        userRating: Int,
        note: String,
        mediaType: MediaType = MediaType.MOVIE,
        watchedSeason: Int = 0,
        details: MovieDetails? = null,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docId = firestoreWatchedDocId(movieId, mediaType)
        val docRef = watchedCollection(uid).document(docId)
        val updates = buildMap<String, Any> {
            put("userRating", userRating.coerceIn(0, 5))
            put("note", note.trim().take(1000))
            put("watchedSeason", watchedSeason.coerceIn(0, 200))
            if (details != null) {
                put("overview", details.overview.take(4000))
                put("genres", details.genres.take(10))
                put("runtime", details.runtime.coerceIn(0, 2000))
                put("numberOfSeasons", details.numberOfSeasons.coerceIn(0, 200))
                put("numberOfEpisodes", details.numberOfEpisodes.coerceIn(0, 50000))
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }

        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun removeFromWatched(
        movieId: Long,
        mediaType: MediaType = MediaType.MOVIE,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docId = firestoreWatchedDocId(movieId, mediaType)
        val docRef = watchedCollection(uid).document(docId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }
}
