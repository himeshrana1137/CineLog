package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.rounded.Movie
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
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.data.repository.TmdbRepository
import com.example.ui.theme.CineCrimsonGlow
import com.example.ui.theme.CineGold
import com.example.ui.theme.CineObsidian
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class AuthFeatureSlide(
    val icon: ImageVector,
    val accentTint: Color,
    val title: String,
    val subtitle: String,
)

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    tmdbRepository: TmdbRepository = remember { TmdbRepository() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var isLoading by remember { mutableStateOf(false) }
    var trendingPosters by remember { mutableStateOf<List<String>>(emptyList()) }
    var isScreenVisible by remember { mutableStateOf(true) }

    // Staggered entry animation flags
    var showLogo by remember { mutableStateOf(false) }
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

    LaunchedEffect(Unit) {
        launch {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                scope = scope,
                onAuthSuccess = onAuthSuccess,
                onUnauthenticated = { /* stay on AuthScreen */ },
            )
        }
        launch {
            val urls = runCatching { tmdbRepository.getTrendingPostersForLogin() }
                .getOrDefault(emptyList())
            if (urls.isNotEmpty()) {
                trendingPosters = urls
            }
        }
        showLogo = true
        delay(80L)
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
            // 1. Animated Poster Wall Background (or soft red radial glow fallback)
            AnimatedPosterWallBackground(
                posterUrls = trendingPosters,
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
                    // 2. Hero Section - Logo + Glow
                    AnimatedVisibility(
                        visible = showLogo,
                        enter = fadeIn(tween(380)) + scaleIn(
                            initialScale = 0.88f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(width = 230.dp, height = 96.dp)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.48f),
                                                Color.Transparent,
                                            )
                                        )
                                    )
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(16.dp),
                                shadowElevation = 14.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Movie,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(26.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "CINELOG",
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

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
            // Fallback: dark gradient with soft red radial glow
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
            return@BoxWithConstraints
        }

        val columnPosters = remember(posterUrls) {
            val cols = List(3) { mutableListOf<String>() }
            posterUrls.forEachIndexed { idx, url ->
                cols[idx % 3].add(url)
            }
            cols.map { list ->
                val base = if (list.isEmpty()) posterUrls.take(5) else list
                // Repeat items so endless scroll loop never exposes empty space
                val expanded = mutableListOf<String>()
                while (expanded.size < 6) {
                    expanded.addAll(base)
                }
                expanded.take(8)
            }
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
