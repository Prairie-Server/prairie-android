package com.continuum.app.common.player

/**
 * Sleep timer choices. [EndOfChapter] is a special value the VM
 * resolves to a duration based on the current chapter's remaining
 * time; [Off] cancels any active timer.
 */
sealed class SleepTimerChoice {
    data object Off : SleepTimerChoice()
    data class Minutes(val minutes: Int) : SleepTimerChoice()
    data object EndOfChapter : SleepTimerChoice()
}
