package org.prairieserver.prairie.playback

import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.network.ApiResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QualityLadderTest {
    @Test
    fun `isValidQualityLadder rejects empty null blank fields and zero values`() {
        assertFalse(isValidQualityLadder(null))
        assertFalse(isValidQualityLadder(emptyList()))
        assertFalse(
            isValidQualityLadder(
                listOf(QualityLadderRung("", "1080p", "1080p", 1080, 6000)),
            ),
        )
        assertFalse(
            isValidQualityLadder(
                listOf(QualityLadderRung("1080p", "", "1080p", 1080, 6000)),
            ),
        )
        assertFalse(
            isValidQualityLadder(
                listOf(QualityLadderRung("1080p", "1080p", "", 1080, 6000)),
            ),
        )
        assertFalse(
            isValidQualityLadder(
                listOf(QualityLadderRung("1080p", "1080p", "1080p", 0, 6000)),
            ),
        )
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
    fun `qualityLadderForSourceHeight omits upscales and never returns empty`() {
        val capped = qualityLadderForSourceHeight(FALLBACK_QUALITY_LADDER, 1080)
        assertEquals("1080p-high", capped.first().id)
        assertTrue(capped.none { it.height > 1088 })
        assertEquals(
            FALLBACK_QUALITY_LADDER.size,
            qualityLadderForSourceHeight(FALLBACK_QUALITY_LADDER, 0).size,
        )
        // Source below every rung → keep the lowest rung rather than empty.
        val tiny = qualityLadderForSourceHeight(FALLBACK_QUALITY_LADDER, 100)
        assertEquals(1, tiny.size)
        assertEquals(FALLBACK_QUALITY_LADDER.last().id, tiny.single().id)
        // +8 tolerance keeps a near-match rung.
        val near = qualityLadderForSourceHeight(
            listOf(QualityLadderRung("1080p", "1080p", "1080p", 1080, 6000)),
            1075,
        )
        assertEquals(1, near.size)
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
        assertTrue(options[1].sublabel.contains("Direct Play"))
        assertTrue(options[1].sublabel.contains("Mbps"))
        assertTrue(options.none { it.id == "2160p" })
        assertTrue(options.any { it.id == "1080p-high" })
    }

    @Test
    fun `buildQualityOptions covers mode aliases remux labels and unknown native height`() {
        val remux = buildQualityOptions(
            ladder = FALLBACK_QUALITY_LADDER,
            nativeHeight = 1080,
            playMethod = "remux",
            sourceResolutionLabel = "1080p",
            sourceBitrateKbps = 0,
            modes = listOf("auto", "source"),
        )
        assertEquals("original", remux[1].id)
        assertEquals("Original (1080p)", remux[1].label)
        assertEquals("Remux", remux[1].sublabel)
        assertTrue(remux.none { it.id == "1080p" || it.id == "1080p-high" })

        val maxMode = buildQualityOptions(
            ladder = FALLBACK_QUALITY_LADDER.take(2),
            nativeHeight = 720,
            playMethod = "transcode",
            sourceResolutionLabel = "720p",
            modes = listOf("max"),
        )
        assertEquals(1, maxMode.count { it.isOriginal })
        assertTrue(maxMode.single { it.isOriginal }.sublabel.contains("Transcode"))

        // Empty modes → DEFAULT_QUALITY_MODES; unknown play method → empty method label.
        val defaults = buildQualityOptions(
            ladder = FALLBACK_QUALITY_LADDER.take(1),
            nativeHeight = 480,
            playMethod = "mystery",
            sourceResolutionLabel = "",
            modes = emptyList(),
        )
        assertEquals("auto", defaults[0].id)
        assertEquals("Original", defaults[1].label)
        assertEquals("", defaults[1].sublabel)

        // nativeHeight <= 0 includes every rung.
        val all = buildQualityOptions(
            ladder = FALLBACK_QUALITY_LADDER,
            nativeHeight = 0,
            modes = listOf("auto"),
        )
        assertEquals(1 + FALLBACK_QUALITY_LADDER.size, all.size)
        assertEquals("~6 Mbps", all.first { it.id == "1080p" }.sublabel)
    }

    @Test
    fun `resolveQualityTargets maps rung auto remux and fallbacks`() {
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
        assertFalse(rung.copyVideo)

        assertNull(
            resolveQualityTargets(
                qualityId = "original",
                options = options,
                playMethod = "direct",
                ladder = FALLBACK_QUALITY_LADDER,
            ),
        )

        val remux = resolveQualityTargets(
            qualityId = "original",
            options = options,
            playMethod = "remux",
            ladder = FALLBACK_QUALITY_LADDER,
        )
        assertNotNull(remux)
        assertTrue(remux.copyVideo)
        assertEquals(0, remux.bitrateKbps)

        val transcodeOriginal = resolveQualityTargets(
            qualityId = "original",
            options = options,
            playMethod = "transcode",
            ladder = FALLBACK_QUALITY_LADDER,
            deviceMaxHeight = 720,
        )
        assertNotNull(transcodeOriginal)
        assertFalse(transcodeOriginal.copyVideo)
        assertEquals(4000, transcodeOriginal.bitrateKbps)

        val auto = resolveQualityTargets(
            qualityId = "auto",
            options = options,
            playMethod = "transcode",
            ladder = FALLBACK_QUALITY_LADDER,
            deviceMaxHeight = 1080,
        )
        assertNotNull(auto)
        assertEquals("1080p", auto.resolution)

        val autoEmpty = resolveQualityTargets(
            qualityId = "auto",
            options = emptyList(),
            playMethod = null,
            ladder = emptyList(),
        )
        assertEquals(QualityTargets("1080p", 6_000, copyVideo = false), autoEmpty)

        // Lookup by ladder id when options lack resolution/bitrate.
        val fromLadder = resolveQualityTargets(
            qualityId = "480p",
            options = listOf(QualityMenuOption(id = "480p", label = "480p")),
            playMethod = null,
            ladder = FALLBACK_QUALITY_LADDER,
        )
        assertEquals("480p", fromLadder!!.resolution)
        assertEquals(1500, fromLadder.bitrateKbps)

        assertNull(
            resolveQualityTargets(
                qualityId = "missing",
                options = emptyList(),
                playMethod = null,
                ladder = FALLBACK_QUALITY_LADDER,
            ),
        )
    }

    @Test
    fun `bestAutoRung prefers tallest at or below max with tolerance`() {
        assertNull(bestAutoRung(emptyList(), 1080))
        assertEquals("2160p", bestAutoRung(FALLBACK_QUALITY_LADDER, 0)!!.id)
        assertEquals("1080p-high", bestAutoRung(FALLBACK_QUALITY_LADDER, 1080)!!.id)
        assertEquals("420p", bestAutoRung(FALLBACK_QUALITY_LADDER, 50)!!.id)
        assertEquals(
            "1080p",
            bestAutoRung(
                listOf(QualityLadderRung("1080p", "1080p", "1080p", 1080, 6000)),
                1075,
            )!!.id,
        )
    }

    @Test
    fun `resolveNativeHeight and sourceHeightForFile cover aliases and probes`() {
        assertEquals(2160, resolveNativeHeight("4K", FALLBACK_QUALITY_LADDER))
        assertEquals(2160, resolveNativeHeight("uhd", FALLBACK_QUALITY_LADDER))
        assertEquals(1440, resolveNativeHeight("1440p", FALLBACK_QUALITY_LADDER))
        assertEquals(1080, resolveNativeHeight("fhd", emptyList()))
        assertEquals(720, resolveNativeHeight("hd", emptyList()))
        assertEquals(480, resolveNativeHeight("sd", emptyList()))
        assertEquals(420, resolveNativeHeight("420p", emptyList()))
        assertEquals(540, resolveNativeHeight("540p", emptyList()))
        assertEquals(0, resolveNativeHeight("bogus", emptyList()))
        assertEquals(
            1080,
            resolveNativeHeight("1080p", listOf(QualityLadderRung("x", "x", "1080p", 1080, 1))),
        )

        assertEquals(2160, sourceHeightForFile(FALLBACK_QUALITY_LADDER, "1080p", probedHeight = 2160))
        assertEquals(0, sourceHeightForFile(FALLBACK_QUALITY_LADDER, null))
        assertEquals(0, sourceHeightForFile(FALLBACK_QUALITY_LADDER, "  "))
        assertEquals(720, sourceHeightForFile(FALLBACK_QUALITY_LADDER, "720p"))
    }

    @Test
    fun `toV3QualityPreference collapses high variants`() {
        assertEquals("auto", toV3QualityPreference(""))
        assertEquals("auto", toV3QualityPreference("auto"))
        assertEquals("original", toV3QualityPreference("original"))
        assertEquals("original", toV3QualityPreference("source"))
        assertEquals("original", toV3QualityPreference("max"))
        assertEquals("1080p", toV3QualityPreference("1080p-high"))
        assertEquals("1080p", toV3QualityPreference("fhd"))
        assertEquals("720p", toV3QualityPreference("720p"))
        assertEquals("720p", toV3QualityPreference("hd"))
        assertEquals("480p", toV3QualityPreference("420p"))
        assertEquals("480p", toV3QualityPreference("sd"))
        assertEquals("2160p", toV3QualityPreference("4k"))
        assertEquals("2160p", toV3QualityPreference("uhd"))
        assertEquals("2160p", toV3QualityPreference("2160p"))
        assertEquals("custom", toV3QualityPreference("custom"))
    }

    @Test
    fun `parseQualityLadderResponse accepts valid server payload`() {
        val parsed = parseQualityLadderResponse(
            QualityLadderResponse(
                rungs = FALLBACK_QUALITY_LADDER,
                modes = listOf("auto", "original"),
                sourceHeight = 2160,
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
        assertEquals("720p", client.cachedOrFallback(sourceHeight = 720).single().id)

        val failing = QualityLadderClient { ApiResult.Error(500, "err", "fail") }
        assertEquals(FALLBACK_QUALITY_LADDER, failing.fetch())

        val network = QualityLadderClient { ApiResult.NetworkError(RuntimeException("down")) }
        assertEquals(FALLBACK_QUALITY_LADDER, network.fetch())
        assertEquals(
            "1080p-high",
            network.cachedOrFallback(sourceHeight = 1080).first().id,
        )

        val invalid = QualityLadderClient {
            ApiResult.Success(QualityLadderResponse(rungs = emptyList()))
        }
        assertEquals(FALLBACK_QUALITY_LADDER, invalid.fetch())

        client.resetCacheForTests()
        assertEquals(FALLBACK_QUALITY_LADDER.first().id, client.cachedOrFallback().first().id)
        client.fetch()
        assertEquals(2, calls)
    }

    @Test
    fun `formatQualityBitrate collapses integers`() {
        assertEquals("8 Mbps", formatQualityBitrate(8000))
        assertEquals("1.5 Mbps", formatQualityBitrate(1500))
        assertEquals("20 Mbps", formatQualityBitrate(20_000))
        assertEquals("720 kbps", formatQualityBitrate(720))
    }

    @Test
    fun `rungForSession picks nearest bitrate at resolution`() {
        assertNull(rungForSession(FALLBACK_QUALITY_LADDER, "", 6000))
        assertNull(rungForSession(FALLBACK_QUALITY_LADDER, "999p", 6000))
        val high = rungForSession(FALLBACK_QUALITY_LADDER, "1080p", 9_500)
        assertEquals("1080p-high", high!!.id)
        val std = rungForSession(FALLBACK_QUALITY_LADDER, "1080P", 5_000)
        assertEquals("1080p", std!!.id)
    }
}
