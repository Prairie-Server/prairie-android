package org.prairieserver.prairie.android.pip

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class MobilePictureInPictureSourceTest {
    private val manifest = File("src/androidMain/AndroidManifest.xml").readText()
    private val activity = File("src/androidMain/kotlin/org/prairieserver/prairie/android/MainActivity.kt").readText()
    private val player = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/player/PlayerScreen.kt",
    ).readText()
    private val settings = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/settings/PlaybackSettings.kt",
    ).readText()

    @Test
    fun mobileActivityDeclaresPictureInPictureWithoutLosingAndroid7Support() {
        assertTrue(manifest.contains("""android:supportsPictureInPicture="true""""))
        assertTrue(manifest.contains("smallestScreenSize"))
    }

    @Test
    fun mobileActivityOwnsSystemPipCallbacks() {
        assertTrue(activity.contains("PrairiePictureInPictureCoordinator"))
        assertTrue(activity.contains("PrairiePictureInPictureSurface.Mobile"))
        assertTrue(activity.contains("override fun onUserLeaveHint()"))
        assertTrue(activity.contains("override fun onPictureInPictureModeChanged"))
        assertTrue(activity.contains("enterPictureInPictureIfEligible"))
        assertTrue(activity.contains("setInPictureInPictureMode(isInPictureInPictureMode)"))
    }

    @Test
    fun mobilePlayerPublishesPlaybackStateAndHidesControlsInPip() {
        assertTrue(player.contains("PrairiePictureInPictureCoordinator"))
        assertTrue(player.contains("pictureInPictureEnabledFlow"))
        assertTrue(player.contains("updatePlaybackState("))
        val pipEffect = player
            .substringAfter("LaunchedEffect(\n        activity,")
            .substringBefore(") {\n        pictureInPictureCoordinator.updatePlaybackState")
        assertTrue(pipEffect.contains("uiState.isPlaying"))
        assertTrue(player.contains("isPlaying = uiState.isPlaying && !uiState.isPaused"))
        assertTrue(player.contains("onGloballyPositioned"))
        assertTrue(player.contains("if (!isInPictureInPictureMode && !castState.isConnected)"))
    }

    @Test
    fun mobileSettingsExposePipToggle() {
        assertTrue(settings.contains("Picture-in-Picture"))
    }
}
