package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.example.R
import com.example.data.model.LanguageChipCount
import com.example.data.model.MovieSummary
import com.example.data.model.WatchProvidersRegion
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.CineLogTheme
import com.example.ui.theme.CineTheme
import com.example.ui.theme.ThemeMode
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun OfflineModeBanner(
    isOffline: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isOffline,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        Surface(
            color = CineTheme.extraColors.warningContainer,
            border = BorderStroke(1.dp, CineTheme.extraColors.warning.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("offline_mode_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = CineTheme.extraColors.warning,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.offline_banner_text),
                    style = MaterialTheme.typography.labelMedium,
                    color = CineTheme.extraColors.warning,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CineTopNavbar(
    userName: String,
    userPhotoUrl: String?,
    isSearchExpanded: Boolean,
    searchQuery: String,
    searchPlaceholder: String,
    searchTestTag: String,
    hasActiveSearchQuery: Boolean = searchQuery.isNotBlank(),
    isSyncing: Boolean = false,
    onSyncClick: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onSearchIconClick: () -> Unit,
    onCollapseSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            focusRequester.requestFocus()
        }
    }

    TopAppBar(
        expandedHeight = 56.dp,
        windowInsets = WindowInsets.statusBars,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        navigationIcon = {
            if (isSearchExpanded) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        onCollapseSearch()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("collapse_search_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Close search",
                    )
                }
            }
        },
        title = {
            if (isSearchExpanded) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(end = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = searchPlaceholder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { focusManager.clearFocus() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .testTag(searchTestTag),
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { onSearchQueryChange("") }
                                    .testTag("clear_search_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.clear_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                CineLogLogo(
                    size = 28.dp,
                    showWordmark = true,
                    animated = false,
                    wordmarkFontSize = 20.sp,
                    layout = CineLogLogoLayout.Horizontal,
                    modifier = Modifier.testTag("top_bar_logo"),
                )
            }
        },
        actions = {
            if (!isSearchExpanded) {
                if (onSyncClick != null) {
                    IconButton(
                        onClick = onSyncClick,
                        enabled = !isSyncing,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("top_sync_button"),
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = stringResource(R.string.sync_library_cd),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onSearchIconClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("top_search_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = if (hasActiveSearchQuery) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                    )
                }
            }

            Box {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable { menuExpanded = true }
                        .testTag("profile_avatar_button"),
                ) {
                    UserProfileAvatar(
                        userName = userName,
                        userPhotoUrl = userPhotoUrl,
                        size = 34.dp,
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.tab_settings),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenSettings()
                        },
                        modifier = Modifier.testTag("navbar_settings_menu_item"),
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.logout),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onLogoutClick()
                        },
                        modifier = Modifier.testTag("logout_button"),
                    )
                }
            }
        },
        modifier = modifier,
    )
}

fun formatGoogleProfilePhotoUrl(rawUrl: String?): String? {
    val trimmed = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (!trimmed.contains("googleusercontent.com", ignoreCase = true)) return trimmed
    val sizePattern = Regex("=s\\d+(-[a-zA-Z0-9]+)?$")
    return when {
        sizePattern.containsMatchIn(trimmed) -> trimmed.replace(sizePattern, "=s200")
        !trimmed.contains("=") -> "$trimmed=s200"
        else -> trimmed
    }
}

@Composable
fun UserProfileAvatar(
    userName: String,
    userPhotoUrl: String?,
    size: Dp = 38.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val initial = userName.trim().firstOrNull()?.uppercase() ?: "U"
    val formattedUrl = remember(userPhotoUrl) { formatGoogleProfilePhotoUrl(userPhotoUrl) }
    var imageState by remember(formattedUrl) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
    ) {
        if (formattedUrl == null || imageState is AsyncImagePainter.State.Error) {
            ProfileInitialFallback(
                initial = initial,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            if (imageState is AsyncImagePainter.State.Loading || imageState is AsyncImagePainter.State.Empty) {
                ProfileAvatarPlaceholder(modifier = Modifier.fillMaxSize())
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(formattedUrl)
                    .crossfade(true)
                    .transformations(CircleCropTransformation())
                    .build(),
                contentDescription = "$userName profile photo",
                contentScale = ContentScale.Crop,
                onState = { imageState = it },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun ProfileAvatarPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ProfileInitialFallback(
    initial: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageFilterRow(
    chips: List<LanguageChipCount>,
    totalCount: Int,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    testTagPrefix: String,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    fadeColor: Color = MaterialTheme.colorScheme.background,
    modifier: Modifier = Modifier,
) {
    if (chips.isEmpty()) return

    val primaryChips = chips.filter { it.isPrimary }
    val extraChips = chips.filter { !it.isPrimary }
    val initialVisible = if (primaryChips.isNotEmpty()) primaryChips else chips.take(5)
    val overflowChips = if (primaryChips.isNotEmpty()) extraChips else chips.drop(5)
    var showMore by remember { mutableStateOf(false) }
    val rowState = rememberLazyListState()

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state = rowState,
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                item(key = "lang_icon") {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = "Language filters",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                item(key = "lang_all") {
                    FilterChip(
                        selected = selectedLanguage == "all",
                        onClick = { onLanguageSelected("all") },
                        label = {
                            Text(
                                text = "All ($totalCount)",
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.testTag("${testTagPrefix}_lang_all"),
                    )
                }
                items(initialVisible, key = { it.code }) { item ->
                    FilterChip(
                        selected = selectedLanguage.equals(item.code, ignoreCase = true),
                        onClick = {
                            onLanguageSelected(
                                if (selectedLanguage.equals(item.code, ignoreCase = true)) "all" else item.code
                            )
                        },
                        label = {
                            Text(
                                text = "${item.label} (${item.count})",
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.testTag("${testTagPrefix}_lang_${item.code}"),
                    )
                }
                if (overflowChips.isNotEmpty()) {
                    item(key = "lang_more") {
                        FilterChip(
                            selected = overflowChips.any { it.code.equals(selectedLanguage, ignoreCase = true) },
                            onClick = { showMore = !showMore },
                            label = {
                                Text(
                                    text = if (showMore) "Less" else "More (+${overflowChips.size})",
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = if (showMore) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            modifier = Modifier.testTag("${testTagPrefix}_lang_more"),
                        )
                    }
                }
            }

            if (rowState.canScrollForward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(32.dp)
                        .height(42.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, fadeColor)
                            )
                        )
                )
            }
        }

        AnimatedVisibility(visible = showMore && overflowChips.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                overflowChips.forEach { item ->
                    FilterChip(
                        selected = selectedLanguage.equals(item.code, ignoreCase = true),
                        onClick = {
                            onLanguageSelected(
                                if (selectedLanguage.equals(item.code, ignoreCase = true)) "all" else item.code
                            )
                        },
                        label = {
                            Text(
                                text = "${item.label} (${item.count})",
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverMovieCard(
    movie: MovieSummary,
    isWatched: Boolean,
    isInWatchlist: Boolean,
    isToggling: Boolean,
    isWatchlistToggling: Boolean,
    userRating: Int,
    providers: WatchProvidersRegion?,
    onCardClick: () -> Unit,
    onToggleWatched: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onAddToList: (() -> Unit)? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = if (isWatched) CineTheme.extraColors.success.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onCardClick)
            .testTag("discover_movie_card_${movie.uniqueKey}"),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val posterUrl = movie.fullPosterThumbUrl()
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = "${movie.title} poster",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(if (compact) 28.dp else 36.dp),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = movie.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compact) 56.dp else 74.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    CineTheme.extraColors.cardScrimStart,
                                    CineTheme.extraColors.cardScrimEnd,
                                )
                            )
                        )
                )

                val badgePadding = if (compact) 6.dp else 8.dp
                // Top-left: Language badge
                val langBadge = movie.languageName
                if (langBadge.isNotBlank()) {
                    Surface(
                        color = CineTheme.extraColors.posterOverlayScrim,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(badgePadding),
                    ) {
                        Text(
                            text = langBadge,
                            style = MaterialTheme.typography.labelMedium,
                            color = CineTheme.extraColors.posterOverlayContent,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }

                // Top-right: Star rating (personal rating if rated, or TMDB star + score)
                if (isWatched && userRating > 0) {
                    Surface(
                        color = CineTheme.extraColors.gold,
                        shape = RoundedCornerShape(7.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(badgePadding),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = CineTheme.extraColors.onGold,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = "$userRating",
                                style = MaterialTheme.typography.labelMedium,
                                color = CineTheme.extraColors.onGold,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                } else {
                    Surface(
                        color = CineTheme.extraColors.posterOverlayScrim,
                        shape = RoundedCornerShape(7.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(badgePadding),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = CineTheme.extraColors.posterStarGold,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f", movie.rating),
                                style = MaterialTheme.typography.labelMedium,
                                color = CineTheme.extraColors.posterOverlayContent,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Bottom-left: Media type badge ("TV" / "Movie")
                Surface(
                    color = if (movie.isTv) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
                    } else {
                        CineTheme.extraColors.posterBadgeBackground
                    },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(badgePadding),
                ) {
                    Text(
                        text = if (movie.isTv) "TV" else "Movie",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (movie.isTv) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            CineTheme.extraColors.posterOverlayContent
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                // Bottom-right: OTT provider mini logos (India)
                val badgeProviders = providers?.badgeProviders.orEmpty()
                if (badgeProviders.isNotEmpty() && !compact) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        badgeProviders.forEach { provider ->
                            val logoUrl = provider.fullLogoUrl()
                            if (logoUrl != null) {
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = provider.providerName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(CineTheme.extraColors.posterBadgeBackground),
                                )
                            }
                        }
                    }
                }
            }

            if (compact) {
                // Compact single-action-row footer for horizontal carousels (130-140dp wide)
                Column(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .clipToBounds(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${movie.releaseYear} • ${if (movie.isTv) "TV" else "Movie"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clickable(enabled = !isToggling, onClick = onToggleWatched)
                                .testTag("toggle_watched_button_${movie.uniqueKey}"),
                        ) {
                            Surface(
                                color = if (isWatched) CineTheme.extraColors.successContainer else MaterialTheme.colorScheme.primary,
                                contentColor = if (isWatched) CineTheme.extraColors.success else MaterialTheme.colorScheme.onPrimary,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    if (isToggling) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = if (isWatched) CineTheme.extraColors.success else MaterialTheme.colorScheme.onPrimary,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isWatched) Icons.Rounded.Check else Icons.Rounded.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = if (isWatched) "Watched" else "Watch",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        }

                        val watchlistLabel = if (isInWatchlist) {
                            stringResource(R.string.in_watchlist)
                        } else {
                            stringResource(R.string.add_to_watchlist)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(enabled = !isWatchlistToggling, onClick = onToggleWatchlist)
                                .testTag("toggle_watchlist_button_${movie.uniqueKey}"),
                        ) {
                            Surface(
                                color = if (isInWatchlist) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isInWatchlist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.size(34.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isWatchlistToggling) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isInWatchlist) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                            contentDescription = watchlistLabel,
                                            tint = if (isInWatchlist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(17.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .padding(10.dp)
                        .clipToBounds(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${movie.releaseYear} • ${if (movie.isTv) "TV Series" else "Movie"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Primary full-width Watched CTA (no clipping/ellipsis)
                    Button(
                        onClick = onToggleWatched,
                        enabled = !isToggling,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isWatched) CineTheme.extraColors.successContainer else MaterialTheme.colorScheme.primary,
                            contentColor = if (isWatched) CineTheme.extraColors.success else MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("toggle_watched_button_${movie.uniqueKey}"),
                    ) {
                        if (isToggling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = if (isWatched) CineTheme.extraColors.success else MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = if (isWatched) Icons.Rounded.Check else Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isWatched) stringResource(R.string.watched_badge) else "Watched",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }

                    // Secondary compact icon buttons row (Watchlist + Add to list)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val watchlistLabel = if (isInWatchlist) {
                            stringResource(R.string.in_watchlist)
                        } else {
                            stringResource(R.string.add_to_watchlist)
                        }
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text(watchlistLabel) } },
                            state = rememberTooltipState(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(enabled = !isWatchlistToggling, onClick = onToggleWatchlist)
                                    .testTag("toggle_watchlist_button_${movie.uniqueKey}"),
                            ) {
                                Surface(
                                    color = if (isInWatchlist) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isInWatchlist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isWatchlistToggling) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isInWatchlist) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                                contentDescription = watchlistLabel,
                                                tint = if (isInWatchlist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(19.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (onAddToList != null) {
                            val addToListDesc = "Add ${movie.title} to custom list"
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = { PlainTooltip { Text("Add to list") } },
                                state = rememberTooltipState(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable(onClick = onAddToList)
                                        .testTag("add_to_list_button_${movie.uniqueKey}"),
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                                contentDescription = addToListDesc,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(19.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveStarRatingBar(
    rating: Int,
    onRatingSelected: (Int) -> Unit,
    starSize: Dp = 32.dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (star in 1..5) {
            val selected = star <= rating
            IconButton(
                onClick = { onRatingSelected(if (rating == star) 0 else star) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("star_rating_$star"),
            ) {
                Icon(
                    imageVector = if (selected) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = "Rate $star stars",
                    tint = if (selected) CineTheme.extraColors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(starSize),
                )
            }
        }
    }
}

@Composable
fun MovieSkeletonCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            CineTheme.extraColors.shimmerBase,
            CineTheme.extraColors.shimmerHighlight,
            CineTheme.extraColors.shimmerBase,
        ),
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 300f),
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(brush)
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(brush)
                )
            }
        }
    }
}

@Composable
fun CineEmptyState(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    useReelArtwork: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (useReelArtwork) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_reel),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .aspectRatio(4f / 3f)
                    .clip(MaterialTheme.shapes.large),
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("empty_state_action_button"),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
fun LibrarySyncErrorState(
    technicalDetails: String,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var detailsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 28.dp)
            .testTag("library_sync_error_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
            modifier = Modifier.size(68.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SyncProblem,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.sync_error_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.sync_error_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onSyncNow,
            enabled = !isSyncing,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            modifier = Modifier
                .widthIn(min = 180.dp, max = 320.dp)
                .heightIn(min = 48.dp)
                .testTag("sync_now_button"),
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.syncing_now),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.sync_now),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        if (technicalDetails.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = { detailsExpanded = !detailsExpanded },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("sync_error_details_toggle"),
            ) {
                Text(
                    text = stringResource(R.string.sync_details_toggle),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (detailsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (detailsExpanded) "Hide error details" else "Show error details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            AnimatedVisibility(
                visible = detailsExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 360.dp)
                        .padding(top = 6.dp)
                        .testTag("sync_error_details_box"),
                ) {
                    Text(
                        text = technicalDetails,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

fun formatLastSyncedLabel(lastSyncedAtMillis: Long?): String {
    if (lastSyncedAtMillis == null || lastSyncedAtMillis <= 0L) {
        return "Last synced: Not yet"
    }
    val syncedCal = Calendar.getInstance().apply { timeInMillis = lastSyncedAtMillis }
    val nowCal = Calendar.getInstance()
    val sameDay = syncedCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
        syncedCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
    val formatted = if (sameDay) {
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(lastSyncedAtMillis))
    } else {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(lastSyncedAtMillis))
    }
    return "Last synced: $formatted"
}

@Preview(name = "Discover Card & Offline Banner - Dark", showBackground = true)
@Composable
private fun CineComponentsDarkPreview() {
    CineLogTheme(themeMode = ThemeMode.DARK) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OfflineModeBanner(isOffline = true)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DiscoverMovieCard(
                        movie = MovieSummary(
                            movieId = 1L,
                            title = "Interstellar",
                            posterPath = null,
                            backdropPath = null,
                            releaseDate = "2014-11-07",
                            rating = 8.4,
                            overview = "",
                            originalLanguage = "en",
                        ),
                        isWatched = true,
                        isInWatchlist = false,
                        isToggling = false,
                        isWatchlistToggling = false,
                        userRating = 5,
                        providers = null,
                        onCardClick = {},
                        onToggleWatched = {},
                        onToggleWatchlist = {},
                        modifier = Modifier.weight(1f),
                    )
                    MovieSkeletonCard(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Preview(name = "Discover Card & Offline Banner - Light", showBackground = true)
@Composable
private fun CineComponentsLightPreview() {
    CineLogTheme(themeMode = ThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OfflineModeBanner(isOffline = true)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DiscoverMovieCard(
                        movie = MovieSummary(
                            movieId = 1L,
                            title = "Interstellar",
                            posterPath = null,
                            backdropPath = null,
                            releaseDate = "2014-11-07",
                            rating = 8.4,
                            overview = "",
                            originalLanguage = "en",
                        ),
                        isWatched = true,
                        isInWatchlist = false,
                        isToggling = false,
                        isWatchlistToggling = false,
                        userRating = 5,
                        providers = null,
                        onCardClick = {},
                        onToggleWatched = {},
                        onToggleWatchlist = {},
                        modifier = Modifier.weight(1f),
                    )
                    MovieSkeletonCard(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
