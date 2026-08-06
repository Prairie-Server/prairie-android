package org.siloserver.silo.android.ui.performance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class MobilePlayerLifecyclePerformanceSourceTest {
    private val module = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/di/AndroidModule.kt",
    ).readText()
    private val screen = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerScreen.kt",
    ).readText()
    private val viewModel = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerViewModel.kt",
    ).readText()

    @Test
    fun playerIsNavigationOwnedAndTeardownIsNonBlockingAndIdempotent() {
        assertTrue(module.contains("viewModel {\n        PlayerViewModel("))
        assertTrue(screen.contains("viewModel: PlayerViewModel = koinViewModel()"))
        assertTrue(viewModel.contains("private val exitPrepared = AtomicBoolean(false)"))
        assertTrue(viewModel.contains("if (!exitPrepared.compareAndSet(false, true)) return"))
        assertTrue(viewModel.contains("private var finalPositionScope: PlaybackWriteScope? = null"))
        assertTrue(viewModel.contains("finalPositionScope = finalPlaybackPositionWriter.captureScope()"))
        assertTrue(viewModel.contains("val scope = finalPositionScope"))
        assertTrue(viewModel.contains("scope = scope,"))
        assertTrue(viewModel.contains("finalPlaybackPositionWriter.submit("))
        // Still the non-blocking teardown this test exists to protect, now
        // qualified by the session this view model owned — phone navigation
        // replaces the player entry, so an unqualified stop could kill the
        // session a newer screen had already adopted.
        assertTrue(viewModel.contains("sessionLifecycle.stopAsync(expectedSessionId ="))
        assertTrue(!viewModel.contains("runBlocking("))
        assertTrue(!screen.contains("onDispose { viewModel.onExit() }"))
        assertTrue(screen.contains("viewModel.claimInitialRouteLoad()"))
        assertTrue(screen.contains("activity?.isChangingConfigurations == true"))
        assertTrue(screen.contains("viewModel.shouldApplyMediaMount(uiState.mediaMountGeneration)"))
        assertTrue(screen.contains("viewModel.claimSubtitleRefresh(uiState.subtitleRefreshNonce)"))
        assertTrue(
            !viewModel.contains(
                "viewModelScope.launch {\n                playbackSessionManager.stopSession(sessionId)",
            ),
        )
    }
}
