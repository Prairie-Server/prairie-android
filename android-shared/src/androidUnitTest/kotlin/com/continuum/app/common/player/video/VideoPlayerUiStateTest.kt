package com.continuum.app.common.player.video

import com.continuum.app.model.playback.PlayMethod
import com.continuum.app.model.playback.PlayerSubtitleInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoPlayerUiStateTest {
    @Test
    fun loadingStateHasNoPlayableMedia() {
        val state = VideoPlayerUiState.Loading(contentId = "movie-123")

        assertEquals("movie-123", state.contentId)
        assertTrue(!state.hasPlayableMedia)
    }

    @Test
    fun readyStateHasPlayableMediaAndResumeMs() {
        val state = VideoPlayerUiState.Ready(
            contentId = "movie-123",
            fileId = 44,
            streamUrl = "https://lib.strm.cafe/api/stream/movie",
            playMethod = PlayMethod.DIRECT,
            title = "Michael",
            subtitle = "Movie",
            artworkUrl = "https://lib.strm.cafe/poster.jpg",
            startPositionSeconds = 1887.25,
            audioTrackIndex = 2,
            subtitleUrls = listOf(PlayerSubtitleInfo(index = 0, language = "en", url = "/subtitles/en.srt")),
        )

        assertTrue(state.hasPlayableMedia)
        assertEquals(PlayMethod.DIRECT, state.playMethod)
        assertEquals(1_887_250L, state.startPositionMs)
        assertEquals(2, state.audioTrackIndex)
        assertEquals(listOf(PlayerSubtitleInfo(index = 0, language = "en", url = "/subtitles/en.srt")), state.subtitleUrls)
    }
}
