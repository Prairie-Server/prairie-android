package com.continuum.app.tv.ui.screens.collections

import com.continuum.app.viewmodel.CollectionsViewModel
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.model.personal.Collection
import com.continuum.app.model.personal.CollectionGroup
import com.continuum.app.tv.ui.components.TvDialogOption
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.continuum.app.tv.ui.components.TvOptionDialog
import com.continuum.app.tv.ui.components.TvTextInputDialog
import com.continuum.app.tv.ui.theme.Spacing
import com.continuum.app.tv.ui.theme.sectionEyebrow
import com.continuum.app.tv.ui.theme.tvPageStartPadding
import com.continuum.app.viewmodel.CollectionSection
import com.continuum.app.viewmodel.GroupAction
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCollectionsScreen(
    onCollectionClick: (collectionId: String, title: String) -> Unit,
    onInitialContentFocus: () -> Unit = {},
    viewModel: CollectionsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val firstCollectionFocusRequester = remember { FocusRequester() }
    // Focus the first card in RENDER (section) order, not raw API order — they
    // can differ once collections are grouped.
    val firstCollectionId = state.sections.firstNotNullOfOrNull { it.collections.firstOrNull()?.id }

    // Local UI state for the context menus a card / group header opens; the
    // actual mutations route through the shared VM's groupAction state.
    var cardActionsTarget by remember { mutableStateOf<Collection?>(null) }
    var pendingDelete by remember { mutableStateOf<Collection?>(null) }
    var groupMenuTarget by remember { mutableStateOf<CollectionGroup?>(null) }

    // One-shot guard so a refresh that re-emits the same collections doesn't
    // jerk focus back to the first card if the user has scrolled.
    var initialFocusRequested by remember { mutableStateOf(false) }
    LaunchedEffect(firstCollectionId) {
        if (initialFocusRequested || firstCollectionId == null) return@LaunchedEffect
        runCatching { firstCollectionFocusRequester.requestFocus() }
        onInitialContentFocus()
        initialFocusRequested = true
    }

    // Refresh on resume so a rename/delete done on the detail screen (which owns
    // a separate VM) is reflected when the user returns to the grid. refresh()
    // is quiet (isRefreshing, no spinner) and re-emits the same first id, so the
    // focus guard above keeps focus where the user left it.
    val lifecycleOwner = LocalLifecycleOwner.current
    var skippedFirstResume by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Skip the resume that fires right after init's load() — only
                // refresh on RETURN to the grid, avoiding a double initial fetch.
                if (skippedFirstResume) viewModel.refresh() else skippedFirstResume = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Header(
            // Always available — the user can create the first group/collection
            // even from an empty grid (parity with the phone header actions).
            showActions = !(state.isLoading && state.collections.isEmpty()),
            onCreateClick = viewModel::showCreateSheet,
            onAddGroupClick = { viewModel.openGroupAction(GroupAction.Create) },
        )

        when {
            state.isLoading && state.collections.isEmpty() -> TvLoadingScreen()
            state.error != null && state.collections.isEmpty() -> TvErrorScreen(
                message = state.error!!,
                onRetry = viewModel::loadCollections,
            )
            state.collections.isEmpty() && state.groups.isEmpty() ->
                EmptyState(onCreateClick = viewModel::showCreateSheet)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(
                    start = tvPageStartPadding(),
                    end = Spacing.safeArea,
                    top = Spacing.lg,
                    bottom = Spacing.xxxl,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.sections.forEach { section ->
                    item(
                        key = "header-${section.groupId ?: "ungrouped"}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        val gid = section.groupId
                        SectionHeader(
                            section = section,
                            onManage = if (gid != null) {
                                { groupMenuTarget = CollectionGroup(id = gid, name = section.name) }
                            } else {
                                null
                            },
                        )
                    }
                    if (section.collections.isEmpty()) {
                        item(
                            key = "empty-${section.groupId ?: "ungrouped"}",
                            span = { GridItemSpan(maxLineSpan) },
                        ) { EmptyGroupHint() }
                    } else {
                        items(section.collections, key = { it.id }) { collection ->
                            CollectionCard(
                                collection = collection,
                                onClick = { onCollectionClick(collection.id, collection.name) },
                                onLongClick = { cardActionsTarget = collection },
                                focusRequester = firstCollectionFocusRequester
                                    .takeIf { collection.id == firstCollectionId },
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showCreateSheet) {
        TvCreateCollectionDialog(
            isCreating = state.isCreating,
            errorMessage = state.createError,
            selectedType = state.createType,
            onTypeChanged = viewModel::onCreateTypeChanged,
            onDismiss = viewModel::hideCreateSheet,
            onCreate = viewModel::createCollection,
        )
    }

    // Card long-press → Move to group / Delete.
    cardActionsTarget?.let { collection ->
        TvOptionDialog(
            title = collection.name,
            options = listOf(
                TvDialogOption(
                    key = "move",
                    title = "Move to group",
                    subtitle = "Organize this collection",
                    onClick = {
                        cardActionsTarget = null
                        viewModel.openGroupAction(GroupAction.Move(collection))
                    },
                ),
                TvDialogOption(
                    key = "delete",
                    title = "Delete collection",
                    subtitle = "Permanently remove this collection",
                    onClick = {
                        cardActionsTarget = null
                        pendingDelete = collection
                    },
                ),
                TvDialogOption(key = "cancel", title = "Cancel", onClick = { cardActionsTarget = null }),
            ),
            onDismiss = { cardActionsTarget = null },
        )
    }

    // Card delete confirmation.
    pendingDelete?.let { collection ->
        TvOptionDialog(
            title = "Delete \"${collection.name}\"?",
            options = listOf(
                TvDialogOption(
                    key = "confirm",
                    title = "Delete",
                    subtitle = "This can't be undone",
                    onClick = {
                        val id = collection.id
                        pendingDelete = null
                        viewModel.deleteCollection(id)
                    },
                ),
                TvDialogOption(key = "cancel", title = "Cancel", onClick = { pendingDelete = null }),
            ),
            onDismiss = { pendingDelete = null },
        )
    }

    // Named-group header → Rename / Delete.
    groupMenuTarget?.let { group ->
        TvOptionDialog(
            title = group.name,
            options = listOf(
                TvDialogOption(
                    key = "rename",
                    title = "Rename group",
                    onClick = {
                        groupMenuTarget = null
                        viewModel.openGroupAction(GroupAction.Rename(group))
                    },
                ),
                TvDialogOption(
                    key = "delete",
                    title = "Delete group",
                    subtitle = "Collections move to Ungrouped",
                    onClick = {
                        groupMenuTarget = null
                        viewModel.openGroupAction(GroupAction.Delete(group))
                    },
                ),
                TvDialogOption(key = "cancel", title = "Cancel", onClick = { groupMenuTarget = null }),
            ),
            onDismiss = { groupMenuTarget = null },
        )
    }

    // Shared group-action dialogs (create / rename / delete / move).
    GroupActionDialogs(
        action = state.groupAction,
        groups = state.groups,
        isBusy = state.isGroupBusy,
        error = state.groupError,
        onCreate = viewModel::createGroup,
        onRename = viewModel::renameGroup,
        onDeleteConfirm = viewModel::deleteGroup,
        onMove = viewModel::moveCollection,
        onDismiss = viewModel::dismissGroupAction,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GroupActionDialogs(
    action: GroupAction?,
    groups: List<CollectionGroup>,
    isBusy: Boolean,
    error: String?,
    onCreate: (String) -> Unit,
    onRename: (groupId: String, name: String) -> Unit,
    onDeleteConfirm: (groupId: String) -> Unit,
    onMove: (collectionId: String, targetGroupId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    when (action) {
        null -> Unit
        is GroupAction.Create -> TvTextInputDialog(
            title = "New group",
            label = "Group name",
            confirmLabel = "Create",
            isBusy = isBusy,
            errorMessage = error,
            onConfirm = onCreate,
            onDismiss = onDismiss,
        )
        is GroupAction.Rename -> TvTextInputDialog(
            title = "Rename group",
            label = "Group name",
            confirmLabel = "Save",
            initialValue = action.group.name,
            isBusy = isBusy,
            errorMessage = error,
            onConfirm = { name -> onRename(action.group.id, name) },
            onDismiss = onDismiss,
        )
        is GroupAction.Delete -> TvOptionDialog(
            title = error?.let { "Delete \"${action.group.name}\"? — $it" }
                ?: "Delete \"${action.group.name}\"?",
            options = listOf(
                TvDialogOption(
                    key = "confirm",
                    title = "Delete group",
                    subtitle = "Collections move to Ungrouped",
                    enabled = !isBusy,
                    onClick = { onDeleteConfirm(action.group.id) },
                ),
                TvDialogOption(key = "cancel", title = "Cancel", onClick = onDismiss),
            ),
            onDismiss = onDismiss,
        )
        is GroupAction.Move -> TvOptionDialog(
            title = error?.let { "Move \"${action.collection.name}\" — $it" }
                ?: "Move \"${action.collection.name}\"",
            options = buildList {
                add(
                    TvDialogOption(
                        key = "ungrouped",
                        title = "Ungrouped",
                        selected = action.collection.groupId == null,
                        enabled = !isBusy,
                        onClick = { onMove(action.collection.id, null) },
                    ),
                )
                groups.sortedWith(compareBy({ it.sortOrder }, { it.name })).forEach { group ->
                    add(
                        TvDialogOption(
                            key = group.id,
                            title = group.name,
                            selected = action.collection.groupId == group.id,
                            enabled = !isBusy,
                            onClick = { onMove(action.collection.id, group.id) },
                        ),
                    )
                }
                add(TvDialogOption(key = "cancel", title = "Cancel", onClick = onDismiss))
            },
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Header(
    showActions: Boolean,
    onCreateClick: () -> Unit,
    onAddGroupClick: () -> Unit,
) {
    val startPadding = tvPageStartPadding()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = startPadding,
                end = Spacing.safeArea,
                top = Spacing.xxl,
                bottom = Spacing.lg,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = "YOUR LIBRARY",
                style = sectionEyebrow,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(
                    imageVector = Icons.Filled.Collections,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = "Collections",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        if (showActions) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Button(
                    onClick = onAddGroupClick,
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CreateNewFolder,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Add Group", style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = onCreateClick,
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "New Collection",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionHeader(
    section: CollectionSection,
    onManage: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = section.name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        if (onManage != null) {
            Card(
                onClick = onManage,
                shape = CardDefaults.shape(shape = RoundedCornerShape(10.dp)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Group actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp).size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyGroupHint() {
    Text(
        text = "No collections in this group yet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Spacing.sm),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EmptyState(onCreateClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Icon(
                imageVector = Icons.Filled.Collections,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(96.dp),
            )
            Text(
                text = "No collections yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Create one to group items together.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onCreateClick,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(text = "Create Collection")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CollectionCard(
    collection: Collection,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = CardDefaults.shape(shape = RoundedCornerShape(16.dp)),
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .fillMaxWidth()
            .height(180.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column {
                Text(
                    text = collection.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = collection.collectionType
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
