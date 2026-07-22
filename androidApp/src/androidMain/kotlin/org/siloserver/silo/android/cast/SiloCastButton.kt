package org.siloserver.silo.android.cast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject

/**
 * Google Cast (Chromecast) button + route picker for the video player top bar.
 *
 * Distinct from the NSD/mDNS SiloCast device-remote button. Tapping it stages
 * the cast-ready stream via [onStartCast] (Tier-2 session) and opens the device
 * picker; the [SiloCastSessionManager] loads the media once a device connects.
 */
@Composable
fun SiloCastButton(
    onStartCast: () -> Unit,
    modifier: Modifier = Modifier,
    castManager: SiloCastSessionManager = koinInject(),
) {
    val castState by castManager.castState.collectAsState()
    val routes by castManager.availableRoutes.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    // Active-scan discovery runs only while the picker is open (battery).
    DisposableEffect(showPicker) {
        if (showPicker) castManager.startDiscovery()
        onDispose { if (showPicker) castManager.stopDiscovery() }
    }

    IconButton(
        onClick = {
            castManager.refreshRoutes()
            showPicker = true
        },
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            imageVector = if (castState.isConnected) Icons.Default.CastConnected else Icons.Default.Cast,
            contentDescription = "Cast",
            tint = if (castState.isConnected) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(22.dp),
        )
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Cast to") },
            text = {
                if (routes.isEmpty()) {
                    Text(
                        text = "No cast devices found. Make sure your Chromecast is on the same Wi-Fi network.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        routes.forEachIndexed { index, route ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }
                            TextButton(
                                onClick = {
                                    // Stage the cast stream, then join the route.
                                    onStartCast()
                                    castManager.selectRoute(route.id)
                                    showPicker = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = route.name,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        if (route.isConnecting) {
                                            Text(
                                                text = "Connecting…",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                    if (route.isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (castState.isConnected) {
                    TextButton(
                        onClick = {
                            castManager.disconnect()
                            showPicker = false
                        },
                    ) { Text("Stop casting") }
                } else {
                    TextButton(onClick = { showPicker = false }) { Text("Close") }
                }
            },
            dismissButton = {
                if (castState.isConnected) {
                    TextButton(onClick = { showPicker = false }) { Text("Close") }
                }
            },
        )
    }
}
