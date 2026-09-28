package org.prairieserver.prairie.model.download

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadTaxonomyTest {
    @Test
    fun mediaTypeWireDecodeIsCaseInsensitiveAndTolerant() {
        assertEquals(DownloadMediaType.TvShow, DownloadMediaType.fromWire("TV"))
        assertEquals(DownloadMediaType.Ebook, DownloadMediaType.fromWire("ebook"))
        assertEquals(DownloadMediaType.Unknown, DownloadMediaType.fromWire("comic"))
        assertEquals(DownloadMediaType.Unknown, DownloadMediaType.fromWire(null))
    }

    @Test
    fun catalogTypesClusterEpisodesUnderTvShows() {
        assertEquals(DownloadMediaType.Movie, DownloadMediaType.fromCatalogType("Movie"))
        for (type in listOf("series", "season", "episode")) {
            assertEquals(DownloadMediaType.TvShow, DownloadMediaType.fromCatalogType(type))
        }
        assertEquals(DownloadMediaType.Audiobook, DownloadMediaType.fromCatalogType("audiobook"))
        assertEquals(DownloadMediaType.Ebook, DownloadMediaType.fromCatalogType("EBOOK"))
        assertEquals(DownloadMediaType.Unknown, DownloadMediaType.fromCatalogType("person"))
        assertEquals(DownloadMediaType.Unknown, DownloadMediaType.fromCatalogType(null))
    }

    @Test
    fun subscriptionEnumsTrimLowercaseAndFallBack() {
        assertEquals(DownloadSubscriptionTargetType.AudiobookSeries, DownloadSubscriptionTargetType.fromWire(" Audiobook_Series "))
        assertEquals(DownloadSubscriptionTargetType.Collection, DownloadSubscriptionTargetType.fromWire("collection"))
        assertEquals(DownloadSubscriptionTargetType.Series, DownloadSubscriptionTargetType.fromWire("podcast"))
        assertEquals(DownloadSubscriptionTargetType.Series, DownloadSubscriptionTargetType.fromWire(null))
        assertEquals(DownloadSubscriptionMediaKind.Reading, DownloadSubscriptionMediaKind.fromWire("READING"))
        assertEquals(DownloadSubscriptionMediaKind.Video, DownloadSubscriptionMediaKind.fromWire("hologram"))
    }

    @Test
    fun subscriptionPersistsWithDefaults() {
        val sub = DownloadSubscription(
            id = "s1", serverId = "srv", profileId = "p", targetType = DownloadSubscriptionTargetType.Season,
            targetId = "t", displayTitle = "Show S1", mediaKind = DownloadSubscriptionMediaKind.Video,
            createdAt = 1, updatedAt = 2,
        )
        val decoded = Json.decodeFromString(DownloadSubscription.serializer(), Json.encodeToString(DownloadSubscription.serializer(), sub))
        assertEquals(sub, decoded)
        assertEquals(true, decoded.wifiOnly)
        assertEquals(3, decoded.keepUnwatchedLimit)
        assertEquals(7, decoded.deleteWatchedAfterDays)
    }
}
