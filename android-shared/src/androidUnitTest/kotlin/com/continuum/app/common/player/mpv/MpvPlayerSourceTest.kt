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
        assertTrue(text.contains("attachSurface(holder.surface)"))
        assertTrue(text.contains("detachSurface()"))
        assertTrue(text.contains("override fun setTrackSelectionParameters"))
        assertTrue(text.contains("override fun getBufferedPosition()"))
        assertTrue(text.contains("override fun release()"))
    }
}
