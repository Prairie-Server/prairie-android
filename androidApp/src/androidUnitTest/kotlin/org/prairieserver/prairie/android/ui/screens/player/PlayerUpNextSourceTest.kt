package org.prairieserver.prairie.android.ui.screens.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerUpNextSourceTest {
    private val overlaySource = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/player/PlayerOverlay.kt",
    ).readText()
    private val cardSource = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/player/PlayerNextUpScreen.kt",
    ).readText()
    private val viewModelSource = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/player/PlayerViewModel.kt",
    ).readText()

    @Test
    fun upNextOverlayRetainsEpisodeInfoDuringExitAnimation() {
        assertTrue(
            overlaySource.contains("retainedUpNextInfo"),
            "PlayerOverlay must retain the last Up Next episode info while AnimatedVisibility fades out",
        )
        assertTrue(
            overlaySource.contains("nextEpisode = retainedUpNextInfo"),
            "The Next-Up screen's episode panel should read the retained info rather than state.nextEpisode directly",
        )
        assertFalse(
            overlaySource.contains("visible = state.showUpNext && state.nextEpisode != null"),
            "Clearing state.nextEpisode during loadContent must not remove UpNextCard content before fadeOut completes",
        )
    }

    @Test
    fun upNextCardUsesCanonicalEpisodeLabel() {
        assertTrue(
            cardSource.contains("nextEpisode.label"),
            "PlayerNextUpScreen must use NextEpisodeInfo.label instead of re-building the season/episode/title label",
        )
        assertFalse(
            cardSource.contains("S\${nextEpisode.seasonNumber}"),
            "PlayerNextUpScreen should not duplicate NextEpisodeInfo.label formatting",
        )
    }

    @Test
    fun upNextAdvanceIsSuppressedInWatchTogetherRooms() {
        val advanceBody = viewModelSource
            .substringAfter("private fun advanceToNextEpisode()")
            .substringBefore("// ---- Settings setters")

        assertTrue(
            advanceBody.contains("remoteTransportSuppressed"),
            "Manual or countdown Up Next advance must not load another episode while Watch Together owns transport",
        )
    }

    @Test
    fun userSeekClearsPendingUpNextCommitBeforeEpisodeResolution() {
        val seekBody = viewModelSource
            .substringAfter("fun onSeek(position: Double)")
            .substringBefore("/**\n     * Immediate, deadband-free seek")

        assertTrue(
            seekBody.contains("pendingApproachingEndVideoEnded = null"),
            "Seeking back after crossing the Up Next trigger must clear pending commits while next episode resolution is still in flight",
        )
    }

    @Test
    fun finishedStateDoesNotSwallowLateNextEpisodeResolution() {
        val nullNextBranch = viewModelSource
            .substringAfter("val next = _uiState.value.nextEpisode")
            .substringBefore("commitApproachingEnd(next, videoEnded)")
        assertFalse(
            nullNextBranch.contains("autoAdvanceHandled = true"),
            "Showing the finished state with no next episode must not latch autoAdvanceHandled — " +
                "a next episode resolving moments later (slow network) still needs to commit the countdown",
        )
        assertFalse(
            nullNextBranch.contains("pendingApproachingEndVideoEnded = null"),
            "The pending end flag must stay latched so resolveNextEpisode can upgrade the finished screen",
        )
    }

    @Test
    fun upNextModelAvoidsDeadPublicSurface() {
        assertFalse(
            viewModelSource.contains("val overview: String?"),
            "NextEpisodeInfo should not expose unused overview text until the card actually renders it",
        )
        assertFalse(
            viewModelSource.contains("upNextCountdownTotalSeconds"),
            "PlayerUiState should not expose an unused Up Next countdown total",
        )
        assertTrue(
            viewModelSource.contains("private val nextUpPromptSeconds: StateFlow<Int>"),
            "Next Up prompt timing flow is ViewModel-internal and should not be public UI API",
        )
    }
}
