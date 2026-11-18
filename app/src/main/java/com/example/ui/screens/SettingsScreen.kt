package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AccentPreset
import com.example.data.repository.LocalAccentPreference
import com.example.ui.MigrationProgressState
import com.example.ui.components.UserProfileAvatar
import com.example.ui.theme.CineElevated
import com.example.ui.theme.CineEmerald
import com.example.ui.theme.CineGold
import com.example.ui.theme.CineObsidian
import com.example.ui.theme.parseHexColorOrDefault

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    userName: String,
    userEmail: String?,
    userPhotoUrl: String? = null,
    isOnline: Boolean,
    accentPreference: LocalAccentPreference,
    itemsMissingMetadataCount: Int,
    migrationState: MigrationProgressState,
    onSelectPreset: (AccentPreset) -> Unit,
    onApplyCustomHex: (String) -> Unit,
    onRunMigration: () -> Unit,
    onOpenImportExport: () -> Unit,
    onClearImageCache: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var customHexInput by remember(accentPreference.hex) {
        mutableStateOf(accentPreference.hex)
    }

    LaunchedEffect(accentPreference.hex) {
        customHexInput = accentPreference.hex
    }

    val previewColor = parseHexColorOrDefault(customHexInput, MaterialTheme.colorScheme.primary)
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 84.dp + navBarBottom),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Custom Theme Accent Color Picker
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_accent_card"),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "THEME ACCENT COLOR",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Text(
                        text = "Pick an accent color. Changes apply across the entire app and sync to your account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val presets = listOf(
                        AccentPreset.RED,
                        AccentPreset.YELLOW,
                        AccentPreset.BLUE,
                        AccentPreset.PURPLE,
                        AccentPreset.NEON_GREEN,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        presets.forEach { preset ->
                            val swatchColor = parseHexColorOrDefault(preset.defaultHex)
                            val isSelected = accentPreference.preset == preset ||
                                accentPreference.hex.equals(preset.defaultHex, ignoreCase = true)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectPreset(preset) }
                                    .padding(4.dp)
                                    .testTag("accent_preset_${preset.key}"),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "${preset.label} selected",
                                            tint = if (preset == AccentPreset.YELLOW || preset == AccentPreset.NEON_GREEN) {
                                                CineObsidian
                                            } else {
                                                Color.White
                                            },
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = preset.label.substringAfter(" "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(previewColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        )
                        OutlinedTextField(
                            value = customHexInput,
                            onValueChange = { if (it.length <= 7) customHexInput = it },
                            singleLine = true,
                            label = { Text("Custom Hex (#RRGGBB)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CineElevated,
                                unfocusedContainerColor = CineElevated,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom_hex_input"),
                        )
                        Button(
                            onClick = { onApplyCustomHex(customHexInput) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("apply_custom_hex_button"),
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
        }

        // 2. Background "Update old data" batch migration
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "LIBRARY METADATA SYNC",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Text(
                        text = if (itemsMissingMetadataCount > 0) {
                            "$itemsMissingMetadataCount older watched ${if (itemsMissingMetadataCount == 1) "title is" else "titles are"} missing runtime, cast, directors, or language."
                        } else {
                            "All watched titles are up to date with runtime, cast, directors, and language."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (migrationState.isRunning && migrationState.total > 0) {
                        LinearProgressIndicator(
                            progress = { migrationState.completed.toFloat() / migrationState.total.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = CineElevated,
                        )
                    }

                    migrationState.message?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelMedium,
                            color = CineGold,
                        )
                    }

                    Button(
                        onClick = onRunMigration,
                        enabled = !migrationState.isRunning && itemsMissingMetadataCount > 0,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_update_old_data_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (migrationState.isRunning) {
                                "Updating ${migrationState.completed} / ${migrationState.total}…"
                            } else if (itemsMissingMetadataCount > 0) {
                                "Update Old Data ($itemsMissingMetadataCount)"
                            } else {
                                "All Old Data Updated"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // 2b. Import & Export Archive (CSV / JSON)
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ImportExport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "IMPORT & EXPORT (CSV / JSON)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = "Export your watched library, watchlist, and custom lists as CSV or JSON, or import IMDb and Letterboxd CSV exports with TMDB matching.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onOpenImportExport,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_open_import_export_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ImportExport,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Import / Export", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Offline Persistence & Image Cache
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (isOnline) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = if (isOnline) CineEmerald else CineGold,
                        )
                        Text(
                            text = if (isOnline) "ONLINE • OFFLINE CACHE READY" else "OFFLINE MODE ACTIVE",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isOnline) CineEmerald else CineGold,
                        )
                    }
                    Text(
                        text = "Firestore local persistence and viewed poster caching are active so your Watched list, Watchlist, notes, and ratings load without internet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedButton(
                        onClick = onClearImageCache,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("clear_image_cache_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Cached Poster Images")
                    }
                }
            }
        }

        // 4. Account & Sign Out
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "GOOGLE ACCOUNT",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        UserProfileAvatar(
                            userName = userName,
                            userPhotoUrl = userPhotoUrl,
                            size = 48.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (!userEmail.isNullOrBlank()) {
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onLogout,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_logout_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Out")
                    }
                }
            }
        }
    }
}
