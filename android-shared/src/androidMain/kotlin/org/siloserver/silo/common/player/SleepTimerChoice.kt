package org.siloserver.silo.common.player

/**
 * Sleep timer choices shared by the phone and TV audiobook players.
 * [EndOfChapter] / [EndOfBook] are resolved by the VM against the current
 * chapter / book boundary (see AudiobookChapters); [Off] cancels any active
 * timer.
 */
sealed class SleepTimerChoice {
    data object Off : SleepTimerChoice()
    data class Minutes(val minutes: Int) : SleepTimerChoice()
    data object EndOfChapter : SleepTimerChoice()
    data object EndOfBook : SleepTimerChoice()
}
