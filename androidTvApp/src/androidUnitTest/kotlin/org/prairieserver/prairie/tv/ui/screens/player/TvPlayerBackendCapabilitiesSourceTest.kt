package org.prairieserver.prairie.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertTrue

class TvPlayerBackendCapabilitiesSourceTest {
    private val viewModelSource = java.io.File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerViewModel.kt",
    ).readText()
    private val hudSource = java.io.File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerHud.kt",
    ).readText()
    private val screenSource = java.io.File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerScreen.kt",
    ).readText()
    private val sharedStatsSource = java.io.File(
        "../android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/PlayerStatsSnapshot.kt",
    ).readText()

    @Test
    fun backendCapabilitiesReachStatsHud() {
        assertTrue(viewModelSource.contains("import org.prairieserver.prairie.common.player.backend.VideoBackendCapabilities"))
        assertTrue(viewModelSource.contains("import org.prairieserver.prairie.common.player.PlayerStatsSnapshot"))
        assertTrue(sharedStatsSource.contains("val backendKind: String? = null"))
        assertTrue(sharedStatsSource.contains("val backendDisplayName: String? = null"))
        assertTrue(sharedStatsSource.contains("val backendRoute: String? = null"))
        assertTrue(sharedStatsSource.contains("val subtitleRendering: String? = null"))
        assertTrue(sharedStatsSource.contains("val hardContainers: String? = null"))
        assertTrue(viewModelSource.contains("fun onBackendCapabilities(capabilities: VideoBackendCapabilities)"))
        assertTrue(hudSource.contains("backendDisplayName?.let { add(\"Backend\" to it) }"))
        assertTrue(hudSource.contains("backendRoute?.let { add(\"Route\" to it) }"))
        assertTrue(hudSource.contains("subtitleRendering?.let { add(\"Subtitles\" to it) }"))
        assertTrue(hudSource.contains("hardContainers?.let { add(\"Hard containers\" to it) }"))
        assertTrue(screenSource.contains("viewModel.onBackendCapabilities(backend.capabilities)"))
    }
}
