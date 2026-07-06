package org.siloserver.silo.android.ui.util

import org.siloserver.silo.model.catalog.EpisodeListItem
import org.siloserver.silo.model.catalog.LeafItemUserData
import org.siloserver.silo.model.catalog.MediaItemUserState
import org.siloserver.silo.model.section.SectionItem
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
    fun returnsPlayedRewatchProgressWhenPositionIsNonzero() {
        val item = SectionItem(
            contentId = "rewatch-episode",
            type = "episode",
            title = "Rewatch",
            positionSeconds = 318.0,
            durationSeconds = 3600.0,
            userState = MediaItemUserState(played = true),
        )

        assertEquals(318.0, playbackResumePosition(item))
    }

    @Test
    fun ignoresWatchedItemsWithResetProgress() {
        assertNull(
            playbackResumePosition(
                SectionItem(
                    contentId = "played-episode",
                    type = "episode",
                    title = "Played",
                    positionSeconds = 0.0,
                    durationSeconds = 3600.0,
                    userState = MediaItemUserState(played = true),
                ),
            ),
        )
    }

    @Test
    fun ignoresTinyOrStaleCompletedProgress() {
        assertNull(
            playbackResumePosition(
                SectionItem(
                    contentId = "intro-only",
                    type = "episode",
                    title = "Intro",
                    positionSeconds = 12.0,
                    durationSeconds = 3600.0,
                    userState = MediaItemUserState(played = false),
                ),
            ),
        )
        assertNull(
            playbackResumePosition(
                SectionItem(
                    contentId = "stale-played-episode",
                    type = "episode",
                    title = "Stale",
                    positionSeconds = 3600.0,
                    durationSeconds = 3600.0,
                    userState = MediaItemUserState(played = true),
                ),
            ),
        )
    }

    @Test
    fun returnsEpisodeListRewatchProgress() {
        val episode = EpisodeListItem(
            contentId = "episode-1",
            seasonNumber = 1,
            episodeNumber = 2,
            userData = LeafItemUserData(
                played = true,
                positionSeconds = 600.0,
                durationSeconds = 3600.0,
            ),
        )

        assertEquals(600.0, playbackResumePosition(episode))
    }
}
