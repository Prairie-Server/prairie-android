package org.siloserver.silo.tv.ui.screens.audiobook

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.siloserver.silo.common.player.AudiobookSettingsStore

/**
 * Focusable skip-interval overlay: choose how far the transport skip buttons
 * jump (10 / 15 / 30 / 60s) for back and forward. TV equivalent of the phone
 * AudiobookSkipIntervalSheet (spec §4.9 — focusable overlay, not a sheet).
 */
@Composable
fun TvAudiobookSkipIntervalPanel(
    skipBackSeconds: Int,
    skipForwardSeconds: Int,
    onSelectSkipBack: (Int) -> Unit,
    onSelectSkipForward: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onFocusAcquisitionFailed: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    // A persisted interval outside the allowed set would attach the requester to
    // no row at all, leaving the panel with nothing to acquire.
    val focusValue = skipBackSeconds.takeIf { it in AudiobookSettingsStore.ALLOWED_SKIP }
        ?: AudiobookSettingsStore.ALLOWED_SKIP.first()

    TvAudiobookOverlayScaffold(
        title = "Skip interval",
        initialFocus = focusRequester,
        modifier = modifier,
        onAcquisitionFailed = onFocusAcquisitionFailed,
    ) {
        SectionLabel("Skip back")
        AudiobookSettingsStore.ALLOWED_SKIP.forEach { seconds ->
            TvAudiobookOverlayRow(
                label = "${seconds}s",
                isCurrent = skipBackSeconds == seconds,
                focusRequester = if (seconds == focusValue) focusRequester else null,
                onSelect = { onSelectSkipBack(seconds) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("Skip forward")
        AudiobookSettingsStore.ALLOWED_SKIP.forEach { seconds ->
            TvAudiobookOverlayRow(
                label = "${seconds}s",
                isCurrent = skipForwardSeconds == seconds,
                onSelect = { onSelectSkipForward(seconds) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = Color.White.copy(alpha = 0.7f),
    )
    Spacer(Modifier.height(4.dp))
}
