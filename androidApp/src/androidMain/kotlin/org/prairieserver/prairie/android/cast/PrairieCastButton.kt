package org.prairieserver.prairie.android.cast

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cast
import org.prairieserver.prairie.android.ui.theme.PrairieForeground
import org.prairieserver.prairie.android.ui.components.PrairieDialogActionStyle
import org.prairieserver.prairie.android.ui.components.PrairieDialogAction
import org.prairieserver.prairie.android.ui.components.PrairieDialog
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer

/**
 * Google Cast (Chromecast) button + route picker for the video player top bar.
 *
 * Distinct from the NSD/mDNS PrairieCast device-remote button. Tapping it stages
 * the cast-ready stream via [onStartCast] (Tier-2 session) and opens the device
 * picker; the [PrairieCastSessionManager] loads the media once a device connects.
 */
@Composable
fun PrairieCastButton(
    onStartCast: () -> Unit,
    modifier: Modifier = Modifier,
    castManager: PrairieCastSessionManager = koinInject(),
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
        PrairieDialog(
            title = "Cast to",
            message = if (routes.isEmpty()) {
                "No cast devices found. Make sure your Chromecast is on the same Wi-Fi network."
            } else {
                null
            },
            onDismissRequest = { showPicker = false },
            icon = Icons.Rounded.Cast,
            actions = buildList {
                if (castState.isConnected) {
                    add(
                        PrairieDialogAction(
                            label = "Stop casting",
                            style = PrairieDialogActionStyle.Destructive,
                            onClick = {
                                castManager.disconnect()
                                showPicker = false
                            },
                        ),
                    )
                }
                add(PrairieDialogAction("Close", { showPicker = false }))
            },
        ) {
            if (routes.isNotEmpty()) {
                Column {
                    routes.forEach { route ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (route.isSelected) Color.White.copy(alpha = 0.07f) else Color.Transparent)
                                .clickable {
                                    // Stage the cast stream, then join the route.
                                    onStartCast()
                                    castManager.selectRoute(route.id)
                                    showPicker = false
                                }
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tv,
                                contentDescription = null,
                                tint = PrairieForeground.copy(alpha = 0.78f),
                                modifier = Modifier.size(22.dp),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 14.dp),
                            ) {
                                Text(text = route.name, color = PrairieForeground, fontSize = 15.sp)
                                if (route.isConnecting) {
                                    Text(text = "Connecting…", fontSize = 12.sp)
                                }
                            }
                            if (route.isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = PrairieForeground,
                                    modifier = Modifier.size(20.dp),
                                )
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
                    TextButton(onClick = { showPicker = false }) { Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Close") }
                }
            dismissButton = {
        )
            }
        }
    }
}
