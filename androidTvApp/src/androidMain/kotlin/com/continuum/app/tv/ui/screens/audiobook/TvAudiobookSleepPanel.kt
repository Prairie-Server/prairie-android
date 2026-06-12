package com.continuum.app.tv.ui.screens.audiobook

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
)

/** Focusable sleep-timer overlay mapping rows to the shared VM's
 *  [SleepTimerChoice]. Replaces the phone SleepTimerSheet (spec §4.9). */
@Composable
fun TvAudiobookSleepPanel(
    currentChoice: SleepTimerChoice,
    onSelectSleep: (SleepTimerChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvAudiobookOverlayScaffold(title = "Sleep timer", modifier = modifier) {
        SLEEP_OPTIONS.forEach { option ->
            TvAudiobookOverlayRow(
                label = option.label,
                isCurrent = option.choice == currentChoice,
                onSelect = { onSelectSleep(option.choice) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
