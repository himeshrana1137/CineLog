package com.example.data.firebase

import android.util.Log
import com.example.auth.authStateFlow
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

const val REAUTH_REQUIRED_MESSAGE = "Please sign in again"
private const val SYNC_LOG_TAG = "CineLogFirestoreSync"

sealed interface FirestoreSnapshotState<out T> {
    data object Unauthenticated : FirestoreSnapshotState<Nothing>

    data class Loading(
        val uid: String,
        val retryCount: Int,
    ) : FirestoreSnapshotState<Nothing>

    data class Data<T>(
        val uid: String,
        val value: T,
        val isFromCache: Boolean,
        val retryCount: Int,
    ) : FirestoreSnapshotState<T>

    data class Error(
        val uid: String,
        val error: Throwable,
        val retryCount: Int,
    ) : FirestoreSnapshotState<Nothing>
}

fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
    generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()

fun isAuthSessionInvalidError(error: Throwable): Boolean {
    val causes = generateSequence(error) { it.cause }
    return causes.any { cause ->
        cause is FirebaseAuthInvalidUserException ||
            cause is FirebaseAuthInvalidCredentialsException ||
            (cause is FirebaseFirestoreException && cause.code == FirebaseFirestoreException.Code.UNAUTHENTICATED)
    }
}

fun formatTechnicalFirestoreError(error: Throwable, fallback: String = "Unknown Firestore error"): String {
    val root = unwrapFirestoreError(error) ?: error
    val className = root::class.java.simpleName.ifBlank { root::class.java.name }
    val codePart = (root as? FirebaseFirestoreException)?.code?.name?.let { " ($it)" }.orEmpty()
    val detailMsg = root.message?.takeIf { it.isNotBlank() } ?: fallback
    return "$className$codePart: $detailMsg"
}

/**
 * Auth-gated, retryable Firestore snapshot stream:
 * - Attaches the underlying Firestore listener ONLY when [auth] has a signed-in user.
 * - Cancels the active listener immediately on sign-out and restarts it on sign-in via [flatMapLatest].
 * - Re-subscribes cleanly whenever [retryTrigger] increments after an error or manual retry.
 * - Catches listener errors inside [flatMapLatest] so the outer auth/retry flow never terminates permanently.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> observeAuthGatedFirestoreStream(
    auth: FirebaseAuth,
    retryTrigger: StateFlow<Int>,
    streamName: String,
    operationType: OperationType,
    pathForUser: (uid: String) -> String,
    snapshotFlowForUser: (uid: String) -> Flow<Pair<T, Boolean>>,
): Flow<FirestoreSnapshotState<T>> {
    val authUidFlow = auth.authStateFlow()
        .map { it?.uid?.takeIf(String::isNotBlank) }
        .distinctUntilChanged()

    return combine(authUidFlow, retryTrigger) { uid, retryCount ->
        uid to retryCount
    }.flatMapLatest { (uid, retryCount) ->
        if (uid == null) {
            flowOf(FirestoreSnapshotState.Unauthenticated)
        } else {
            val path = pathForUser(uid)
            flow {
                emit(FirestoreSnapshotState.Loading(uid = uid, retryCount = retryCount))
                snapshotFlowForUser(uid).collect { (value, isFromCache) ->
                    emit(
                        FirestoreSnapshotState.Data(
                            uid = uid,
                            value = value,
                            isFromCache = isFromCache,
                            retryCount = retryCount,
                        )
                    )
                }
            }.catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val resolvedError = firestoreError ?: error
                Log.e(
                    SYNC_LOG_TAG,
                    "Firestore listener [$streamName] failed (path=$path, retryCount=$retryCount): " +
                        "${resolvedError::class.java.name}: ${resolvedError.message}",
                    resolvedError,
                )
                if (resolvedError is Exception && auth.currentUser?.uid == uid) {
                    handleFirestoreError(resolvedError, operationType, path, auth)
                }
                emit(
                    FirestoreSnapshotState.Error(
                        uid = uid,
                        error = resolvedError,
                        retryCount = retryCount,
                    )
                )
            }
        }
    }
}

/**
 * Runs a single read-only server fetch (never writes, updates, or deletes data) and logs any failure
 * with its exception class and message.
 */
suspend fun verifyReadOnlyServerFetch(
    streamName: String,
    path: String,
    operationType: OperationType,
    auth: FirebaseAuth,
    fetchBlock: suspend () -> Unit,
): Result<Unit> {
    return try {
        fetchBlock()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val root = unwrapFirestoreError(e) ?: e
        Log.e(
            SYNC_LOG_TAG,
            "Server fetch verification [$streamName] failed (path=$path): " +
                "${root::class.java.name}: ${root.message}",
            root,
        )
        handleFirestoreError(root, operationType, path, auth)
        Result.failure(root)
    }
}
