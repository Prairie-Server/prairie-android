package com.continuum.app.tv.ui.screens.watchtogether

import com.continuum.app.tv.ui.screens.detail.TvWatchTogetherHostTarget
import com.continuum.app.tv.ui.screens.detail.tvWatchTogetherHostTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvWatchTogetherHostTargetTest {
    @Test
    fun movieHostsCurrentDetailVersion() {
        assertEquals(
            TvWatchTogetherHostTarget(contentId = "movie-1", fileId = 42),
            tvWatchTogetherHostTarget(
                detailContentId = "movie-1",
                detailType = "movie",
                nextUpContentId = null,
                selectedFileId = 42,
            ),
        )
    }

    @Test
    fun seriesHostsNextUpEpisodeNotContainer() {
        assertEquals(
            TvWatchTogetherHostTarget(contentId = "episode-7", fileId = 99),
            tvWatchTogetherHostTarget(
                detailContentId = "series-1",
                detailType = "series",
                nextUpContentId = "episode-7",
                selectedFileId = 99,
            ),
        )
    }

    @Test
    fun seasonWithoutNextUpDoesNotExposeDeadWatchTogetherAction() {
        assertNull(
            tvWatchTogetherHostTarget(
                detailContentId = "season-1",
                detailType = "season",
                nextUpContentId = null,
                selectedFileId = null,
            ),
        )
    }

    @Test
    fun nonVideoTypesDoNotExposeWatchTogetherAction() {
        assertNull(
            tvWatchTogetherHostTarget(
                detailContentId = "audio-1",
                detailType = "audiobook",
                nextUpContentId = null,
                selectedFileId = 1,
            ),
        )
        assertNull(
            tvWatchTogetherHostTarget(
                detailContentId = "book-1",
                detailType = "ebook",
                nextUpContentId = null,
                selectedFileId = 2,
            ),
        )
        assertNull(
            tvWatchTogetherHostTarget(
                detailContentId = "music-1",
                detailType = "music",
                nextUpContentId = null,
                selectedFileId = 3,
            ),
        )
    }
}
