package org.siloserver.silo.common.player

import org.siloserver.silo.audiobook.AudiobookChapter
import org.siloserver.silo.common.player.AudiobookPlayerViewModel.Companion.shouldPauseForSleep
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure coverage of the boundary sleep-timer decision the VM's position watcher
 * delegates to. The VM itself (Media3 / session / network collaborators) is not
 * practically unit-testable, but the trigger math is — see also
 * AudiobookChaptersSleepTriggerTest for the underlying chapter helpers.
 */
class AudiobookPlayerSleepWatcherTest {

    private val chapters = listOf(
        AudiobookChapter(startSeconds = 0.0, endSeconds = 10.0),
        AudiobookChapter(startSeconds = 10.0, endSeconds = 25.0),
        AudiobookChapter(startSeconds = 25.0, endSeconds = 40.0),
    )
    private val duration = 40.0

    @Test
    fun endOfChapterFiresOnlyOnCrossingTheContainingChapterEnd() {
        // Mid-chapter: no pause.
        assertFalse(
            shouldPauseForSleep(SleepTimerChoice.EndOfChapter, chapters, duration, 5.0, 8.0),
        )
        // Crossing chapter[0] end (10.0): pause.
        assertTrue(
            shouldPauseForSleep(SleepTimerChoice.EndOfChapter, chapters, duration, 8.0, 12.0),
        )
        // Already past the boundary: no spurious second pause.
        assertFalse(
            shouldPauseForSleep(SleepTimerChoice.EndOfChapter, chapters, duration, 12.0, 14.0),
        )
    }

    @Test
    fun endOfBookFiresOnlyOnCrossingTheBookEnd() {
        // Crossing a mid-book chapter boundary must NOT fire end-of-book.
        assertFalse(
            shouldPauseForSleep(SleepTimerChoice.EndOfBook, chapters, duration, 8.0, 12.0),
        )
        // Crossing the book end (40.0): pause.
        assertTrue(
            shouldPauseForSleep(SleepTimerChoice.EndOfBook, chapters, duration, 38.0, 41.0),
        )
    }

    @Test
    fun offAndMinutesNeverFireViaTheWatcher() {
        assertFalse(
            shouldPauseForSleep(SleepTimerChoice.Off, chapters, duration, 8.0, 12.0),
        )
        assertFalse(
            shouldPauseForSleep(SleepTimerChoice.Minutes(5), chapters, duration, 38.0, 41.0),
        )
    }

    @Test
    fun endOfChapterWithNoChaptersDegradesToBookEnd() {
        // No chapters: end-of-chapter falls back to the book end (duration),
        // so it does not pause early at an arbitrary mid-book position.
        assertFalse(
            shouldPauseForSleep(SleepTimerChoice.EndOfChapter, emptyList(), duration, 8.0, 12.0),
        )
        assertTrue(
            shouldPauseForSleep(SleepTimerChoice.EndOfChapter, emptyList(), duration, 38.0, 41.0),
        )
    }
}
