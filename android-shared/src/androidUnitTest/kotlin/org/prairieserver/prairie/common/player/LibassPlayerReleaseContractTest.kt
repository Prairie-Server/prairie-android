package org.prairieserver.prairie.common.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LibassPlayerReleaseContractTest {
    private val factorySource = source(
        "src/androidMain/kotlin/org/prairieserver/prairie/common/player/PrairiePlayerFactory.kt",
    )
    private val serviceSource = source(
        "src/androidMain/kotlin/org/prairieserver/prairie/common/player/PrairiePlaybackService.kt",
    )
    private val backendSource = source(
        "src/androidMain/kotlin/org/prairieserver/prairie/common/player/backend/Media3VideoPlaybackBackend.kt",
    )
    private val bridgeSource = File(
        requireNotNull(System.getProperty("user.dir")).replace("android-shared", "libass-bridge"),
        "src/main/java/org/prairieserver/prairie/libass/LibassBridge.java",
    ).readText()

    @Test
    fun playerTeardownIsOwnedByFactoryAndBridge() {
        assertTrue(factorySource.contains("fun releasePlayer(player: Player)"))
        assertTrue(factorySource.contains("libassBridge::releasePlayer"))
        assertTrue(serviceSource.contains("playerFactory.releasePlayer(player)"))
        assertTrue(backendSource.contains("playerFactory.releasePlayer(player)"))
    }

    @Test
    fun bridgeRecyclesHandlerAndRemovesRetiredOverlay() {
        assertTrue(bridgeSource.contains("private volatile AssHandler handler"))
        assertTrue(bridgeSource.contains("public void releasePlayer(ExoPlayer player)"))
        assertTrue(bridgeSource.contains("retireOverlay();"))
        assertTrue(bridgeSource.contains("newHandler();"))
    }

    private fun source(relativePath: String): String =
        File(requireNotNull(System.getProperty("user.dir")), relativePath).readText()
}
