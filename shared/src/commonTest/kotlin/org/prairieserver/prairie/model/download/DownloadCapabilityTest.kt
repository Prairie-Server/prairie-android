package org.prairieserver.prairie.model.download

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadCapabilityTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes the server capability envelope`() {
        val source = """
            {
              "enabled": true,
              "download_allowed": true,
              "quality_presets": ["original", "20mbps", "10mbps", "5mbps", "2mbps", "1mbps"],
              "transcode_enabled": true,
              "transcode_user_allowed": true,
              "season_download": true,
              "series_monitoring": true,
              "monitoring_modes": ["all", "future"]
            }
        """.trimIndent()

        val cap = json.decodeFromString<DownloadCapability>(source)
        assertTrue(cap.isUsable)
        assertEquals(6, cap.qualityPresets.size)
        assertTrue(cap.transcodeEnabled)
    }

    @Test
    fun `allowedQualities offers every preset when transcode is enabled and allowed`() {
        val cap = DownloadCapability(
            enabled = true,
            downloadAllowed = true,
            qualityPresets = listOf("original", "20mbps", "10mbps", "5mbps", "2mbps", "1mbps"),
            transcodeEnabled = true,
            transcodeUserAllowed = true,
        )
        assertEquals(DownloadQuality.entries.toList(), cap.allowedQualities())
    }

    @Test
    fun `allowedQualities collapses to Original when transcode is disabled`() {
        val cap = DownloadCapability(
            enabled = true,
            downloadAllowed = true,
            // Even if the server still listed bitrate presets, a disabled
            // transcode gate must hide them (avoids a 403 transcode_disabled).
            qualityPresets = listOf("original", "10mbps", "5mbps"),
            transcodeEnabled = false,
            transcodeUserAllowed = true,
        )
        assertEquals(listOf(DownloadQuality.Original), cap.allowedQualities())
    }

    @Test
    fun `allowedQualities collapses to Original when the user is not allowed to transcode`() {
        val cap = DownloadCapability(
            enabled = true,
            downloadAllowed = true,
            qualityPresets = listOf("original", "10mbps"),
            transcodeEnabled = true,
            transcodeUserAllowed = false,
        )
        assertEquals(listOf(DownloadQuality.Original), cap.allowedQualities())
    }

    @Test
    fun `allowedQualities filters to only the presets the server advertised`() {
        val cap = DownloadCapability(
            enabled = true,
            downloadAllowed = true,
            qualityPresets = listOf("original", "5mbps"),
            transcodeEnabled = true,
            transcodeUserAllowed = true,
        )
        assertEquals(
            listOf(DownloadQuality.Original, DownloadQuality.Mbps5),
            cap.allowedQualities(),
        )
    }

    @Test
    fun `allowedQualities never returns empty`() {
        val cap = DownloadCapability(enabled = false, downloadAllowed = false)
        assertFalse(cap.isUsable)
        assertEquals(listOf(DownloadQuality.Original), cap.allowedQualities())
    }
}
