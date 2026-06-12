package com.continuum.app.audiobook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Boundary-crossing math used by the audiobook player's end-of-chapter /
 * end-of-book sleep timer. The VM's position watcher delegates here so the
 * trigger logic is shared with the phone + TV UIs and tested once.
 */
class AudiobookChaptersSleepTriggerTest {

    private val chapters = listOf(
        AudiobookChapter(startSeconds = 0.0, endSeconds = 10.0),
        AudiobookChapter(startSeconds = 10.0, endSeconds = 25.0),
        AudiobookChapter(startSeconds = 25.0, endSeconds = 40.0),
    )

    @Test
    fun currentChapterEndReturnsContainingChapterEndMidChapter() {
        assertEquals(25.0, AudiobookChapters.currentChapterEndSeconds(chapters, 12.0))
    }

    @Test
    fun currentChapterEndReturnsLastChapterEndPastTheEnd() {
        assertEquals(40.0, AudiobookChapters.currentChapterEndSeconds(chapters, 999.0))
    }

    @Test
    fun currentChapterEndIsNullForEmptyChapters() {
        assertNull(AudiobookChapters.currentChapterEndSeconds(emptyList(), 5.0))
    }

    @Test
    fun positionExactlyOnBoundaryResolvesToNextChapterEnd() {
        // Boundary is start-inclusive: position == chapter[0].endSeconds (10.0)
        // belongs to chapter[1], whose end is 25.0.
        assertEquals(25.0, AudiobookChapters.currentChapterEndSeconds(chapters, 10.0))
    }

    @Test
    fun bookEndUsesDurationWhenItExceedsLastChapter() {
        assertEquals(50.0, AudiobookChapters.bookEndSeconds(chapters, 50.0))
    }

    @Test
    fun bookEndFallsBackToLastChapterWhenDurationShort() {
        assertEquals(40.0, AudiobookChapters.bookEndSeconds(chapters, 0.0))
        assertEquals(40.0, AudiobookChapters.bookEndSeconds(chapters, 30.0))
    }

    @Test
    fun hasCrossedBoundaryDetectsTheCrossing() {
        assertTrue(AudiobookChapters.hasCrossedBoundary(10.0, 12.0, 11.0))
        assertFalse(AudiobookChapters.hasCrossedBoundary(12.0, 14.0, 11.0))
        assertFalse(AudiobookChapters.hasCrossedBoundary(10.0, 10.5, 11.0))
        assertTrue(AudiobookChapters.hasCrossedBoundary(10.0, 11.0, 11.0))
    }

    @Test
    fun singleChapterCurrentEndEqualsBookEnd() {
        val single = listOf(AudiobookChapter(startSeconds = 0.0, endSeconds = 100.0))
        assertEquals(
            AudiobookChapters.bookEndSeconds(single, 100.0),
            AudiobookChapters.currentChapterEndSeconds(single, 42.0),
        )
    }
}
