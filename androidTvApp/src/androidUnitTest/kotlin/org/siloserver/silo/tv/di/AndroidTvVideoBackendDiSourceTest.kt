package org.siloserver.silo.tv.di

import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidTvVideoBackendDiSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/di/AndroidTvModule.kt",
    ).readText()

    @Test
    fun tvRegistersVideoPlaybackBackendFactory() {
        assertTrue(source.contains("import org.siloserver.silo.common.player.backend.VideoPlaybackBackendFactory"))
        assertTrue(source.contains("VideoPlaybackBackendFactory("))
        assertTrue(source.contains("playerFactory = get()"))
        assertTrue(source.contains("audioTrackManager = get()"))
        assertTrue(source.contains("subtitleManager = get()"))
    }
}
