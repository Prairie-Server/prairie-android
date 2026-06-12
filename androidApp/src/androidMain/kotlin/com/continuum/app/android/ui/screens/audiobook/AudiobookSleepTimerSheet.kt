package com.continuum.app.android.ui.screens.audiobook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
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
import com.continuum.app.common.player.SleepTimerChoice

/**
 * Sleep timer picker bottom sheet. Standard presets (5 / 10 / 15 / 30
 * / 45 / 60 min), an "End of chapter" option (resolves to the
 * current chapter's remaining seconds), and "Off" to cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookSleepTimerSheet(
    currentChoice: SleepTimerChoice,
    minutesLeft: Int?,
    onChoiceSelected: (SleepTimerChoice) -> Unit,
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
                text = "Sleep timer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (minutesLeft != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${minutesLeft} min remaining",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            val items = listOf(
                "5 minutes" to SleepTimerChoice.Minutes(5),
                "10 minutes" to SleepTimerChoice.Minutes(10),
                "15 minutes" to SleepTimerChoice.Minutes(15),
                "30 minutes" to SleepTimerChoice.Minutes(30),
                "45 minutes" to SleepTimerChoice.Minutes(45),
                "60 minutes" to SleepTimerChoice.Minutes(60),
                "End of chapter" to SleepTimerChoice.EndOfChapter,
                "Off" to SleepTimerChoice.Off,
            )

            items.forEach { (label, choice) ->
                ChoiceRow(
                    label = label,
                    selected = choiceEquals(currentChoice, choice),
                    onClick = {
                        onChoiceSelected(choice)
                        onDismiss()
                    },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun choiceEquals(a: SleepTimerChoice, b: SleepTimerChoice): Boolean = when {
    a is SleepTimerChoice.Off && b is SleepTimerChoice.Off -> true
    a is SleepTimerChoice.EndOfChapter && b is SleepTimerChoice.EndOfChapter -> true
    a is SleepTimerChoice.Minutes && b is SleepTimerChoice.Minutes -> a.minutes == b.minutes
    else -> false
}

@Composable
private fun ChoiceRow(
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
        Icon(
            imageVector = Icons.Filled.Bedtime,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.padding(start = 12.dp))
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
