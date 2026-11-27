package com.example

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.authStateFlow
import com.example.data.firebase.REAUTH_REQUIRED_MESSAGE
import com.example.data.repository.ThemePreferencesStore
import com.example.data.repository.UserSettingsRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.MainCinemaScreen
import com.example.ui.theme.CineLightBackground
import com.example.ui.theme.CineLogTheme
import com.example.ui.theme.CineObsidian
import com.example.ui.theme.parseHexColorOrDefault
import com.example.ui.theme.resolveDarkTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        ThemePreferencesStore.ensureLoaded(applicationContext)
        splashScreen.setKeepOnScreenCondition { !ThemePreferencesStore.isLoaded.value }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val context = LocalContext.current
            val initialHex = remember(context) {
                UserSettingsRepository(context).loadLocalAccent().hex
            }
            var accentHex by remember { mutableStateOf(initialHex) }
            val themeMode by ThemePreferencesStore.themeMode.collectAsStateWithLifecycle()
            var isAuthScreen by remember { mutableStateOf(Firebase.auth.currentUser == null) }

            val accentColor = remember(accentHex) { parseHexColorOrDefault(accentHex) }
            val isAppDark = resolveDarkTheme(themeMode)
            val effectiveBarsDark = isAppDark || isAuthScreen

            DisposableEffect(effectiveBarsDark) {
                val barStyle = if (effectiveBarsDark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        scrim = android.graphics.Color.TRANSPARENT,
                        darkScrim = android.graphics.Color.TRANSPARENT,
                    )
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle,
                )
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !effectiveBarsDark
                    isAppearanceLightNavigationBars = !effectiveBarsDark
                }
                val bgColor = if (effectiveBarsDark) CineObsidian.toArgb() else CineLightBackground.toArgb()
                window.setBackgroundDrawable(ColorDrawable(bgColor))
                onDispose {}
            }

            CineLogTheme(
                themeMode = themeMode,
                accentColor = accentColor,
            ) {
                AppContent(
                    accentColor = accentColor,
                    onAccentHexChanged = { newHex ->
                        if (!newHex.equals(accentHex, ignoreCase = true)) {
                            accentHex = newHex
                        }
                    },
                    onAuthScreenVisibleChanged = { visible ->
                        isAuthScreen = visible
                    },
                )
            }
        }
    }
}

@Composable
fun AppContent(
    accentColor: Color,
    onAccentHexChanged: (String) -> Unit,
    onAuthScreenVisibleChanged: (Boolean) -> Unit = {},
    auth: FirebaseAuth = Firebase.auth,
) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    var authNoticeMessage by remember { mutableStateOf<String?>(null) }
    var explicitLogoutInProgress by remember { mutableStateOf(false) }
    var hadAuthenticatedUser by remember { mutableStateOf(auth.currentUser != null) }

    LaunchedEffect(currentUser) {
        onAuthScreenVisibleChanged(currentUser == null)
        if (currentUser != null) {
            hadAuthenticatedUser = true
            explicitLogoutInProgress = false
            authNoticeMessage = null
        } else if (hadAuthenticatedUser) {
            if (!explicitLogoutInProgress && authNoticeMessage == null) {
                authNoticeMessage = REAUTH_REQUIRED_MESSAGE
            }
            hadAuthenticatedUser = false
            explicitLogoutInProgress = false
        }
    }

    val user = currentUser
    if (user == null) {
        CineLogTheme(
            accentColor = accentColor,
            forceDark = true,
        ) {
            AuthScreen(
                initialMessage = authNoticeMessage,
                onMessageConsumed = { authNoticeMessage = null },
                onAuthSuccess = { authNoticeMessage = null },
            )
        }
    } else {
        key(user.uid) {
            MainCinemaScreen(
                currentUser = user,
                onAccentHexChanged = onAccentHexChanged,
                auth = auth,
                onRequireReAuth = { message ->
                    authNoticeMessage = message.ifBlank { REAUTH_REQUIRED_MESSAGE }
                    explicitLogoutInProgress = false
                    if (auth.currentUser != null) {
                        auth.signOut()
                    }
                },
                onExplicitLogout = {
                    explicitLogoutInProgress = true
                    authNoticeMessage = null
                },
            )
        }
    }
}
