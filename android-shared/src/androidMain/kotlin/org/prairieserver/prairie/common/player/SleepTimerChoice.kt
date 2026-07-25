package org.prairieserver.prairie.common.player

/**
 * Sleep timer choices shared by the phone and TV audiobook players.
 * [Minutes] runs a fixed wall-clock countdown; [Off] cancels any active timer.
 *
 * Matches Apple, which offers only Off / 15 / 30 / 60-minute timers — there is
 * no end-of-chapter / end-of-book boundary option (see Apple `SleepTimer.swift`).
 */
sealed class SleepTimerChoice {
    data object Off : SleepTimerChoice()
    data class Minutes(val minutes: Int) : SleepTimerChoice()
}
