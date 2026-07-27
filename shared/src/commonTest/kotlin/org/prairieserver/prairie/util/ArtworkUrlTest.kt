package org.prairieserver.prairie.util

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtworkUrlTest {
    @AfterTest
    fun resetFormats() {
        ImageFormats.resetForTests()
    }

    @Test
    fun rewritesWebPObjectKeysToAVIF() {
        assertEquals(
            "library/1/poster/original.abc123.avif",
            ArtworkUrl.webPAVIFSibling("library/1/poster/original.abc123.webp"),
        )
        assertEquals("original.avif", ArtworkUrl.webPAVIFSibling("original.webp"))
    }

    @Test
    fun rewritesWebPObjectKeysToPNG() {
        assertEquals(
            "library/1/poster/original.abc123.png",
            ArtworkUrl.webPPNGSibling("library/1/poster/original.abc123.webp"),
        )
    }

    @Test
    fun preservesQueryStringsOnAbsoluteURLs() {
        assertEquals(
            "https://cdn.example.com/art/original.rev.avif?X-Amz-Signature=abc",
            ArtworkUrl.webPAVIFSibling(
                "https://cdn.example.com/art/original.rev.webp?X-Amz-Signature=abc",
            ),
        )
        assertEquals(
            "https://cdn.example.com/art/original.rev.png?X-Amz-Signature=abc",
            ArtworkUrl.webPPNGSibling(
                "https://cdn.example.com/art/original.rev.webp?X-Amz-Signature=abc",
            ),
        )
    }

    @Test
    fun returnsNullForNonWebPInputs() {
        assertNull(ArtworkUrl.webPAVIFSibling("poster.jpg"))
        assertNull(ArtworkUrl.webPPNGSibling("https://cdn.example.com/art/original.png"))
        assertNull(ArtworkUrl.webPAVIFSibling(""))
        assertNull(ArtworkUrl.webPAVIFSibling(null))
    }

    @Test
    fun candidatesFollowConfiguredPreference() {
        ImageFormats.configure(listOf("avif", "webp", "png"))
        assertEquals(
            listOf("poster.avif", "poster.webp", "poster.png"),
            ArtworkUrl.candidates("poster.webp"),
        )
        ImageFormats.configure(listOf("webp", "png"))
        assertEquals(
            listOf("poster.webp", "poster.png"),
            ArtworkUrl.candidates("poster.webp"),
        )
        assertEquals(listOf("poster.jpg"), ArtworkUrl.candidates("poster.jpg"))
    }

    @Test
    fun preferredFallsBackToOriginal() {
        ImageFormats.configure(listOf("avif", "webp", "png"))
        assertEquals("poster.jpg", ArtworkUrl.preferred("poster.jpg"))
        assertEquals("poster.avif", ArtworkUrl.preferred("poster.webp"))
        ImageFormats.configure(listOf("webp", "png"))
        assertEquals("poster.webp", ArtworkUrl.preferred("poster.webp"))
    }

    @Test
    fun extensionMatchIsCaseInsensitive() {
        assertEquals("poster.avif", ArtworkUrl.webPAVIFSibling("poster.WEBP"))
        assertEquals("poster.png", ArtworkUrl.webPPNGSibling("poster.WEBP"))
    }

    @Test
    fun imageFormatsHeaderValue() {
        ImageFormats.configureForApiLevel(31)
        assertEquals("avif,webp,png", ImageFormats.headerValue())
        ImageFormats.configureForApiLevel(30)
        assertEquals("webp,png", ImageFormats.headerValue())
    }
}
