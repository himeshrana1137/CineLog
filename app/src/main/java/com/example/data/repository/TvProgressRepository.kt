package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.firebase.FirestoreProvider
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.MediaType
import com.example.data.model.MovieDetails
import com.example.data.model.MovieSummary
import com.example.data.model.TvComputedProgress
import com.example.data.model.WatchedMovie
import com.example.data.model.WatchedSeason
import com.example.data.model.firestoreWatchedDocId
import com.example.data.model.todayIsoDate
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class SeasonBatchWrite(
    val seasonNumber: Int,
    val episodeCount: Int,
    val watched: Boolean,
    val watchedEpisodes: List<Int>,
    val watchedRuntimeMinutes: Int,
)

class TvProgressRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirestoreProvider.getInstance(context)
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun watchedDocRef(uid: String, tvId: Long): DocumentReference =
        db.collection("users")
            .document(uid)
            .collection("watched")
            .document(firestoreWatchedDocId(tvId, MediaType.TV))

    private fun seasonsCollection(uid: String, tvId: Long) =
        watchedDocRef(uid, tvId).collection("seasons")

    fun observeSeasonsForShow(userId: String, tvId: Long): Flow<List<WatchedSeason>> = flow {
        val path = "users/$userId/watched/${firestoreWatchedDocId(tvId, MediaType.TV)}/seasons"
        emitAll(
            seasonsCollection(userId, tvId)
                .snapshots(MetadataChanges.INCLUDE)
                .filter { snapshot ->
                    val hasPending = snapshot.metadata.hasPendingWrites()
                    if (hasPending) {
                        Log.d(
                            TAG,
                            "observeSeasonsForShow ignoring pending snapshot tvId=$tvId count=${snapshot.size()}"
                        )
                    }
                    !hasPending
                }
                .map { snapshot ->
                    val seasons = snapshot.toObjects(
                        WatchedSeason::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.seasonNumber }
                    Log.d(
                        TAG,
                        "observeSeasonsForShow confirmed snapshot tvId=$tvId count=${seasons.size} fromCache=${snapshot.metadata.isFromCache}"
                    )
                    seasons
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    Log.e(
                        TAG,
                        "observeSeasonsForShow error tvId=$tvId path=$path message=${exceptionToLog?.message}",
                        exceptionToLog
                    )
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw (firestoreError ?: error)
                }
        )
    }

    suspend fun getSeasonsForShow(tvId: Long): Result<List<WatchedSeason>> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val path = "users/$uid/watched/${firestoreWatchedDocId(tvId, MediaType.TV)}/seasons"
        return try {
            val snap = seasonsCollection(uid, tvId).get().await()
            val seasons = snap.toObjects(
                WatchedSeason::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedBy { it.seasonNumber }
            Log.d(TAG, "getSeasonsForShow success tvId=$tvId count=${seasons.size}")
            Result.success(seasons)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "getSeasonsForShow failed tvId=$tvId code=${e.code} message=${e.message}", e)
            handleFirestoreError(e, OperationType.LIST, path, auth)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "getSeasonsForShow failed tvId=$tvId message=${e.message}", e)
            handleFirestoreError(e, OperationType.LIST, path, auth)
            Result.failure(e)
        }
    }

    suspend fun saveTvProgressBatch(
        summary: MovieSummary,
        details: MovieDetails?,
        existingWatched: WatchedMovie?,
        seasonWrites: List<SeasonBatchWrite>,
        deleteSeasonNumbers: List<Int> = emptyList(),
        progress: TvComputedProgress,
        existingSeasonIds: Set<Int> = emptySet(),
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val tvId = summary.movieId
        val parentRef = watchedDocRef(uid, tvId)
        val seasonsCol = seasonsCollection(uid, tvId)
        val seasonNumsLog = seasonWrites.map { it.seasonNumber }

        val resolvedLang = (details?.originalLanguage?.takeIf { it.isNotBlank() }
            ?: existingWatched?.originalLanguage?.takeIf { it.isNotBlank() }
            ?: summary.originalLanguage)
            .trim()
            .lowercase(Locale.US)
            .take(16)

        val totalSeasons = progress.totalSeasons.coerceIn(0, 200)
        val totalEpisodes = progress.totalEpisodes.coerceIn(0, 50000)

        Log.d(
            TAG,
            "saveTvProgressBatch start tvId=$tvId seasonNumber=$seasonNumsLog deletes=$deleteSeasonNumbers status=${progress.status} watchedEpisodes=${progress.watchedEpisodesCount}"
        )

        return try {
            val parentExists = parentRef.get().await().exists()

            val uniqueDeletes = deleteSeasonNumbers.distinct()
            val totalOps = uniqueDeletes.size + seasonWrites.size + 1

            if (totalOps <= 450) {
                // Single atomic batch for all season deletes/writes AND parent show document
                val batch = db.batch()
                uniqueDeletes.forEach { seasonNum ->
                    batch.delete(seasonsCol.document(seasonNum.toString()))
                }
                seasonWrites.forEach { write ->
                    enqueueSeasonSet(batch, uid, tvId, seasonsCol.document(write.seasonNumber.toString()), write)
                }
                enqueueParentWrite(
                    batch = batch,
                    uid = uid,
                    parentRef = parentRef,
                    parentExists = parentExists,
                    summary = summary,
                    details = details,
                    existingWatched = existingWatched,
                    resolvedLang = resolvedLang,
                    totalSeasons = totalSeasons,
                    totalEpisodes = totalEpisodes,
                    progress = progress,
                )
                batch.commit().await()
            } else {
                uniqueDeletes.chunked(380).forEach { delChunk ->
                    val batch = db.batch()
                    delChunk.forEach { seasonNum ->
                        batch.delete(seasonsCol.document(seasonNum.toString()))
                    }
                    batch.commit().await()
                }
                val writeChunks = seasonWrites.chunked(380)
                if (writeChunks.isEmpty()) {
                    val batch = db.batch()
                    enqueueParentWrite(
                        batch = batch,
                        uid = uid,
                        parentRef = parentRef,
                        parentExists = parentExists,
                        summary = summary,
                        details = details,
                        existingWatched = existingWatched,
                        resolvedLang = resolvedLang,
                        totalSeasons = totalSeasons,
                        totalEpisodes = totalEpisodes,
                        progress = progress,
                    )
                    batch.commit().await()
                } else {
                    writeChunks.forEachIndexed { chunkIdx, chunk ->
                        val batch = db.batch()
                        chunk.forEach { write ->
                            enqueueSeasonSet(
                                batch,
                                uid,
                                tvId,
                                seasonsCol.document(write.seasonNumber.toString()),
                                write
                            )
                        }
                        if (chunkIdx == writeChunks.lastIndex) {
                            enqueueParentWrite(
                                batch = batch,
                                uid = uid,
                                parentRef = parentRef,
                                parentExists = parentExists,
                                summary = summary,
                                details = details,
                                existingWatched = existingWatched,
                                resolvedLang = resolvedLang,
                                totalSeasons = totalSeasons,
                                totalEpisodes = totalEpisodes,
                                progress = progress,
                            )
                        }
                        batch.commit().await()
                    }
                }
            }

            Log.d(
                TAG,
                "saveTvProgressBatch success tvId=$tvId seasonNumber=$seasonNumsLog status=${progress.status} watchedEpisodes=${progress.watchedEpisodesCount}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            Log.e(
                TAG,
                "saveTvProgressBatch failure tvId=$tvId seasonNumber=$seasonNumsLog code=${e.code} message=${e.message}",
                e
            )
            handleFirestoreError(e, OperationType.WRITE, parentRef.path, auth)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "saveTvProgressBatch failure tvId=$tvId seasonNumber=$seasonNumsLog message=${e.message}",
                e
            )
            handleFirestoreError(e, OperationType.WRITE, parentRef.path, auth)
            Result.failure(e)
        }
    }

    private fun enqueueSeasonSet(
        batch: WriteBatch,
        uid: String,
        tvId: Long,
        docRef: DocumentReference,
        write: SeasonBatchWrite,
    ) {
        val cleanEpisodes = write.watchedEpisodes.filter { it > 0 }.distinct().sorted().take(2000)
        val payload = buildMap<String, Any?> {
            put("userId", uid)
            put("tvId", tvId)
            put("seasonNumber", write.seasonNumber.coerceIn(0, 200))
            put("episodeCount", write.episodeCount.coerceIn(0, 2000))
            put("watched", write.watched)
            put("watchedEpisodes", cleanEpisodes)
            put("watchedRuntimeMinutes", write.watchedRuntimeMinutes.coerceIn(0, 500000))
            put("watchedAt", if (write.watched) FieldValue.serverTimestamp() else null)
            put("updatedAt", FieldValue.serverTimestamp())
        }
        batch.set(docRef, payload)
    }

    private fun enqueueParentWrite(
        batch: WriteBatch,
        uid: String,
        parentRef: DocumentReference,
        parentExists: Boolean,
        summary: MovieSummary,
        details: MovieDetails?,
        existingWatched: WatchedMovie?,
        resolvedLang: String,
        totalSeasons: Int,
        totalEpisodes: Int,
        progress: TvComputedProgress,
    ) {
        if (parentExists) {
            val updates = buildMap<String, Any> {
                put("title", summary.title.take(300).ifBlank { "Untitled Series" })
                summary.posterPath?.takeIf { it.isNotBlank() }?.let { put("posterPath", it.take(500)) }
                put("releaseDate", summary.releaseDate.take(32))
                put("rating", summary.rating.coerceIn(0.0, 10.0))
                put("originalLanguage", resolvedLang)
                if (details != null) {
                    put("overview", details.overview.take(4000))
                    put("genres", details.genres.filter { it.isNotBlank() }.take(10))
                    put("cast", details.cast.map { it.name.trim() }.filter { it.isNotBlank() }.take(10))
                    put("directors", details.directors.map { it.trim() }.filter { it.isNotBlank() }.take(10))
                    put("runtime", details.runtime.coerceIn(0, 2000))
                }
                put("numberOfSeasons", totalSeasons)
                put("numberOfEpisodes", totalEpisodes)
                put("status", progress.status.key)
                put("watchedEpisodesCount", progress.watchedEpisodesCount.coerceIn(0, 50000))
                put("watchedSeasonsCount", progress.watchedSeasonsCount.coerceIn(0, 200))
                put("watchedRuntimeMinutes", progress.watchedRuntimeMinutes.coerceIn(0, 5000000))
                put("nextUpSeason", progress.nextUpSeason.coerceIn(0, 200))
                put("nextUpEpisode", progress.nextUpEpisode.coerceIn(0, 5000))
                put("currentSeason", progress.currentSeason.coerceIn(0, 200))
                put("updatedAt", FieldValue.serverTimestamp())
            }
            batch.update(parentRef, updates)
        } else {
            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("movieId", summary.movieId)
                put("mediaType", MediaType.TV.value)
                put("title", summary.title.take(300).ifBlank { "Untitled Series" })
                summary.posterPath?.takeIf { it.isNotBlank() }?.let { put("posterPath", it.take(500)) }
                put("releaseDate", summary.releaseDate.take(32))
                put("rating", summary.rating.coerceIn(0.0, 10.0))
                put("userRating", existingWatched?.userRating ?: 0)
                put("note", existingWatched?.note.orEmpty())
                put("reaction", existingWatched?.reaction.orEmpty())
                put("originalLanguage", resolvedLang)
                put("overview", (details?.overview ?: summary.overview).take(4000))
                put("genres", details?.genres?.filter { it.isNotBlank() }?.take(10).orEmpty())
                put("cast", details?.cast?.map { it.name.trim() }?.filter { it.isNotBlank() }?.take(10).orEmpty())
                put("directors", details?.directors?.map { it.trim() }?.filter { it.isNotBlank() }?.take(10).orEmpty())
                put("runtime", (details?.runtime ?: existingWatched?.runtime ?: 0).coerceIn(0, 2000))
                put("numberOfSeasons", totalSeasons)
                put("numberOfEpisodes", totalEpisodes)
                put("watchedSeason", 0)
                put("status", progress.status.key)
                put("watchedEpisodesCount", progress.watchedEpisodesCount.coerceIn(0, 50000))
                put("watchedSeasonsCount", progress.watchedSeasonsCount.coerceIn(0, 200))
                put("watchedRuntimeMinutes", progress.watchedRuntimeMinutes.coerceIn(0, 5000000))
                put("nextUpSeason", progress.nextUpSeason.coerceIn(0, 200))
                put("nextUpEpisode", progress.nextUpEpisode.coerceIn(0, 5000))
                put("currentSeason", progress.currentSeason.coerceIn(0, 200))
                put("watchLog", listOf(todayIsoDate()))
                put("watchedAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            batch.set(parentRef, payload)
        }
    }

    companion object {
        private const val TAG = "TvProgressRepo"

        fun formatSaveError(error: Throwable): String {
            val firestoreEx = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            return if (firestoreEx != null) {
                when (firestoreEx.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "Could not save: permission denied. Check Firestore rules."
                    FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                        "Could not save: please sign in again."
                    FirebaseFirestoreException.Code.UNAVAILABLE ->
                        "Could not save: network unavailable. Check your connection."
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                        "Could not save: request timed out. Please retry."
                    else ->
                        "Could not save: ${firestoreEx.localizedMessage ?: firestoreEx.code.name}"
                }
            } else {
                "Could not save: ${error.localizedMessage ?: error::class.simpleName ?: "unknown error"}"
            }
        }
    }
}
