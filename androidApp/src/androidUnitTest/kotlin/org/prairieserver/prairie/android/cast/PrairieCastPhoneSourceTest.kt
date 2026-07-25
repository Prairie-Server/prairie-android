package org.prairieserver.prairie.android.cast

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PrairieCastPhoneSourceTest {
    @Test
    fun phoneHasBrowserControllerRemoteAndPlayOnDeviceEntrypoint() {
        val controller = File("src/androidMain/kotlin/org/prairieserver/prairie/android/cast/PrairieCastController.kt").takeIf { it.exists() }?.readText().orEmpty()
        val picker = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/cast/PrairieCastTargetPickerSheet.kt").takeIf { it.exists() }?.readText().orEmpty()
        val remote = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/cast/PrairieCastRemoteScreen.kt").takeIf { it.exists() }?.readText().orEmpty()
        val mini = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/cast/PrairieCastMiniBar.kt").takeIf { it.exists() }?.readText().orEmpty()
        val home = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/home/HomeScreen.kt").readText()
        val main = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/MainScreen.kt").readText()
        val navigation = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/navigation/AppNavigation.kt").readText()
        val detail = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/detail/ItemDetailScreen.kt").readText()

        assertTrue(controller.contains("PrairieCastController"))
        assertTrue(controller.contains("PrairieCastNsdBrowser"))
        assertTrue(picker.contains("PrairieCastTargetPickerSheet"))
        assertTrue(remote.contains("PrairieCastRemoteScreen"))
        assertTrue(mini.contains("PrairieCastMiniBar"))
        assertTrue(home.contains("Icons.Outlined.SettingsRemote"))
        assertTrue(home.contains("contentDescription = \"Remote Control\""))
        assertTrue(controller.contains("launchOnConnectedTarget"))
        assertTrue(main.contains("prairieCastController.launchOnConnectedTarget"))
        assertTrue(navigation.contains("prairieCastController.launchOnConnectedTarget"))
        assertTrue(detail.contains("Play on device"))
    }

    @Test
    fun phoneControllerHasAppleParitySessionLifecycle() {
        val controller = File("src/androidMain/kotlin/org/prairieserver/prairie/android/cast/PrairieCastController.kt").readText()
        val starter = File("src/androidMain/kotlin/org/prairieserver/prairie/android/cast/PrairieCastForegroundStarter.kt").readText()
        val store = File("src/androidMain/kotlin/org/prairieserver/prairie/android/cast/PrairieCastLastTargetStore.kt").readText()

        // Heartbeat: phone pings every 3 s; only a pong resets the miss counter.
        assertTrue(controller.contains("heartbeatLoop"))
        assertTrue(controller.contains("MAX_MISSED_HEARTBEATS"))
        // Auto-reconnect with backoff after a dropped (non-deliberate) transport.
        assertTrue(controller.contains("beginReconnect"))
        assertTrue(controller.contains("MAX_RECONNECT_ATTEMPTS"))
        assertTrue(controller.contains("isReconnecting"))
        // Persisted last target + playing-gated silent auto-resume.
        assertTrue(controller.contains("attemptAutoResumeIfIdle"))
        assertTrue(controller.contains("lastTargetStore"))
        assertTrue(store.contains("PrairieCastPersistedTarget"))
        assertTrue(starter.contains("ProcessLifecycleOwner"))
        // Reused-identity handoff: the TV skips the challenge and answers
        // handoff_ready directly when switching items mid-playback, so the
        // phone must race both instead of deadlocking on the challenge.
        assertTrue(controller.contains("handoffReady.onAwait"))
        assertTrue(controller.contains("handoffChallenge.onAwait"))
    }

    @Test
    fun phoneRemoteUiHasAppleParitySurfaces() {
        val remote = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/cast/PrairieCastRemoteScreen.kt").readText()
        val picker = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/cast/PrairieCastTargetPickerSheet.kt").readText()
        val artwork = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/cast/PrairieCastArtwork.kt").readText()
        val browser = File("../android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/cast/PrairieCastNsdBrowser.kt").readText()

        // Remote: scrubber, buffering spinner, connection states, overflow menu.
        assertTrue(remote.contains("RemoteScrubber"))
        assertTrue(remote.contains("Reconnecting…"))
        assertTrue(remote.contains("Connected to "))
        assertTrue(remote.contains("Choose a Different TV"))
        assertTrue(remote.contains("isBuffering"))
        // Artwork resolved from contentId, like Apple's PrairieControlArtworkResolver.
        assertTrue(artwork.contains("rememberPrairieCastArtwork"))
        // Picker: searching/empty states and TXT-driven row context.
        assertTrue(picker.contains("Searching for Prairie TVs"))
        assertTrue(picker.contains("No Prairie TVs Found"))
        assertTrue(picker.contains("Playing now"))
        // Browser surfaces the TXT metadata the picker/auto-resume rely on.
        assertTrue(browser.contains("serverId"))
        assertTrue(browser.contains("isPlaying"))
    }
}
