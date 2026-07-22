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
        assertTrue(viewModel.contains("finalPlaybackPositionWriter.submit("))
        assertTrue(viewModel.contains("sessionLifecycle.stopAsync()"))
        assertTrue(!viewModel.contains("runBlocking("))
        assertTrue(
            !viewModel.contains(
                "viewModelScope.launch {\n                playbackSessionManager.stopSession(sessionId)",
            ),
        )
    }
}
