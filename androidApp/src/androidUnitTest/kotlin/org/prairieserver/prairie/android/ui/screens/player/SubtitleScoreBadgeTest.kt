package org.prairieserver.prairie.android.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleScoreBadgeTest {

    @Test
    fun seventyAndAboveIsHigh() {
        assertEquals(ScoreBadgeBucket.High, scoreBadgeBucket(70))
        assertEquals(ScoreBadgeBucket.High, scoreBadgeBucket(100))
    }

    @Test
    fun fortyToSixtyNineIsMedium() {
        assertEquals(ScoreBadgeBucket.Medium, scoreBadgeBucket(40))
        assertEquals(ScoreBadgeBucket.Medium, scoreBadgeBucket(69))
    }

    @Test
    fun belowFortyIsLow() {
        assertEquals(ScoreBadgeBucket.Low, scoreBadgeBucket(39))
        assertEquals(ScoreBadgeBucket.Low, scoreBadgeBucket(0))
        assertEquals(ScoreBadgeBucket.Low, scoreBadgeBucket(-5))
    }
}
