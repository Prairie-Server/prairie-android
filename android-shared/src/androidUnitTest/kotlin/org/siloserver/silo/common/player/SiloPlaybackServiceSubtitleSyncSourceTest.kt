package org.siloserver.silo.common.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiloPlaybackServiceSubtitleSyncSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/org/siloserver/silo/common/player/SiloPlaybackService.kt",
    ).readText()

    @Test
    fun subtitleSyncChangesReparseCurrentMediaItemAtSamePosition() {
        assertTrue(
            // Call site now sources the engine from activePlayer (engine-swap support);
            // the reparse still reprepares the current item to rebuild sidecar cues.
            source.contains("reparseCurrentMediaItemAtCurrentPosition(p, offsetMs)"),
            "subtitle sync changes must reprepare the current item so parsed sidecar cue timestamps are rebuilt",
        )
        assertTrue(source.contains("player.setMediaItems(mediaItems, currentIndex, positionMs)"))
        assertTrue(source.contains("player.prepare()"))
        assertTrue(source.contains("player.playWhenReady = playWhenReady"))
    }

    @Test
    fun syncOffsetsAreForwardedToMpvInsteadOfMedia3OnlyProcessors() {
        assertTrue(source.contains("import org.siloserver.silo.common.player.mpv.MpvPlayer"))
        assertTrue(source.contains("(p as? MpvPlayer)?.setAudioDelayMs(delayMs)"))
        assertTrue(source.contains("(p as? MpvPlayer)?.setSubtitleDelayMs(offsetMs)"))
    }

    @Test
    fun savedSyncOffsetsAreAppliedWhenBindingMpvAfterEngineSwap() {
        val bindNewPlayerBody = source.substringAfter("private fun bindNewPlayer(")
            .substringBefore("} catch (t: Throwable)")

        assertTrue(
            bindNewPlayerBody.contains("applyCurrentSyncOffsetsToMpv(newPlayer)"),
            "binding a newly-created MPV player must push already-saved sync offsets even when settings flows do not emit again",
        )
        assertTrue(source.contains("val mpvPlayer = player as? MpvPlayer ?: return"))
        assertTrue(source.contains("mpvPlayer.setAudioDelayMs(delayProcessor.getActiveDelayMs())"))
        assertTrue(source.contains("mpvPlayer.setSubtitleDelayMs(subtitleOffsetHolder.getOffsetMs())"))
    }

    @Test
    fun subtitleSyncReparseIsLimitedToItemsWithTextTracks() {
        assertTrue(source.contains("val hasConfiguredSubtitles ="))
        assertTrue(source.contains("val hasTextTracks ="))
        assertTrue(source.contains("if (!hasConfiguredSubtitles && !hasTextTracks) return"))
    }

    @Test
    fun subtitleSyncDoesNotUseNoOpSeekAsItsOnlyRefreshMechanism() {
        val syncJob = source.substringAfter("subtitleSyncJob = scope.launch")
            .substringBefore("private fun createPlaybackPlayer")

        assertFalse(
            syncJob.contains("player.seekTo(player.currentPosition)"),
            "seekTo(currentPosition) does not force Media3 to reparse already-loaded sidecar cues",
        )
    }
}
