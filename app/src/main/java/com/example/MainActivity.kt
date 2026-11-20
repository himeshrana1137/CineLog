package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.authStateFlow
import com.example.data.firebase.REAUTH_REQUIRED_MESSAGE
import com.example.data.repository.UserSettingsRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.MainCinemaScreen
import com.example.ui.theme.CineLogTheme
import com.example.ui.theme.parseHexColorOrDefault
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
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

            CineLogTheme(accentColor = parseHexColorOrDefault(accentHex)) {
                AppContent(
                    onAccentHexChanged = { newHex ->
                        if (!newHex.equals(accentHex, ignoreCase = true)) {
                            accentHex = newHex
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun AppContent(
    onAccentHexChanged: (String) -> Unit,
    auth: FirebaseAuth = Firebase.auth,
) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    var authNoticeMessage by remember { mutableStateOf<String?>(null) }
    var explicitLogoutInProgress by remember { mutableStateOf(false) }
    var hadAuthenticatedUser by remember { mutableStateOf(auth.currentUser != null) }

    LaunchedEffect(currentUser) {
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
        AuthScreen(
            initialMessage = authNoticeMessage,
            onMessageConsumed = { authNoticeMessage = null },
            onAuthSuccess = { authNoticeMessage = null },
        )
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
