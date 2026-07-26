package org.prairieserver.prairie.tv.ui.screens.servers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import org.prairieserver.prairie.tv.R
import org.prairieserver.prairie.tv.ui.components.TvDialogOption
import org.prairieserver.prairie.tv.ui.components.TvOptionDialog
import org.prairieserver.prairie.tv.ui.theme.Spacing
import org.prairieserver.prairie.tv.ui.theme.FocusedContainer
import org.prairieserver.prairie.tv.ui.theme.FocusedContent
import org.prairieserver.prairie.tv.ui.theme.InterFamily
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * Multi-server picker for the TV app — first-run LAN discovery with branding,
 * plus focus-aware management of saved servers. Long-press / Menu opens Remove
 * (rename is intentionally omitted on TV).
 *
 * Phone pairing remains reachable via [onAddServer] → [TvServerSetupScreen],
 * which hosts the companion pairing flow.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvServerListScreen(
    onAddServer: () -> Unit,
    onSwitched: (TvServerSwitchDestination) -> Unit,
    onBack: (() -> Unit)? = null,
    autoScan: Boolean = false,
    viewModel: TvServerListViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val firstFocus = remember { FocusRequester() }
    var confirmRemove by remember { mutableStateOf<ServerEntry?>(null) }
    val isFirstRun = onBack == null

    if (onBack != null) {
        BackHandler(enabled = true) { onBack() }
    }

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

    LaunchedEffect(state.emptyRegistry) {
        // Active server removed with none left — stay on the list and scan again.
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

    LaunchedEffect(state.servers.size, freshHits.size, state.isScanning) {
        repeat(TvInitialFocusRetryCount) {
            if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(TvInitialFocusRetryDelayMs)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ServerSettingsBackground)
            .padding(start = 44.dp, top = Spacing.safeArea, end = 44.dp, bottom = Spacing.xxxl),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(
                modifier = Modifier.width(220.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (isFirstRun) {
                    Image(
                        painter = painterResource(id = R.drawable.prairie_wordmark),
                        contentDescription = "Prairie",
                        modifier = Modifier
                            .width(140.dp)
                            .height(36.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "CONNECT",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                    Text(
                        text = "Choose a server",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        text = "Pick a saved server or one found on your LAN. Sign-in comes next. Pair with phone is available when you add manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                } else {
                    Text(
                        text = "CONNECTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                    Text(
                        text = "Manage Servers",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        text = "Choose, add, or remove a Prairie server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                }
            }

            Column(
                modifier = Modifier.widthIn(max = ServerListMaxWidth),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(
                        label = if (state.isScanning) "Scanning…" else "Scan again",
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.startScan(includeDeep = true) },
                        enabled = !state.isScanning && !state.isConnecting,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    ActionTile(
                        label = "Add manually",
                        icon = Icons.Default.Add,
                        onClick = onAddServer,
                        enabled = !state.isScanning && !state.isConnecting,
                    )
                }

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

                if (state.servers.isNotEmpty()) {
                    Text(
                        text = "Saved",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(state.servers, key = { it.id }) { entry ->
                            ServerRow(
                                entry = entry,
                                isActive = entry.id == state.activeId,
                                isPending = entry.id == state.pendingSwitchToId || state.isConnecting,
                                onSelect = { viewModel.onSelect(entry.id) },
                                onRemove = { confirmRemove = entry },
                                showRemove = !isFirstRun,
                            )
                        }
                    }
                }

                if (freshHits.isNotEmpty() || state.isScanning) {
                    Text(
                        text = "Discovered",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                    if (freshHits.isEmpty() && state.isScanning) {
                        Text(
                            text = "Scanning your network for Prairie…",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.62f),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(freshHits, key = { it.url }) { hit ->
                                DiscoveredRow(
                                    hit = hit,
                                    enabled = !state.isScanning && !state.isConnecting,
                                    onSelect = {
                                        viewModel.selectDiscovered(hit.url, hit.serverName)
                                    },
                                )
                            }
                        }
                    }
                }

                if (!state.isScanning && state.servers.isEmpty() && freshHits.isEmpty()) {
                    Text(
                        text = "No servers yet — wait for the scan, or add a URL manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                }
            }
        }
    }

    confirmRemove?.let { target ->
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

private const val TvInitialFocusRetryCount = 4
private const val TvInitialFocusRetryDelayMs = 50L
private val ServerSettingsBackground = Color(0xFF17181A)
private val ServerListMaxWidth = 620.dp
private val ServerRowShape = RoundedCornerShape(8.dp)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val foreground = if (isFocused) FocusedContent else Color.White
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
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) foreground else foreground.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = InterFamily,
                color = if (enabled) foreground else foreground.copy(alpha = 0.4f),
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ServerRow(
    entry: ServerEntry,
    isActive: Boolean,
    isPending: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    showRemove: Boolean,
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

        if (showRemove) {
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
}
