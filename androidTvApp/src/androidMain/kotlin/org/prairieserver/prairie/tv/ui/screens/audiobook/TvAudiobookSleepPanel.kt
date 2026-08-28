package org.prairieserver.prairie.tv.ui.screens.audiobook

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import org.prairieserver.prairie.common.player.SleepTimerChoice

private data class SleepOption(val label: String, val choice: SleepTimerChoice)

// Apple's set: Off / 15 / 30 / 60 minutes only — no end-of-chapter /
// end-of-book boundary (SleepTimerChoice was trimmed to Off / Minutes). Matches
// the phone AudiobookSleepTimerSheet.
private val SLEEP_OPTIONS = listOf(
    SleepOption("Off", SleepTimerChoice.Off),
    SleepOption("15 minutes", SleepTimerChoice.Minutes(15)),
    SleepOption("30 minutes", SleepTimerChoice.Minutes(30)),
    SleepOption("60 minutes", SleepTimerChoice.Minutes(60)),
)

/** Focusable sleep-timer overlay mapping rows to the shared VM's
 *  [SleepTimerChoice]. Replaces the phone SleepTimerSheet (spec §4.9). */
@Composable
fun TvAudiobookSleepPanel(
    currentChoice: SleepTimerChoice,
    onSelectSleep: (SleepTimerChoice) -> Unit,
    modifier: Modifier = Modifier,
    onFocusAcquisitionFailed: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusIndex = SLEEP_OPTIONS.indexOfFirst { it.choice == currentChoice }.coerceAtLeast(0)
    // Scroll only; the scaffold retries until focus is observed on the row.
    LaunchedEffect(Unit) { listState.scrollToItem(focusIndex) }

    TvAudiobookOverlayScaffold(
        title = "Sleep timer",
        initialFocus = focusRequester,
        modifier = modifier,
        onAcquisitionFailed = onFocusAcquisitionFailed,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            itemsIndexed(SLEEP_OPTIONS) { i, option ->
                TvAudiobookOverlayRow(
                    label = option.label,
                    isCurrent = option.choice == currentChoice,
                    focusRequester = if (i == focusIndex) focusRequester else null,
                    onSelect = { onSelectSleep(option.choice) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
