package com.continuum.app.tv.ui.screens.audiobook

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.continuum.app.common.player.SleepTimerChoice

private data class SleepOption(val label: String, val choice: SleepTimerChoice)

private val SLEEP_OPTIONS = listOf(
    SleepOption("Off", SleepTimerChoice.Off),
    SleepOption("5 minutes", SleepTimerChoice.Minutes(5)),
    SleepOption("10 minutes", SleepTimerChoice.Minutes(10)),
    SleepOption("15 minutes", SleepTimerChoice.Minutes(15)),
    SleepOption("30 minutes", SleepTimerChoice.Minutes(30)),
    SleepOption("45 minutes", SleepTimerChoice.Minutes(45)),
    SleepOption("60 minutes", SleepTimerChoice.Minutes(60)),
    SleepOption("End of chapter", SleepTimerChoice.EndOfChapter),
    SleepOption("End of book", SleepTimerChoice.EndOfBook),
)

/** Focusable sleep-timer overlay mapping rows to the shared VM's
 *  [SleepTimerChoice]. Replaces the phone SleepTimerSheet (spec §4.9). */
@Composable
fun TvAudiobookSleepPanel(
    currentChoice: SleepTimerChoice,
    onSelectSleep: (SleepTimerChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusIndex = SLEEP_OPTIONS.indexOfFirst { it.choice == currentChoice }.coerceAtLeast(0)
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    TvAudiobookOverlayScaffold(title = "Sleep timer", modifier = modifier) {
        SLEEP_OPTIONS.forEachIndexed { i, option ->
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
