package org.prairieserver.prairie.cast

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Wire model for the `_prairiecast._tcp` phone→TV control channel.
 *
 * BYTE-COMPATIBLE WITH prairie-apple's PrairieControlMessage
 * (iosApp/Control/PrairieControlProtocol.swift) — Apple clients ship this exact
 * schema and cannot be revved in lockstep, so every field name, casing, enum
 * string, and envelope key here mirrors the Swift Codable output: camelCase
 * payload fields, `{type, v, <kind>}` envelope with the payload nested under
 * the kind key, payloadless ping/pong, an optional `close` payload, and Int64
 * track ids. Every field added after the first release is optional and sent
 * only to peers that list the matching [PrairieCastFeature]. Change this file
 * only together with prairie-apple.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class PrairieCastMessage {
    abstract val v: Int

    @Serializable
    @SerialName("hello")
    data class Hello(
        val hello: PrairieCastHello,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = PrairieCastProtocol.version,
    ) : PrairieCastMessage()

    @Serializable
    @SerialName("handoff_offer")
    data class HandoffOffer(
        val handoffOffer: PrairieCastHandoffOffer,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = PrairieCastProtocol.version,
    ) : PrairieCastMessage()

    @Serializable
    @SerialName("handoff_challenge")
    data class HandoffChallenge(
        val handoffChallenge: PrairieCastHandoffChallenge,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = PrairieCastProtocol.version,
    ) : PrairieCastMessage()

    @Serializable
    @SerialName("handoff_ready")
    data class HandoffReady(
        val handoffReady: PrairieCastHandoffReady,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = PrairieCastProtocol.version,
    ) : PrairieCastMessage()

    @Serializable
    @SerialName("handoff_cancel")
    data class HandoffCancel(
        val handoffCancel: PrairieCastHandoffCancel,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = PrairieCastProtocol.version,
    ) : PrairieCastMessage()

    @Serializable
    @SerialName("launch")
    data class Launch(
        val launch: PrairieCastLaunchRequest,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = PrairieCastProtocol.version,
    ) : PrairieCastMessage()

    @Serializable
    @SerialName("control")
    data class Control(
        val control: PrairieCastControlCommand,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = PrairieCastProtocol.version,
    ) : PrairieCastMessage()

    @Serializable
    @SerialName("state")
    data class State(
        val state: PrairieCastPlaybackState,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = PrairieCastProtocol.version,
    ) : PrairieCastMessage()

    @Serializable
    @SerialName("error")
    data class Error(
        val error: PrairieCastError,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = PrairieCastProtocol.version,
    ) : PrairieCastMessage()

    /** A controller's `passive` flag after hello. Sent only to receivers that list [PrairieCastFeature.Presence]. */
    @Serializable
    @SerialName("presence")
    data class Presence(
        val presence: PrairieCastPresence,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = PrairieCastProtocol.version,
    ) : PrairieCastMessage()

    @Serializable
    @SerialName("ping")
    data class Ping(
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = PrairieCastProtocol.version,
    ) : PrairieCastMessage()

    @Serializable
    @SerialName("pong")
    data class Pong(
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = PrairieCastProtocol.version,
    ) : PrairieCastMessage()

    /**
     * Ends the session. [close] carries `{"reason": ...}` ([PrairieCastCloseReason])
     * only for peers that list [PrairieCastFeature.CloseReason]; older peers get a
     * bare close. Kept as raw JSON so a malformed payload reads as no reason, as
     * on Apple, instead of failing the frame.
     */
    @Serializable
    @SerialName("close")
    data class Close(
        val close: JsonElement? = null,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = PrairieCastProtocol.version,
    ) : PrairieCastMessage() {
        val reason: String?
            get() = ((close as? JsonObject)?.get("reason") as? JsonPrimitive)
                ?.takeIf { it.isString }?.contentOrNull

        companion object {
            fun withReason(reason: String?): Close =
                Close(close = reason?.let { buildJsonObject { put("reason", it) } })
        }
    }

    companion object {
        /** Every `type` this build understands, read from the serializer so it can't drift. */
        val knownTypes: Set<String> by lazy {
            serializer().descriptor.getElementDescriptor(1).elementNames.toSet()
        }

        /**
         * Decodes one frame, or returns null for a `type` this build doesn't
         * know. A newer peer may send kinds added after this build; dropping
         * them keeps the session up where a decode error would tear it down
         * (Apple ignores them the same way). A known type with a malformed
         * payload still throws.
         */
        fun decodeOrNull(json: Json, text: String): PrairieCastMessage? {
            val frame = json.parseToJsonElement(text) as? JsonObject
                ?: throw SerializationException("PrairieCast frame is not a JSON object")
            val type = (frame["type"] as? JsonPrimitive)?.contentOrNull
            if (type != null && type !in knownTypes) return null
            return json.decodeFromJsonElement(serializer(), frame)
        }
    }
}

@Serializable
enum class PrairieCastPeerRole {
    @SerialName("phone")
    Phone,

    @SerialName("tv")
    Tv,
}

/**
 * Session handshake. Both peers send one immediately after connecting; the TV
 * authorizes the controller only when [serverId] matches its own active
 * server — that check (plus same-LAN discovery) is the trust anchor, the
 * fixed TLS-PSK is confidentiality only.
 */
@Serializable
data class PrairieCastHello(
    val role: PrairieCastPeerRole,
    val deviceName: String,
    val deviceId: String,
    val serverId: String? = null,
    val serverName: String? = null,
    val supportedVersions: List<Int>,
    /** The deployment identity behind [serverId], when known, so peers on different addresses of one server match. */
    val serverIdentity: String? = null,
    /** [PrairieCastFeature] names this peer supports. Absent from older peers, which support none. */
    val features: List<String>? = null,
    /** A controller that only watches state (silent auto-resume, an integration); it never puts up the TV's standby screen. */
    val passive: Boolean? = null,
    /** [PrairieCastClientKind]. `role` stays `phone` for integrations because shipped receivers reject other roles. */
    val clientKind: String? = null,
) {
    fun supports(feature: String): Boolean = features?.contains(feature) == true
}

@Serializable
data class PrairieCastPlaybackRequest(
    val contentId: String,
    val fileId: Int? = null,
    val audioTrackIndex: Int? = null,
    val subtitleTrackIndex: Int? = null,
    val startFromBeginning: Boolean,
    val resumePosition: Double? = null,
    val libraryId: Int? = null,
    /**
     * True when the viewer picked [fileId]'s version on the phone, so the TV
     * pins it (`allow_alternate_versions: false`). Omitted otherwise; older
     * peers ignore it, as Apple's do.
     */
    val pinsVersion: Boolean? = null,
)

@Serializable
data class PrairieCastLaunchRequest(
    val serverId: String,
    val playback: PrairieCastPlaybackRequest,
)

@Serializable
data class PrairieCastHandoffOffer(
    val requestId: String,
    val serverId: String,
    val serverURL: String,
    val serverName: String? = null,
    val profileId: String,
    val profileName: String? = null,
    /** Display-only: what the phone is about to launch. Older peers omit and ignore it. */
    val title: String? = null,
    /** The deployment identity the phone verified at [serverURL]. */
    val serverIdentity: String? = null,
    /**
     * Other addresses the deployment offers, from its connections document,
     * public first, then providers. The TV may use one that reports
     * [serverIdentity]; reachability is the TV's to test.
     */
    val serverEndpoints: List<PrairieCastServerEndpoint>? = null,
    /** The token from an earlier `handoff_ready`, to reuse that identity from a new connection without another approval. */
    val resumeToken: String? = null,
    /** The user agreed to replace a title another profile started, after the TV answered `replace_confirmation_required`. */
    val replaceConfirmed: Boolean? = null,
    /** With [replaceConfirmed]: the title the user agreed to replace. The TV honors it only while that title is on screen. */
    val replacingContentId: String? = null,
)

/**
 * One address in [PrairieCastHandoffOffer.serverEndpoints]. Mirrors prairie-apple
 * `ServerEndpoint`: [kind] is `public` or `provider`, kept as a string so a
 * kind this build doesn't know still decodes.
 */
@Serializable
data class PrairieCastServerEndpoint(
    val url: String,
    val kind: String,
    /** Provider slug for a `provider` endpoint, e.g. `tailscale`. */
    val provider: String? = null,
    /** Provider display name from its manifest. */
    val displayName: String? = null,
)

@Serializable
data class PrairieCastHandoffChallenge(
    val requestId: String,
    val userCode: String,
    val matchCode: String,
    val expiresAt: String,
)

@Serializable
data class PrairieCastHandoffReady(
    val requestId: String,
    val serverId: String,
    val profileId: String,
    val sessionExpiresAt: String,
    val reused: Boolean,
    /** Proof for reusing this identity from a later connection, sent only to controllers that list [PrairieCastFeature.ResumeToken]. */
    val resumeToken: String? = null,
)

@Serializable
data class PrairieCastHandoffCancel(
    val requestId: String,
    val reason: String,
    val message: String? = null,
    /** With reason `replace_confirmation_required`: the title the handoff would replace, so the controller can ask. */
    val nowPlaying: PrairieCastNowPlayingSummary? = null,
)

/** What a controller needs to ask "Replace what Alex is watching?". */
@Serializable
data class PrairieCastNowPlayingSummary(
    val contentId: String? = null,
    val title: String,
    val subtitle: String? = null,
    val profileName: String? = null,
    val controllerName: String? = null,
)

@Serializable
data class PrairieCastPresence(
    val passive: Boolean,
)

@Serializable
data class PrairieCastTrack(
    val kind: String,
    val trackId: Long,
    val title: String,
    val detail: String? = null,
)

@Serializable
data class PrairieCastQualityOption(
    val id: String,
    val label: String,
    val detail: String? = null,
)

@Serializable
data class PrairieCastPlaybackState(
    val contentId: String? = null,
    val sessionId: String? = null,
    val title: String,
    val subtitle: String? = null,
    val isPlaying: Boolean,
    val isLoading: Boolean,
    val isBuffering: Boolean,
    val currentTime: Double,
    val duration: Double,
    val audioTracks: List<PrairieCastTrack>,
    val subtitleTracks: List<PrairieCastTrack>,
    val selectedAudioTrackId: Long? = null,
    val selectedSubtitleTrackId: Long? = null,
    val qualityOptions: List<PrairieCastQualityOption>,
    val activeQualityId: String,
    val isQualitySwitching: Boolean,
    val playbackSpeed: Double,
    val videoGravity: String,
    val hdrEnabled: Boolean,
    val supportsVideoGravity: Boolean,
    val supportsHDRToggle: Boolean,
    val subtitleSyncMs: Int? = null,
    val subtitlePosition: String? = null,
    val supportsSubtitleDelay: Boolean? = null,
    val supportsSubtitlePosition: Boolean? = null,
    val volume: Double,
    val isMuted: Boolean,
    val hasNextEpisode: Boolean,
    val nextEpisodeTitle: String? = null,
    val error: String? = null,
    /**
     * Absolute, signed poster URL for the playing title (the series poster for
     * an episode), so an integration can fetch artwork without its own Prairie
     * sign-in. The TV re-signs it before it expires. Omitted when unknown;
     * older peers ignore it, so it needs no feature flag.
     */
    val posterUrl: String? = null,
    /** Synopsis of the playing title. Omitted when unknown. */
    val overview: String? = null,
)

/**
 * Command envelope: a [name] from Apple's closed Name enum plus the sparse
 * argument fields. Current Apple builds ignore a name they don't know, but
 * older ones reject the whole frame, so never send a string outside this set.
 */
@Serializable
data class PrairieCastControlCommand(
    val name: String,
    val seconds: Double? = null,
    val trackId: Long? = null,
    val speed: Double? = null,
    val volume: Double? = null,
    val value: String? = null,
    val enabled: Boolean? = null,
    val milliseconds: Int? = null,
) {
    companion object {
        const val Play = "play"
        const val Pause = "pause"
        const val PlayPause = "play_pause"
        const val Seek = "seek"
        const val Stop = "stop"
        const val SelectAudioTrack = "select_audio_track"
        const val SelectSubtitleTrack = "select_subtitle_track"
        const val SetPlaybackSpeed = "set_playback_speed"
        const val SetQuality = "set_quality"
        const val SetVideoGravity = "set_video_gravity"
        const val SetSubtitleSyncMs = "set_subtitle_sync_ms"
        const val SetSubtitlePosition = "set_subtitle_position"
        const val SetVolume = "set_volume"
        const val SetMuted = "set_muted"
        const val PlayNext = "play_next"

        /** Every command name this build implements. */
        val knownNames: Set<String> = setOf(
            Play, Pause, PlayPause, Seek, Stop, SelectAudioTrack, SelectSubtitleTrack, SetPlaybackSpeed,
            SetQuality, SetVideoGravity, SetSubtitleSyncMs, SetSubtitlePosition, SetVolume, SetMuted, PlayNext,
        )

        fun play(): PrairieCastControlCommand = PrairieCastControlCommand(name = Play)

        fun pause(): PrairieCastControlCommand = PrairieCastControlCommand(name = Pause)

        fun playPause(): PrairieCastControlCommand = PrairieCastControlCommand(name = PlayPause)

        fun stop(): PrairieCastControlCommand = PrairieCastControlCommand(name = Stop)

        fun seek(seconds: Double): PrairieCastControlCommand =
            PrairieCastControlCommand(name = Seek, seconds = seconds)

        fun selectAudioTrack(trackId: Long): PrairieCastControlCommand =
            PrairieCastControlCommand(name = SelectAudioTrack, trackId = trackId)

        fun selectSubtitleTrack(trackId: Long?): PrairieCastControlCommand =
            PrairieCastControlCommand(name = SelectSubtitleTrack, trackId = trackId)

        fun setPlaybackSpeed(speed: Double): PrairieCastControlCommand =
            PrairieCastControlCommand(name = SetPlaybackSpeed, speed = speed)

        fun setQuality(qualityId: String): PrairieCastControlCommand =
            PrairieCastControlCommand(name = SetQuality, value = qualityId)

        fun setVideoGravity(value: String): PrairieCastControlCommand =
            PrairieCastControlCommand(name = SetVideoGravity, value = value)

        fun setSubtitleSyncMs(milliseconds: Int): PrairieCastControlCommand =
            PrairieCastControlCommand(name = SetSubtitleSyncMs, milliseconds = milliseconds)

        fun setSubtitlePosition(value: String): PrairieCastControlCommand =
            PrairieCastControlCommand(name = SetSubtitlePosition, value = value)

        fun setVolume(volume: Double): PrairieCastControlCommand =
            PrairieCastControlCommand(name = SetVolume, volume = volume)

        fun setMuted(muted: Boolean): PrairieCastControlCommand =
            PrairieCastControlCommand(name = SetMuted, enabled = muted)

        fun playNext(): PrairieCastControlCommand = PrairieCastControlCommand(name = PlayNext)
    }
}

@Serializable
data class PrairieCastError(
    val code: String,
    val message: String,
)
