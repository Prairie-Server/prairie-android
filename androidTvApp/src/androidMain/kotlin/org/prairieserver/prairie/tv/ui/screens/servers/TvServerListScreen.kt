package org.prairieserver.prairie.tv.ui.screens.servers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.prairieserver.prairie.discovery.DiscoveryHit
import org.prairieserver.prairie.discovery.normalizeDiscoveryUrl
import org.prairieserver.prairie.model.server.ServerEntry
import org.prairieserver.prairie.tv.ui.focus.rememberTvContentInitialFocus
import org.prairieserver.prairie.tv.ui.components.TvDialogOption
import org.prairieserver.prairie.tv.ui.components.TvOptionDialog
import org.prairieserver.prairie.tv.ui.theme.Spacing
import org.prairieserver.prairie.tv.ui.theme.FocusedContainer
import org.prairieserver.prairie.tv.ui.theme.FocusedContent
import org.prairieserver.prairie.tv.ui.theme.InterFamily
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * Multi-server picker for the TV app — focus-aware list of saved servers
 * with an "Add Server" tile at the top. Long-press / Menu opens an action
 * sheet with Remove (rename is intentionally omitted on TV: easier to
 * remove + re-add than to edit a string with the on-screen keyboard).
 *
 * Prairie (691fcfe1): this is also the first-run connect screen. With
 * [autoScan] it probes the LAN once for Prairie servers (shared
 * `LanDiscovery`, same algorithm as the phone and prairie-smarttv) and lists
 * them under "Discovered"; manual URL entry stays behind [onAddServer]. An
 * upstream sync dropped the discovery UI once; anchored in
 * scripts/prairie-invariants.txt.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvServerListScreen(
    onAddServer: () -> Unit,
    onSwitched: (TvServerSwitchDestination) -> Unit,
    onBack: (() -> Unit)?,
    autoScan: Boolean = false,
    viewModel: TvServerListViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val firstFocus = remember { FocusRequester() }
    var confirmRemove by remember { mutableStateOf<ServerEntry?>(null) }
    // Start destination with nothing behind it: first-run connect.
    val isFirstRun = onBack == null

    if (onBack != null) {
        BackHandler(enabled = true) { onBack() }
    }

    LaunchedEffect(autoScan) {
        if (autoScan) viewModel.maybeAutoScan()
    }

    LaunchedEffect(state.emptyRegistry) {
        // Active server removed with none left — stay here and scan again.
        if (state.emptyRegistry) {
            viewModel.onEmptyRegistryConsumed()
            viewModel.startScan(includeDeep = true)
        }
    }

    val savedUrls = remember(state.servers) {
        state.servers.map { normalizeDiscoveryUrl(it.url) }.toSet()
    }
    val freshHits = remember(state.discovered, savedUrls) {
        state.discovered.filter { it.url !in savedUrls }
    }
    val busy = state.isScanning || state.isConnecting

    LaunchedEffect(state.switchedTo) {
        val destination = state.switchedTo
        if (destination != null) {
            viewModel.onSwitchConsumed()
            onSwitched(destination)
        }
    }

    // Anchor focus on the first row whenever the list materializes so d-pad
    // navigation has somewhere to land. The rows are lazy, so the first request
    // lands before placement and is rejected — this retries until focus is
    // actually observed rather than until a call merely returns.
    val contentInitialFocus = rememberTvContentInitialFocus(
        target = firstFocus,
        contentKey = state.servers.firstOrNull()?.id,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(contentInitialFocus)
            .background(ServerSettingsBackground)
            .padding(start = 44.dp, top = Spacing.safeArea, end = 44.dp, bottom = Spacing.xxxl),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(
                modifier = Modifier.width(200.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = if (isFirstRun) "CONNECT" else "CONNECTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
                Text(
                    text = if (isFirstRun) "Choose a server" else "Manage Servers",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    text = if (isFirstRun) {
                        "Pick a saved server or one found on your network. Sign-in comes next."
                    } else {
                        "Choose, add, or remove a Prairie server."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f),
                )
            }

            Column(
                modifier = Modifier.widthIn(max = ServerListMaxWidth),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Saved Servers",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.62f),
                )
                AddServerTile(
                    onClick = onAddServer,
                    modifier = Modifier.focusRequester(
                        if (state.servers.isEmpty()) firstFocus else FocusRequester.Default,
                    ),
                )
                ScanTile(
                    isScanning = state.isScanning,
                    enabled = !busy,
                    onClick = { viewModel.startScan(includeDeep = true) },
                )
                state.scanStatus?.takeIf { it.isNotBlank() }?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                }
                state.scanError?.takeIf { it.isNotBlank() }?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.servers, key = { it.id }) { entry ->
                        val rowModifier = if (entry == state.servers.firstOrNull()) {
                            Modifier.focusRequester(firstFocus)
                        } else Modifier

                        ServerRow(
                            entry = entry,
                            isActive = entry.id == state.activeId,
                            isPending = entry.id == state.pendingSwitchToId,
                            onSelect = { viewModel.onSelect(entry.id) },
                            onRemove = { confirmRemove = entry },
                            modifier = rowModifier,
                        )
                    }
                    if (freshHits.isNotEmpty() || state.isScanning) {
                        item(key = "discovered-header") {
                            Text(
                                text = "Discovered",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.62f),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    if (freshHits.isEmpty() && state.isScanning) {
                        item(key = "discovered-scanning") {
                            Text(
                                text = "Scanning your network for Prairie…",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.62f),
                            )
                        }
                    }
                    items(freshHits, key = { "hit:" + it.url }) { hit ->
                        DiscoveredRow(
                            hit = hit,
                            enabled = !busy,
                            onSelect = { viewModel.selectDiscovered(hit.url, hit.serverName) },
                        )
                    }
                }
            }
        }
    }

    confirmRemove?.let { target ->
        // Removing the active server signs the user out of it — say so
        // explicitly instead of showing the same generic confirm as any
        // other row, so it isn't a silent footgun.
        val isActiveTarget = target.id == state.activeId
        TvOptionDialog(
            title = if (isActiveTarget) {
                "Sign out & remove ${target.displayName}?"
            } else {
                "Remove ${target.displayName}?"
            },
            options = listOf(
                TvDialogOption(
                    key = "confirm",
                    title = if (isActiveTarget) "Sign out & remove" else "Remove",
                    subtitle = if (isActiveTarget) {
                        "This is the server you're signed into — removing it will sign you out of it."
                    } else {
                        null
                    },
                    onClick = {
                        viewModel.onRemove(target.id)
                        confirmRemove = null
                    },
                ),
                TvDialogOption(
                    key = "cancel",
                    title = "Cancel",
                    onClick = { confirmRemove = null },
                ),
            ),
            onDismiss = { confirmRemove = null },
        )
    }

}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ScanTile(
    isScanning: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val foreground = (if (isFocused) FocusedContent else Color.White)
        .let { if (enabled) it else it.copy(alpha = 0.4f) }
    Card(
        onClick = { if (enabled) onClick() },
        interactionSource = interactionSource,
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.055f),
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
        ),
        shape = CardDefaults.shape(shape = ServerRowShape),
        scale = CardDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = if (isScanning) "Scanning…" else "Scan network",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = InterFamily,
                color = foreground,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DiscoveredRow(
    hit: DiscoveryHit,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val interactionSource = remember(hit.url) { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val foreground = if (isFocused) FocusedContent else Color.White
    Card(
        onClick = { if (enabled) onSelect() },
        interactionSource = interactionSource,
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.055f),
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
        ),
        shape = CardDefaults.shape(shape = ServerRowShape),
        scale = CardDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = hit.serverName.trim().ifBlank { hit.url },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = InterFamily,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Found · ${hit.url}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = InterFamily,
                color = foreground.copy(alpha = if (isFocused) 0.68f else 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val ServerSettingsBackground = Color(0xFF17181A)
private val ServerListMaxWidth = 620.dp
private val ServerRowShape = RoundedCornerShape(8.dp)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AddServerTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val foreground = if (isFocused) FocusedContent else Color.White
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.055f),
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
        ),
        shape = CardDefaults.shape(shape = ServerRowShape),
        scale = CardDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "Add Server",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = InterFamily,
                color = foreground,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ServerRow(
    entry: ServerEntry,
    isActive: Boolean,
    isPending: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val interactionSource = remember(entry.id) { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()
        val foreground = if (isFocused) FocusedContent else Color.White
        Card(
            onClick = onSelect,
            interactionSource = interactionSource,
            colors = CardDefaults.colors(
                containerColor = if (isActive) {
                    Color.White.copy(alpha = 0.10f)
                } else {
                    Color.White.copy(alpha = 0.055f)
                },
                focusedContainerColor = FocusedContainer,
                focusedContentColor = FocusedContent,
            ),
            shape = CardDefaults.shape(shape = ServerRowShape),
            scale = CardDefaults.scale(focusedScale = 1f),
            // The TV Card is already focusable; adding .focusable() here creates
            // a dead second focus stop (no visual, OK does nothing). Keep only
            // the weight, matching AddServerTile.
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.fetchedName?.takeIf { it.isNotBlank() } ?: entry.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = InterFamily,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = entry.url,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = InterFamily,
                        color = foreground.copy(alpha = if (isFocused) 0.68f else 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isActive) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Active",
                        tint = foreground.copy(alpha = 0.72f),
                        modifier = Modifier.size(20.dp),
                    )
                } else if (isPending) {
                    Text(
                        text = "Switching…",
                        style = MaterialTheme.typography.labelSmall,
                        color = foreground.copy(alpha = 0.72f),
                    )
                }
            }
        }

        Surface(
            onClick = onRemove,
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.055f),
                contentColor = MaterialTheme.colorScheme.error,
                focusedContainerColor = FocusedContainer,
                focusedContentColor = FocusedContent,
            ),
            shape = ClickableSurfaceDefaults.shape(shape = ServerRowShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
