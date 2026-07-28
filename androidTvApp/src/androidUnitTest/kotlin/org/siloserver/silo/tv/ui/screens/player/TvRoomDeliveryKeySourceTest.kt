package org.siloserver.silo.tv.ui.screens.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvRoomDeliveryKeySourceTest {
    private val controllerSource = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvRoomSyncController.kt",
    ).readText()

    @Test
    fun stateReportRequiresAnExplicitNonNullDeliveryKey() {
        val reportingBlock = controllerSource
            .substringAfter("// Drift reporting loop")
            .substringBefore("// ready / buffering during the waiting barrier.")

        assertFalse(reportingBlock.contains("deliveryKey!!"))
        assertTrue(reportingBlock.contains("deliveryKey != null"))
        assertTrue(
            reportingBlock.indexOf("deliveryKey != null") <
                reportingBlock.indexOf("repository.stateReport("),
        )
    }
}
