package org.prairieserver.prairie.tv.ui.components

import org.prairieserver.prairie.model.catalog.OverlaySummary
import kotlin.test.Test
import kotlin.test.assertEquals

class TvFocusMarqueeModelTest {

    @Test fun qualityBadgesPreserveDolbyVisionAndAtmos() {
        val summary = OverlaySummary(
            resolution = "2160p",
            hdr = "Dolby Vision",
            audio = "TrueHD Atmos",
        )

        assertEquals(
            listOf("4K", "DOLBY VISION", "ATMOS"),
            TvMarqueeContent.qualityBadges(summary),
        )
    }

    @Test fun qualityBadgesPreserveHdr10AndDtsHd() {
        val summary = OverlaySummary(hdr = "HDR10", audio = "DTS-HD")

        assertEquals(
            listOf("HDR10", "DTS-HD"),
            TvMarqueeContent.qualityBadges(summary),
        )
    }
}
