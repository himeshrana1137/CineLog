package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.firebase.FirestoreProvider
import com.example.data.firebase.FirestoreSnapshotState
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.firebase.observeAuthGatedFirestoreStream
import com.example.data.firebase.verifyReadOnlyServerFetch
import com.example.data.model.CustomList
import com.example.data.model.CustomListEntry
import com.example.data.model.MediaType
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
import java.util.UUID

class CustomListRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirestoreProvider.getInstance(context)
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun listsCollection(uid: String) =
        db.collection("users").document(uid).collection("lists")

    private val _retryTrigger = MutableStateFlow(0)
    val retryTrigger: StateFlow<Int> = _retryTrigger.asStateFlow()

    fun triggerRetry() {
        _retryTrigger.update { it + 1 }
    }

    fun observeListsForAuthUser(): Flow<FirestoreSnapshotState<List<CustomList>>> =
        observeAuthGatedFirestoreStream(
            auth = auth,
            retryTrigger = retryTrigger,
            streamName = "lists",
            operationType = OperationType.LIST,
            pathForUser = { uid -> "users/$uid/lists" },
            snapshotFlowForUser = { uid ->
                listsCollection(uid)
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snapshot ->
                        val lists = snapshot.toObjects(
                            CustomList::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        lists to snapshot.metadata.isFromCache
                    }
            },
        )

    suspend fun verifyServerAccess(userId: String): Result<Unit> =
        verifyReadOnlyServerFetch(
            streamName = "lists",
            path = "users/$userId/lists",
            operationType = OperationType.LIST,
            auth = auth,
        ) {
            listsCollection(userId).limit(1).get(Source.SERVER).await()
        }

    fun observeLists(userId: String): Flow<List<CustomList>> = flow {
        val path = "users/$userId/lists"
        emitAll(
            listsCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CustomList::class.java,
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
                        "observeLists error path=$path: ${(exceptionToLog ?: error)::class.java.name}: ${(exceptionToLog ?: error).message}",
                        exceptionToLog ?: error,
                    )
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw (firestoreError ?: error)
                }
        )
    }

    suspend fun createList(
        name: String,
        description: String = "",
        initialItem: CustomListEntry? = null,
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanName = name.trim().take(120)
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("List name cannot be empty."))
        }
        val docId = "list_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = listsCollection(uid).document(docId)
        val entries = if (initialItem != null) listOf(initialItem.toFirestoreMap()) else emptyList()
        val payload = mapOf(
            "userId" to uid,
            "name" to cleanName,
            "description" to description.trim().take(500),
            "items" to entries,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun renameList(
        listId: String,
        newName: String,
        newDescription: String? = null,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanName = newName.trim().take(120)
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("List name cannot be empty."))
        }
        val docRef = listsCollection(uid).document(listId)
        val updates = buildMap<String, Any> {
            put("name", cleanName)
            if (newDescription != null) {
                put("description", newDescription.trim().take(500))
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

    suspend fun toggleMovieInList(
        list: CustomList,
        entry: CustomListEntry,
    ): Result<Boolean> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = listsCollection(uid).document(list.id)
        val exists = list.items.any { it.uniqueKey == entry.uniqueKey }
        val updatedItems = if (exists) {
            list.items.filterNot { it.uniqueKey == entry.uniqueKey }
        } else {
            (list.items + entry).take(300)
        }
        return try {
            docRef.update(
                mapOf(
                    "items" to updatedItems.map { it.toFirestoreMap() },
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(!exists)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun removeMovieFromList(
        list: CustomList,
        movieId: Long,
        mediaType: MediaType,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val targetKey = "${mediaType.value}_$movieId"
        val docRef = listsCollection(uid).document(list.id)
        val updatedItems = list.items.filterNot { it.uniqueKey == targetKey }
        return try {
            docRef.update(
                mapOf(
                    "items" to updatedItems.map { it.toFirestoreMap() },
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

    suspend fun deleteList(listId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = listsCollection(uid).document(listId)
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
        private const val TAG = "CustomListRepo"
    }
}
