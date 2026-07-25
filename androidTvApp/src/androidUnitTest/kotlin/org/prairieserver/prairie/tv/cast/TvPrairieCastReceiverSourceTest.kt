package org.prairieserver.prairie.tv.cast

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvPrairieCastReceiverSourceTest {
    @Test
    fun receiverAdvertisesPrairiecastAndAllowsNewestControllerToWin() {
        val receiver = File("src/androidMain/kotlin/org/prairieserver/prairie/tv/cast/TvPrairieCastReceiver.kt").takeIf { it.exists() }?.readText().orEmpty()
        val identity = File("src/androidMain/kotlin/org/prairieserver/prairie/tv/cast/RemotePlaybackIdentityManager.kt").readText()
        val module = File("src/androidMain/kotlin/org/prairieserver/prairie/tv/di/AndroidTvModule.kt").readText()
        val navigation = File("src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/navigation/TvAppNavigation.kt").readText()

        assertTrue(receiver.contains("_prairiecast._tcp") || receiver.contains("PrairieCastProtocol.serviceType"))
        assertTrue(receiver.contains("activeSession"))
        assertTrue(receiver.contains("closePreviousController"))
        assertTrue(receiver.contains("PrairieCastMessage.HandoffOffer"))
        assertTrue(receiver.contains("launchRequests"))
        assertTrue(receiver.contains("withContext(Dispatchers.Main.immediate)"))
        assertTrue(identity.contains("beginTemporaryScope"))
        assertTrue(navigation.contains("prairieCastReceiver.launchRequests.collect"))
        assertTrue(navigation.contains("TvRoute.Player"))
        assertTrue(module.contains("TvPrairieCastReceiver"))
    }

    @Test
    fun playerAdapterMapsCoreControls() {
        val adapter = File("src/androidMain/kotlin/org/prairieserver/prairie/tv/cast/TvPrairieCastPlayerAdapter.kt").takeIf { it.exists() }?.readText().orEmpty()
        listOf("playPause", "seek", "selectSubtitle", "selectAudio", "setPlaybackSpeed", "playNext").forEach {
            assertTrue(adapter.contains(it), "Adapter must map $it.")
        }
    }
}
