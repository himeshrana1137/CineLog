package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.TmdbImageItem
import com.example.ui.theme.CineCharcoal
import com.example.ui.theme.CineElevated
import com.example.ui.theme.CineLogTheme
import com.example.ui.theme.CineObsidian
import com.example.ui.theme.CineTheme
import com.example.util.MediaSaver
import kotlinx.coroutines.launch

private enum class WallpaperEditorMode(val label: String) {
    WALLPAPER_CROP("Phone Wallpaper (9:16)"),
    ORIGINAL("Original Quality"),
}

@Composable
fun WallpaperCropDialog(
    mediaTitle: String,
    imageItem: TmdbImageItem,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(WallpaperEditorMode.WALLPAPER_CROP) }
    var previewBitmap by remember(imageItem.filePath) { mutableStateOf<Bitmap?>(null) }
    var croppedPreview by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Pan & Zoom relative to 9:16 window inside source image
    var offsetXFraction by remember(imageItem.filePath) { mutableFloatStateOf(0f) }
    var offsetYFraction by remember(imageItem.filePath) { mutableFloatStateOf(0f) }
    var zoom by remember(imageItem.filePath) { mutableFloatStateOf(1f) }

    LaunchedEffect(imageItem.filePath) {
        isLoading = true
        loadError = null
        val res = MediaSaver.downloadBitmap(imageItem.previewUrl())
        res.fold(
            onSuccess = { bmp ->
                previewBitmap = bmp
                isLoading = false
            },
            onFailure = { err ->
                loadError = err.localizedMessage ?: "Failed to load image."
                isLoading = false
            }
        )
    }

    LaunchedEffect(previewBitmap, offsetXFraction, offsetYFraction, zoom, mode) {
        val src = previewBitmap ?: return@LaunchedEffect
        if (mode == WallpaperEditorMode.WALLPAPER_CROP) {
            croppedPreview = MediaSaver.cropToNineSixteenBitmap(
                source = src,
                offsetXFraction = offsetXFraction,
                offsetYFraction = offsetYFraction,
                zoom = zoom,
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val accent = MaterialTheme.colorScheme.primary
        val wrapDark = !CineTheme.extraColors.isDark
        val dialogBody: @Composable () -> Unit = {
        Surface(
            color = CineObsidian,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mediaTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (imageItem.width > 0 && imageItem.height > 0) {
                                "${imageItem.width} × ${imageItem.height} • TMDB Gallery"
                            } else "TMDB High-Res Artwork",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_wallpaper_dialog_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close wallpaper editor",
                        )
                    }
                }

                // Mode Switcher
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WallpaperEditorMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(m.label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (m == WallpaperEditorMode.WALLPAPER_CROP) {
                                        Icons.Rounded.Crop
                                    } else {
                                        Icons.Rounded.Download
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
                            modifier = Modifier.testTag("wallpaper_mode_${m.name.lowercase()}"),
                        )
                    }
                }

                // Interactive Viewport
                Surface(
                    color = CineCharcoal,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        } else if (loadError != null) {
                            Text(
                                text = loadError ?: "Unable to load artwork.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else if (mode == WallpaperEditorMode.WALLPAPER_CROP) {
                            val bmp = croppedPreview ?: previewBitmap
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .aspectRatio(9f / 16f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(CineElevated)
                                        .border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(18.dp),
                                        )
                                        .pointerInput(imageItem.filePath) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val scaleDiv = (size.width.toFloat().coerceAtLeast(1f))
                                                offsetXFraction = (offsetXFraction - dragAmount.x / scaleDiv)
                                                    .coerceIn(-0.5f, 0.5f)
                                                offsetYFraction = (offsetYFraction - dragAmount.y / scaleDiv)
                                                    .coerceIn(-0.5f, 0.5f)
                                            }
                                        }
                                        .testTag("wallpaper_crop_canvas"),
                                ) {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "9:16 Wallpaper Preview — drag to reposition",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    Surface(
                                        color = CineObsidian.copy(alpha = 0.72f),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(10.dp),
                                    ) {
                                        Text(
                                            text = "Drag to pan • 9:16",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        } else {
                            val bmp = previewBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "$mediaTitle artwork",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            }
                        }
                    }
                }

                if (mode == WallpaperEditorMode.WALLPAPER_CROP && previewBitmap != null) {
                    Surface(
                        color = CineCharcoal,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ZoomIn,
                                contentDescription = "Zoom",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Slider(
                                value = zoom,
                                onValueChange = { zoom = it },
                                valueRange = 1f..3f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("wallpaper_zoom_slider"),
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", zoom)}×",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                // Bottom Save Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val originalBmp = MediaSaver.downloadBitmap(imageItem.originalUrl())
                                    .getOrElse { previewBitmap }
                                if (originalBmp == null) {
                                    isSaving = false
                                    onNotify("Could not download original image.")
                                    return@launch
                                }
                                val saved = MediaSaver.saveBitmapToGallery(
                                    context = context,
                                    bitmap = originalBmp,
                                    fileNamePrefix = "CineLog_Original_$mediaTitle",
                                )
                                isSaving = false
                                saved.fold(
                                    onSuccess = { msg -> onNotify(msg) },
                                    onFailure = { err -> onNotify(err.localizedMessage ?: "Download failed.") },
                                )
                            }
                        },
                        enabled = !isLoading && !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("download_original_image_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Original PNG")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val hiResSource = MediaSaver.downloadBitmap(imageItem.originalUrl())
                                    .getOrElse { previewBitmap }
                                if (hiResSource == null) {
                                    isSaving = false
                                    onNotify("Could not download high-res wallpaper.")
                                    return@launch
                                }
                                val cropped = MediaSaver.cropToNineSixteenBitmap(
                                    source = hiResSource,
                                    offsetXFraction = offsetXFraction,
                                    offsetYFraction = offsetYFraction,
                                    zoom = zoom,
                                )
                                val saved = MediaSaver.saveBitmapToGallery(
                                    context = context,
                                    bitmap = cropped,
                                    fileNamePrefix = "CineLog_Wallpaper_$mediaTitle",
                                )
                                isSaving = false
                                saved.fold(
                                    onSuccess = { msg -> onNotify("9:16 Wallpaper ready — $msg") },
                                    onFailure = { err -> onNotify(err.localizedMessage ?: "Wallpaper save failed.") },
                                )
                            }
                        },
                        enabled = !isLoading && !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_wallpaper_button"),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Wallpaper,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save 9:16 Wallpaper", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        }
        if (wrapDark) {
            CineLogTheme(accentColor = accent, forceDark = true, content = dialogBody)
        } else {
            dialogBody()
        }
    }
}
