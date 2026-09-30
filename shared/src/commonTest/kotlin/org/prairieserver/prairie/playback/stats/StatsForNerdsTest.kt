package org.prairieserver.prairie.playback.stats

import org.prairieserver.prairie.model.playback.AudioValidationClaims
import org.prairieserver.prairie.model.playback.PlayMethod
import org.prairieserver.prairie.model.playback.PlaybackDegradationWarning
import org.prairieserver.prairie.model.playback.PlaybackDelivery
import org.prairieserver.prairie.model.playback.PlaybackExecutionPlan
import org.prairieserver.prairie.model.playback.PlaybackRouteFamily
import org.prairieserver.prairie.model.playback.PlaybackSourceMetadata
import org.prairieserver.prairie.model.playback.PlaybackStreamRequest
import org.prairieserver.prairie.model.playback.PlaybackTransformationV3
import org.prairieserver.prairie.model.playback.PlaybackValidationClaims
import org.prairieserver.prairie.model.playback.VideoValidationClaims
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsForNerdsTest {

    // --- Ring buffer ------------------------------------------------------

    @Test
    fun `event log keeps only the newest entries, oldest first`() {
        var log = PlayerEventLog(capacity = 3)
        for (i in 1..5) log = log.append(i * 1000L, PlayerEventSeverity.INFO, "event $i")
        assertEquals(listOf("event 3", "event 4", "event 5"), log.entries.map { it.message })
        assertFalse(log.isEmpty)
    }

    @Test
    fun `default capacity matches the smarttv window`() {
        var log = PlayerEventLog()
        assertTrue(log.isEmpty)
        for (i in 1..20) log = log.append(i.toLong(), PlayerEventSeverity.INFO, "e$i")
        assertEquals(PlayerEventLog.DEFAULT_CAPACITY, log.entries.size)
        assertEquals("e13", log.entries.first().message)
    }

    @Test
    fun `identical consecutive events collapse with a repeat count`() {
        val log = PlayerEventLog()
            .append(1_000L, PlayerEventSeverity.WARNING, "Dropped 3 frames")
            .append(2_000L, PlayerEventSeverity.WARNING, "Dropped 3 frames")
            .append(3_000L, PlayerEventSeverity.ERROR, "Player error")
            .append(4_000L, PlayerEventSeverity.WARNING, "Dropped 3 frames")
        assertEquals(3, log.entries.size)
        assertEquals(2, log.entries[0].repeat)
        assertEquals(2_000L, log.entries[0].atEpochMs)
        assertEquals(1, log.entries[2].repeat)
    }

    @Test
    fun `same message at a different severity does not collapse`() {
        val log = PlayerEventLog()
            .append(1L, PlayerEventSeverity.INFO, "x")
            .append(2L, PlayerEventSeverity.ERROR, "x")
        assertEquals(2, log.entries.size)
    }

    @Test
    fun `non-positive capacity still keeps the latest event`() {
        val log = PlayerEventLog(capacity = 0)
            .append(1L, PlayerEventSeverity.INFO, "a")
            .append(2L, PlayerEventSeverity.INFO, "b")
        assertEquals(listOf("b"), log.entries.map { it.message })
    }

    @Test
    fun `appended messages have URL secrets removed`() {
        val log = PlayerEventLog().append(
            1L,
            PlayerEventSeverity.ERROR,
            "Load error: 403 https://srv.lan:8096/api/v2/stream/a/master.m3u8?token=abc.def#t",
        )
        assertEquals("Load error: 403 https://srv.lan:8096/api/v2/stream/a/master.m3u8?…", log.entries.single().message)
    }

    @Test
    fun `event lines carry a wall clock and severity tag`() {
        // 2026-01-01T13:02:03.456Z
        val t = 1_767_272_523_456L
        assertEquals("13:02:03", formatEventClock(t))
        assertEquals("08:02:03", formatEventClock(t, utcOffsetMs = -5L * 3_600_000))
        assertEquals("23:30:00", formatEventClock(-30L * 60_000))
        assertEquals("13:02:03 Ready", PlayerEvent(t, PlayerEventSeverity.INFO, "Ready").displayLine())
        assertEquals(
            "13:02:03 WARN Buffering ×4",
            PlayerEvent(t, PlayerEventSeverity.WARNING, "Buffering", repeat = 4).displayLine(),
        )
        assertEquals("13:02:03 ERROR Boom", PlayerEvent(t, PlayerEventSeverity.ERROR, "Boom").displayLine())
    }

    // --- Redaction / stream path ------------------------------------------

    @Test
    fun `redaction strips queries fragments and userinfo from every URL`() {
        assertEquals(
            "GET https://srv/a.m3u8?… then http://srv/b.ts?…",
            redactUrlSecrets("GET https://user:pw@srv/a.m3u8?t=1 then http://srv/b.ts#frag"),
        )
        assertEquals("no url here", redactUrlSecrets("no url here"))
    }

    @Test
    fun `stream description shows kind and path only`() {
        val hls = describeStreamUrl("https://srv.lan:8096/api/v2/stream/abc/master.m3u8?stream_token=secret")
        assertEquals(StreamUrlDescription("HLS", "/api/v2/stream/abc/master.m3u8"), hls)
        assertEquals("HLS · /api/v2/stream/abc/master.m3u8", hls?.displayValue())
        assertEquals("DASH", describeStreamUrl("https://srv/x/manifest.mpd")?.kind)
        assertEquals("Progressive", describeStreamUrl("https://srv/api/v2/stream/abc?token=t")?.kind)
        assertEquals("/", describeStreamUrl("https://srv")?.path)
    }

    @Test
    fun `stream type hint wins over the extension`() {
        assertEquals("HLS", describeStreamUrl("https://srv/api/v2/stream/abc", streamType = "HLS")?.kind)
        assertEquals("DASH", describeStreamUrl("https://srv/api/v2/stream/abc", streamType = "dash")?.kind)
    }

    @Test
    fun `local files show only the file name`() {
        assertEquals(
            StreamUrlDescription("Local file", "movie.mkv"),
            describeStreamUrl("file:///data/user/0/org.prairieserver.prairie/files/dl/movie.mkv"),
        )
        assertEquals("Local file", describeStreamUrl("/storage/emulated/0/x.mp4")?.kind)
        assertEquals("Local file", describeStreamUrl("content://media/external/video/42")?.kind)
    }

    @Test
    fun `token-like path segments are elided but ids stay`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"
        val opaque = "a".repeat(48)
        val d = describeStreamUrl("https://srv/s/$jwt/$opaque/9f1c2d3e-aaaa-bbbb-cccc-1234567890ab/index.m3u8")
        assertEquals("/s/…/…/9f1c2d3e-aaaa-bbbb-cccc-1234567890ab/index.m3u8", d?.path)
    }

    @Test
    fun `blank stream url has no description`() {
        assertNull(describeStreamUrl(null))
        assertNull(describeStreamUrl("   "))
        assertEquals("api/x.m3u8", describeStreamUrl("api/x.m3u8")?.path)
    }

    // --- Plan summary -----------------------------------------------------

    private fun plan(
        delivery: PlaybackDelivery = PlaybackDelivery.SERVER_REMUX_PROGRESSIVE,
        method: PlayMethod? = PlayMethod.REMUX,
        trace: List<String> = emptyList(),
        warnings: List<PlaybackDegradationWarning> = emptyList(),
        audioClaims: AudioValidationClaims = AudioValidationClaims(),
        videoClaims: VideoValidationClaims = VideoValidationClaims(),
    ) = PlaybackExecutionPlan(
        planId = "p1",
        delivery = delivery,
        routeFamily = PlaybackRouteFamily.SERVER_ADAPTIVE,
        stream = PlaybackStreamRequest(
            url = "https://srv/api/v2/stream/abc/master.m3u8?token=x",
            streamType = "hls",
            playMethod = method,
        ),
        source = PlaybackSourceMetadata(
            container = "mp4",
            videoCodec = "hevc",
            audioCodec = "eac3",
            resolution = "3840x1600",
            hdrFormat = "hdr10",
        ),
        claims = PlaybackValidationClaims(video = videoClaims, audio = audioClaims),
        transformations = listOf(
            PlaybackTransformationV3(name = "audio_transcode"),
            PlaybackTransformationV3(name = "audio_transcode"),
            PlaybackTransformationV3(name = " "),
        ),
        degradationWarnings = warnings,
        decisionTrace = trace,
    )

    @Test
    fun `plan summary names method, delivery, container and codecs`() {
        assertEquals(
            "Remux · server_remux_progressive · container=mp4 · video=hevc 3840x1600 hdr10 · audio=eac3",
            plan().statsSummary(),
        )
        assertEquals(
            "Transcode · server_transcode_hls · container=mp4 · video=hevc 3840x1600 hdr10 · audio=aac passthrough",
            plan(
                delivery = PlaybackDelivery.SERVER_TRANSCODE_HLS,
                method = null,
                audioClaims = AudioValidationClaims(codec = "aac", passthrough = true),
            ).statsSummary(),
        )
    }

    @Test
    fun `planner reason prefers the trace, then warnings, then claims`() {
        assertEquals(
            "video_ok → audio_adaptation",
            plan(trace = listOf("probe", "video_ok", "audio_adaptation", " ").statsReason(),
        )
        assertEquals(
            "Audio downmixed; hdr_tonemap",
            plan(
                warnings = listOf(
                    PlaybackDegradationWarning("downmix", "Audio downmixed"),
                    PlaybackDegradationWarning("hdr_tonemap", ""),
                ),
            ).statsReason(),
        )
        assertEquals(
            "eac3 unsupported",
            plan(audioClaims = AudioValidationClaims(reason = "eac3 unsupported")).statsReason(),
        )
        assertEquals(
            "profile 7",
            plan(videoClaims = VideoValidationClaims(dolbyVisionReason = "profile 7")).statsReason(),
        )
        assertNull(plan().statsReason())
    }

    @Test
    fun `transformations are distinct and non-blank`() {
        assertEquals("audio_transcode", plan().statsTransformations())
        assertNull(plan().copy(transformations = emptyList()).statsTransformations())
    }

    @Test
    fun `play method labels cover every method and delivery`() {
        assertEquals("Direct play", playMethodLabel(PlayMethod.DIRECT, null))
        assertEquals("Remux", playMethodLabel(PlayMethod.REMUX, null))
        assertEquals("Transcode", playMethodLabel(PlayMethod.TRANSCODE, null))
        assertEquals("Direct play", playMethodLabel(null, PlaybackDelivery.ORIGINAL_HTTP))
        assertEquals("Remux", playMethodLabel(null, PlaybackDelivery.SERVER_REMUX_HLS))
        assertEquals("Remux", playMethodLabel(null, PlaybackDelivery.SERVER_REMUX_PROGRESSIVE))
        assertEquals("Transcode", playMethodLabel(null, PlaybackDelivery.SERVER_TRANSCODE_HLS))
        assertEquals("Local normalization", playMethodLabel(null, PlaybackDelivery.CLIENT_LOCAL_NORMALIZATION))
        assertNull(playMethodLabel(null, null))
        assertEquals(
            listOf(
                "original_http",
                "server_remux_hls",
                "server_remux_progressive",
                "server_transcode_hls",
                "client_local_normalization",
            ),
            PlaybackDelivery.entries.map { it.wireName() },
        )
    }

    // --- Track / quality / buffer -------------------------------------------

    @Test
    fun `audio track label combines ordinal, language, codec, channels and title`() {
        assertEquals(
            "#2 · eng · EAC3 5.1 · Commentary",
            describeAudioTrackForStats(1, "eng", "eac3", 6, "Commentary"),
        )
        // Title that just repeats the language or codec is dropped.
        assertEquals("#1 · eng · AAC Stereo", describeAudioTrackForStats(0, "eng", "aac", 2, "ENG"))
        assertEquals("#1 · AAC Stereo", describeAudioTrackForStats(0, null, "aac", 2, "aac stereo"))
        assertEquals("fre · Mono", describeAudioTrackForStats(null, "fre", null, 1))
        assertNull(describeAudioTrackForStats(-1, " ", null, 0))
    }

    @Test
    fun `channel labels`() {
        assertNull(audioChannelsLabel(null))
        assertNull(audioChannelsLabel(0))
        assertEquals("Mono", audioChannelsLabel(1))
        assertEquals("Stereo", audioChannelsLabel(2))
        assertEquals("5.1", audioChannelsLabel(6))
        assertEquals("7.1", audioChannelsLabel(8))
        assertEquals("3ch", audioChannelsLabel(3))
    }

    @Test
    fun `quality preference labels`() {
        assertEquals("Auto", qualityPreferenceLabel("auto"))
        assertEquals("Auto", qualityPreferenceLabel("-1"))
        assertEquals("Original", qualityPreferenceLabel("ORIGINAL"))
        assertEquals("1080p", qualityPreferenceLabel(" 1080p "))
        assertNull(qualityPreferenceLabel(""))
        assertNull(qualityPreferenceLabel(null))
    }

    @Test
    fun `buffer and bitrate formatting`() {
        assertEquals("12.3 s", bufferHealthLabel(12_340))
        assertEquals("0.0 s", bufferHealthLabel(0))
        assertNull(bufferHealthLabel(-1))
        assertNull(bufferHealthLabel(null))
        assertEquals("19.4 Mbps", formatStatsBitrate(19_400_000))
        assertEquals("640 Kbps", formatStatsBitrate(640_000))
        assertEquals("900 bps", formatStatsBitrate(900))
    }

    // --- Rows ---------------------------------------------------------------

    @Test
    fun `plan rows list what applies in display order`() {
        val rows = PlaybackStatsContext(
            plan = plan(trace = listOf("audio_adaptation")),
            audioTrack = "#1 · eng · AAC Stereo",
            qualityPreference = "auto",
        ).planRows()
        assertEquals(
            listOf("Plan", "Planner reason", "Transformations", "Audio track", "Stream", "Quality"),
            rows.map { it.label },
        )
        // The plan's stream URL is used when the player URL is unknown, token stripped.
        assertEquals("HLS · /api/v2/stream/abc/master.m3u8", rows.first { it.label == "Stream" }.value)
    }

    @Test
    fun `plan rows prefer the mounted stream url and fall back to the play method`() {
        val rows = PlaybackStatsContext(
            playMethod = PlayMethod.DIRECT,
            streamUrl = "https://srv/api/v2/files/7/stream?token=t",
        ).planRows()
        assertEquals(
            listOf(StatsRow("Plan", "Direct play"), StatsRow("Stream", "Progressive · /api/v2/files/7/stream")),
            rows,
        )
        assertTrue(PlaybackStatsContext().planRows().isEmpty())
    }
}
