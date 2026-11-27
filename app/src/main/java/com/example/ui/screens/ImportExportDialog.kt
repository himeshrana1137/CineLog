package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.MovieSummary
import com.example.ui.ImportUiState
import com.example.ui.theme.CineTheme
import com.example.util.ImportCandidateRow
import kotlinx.coroutines.launch

@Composable
fun ImportExportDialog(
    isOpen: Boolean,
    importUiState: ImportUiState,
    watchedCount: Int,
    watchlistCount: Int,
    customListsCount: Int,
    buildExportCsv: () -> String,
    buildExportJson: () -> String,
    onStartImportCsv: (String) -> Unit,
    onSearchFixQuery: suspend (String) -> Result<List<MovieSummary>>,
    onFixRow: (rowIndex: Int, matched: MovieSummary) -> Unit,
    onToggleSkipRow: (rowIndex: Int) -> Unit,
    onCommitImport: () -> Unit,
    onClearPreview: () -> Unit,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isOpen) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPasteCsvDialog by remember { mutableStateOf(false) }
    var fixingRow by remember { mutableStateOf<ImportCandidateRow?>(null) }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val payload = buildExportCsv()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                } ?: error("Could not open output file.")
            }.fold(
                onSuccess = { onNotify("Exported CineLog library as CSV!") },
                onFailure = { onNotify(it.localizedMessage ?: "CSV export failed.") }
            )
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val payload = buildExportJson()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                } ?: error("Could not open output file.")
            }.fold(
                onSuccess = { onNotify("Exported CineLog library as JSON!") },
                onFailure = { onNotify(it.localizedMessage ?: "JSON export failed.") }
            )
        }
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Could not read selected file.")
            }.fold(
                onSuccess = { text -> onStartImportCsv(text) },
                onFailure = { onNotify(it.localizedMessage ?: "Failed to read CSV file.") }
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .testTag("import_export_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "LIBRARY BACKUP & MIGRATION",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Import / Export",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                // Export Section
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "EXPORT CINE-LOG ARCHIVE",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "$watchedCount watched • $watchlistCount watchlist • $customListsCount custom lists",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { exportCsvLauncher.launch("cinelog_export.csv") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("export_csv_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export CSV")
                            }
                            OutlinedButton(
                                onClick = { exportJsonLauncher.launch("cinelog_export.json") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("export_json_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export JSON")
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Import Section
                Text(
                    text = "IMPORT CSV (IMDB / LETTERBOXD)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )

                when (importUiState) {
                    is ImportUiState.Idle -> {
                        Text(
                            text = "Pick an IMDb ratings/watchlist CSV or Letterboxd watched/diary CSV. Titles are matched against TMDB so you can review and fix any unmatched rows before saving.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    importCsvLauncher.launch(
                                        arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("import_csv_file_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose CSV", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { showPasteCsvDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("paste_csv_button"),
                            ) {
                                Text("Paste CSV")
                            }
                        }
                    }

                    is ImportUiState.Matching -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "Matching ${importUiState.format.label} on TMDB…",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                LinearProgressIndicator(
                                    progress = {
                                        if (importUiState.total > 0) {
                                            importUiState.completed.toFloat() / importUiState.total.toFloat()
                                        } else 0f
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = "${importUiState.completed} of ${importUiState.total} rows processed",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    is ImportUiState.Preview -> {
                        val matchedCount = importUiState.rows.count { it.isMatched }
                        val unmatchedCount = importUiState.rows.count { !it.isSkipped && it.matchedMovie == null }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    color = CineTheme.extraColors.successContainer,
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text(
                                        text = "$matchedCount Matched",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CineTheme.extraColors.success,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                                if (unmatchedCount > 0) {
                                    Surface(
                                        color = CineTheme.extraColors.warningContainer,
                                        shape = RoundedCornerShape(50),
                                    ) {
                                        Text(
                                            text = "$unmatchedCount Unmatched",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = CineTheme.extraColors.warning,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                            TextButton(
                                onClick = onClearPreview,
                                enabled = !importUiState.isSaving,
                            ) {
                                Text("Reset")
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(importUiState.rows, key = { it.rowIndex }) { row ->
                                ImportPreviewRowCard(
                                    row = row,
                                    onFixClick = { fixingRow = row },
                                    onToggleSkip = { onToggleSkipRow(row.rowIndex) },
                                )
                            }
                        }

                        Button(
                            onClick = onCommitImport,
                            enabled = matchedCount > 0 && !importUiState.isSaving,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("commit_import_button"),
                        ) {
                            if (importUiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Saving to Firestore…")
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save $matchedCount Matched Titles", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPasteCsvDialog) {
        var csvText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasteCsvDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Paste CSV Contents") },
            text = {
                OutlinedTextField(
                    value = csvText,
                    onValueChange = { csvText = it },
                    minLines = 5,
                    maxLines = 8,
                    placeholder = { Text("Name,Year,Rating,Watched Date\nInception,2010,5,2026-01-10") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_csv_input"),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPasteCsvDialog = false
                        onStartImportCsv(csvText)
                    },
                    enabled = csvText.isNotBlank(),
                    modifier = Modifier.testTag("confirm_paste_csv_button"),
                ) {
                    Text("Match Titles")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteCsvDialog = false }) { Text("Cancel") }
            },
        )
    }

    fixingRow?.let { targetRow ->
        FixImportMatchDialog(
            row = targetRow,
            onSearch = onSearchFixQuery,
            onSelectMatch = { chosen ->
                onFixRow(targetRow.rowIndex, chosen)
                fixingRow = null
            },
            onDismiss = { fixingRow = null },
        )
    }
}

@Composable
private fun ImportPreviewRowCard(
    row: ImportCandidateRow,
    onFixClick: () -> Unit,
    onToggleSkip: () -> Unit,
) {
    val matched = row.matchedMovie
    val extraColors = CineTheme.extraColors
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            when {
                row.isSkipped -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                matched != null -> extraColors.success.copy(alpha = 0.5f)
                else -> extraColors.warning.copy(alpha = 0.7f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("import_preview_row_${row.rowIndex}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (matched != null && !row.isSkipped) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = when {
                    row.isSkipped -> MaterialTheme.colorScheme.onSurfaceVariant
                    matched != null -> extraColors.success
                    else -> extraColors.warning
                },
                modifier = Modifier.size(20.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${row.sourceTitle}${row.year?.let { " ($it)" } ?: ""}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (row.isSkipped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        row.isSkipped -> "Skipped"
                        matched != null -> "Matched: ${matched.title} (${matched.releaseYear})"
                        else -> "No automatic TMDB match found"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (matched == null && !row.isSkipped) extraColors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            OutlinedButton(
                onClick = onFixClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("fix_import_row_${row.rowIndex}"),
            ) {
                Text("Fix")
            }

            TextButton(
                onClick = onToggleSkip,
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Text(if (row.isSkipped) "Include" else "Skip")
            }
        }
    }
}

@Composable
private fun FixImportMatchDialog(
    row: ImportCandidateRow,
    onSearch: suspend (String) -> Result<List<MovieSummary>>,
    onSelectMatch: (MovieSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(row.sourceTitle) }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<MovieSummary>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            isSearching = true
            errorMsg = null
            onSearch(q).fold(
                onSuccess = { results = it },
                onFailure = { errorMsg = it.localizedMessage ?: "Search failed." }
            )
            isSearching = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Match \"${row.sourceTitle}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text("Search TMDB") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { runSearch() },
                        enabled = !isSearching && query.isNotBlank(),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search")
                    }
                }

                if (isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                errorMsg?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { it.uniqueKey }) { item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onSelectMatch(item) },
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(54.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val poster = item.fullPosterUrl()
                                    if (poster != null) {
                                        AsyncImage(
                                            model = poster,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Movie,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${item.releaseYear} • ${item.mediaType.label}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
