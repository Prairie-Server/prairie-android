package com.continuum.app.common.player.video

import com.continuum.app.model.playback.PlayMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class VideoPlaybackSessionCoordinatorTest {
    @Test
    fun startPassesRequestToStarter() = runTest {
        val starter = FakePlaybackStarter()
        val coordinator = VideoPlaybackSessionCoordinator(starter)
        val request = VideoPlaybackStartRequest(
            contentId = "movie-123",
            preferredFileId = 44,
            roomId = "room-1",
            resumePositionOverride = 0.0,
        )

        coordinator.start(request)

        assertEquals(listOf(request), starter.requests)
    }

    @Test
    fun startMapsReadyResultToReadyUiState() = runTest {
        val starter = FakePlaybackStarter(
            result = VideoPlaybackStartResult.Ready(
                contentId = "movie-123",
                fileId = 44,
                streamUrl = "https://lib.strm.cafe/api/stream/movie",
                playMethod = PlayMethod.DIRECT,
                title = "Michael",
                subtitle = "Movie",
                artworkUrl = "https://lib.strm.cafe/poster.jpg",
                startPositionSeconds = 1887.25,
            ),
        )
        val coordinator = VideoPlaybackSessionCoordinator(starter)

        val state = coordinator.start(baseRequest())

        val ready = assertIs<VideoPlayerUiState.Ready>(state)
        assertEquals("movie-123", ready.contentId)
        assertEquals(44, ready.fileId)
        assertEquals("https://lib.strm.cafe/api/stream/movie", ready.streamUrl)
        assertEquals(PlayMethod.DIRECT, ready.playMethod)
        assertEquals("Michael", ready.title)
        assertEquals("Movie", ready.subtitle)
        assertEquals("https://lib.strm.cafe/poster.jpg", ready.artworkUrl)
        assertEquals(1887.25, ready.startPositionSeconds)
    }

    @Test
    fun startMapsErrorResultToErrorUiState() = runTest {
        val starter = FakePlaybackStarter(
            result = VideoPlaybackStartResult.Error(
                contentId = "movie-123",
                message = "Failed to start playback",
            ),
        )
        val coordinator = VideoPlaybackSessionCoordinator(starter)

        val state = coordinator.start(baseRequest())

        val error = assertIs<VideoPlayerUiState.Error>(state)
        assertEquals("movie-123", error.contentId)
        assertEquals("Failed to start playback", error.message)
    }

    @Test
    fun startPreservesExplicitZeroResumeOverride() = runTest {
        val starter = FakePlaybackStarter()
        val coordinator = VideoPlaybackSessionCoordinator(starter)

        coordinator.start(baseRequest(resumePositionOverride = 0.0))

        assertEquals(0.0, starter.requests.single().resumePositionOverride)
    }

    @Test
    fun startPreservesInvalidResumeOverrideForStarter() = runTest {
        val starter = FakePlaybackStarter()
        val coordinator = VideoPlaybackSessionCoordinator(starter)

        coordinator.start(baseRequest(resumePositionOverride = -12.5))

        assertEquals(-12.5, starter.requests.single().resumePositionOverride)
    }

    private fun baseRequest(
        resumePositionOverride: Double? = 1887.25,
    ) = VideoPlaybackStartRequest(
        contentId = "movie-123",
        preferredFileId = 44,
        roomId = null,
        resumePositionOverride = resumePositionOverride,
    )

    private class FakePlaybackStarter(
        var result: VideoPlaybackStartResult = VideoPlaybackStartResult.Ready(
            contentId = "movie-123",
            fileId = 44,
            streamUrl = "https://lib.strm.cafe/api/stream/movie",
            playMethod = PlayMethod.DIRECT,
            title = "Michael",
            subtitle = "Movie",
            artworkUrl = "https://lib.strm.cafe/poster.jpg",
            startPositionSeconds = 1887.25,
        ),
    ) : VideoPlaybackStarter {
        val requests = mutableListOf<VideoPlaybackStartRequest>()

        override suspend fun start(request: VideoPlaybackStartRequest): VideoPlaybackStartResult {
            requests += request
            return result
        }
    }
}
