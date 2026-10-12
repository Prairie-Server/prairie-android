package org.prairieserver.prairie.android.ui.screens.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.prairieserver.prairie.common.player.PlayerStatsSnapshot
import org.prairieserver.prairie.common.player.healthRows
import org.prairieserver.prairie.playback.stats.PlaybackStatsContext
import org.prairieserver.prairie.playback.stats.displayLine
import org.prairieserver.prairie.playback.stats.planRows

@Composable
fun PlaybackStatsSheet(
    isVisible: Boolean,
    stats: PlayerStatsSnapshot,
    onDismiss: () -> Unit,
    // Back affordance: hands over to the settings menu (wired in PlayerOverlay).
    onBack: (() -> Unit)? = null,
    sessionId: String? = null,
    playMethod: String? = null,
    positionLabel: String? = null,
    tabletopPaneHeight: Dp? = null,
    // Prairie: plan / audio track / stream path / quality, shared with the TV HUD.
    playbackContext: PlaybackStatsContext = PlaybackStatsContext(),
) {
    if (!isVisible) return

    val controller = rememberPlayerMenuController(onDismiss)

    PlayerMenu(controller = controller, tabletopPaneHeight = tabletopPaneHeight) {
        PlayerPanelHeader(
            title = "Stats for nerds",
            onClose = { controller.dismiss() },
            onBack = onBack?.let { back -> { controller.dismiss(then = back) } },
            backDescription = "Back to settings",
        )
        PlayerMenuScrollColumn(horizontalPadding = 12.dp) {
            val planRows = playbackContext.planRows()
            if (planRows.isNotEmpty()) {
                PlayerSectionHeader("Playback plan")
                PlayerGroup {
                    planRows.forEachIndexed { index, row ->
                        StatsBlockRow(label = row.label, value = row.value, separator = index > 0)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            PlayerSectionHeader("Current stream")
            val rows = buildList {
                sessionId?.takeIf { it.isNotBlank() }?.let { add("Session" to it) }
                playMethod?.takeIf { it.isNotBlank() }?.let { add("Play method" to it) }
                positionLabel?.takeIf { it.isNotBlank() }?.let { add("Position" to it) }
                addAll(stats.mobileStatsRows())
                stats.healthRows().forEach { add(it.label to it.value) }
            }
            PlayerGroup {
                if (rows.isEmpty()) {
                    StatsRow(label = "Waiting for player data", value = "", separator = false)
                } else {
                    rows.forEachIndexed { index, (label, value) ->
                        StatsRow(label = label, value = value, separator = index > 0)
                    }
                }
            }

            if (!stats.events.isEmpty) {
                Spacer(Modifier.height(12.dp))
                PlayerSectionHeader("Recent player events")
                val offsetMs = remember(stats.events) {
                    java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
                }
                PlayerGroup {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        stats.events.entries.forEach { event ->
                            Text(
                                text = event.displayLine(offsetMs),
                                style = PlayerType.RowDetail.copy(color = PlayerChrome.Paper),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Label over a wrapping value, for the long plan / stream / track lines. */
@Composable
private fun StatsBlockRow(label: String, value: String, separator: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .playerGroupSeparator(separator)
            .heightIn(min = 40.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = PlayerType.RowDetail.copy(fontSize = PlayerType.Value.fontSize),
        )
        Text(
            text = value,
            style = PlayerType.Value.copy(color = PlayerChrome.Paper),
        )
    }
}

@Composable
private fun StatsRow(label: String, value: String, separator: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .playerGroupSeparator(separator)
            .heightIn(min = 40.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = PlayerType.RowDetail.copy(fontSize = PlayerType.Value.fontSize),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Column(modifier = Modifier.weight(1.4f)) {
            Text(
                text = value,
                style = PlayerType.Value.copy(color = PlayerChrome.Paper),
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal fun PlayerStatsSnapshot.mobileStatsRows(): List<Pair<String, String>> = buildList {
    backendDisplayName?.let { add("Backend" to it) }
    backendRoute?.let { add("Route" to it) }
    subtitleRendering?.let { add("Subtitles" to it) }
    hardContainers?.let { add("Hard containers" to it) }
    videoCodec?.let { add("Video codec" to it) }
    resolution?.let { add("Resolution" to it) }
    frameRate?.let { add("Frame rate" to String.format(java.util.Locale.US, "%.3f fps", it)) }
    hdrMode?.let { add("HDR mode" to it) }
    videoDecoderName?.let { add("Video decoder" to it) }
    audioCodec?.let { add("Audio codec" to it) }
    audioDecoderName?.let { add("Audio decoder" to it) }
    // Media3's onBandwidthEstimate value — measured network throughput, not the
    // media bitrate. Labelling it "Bitrate" reads as a ~19 Mbps stream claiming
    // 151 Mbps on a fast LAN.
    bitrateBps?.let { add("Estimated bandwidth" to formatStatsBitrate(it)) }
    if (droppedFrames > 0) add("Dropped frames" to droppedFrames.toString())
    if (audioUnderruns > 0) add("Audio underruns" to audioUnderruns.toString())
}

internal fun formatStatsBitrate(bps: Long): String = when {
    bps >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f Mbps", bps / 1_000_000.0)
    bps >= 1_000 -> String.format(java.util.Locale.US, "%.0f Kbps", bps / 1_000.0)
    else -> "$bps bps"
}
