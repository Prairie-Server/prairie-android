package org.siloserver.silo.android.ui.screens.audiobook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.siloserver.silo.common.player.AudiobookSettingsStore

/**
 * Skip-interval picker bottom sheet. Lets the user set how far the
 * skip-back and skip-forward transport buttons jump (10 / 15 / 30 / 60s),
 * replacing the old fixed ±30s. Values come from [AudiobookSettingsStore.ALLOWED_SKIP].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookSkipIntervalSheet(
    skipBackSeconds: Int,
    skipForwardSeconds: Int,
    onSkipBackSelected: (Int) -> Unit,
    onSkipForwardSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
            Text(
                text = "Skip back",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AudiobookSettingsStore.ALLOWED_SKIP.forEach { seconds ->
                IntervalRow(
                    label = "${seconds}s",
                    selected = skipBackSeconds == seconds,
                    onClick = { onSkipBackSelected(seconds) },
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Skip forward",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AudiobookSettingsStore.ALLOWED_SKIP.forEach { seconds ->
                IntervalRow(
                    label = "${seconds}s",
                    selected = skipForwardSeconds == seconds,
                    onClick = { onSkipForwardSelected(seconds) },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun IntervalRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
