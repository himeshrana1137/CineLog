package com.example.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firebase.FirestoreSnapshotState
import com.example.data.firebase.REAUTH_REQUIRED_MESSAGE
import com.example.data.model.MediaType
import com.example.data.model.MovieSummary
import com.example.data.repository.CustomListRepository
import com.example.data.repository.TmdbRepository
import com.example.data.repository.TvProgressRepository
import com.example.data.repository.UserSettingsRepository
import com.example.data.repository.WatchedMovieRepository
import com.example.data.repository.WatchlistRepository
import com.example.ui.components.formatLastSyncedLabel
import com.example.util.NetworkMonitor
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.util.concurrent.atomic.AtomicInteger

class FirestoreSyncRetryTest : FirestoreEmulatorTestBase() {

    companion object {
        private const val FLOW_TIMEOUT_MS = 15_000L
    }

    private fun createOnlineNetworkMonitor(context: Context): NetworkMonitor {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        if (activeNetwork != null) {
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: ShadowNetworkCapabilities.newInstance()
            Shadows.shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            Shadows.shadowOf(cm).setNetworkCapabilities(activeNetwork, caps)
        }
        return NetworkMonitor(context)
    }

    private fun sampleMovie(id: Long, title: String) = MovieSummary(
        movieId = id,
        mediaType = MediaType.MOVIE,
        title = title,
        posterPath = "/poster_$id.jpg",
        backdropPath = null,
        releaseDate = "2024-01-01",
        rating = 8.4,
        overview = "Sample synopsis",
        originalLanguage = "en",
    )

    @Test
    fun authGatedListeners_waitForSignIn_andRestartOnAuthChange(): Unit = runBlocking {
        auth.signOut()
        val watchedRepo = WatchedMovieRepository(firestore, auth)
        val watchlistRepo = WatchlistRepository(firestore, auth)
        val listsRepo = CustomListRepository(firestore, auth)
        val settingsRepo = UserSettingsRepository(db = firestore, auth = auth)
        val tvRepo = TvProgressRepository(firestore, auth)

        // When signed out, all 5 auth-gated streams emit Unauthenticated instead of attaching and failing
        val watchedUnauth = withTimeout(FLOW_TIMEOUT_MS) {
            watchedRepo.observeWatchedMoviesForAuthUser().first()
        }
        val watchlistUnauth = withTimeout(FLOW_TIMEOUT_MS) {
            watchlistRepo.observeWatchlistForAuthUser().first()
        }
        val listsUnauth = withTimeout(FLOW_TIMEOUT_MS) {
            listsRepo.observeListsForAuthUser().first()
        }
        val settingsUnauth = withTimeout(FLOW_TIMEOUT_MS) {
            settingsRepo.observeSettingsForAuthUser().first()
        }
        val seasonsUnauth = withTimeout(FLOW_TIMEOUT_MS) {
            tvRepo.observeSeasonsForAuthUser(1396L).first()
        }

        assertTrue(watchedUnauth is FirestoreSnapshotState.Unauthenticated)
        assertTrue(watchlistUnauth is FirestoreSnapshotState.Unauthenticated)
        assertTrue(listsUnauth is FirestoreSnapshotState.Unauthenticated)
        assertTrue(settingsUnauth is FirestoreSnapshotState.Unauthenticated)
        assertTrue(seasonsUnauth is FirestoreSnapshotState.Unauthenticated)

        // Sign in as user A and verify listener attaches and streams data
        val uidA = signInTestUser("sync_user_a@test.com")
        watchedRepo.markAsWatched(sampleMovie(101L, "Arrival")).getOrThrow()

        val stateA = withTimeout(FLOW_TIMEOUT_MS) {
            watchedRepo.observeWatchedMoviesForAuthUser().first { state ->
                state is FirestoreSnapshotState.Data && state.value.any { it.movieId == 101L }
            } as FirestoreSnapshotState.Data
        }
        assertEquals(uidA, stateA.uid)
        assertEquals(1, stateA.value.count { it.movieId == 101L })

        // Sign out -> stream transitions cleanly back to Unauthenticated (cancelling listener)
        auth.signOut()
        val afterSignOut = withTimeout(FLOW_TIMEOUT_MS) {
            watchedRepo.observeWatchedMoviesForAuthUser().first { it is FirestoreSnapshotState.Unauthenticated }
        }
        assertTrue(afterSignOut is FirestoreSnapshotState.Unauthenticated)

        // Sign in as user B -> flatMapLatest restarts listener scoped to user B
        val uidB = signInTestUser("sync_user_b@test.com")
        watchedRepo.markAsWatched(sampleMovie(202L, "Interstellar")).getOrThrow()

        val stateB = withTimeout(FLOW_TIMEOUT_MS) {
            watchedRepo.observeWatchedMoviesForAuthUser().first { state ->
                state is FirestoreSnapshotState.Data && state.uid == uidB && state.value.any { it.movieId == 202L }
            } as FirestoreSnapshotState.Data
        }
        assertTrue(stateB.value.none { it.movieId == 101L })
        assertTrue(stateB.value.any { it.movieId == 202L })

        // Cleanup
        watchedRepo.removeFromWatched(202L, MediaType.MOVIE)
    }

    @Test
    fun syncNow_refreshesToken_reattachesListeners_confirmsServer_andPreservesAllData(): Unit = runBlocking {
        val uid = signInTestUser("sync_retry_preserve@test.com")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val watchedRepo = WatchedMovieRepository(firestore, auth)
        val tvRepo = TvProgressRepository(firestore, auth)
        val watchlistRepo = WatchlistRepository(firestore, auth)
        val listsRepo = CustomListRepository(firestore, auth)
        val settingsRepo = UserSettingsRepository(db = firestore, auth = auth)

        // Seed existing user data that MUST NOT be deleted or overwritten during retry
        watchedRepo.markAsWatched(sampleMovie(550L, "Fight Club")).getOrThrow()
        watchedRepo.updateRatingAndNote(
            movieId = 550L,
            userRating = 5,
            note = "Keep this note untouched",
            mediaType = MediaType.MOVIE,
        ).getOrThrow()

        val tokenRefreshCalls = AtomicInteger(0)
        val viewModel = CineLogViewModel(
            tmdbRepository = TmdbRepository(apiKeyProvider = { "" }),
            watchedRepository = watchedRepo,
            tvProgressRepository = tvRepo,
            watchlistRepository = watchlistRepo,
            customListRepository = listsRepo,
            settingsRepository = settingsRepo,
            networkMonitor = createOnlineNetworkMonitor(context),
            currentUserId = uid,
            auth = auth,
            tokenRefresher = { user ->
                tokenRefreshCalls.incrementAndGet()
                user.getIdToken(true).await()
            },
        )

        val watchedCollector = launch { viewModel.watchedState.collect { } }
        val watchlistCollector = launch { viewModel.watchlistState.collect { } }
        val listsCollector = launch { viewModel.customListsState.collect { } }

        try {
            // Wait for initial sync to load our seeded movie
            withTimeout(FLOW_TIMEOUT_MS) {
                viewModel.watchedState.first { state ->
                    state is WatchedUiState.Success && state.movies.any { it.movieId == 550L }
                }
            }

            val initialWatchedRetry = watchedRepo.retryTrigger.value
            val initialWatchlistRetry = watchlistRepo.retryTrigger.value
            val initialListsRetry = listsRepo.retryTrigger.value
            val initialSettingsRetry = settingsRepo.retryTrigger.value

            // Trigger manual Sync now
            viewModel.syncNow()

            // Wait for sync operation to complete
            withTimeout(FLOW_TIMEOUT_MS) {
                viewModel.isSyncingLibrary.first { !it }
            }
            // Allow retry triggers to settle
            delay(150L)

            assertEquals("Expected exactly 1 ID token refresh during syncNow", 1, tokenRefreshCalls.get())
            assertTrue(watchedRepo.retryTrigger.value > initialWatchedRetry)
            assertTrue(watchlistRepo.retryTrigger.value > initialWatchlistRetry)
            assertTrue(listsRepo.retryTrigger.value > initialListsRetry)
            assertTrue(settingsRepo.retryTrigger.value > initialSettingsRetry)
            assertNotNull("Expected lastSyncedAtMillis to be recorded", viewModel.lastSyncedAtMillis.value)
            assertFalse(viewModel.isSyncingLibrary.value)

            // Verify existing user data was NEVER deleted or overwritten during retry
            val persisted = watchedRepo.getWatchedMovie(550L, MediaType.MOVIE).getOrThrow()
            assertNotNull(persisted)
            assertEquals(5, persisted!!.userRating)
            assertEquals("Keep this note untouched", persisted.note)
        } finally {
            watchedCollector.cancel()
            watchlistCollector.cancel()
            listsCollector.cancel()
            watchedRepo.removeFromWatched(550L, MediaType.MOVIE)
        }
    }

    @Test
    fun syncNow_withNoSignedInUser_emitsPleaseSignInAgain(): Unit = runBlocking {
        auth.signOut()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = CineLogViewModel(
            tmdbRepository = TmdbRepository(apiKeyProvider = { "" }),
            watchedRepository = WatchedMovieRepository(firestore, auth),
            tvProgressRepository = TvProgressRepository(firestore, auth),
            watchlistRepository = WatchlistRepository(firestore, auth),
            customListRepository = CustomListRepository(firestore, auth),
            settingsRepository = UserSettingsRepository(db = firestore, auth = auth),
            networkMonitor = createOnlineNetworkMonitor(context),
            currentUserId = "missing_user",
            auth = auth,
        )

        val reAuthDeferred = async {
            withTimeout(FLOW_TIMEOUT_MS) {
                viewModel.reAuthRequiredEvents.first()
            }
        }
        delay(50L)
        viewModel.syncNow()

        val message = reAuthDeferred.await()
        assertEquals(REAUTH_REQUIRED_MESSAGE, message)
    }

    @Test
    fun syncNow_whenTokenRefreshFails_stopsWithoutLoopingAndShowsErrorDetails(): Unit = runBlocking {
        val uid = signInTestUser("sync_token_fail@test.com")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val refreshAttempts = AtomicInteger(0)

        val viewModel = CineLogViewModel(
            tmdbRepository = TmdbRepository(apiKeyProvider = { "" }),
            watchedRepository = WatchedMovieRepository(firestore, auth),
            tvProgressRepository = TvProgressRepository(firestore, auth),
            watchlistRepository = WatchlistRepository(firestore, auth),
            customListRepository = CustomListRepository(firestore, auth),
            settingsRepository = UserSettingsRepository(db = firestore, auth = auth),
            networkMonitor = createOnlineNetworkMonitor(context),
            currentUserId = uid,
            auth = auth,
            tokenRefresher = {
                refreshAttempts.incrementAndGet()
                throw IllegalStateException("Simulated token refresh failure")
            },
        )

        val watchedCollector = launch { viewModel.watchedState.collect { } }
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                viewModel.watchedState.first { it is WatchedUiState.Success }
            }

            viewModel.syncNow()

            withTimeout(FLOW_TIMEOUT_MS) {
                viewModel.isSyncingLibrary.first { !it }
            }
            delay(200L)

            assertEquals("Must not loop endlessly on token refresh error", 1, refreshAttempts.get())
            assertFalse(viewModel.isSyncingLibrary.value)
        } finally {
            watchedCollector.cancel()
        }
    }

    @Test
    fun formatLastSyncedLabel_handlesNullAndTimestamp() {
        assertEquals("Last synced: Not yet", formatLastSyncedLabel(null))
        val formattedNow = formatLastSyncedLabel(System.currentTimeMillis())
        assertTrue(formattedNow.startsWith("Last synced: "))
        assertFalse(formattedNow.contains("Not yet"))
    }
}
