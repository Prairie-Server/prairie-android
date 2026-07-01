package org.siloserver.silo.android.ui.screens.collections

import org.siloserver.silo.viewmodel.CollectionSection
import org.siloserver.silo.viewmodel.CollectionsViewModel
import org.siloserver.silo.viewmodel.GroupAction
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.siloserver.silo.android.ui.components.SiloTopBar
import org.siloserver.silo.android.ui.components.EmptyStateView
import org.siloserver.silo.android.ui.components.ErrorView
import org.siloserver.silo.android.ui.components.LoadingIndicator
import org.siloserver.silo.model.personal.Collection
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    onBackClick: () -> Unit,
    onCollectionClick: (String) -> Unit,
    viewModel: CollectionsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            SiloTopBar(
                title = "Collections",
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = { viewModel.openGroupAction(GroupAction.Create) }) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Add group",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = viewModel::showCreateSheet) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create collection",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> {
                LoadingIndicator(modifier = Modifier.padding(padding))
            }
            state.error != null && state.collections.isEmpty() && state.groups.isEmpty() -> {
                ErrorView(
                    message = state.error ?: "Unknown error",
                    onRetry = viewModel::loadCollections,
                    modifier = Modifier.padding(padding),
                )
            }
            state.collections.isEmpty() && state.groups.isEmpty() -> {
                EmptyStateView(
                    title = "No collections",
                    subtitle = "Create a collection to organize your media",
                    icon = Icons.Outlined.CollectionsBookmark,
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        for (section in state.sections) {
                            item(key = "header:${section.groupId ?: "ungrouped"}") {
                                SectionHeader(
                                    section = section,
                                    onRename = if (section.groupId != null) {
                                        {
                                            val g = state.groups.firstOrNull { it.id == section.groupId }
                                            if (g != null) viewModel.openGroupAction(GroupAction.Rename(g))
                                        }
                                    } else null,
                                    onDelete = if (section.groupId != null) {
                                        {
                                            val g = state.groups.firstOrNull { it.id == section.groupId }
                                            if (g != null) viewModel.openGroupAction(GroupAction.Delete(g))
                                        }
                                    } else null,
                                )
                            }

                            item(key = "section:${section.groupId ?: "ungrouped"}") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surface),
                                ) {
                                    if (section.collections.isEmpty()) {
                                        Text(
                                            text = "Drop collections here to add them to this group.",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 11.dp),
                                        )
                                    } else {
                                        section.collections.forEachIndexed { index, collection ->
                                            if (index > 0) {
                                                HorizontalDivider(
                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                    modifier = Modifier.padding(start = 16.dp),
                                                )
                                            }
                                            CollectionCard(
                                                collection = collection,
                                                onClick = { onCollectionClick(collection.id) },
                                                onDeleteClick = { viewModel.deleteCollection(collection.id) },
                                                onMoveClick = {
                                                    viewModel.openGroupAction(GroupAction.Move(collection))
                                                },
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

    if (state.showCreateSheet) {
        CreateCollectionSheet(
            sheetState = sheetState,
            name = state.createName,
            selectedType = state.createType,
            isCreating = state.isCreating,
            error = state.createError,
            onNameChanged = viewModel::onCreateNameChanged,
            onTypeChanged = viewModel::onCreateTypeChanged,
            onCreateClick = viewModel::createCollection,
            onDismiss = viewModel::hideCreateSheet,
        )
    }

    GroupActionDialog(
        action = state.groupAction,
        isBusy = state.isGroupBusy,
        error = state.groupError,
        groups = state.groups,
        onCreate = viewModel::createGroup,
        onRename = viewModel::renameGroup,
        onDelete = viewModel::deleteGroup,
        onMove = viewModel::moveCollection,
        onDismiss = viewModel::dismissGroupAction,
    )
}

@Composable
private fun SectionHeader(
    section: CollectionSection,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = section.name,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onRename != null || onDelete != null) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Group options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (onRename != null) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("Delete group") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionCard(
    collection: Collection,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoveClick: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                )
                .padding(horizontal = 16.dp)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = collection.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = rowSubtitle(collection),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Move") },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onMoveClick()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onDeleteClick()
                },
            )
        }
    }
}

private fun rowSubtitle(collection: Collection): String {
    val kind = collection.collectionType.replaceFirstChar { it.uppercase() }
    val count = collection.itemCount
    return if (count != null && count > 0) {
        "$kind · $count item${if (count == 1) "" else "s"}"
    } else {
        kind
    }
}

@Composable
private fun GroupActionDialog(
    action: GroupAction?,
    isBusy: Boolean,
    error: String?,
    groups: List<org.siloserver.silo.model.personal.CollectionGroup>,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    when (action) {
        null -> Unit
        is GroupAction.Create -> NameDialog(
            title = "New group",
            confirmLabel = "Create",
            initialName = "",
            isBusy = isBusy,
            error = error,
            onConfirm = onCreate,
            onDismiss = onDismiss,
        )
        is GroupAction.Rename -> NameDialog(
            title = "Rename “${action.group.name}”",
            confirmLabel = "Save",
            initialName = action.group.name,
            isBusy = isBusy,
            error = error,
            onConfirm = { onRename(action.group.id, it) },
            onDismiss = onDismiss,
        )
        is GroupAction.Delete -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete “${action.group.name}”?") },
            text = {
                Text("Collections in this group will move to Ungrouped. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = { onDelete(action.group.id) },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            },
        )
        is GroupAction.Move -> MoveDialog(
            collection = action.collection,
            groups = groups,
            isBusy = isBusy,
            error = error,
            onConfirm = { target -> onMove(action.collection.id, target) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    prompt: String = "Group name",
    isBusy: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(prompt) },
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isBusy && name.isNotBlank(),
                onClick = { onConfirm(name.trim()) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun MoveDialog(
    collection: Collection,
    groups: List<org.siloserver.silo.model.personal.CollectionGroup>,
    isBusy: Boolean,
    error: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(collection.id) { mutableStateOf(collection.groupId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move “${collection.name}” to") },
        text = {
            Column {
                GroupOptionRow(
                    label = "Ungrouped",
                    selected = selected == null,
                    onClick = { selected = null },
                )
                for (group in groups.sortedBy { it.sortOrder }) {
                    GroupOptionRow(
                        label = group.name,
                        selected = selected == group.id,
                        onClick = { selected = group.id },
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isBusy,
                onClick = { onConfirm(selected) },
            ) { Text("Move") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun GroupOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
