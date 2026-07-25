package org.prairieserver.prairie.common.player.cast

import android.util.Log
import java.net.URLEncoder
import org.prairieserver.prairie.common.player.PlaybackNetworkEvidenceProvider
import org.prairieserver.prairie.common.player.PlaybackSessionManager
import org.prairieserver.prairie.common.player.mediaItemMimeType
import org.prairieserver.prairie.common.player.resolvePlaybackStreamUrl
import org.prairieserver.prairie.model.playback.ClientCodecCapabilities
import org.prairieserver.prairie.model.playback.ClientPlaybackContext
import org.prairieserver.prairie.model.playback.DETAILED_DECODE_CAPABILITIES_FEATURE
import org.prairieserver.prairie.model.playback.DEVICE_QUIRKS_V3_FEATURE
import org.prairieserver.prairie.model.playback.EngineCapabilityEnvelope
import org.prairieserver.prairie.model.playback.EngineSubtitleCapabilities
import org.prairieserver.prairie.model.playback.MEDIA3_ONLY_FEATURE
import org.prairieserver.prairie.model.playback.PLAYBACK_PLAN_V3_FEATURE
import org.prairieserver.prairie.model.playback.PlaybackDeviceContext
import org.prairieserver.prairie.model.playback.PlaybackEngineKind
import org.prairieserver.prairie.model.playback.PlayMethod
import org.prairieserver.prairie.model.playback.PlaybackOutputContext
import org.prairieserver.prairie.model.playback.PlaybackSessionResponse
import org.prairieserver.prairie.model.playback.SEEK_REANCHOR_V3_FEATURE
import org.prairieserver.prairie.model.playback.VideoDecodeCapability
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.repository.PlaybackRepository

/**
 * Tier-2 Google Cast session preparation.
 *
 * Casting to a Chromecast dongle can NOT reuse the phone's current stream: the
 * phone may be direct-playing an MKV/HEVC file the dongle cannot decode, and the
 * phone's stream URL is authenticated with an `Authorization` header that a
 * Cast receiver cannot send. So this helper opens a *separate* playback session
 * that advertises a conservative Chromecast codec profile (H.264 / AAC / MP4),
 * making the server return a cast-playable HLS or progressive MP4, then rewrites
 * the resulting URL into a self-contained `?st=`-signed absolute URL the dongle
 * can fetch on its own.
 *
 * It deliberately uses its OWN [PlaybackSessionManager] instance (never the DI
 * singleton) because that class holds a single `activeVideoAttempt` reference —
 * driving a cast session through the shared instance would clobber the phone's
 * local playback session. The cast start path ([PlaybackSessionManager.startSessionV2])
 * is stateless with respect to that reference, so a throwaway instance is safe
 * and cheap.
 */
class CastPlaybackPreparer(
    private val playbackRepository: PlaybackRepository,
    private val tokenManager: TokenManager,
    private val networkEvidenceProvider: PlaybackNetworkEvidenceProvider =
        PlaybackNetworkEvidenceProvider.None,
) {
    /**
     * Opens a cast-capability playback session and resolves a self-contained
     * cast media spec. Returns `null` when the server refuses or the network
     * fails — the caller should surface a "couldn't cast" notice.
     */
    suspend fun prepareCastMedia(request: CastPrepareRequest): CastMediaSpec? {
        val capabilities = chromecastCodecCapabilities()
        val context = chromecastPlaybackContext(request.appVersion)

        // Separate manager instance so the phone's activeVideoAttempt is untouched.
        val castSession = PlaybackSessionManager(
            playbackRepository = playbackRepository,
            tokenManager = tokenManager,
            networkEvidenceProvider = networkEvidenceProvider,
        )

        val result = castSession.startSessionV2(
            fileId = request.fileId,
            profileId = request.profileId,
            capabilities = capabilities,
            audioTrackIndex = request.audioTrackIndex,
            subtitleTrackIndex = request.subtitleTrackIndex,
            qualityPreference = "auto",
            startPosition = request.startPositionSeconds,
            clientPlaybackContext = context,
            preserveDirectAudioSelection = false,
            // A Cast receiver can seek direct play (HTTP Range on the real
            // file) and an encoded HLS transcode (full VOD manifest), but
            // never the progressive remux pipe. The flag makes the server
            // upgrade a would-be remux to a transcode session; direct-playable
            // files stay direct at original quality for free.
            seekableStreamsOnly = true,
        )

        val startedSession = when (result) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> {
                Log.w(TAG, "Cast session start failed: ${result.code} ${result.message}")
                return null
            }
            is ApiResult.NetworkError -> {
                Log.w(TAG, "Cast session start network error: ${result.exception}")
                return null
            }
        }

        // From here on a server session exists. If the preparing coroutine is
        // cancelled before the spec is handed to the Cast SDK, stop the
        // session — otherwise it lingers, counts against the account's
        // concurrent-stream cap, and every later cast start 429s.
        try {
            return buildCastMediaSpec(request, startedSession, castSession)
        } catch (e: kotlinx.coroutines.CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                castSession.stopSession(startedSession.sessionId)
            }
            throw e
        }
    }

    private suspend fun buildCastMediaSpec(
        request: CastPrepareRequest,
        startedSession: PlaybackSessionResponse,
        castSession: PlaybackSessionManager,
    ): CastMediaSpec? {
        // A transcode start response only carries a placeholder manifest URL:
        // the manifest goes live (and gains its full-recipe ?st= token) when
        // the transcode is actually started. The phone drives that here — the
        // receiver only ever sees the final signed manifest URL.
        val session = if (startedSession.playMethod == PlayMethod.TRANSCODE) {
            val seekSeconds = startedSession.position
                .takeIf { it.isFinite() && it >= 0.0 }
                ?: request.startPositionSeconds
            val transcodeResult = castSession.startTranscodeFallback(
                session = startedSession,
                seekSeconds = seekSeconds,
                resolution = CAST_TRANSCODE_RESOLUTION,
                mode = PlaybackSessionManager.TranscodeMode.FULL,
                audioTrackIndex = request.audioTrackIndex,
                // Cast subtitles ride as WebVTT text tracks, never burn-in.
                subtitleTrackIndex = null,
            )
            when (transcodeResult) {
                is ApiResult.Success -> transcodeResult.data
                is ApiResult.Error -> {
                    Log.w(TAG, "Cast transcode start failed: ${transcodeResult.code} ${transcodeResult.message}")
                    castSession.stopSession(startedSession.sessionId)
                    return null
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "Cast transcode start network error: ${transcodeResult.exception}")
                    castSession.stopSession(startedSession.sessionId)
                    return null
                }
            }
        } else {
            startedSession
        }

        val serverUrl = tokenManager.getServerUrl()
        val token = tokenManager.getAccessToken()
        val plan = session.playbackPlan

        val castUrl = signStreamUrl(
            resolvePlaybackStreamUrl(serverUrl, session.streamUrl),
            token,
        )
        // The receiver must know whether the URL is an HLS manifest or a
        // progressive stream. The legacy plan often fails tolerant parsing when
        // the server answers with a V3-shaped plan, so a null plan is the NORMAL
        // case here — and guessing "HLS" from playMethod alone told the receiver
        // to parse a progressive fMP4 remux stream as an m3u8 playlist. Trust the
        // URL shape instead: only the transcode manifest is HLS.
        val mimeType = if (plan?.delivery != null) {
            mediaItemMimeType(session.playMethod, plan.source?.container, plan.delivery)
                ?: castFallbackMimeType(session.streamUrl)
        } else {
            castFallbackMimeType(session.streamUrl)
        }

        // Subtitle URLs are signed with the STREAM token from the cast URL, not
        // the account access token: the server stamps a session-scoped ?st= on
        // the stream URL only, and the subtitle route rejects anything else
        // (observed: all subtitle fetches 401'd with the access token).
        val streamToken = STREAM_TOKEN_VALUE_REGEX.find(castUrl)?.groupValues?.get(1)
        val usableSubs = session.subtitleUrls.orEmpty().filter { it.url.isNotBlank() }
        val labels = castSubtitleLabels(usableSubs)
        val subtitles = usableSubs.mapIndexed { subIndex, sub ->
            val base = forceVttFormat(resolvePlaybackStreamUrl(serverUrl, sub.url))
            CastSubtitleTrack(
                url = if (streamToken != null && !STREAM_TOKEN_REGEX.containsMatchIn(base)) {
                    val sep = if (base.contains('?')) '&' else '?'
                    "$base${sep}st=$streamToken"
                } else {
                    signStreamUrl(base, token)
                },
                language = sub.language,
                label = labels[subIndex],
                // Activate the track the user selected on the phone.
                selected = request.subtitleTrackIndex != null &&
                    request.subtitleTrackIndex >= 0 &&
                    sub.index == request.subtitleTrackIndex,
            )
        }

        return CastMediaSpec(
            fileId = request.fileId,
            streamUrl = castUrl,
            mimeType = mimeType,
            title = request.title,
            posterUrl = request.posterUrl,
            positionSeconds = session.position.takeIf { it.isFinite() && it >= 0.0 }
                ?: request.startPositionSeconds,
            durationSeconds = session.durationSeconds ?: 0.0,
            subtitles = subtitles,
        )
    }

    /**
     * Makes a stream URL self-contained for a Cast receiver by carrying the
     * session/access token in a `?st=` query parameter (the receiver can't send
     * the `Authorization` header the phone normally relies on).
     *
     * If the server already stamped an `st=` token onto the URL (transcode
     * plans do this today), it is used as-is. The server is being updated
     * separately to accept `?st=` as auth on stream routes.
     */
    private fun signStreamUrl(url: String, token: String?): String {
        if (token.isNullOrBlank()) return url
        if (STREAM_TOKEN_REGEX.containsMatchIn(url)) return url
        val separator = if (url.contains('?')) '&' else '?'
        val encoded = URLEncoder.encode(token, "UTF-8")
        return "$url${separator}st=$encoded"
    }

    /**
     * Human labels for the CC picker and the receiver's track metadata. The
     * server's label field is a fallback chain that bottoms out at the sidecar
     * FILENAME (release-group junk) or the codec name ("SUBRIP"), so it is
     * ignored: the language code is resolved to its display name ("da" →
     * "Danish"), forced tracks are marked, and same-language duplicates get a
     * counter ("English 2") so every menu row is distinct.
     */
    private fun castSubtitleLabels(
        subs: List<org.prairieserver.prairie.model.playback.PlayerSubtitleInfo>,
    ): List<String> {
        val bases = subs.map { sub ->
            val code = sub.language?.trim()?.takeIf { it.isNotBlank() }
            val display = code?.let { c ->
                val locale = java.util.Locale.forLanguageTag(c.replace('_', '-').lowercase())
                locale.displayLanguage
                    .takeIf { it.isNotBlank() && !it.equals(locale.language, ignoreCase = true) }
            }
            val base = display
                ?: code?.replaceFirstChar { it.uppercase() }
                ?: "Subtitle"
            if (sub.forced == true) "$base (Forced)" else base
        }
        val totals = bases.groupingBy { it }.eachCount()
        val seen = mutableMapOf<String, Int>()
        return bases.map { base ->
            val n = (seen[base] ?: 0) + 1
            seen[base] = n
            if ((totals[base] ?: 0) > 1) "$base $n" else base
        }
    }

    /**
     * The Cast Default Media Receiver renders only WebVTT text tracks. The
     * server defaults subtitle extraction to VTT but honors an explicit
     * `format=` (the phone requests `ass` for libass rendering), so any
     * format param is rewritten to `vtt` for the receiver.
     */
    private fun forceVttFormat(url: String): String =
        if (FORMAT_PARAM_REGEX.containsMatchIn(url)) {
            url.replace(FORMAT_PARAM_REGEX) { "${it.groupValues[1]}format=vtt" }
        } else {
            val separator = if (url.contains('?')) '&' else '?'
            "$url${separator}format=vtt"
        }

    private companion object {
        private const val TAG = "CastPlaybackPreparer"

        // Matches the conservative Chromecast codec profile: 1080p is the
        // highest rung every generation of the hardware decodes.
        private const val CAST_TRANSCODE_RESOLUTION = "1080p"
        private val STREAM_TOKEN_REGEX = Regex("[?&]st=")
        private val STREAM_TOKEN_VALUE_REGEX = Regex("[?&]st=([^&]+)")
        private val FORMAT_PARAM_REGEX = Regex("([?&])format=[^&]*")

        /**
         * Mime for a cast URL when the plan's delivery is unknown. The server's
         * HLS delivery is always a `.m3u8` manifest URL; every other stream URL
         * is progressive, and under the cast capability profile (mp4-only
         * containers, progressive remux outputs fragmented MP4) that is always
         * `video/mp4`.
         */
        fun castFallbackMimeType(streamUrl: String): String =
            if (streamUrl.substringBefore('?').contains(".m3u8")) {
                "application/x-mpegURL"
            } else {
                "video/mp4"
            }
    }
}

/** Inputs captured from the live player state when the user starts casting. */
data class CastPrepareRequest(
    val fileId: Int,
    val profileId: String,
    val startPositionSeconds: Double,
    val audioTrackIndex: Int?,
    val subtitleTrackIndex: Int?,
    val title: String,
    val posterUrl: String?,
    val appVersion: String,
)

/** Self-contained media descriptor handed to the Cast receiver. */
data class CastMediaSpec(
    val fileId: Int,
    val streamUrl: String,
    val mimeType: String,
    val title: String,
    val posterUrl: String?,
    val positionSeconds: Double,
    val durationSeconds: Double,
    val subtitles: List<CastSubtitleTrack>,
)

data class CastSubtitleTrack(
    val url: String,
    val language: String?,
    val label: String,
    val selected: Boolean,
)

/**
 * A static, conservative codec profile every Chromecast can decode. Unlike
 * [org.prairieserver.prairie.common.player.PlaybackCapabilityDetector.detect], this
 * NEVER probes the phone — the phone's decoders are irrelevant to what the
 * dongle can play.
 */
fun chromecastCodecCapabilities(): ClientCodecCapabilities = ClientCodecCapabilities(
    codecsVideo = listOf("h264"),
    codecsVideoHardware = listOf("h264"),
    codecsAudio = listOf("aac", "mp3"),
    containers = listOf("mp4"),
    maxResolution = "1080p",
    hdr = false,
    hdrDetails = null,
    audioPassthrough = null,
    videoDecode = listOf(
        VideoDecodeCapability(
            codec = "h264",
            profiles = listOf("baseline", "main", "high"),
            // Decimal-encoded H.264 levels (10 = 1.0 … 42 = 4.2), matching
            // MediaCodecCapabilitiesProbe.normalizedLevel so the server reads
            // them identically. Capped at 4.2 (1080p / ~62 Mbps).
            levels = listOf(10, 11, 12, 13, 20, 21, 22, 30, 31, 32, 40, 41, 42),
            bitDepths = listOf(8),
            hardware = true,
        ),
    ),
)

/**
 * Mirrors the shape [org.prairieserver.prairie.common.player.PlaybackCapabilityDetector.detectPlaybackContext]
 * produces, but declares the Chromecast-oriented engine set (HLS + direct MP4)
 * instead of the phone's probed engines. Progressive remux is deliberately
 * absent — see the inline note.
 */
fun chromecastPlaybackContext(appVersion: String): ClientPlaybackContext {
    val videoCodecs = listOf("h264")
    val audioCodecs = listOf("aac", "mp3")
    val plainTextSubtitles = EngineSubtitleCapabilities(
        embeddedText = true,
        sidecarText = true,
    )
    return ClientPlaybackContext(
        features = listOf(
            PLAYBACK_PLAN_V3_FEATURE,
            SEEK_REANCHOR_V3_FEATURE,
            MEDIA3_ONLY_FEATURE,
            DETAILED_DECODE_CAPABILITIES_FEATURE,
            DEVICE_QUIRKS_V3_FEATURE,
        ),
        formFactor = "mobile",
        appVersion = appVersion,
        device = PlaybackDeviceContext(),
        output = PlaybackOutputContext(
            hdrDetails = null,
            audioPassthrough = null,
            currentSink = "cast_receiver",
            sinkType = "cast",
        ),
        engines = mapOf(
            PlaybackEngineKind.MEDIA3_HLS to EngineCapabilityEnvelope(
                enabled = true,
                supportedOnDevice = true,
                containers = listOf("m3u8", "hls"),
                videoCodecs = videoCodecs,
                audioDecodeCodecs = audioCodecs,
                subtitles = plainTextSubtitles,
                features = listOf("hls", "buffer_reporting"),
            ),
            // HLS is deliberately the ONLY engine. Any progressive delivery
            // (direct or remux) reaches the receiver as a chunked/range-less
            // stream it cannot seek — the slider and ±30s skips restarted
            // playback from zero, and the receiver reported a live, growing
            // duration. Advertising MEDIA3_DIRECT (mp4) was enough for the
            // planner to keep choosing progressive remux, so it goes too: with
            // HLS alone the server must serve a VOD manifest, which the
            // receiver seeks natively via segments.
        ),
    )
}
