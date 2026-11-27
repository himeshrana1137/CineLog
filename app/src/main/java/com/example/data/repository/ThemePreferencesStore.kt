package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

private val Context.themePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cinelog_theme_preferences",
)

/**
 * Local-only per-device DataStore holder for [ThemeMode].
 * Never syncs to Firestore so Firestore security rules remain untouched.
 *
 * Defaults to [ThemeMode.DARK] for users who have not explicitly selected a theme.
 */
object ThemePreferencesStore {
    private const val TAG = "ThemePreferencesStore"
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadStarted = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    fun ensureLoaded(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (!loadStarted.compareAndSet(false, true)) return

        scope.launch {
            try {
                val stored = app.themePreferencesDataStore.data
                    .catch { e ->
                        if (e is IOException) {
                            Log.e(TAG, "Failed to read theme DataStore: ${e.message}", e)
                            emit(emptyPreferences())
                        } else {
                            throw e
                        }
                    }
                    .map { prefs -> ThemeMode.fromKey(prefs[KEY_THEME_MODE]) }
                    .first()
                _themeMode.value = stored
            } catch (e: Exception) {
                Log.e(TAG, "Error loading stored ThemeMode: ${e::class.java.name}: ${e.message}", e)
                _themeMode.value = ThemeMode.DARK
            } finally {
                _isLoaded.value = true
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode, context: Context? = null) {
        _themeMode.value = mode
        _isLoaded.value = true
        val targetContext = context?.applicationContext ?: appContext ?: return
        appContext = targetContext
        try {
            targetContext.themePreferencesDataStore.edit { prefs ->
                prefs[KEY_THEME_MODE] = mode.key
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist ThemeMode=$mode: ${e::class.java.name}: ${e.message}", e)
        }
    }
}
