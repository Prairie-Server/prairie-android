package org.siloserver.silo.tv.ui.screens.detail

import kotlinx.coroutines.test.runTest
import org.siloserver.silo.model.catalog.AudioTrack
import org.siloserver.silo.model.catalog.FileVersion
import org.siloserver.silo.model.catalog.SubtitleTrack
import org.siloserver.silo.playback.audioTrackFingerprint
import org.siloserver.silo.playback.subtitleTrackFingerprint
import org.siloserver.silo.repository.port.LocalTrackSelection
import org.siloserver.silo.repository.port.OutboxHandle
import org.siloserver.silo.repository.port.UserItemStatePort
import org.siloserver.silo.repository.port.WriteOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvTrackSelectionPersistenceTest {
    @Test
    fun nextUpSelectionRecordsEpisodeFileAndCombinedSubtitleFingerprint() = runTest {
        val version = version()
        val payload = buildTrackSelectionPersistence(
            targetContentId = "episode-42",
            version = version,
            selectedAudioIndex = 1,
            selectedSubtitleIndex = 0,
        )
        val port = RecordingTrackSelectionPort()

        recordTrackSelection(port, payload)

        assertEquals("episode-42", port.contentId)
        assertEquals(22, port.fileId)
        assertEquals(audioTrackFingerprint(version.audioTracks!![1]), port.audioFingerprint)
        // Combined index 0 is the external catalog track at ordinal 1.
        assertEquals(subtitleTrackFingerprint(version.subtitleTracks!![1]), port.subtitleFingerprint)
    }

    @Test
    fun durableFingerprintsRestoreToAudioOrdinalAndCombinedSubtitleIndex() {
        val version = version()
        val restored = restoreTrackSelection(
            version,
            LocalTrackSelection(
                audioFingerprint = audioTrackFingerprint(version.audioTracks!![1]),
                subtitleFingerprint = subtitleTrackFingerprint(version.subtitleTracks!![1]),
            ),
        )

        assertEquals(1, restored.audioIndex)
        assertEquals(0, restored.subtitleIndex)
    }

    @Test
    fun nextUpSessionIsKeyedToEpisodeIdentity() {
        TvDetailTrackSelectionSession.remember("episode-session-a", 22, 1, 0)
        TvDetailTrackSelectionSession.remember("episode-session-b", 23, null, -1)

        assertEquals(TvDetailTrackSelectionSession.Saved(22, 1, 0), TvDetailTrackSelectionSession.recall("episode-session-a"))
        assertEquals(TvDetailTrackSelectionSession.Saved(23, null, -1), TvDetailTrackSelectionSession.recall("episode-session-b"))
    }

    @Test
    fun lateRestoreCannotApplyAfterEpisodeOrVersionChanges() {
        assertTrue(shouldApplyNextUpTrackRestore("episode-42", "episode-42", 22, 22))
        assertFalse(shouldApplyNextUpTrackRestore("episode-43", "episode-42", 22, 22))
        assertFalse(shouldApplyNextUpTrackRestore("episode-42", "episode-42", 23, 22))
    }

    private fun version() = FileVersion(
        fileId = 22,
        audioTracks = listOf(
            AudioTrack(index = 4, codec = "aac", language = "eng"),
            AudioTrack(index = 9, codec = "ac3", language = "jpn", title = "Commentary"),
        ),
        subtitleTracks = listOf(
            SubtitleTrack(index = 10, codec = "srt", language = "eng"),
            SubtitleTrack(index = 0, codec = "srt", language = "fre", external = true),
            SubtitleTrack(index = 11, codec = "ass", language = "jpn"),
        ),
    )
}

private class RecordingTrackSelectionPort : UserItemStatePort {
    var contentId: String? = null
    var fileId: Int? = null
    var audioFingerprint: String? = null
    var subtitleFingerprint: String? = null

    override suspend fun recordWatched(contentId: String, watched: Boolean) = OutboxHandle.NONE
    override suspend fun recordFavorite(contentId: String, favorite: Boolean) = OutboxHandle.NONE
    override suspend fun recordRating(contentId: String, rating: Int?) = OutboxHandle.NONE
    override suspend fun resolve(handle: OutboxHandle, outcome: WriteOutcome) = Unit

    override suspend fun recordAudioTrackSelection(
        contentId: String,
        fileId: Int,
        audioFingerprint: String?,
    ) {
        this.contentId = contentId
        this.fileId = fileId
        this.audioFingerprint = audioFingerprint
    }

    override suspend fun recordSubtitleTrackSelection(
        contentId: String,
        fileId: Int,
        subtitleFingerprint: String?,
    ) {
        this.contentId = contentId
        this.fileId = fileId
        this.subtitleFingerprint = subtitleFingerprint
    }
}
