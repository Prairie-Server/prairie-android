package org.siloserver.silo.common.player.cast

import android.util.Log
import java.net.URLEncoder
import org.siloserver.silo.common.player.PlaybackNetworkEvidenceProvider
import org.siloserver.silo.common.player.PlaybackSessionManager
import org.siloserver.silo.common.player.mediaItemMimeType
import org.siloserver.silo.common.player.resolvePlaybackStreamUrl
import org.siloserver.silo.model.playback.ClientCodecCapabilities
import org.siloserver.silo.model.playback.ClientPlaybackContext
import org.siloserver.silo.model.playback.DETAILED_DECODE_CAPABILITIES_FEATURE
import org.siloserver.silo.model.playback.DEVICE_QUIRKS_V3_FEATURE
import org.siloserver.silo.model.playback.EngineCapabilityEnvelope
import org.siloserver.silo.model.playback.EngineSubtitleCapabilities
import org.siloserver.silo.model.playback.MEDIA3_ONLY_FEATURE
import org.siloserver.silo.model.playback.PLAYBACK_PLAN_V3_FEATURE
import org.siloserver.silo.model.playback.PlaybackDeviceContext
import org.siloserver.silo.model.playback.PlaybackEngineKind
import org.siloserver.silo.model.playback.PlaybackOutputContext
import org.siloserver.silo.model.playback.SEEK_REANCHOR_V3_FEATURE
import org.siloserver.silo.model.playback.VideoDecodeCapability
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.repository.PlaybackRepository

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
            playMethod = null,
        )

        val session = when (result) {
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

        val serverUrl = tokenManager.getServerUrl()
        val token = tokenManager.getAccessToken()
        val plan = session.playbackPlan

        val castUrl = signStreamUrl(
            resolvePlaybackStreamUrl(serverUrl, session.streamUrl),
            token,
        )
        // Force h264/mp4 caps means direct → mp4, otherwise HLS. Fall back to the
        // HLS mime because that is what the server returns for the cast profile in
        // the common (transcode/remux) case.
        val mimeType = mediaItemMimeType(
            session.playMethod,
            plan?.source?.container,
            plan?.delivery,
        ) ?: "application/x-mpegURL"

        val subtitles = session.subtitleUrls.orEmpty()
            .filter { it.url.isNotBlank() }
            .map { sub ->
                CastSubtitleTrack(
                    url = signStreamUrl(resolvePlaybackStreamUrl(serverUrl, sub.url), token),
                    language = sub.language,
                    label = sub.label ?: sub.language ?: "Subtitle",
                    // Activate the track the user selected on the phone.
                    selected = request.subtitleTrackIndex != null &&
                        request.subtitleTrackIndex >= 0 &&
                        sub.index == request.subtitleTrackIndex,
                )
            }

        return CastMediaSpec(
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

    private companion object {
        private const val TAG = "CastPlaybackPreparer"
        private val STREAM_TOKEN_REGEX = Regex("[?&]st=")
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
 * [org.siloserver.silo.common.player.PlaybackCapabilityDetector.detect], this
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
 * Mirrors the shape [org.siloserver.silo.common.player.PlaybackCapabilityDetector.detectPlaybackContext]
 * produces, but declares the Chromecast-oriented engine set (HLS + direct MP4 +
 * progressive remux, all enabled) instead of the phone's probed engines.
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
            PlaybackEngineKind.MEDIA3_DIRECT to EngineCapabilityEnvelope(
                enabled = true,
                supportedOnDevice = true,
                containers = listOf("mp4"),
                videoCodecs = videoCodecs,
                audioDecodeCodecs = audioCodecs,
                subtitles = plainTextSubtitles,
                features = listOf("buffer_reporting"),
            ),
            PlaybackEngineKind.MEDIA3_PROGRESSIVE_REMUX to EngineCapabilityEnvelope(
                enabled = true,
                supportedOnDevice = true,
                containers = listOf("mp4"),
                videoCodecs = videoCodecs,
                audioDecodeCodecs = audioCodecs,
                subtitles = plainTextSubtitles,
                features = listOf("progressive", "buffer_reporting"),
            ),
        ),
    )
}
