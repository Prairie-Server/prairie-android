package com.continuum.app.common.player.mpv

import kotlin.test.Test
import kotlin.test.assertTrue

class MpvPlayerSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/mpv/MpvPlayer.kt",
    )

    @Test
    fun mpvPlayerWrapsLibmpvAsMedia3Player() {
        val text = source.readText()

        assertTrue(text.contains("class MpvPlayer"))
        assertTrue(text.contains("BasePlayer()"))
        assertTrue(text.contains("MPVLib.create(context)"))
        assertTrue(text.contains("setOptionString(\"gpu-context\", \"android\")"))
        assertTrue(text.contains("setOptionString(\"opengl-es\", \"yes\")"))
        assertTrue(text.contains("setOptionString(\"cache\", \"yes\")"))
        assertTrue(text.contains("setOptionString(\"cache-pause-initial\", \"yes\")"))
        assertTrue(text.contains("setOptionString(\"sub-scale-with-window\", \"yes\")"))
        assertTrue(text.contains("arrayOf(\"sub-add\""))
        assertTrue(text.contains("attachSurface(surface)"))
        assertTrue(text.contains("detachSurface()"))
        assertTrue(text.contains("override fun setTrackSelectionParameters"))
        assertTrue(text.contains("override fun getBufferedPosition()"))
        assertTrue(text.contains("override fun release()"))
    }

    @Test
    fun mpvPlayerAdvertisesMediaSessionMountCommands() {
        val text = source.readText()

        assertTrue(text.contains("COMMAND_SET_MEDIA_ITEM"))
        assertTrue(text.contains("COMMAND_PREPARE"))
        assertTrue(text.contains("COMMAND_SET_PLAYLIST_METADATA"))
    }

    @Test
    fun mpvPlayerAppliesHttpHeadersBeforeLoadingMedia() {
        val text = source.readText()

        assertTrue(text.contains("httpHeaderFieldsProvider"))
        assertTrue(text.contains("setPropertyString(\"http-header-fields\""))
        assertTrue(text.contains("joinToString(\"\\r\\n\")"))
        assertTrue(text.indexOf("applyHttpHeaderFields()") < text.indexOf("arrayOf(\n                    \"loadfile\""))
    }

    @Test
    fun mpvPlayerAttachesEveryMedia3VideoSurfacePath() {
        val text = source.readText()

        assertTrue(text.contains("private fun attachVideoSurface(surface: Surface?)"))
        assertTrue(text.contains("private fun detachVideoSurface(surface: Surface?)"))
        assertTrue(text.contains("mpv.attachSurface(surface)"))
        assertTrue(text.contains("mpv.detachSurface()"))
        assertTrue(text.contains("setPropertyString(\"android-surface-size\""))
        assertTrue(text.contains("override fun setVideoSurface(surface: Surface?) {\n        attachVideoSurface(surface)"))
        assertTrue(text.contains("override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?)"))
        assertTrue(text.contains("surfaceHolder?.addCallback(surfaceCallback)"))
        assertTrue(text.contains("setVideoSurfaceHolder(surfaceView?.holder)"))
    }

    @Test
    fun mpvPlayerKeepsVideoOutputAliveAcrossSurfaceTransitions() {
        val text = source.readText()
        val attachBody = text.substringAfter("private fun attachVideoSurface(surface: Surface?)")
            .substringBefore("private fun detachVideoSurface")
        val detachBody = text.substringAfter("private fun detachVideoSurface(surface: Surface?)")
            .substringBefore("private fun updateVideoSurfaceSize")

        assertTrue(attachBody.contains("mpv.attachSurface(surface)"))
        assertTrue(attachBody.contains("setOptionString(\"force-window\", \"yes\")"))
        assertTrue(!attachBody.contains("setOptionString(\"vo\", videoOutput)"))
        assertTrue(detachBody.contains("mpv.detachSurface()"))
        assertTrue(!detachBody.contains("setOptionString(\"vid\", \"no\")"))
        assertTrue(!detachBody.contains("setOptionString(\"vo\", \"null\")"))
        assertTrue(!detachBody.contains("setOptionString(\"force-window\", \"no\")"))
    }

    @Test
    fun mpvPlayerDisablesBuiltInOsdControls() {
        val text = source.readText()

        assertTrue(text.contains("setOptionString(\"osc\", \"no\")"))
        assertTrue(text.contains("setOptionString(\"osd-level\", \"0\")"))
        assertTrue(text.contains("setOptionString(\"osd-bar\", \"no\")"))
    }

    @Test
    fun mpvPlayerReportsStablePositionBeforeTimePosArrives() {
        val text = source.readText()

        assertTrue(text.contains("currentPositionMs = initialSeekTo"))
        assertTrue(text.contains("override fun getCurrentPosition(): Long =\n        (currentPositionMs ?: initialSeekTo).coerceAtLeast(0L)"))
        assertTrue(!text.contains("override fun getCurrentPosition(): Long = currentPositionMs ?: C.TIME_UNSET"))
    }

    @Test
    fun mpvPlayerAppliesInitialSeekWithoutDefaultSeekLoop() {
        val text = source.readText()

        assertTrue(text.contains("private fun applyPendingInitialSeek()"))
        assertTrue(text.contains("if (initialSeekTo <= 0L) return"))
        assertTrue(text.contains("performSeek(targetMs)"))
        assertTrue(!text.contains("seekTo(C.TIME_UNSET)"))
    }

    @Test
    fun mpvPlayerPreservesPlayIntentAcrossPrepareReset() {
        val text = source.readText()
        val resetBody = text.substringAfter("private fun resetInternalState()")
            .substringBefore("override fun onAudioFocusChange")

        assertTrue(!resetBody.contains("currentPlayWhenReady = false"))
        assertTrue(text.contains("setPlayerStateAndNotifyIfChanged(playbackState = STATE_READY)"))
    }
}
