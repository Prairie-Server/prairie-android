package org.prairieserver.prairie.model

import org.prairieserver.prairie.model.admin.AdminAuditEntry
import org.prairieserver.prairie.model.admin.AdminSession
import org.prairieserver.prairie.model.audiobook.AudiobookBookmark
import org.prairieserver.prairie.model.auth.ImpersonationInfo
import org.prairieserver.prairie.model.book.BookFormat
import org.prairieserver.prairie.model.book.BookMetadata
import org.prairieserver.prairie.model.catalog.BrowseResponse
import org.prairieserver.prairie.model.catalog.CastMember
import org.prairieserver.prairie.model.catalog.CrewMember
import org.prairieserver.prairie.model.catalog.Season
import org.prairieserver.prairie.model.catalog.SeasonUserData
import org.prairieserver.prairie.model.catalog.SubtitleInfo
import org.prairieserver.prairie.model.catalog.sortedForDisplay
import org.prairieserver.prairie.model.common.ErrorResponse
import org.prairieserver.prairie.model.common.PaginatedRequest
import org.prairieserver.prairie.model.download.DownloadMediaType
import org.prairieserver.prairie.model.download.DownloadQuality
import org.prairieserver.prairie.model.download.DownloadRecord
import org.prairieserver.prairie.model.download.DownloadSidecar
import org.prairieserver.prairie.model.download.DownloadSubscription
import org.prairieserver.prairie.model.download.DownloadSubscriptionMediaKind
import org.prairieserver.prairie.model.download.DownloadSubscriptionTargetType
import org.prairieserver.prairie.model.personal.LibraryPlaybackPreference
import org.prairieserver.prairie.model.personal.ProgressEntry
import org.prairieserver.prairie.model.playback.HdrCapabilities
import org.prairieserver.prairie.model.playback.VideoDecodeCapability
import org.prairieserver.prairie.model.profile.ProfilesResponse
import org.prairieserver.prairie.model.profile.VerifyPinRequest
import org.prairieserver.prairie.model.profile.VerifyPinResponse
import org.prairieserver.prairie.model.section.SectionLayout
import org.prairieserver.prairie.model.section.splitFeatured
import org.prairieserver.prairie.model.section.ResolvedSection
import org.prairieserver.prairie.model.settings.LibraryPlaybackPref
import org.prairieserver.prairie.model.settings.LibraryPlaybackPrefRequest
import org.prairieserver.prairie.model.settings.LibraryPlaybackPrefsResponse
import org.prairieserver.prairie.model.settings.PlaybackPrefSentinel
import org.prairieserver.prairie.network.PrairieJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Broader decoding / helper coverage for shared models that were below the
 * 90% commonMain gate — serialization constructors + enum wire maps.
 */
class SharedModelsCoverageTest {

    private val json = PrairieJson

    @Test
    fun downloadSidecarAndSubscriptionRoundTrip() {
        val record = DownloadRecord(
            id = "dl1",
            contentId = "c1",
            mediaFileId = 9,
            kind = "queued",
            status = "completed",
            createdAt = "2026-01-01T00:00:00Z",
        )
        val sidecar = DownloadSidecar(
            record = record,
            title = "Film",
            subtitle = "Director's Cut",
            posterUrl = "/p.jpg",
            year = 2024,
            seriesTitle = null,
            seriesContentId = null,
            seasonNumber = null,
            episodeNumber = null,
            fileName = "film.mkv",
            container = "mkv",
            localUri = "content://downloads/1",
            mediaType = DownloadMediaType.Movie.wire,
            overview = "About",
            author = null,
            narrator = null,
            durationSeconds = 120.0,
            chapters = emptyList(),
            resumeValidator = "etag-1",
            updatedAtMs = 1_700_000_000_000L,
        )
        val encoded = json.encodeToString(DownloadSidecar.serializer(), sidecar)
        val decoded = json.decodeFromString(DownloadSidecar.serializer(), encoded)
        assertEquals("Film", decoded.title)
        assertEquals(DownloadMediaType.Movie, DownloadMediaType.fromWire(decoded.mediaType))
        assertEquals(DownloadMediaType.TvShow, DownloadMediaType.fromCatalogType("episode"))
        assertEquals(DownloadMediaType.Unknown, DownloadMediaType.fromWire("nope"))

        assertEquals(DownloadSubscriptionTargetType.Season, DownloadSubscriptionTargetType.fromWire("SEASON"))
        assertEquals(DownloadSubscriptionTargetType.Series, DownloadSubscriptionTargetType.fromWire(null))
        assertEquals(DownloadSubscriptionMediaKind.Audio, DownloadSubscriptionMediaKind.fromWire("audio"))
        assertEquals(DownloadSubscriptionMediaKind.Video, DownloadSubscriptionMediaKind.fromWire("x"))

        val sub = DownloadSubscription(
            id = "s1",
            serverId = "srv",
            profileId = "p1",
            targetType = DownloadSubscriptionTargetType.Series,
            targetId = "series-1",
            displayTitle = "Show",
            mediaKind = DownloadSubscriptionMediaKind.Video,
            quality = DownloadQuality.Mbps10,
            wifiOnly = true,
            enabled = true,
            includeExisting = true,
            keepUnwatchedLimit = 5,
            deleteWatchedAfterDays = 14,
            createdAt = 1L,
            updatedAt = 2L,
            lastEvaluatedAt = 3L,
            lastError = null,
        )
        val subJson = json.encodeToString(DownloadSubscription.serializer(), sub)
        assertEquals("s1", json.decodeFromString(DownloadSubscription.serializer(), subJson).id)
    }

    @Test
    fun bookMetadataAndFormats() {
        val meta = BookMetadata(
            author = "Ada",
            authors = listOf("Ada", "Grace"),
            format = "epub",
            pageCount = 200,
            fileSizeBytes = 1024,
            fileUrl = "/books/1.epub",
            publisher = "Press",
            publishedDate = "2020",
            isbn = "123",
            language = "en",
            wordCount = 50_000,
        )
        assertEquals(BookFormat.Epub, meta.formatEnum())
        assertEquals(BookFormat.Pdf, BookFormat.fromWire(".PDF"))
        assertEquals(BookFormat.Cbz, BookFormat.fromPath("/x/y.CBZ"))
        assertEquals(BookFormat.Unknown, BookFormat.fromPath(null))
        val encoded = json.encodeToString(BookMetadata.serializer(), meta)
        assertEquals("Ada", json.decodeFromString(BookMetadata.serializer(), encoded).author)
    }

    @Test
    fun catalogPeopleSeasonsAndBrowse() {
        val cast = CastMember(name = "Ada", character = "Lead", order = 1, personId = "p1")
        val crew = CrewMember(name = "Grace", job = "Director", personId = "p2")
        assertEquals("Ada", json.decodeFromString(CastMember.serializer(), json.encodeToString(CastMember.serializer(), cast)).name)
        assertEquals("Director", json.decodeFromString(CrewMember.serializer(), json.encodeToString(CrewMember.serializer(), crew)).job)

        val seasons = listOf(
            Season(contentId = "s0", seasonNumber = 0, isSpecials = true, title = "Specials"),
            Season(contentId = "s2", seasonNumber = 2, title = "Two", episodeCount = 8),
            Season(contentId = "s1", seasonNumber = 1, title = "One", episodeCount = 10, userData = SeasonUserData()),
        )
        assertEquals(listOf("s1", "s2", "s0"), seasons.sortedForDisplay().map { it.contentId })

        val browse = BrowseResponse(items = emptyList(), total = 0)
        assertEquals(0, json.decodeFromString(BrowseResponse.serializer(), json.encodeToString(BrowseResponse.serializer(), browse)).total)

        val sub = SubtitleInfo(source = "embedded", language = "en", codec = "subrip", forced = true, title = "English")
        assertTrue(json.decodeFromString(SubtitleInfo.serializer(), json.encodeToString(SubtitleInfo.serializer(), sub)).forced)
    }

    @Test
    fun playbackDecodeAndAdminWire() {
        val decode = VideoDecodeCapability(
            codec = "hevc",
            decoderName = "c2.hevc",
            profiles = listOf("Main"),
            levels = listOf(150),
            bitDepths = listOf(10),
            maxWidth = 3840,
            maxHeight = 2160,
            maxFrameRate = 60.0,
            maxBitrateKbps = 80_000,
            hardware = true,
        )
        assertTrue(json.decodeFromString(VideoDecodeCapability.serializer(), json.encodeToString(VideoDecodeCapability.serializer(), decode)).hardware)

        val hdr = HdrCapabilities(hdr10 = true, hdr10Plus = false, hlg = false, dolbyVisionProfiles = listOf(5, 8))
        assertEquals(listOf(5, 8), json.decodeFromString(HdrCapabilities.serializer(), json.encodeToString(HdrCapabilities.serializer(), hdr)).dolbyVisionProfiles)

        val session = AdminSession(
            sessionId = "sess",
            userId = 1,
            username = "u",
            profileId = "p",
            mediaFileId = 2,
            requestedMediaFileId = 2,
            mediaTitle = "Film",
            mediaType = "movie",
            playMethod = "direct",
            reportingNode = "n1",
            startedAt = "t0",
            updatedAt = "t1",
        )
        assertEquals("sess", json.decodeFromString(AdminSession.serializer(), json.encodeToString(AdminSession.serializer(), session)).sessionId)

        val audit = AdminAuditEntry(
            id = 9,
            timestamp = "t",
            clientIp = "1.1.1.1",
            method = "GET",
            path = "/api/v1/x",
            statusCode = 200,
        )
        assertEquals(200, json.decodeFromString(AdminAuditEntry.serializer(), json.encodeToString(AdminAuditEntry.serializer(), audit)).statusCode)
    }

    @Test
    fun settingsPersonalAuthAndCommon() {
        val pref = LibraryPlaybackPref(profileId = "p", libraryId = 3, audioLanguage = "en")
        val prefs = LibraryPlaybackPrefsResponse(preferences = listOf(pref))
        assertEquals(3, json.decodeFromString(LibraryPlaybackPrefsResponse.serializer(), json.encodeToString(LibraryPlaybackPrefsResponse.serializer(), prefs)).preferences.first().libraryId)
        val req = LibraryPlaybackPrefRequest(subtitleMode = "auto", showForcedSubtitles = true)
        assertTrue(json.decodeFromString(LibraryPlaybackPrefRequest.serializer(), json.encodeToString(LibraryPlaybackPrefRequest.serializer(), req)).showForcedSubtitles == true)
        assertEquals("__inherit__", PlaybackPrefSentinel.Inherit)

        val progress = ProgressEntry(mediaItemId = "m1", positionSeconds = 10.0, durationSeconds = 100.0, completed = false)
        assertEquals("m1", json.decodeFromString(ProgressEntry.serializer(), json.encodeToString(ProgressEntry.serializer(), progress)).mediaItemId)
        val libPref = LibraryPlaybackPreference(profileId = "p", libraryId = 1, subtitleLanguage = "es")
        assertEquals("es", json.decodeFromString(LibraryPlaybackPreference.serializer(), json.encodeToString(LibraryPlaybackPreference.serializer(), libPref)).subtitleLanguage)

        val bookmark = AudiobookBookmark(id = "b1", positionSeconds = 12.5, chapterTitle = "Ch1", createdAtMs = 99L)
        assertEquals("Ch1", json.decodeFromString(AudiobookBookmark.serializer(), json.encodeToString(AudiobookBookmark.serializer(), bookmark)).chapterTitle)

        val imp = ImpersonationInfo(active = true, impersonatorUserId = 2, impersonatorUsername = "admin")
        assertTrue(json.decodeFromString(ImpersonationInfo.serializer(), json.encodeToString(ImpersonationInfo.serializer(), imp)).active)

        assertEquals("err", json.decodeFromString(ErrorResponse.serializer(), """{"error":"err","message":"m"}""").error)
        assertEquals(10, json.decodeFromString(PaginatedRequest.serializer(), """{"offset":0,"limit":10}""").limit)

        val pinReq = VerifyPinRequest(pin = "1234")
        val pinRes = VerifyPinResponse(valid = true, profileToken = "tok")
        assertEquals("1234", json.decodeFromString(VerifyPinRequest.serializer(), json.encodeToString(VerifyPinRequest.serializer(), pinReq)).pin)
        assertEquals("tok", json.decodeFromString(VerifyPinResponse.serializer(), json.encodeToString(VerifyPinResponse.serializer(), pinRes)).profileToken)
        assertTrue(json.decodeFromString(ProfilesResponse.serializer(), """{"profiles":[]}""").profiles.isEmpty())
    }

    @Test
    fun sectionLayoutAndFeaturedSplit() {
        val layout = SectionLayout(id = "home", title = "Home", sectionType = "custom")
        assertEquals("home", json.decodeFromString(SectionLayout.serializer(), json.encodeToString(SectionLayout.serializer(), layout)).id)

        val featured = ResolvedSection(
            id = "f1",
            sectionType = "continue_watching",
            title = "Featured",
            featured = true,
            items = emptyList(),
        )
        // Empty featured does not qualify.
        assertNull(listOf(featured).splitFeatured().featured)

        val withItems = featured.copy(
            items = listOf(
                org.prairieserver.prairie.model.section.SectionItem(
                    contentId = "c1",
                    type = "movie",
                    title = "A",
                ),
            ),
        )
        val plain = ResolvedSection(
            id = "r1",
            sectionType = "recently_added",
            title = "Recent",
            featured = false,
            items = emptyList(),
        )
        val split = listOf(withItems, plain).splitFeatured()
        assertEquals("f1", split.featured?.id)
        assertEquals(listOf("r1"), split.rest.map { it.id })
        assertFalse(split.rest.any { it.id == "f1" })
    }
}
