package org.siloserver.silo.common.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticatedDataSourceFactoryTest {
    @Test
    fun streamRelativeFallbackUsesPlaybackStreamResolver() {
        assertEquals(
            "https://silo.example/api/v1/stream/session-1",
            resolveRoutedDataSourceUrl("https://silo.example", "/stream/session-1"),
        )
    }

    @Test
    fun apiRelativeFallbackIsNotDoublePrefixed() {
        assertEquals(
            "https://silo.example/api/v1/stream/session-1",
            resolveRoutedDataSourceUrl("https://silo.example", "/api/v1/stream/session-1"),
        )
    }

    @Test
    fun absoluteFallbackUrlsArePreserved() {
        assertEquals(
            "https://cdn.example/stream/session-1",
            resolveRoutedDataSourceUrl("https://silo.example", "https://cdn.example/stream/session-1"),
        )
    }

    @Test
    fun srtSubtitlePayloadBytesAreNormalizedBeforeMedia3ParsesThem() {
        val loose = """
            00:00:01.000 --> 00:00:02.000
            Hello.
            00:00:03.000 --> 00:00:04.000
            Goodbye.
        """.trimIndent().encodeToByteArray()

        val text = normalizeSubripDataIfNeeded(loose).decodeToString()

        assertTrue(text.startsWith("1\n00:00:01,000 --> 00:00:02,000"))
        assertTrue(text.contains("\n\n2\n00:00:03,000 --> 00:00:04,000"))
    }

    @Test
    fun srtSubtitleUrlsAreDetectedForWholeFileReads() {
        assertTrue(shouldNormalizeSubripPath("/api/v1/stream/session/subtitles/4.srt", 0L))
    }

    @Test
    fun nonSrtPayloadsDoNotUseSubtitleNormalization() {
        assertFalse(shouldNormalizeSubripPath("/video/segment.ts", 0L))
        assertFalse(shouldNormalizeSubripPath("/api/v1/stream/session/subtitles/4.srt", 512L))
    }
}
