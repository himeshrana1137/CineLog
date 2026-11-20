package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.firebase.FirestoreProvider
import com.example.data.firebase.FirestoreSnapshotState
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.firebase.observeAuthGatedFirestoreStream
import com.example.data.firebase.verifyReadOnlyServerFetch
import com.example.data.model.MediaType
import com.example.data.model.MovieDetails
import com.example.data.model.MovieSummary
import com.example.data.model.WatchlistItem
import com.example.data.model.firestoreWatchedDocId
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import java.util.Locale

class WatchlistRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirestoreProvider.getInstance(context)
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun watchlistCollection(uid: String) =
        db.collection("users").document(uid).collection("watchlist")

    private val _retryTrigger = MutableStateFlow(0)
    val retryTrigger: StateFlow<Int> = _retryTrigger.asStateFlow()

    fun triggerRetry() {
        _retryTrigger.update { it + 1 }
    }

    fun observeWatchlistForAuthUser(): Flow<FirestoreSnapshotState<List<WatchlistItem>>> =
        observeAuthGatedFirestoreStream(
            auth = auth,
            retryTrigger = retryTrigger,
            streamName = "watchlist",
            operationType = OperationType.LIST,
            pathForUser = { uid -> "users/$uid/watchlist" },
            snapshotFlowForUser = { uid ->
                watchlistCollection(uid)
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snapshot ->
                        val items = snapshot.toObjects(
                            WatchlistItem::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        items to snapshot.metadata.isFromCache
                    }
            },
        )

    suspend fun verifyServerAccess(userId: String): Result<Unit> =
        verifyReadOnlyServerFetch(
            streamName = "watchlist",
            path = "users/$userId/watchlist",
            operationType = OperationType.LIST,
            auth = auth,
        ) {
            watchlistCollection(userId).limit(1).get(Source.SERVER).await()
        }

    fun observeWatchlist(userId: String): Flow<List<WatchlistItem>> = flow {
        val path = "users/$userId/watchlist"
        emitAll(
            watchlistCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WatchlistItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    Log.e(
                        TAG,
                        "observeWatchlist error path=$path: ${(exceptionToLog ?: error)::class.java.name}: ${(exceptionToLog ?: error).message}",
                        exceptionToLog ?: error,
                    )
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw (firestoreError ?: error)
                }
        )
    }

    suspend fun addToWatchlist(
        movie: MovieSummary,
        details: MovieDetails? = null,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docId = firestoreWatchedDocId(movie.movieId, movie.mediaType)
        val docRef = watchlistCollection(uid).document(docId)
        val resolvedLang = (details?.originalLanguage?.takeIf { it.isNotBlank() }
            ?: movie.originalLanguage)
            .trim()
            .lowercase(Locale.US)
            .take(16)

        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("movieId", movie.movieId)
            put("mediaType", movie.mediaType.value)
            put("title", movie.title.take(300).ifBlank { "Untitled" })
            movie.posterPath?.takeIf { it.isNotBlank() }?.let { put("posterPath", it.take(500)) }
            put("releaseDate", movie.releaseDate.take(32))
            put("rating", movie.rating.coerceIn(0.0, 10.0))
            put("overview", (details?.overview ?: movie.overview).take(4000))
            put("originalLanguage", resolvedLang)
            if (details != null) {
                put("genres", details.genres.filter { it.isNotBlank() }.take(10))
                put("runtime", details.runtime.coerceIn(0, 2000))
                put("numberOfSeasons", details.numberOfSeasons.coerceIn(0, 200))
                put("numberOfEpisodes", details.numberOfEpisodes.coerceIn(0, 50000))
            } else {
                put("runtime", 0)
                put("numberOfSeasons", 0)
                put("numberOfEpisodes", 0)
            }
            put("addedAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        return try {
            val existingSnap = try {
                docRef.get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (existingSnap != null && existingSnap.exists()) {
                val updates = buildMap<String, Any> {
                    put("title", movie.title.take(300).ifBlank { "Untitled" })
                    movie.posterPath?.takeIf { it.isNotBlank() }?.let { put("posterPath", it.take(500)) }
                    put("releaseDate", movie.releaseDate.take(32))
                    put("rating", movie.rating.coerceIn(0.0, 10.0))
                    put("overview", (details?.overview ?: movie.overview).take(4000))
                    put("originalLanguage", resolvedLang)
                    if (details != null) {
                        put("genres", details.genres.filter { it.isNotBlank() }.take(10))
                        put("runtime", details.runtime.coerceIn(0, 2000))
                        put("numberOfSeasons", details.numberOfSeasons.coerceIn(0, 200))
                        put("numberOfEpisodes", details.numberOfEpisodes.coerceIn(0, 50000))
                    }
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            } else {
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun removeFromWatchlist(
        movieId: Long,
        mediaType: MediaType = MediaType.MOVIE,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docId = firestoreWatchedDocId(movieId, mediaType)
        val docRef = watchlistCollection(uid).document(docId)
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

    companion object {
        private const val TAG = "WatchlistRepo"
    }
}
