package org.prairieserver.prairie.android.ui.screens.servers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.prairieserver.prairie.android.R
import org.prairieserver.prairie.discovery.DiscoveryHit
import org.prairieserver.prairie.discovery.normalizeDiscoveryUrl
import org.prairieserver.prairie.model.server.ServerEntry
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.material.icons.filled.Close

/**
 * Server list — first-run connect (scan + branding) and multi-server management.
 *
 * First-run (`onBack == null`): hero with Prairie wordmark, Saved + Discovered
 * sections, Scan again + Add manually. Management mode keeps rename/remove and
 * a back affordance when opened from settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    onAddServer: () -> Unit,
    onSwitched: (ServerSwitchDestination) -> Unit,
    onBack: (() -> Unit)? = null,
    autoScan: Boolean = false,
    viewModel: ServerListViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val isFirstRun = onBack == null

    var renameTarget by remember { mutableStateOf<ServerEntry?>(null) }
    var removeTarget by remember { mutableStateOf<ServerEntry?>(null) }

    LaunchedEffect(autoScan) {
        if (autoScan) viewModel.maybeAutoScan()
    }

    LaunchedEffect(state.switchedTo) {
        val destination = state.switchedTo
        if (destination != null) {
            viewModel.onSwitchConsumed()
            onSwitched(destination)
        }
    }

    val savedUrls = remember(state.servers) {
        state.servers.map { normalizeDiscoveryUrl(it.url) }.toSet()
    }
    val freshHits = remember(state.discovered, savedUrls) {
        state.discovered.filter { it.url !in savedUrls }
    }

    Scaffold(
        topBar = {
            if (!isFirstRun) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Servers",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { onBack?.invoke() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            if (isFirstRun) {
                item {
                    FirstRunHero()
                    Spacer(Modifier.height(20.dp))
                }
            }

            item {
                ScanActionsRow(
                    isScanning = state.isScanning || state.isConnecting,
                    onScanAgain = { viewModel.startScan(includeDeep = true) },
                    onAddManually = onAddServer,
                )
                Spacer(Modifier.height(12.dp))
            }

            state.scanStatus?.takeIf { it.isNotBlank() }?.let { status ->
                item {
                    Text(
                        text = status,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            state.scanError?.takeIf { it.isNotBlank() }?.let { error ->
                item {
                    Text(
                        text = error,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            if (state.servers.isNotEmpty()) {
                item { SectionHeader(text = "Saved") }
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
                                isPending = entry.id == state.pendingSwitchToId || state.isConnecting,
                                showActions = !isFirstRun,
                                onClick = { viewModel.onSelect(entry.id) },
                                onRename = { renameTarget = entry },
                                onRemove = { removeTarget = entry },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }

            if (freshHits.isNotEmpty() || state.isScanning) {
                item { SectionHeader(text = "Discovered") }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        if (freshHits.isEmpty() && state.isScanning) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = "Scanning your network for Prairie…",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            freshHits.forEach { hit ->
                                DiscoveredRow(
                                    hit = hit,
                                    enabled = !state.isScanning && !state.isConnecting,
                                    onClick = {
                                        viewModel.selectDiscovered(hit.url, hit.serverName)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (!state.isScanning && state.servers.isEmpty() && freshHits.isEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "No servers yet — wait for the scan, or add a URL manually.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                TextButton(onClick = { removeTarget = null }) { Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier = Modifier.width(8.dp))
                    Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        )
    }
}

@Composable
private fun FirstRunHero() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.prairie_wordmark),
            contentDescription = "Prairie",
            modifier = Modifier
                .width(160.dp)
                .height(48.dp),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = "Connect to your server",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Choose a saved server or one found on your LAN. Sign-in comes next.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScanActionsRow(
    isScanning: Boolean,
    onScanAgain: () -> Unit,
    onAddManually: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ActionChip(
            label = if (isScanning) "Scanning…" else "Scan again",
            icon = Icons.Default.Refresh,
            enabled = !isScanning,
            onClick = onScanAgain,
            modifier = Modifier.weight(1f),
        )
        ActionChip(
            label = "Add manually",
            icon = Icons.Default.Add,
            enabled = !isScanning,
            onClick = onAddManually,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
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
    showActions: Boolean,
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
                onLongClick = { if (showActions) menuExpanded = true },
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

        if (showActions) {
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
}

@Composable
private fun DiscoveredRow(
    hit: DiscoveryHit,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = hit.serverName.trim().ifBlank { hit.url },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = "Found · ${hit.url}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
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
                TextButton(onClick = onDismiss) { Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier = Modifier.width(8.dp))
                    Text("Cancel") }
            }
        },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    )
}
