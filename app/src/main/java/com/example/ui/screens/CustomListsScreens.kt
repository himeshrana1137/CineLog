package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.CustomList
import com.example.data.model.CustomListEntry
import com.example.data.model.MovieSummary
import com.example.ui.CustomListsUiState
import com.example.ui.ListsSubTab
import com.example.ui.components.CineEmptyState
import com.example.ui.components.LibrarySyncErrorState
import com.example.ui.theme.CineTheme
import java.util.Locale

@Composable
fun CustomListsTabContent(
    customListsState: CustomListsUiState,
    listsSubTab: ListsSubTab,
    watchlistCount: Int,
    customListsCount: Int,
    onSelectListsSubTab: (ListsSubTab) -> Unit,
    columnsCount: Int,
    onCreateList: (name: String, description: String) -> Unit,
    onRenameList: (list: CustomList, newName: String, newDesc: String) -> Unit,
    onDeleteList: (CustomList) -> Unit,
    onOpenListDetails: (CustomList) -> Unit,
    isSyncing: Boolean = false,
    onSyncNow: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingList by remember { mutableStateOf<CustomList?>(null) }
    var deletingList by remember { mutableStateOf<CustomList?>(null) }
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottomPadding = 84.dp + navBarBottom

    val headerRow: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CUSTOM COLLECTIONS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Curate themed playlists & watchlists",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { showCreateDialog = true },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("create_custom_list_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("New List", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (customListsState) {
            is CustomListsUiState.Loading -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = listBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "lists_subtabs_row") {
                        ListsSubTabsRow(
                            listsSubTab = listsSubTab,
                            watchlistCount = watchlistCount,
                            customListsCount = customListsCount,
                            onSelectListsSubTab = onSelectListsSubTab,
                        )
                    }
                    item { headerRow() }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            is CustomListsUiState.Error -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = listBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "lists_subtabs_row") {
                        ListsSubTabsRow(
                            listsSubTab = listsSubTab,
                            watchlistCount = watchlistCount,
                            customListsCount = customListsCount,
                            onSelectListsSubTab = onSelectListsSubTab,
                        )
                    }
                    item { headerRow() }
                    item {
                        LibrarySyncErrorState(
                            technicalDetails = customListsState.message,
                            isSyncing = isSyncing,
                            onSyncNow = onSyncNow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        )
                    }
                }
            }

            is CustomListsUiState.Success -> {
                val gridCols = if (columnsCount >= 3) 2 else 1
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridCols),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = listBottomPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "lists_subtabs_row") {
                        ListsSubTabsRow(
                            listsSubTab = listsSubTab,
                            watchlistCount = watchlistCount,
                            customListsCount = customListsCount,
                            onSelectListsSubTab = onSelectListsSubTab,
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "custom_lists_header") {
                        headerRow()
                    }
                    if (customListsState.lists.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "custom_lists_empty") {
                            CineEmptyState(
                                title = "No custom lists yet",
                                subtitle = "Group favorite franchises, weekend marathons, or comfort films into custom lists.",
                                actionLabel = "Create First List",
                                onActionClick = { showCreateDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                            )
                        }
                    } else {
                        items(
                            items = customListsState.lists,
                            key = { it.id },
                        ) { list ->
                            CustomListCard(
                                list = list,
                                onClick = { onOpenListDetails(list) },
                                onRenameClick = { editingList = list },
                                onDeleteClick = { deletingList = list },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        ListNameDialog(
            title = "Create Custom List",
            initialName = "",
            initialDescription = "",
            confirmLabel = "Create",
            onConfirm = { name, desc ->
                onCreateList(name, desc)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    editingList?.let { list ->
        ListNameDialog(
            title = "Rename List",
            initialName = list.name,
            initialDescription = list.description,
            confirmLabel = "Save",
            onConfirm = { name, desc ->
                onRenameList(list, name, desc)
                editingList = null
            },
            onDismiss = { editingList = null },
        )
    }

    deletingList?.let { list ->
        AlertDialog(
            onDismissRequest = { deletingList = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete \"${list.name}\"?") },
            text = { Text("This removes the custom list (${list.items.size} items) from your account. Your Watched library won't be affected.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteList(list)
                        deletingList = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingList = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CustomListCard(
    list: CustomList,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("custom_list_card_${list.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 2x2 Poster Collage
            PosterCollage2x2(
                posterUrls = list.posterPreviews,
                modifier = Modifier.size(92.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (list.description.isNotBlank()) {
                    Text(
                        text = list.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = "${list.items.size} ${if (list.items.size == 1) "title" else "titles"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onRenameClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("rename_list_${list.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Rename list",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_list_${list.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete list",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterCollage2x2(
    posterUrls: List<String>,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clip(RoundedCornerShape(10.dp)),
    ) {
        if (posterUrls.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CollectionsBookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageTile(url = posterUrls.getOrNull(0), modifier = Modifier.weight(1f).fillMaxHeight())
                    CollageTile(url = posterUrls.getOrNull(1), modifier = Modifier.weight(1f).fillMaxHeight())
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageTile(url = posterUrls.getOrNull(2), modifier = Modifier.weight(1f).fillMaxHeight())
                    CollageTile(url = posterUrls.getOrNull(3), modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun CollageTile(
    url: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ListNameDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    confirmLabel: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var desc by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) name = it },
                    singleLine = true,
                    label = { Text("List Name") },
                    placeholder = { Text("e.g. Sci-Fi Mind-Benders") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_list_name_input"),
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { if (it.length <= 240) desc = it },
                    minLines = 2,
                    maxLines = 3,
                    label = { Text("Description (optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_list_desc_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), desc.trim()) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_custom_list_dialog"),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(
    target: MovieSummary,
    lists: List<CustomList>,
    onToggleMovieInList: (CustomList, MovieSummary) -> Unit,
    onCreateListAndAdd: (String, MovieSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newListName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = "ADD TO CUSTOM LIST",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = target.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Quick inline Create & Add
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { if (it.length <= 80) newListName = it },
                    singleLine = true,
                    placeholder = { Text("New list name…") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_new_list_input"),
                )
                Button(
                    onClick = {
                        val trimmed = newListName.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreateListAndAdd(trimmed, target)
                            newListName = ""
                        }
                    },
                    enabled = newListName.trim().isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("quick_new_list_add_button"),
                ) {
                    Text("Create & Add")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (lists.isEmpty()) {
                Text(
                    text = "You haven't created any custom lists yet. Create one above to add \"${target.title}\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(lists, key = { it.id }) { list ->
                        val isMember = list.containsMedia(target.movieId, target.mediaType)
                        Surface(
                            color = if (isMember) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(
                                1.dp,
                                if (isMember) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onToggleMovieInList(list, target) }
                                .testTag("add_to_list_row_${list.id}"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = list.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${list.items.size} ${if (list.items.size == 1) "title" else "titles"}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Checkbox(
                                    checked = isMember,
                                    onCheckedChange = { onToggleMovieInList(list, target) },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomListDetailsSheet(
    list: CustomList,
    onMovieClick: (MovieSummary) -> Unit,
    onRemoveItem: (CustomList, CustomListEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${list.items.size} ${if (list.items.size == 1) "TITLE" else "TITLES"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = list.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (list.description.isNotBlank()) {
                        Text(
                            text = list.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (list.items.isEmpty()) {
                Text(
                    text = "This list is empty. Use the Add to List button on any movie or TV show card to add titles here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 18.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list.items, key = { it.uniqueKey }) { entry ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    onDismiss()
                                    onMovieClick(entry.toSummary())
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(46.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val posterUrl = entry.fullPosterUrl()
                                    if (posterUrl != null) {
                                        AsyncImage(
                                            model = posterUrl,
                                            contentDescription = entry.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Movie,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = buildString {
                                                append(entry.releaseYear)
                                                if (entry.isTv) append(" • TV")
                                                if (entry.languageName.isNotBlank()) append(" • ${entry.languageName}")
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Star,
                                            contentDescription = null,
                                            tint = CineTheme.extraColors.gold,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.1f", entry.rating),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = CineTheme.extraColors.gold,
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onRemoveItem(list, entry) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remove from list",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
