package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.firebase.FirestoreProvider
import com.example.data.firebase.FirestoreSnapshotState
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.firebase.observeAuthGatedFirestoreStream
import com.example.data.firebase.verifyReadOnlyServerFetch
import com.example.data.model.AccentPreset
import com.example.data.model.UserSettings
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
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

data class LocalAccentPreference(
    val preset: AccentPreset = AccentPreset.RED,
    val hex: String = AccentPreset.RED.defaultHex,
)

class UserSettingsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val prefs: SharedPreferences? = null,
) {
    constructor(context: Context) : this(
        db = FirestoreProvider.getInstance(context),
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun settingsDoc(uid: String) =
        db.collection("users").document(uid).collection("settings").document(PREFERENCES_DOC_ID)

    fun loadLocalAccent(): LocalAccentPreference {
        val presetKey = prefs?.getString(KEY_ACCENT_PRESET, AccentPreset.RED.key)
        val hex = prefs?.getString(KEY_ACCENT_HEX, AccentPreset.RED.defaultHex)
        val preset = AccentPreset.fromKey(presetKey)
        val normalizedHex = normalizeHexColor(hex) ?: preset.defaultHex
        return LocalAccentPreference(preset = preset, hex = normalizedHex)
    }

    fun saveLocalAccent(preset: AccentPreset, hex: String) {
        val normalized = normalizeHexColor(hex) ?: preset.defaultHex
        prefs?.edit()
            ?.putString(KEY_ACCENT_PRESET, preset.key)
            ?.putString(KEY_ACCENT_HEX, normalized)
            ?.apply()
    }

    private val _retryTrigger = MutableStateFlow(0)
    val retryTrigger: StateFlow<Int> = _retryTrigger.asStateFlow()

    fun triggerRetry() {
        _retryTrigger.update { it + 1 }
    }

    fun loadLastSyncedAtMillis(): Long? {
        val raw = prefs?.getLong(KEY_LAST_SYNCED_AT, -1L) ?: -1L
        return raw.takeIf { it > 0L }
    }

    fun saveLastSyncedAtMillis(timestampMillis: Long) {
        if (timestampMillis <= 0L) return
        prefs?.edit()?.putLong(KEY_LAST_SYNCED_AT, timestampMillis)?.apply()
    }

    fun observeSettingsForAuthUser(): Flow<FirestoreSnapshotState<UserSettings?>> =
        observeAuthGatedFirestoreStream(
            auth = auth,
            retryTrigger = retryTrigger,
            streamName = "settings",
            operationType = OperationType.GET,
            pathForUser = { uid -> "users/$uid/settings/$PREFERENCES_DOC_ID" },
            snapshotFlowForUser = { uid ->
                settingsDoc(uid)
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snapshot ->
                        val settings = snapshot.toObject(UserSettings::class.java)
                        settings to snapshot.metadata.isFromCache
                    }
            },
        )

    suspend fun verifyServerAccess(userId: String): Result<Unit> =
        verifyReadOnlyServerFetch(
            streamName = "settings",
            path = "users/$userId/settings/$PREFERENCES_DOC_ID",
            operationType = OperationType.GET,
            auth = auth,
        ) {
            settingsDoc(userId).get(Source.SERVER).await()
        }

    fun observeSettings(userId: String): Flow<UserSettings?> = flow {
        val path = "users/$userId/settings/$PREFERENCES_DOC_ID"
        emitAll(
            settingsDoc(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(UserSettings::class.java)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    Log.e(
                        TAG,
                        "observeSettings error path=$path: ${(exceptionToLog ?: error)::class.java.name}: ${(exceptionToLog ?: error).message}",
                        exceptionToLog ?: error,
                    )
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path, auth)
                    }
                    throw (firestoreError ?: error)
                }
        )
    }

    suspend fun saveAccentPreference(
        preset: AccentPreset,
        hex: String,
    ): Result<Unit> {
        val normalizedHex = normalizeHexColor(hex) ?: preset.defaultHex
        saveLocalAccent(preset, normalizedHex)

        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = settingsDoc(uid)
        val payload = mapOf(
            "userId" to uid,
            "accentPreset" to preset.key,
            "accentHex" to normalizedHex,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun markTvMigrationCompleted(): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = settingsDoc(uid)
        return try {
            val snap = try {
                docRef.get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (snap != null && snap.exists()) {
                docRef.update(
                    mapOf(
                        "tvMigrationCompleted" to true,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                val local = loadLocalAccent()
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "accentPreset" to local.preset.key,
                        "accentHex" to local.hex,
                        "tvMigrationCompleted" to true,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "UserSettingsRepo"
        private const val PREFS_NAME = "cinelog_user_preferences"
        private const val KEY_ACCENT_PRESET = "accent_preset"
        private const val KEY_ACCENT_HEX = "accent_hex"
        private const val KEY_LAST_SYNCED_AT = "last_synced_at_millis"
        const val PREFERENCES_DOC_ID = "preferences"

        private val HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

        fun normalizeHexColor(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim().let { if (it.startsWith("#")) it else "#$it" }
            return if (HEX_REGEX.matches(trimmed)) trimmed.uppercase() else null
        }
    }
}
