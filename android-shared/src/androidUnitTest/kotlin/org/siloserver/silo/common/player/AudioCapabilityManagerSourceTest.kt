package org.siloserver.silo.common.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class AudioCapabilityManagerSourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/common/player/AudioCapabilityManager.kt",
    ).readText()

    @Test
    fun `initial receiver snapshot is published without waiting for a route change`() {
        assertContains(source, "val initialCapabilities = receiver.register()")
        assertContains(source, "publishCapabilities(mapCapabilities(initialCapabilities))")
    }
}
