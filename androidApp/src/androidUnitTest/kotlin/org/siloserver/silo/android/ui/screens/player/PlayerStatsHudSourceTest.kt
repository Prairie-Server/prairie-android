package org.siloserver.silo.android.ui.screens.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PlayerStatsHudSourceTest {
    private val viewModelSource = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerViewModel.kt",
    ).readText()
    private val screenSource = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerScreen.kt",
    ).readText()
    private val overlaySource = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerOverlay.kt",
    ).readText()
    private val settingsSource = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerSettingsSheet.kt",
    ).readText()

    @Test
    fun mobilePlayerCollectsSharedStatsAndBackendCapabilities() {
        assertTrue(viewModelSource.contains("PlaybackAnalyticsListener"))
        assertTrue(viewModelSource.contains("val stats: PlayerStatsSnapshot = PlayerStatsSnapshot()"))
        assertTrue(viewModelSource.contains("playbackAnalytics.events.collect"))
        assertTrue(viewModelSource.contains("reducePlayerStats(it.stats, event)"))
        assertTrue(viewModelSource.contains("fun onBackendCapabilities(capabilities: VideoBackendCapabilities)"))
        assertTrue(viewModelSource.contains("stats = PlayerStatsSnapshot(),\n            )"))
        assertTrue(screenSource.contains("viewModel.onBackendCapabilities(backend.capabilities)"))
    }

    @Test
    fun mobileSettingsExposePlaybackStatsSheet() {
        assertTrue(settingsSource.contains("onOpenPlaybackStats"))
        assertTrue(settingsSource.contains("label = \"Playback Stats\""))
        assertTrue(settingsSource.contains("subtitle = stats.summaryLabel()"))
        assertTrue(overlaySource.contains("statsSheetVisible"))
        assertTrue(overlaySource.contains("PlaybackStatsSheet("))
        assertTrue(overlaySource.contains("stats = state.stats"))
    }
}
