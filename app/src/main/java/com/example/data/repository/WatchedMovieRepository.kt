package com.example.data.repository

import android.content.Context
import com.example.data.firebase.FirestoreProvider
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.MediaType
import com.example.data.model.MovieDetails
import com.example.data.model.MovieSummary
import com.example.data.model.QuickReaction
import com.example.data.model.WatchedMovie
import com.example.data.model.firestoreWatchedDocId
import com.example.data.model.todayIsoDate
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
import java.util.Locale

data class BatchWatchedImportItem(
    val summary: MovieSummary,
    val details: MovieDetails? = null,
    val userRating: Int = 0,
    val note: String = "",
    val reaction: String = "",
    val watchLog: List<String> = listOf(todayIsoDate()),
)

class WatchedMovieRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirestoreProvider.getInstance(context)
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
        reaction: String = "",
        watchDate: String = todayIsoDate(),
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docId = firestoreWatchedDocId(movie.movieId, movie.mediaType)
        val docRef = watchedCollection(uid).document(docId)
        val resolvedLang = (details?.originalLanguage?.takeIf { it.isNotBlank() }
            ?: movie.originalLanguage)
            .trim()
            .lowercase(Locale.US)
            .take(16)
        val safeReaction = QuickReaction.fromKey(reaction)?.key ?: ""
        val cleanDate = sanitizeIsoDate(watchDate) ?: todayIsoDate()

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
            put("reaction", safeReaction)
            put("originalLanguage", resolvedLang)
            put("overview", (details?.overview ?: movie.overview).take(4000))
            if (details != null) {
                put("genres", details.genres.filter { it.isNotBlank() }.take(10))
                put("cast", details.cast.map { it.name.trim() }.filter { it.isNotBlank() }.take(10))
                put("directors", details.directors.map { it.trim() }.filter { it.isNotBlank() }.take(10))
                put("runtime", details.runtime.coerceIn(0, 2000))
                put("numberOfSeasons", details.numberOfSeasons.coerceIn(0, 200))
                put("numberOfEpisodes", details.numberOfEpisodes.coerceIn(0, 50000))
            } else {
                put("genres", emptyList<String>())
                put("cast", emptyList<String>())
                put("directors", emptyList<String>())
                put("numberOfSeasons", 0)
                put("numberOfEpisodes", 0)
            }
            put("watchedSeason", watchedSeason.coerceIn(0, 200))
            put("watchLog", listOf(cleanDate))
            put("watchedAt", FieldValue.serverTimestamp())
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
                val existingModel = existingSnap.toObject(
                    WatchedMovie::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val mergedWatchLog = existingModel?.resolvedWatchDates.orEmpty()
                    .ifEmpty { listOf(cleanDate) }
                    .takeLast(500)
                val updates = buildMap<String, Any> {
                    put("title", movie.title.take(300).ifBlank { "Untitled" })
                    movie.posterPath?.takeIf { it.isNotBlank() }?.let { put("posterPath", it.take(500)) }
                    put("releaseDate", movie.releaseDate.take(32))
                    put("rating", movie.rating.coerceIn(0.0, 10.0))
                    if (safeReaction.isNotEmpty()) {
                        put("reaction", safeReaction)
                    }
                    put("originalLanguage", resolvedLang)
                    put("overview", (details?.overview ?: movie.overview).take(4000))
                    if (details != null) {
                        put("genres", details.genres.filter { it.isNotBlank() }.take(10))
                        put("cast", details.cast.map { it.name.trim() }.filter { it.isNotBlank() }.take(10))
                        put("directors", details.directors.map { it.trim() }.filter { it.isNotBlank() }.take(10))
                        put("runtime", details.runtime.coerceIn(0, 2000))
                        put("numberOfSeasons", details.numberOfSeasons.coerceIn(0, 200))
                        put("numberOfEpisodes", details.numberOfEpisodes.coerceIn(0, 50000))
                    }
                    if (watchedSeason > 0) {
                        put("watchedSeason", watchedSeason.coerceIn(0, 200))
                    }
                    put("watchLog", mergedWatchLog)
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

    suspend fun logRewatch(
        movieId: Long,
        mediaType: MediaType,
        watchDate: String = todayIsoDate(),
        currentLog: List<String> = emptyList(),
    ): Result<List<String>> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docId = firestoreWatchedDocId(movieId, mediaType)
        val docRef = watchedCollection(uid).document(docId)
        val cleanDate = sanitizeIsoDate(watchDate) ?: todayIsoDate()

        return try {
            val baseLog = if (currentLog.isNotEmpty()) {
                currentLog.mapNotNull { sanitizeIsoDate(it) }
            } else {
                val snap = docRef.get().await()
                val existing = snap.toObject(
                    WatchedMovie::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                existing?.resolvedWatchDates.orEmpty()
            }
            val updatedLog = (baseLog + cleanDate).takeLast(500)
            docRef.update(
                mapOf(
                    "watchLog" to updatedLog,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(updatedLog)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateWatchLog(
        movieId: Long,
        mediaType: MediaType,
        watchLog: List<String>,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docId = firestoreWatchedDocId(movieId, mediaType)
        val docRef = watchedCollection(uid).document(docId)
        val cleaned = watchLog.mapNotNull { sanitizeIsoDate(it) }.takeLast(500)
        return try {
            docRef.update(
                mapOf(
                    "watchLog" to cleaned,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun importBatch(items: List<BatchWatchedImportItem>): Result<Int> {
        if (items.isEmpty()) return Result.success(0)
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val col = watchedCollection(uid)
        var saved = 0
        return try {
            val existingDocs = try {
                col.get().await().documents.associateBy { it.id }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyMap()
            }
            items.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { item ->
                    val movie = item.summary
                    val details = item.details
                    val docId = firestoreWatchedDocId(movie.movieId, movie.mediaType)
                    val docRef = col.document(docId)
                    val resolvedLang = (details?.originalLanguage?.takeIf { it.isNotBlank() }
                        ?: movie.originalLanguage)
                        .trim()
                        .lowercase(Locale.US)
                        .take(16)
                    val safeReaction = QuickReaction.fromKey(item.reaction)?.key ?: ""
                    val cleanLog = item.watchLog.mapNotNull { sanitizeIsoDate(it) }
                        .ifEmpty { listOf(todayIsoDate()) }
                        .takeLast(500)

                    val existingSnap = existingDocs[docId]
                    if (existingSnap != null && existingSnap.exists()) {
                        val existingModel = existingSnap.toObject(
                            WatchedMovie::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        val mergedLog = (existingModel?.resolvedWatchDates.orEmpty() + cleanLog)
                            .distinct()
                            .sorted()
                            .takeLast(500)
                        val updates = buildMap<String, Any> {
                            put("title", movie.title.take(300).ifBlank { "Untitled" })
                            movie.posterPath?.takeIf { it.isNotBlank() }?.let { put("posterPath", it.take(500)) }
                            put("releaseDate", movie.releaseDate.take(32))
                            put("rating", movie.rating.coerceIn(0.0, 10.0))
                            if (item.userRating > 0) {
                                put("userRating", item.userRating.coerceIn(0, 5))
                            }
                            if (item.note.isNotBlank()) {
                                put("note", item.note.trim().take(1000))
                            }
                            if (safeReaction.isNotEmpty()) {
                                put("reaction", safeReaction)
                            }
                            put("originalLanguage", resolvedLang)
                            put("overview", (details?.overview ?: movie.overview).take(4000))
                            if (details != null) {
                                put("genres", details.genres.filter { it.isNotBlank() }.take(10))
                                put("cast", details.cast.map { it.name.trim() }.filter { it.isNotBlank() }.take(10))
                                put("directors", details.directors.map { it.trim() }.filter { it.isNotBlank() }.take(10))
                                put("runtime", details.runtime.coerceIn(0, 2000))
                                put("numberOfSeasons", details.numberOfSeasons.coerceIn(0, 200))
                                put("numberOfEpisodes", details.numberOfEpisodes.coerceIn(0, 50000))
                            }
                            put("watchLog", mergedLog)
                            put("updatedAt", FieldValue.serverTimestamp())
                        }
                        batch.update(docRef, updates)
                    } else {
                        val payload = buildMap<String, Any> {
                            put("userId", uid)
                            put("movieId", movie.movieId)
                            put("mediaType", movie.mediaType.value)
                            put("title", movie.title.take(300).ifBlank { "Untitled" })
                            movie.posterPath?.takeIf { it.isNotBlank() }?.let { put("posterPath", it.take(500)) }
                            put("releaseDate", movie.releaseDate.take(32))
                            put("rating", movie.rating.coerceIn(0.0, 10.0))
                            put("userRating", item.userRating.coerceIn(0, 5))
                            put("note", item.note.trim().take(1000))
                            put("reaction", safeReaction)
                            put("originalLanguage", resolvedLang)
                            put("overview", (details?.overview ?: movie.overview).take(4000))
                            put("genres", details?.genres?.filter { it.isNotBlank() }?.take(10).orEmpty())
                            put("cast", details?.cast?.map { it.name.trim() }?.filter { it.isNotBlank() }?.take(10).orEmpty())
                            put("directors", details?.directors?.map { it.trim() }?.filter { it.isNotBlank() }?.take(10).orEmpty())
                            put("runtime", (details?.runtime ?: 0).coerceIn(0, 2000))
                            put("numberOfSeasons", (details?.numberOfSeasons ?: 0).coerceIn(0, 200))
                            put("numberOfEpisodes", (details?.numberOfEpisodes ?: 0).coerceIn(0, 50000))
                            put("watchedSeason", 0)
                            put("watchLog", cleanLog)
                            put("watchedAt", FieldValue.serverTimestamp())
                            put("updatedAt", FieldValue.serverTimestamp())
                        }
                        batch.set(docRef, payload)
                    }
                }
                batch.commit().await()
                saved += chunk.size
            }
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/watched", auth)
            Result.failure(e)
        }
    }

    suspend fun updateRatingAndNote(
        movieId: Long,
        userRating: Int,
        note: String,
        mediaType: MediaType = MediaType.MOVIE,
        watchedSeason: Int = 0,
        reaction: String? = null,
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
            if (reaction != null) {
                put("reaction", QuickReaction.fromKey(reaction)?.key ?: "")
            }
            if (details != null) {
                put("overview", details.overview.take(4000))
                put("genres", details.genres.filter { it.isNotBlank() }.take(10))
                put("cast", details.cast.map { it.name.trim() }.filter { it.isNotBlank() }.take(10))
                put("directors", details.directors.map { it.trim() }.filter { it.isNotBlank() }.take(10))
                if (details.originalLanguage.isNotBlank()) {
                    put("originalLanguage", details.originalLanguage.trim().lowercase(Locale.US).take(16))
                }
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

    suspend fun updateReaction(
        movieId: Long,
        mediaType: MediaType,
        reaction: String,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docId = firestoreWatchedDocId(movieId, mediaType)
        val docRef = watchedCollection(uid).document(docId)
        val safeReaction = QuickReaction.fromKey(reaction)?.key ?: ""
        val updates = mapOf(
            "reaction" to safeReaction,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
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

    suspend fun enrichWatchedMetadata(
        movieId: Long,
        mediaType: MediaType,
        details: MovieDetails,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docId = firestoreWatchedDocId(movieId, mediaType)
        val docRef = watchedCollection(uid).document(docId)
        val updates = buildMap<String, Any> {
            put("runtime", details.runtime.coerceIn(0, 2000))
            put("originalLanguage", details.originalLanguage.trim().lowercase(Locale.US).take(16))
            put("cast", details.cast.map { it.name.trim() }.filter { it.isNotBlank() }.take(10))
            put("directors", details.directors.map { it.trim() }.filter { it.isNotBlank() }.take(10))
            put("genres", details.genres.filter { it.isNotBlank() }.take(10))
            if (details.overview.isNotBlank()) {
                put("overview", details.overview.take(4000))
            }
            put("numberOfSeasons", details.numberOfSeasons.coerceIn(0, 200))
            put("numberOfEpisodes", details.numberOfEpisodes.coerceIn(0, 50000))
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

    companion object {
        private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        fun sanitizeIsoDate(raw: String?): String? {
            val trimmed = raw?.trim()?.take(10) ?: return null
            return if (ISO_DATE_REGEX.matches(trimmed)) trimmed else null
        }
    }
}
