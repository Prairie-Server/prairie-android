package com.continuum.app.android.ui.util

import com.continuum.app.model.catalog.MediaItemUserState
import com.continuum.app.model.section.SectionItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackResumePositionTest {

    @Test
    fun returnsSectionProgressWhenPlayableResumeExists() {
        val item = SectionItem(
            contentId = "episode-1",
            type = "episode",
            title = "The Thief",
            positionSeconds = 318.0,
            durationSeconds = 3600.0,
            userState = MediaItemUserState(played = false),
        )

        assertEquals(318.0, playbackResumePosition(item))
    }

    @Test
    fun ignoresPlayedOrTinyProgress() {
        assertNull(
            playbackResumePosition(
                SectionItem(
                    contentId = "played-episode",
                    type = "episode",
                    title = "Played",
                    positionSeconds = 318.0,
                    userState = MediaItemUserState(played = true),
                ),
            ),
        )
        assertNull(
            playbackResumePosition(
                SectionItem(
                    contentId = "intro-only",
                    type = "episode",
                    title = "Intro",
                    positionSeconds = 12.0,
                    userState = MediaItemUserState(played = false),
                ),
            ),
        )
    }
}
