package com.continuum.app.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertTrue

class TvPlayerBackendCapabilitiesSourceTest {
    private val viewModelSource = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt",
    ).readText()
    private val hudSource = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt",
    ).readText()
    private val screenSource = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt",
    ).readText()

    @Test
    fun backendCapabilitiesReachStatsHud() {
        assertTrue(viewModelSource.contains("import com.continuum.app.common.player.backend.VideoBackendCapabilities"))
        assertTrue(viewModelSource.contains("val backendKind: String? = null"))
        assertTrue(viewModelSource.contains("val backendRoute: String? = null"))
        assertTrue(viewModelSource.contains("val subtitleRendering: String? = null"))
        assertTrue(viewModelSource.contains("fun onBackendCapabilities(capabilities: VideoBackendCapabilities)"))
        assertTrue(hudSource.contains("backendKind?.let { add(\"Backend\" to it) }"))
        assertTrue(hudSource.contains("backendRoute?.let { add(\"Route\" to it) }"))
        assertTrue(hudSource.contains("subtitleRendering?.let { add(\"Subtitles\" to it) }"))
        assertTrue(screenSource.contains("viewModel.onBackendCapabilities(backend.capabilities)"))
    }
}
