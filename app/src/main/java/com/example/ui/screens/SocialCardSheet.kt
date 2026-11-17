package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.WatchedMovie
import com.example.ui.theme.CineCharcoal
import com.example.ui.theme.CineElevated
import com.example.util.MediaSaver
import com.example.util.SocialCardFormat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialCardSheet(
    movie: WatchedMovie,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accentColorInt = MaterialTheme.colorScheme.primary.toArgb()

    var selectedFormat by remember { mutableStateOf(SocialCardFormat.STORY) }
    var posterBitmap by remember(movie.uniqueKey) { mutableStateOf<Bitmap?>(null) }
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }

    LaunchedEffect(movie.uniqueKey, movie.posterPath) {
        val url = movie.fullPosterUrl()
        posterBitmap = if (url != null) {
            MediaSaver.downloadBitmap(url).getOrNull()
        } else null
    }

    LaunchedEffect(movie, posterBitmap, selectedFormat, accentColorInt) {
        isRendering = true
        renderedBitmap = MediaSaver.renderSocialCardBitmap(
            movie = movie,
            posterBitmap = posterBitmap,
            format = selectedFormat,
            accentColorInt = accentColorInt,
        )
        isRendering = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CineCharcoal,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SPOILER-FREE SOCIAL CARD",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Poster, title, year, stars & reaction — zero plot spoilers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_social_card_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close social card",
                    )
                }
            }

            // Format Switcher: Story (1080x1920) vs Square (1080x1080)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SocialCardFormat.entries.forEach { format ->
                    FilterChip(
                        selected = selectedFormat == format,
                        onClick = { selectedFormat = format },
                        label = { Text("${format.label} (${format.aspectText})") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (format == SocialCardFormat.STORY) {
                                    Icons.Rounded.Smartphone
                                } else {
                                    Icons.Rounded.CropSquare
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.testTag("social_format_${format.name.lowercase()}"),
                    )
                }
            }

            // Live Rendered PNG Card Preview
            Surface(
                color = CineElevated,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = renderedBitmap
                    if (bmp != null && !isRendering) {
                        val ratio = selectedFormat.widthPx.toFloat() / selectedFormat.heightPx.toFloat()
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Spoiler-free card preview for ${movie.title}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .heightIn(max = 390.dp)
                                .aspectRatio(ratio)
                                .clip(RoundedCornerShape(12.dp))
                                .testTag("social_card_preview_image"),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .background(CineElevated),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Action Buttons: Download PNG & Share (with Download fallback)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val bmp = renderedBitmap ?: return@OutlinedButton
                        scope.launch {
                            isExporting = true
                            val result = MediaSaver.saveBitmapToGallery(
                                context = context,
                                bitmap = bmp,
                                fileNamePrefix = "CineLog_${movie.title}_${selectedFormat.name}",
                            )
                            isExporting = false
                            result.fold(
                                onSuccess = { msg -> onNotify(msg) },
                                onFailure = { err -> onNotify(err.localizedMessage ?: "Failed to save PNG.") },
                            )
                        }
                    },
                    enabled = renderedBitmap != null && !isExporting,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("download_social_card_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download PNG", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        val bmp = renderedBitmap ?: return@Button
                        scope.launch {
                            isExporting = true
                            val shareText = buildString {
                                append("Just watched ${movie.title} (${movie.releaseYear})")
                                if (movie.userRating > 0) append(" — Rated ${movie.userRating}/5 ★")
                                movie.quickReaction?.let { append(" ${it.emoji}") }
                                append(" on CineLog")
                            }
                            val shareResult = MediaSaver.shareBitmapViaSheet(
                                context = context,
                                bitmap = bmp,
                                fileNamePrefix = "CineLog_${movie.title}",
                                shareText = shareText,
                            )
                            if (shareResult.isFailure) {
                                // Fallback to saving PNG to Pictures/CineLog
                                val fallback = MediaSaver.saveBitmapToGallery(
                                    context = context,
                                    bitmap = bmp,
                                    fileNamePrefix = "CineLog_${movie.title}_${selectedFormat.name}",
                                )
                                fallback.fold(
                                    onSuccess = { msg -> onNotify("Share unavailable — $msg") },
                                    onFailure = { err -> onNotify(err.localizedMessage ?: "Share failed.") },
                                )
                            }
                            isExporting = false
                        }
                    },
                    enabled = renderedBitmap != null && !isExporting,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_social_card_button"),
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
