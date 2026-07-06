package org.siloserver.silo.common.downloads

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DownloadEnqueuerSourceTest {

    private val source = File(
        "../android-shared/src/androidMain/kotlin/org/siloserver/silo/common/downloads/DownloadEnqueuer.kt"
    ).readText()

    @Test
    fun `download create requests include the default download quality preset`() {
        assertTrue(source.contains("defaultDownloadQualityFlow.first()"))
        assertTrue(source.contains("quality = quality.wire"))
        assertTrue(source.contains("targetBitrateKbps = quality.targetBitrateKbps"))
    }

    @Test
    fun `download create requests accept an explicit per-download quality override`() {
        assertTrue(source.contains("downloadQualityOverride: DownloadQuality? = null"))
        assertTrue(source.contains("downloadQualityOverride ?: DownloadQuality.fromWire"))
        assertTrue(source.contains("quality = quality.wire"))
        assertTrue(source.contains("targetBitrateKbps = quality.targetBitrateKbps"))
    }
}
