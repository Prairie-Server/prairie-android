package org.siloserver.silo.tv.cast

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvSiloCastReceiverSourceTest {
    @Test
    fun receiverAdvertisesSilocastAndAllowsNewestControllerToWin() {
        val receiver = File("src/androidMain/kotlin/org/siloserver/silo/tv/cast/TvSiloCastReceiver.kt").takeIf { it.exists() }?.readText().orEmpty()
        val module = File("src/androidMain/kotlin/org/siloserver/silo/tv/di/AndroidTvModule.kt").readText()

        assertTrue(receiver.contains("_silocast._tcp") || receiver.contains("SiloCastProtocol.serviceType"))
        assertTrue(receiver.contains("activeSession"))
        assertTrue(receiver.contains("closePreviousController"))
        assertTrue(module.contains("TvSiloCastReceiver"))
    }

    @Test
    fun playerAdapterMapsCoreControls() {
        val adapter = File("src/androidMain/kotlin/org/siloserver/silo/tv/cast/TvSiloCastPlayerAdapter.kt").takeIf { it.exists() }?.readText().orEmpty()
        listOf("playPause", "seek", "selectSubtitle", "selectAudio", "setPlaybackSpeed", "playNext").forEach {
            assertTrue(adapter.contains(it), "Adapter must map $it.")
        }
    }
}
