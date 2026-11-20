package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.data.repository.DEFAULT_LOGIN_BACKGROUND_STYLE
import com.example.data.repository.DEFAULT_LOGIN_POSTER_SOURCE
import com.example.data.repository.LoginBackgroundStyle
import com.example.data.repository.TmdbRepository
import com.example.ui.components.CineLogLogo
import com.example.ui.components.CineLogLogoLayout
import com.example.ui.theme.CineCrimsonGlow
import com.example.ui.theme.CineGold
import com.example.ui.theme.CineObsidian
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class AuthFeatureSlide(
    val icon: ImageVector,
    val accentTint: Color,
    val title: String,
    val subtitle: String,
)

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    initialMessage: String? = null,
    onMessageConsumed: () -> Unit = {},
    tmdbRepository: TmdbRepository = remember { TmdbRepository() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var isLoading by remember { mutableStateOf(false) }
    var activeNotice by remember(initialMessage) { mutableStateOf(initialMessage) }
    var loginPosters by remember {
        mutableStateOf(
            tmdbRepository.peekMemoryCachedLoginPosters(
                source = DEFAULT_LOGIN_POSTER_SOURCE,
                style = DEFAULT_LOGIN_BACKGROUND_STYLE,
            ).orEmpty()
        )
    }
    var isScreenVisible by remember { mutableStateOf(true) }

    // Staggered entry animation flags
    var showHeadline by remember { mutableStateOf(false) }
    var showCards by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME,
                Lifecycle.Event.ON_START -> isScreenVisible = true
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> isScreenVisible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(initialMessage) {
        if (!initialMessage.isNullOrBlank()) {
            activeNotice = initialMessage
            snackbarHostState.showSnackbar(initialMessage)
        }
    }

    LaunchedEffect(Unit) {
        if (initialMessage.isNullOrBlank()) {
            launch {
                attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    scope = scope,
                    onAuthSuccess = onAuthSuccess,
                    onUnauthenticated = { /* stay on AuthScreen */ },
                )
            }
        }
        launch {
            val urls = runCatching {
                tmdbRepository.getLoginPosters(
                    source = DEFAULT_LOGIN_POSTER_SOURCE,
                    style = DEFAULT_LOGIN_BACKGROUND_STYLE,
                    context = context.applicationContext,
                )
            }.getOrDefault(emptyList())
            if (urls.isNotEmpty()) {
                loginPosters = urls
            }
        }
        delay(120L)
        showHeadline = true
        delay(80L)
        showCards = true
        delay(80L)
        showButton = true
    }

    val slides = remember(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary) {
        listOf(
            AuthFeatureSlide(
                icon = Icons.Rounded.Search,
                accentTint = CineCrimsonGlow,
                title = "Live TMDB Catalog",
                subtitle = "Search movies & TV series worldwide with trailers and cast.",
            ),
            AuthFeatureSlide(
                icon = Icons.Rounded.Star,
                accentTint = CineGold,
                title = "Personal Ratings & Notes",
                subtitle = "Score 1–5 stars, reactions, and memorable quotes.",
            ),
            AuthFeatureSlide(
                icon = Icons.Rounded.CloudDone,
                accentTint = Color(0xFF34D399),
                title = "Realtime Cloud Library",
                subtitle = "Instant watchlist, diary & episode sync tied to Google.",
            ),
        )
    }

    val pagerState = rememberPagerState(pageCount = { slides.size })
    LaunchedEffect(isScreenVisible, pagerState.pageCount) {
        while (isScreenVisible) {
            delay(3000L)
            if (isScreenVisible && !pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % slides.size
                pagerState.animateScrollToPage(
                    page = next,
                    animationSpec = tween(durationMillis = 500),
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // 1. Cinematic Poster Slideshow / Poster Wall Background (or soft red glow fallback)
            LoginBackdrop(
                posterUrls = loginPosters,
                style = DEFAULT_LOGIN_BACKGROUND_STYLE,
                animate = isScreenVisible,
                accentColor = MaterialTheme.colorScheme.primary,
                backgroundColor = MaterialTheme.colorScheme.background,
            )

            // Main scrollable content with safe systemBars insets
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.widthIn(max = 460.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 2. Hero Section - Animated CineLog Logo + Glow
                    CineLogLogo(
                        size = 72.dp,
                        showWordmark = true,
                        animated = true,
                        wordmarkFontSize = 34.sp,
                        layout = CineLogLogoLayout.Vertical,
                        showGlow = true,
                        modifier = Modifier.testTag("login_logo"),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Headline + Tagline + One-line subtitle
                    AnimatedVisibility(
                        visible = showHeadline,
                        enter = fadeIn(tween(320)) + slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = tween(320),
                        ),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.brand_tagline),
                                style = MaterialTheme.typography.labelMedium,
                                color = CineGold,
                                letterSpacing = 2.4.sp,
                                textAlign = TextAlign.Center,
                            )

                            val accentStart = MaterialTheme.colorScheme.primary
                            val accentEnd = CineCrimsonGlow
                            val headlineAnnotated = remember(accentStart, accentEnd) {
                                buildAnnotatedString {
                                    append("Track ")
                                    withStyle(
                                        SpanStyle(
                                            brush = Brush.linearGradient(
                                                colors = listOf(accentStart, accentEnd)
                                            ),
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                    ) {
                                        append("every film")
                                    }
                                    append(" you watch")
                                }
                            }

                            Text(
                                text = headlineAnnotated,
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 42.sp,
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                            )

                            Text(
                                text = "Movies, series & reviews synced in your pocket.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3. Feature Highlight Carousel (Glassmorphism Cards + Dots)
                    AnimatedVisibility(
                        visible = showCards,
                        enter = fadeIn(tween(320)) + slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = tween(320),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                pageSpacing = 12.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_feature_pager"),
                            ) { page ->
                                GlassFeatureCard(slide = slides[page])
                            }

                            // Dot indicators
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                repeat(slides.size) { index ->
                                    val selected = pagerState.currentPage == index
                                    val width by animateDpAsState(
                                        targetValue = if (selected) 22.dp else 7.dp,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        label = "dot_width",
                                    )
                                    Box(
                                        modifier = Modifier
                                            .height(7.dp)
                                            .width(width)
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    Color.White.copy(alpha = 0.28f)
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(26.dp))

                    // 4. Google Sign-In Button + Footer
                    AnimatedVisibility(
                        visible = showButton,
                        enter = fadeIn(tween(320)) + slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = tween(320),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            activeNotice?.takeIf { it.isNotBlank() }?.let { noticeText ->
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.65f),
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .testTag("auth_notice_banner"),
                                ) {
                                    Text(
                                        text = noticeText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    )
                                }
                            }

                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val buttonScale by animateFloatAsState(
                                targetValue = if (isPressed && !isLoading) 0.97f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                                label = "google_btn_scale",
                            )

                            Button(
                                onClick = {
                                    if (isLoading) return@Button
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isLoading = true
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onAuthSuccess = {
                                            isLoading = false
                                            activeNotice = null
                                            onMessageConsumed()
                                            onAuthSuccess()
                                        },
                                        onAuthError = { msg ->
                                            isLoading = false
                                            val display = msg.ifBlank { "Sign-in failed, try again" }
                                            scope.launch {
                                                snackbarHostState.showSnackbar(display)
                                            }
                                        },
                                        onAuthCancelled = {
                                            isLoading = false
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Sign-in cancelled, try again.")
                                            }
                                        }
                                    )
                                },
                                enabled = !isLoading,
                                interactionSource = interactionSource,
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = CineObsidian,
                                    disabledContainerColor = Color.White.copy(alpha = 0.72f),
                                    disabledContentColor = CineObsidian,
                                ),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 6.dp,
                                    pressedElevation = 2.dp,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .graphicsLayer {
                                        scaleX = buttonScale
                                        scaleY = buttonScale
                                    }
                                    .testTag("google_sign_in_button"),
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = CineObsidian,
                                        strokeWidth = 2.5.dp,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.signing_in),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                } else {
                                    GoogleGLogo(modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.continue_with_google),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Secured with Firebase Auth and Google Sign-In",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Terms and Privacy",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun LoginBackdrop(
    posterUrls: List<String>,
    style: LoginBackgroundStyle,
    animate: Boolean,
    accentColor: Color,
    backgroundColor: Color,
) {
    val cleanPosters = remember(posterUrls) {
        posterUrls.filter { it.isNotBlank() }.distinct()
    }
    if (cleanPosters.isEmpty()) {
        LoginFallbackGlowBackground(
            accentColor = accentColor,
            backgroundColor = backgroundColor,
        )
        return
    }

    when (style) {
        LoginBackgroundStyle.SLIDESHOW -> CinematicSlideshowBackground(
            posters = cleanPosters,
            animate = animate,
            backgroundColor = backgroundColor,
        )
        LoginBackgroundStyle.POSTER_WALL -> AnimatedPosterWallBackground(
            posterUrls = cleanPosters,
            animate = animate,
            accentColor = accentColor,
            backgroundColor = backgroundColor,
        )
    }
}

@Composable
private fun LoginFallbackGlowBackground(
    accentColor: Color,
    backgroundColor: Color,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.14f),
                            backgroundColor,
                            CineObsidian,
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.24f),
                            Color.Transparent,
                        ),
                        center = Offset(constraints.maxWidth / 2f, constraints.maxHeight * 0.22f),
                        radius = (constraints.maxWidth * 0.78f).coerceAtLeast(300f),
                    )
                )
        )
    }
}

@Composable
private fun CinematicSlideshowBackground(
    posters: List<String>,
    animate: Boolean,
    backgroundColor: Color,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { context.imageLoader }
    var currentIndex by remember(posters) { mutableIntStateOf(0) }

    LaunchedEffect(posters, animate) {
        if (!animate || posters.size <= 1) return@LaunchedEffect
        while (true) {
            delay(4000L)
            currentIndex = (currentIndex + 1) % posters.size
        }
    }

    LaunchedEffect(posters, currentIndex) {
        if (posters.size <= 1) return@LaunchedEffect
        val nextUrl = posters[(currentIndex + 1) % posters.size]
        imageLoader.enqueue(
            ImageRequest.Builder(context)
                .data(nextUrl)
                .size(780, 1170)
                .build()
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        AnimatedContent(
            targetState = currentIndex,
            transitionSpec = {
                fadeIn(animationSpec = tween(durationMillis = 1000)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 1000))
            },
            contentKey = { idx -> posters.getOrNull(idx) ?: idx },
            label = "login_slideshow_crossfade",
            modifier = Modifier.fillMaxSize(),
        ) { slideIndex ->
            val posterUrl = posters.getOrNull(slideIndex).orEmpty()
            val driftDirection = remember(slideIndex, posterUrl) {
                val rng = Random(slideIndex * 31 + posterUrl.hashCode())
                val angle = rng.nextDouble(0.0, PI * 2.0)
                Offset(x = cos(angle).toFloat(), y = sin(angle).toFloat())
            }
            val kenBurnsProgress = remember(slideIndex, posterUrl) { Animatable(0f) }

            LaunchedEffect(slideIndex, posterUrl, animate) {
                if (!animate) return@LaunchedEffect
                val remaining = (1f - kenBurnsProgress.value).coerceIn(0f, 1f)
                if (remaining > 0f) {
                    kenBurnsProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = (remaining * 5000f).toInt().coerceAtLeast(100),
                            easing = LinearEasing,
                        ),
                    )
                }
            }

            val density = LocalDensity.current
            val maxDriftXPx = with(density) { 12.dp.toPx() }
            val maxDriftYPx = with(density) { 18.dp.toPx() }

            val imageRequest = remember(posterUrl, context) {
                ImageRequest.Builder(context)
                    .data(posterUrl)
                    .size(780, 1170)
                    .crossfade(false)
                    .build()
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val t = kenBurnsProgress.value
                        val scale = 1.0f + 0.12f * t
                        scaleX = scale
                        scaleY = scale
                        translationX = driftDirection.x * maxDriftXPx * t
                        translationY = driftDirection.y * maxDriftYPx * t
                    },
            )
        }

        // Dark vertical gradient overlay: ~0.25 at top rising to ~0.90 behind logo, headline, pager & button
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to backgroundColor.copy(alpha = 0.25f),
                        0.18f to backgroundColor.copy(alpha = 0.52f),
                        0.34f to backgroundColor.copy(alpha = 0.88f),
                        0.58f to backgroundColor.copy(alpha = 0.92f),
                        1.00f to backgroundColor.copy(alpha = 0.97f),
                    )
                )
        )
    }
}

@Composable
private fun AnimatedPosterWallBackground(
    posterUrls: List<String>,
    animate: Boolean,
    accentColor: Color,
    backgroundColor: Color,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        if (posterUrls.isEmpty()) {
            LoginFallbackGlowBackground(
                accentColor = accentColor,
                backgroundColor = backgroundColor,
            )
            return@BoxWithConstraints
        }

        val columnPosters = remember(posterUrls) {
            distributePostersAcrossColumns(posterUrls = posterUrls, columnCount = 3)
        }

        val progress = if (animate) {
            val infiniteTransition = rememberInfiniteTransition(label = "poster_wall")
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 28000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "poster_scroll_progress",
            ).value
        } else {
            0f
        }

        val density = LocalDensity.current
        val tileWidthDp = ((maxWidth / 3f) + 12.dp).coerceIn(118.dp, 190.dp)
        val tileHeightDp = tileWidthDp * 1.5f
        val spacingDp = 10.dp
        val tilePitchPx = with(density) { (tileHeightDp + spacingDp).toPx() }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = -12f
                    scaleX = 1.24f
                    scaleY = 1.24f
                }
                .alpha(0.50f),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            columnPosters.forEachIndexed { colIndex, urls ->
                val cyclePx = (urls.size / 2).coerceAtLeast(2) * tilePitchPx
                val direction = if (colIndex % 2 == 0) -1f else 1f
                val rawShift = progress * cyclePx * direction
                val loopShift = if (direction < 0f) rawShift else rawShift - cyclePx

                Column(
                    modifier = Modifier
                        .width(tileWidthDp)
                        .graphicsLayer {
                            translationY = loopShift
                        },
                    verticalArrangement = Arrangement.spacedBy(spacingDp),
                ) {
                    urls.forEachIndexed { itemIdx, url ->
                        val stableKey = remember(colIndex, itemIdx, url) { "${colIndex}_${itemIdx}_$url" }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.05f)),
                        ) {
                            AsyncImage(
                                model = stableKey.let { url },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        // Dark vertical gradient overlay: transparent at top to near-black at ~60% height
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.32f to backgroundColor.copy(alpha = 0.55f),
                        0.60f to backgroundColor.copy(alpha = 0.94f),
                        1.0f to backgroundColor,
                    )
                )
        )
    }
}

/**
 * Partitions unique posters round-robin across [columnCount] columns so no poster appears
 * side by side, then duplicates each column's cycle unit cleanly (`cycleUnit + cycleUnit`)
 * for seamless infinite vertical scrolling.
 */
internal fun distributePostersAcrossColumns(
    posterUrls: List<String>,
    columnCount: Int = 3,
    minCycleLength: Int = 6,
): List<List<String>> {
    val unique = posterUrls.filter { it.isNotBlank() }.distinct()
    if (unique.isEmpty() || columnCount <= 0) return List(columnCount.coerceAtLeast(0)) { emptyList() }

    val baseColumns: List<List<String>> = if (unique.size < columnCount) {
        List(columnCount) { colIndex ->
            List(unique.size) { offset -> unique[(offset + colIndex) % unique.size] }
        }
    } else {
        val cols = List(columnCount) { mutableListOf<String>() }
        unique.forEachIndexed { idx, url ->
            cols[idx % columnCount].add(url)
        }
        cols
    }

    return baseColumns.map { base ->
        val cycleUnit = mutableListOf<String>()
        while (cycleUnit.size < minCycleLength) {
            cycleUnit.addAll(base)
        }
        // Double the exact cycle unit so half-list translation wraps seamlessly
        cycleUnit + cycleUnit
    }
}

@Composable
private fun GlassFeatureCard(slide: AuthFeatureSlide) {
    Surface(
        color = Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Apply blur only on API 31+ to the decorative frosted tint backdrop so text/icon stay sharp
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(18.dp)
                        } else {
                            Modifier
                        }
                    )
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                slide.accentTint.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.03f),
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    color = slide.accentTint.copy(alpha = 0.16f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, slide.accentTint.copy(alpha = 0.35f)),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = slide.icon,
                            contentDescription = null,
                            tint = slide.accentTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = slide.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = slide.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleGLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.19f
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        // Blue right + bottom-right arc
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -15f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
        // Green bottom arc
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 55f,
            sweepAngle = 95f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
        // Yellow left arc
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 150f,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
        // Red top arc
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 210f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
        // Blue horizontal bar
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.50f, size.height * 0.50f),
            end = Offset(size.width * 0.92f, size.height * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
