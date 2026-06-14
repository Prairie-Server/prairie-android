package com.continuum.app.android.di

import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidVideoBackendDiSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt",
    ).readText()

    @Test
    fun mobileRegistersVideoPlaybackBackendFactory() {
        assertTrue(source.contains("import com.continuum.app.common.player.backend.VideoPlaybackBackendFactory"))
        assertTrue(source.contains("VideoPlaybackBackendFactory("))
        assertTrue(source.contains("playerFactory = get()"))
        assertTrue(source.contains("audioTrackManager = get()"))
        assertTrue(source.contains("subtitleManager = get()"))
    }
}
