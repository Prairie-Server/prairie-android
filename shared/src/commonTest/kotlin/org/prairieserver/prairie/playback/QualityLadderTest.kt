package org.prairieserver.prairie.playback

import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.network.ApiResult
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QualityLadderTest {
    @BeforeTest
    fun reset() {
        // Each test constructs its own client; no shared cache.
    }

    @Test
    fun `isValidQualityLadder rejects empty and zero bitrate`() {
        assertFalse(isValidQualityLadder(emptyList()))
        assertFalse(
            isValidQualityLadder(
                listOf(
                    QualityLadderRung("1080p", "1080p", "1080p", 1080, 6000),
                    QualityLadderRung("720p", "720p", "720p", 720, 0),
                ),
            ),
        )
        assertTrue(isValidQualityLadder(FALLBACK_QUALITY_LADDER))
    }

    @Test
    fun `qualityLadderForSourceHeight omits upscales`() {
        val capped = qualityLadderForSourceHeight(FALLBACK_QUALITY_LADDER, 1080)
        assertEquals("1080p-high", capped.first().id)
        assertTrue(capped.none { it.height > 1088 })
        assertEquals(
            FALLBACK_QUALITY_LADDER.size,
            qualityLadderForSourceHeight(FALLBACK_QUALITY_LADDER, 0).size,
        )
    }

    @Test
    fun `buildQualityOptions puts modes first and skips native rung`() {
        val options = buildQualityOptions(
            ladder = FALLBACK_QUALITY_LADDER,
            nativeHeight = 2160,
            playMethod = "direct",
            sourceResolutionLabel = "2160p",
            sourceBitrateKbps = 40_000,
        )
        assertEquals("auto", options[0].id)
        assertEquals("original", options[1].id)
        assertTrue(options[1].label.contains("4K"))
        assertTrue(options.none { it.id == "2160p" })
        assertTrue(options.any { it.id == "1080p-high" })
    }

    @Test
    fun `resolveQualityTargets maps rung and auto`() {
        val options = buildQualityOptions(FALLBACK_QUALITY_LADDER, nativeHeight = 2160)
        val rung = resolveQualityTargets(
            qualityId = "720p-high",
            options = options,
            playMethod = "direct",
            ladder = FALLBACK_QUALITY_LADDER,
        )
        assertNotNull(rung)
        assertEquals("720p", rung.resolution)
        assertEquals(4000, rung.bitrateKbps)

        assertNull(
            resolveQualityTargets(
                qualityId = "original",
                options = options,
                playMethod = "direct",
                ladder = FALLBACK_QUALITY_LADDER,
            ),
        )

        val auto = resolveQualityTargets(
            qualityId = "auto",
            options = options,
            playMethod = "transcode",
            ladder = FALLBACK_QUALITY_LADDER,
            deviceMaxHeight = 1080,
        )
        assertNotNull(auto)
        assertEquals("1080p", auto.resolution)
    }

    @Test
    fun `toV3QualityPreference collapses high variants`() {
        assertEquals("auto", toV3QualityPreference("auto"))
        assertEquals("original", toV3QualityPreference("original"))
        assertEquals("1080p", toV3QualityPreference("1080p-high"))
        assertEquals("720p", toV3QualityPreference("720p"))
        assertEquals("480p", toV3QualityPreference("420p"))
        assertEquals("2160p", toV3QualityPreference("4k"))
    }

    @Test
    fun `parseQualityLadderResponse accepts valid server payload`() {
        val parsed = parseQualityLadderResponse(
            QualityLadderResponse(
                rungs = FALLBACK_QUALITY_LADDER,
                modes = listOf("auto", "original"),
            ),
        )
        assertEquals(FALLBACK_QUALITY_LADDER.size, parsed!!.size)
        assertNull(
            parseQualityLadderResponse(
                QualityLadderResponse(rungs = emptyList()),
            ),
        )
    }

    @Test
    fun `QualityLadderClient caches success and falls back on error`() = runTest {
        var calls = 0
        val client = QualityLadderClient {
            calls++
            ApiResult.Success(
                QualityLadderResponse(
                    rungs = listOf(
                        QualityLadderRung("1080p", "1080p", "1080p", 1080, 6000),
                        QualityLadderRung("720p", "720p", "720p", 720, 2000),
                    ),
                ),
            )
        }
        val first = client.fetch()
        val second = client.fetch()
        assertEquals(1, calls)
        assertEquals(first, second)
        assertEquals("1080p", first.first().id)

        val failing = QualityLadderClient { ApiResult.Error(500, "err", "fail") }
        assertEquals(FALLBACK_QUALITY_LADDER, failing.fetch())
    }

    @Test
    fun `formatQualityBitrate collapses integers`() {
        assertEquals("8 Mbps", formatQualityBitrate(8000))
        assertEquals("1.5 Mbps", formatQualityBitrate(1500))
        assertEquals("720 kbps", formatQualityBitrate(720))
    }
}
