package org.siloserver.silo.common.diagnostics

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.siloserver.silo.model.diagnostics.DiagnosticsDeviceProvenance
import org.siloserver.silo.model.diagnostics.DiagnosticsDeviceSentinel
import org.siloserver.silo.model.diagnostics.decodeDiagnosticsDeviceSnapshot
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceSnapshotCollectorTest {
    @Test
    fun inaccessibleAudioProbeReportsUnknownWithoutDiscardingOtherSections() {
        val collector = DeviceSnapshotCollector(
            probe = FakeDeviceProbe(audioFailure = SecurityException("denied")),
            nowRfc3339 = { CAPTURED_AT },
        )

        val snapshot = collector.capture(DiagnosticsDeviceProvenance.PRE_FAILURE)

        assertEquals("NVIDIA", snapshot.identity.jsonObject.getValue("manufacturer").jsonPrimitive.content)
        assertEquals("Shield", snapshot.identity.jsonObject.getValue("model").jsonPrimitive.content)
        assertEquals(DiagnosticsDeviceSentinel.UNKNOWN.wireValue, snapshot.audio.jsonPrimitive.content)
        assertEquals("ethernet", snapshot.network.jsonObject.getValue("transport").jsonPrimitive.content)
        assertEquals(CAPTURED_AT, snapshot.capturedAt)
        assertEquals(DiagnosticsDeviceProvenance.PRE_FAILURE, snapshot.provenance)
    }

    @Test
    fun unavailableSectionsUseNotCollectedAndCodecEvidenceIsBounded() {
        val codecs = List(140) { index ->
            DiagnosticsCodecSnapshot(
                codec = "codec-$index",
                decoderName = "decoder-$index",
                hardware = true,
            )
        }
        val collector = DeviceSnapshotCollector(
            probe = FakeDeviceProbe(display = null, network = null, codecs = codecs),
            nowRfc3339 = { CAPTURED_AT },
        )

        val snapshot = collector.capture(DiagnosticsDeviceProvenance.POST_RESTART)

        assertEquals(DiagnosticsDeviceSentinel.NOT_COLLECTED.wireValue, snapshot.display.jsonPrimitive.content)
        assertEquals(DiagnosticsDeviceSentinel.NOT_COLLECTED.wireValue, snapshot.network.jsonPrimitive.content)
        assertEquals(128, snapshot.videoCodecs.jsonArray.size)
        assertEquals("codec-0", snapshot.videoCodecs.jsonArray.first().jsonObject.getValue("codec").jsonPrimitive.content)
        assertEquals(DiagnosticsDeviceProvenance.POST_RESTART, snapshot.provenance)
    }

    @Test
    fun cacheReturnsValidatedDefensiveCopies() {
        val snapshot = DeviceSnapshotCollector(
            probe = FakeDeviceProbe(),
            nowRfc3339 = { CAPTURED_AT },
        ).capture(DiagnosticsDeviceProvenance.PRE_FAILURE)
        val cache = DeviceSnapshotCache()

        cache.update(snapshot)
        val first = assertNotNull(cache.currentBytes())
        val expected = first.copyOf()
        first.fill(0)

        val second = assertNotNull(cache.currentBytes())
        assertContentEquals(expected, second)
        assertEquals(snapshot, decodeDiagnosticsDeviceSnapshot(second.decodeToString()))
        assertFalse(second.decodeToString().contains("ssid", ignoreCase = true))
        assertFalse(second.decodeToString().contains("192.168."))
        assertTrue(second.decodeToString().contains("build_fingerprint_hash"))
    }

    @Test
    fun stableIdentifiersAreOneWayAndDeterministic() {
        val raw = "route-address-00:11:22:33:44:55"

        val first = stableIdentifierHash(raw)

        assertEquals(first, stableIdentifierHash(raw))
        assertEquals(32, first.length)
        assertFalse(first.contains(raw))
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
    }

    private class FakeDeviceProbe(
        private val display: DiagnosticsDisplaySnapshot? = DiagnosticsDisplaySnapshot(
            widthPx = 3840,
            heightPx = 2160,
            refreshRateHz = 59.94,
            hdr10 = true,
            hdr10Plus = false,
            hlg = true,
            dolbyVisionProfiles = listOf(5, 7, 8),
        ),
        private val network: DiagnosticsNetworkSnapshot? = DiagnosticsNetworkSnapshot(
            connected = true,
            validated = true,
            metered = false,
            transport = "ethernet",
            bandwidthEstimateKbps = null,
            linkDownstreamKbps = 1_000_000,
        ),
        private val codecs: List<DiagnosticsCodecSnapshot>? = listOf(
            DiagnosticsCodecSnapshot("hevc", "c2.nvidia.hevc.decoder", true, listOf("main 10"), listOf(10), 3840, 2160, 60.0),
        ),
        private val audioFailure: Throwable? = null,
    ) : DiagnosticsDeviceProbe {
        override fun identity() = DiagnosticsIdentitySnapshot(
            manufacturer = "NVIDIA",
            model = "Shield",
            device = "foster",
            osRelease = "16",
            sdkInt = 36,
            formFactor = "tv",
            buildFingerprintHash = "a".repeat(32),
        )

        override fun display(): DiagnosticsDisplaySnapshot? = display

        override fun audio(): DiagnosticsAudioSnapshot {
            audioFailure?.let { throw it }
            return DiagnosticsAudioSnapshot(
                sinkType = "hdmi",
                routeHashes = listOf("b".repeat(32)),
                routeGeneration = 4,
                passthroughCodecs = listOf("ac3", "eac3", "truehd"),
                maxChannels = 8,
                spatializerEnabled = false,
                suppressedFormats = listOf("audio/true-hd:8"),
            )
        }

        override fun codecs(): List<DiagnosticsCodecSnapshot>? = codecs

        override fun network(): DiagnosticsNetworkSnapshot? = network
    }

    private companion object {
        const val CAPTURED_AT = "2026-07-22T12:34:56.000Z"
    }
}
