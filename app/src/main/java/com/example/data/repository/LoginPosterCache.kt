package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Single switch controlling which poster collection populates the Login screen backdrop.
 * Change [DEFAULT_LOGIN_POSTER_SOURCE] below to switch between MARVEL and TRENDING.
 */
enum class LoginPosterSource {
    TRENDING,
    MARVEL,
}

val DEFAULT_LOGIN_POSTER_SOURCE: LoginPosterSource = LoginPosterSource.MARVEL

/**
 * Single switch controlling how posters render on the Login screen background.
 * Change [DEFAULT_LOGIN_BACKGROUND_STYLE] below to switch between SLIDESHOW and POSTER_WALL.
 */
enum class LoginBackgroundStyle {
    SLIDESHOW,
    POSTER_WALL,
}

val DEFAULT_LOGIN_BACKGROUND_STYLE: LoginBackgroundStyle = LoginBackgroundStyle.SLIDESHOW

private val Context.loginPosterDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cinelog_login_poster_cache"
)

internal data class LoginPosterCacheKey(
    val source: LoginPosterSource,
    val style: LoginBackgroundStyle,
)

internal data class CachedPosterEntry(
    val urls: List<String>,
    val cachedAtMillis: Long,
)

internal class LoginPosterMemoryStore {
    private val entries = ConcurrentHashMap<LoginPosterCacheKey, CachedPosterEntry>()

    fun getIfFresh(
        source: LoginPosterSource,
        style: LoginBackgroundStyle,
        nowMillis: Long,
        ttlMillis: Long,
    ): List<String>? {
        val key = LoginPosterCacheKey(source, style)
        val entry = entries[key] ?: return null
        if (entry.urls.isEmpty()) return null
        if (nowMillis - entry.cachedAtMillis >= ttlMillis) {
            entries.remove(key, entry)
            return null
        }
        return entry.urls
    }

    fun put(
        source: LoginPosterSource,
        style: LoginBackgroundStyle,
        urls: List<String>,
        cachedAtMillis: Long,
    ) {
        if (urls.isEmpty()) return
        entries[LoginPosterCacheKey(source, style)] = CachedPosterEntry(
            urls = urls,
            cachedAtMillis = cachedAtMillis,
        )
    }

    fun clear() {
        entries.clear()
    }
}

internal class LoginPosterCache(
    private val memoryStore: LoginPosterMemoryStore = sharedMemoryStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val dataStoreProvider: ((Context) -> DataStore<Preferences>)? = null,
) {
    fun peekMemory(
        source: LoginPosterSource,
        style: LoginBackgroundStyle = DEFAULT_LOGIN_BACKGROUND_STYLE,
    ): List<String>? {
        return memoryStore.getIfFresh(
            source = source,
            style = style,
            nowMillis = clock(),
            ttlMillis = ttlMillis,
        )
    }

    suspend fun getCachedPosters(
        source: LoginPosterSource,
        style: LoginBackgroundStyle = DEFAULT_LOGIN_BACKGROUND_STYLE,
        context: Context?,
    ): List<String>? {
        val now = clock()
        memoryStore.getIfFresh(source = source, style = style, nowMillis = now, ttlMillis = ttlMillis)
            ?.let { return it }

        val dataStore = resolveDataStore(context) ?: return null
        return runCatching {
            val prefs = dataStore.data.first()
            val cachedAt = prefs[cachedAtKey(source, style)] ?: return null
            if (now - cachedAt >= ttlMillis) {
                return null
            }
            val rawUrls = prefs[urlsKey(source, style)].orEmpty()
            val urls = rawUrls
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (urls.isEmpty()) {
                null
            } else {
                memoryStore.put(source = source, style = style, urls = urls, cachedAtMillis = cachedAt)
                urls
            }
        }.getOrNull()
    }

    suspend fun savePosters(
        source: LoginPosterSource,
        style: LoginBackgroundStyle = DEFAULT_LOGIN_BACKGROUND_STYLE,
        urls: List<String>,
        context: Context?,
    ) {
        val cleaned = urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleaned.isEmpty()) return

        val now = clock()
        memoryStore.put(source = source, style = style, urls = cleaned, cachedAtMillis = now)

        val dataStore = resolveDataStore(context) ?: return
        runCatching {
            dataStore.edit { prefs ->
                prefs[urlsKey(source, style)] = cleaned.joinToString(separator = "\n")
                prefs[cachedAtKey(source, style)] = now
            }
        }
    }

    private fun resolveDataStore(context: Context?): DataStore<Preferences>? {
        val appContext = context?.applicationContext ?: return null
        return dataStoreProvider?.invoke(appContext) ?: appContext.loginPosterDataStore
    }

    companion object {
        val DEFAULT_TTL_MILLIS: Long = TimeUnit.HOURS.toMillis(24)
        internal val sharedMemoryStore = LoginPosterMemoryStore()

        private fun urlsKey(source: LoginPosterSource, style: LoginBackgroundStyle) =
            stringPreferencesKey("poster_urls_${source.name.lowercase()}_${style.name.lowercase()}")

        private fun cachedAtKey(source: LoginPosterSource, style: LoginBackgroundStyle) =
            longPreferencesKey("cached_at_${source.name.lowercase()}_${style.name.lowercase()}")
    }
}
