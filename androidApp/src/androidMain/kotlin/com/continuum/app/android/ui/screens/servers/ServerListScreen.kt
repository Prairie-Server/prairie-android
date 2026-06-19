package com.continuum.app.android.ui.screens.servers

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.continuum.app.model.server.ServerEntry
import org.koin.compose.viewmodel.koinViewModel

/**
 * Server list — manage the multi-server registry on phone.
 *
 * Visual parity with iOS `ServerListView` (phone): an inset-grouped list with a
 * "Saved servers" header, surface-elevated row cards carrying a leading status
 * icon (filled check circle when active, otherwise a server-rack glyph), and a
 * trailing "Add Server" row in its own group. Rename/Remove live behind a
 * long-press context menu, exactly as the iOS swipe/context actions do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    onAddServer: () -> Unit,
    onSwitched: (ServerSwitchDestination) -> Unit,
    onBack: () -> Unit,
    viewModel: ServerListViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    var renameTarget by remember { mutableStateOf<ServerEntry?>(null) }
    var removeTarget by remember { mutableStateOf<ServerEntry?>(null) }

    LaunchedEffect(state.switchedTo) {
        val destination = state.switchedTo
        if (destination != null) {
            viewModel.onSwitchConsumed()
            onSwitched(destination)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Servers",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.servers.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onAddServer = onAddServer,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            ) {
                // Section: Saved servers
                item {
                    SectionHeader(text = "Saved servers")
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        state.servers.forEach { entry ->
                            ServerRow(
                                entry = entry,
                                isActive = entry.id == state.activeId,
                                isPending = entry.id == state.pendingSwitchToId,
                                onClick = { viewModel.onSelect(entry.id) },
                                onRename = { renameTarget = entry },
                                onRemove = { removeTarget = entry },
                            )
                        }
                    }
                }

                // Section: Add Server
                item {
                    Spacer(Modifier.height(24.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        AddServerRow(onClick = onAddServer)
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            entry = target,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.onRename(target.id, newName)
                renameTarget = null
            },
        )
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove this server?") },
            text = {
                Text(
                    "Sign-in credentials for ${target.displayName} will be " +
                        "forgotten on this device.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onRemove(target.id)
                    removeTarget = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerRow(
    entry: ServerEntry,
    isActive: Boolean,
    isPending: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = !isPending,
                onClick = onClick,
                onLongClick = { menuExpanded = true },
            )
            .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Storage,
                contentDescription = if (isActive) "Active" else null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = entry.url,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        Box {
            IconButton(
                enabled = !isPending,
                onClick = { menuExpanded = true },
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Server actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("Remove Server") },
                    onClick = {
                        menuExpanded = false
                        onRemove()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AddServerRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = "Add Server",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RenameDialog(
    entry: ServerEntry,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf(entry.userOverrideName ?: entry.fetchedName.orEmpty()) }
    val hasOverride = entry.userOverrideName != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename server") },
        text = {
            Column {
                Text(
                    text = "Override the server-provided name with a label just " +
                        "for this device.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input) }) { Text("Save") }
        },
        dismissButton = {
            if (hasOverride) {
                TextButton(onClick = { onConfirm("") }) {
                    Text(
                        "Reset to server-provided name",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    )
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onAddServer: () -> Unit,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No saved servers",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add your first Silo server to get started.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onAddServer)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Add Server",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
