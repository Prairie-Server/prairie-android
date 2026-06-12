package com.continuum.app.android.ui.screens.audiobook

import com.continuum.app.model.catalog.VersionChapter
import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterProgressMathTest {
    private val chapters = listOf(
        VersionChapter(index = 0, title = "One", startSeconds = 0.0, endSeconds = 100.0),
        VersionChapter(index = 1, title = "Two", startSeconds = 100.0, endSeconds = 250.0),
        VersionChapter(index = 2, title = "Three", startSeconds = 250.0, endSeconds = 400.0),
    )

    @Test
    fun chapterRelativeSeconds_midChapter() {
        assertEquals(40.0, chapterRelativeSeconds(chapters, currentIndex = 1, positionSeconds = 140.0), 0.001)
    }

    @Test
    fun chapterRelativeSeconds_clampsBelowStart() {
        // Position momentarily behind the chapter start clamps to 0.
        assertEquals(0.0, chapterRelativeSeconds(chapters, currentIndex = 1, positionSeconds = 90.0), 0.001)
    }

    @Test
    fun chapterRelativeDuration_isChapterLength() {
        assertEquals(150.0, chapterRelativeDuration(chapters, currentIndex = 1), 0.001)
    }

    @Test
    fun degradesToWholeBookWhenNoChapters() {
        assertEquals(140.0, chapterRelativeSeconds(emptyList(), currentIndex = 0, positionSeconds = 140.0), 0.001)
        assertEquals(0.0, chapterRelativeDuration(emptyList(), currentIndex = 0), 0.001)
    }

    @Test
    fun outOfRangeIndexIsSafe() {
        assertEquals(0.0, chapterRelativeSeconds(chapters, currentIndex = 9, positionSeconds = 140.0), 0.001)
        assertEquals(0.0, chapterRelativeDuration(chapters, currentIndex = 9), 0.001)
    }
}
