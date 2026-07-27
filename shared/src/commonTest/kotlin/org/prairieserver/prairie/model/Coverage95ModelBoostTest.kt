package org.prairieserver.prairie.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.prairieserver.prairie.model.auth.AuthSession
import org.prairieserver.prairie.model.auth.LoginRequest
import org.prairieserver.prairie.model.auth.LoginResponse
import org.prairieserver.prairie.model.auth.RefreshRequest
import org.prairieserver.prairie.model.auth.RefreshResponse
import org.prairieserver.prairie.model.auth.SessionsResponse
import org.prairieserver.prairie.model.auth.SetupRequest
import org.prairieserver.prairie.model.auth.SetupStatusResponse
import org.prairieserver.prairie.model.auth.SignupRequest
import org.prairieserver.prairie.model.auth.SignupStatusResponse
import org.prairieserver.prairie.model.auth.User
import org.prairieserver.prairie.model.catalog.AudiobookGroup
import org.prairieserver.prairie.model.catalog.AudiobookGroupsResponse
import org.prairieserver.prairie.model.catalog.AudioTrack
import org.prairieserver.prairie.model.catalog.BrowseItem
import org.prairieserver.prairie.model.catalog.CatalogFiltersResponse
import org.prairieserver.prairie.model.catalog.CatalogQueryGroup
import org.prairieserver.prairie.model.catalog.CatalogQueryRule
import org.prairieserver.prairie.model.catalog.CatalogResponse
import org.prairieserver.prairie.model.catalog.EpisodeFile
import org.prairieserver.prairie.model.catalog.EpisodeListItem
import org.prairieserver.prairie.model.catalog.EpisodesResponse
import org.prairieserver.prairie.model.catalog.FileVersion
import org.prairieserver.prairie.model.catalog.ItemDetail
import org.prairieserver.prairie.model.catalog.LeafItemUserData
import org.prairieserver.prairie.model.catalog.MediaItemUserState
import org.prairieserver.prairie.model.catalog.OverlaySummary
import org.prairieserver.prairie.model.catalog.Person
import org.prairieserver.prairie.model.catalog.QueryDefinition
import org.prairieserver.prairie.model.catalog.QueryGroup
import org.prairieserver.prairie.model.catalog.QueryRule
import org.prairieserver.prairie.model.catalog.QuerySort
import org.prairieserver.prairie.model.catalog.SeasonsResponse
import org.prairieserver.prairie.model.catalog.SubtitleTrack
import org.prairieserver.prairie.model.catalog.TimeRange
import org.prairieserver.prairie.model.catalog.VideoTrack
import org.prairieserver.prairie.model.catalog.VersionChapter
import org.prairieserver.prairie.model.catalog.WatchDetail
import org.prairieserver.prairie.model.notifications.CapabilityAccountChannel
import org.prairieserver.prairie.model.notifications.CapabilityInApp
import org.prairieserver.prairie.model.notifications.CapabilityPush
import org.prairieserver.prairie.model.notifications.CapabilityWebPush
import org.prairieserver.prairie.model.notifications.CapabilityWebhooks
import org.prairieserver.prairie.model.notifications.NotificationCapability
import org.prairieserver.prairie.model.notifications.NotificationListResponse
import org.prairieserver.prairie.model.notifications.NotificationPreferences
import org.prairieserver.prairie.model.notifications.NotificationPreferencesUpdate
import org.prairieserver.prairie.model.notifications.NotificationReadPayload
import org.prairieserver.prairie.model.notifications.NotificationRealtime
import org.prairieserver.prairie.model.notifications.NotificationReasonFlags
import org.prairieserver.prairie.model.notifications.NotificationRow
import org.prairieserver.prairie.model.notifications.NotificationSyncResponse
import org.prairieserver.prairie.model.notifications.NotificationType
import org.prairieserver.prairie.model.notifications.UnreadCountResponse
import org.prairieserver.prairie.model.notifications.WsFrameEnvelope
import org.prairieserver.prairie.model.notifications.WsHello
import org.prairieserver.prairie.model.notifications.WsRejectedChannel
import org.prairieserver.prairie.model.notifications.WsSubscribe
import org.prairieserver.prairie.model.notifications.WsSubscribed
import org.prairieserver.prairie.model.notifications.WsTicketResponse
import org.prairieserver.prairie.model.playback.AudioPassthroughCapabilities
import org.prairieserver.prairie.model.playback.AudioPassthroughEntry
import org.prairieserver.prairie.model.playback.AudioValidationClaims
import org.prairieserver.prairie.model.playback.CLIENT_POST_RESUME_VIDEO_RECOVERY
import org.prairieserver.prairie.model.playback.ClientCodecCapabilities
import org.prairieserver.prairie.model.playback.ClientPlaybackContext
import org.prairieserver.prairie.model.playback.EngineCapabilityEnvelope
import org.prairieserver.prairie.model.playback.EngineSubtitleCapabilities
import org.prairieserver.prairie.model.playback.HdrCapabilities
import org.prairieserver.prairie.model.playback.PlayMethod
import org.prairieserver.prairie.model.playback.PlaybackAppliedQuirkV3
import org.prairieserver.prairie.model.playback.PlaybackDecisionOutcome
import org.prairieserver.prairie.model.playback.PlaybackDecisionResponseV3
import org.prairieserver.prairie.model.playback.PlaybackDelivery
import org.prairieserver.prairie.model.playback.PlaybackDeviceContext
import org.prairieserver.prairie.model.playback.PlaybackEngineKind
import org.prairieserver.prairie.model.playback.PlaybackExecutionPlan
import org.prairieserver.prairie.model.playback.PlaybackFailureV3
import org.prairieserver.prairie.model.playback.PlaybackFallbackCandidate
import org.prairieserver.prairie.model.playback.PlaybackHeaderRefreshMode
import org.prairieserver.prairie.model.playback.PlaybackInfo
import org.prairieserver.prairie.model.playback.PlaybackOutputContext
import org.prairieserver.prairie.model.playback.PlaybackPlanV3
import org.prairieserver.prairie.model.playback.PlaybackReplanRequestV3
import org.prairieserver.prairie.model.playback.PlaybackRouteEventV3
import org.prairieserver.prairie.model.playback.PlaybackRouteFamily
import org.prairieserver.prairie.model.playback.PlaybackSessionResponse
import org.prairieserver.prairie.model.playback.PlaybackSourceDescriptorV3
import org.prairieserver.prairie.model.playback.PlaybackSourceMetadata
import org.prairieserver.prairie.model.playback.PlaybackStartRequestV3
import org.prairieserver.prairie.model.playback.PlaybackStreamProtocol
import org.prairieserver.prairie.model.playback.PlaybackStreamRequest
import org.prairieserver.prairie.model.playback.PlaybackStreamV3
import org.prairieserver.prairie.model.playback.PlaybackSubtitleArtifactV3
import org.prairieserver.prairie.model.playback.PlaybackSubtitleDecisionV3
import org.prairieserver.prairie.model.playback.PlaybackSubtitleModeV3
import org.prairieserver.prairie.model.playback.PlaybackTerminalV3
import org.prairieserver.prairie.model.playback.PlaybackTimeline
import org.prairieserver.prairie.model.playback.PlaybackTimelineV3
import org.prairieserver.prairie.model.playback.PlaybackTransformationExecutor
import org.prairieserver.prairie.model.playback.PlaybackTransformationV3
import org.prairieserver.prairie.model.playback.PlaybackValidationClaims
import org.prairieserver.prairie.model.playback.PlaybackV3Validation
import org.prairieserver.prairie.model.playback.PlayerSubtitleInfo
import org.prairieserver.prairie.model.playback.ProgressRequest
import org.prairieserver.prairie.model.playback.RouteCapabilitySnapshot
import org.prairieserver.prairie.model.playback.RouteRequirements
import org.prairieserver.prairie.model.playback.SelectedPlaybackTracks
import org.prairieserver.prairie.model.playback.SelectedPlaybackTracksV3
import org.prairieserver.prairie.model.playback.SubtitleFidelityPreference
import org.prairieserver.prairie.model.playback.SubtitleValidationClaims
import org.prairieserver.prairie.model.playback.TranscodeStartRequest
import org.prairieserver.prairie.model.playback.TranscodeStartResponse
import org.prairieserver.prairie.model.playback.VideoDecodeCapability
import org.prairieserver.prairie.model.playback.VideoValidationClaims
import org.prairieserver.prairie.model.playback.executableMedia3ClientTransformations
import org.prairieserver.prairie.model.playback.validateForMedia3
import org.prairieserver.prairie.model.watchtogether.AddSuggestionRequest
import org.prairieserver.prairie.model.watchtogether.CreateRoomRequest
import org.prairieserver.prairie.model.watchtogether.GuestControlPolicy
import org.prairieserver.prairie.model.watchtogether.JoinRoomRequest
import org.prairieserver.prairie.model.watchtogether.MemberRole
import org.prairieserver.prairie.model.watchtogether.PromoteSuggestionRequest
import org.prairieserver.prairie.model.watchtogether.RoomPhase
import org.prairieserver.prairie.model.watchtogether.RoomPlaybackState
import org.prairieserver.prairie.model.watchtogether.RoomResponse
import org.prairieserver.prairie.model.watchtogether.RoomSelectionMode
import org.prairieserver.prairie.model.watchtogether.RoomSnapshot
import org.prairieserver.prairie.model.watchtogether.SetSelectionRequest
import org.prairieserver.prairie.model.watchtogether.Suggestion
import org.prairieserver.prairie.model.watchtogether.SuggestionsResponse
import org.prairieserver.prairie.model.watchtogether.TransportAction
import org.prairieserver.prairie.model.watchtogether.TransportCommand
import org.prairieserver.prairie.model.watchtogether.UpdatePolicyRequest
import org.prairieserver.prairie.model.watchtogether.WsAttachSession
import org.prairieserver.prairie.model.watchtogether.WsBuffering
import org.prairieserver.prairie.model.watchtogether.WsError
import org.prairieserver.prairie.model.watchtogether.WsPing
import org.prairieserver.prairie.model.watchtogether.WsPong
import org.prairieserver.prairie.model.watchtogether.WsReady
import org.prairieserver.prairie.model.watchtogether.WsRoomClosed
import org.prairieserver.prairie.model.watchtogether.WsStateReport
import org.prairieserver.prairie.model.watchtogether.WsTransportRequest
import org.prairieserver.prairie.network.HelloCapabilities
import org.prairieserver.prairie.network.HelloClient
import org.prairieserver.prairie.network.PlaybackAckEnvelope
import org.prairieserver.prairie.network.PlaybackCommandNames
import org.prairieserver.prairie.network.PlaybackHelloEnvelope
import org.prairieserver.prairie.network.PlaybackRealtimeEvent
import org.prairieserver.prairie.network.PlaybackResultEnvelope
import org.prairieserver.prairie.network.PrairieJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Coverage95ModelBoostTest {
    private val json = PrairieJson

    @Test
    fun playbackLegacyAndV3WireModelsRoundTrip() {
        val legacyPlan = PlaybackExecutionPlan(
            planId = "legacy-plan",
            protocolVersion = 2,
            delivery = PlaybackDelivery.SERVER_REMUX_HLS,
            engine = PlaybackEngineKind.MEDIA3_HLS,
            routeFamily = PlaybackRouteFamily.SERVER_ADAPTIVE,
            stream = PlaybackStreamRequest(
                url = "/stream.m3u8",
                streamType = "hls",
                playMethod = PlayMethod.REMUX,
            ),
            timeline = PlaybackTimeline(
                playerStartSeconds = 12.5,
                streamOriginSeconds = 4.0,
                timelineOffsetSeconds = 8.5,
                canSeekAnywhere = false,
                sourceStartSeconds = 2.0,
                seekWindowStartSeconds = 1.0,
                seekWindowEndSeconds = 99.0,
                seekRestoration = "source_position",
            ),
            selectedTracks = SelectedPlaybackTracks(audioIndex = 1, subtitleIndex = 2),
            source = PlaybackSourceMetadata(
                mediaFileId = 44,
                container = "mkv",
                videoCodec = "hevc",
                audioCodec = "truehd",
                resolution = "4k",
                hdrFormat = "dolby_vision",
                dolbyVisionProfile = 7,
                subtitleCodec = "ass",
            ),
            capabilities = RouteCapabilitySnapshot(
                engineAvailable = true,
                validatedClaims = listOf("hdr10"),
                blockers = listOf("none"),
            ),
            requirements = RouteRequirements(
                requiresHdrPreservation = true,
                requiresDolbyVisionPreservation = true,
                requiresAudioPassthrough = true,
                requiresAssFidelity = true,
                requiresBitmapSubtitles = true,
            ),
            claims = PlaybackValidationClaims(
                video = VideoValidationClaims(hdr10 = true, hdr10Plus = true, hlg = true, dolbyVision = true, dolbyVisionReason = "supported"),
                audio = AudioValidationClaims(codec = "truehd", passthrough = true, atmosPreserved = true, dtsVariant = "dts-hd", reason = "layout"),
                subtitles = SubtitleValidationClaims(assStylingPreserved = true, bitmapOverlay = true, bitmapSidecar = true, reason = "sidecar"),
            ),
            transformations = listOf(PlaybackTransformationV3("container_remux")),
            appliedQuirks = listOf(PlaybackAppliedQuirkV3("q1", "rev1", "avoid", "device")),
            runtimeCorrections = listOf(CLIENT_POST_RESUME_VIDEO_RECOVERY),
            fallbacks = listOf(PlaybackFallbackCandidate(PlaybackDelivery.SERVER_TRANSCODE_HLS, PlaybackEngineKind.MEDIA3_HLS, "fallback")),
            degradationWarnings = listOf(org.prairieserver.prairie.model.playback.PlaybackDegradationWarning("warn", "message")),
            decisionTrace = listOf("selected"),
            requestedMediaFileId = 40,
            effectiveMediaFileId = 44,
        )
        val session = PlaybackSessionResponse(
            sessionId = "session-1",
            userId = 7,
            profileId = "profile",
            mediaFileId = 44,
            playMethod = PlayMethod.REMUX,
            position = 12.5,
            isPaused = true,
            streamUrl = "/stream.m3u8",
            audioTrackIndex = 1,
            durationSeconds = 120.0,
            subtitleUrls = listOf(
                PlayerSubtitleInfo(
                    index = 2,
                    language = "en",
                    codec = "ass",
                    label = "English",
                    source = "embedded",
                    forced = false,
                    url = "/sub.ass",
                    catalogLabel = "English Signs",
                    catalogSource = "catalog",
                    isDefault = true,
                ),
            ),
            playbackInfo = PlaybackInfo(
                streamType = "hls",
                transcodeAudio = true,
                videoCodec = "hevc",
                audioCodec = "truehd",
            ),
            playbackPlan = legacyPlan,
        )
        assertEquals("legacy-plan", json.decodeFromString(PlaybackSessionResponse.serializer(), json.encodeToString(PlaybackSessionResponse.serializer(), session)).playbackPlan?.planId)

        val capabilities = ClientCodecCapabilities(
            codecsVideo = listOf("hevc"),
            codecsVideoHardware = listOf("hevc"),
            codecsAudio = listOf("truehd"),
            containers = listOf("mkv"),
            maxResolution = "4k",
            hdr = true,
            hdrDetails = HdrCapabilities(hdr10 = true, hdr10Plus = true, hlg = true, dolbyVisionProfiles = listOf(7, 8)),
            audioPassthrough = AudioPassthroughCapabilities(
                passthroughCodecs = listOf("truehd"),
                spatializerEnabled = true,
                maxChannels = 8,
                entries = listOf(AudioPassthroughEntry("truehd", channelCounts = listOf(8), layouts = listOf("7.1"))),
            ),
            videoDecode = listOf(VideoDecodeCapability("hevc", decoderName = "c2.hevc", profiles = listOf("Main10"), levels = listOf(153), bitDepths = listOf(10), maxWidth = 3840, maxHeight = 2160, maxFrameRate = 60.0, maxBitrateKbps = 80000, hardware = true)),
        )
        val context = ClientPlaybackContext(
            formFactor = "tv",
            appVersion = "1",
            device = PlaybackDeviceContext(manufacturer = "Prairie", model = "Box", brand = "P", device = "device", product = "product", socManufacturer = "soc", socModel = "model", buildId = "id", buildDisplay = "display", securityPatch = "2026-01", sdkInt = 35, abis = listOf("arm64-v8a")),
            output = PlaybackOutputContext(hdrDetails = capabilities.hdrDetails, audioPassthrough = capabilities.audioPassthrough, currentSink = "hdmi", sinkType = "avr", outputRouteGeneration = 9),
            engines = mapOf(
                PlaybackEngineKind.MEDIA3_DIRECT to EngineCapabilityEnvelope(
                    enabled = true,
                    supportedOnDevice = true,
                    failureReason = null,
                    containers = listOf("mkv"),
                    videoCodecs = listOf("hevc"),
                    audioDecodeCodecs = listOf("aac"),
                    audioPassthroughCodecs = listOf("truehd"),
                    maxChannels = 8,
                    hdrDetails = capabilities.hdrDetails,
                    subtitles = EngineSubtitleCapabilities(embeddedText = true, sidecarText = true, assStyling = true, embeddedBitmap = true, sidecarBitmap = true, fontAttachments = true),
                    features = listOf("range"),
                    transformations = listOf(PlaybackTransformationV3("server_remux")),
                    authHeaderRefresh = true,
                    validatedClaims = listOf("hdr10"),
                ),
            ),
        )
        val start = PlaybackStartRequestV3(
            fileId = 44,
            profileId = "profile",
            playbackAttemptId = "attempt",
            subtitleFidelityPreference = SubtitleFidelityPreference.PRESERVE,
            startPosition = 10.0,
            audioTrackId = "a1",
            audioTrackIndex = 1,
            subtitleTrackId = "s1",
            subtitleTrackIndex = 2,
            outputRouteGeneration = 9,
            metered = true,
            bandwidthEstimateKbps = 100000,
            bandwidthCapKbps = 80000,
            capabilities = capabilities,
            clientPlaybackContext = context,
        )
        assertEquals("attempt", json.decodeFromString(PlaybackStartRequestV3.serializer(), json.encodeToString(PlaybackStartRequestV3.serializer(), start)).playbackAttemptId)

        val replan = PlaybackReplanRequestV3(
            operation = "seek_reanchor",
            playbackAttemptId = "attempt",
            replanRequestId = "replan",
            failedPlanId = "plan",
            planAttemptId = "plan-attempt",
            planAttemptKey = "key",
            attemptedPlanKeys = listOf("key"),
            attemptCount = 2,
            positionSeconds = 42.0,
            outputRouteGeneration = 9,
            metered = true,
            bandwidthEstimateKbps = 50000,
            bandwidthCapKbps = 40000,
            selectedTracks = SelectedPlaybackTracksV3(),
            failure = PlaybackFailureV3("decoder", "failed", "c2.hevc"),
            capabilities = capabilities,
            clientPlaybackContext = context,
        )
        val event = PlaybackRouteEventV3(
            playbackAttemptId = "attempt",
            sessionId = "session",
            planId = "plan",
            planAttemptId = "plan-attempt",
            planAttemptKey = "key",
            event = "fallback",
            failureClassification = "decoder",
            fallbackReason = "replan",
            appliedQuirkIds = listOf("q1"),
            quirkRegistryRevision = "rev1",
            outputRouteGeneration = 9,
            diagnostics = mapOf("decoder" to "c2.hevc"),
        )
        assertEquals("decoder", json.decodeFromString(PlaybackReplanRequestV3.serializer(), json.encodeToString(PlaybackReplanRequestV3.serializer(), replan)).failure.classification)
        assertEquals("fallback", json.decodeFromString(PlaybackRouteEventV3.serializer(), json.encodeToString(PlaybackRouteEventV3.serializer(), event)).event)

        assertEquals("session-1", ProgressRequest(1.0, isPaused = false).copy(position = 2.0).let { session.sessionId })
        assertEquals("manifest.m3u8", TranscodeStartResponse("s", "ok", switchedFileId = 45, manifestUrl = "manifest.m3u8", durationSeconds = 120.0, playerStartSeconds = 1.0, streamOriginSeconds = 2.0, timelineOffsetSeconds = 3.0, canSeekAnywhere = true).manifestUrl)
        assertEquals(4000, TranscodeStartRequest("s", 10.0, "1080p", "h264", "aac", 4000, 6, 1, 2, subtitleBurnIn = false).targetBitrateKbps)
    }

    @Test
    fun playbackV3ValidationCoversTerminalAndReplanEdges() {
        val plan = PlaybackPlanV3(
            planId = "plan",
            sessionId = "session",
            delivery = PlaybackDelivery.ORIGINAL_HTTP,
            engine = PlaybackEngineKind.MEDIA3_DIRECT,
            stream = PlaybackStreamV3("/stream", PlaybackStreamProtocol.HTTP_PROGRESSIVE, container = "mkv"),
            decisionReason = "direct",
            timeline = PlaybackTimelineV3(sourceStartSeconds = 1.0, streamOriginSeconds = 1.0, playerStartSeconds = 2.0, timelineOffsetSeconds = 1.0, seekWindowStartSeconds = 0.0, seekWindowEndSeconds = 100.0, canSeekAnywhere = true, seekRestoration = "player_position"),
            selectedTracks = SelectedPlaybackTracksV3(
                audio = org.prairieserver.prairie.model.playback.PlaybackTrackIdentityV3("a1", 1),
                subtitle = org.prairieserver.prairie.model.playback.PlaybackTrackIdentityV3("s1", 2),
            ),
            subtitle = PlaybackSubtitleDecisionV3(
                mode = PlaybackSubtitleModeV3.RENDER,
                trackId = "s1",
                artifact = PlaybackSubtitleArtifactV3("/sub.vtt", "text/vtt", "vtt", 1.0),
            ),
            source = PlaybackSourceDescriptorV3(mediaFileId = 44, durationSeconds = 120.0),
        )
        fun response(copy: PlaybackPlanV3? = plan) = PlaybackDecisionResponseV3(
            protocolVersion = 3,
            serverFeatures = listOf("playback_plan_v3"),
            outcome = PlaybackDecisionOutcome.PLAYABLE,
            playbackPlan = copy,
        )

        assertEquals("invalid_terminal_response", assertIs<PlaybackV3Validation.Terminal>(
            PlaybackDecisionResponseV3(protocolVersion = 3, serverFeatures = listOf("playback_plan_v3"), outcome = PlaybackDecisionOutcome.ADAPTATION_UNAVAILABLE).validateForMedia3(),
        ).reason)
        assertEquals("invalid_playback_outcome", assertIs<PlaybackV3Validation.Terminal>(
            PlaybackDecisionResponseV3(protocolVersion = 3, serverFeatures = listOf("playback_plan_v3")).validateForMedia3(),
        ).reason)
        assertEquals("invalid_playback_plan", assertIs<PlaybackV3Validation.Terminal>(response(null).validateForMedia3()).reason)
        assertEquals("invalid_playback_plan", assertIs<PlaybackV3Validation.Terminal>(response(plan.copy(sessionId = null)).validateForMedia3()).reason)
        assertEquals("invalid_playback_plan", assertIs<PlaybackV3Validation.Terminal>(response(plan.copy(protocolVersion = 2)).validateForMedia3()).reason)
        assertEquals("unsupported_legacy_engine", assertIs<PlaybackV3Validation.ReplanRequired>(response(plan.copy(engine = PlaybackEngineKind.EXTERNAL_PLAYER)).validateForMedia3()).reason)
        assertEquals("invalid_playback_plan", assertIs<PlaybackV3Validation.Terminal>(response(plan.copy(stream = plan.stream.copy(url = " "))).validateForMedia3()).reason)
        assertEquals("unsupported_client_runtime_correction:future_fix", assertIs<PlaybackV3Validation.ReplanRequired>(response(plan.copy(runtimeCorrections = listOf("future_fix"))).validateForMedia3()).reason)
        assertEquals("invalid_original_server_transformation", assertIs<PlaybackV3Validation.ReplanRequired>(response(plan.copy(transformations = listOf(PlaybackTransformationV3("server_transform", PlaybackTransformationExecutor.SERVER)))).validateForMedia3()).reason)
        assertEquals(listOf("client_dv7_to_hdr10"), plan.copy(transformations = listOf(PlaybackTransformationV3("client_dv7_to_hdr10", PlaybackTransformationExecutor.CLIENT))).executableMedia3ClientTransformations())
        assertEquals(PlaybackHeaderRefreshMode.SESSION, plan.stream.headerRefresh)
        assertEquals(PlaybackTerminalV3("blocked", "No route", retryable = true).retryable, true)
    }

    @Test
    fun catalogAuthNotificationRealtimeModelsRoundTrip() {
        val overlay = OverlaySummary("4k", "HDR10", "TrueHD", "7.1", "hevc", "mkv", "2.39", "theatrical", "IMAX", multiAudio = true, multiSub = true)
        val file = FileVersion(
            fileId = 44,
            fileName = "movie.mkv",
            filePath = "/media/movie.mkv",
            resolution = "4k",
            codecVideo = "hevc",
            codecAudio = "truehd",
            hdr = true,
            container = "mkv",
            fileSize = 1024L,
            duration = 120.0,
            bitrate = 80000,
            addedAt = "2026-01-01T00:00:00Z",
            videoTracks = listOf(VideoTrack(0, "hevc", "dvhe", 8, "4k", 3840, 2160, 23.976, 80000, true, "dolby_vision", "main10", "153", 10, "bt2020", "bt2020", "pq", "Main", "und")),
            audioTracks = listOf(AudioTrack(0, "truehd", 8, "7.1", 4000, 48000, "en", "Main", isDefault = true)),
            effectiveAudioTrackIndex = 0,
            subtitleTracks = listOf(SubtitleTrack(2, "ass", "en", "Signs", forced = true, isDefault = true, external = true, externalPath = "/sub.ass")),
            chapters = listOf(VersionChapter(1, "Intro", 0.0, 60.0, "embedded", "/chapter.jpg", "thumb")),
            presentationKind = "audiobook_part",
            presentationGroupKey = "book",
            presentationPartIndex = 1,
            presentationPartTotal = 3,
        )
        val item = ItemDetail(
            contentId = "movie-1",
            type = "movie",
            status = "released",
            title = "Movie",
            sortTitle = "Movie",
            originalTitle = "Original",
            originalLanguage = "en",
            showStatus = "continuing",
            year = 2026,
            overview = "Overview",
            pendingTranslationLanguage = "es",
            tagline = "Tag",
            runtime = 120,
            contentRating = "PG",
            genres = listOf("Drama"),
            ratingImdb = 7.5,
            ratingTmdb = 8.0,
            ratingRtCritic = 90,
            ratingRtAudience = 95,
            imdbId = "tt1",
            tmdbId = "1",
            tvdbId = "2",
            cast = listOf(org.prairieserver.prairie.model.catalog.CastMember("Actor", "Lead", 1, "p1", "t1", "v1", "i1", "plex", "/p.jpg", "thumb")),
            crew = listOf(org.prairieserver.prairie.model.catalog.CrewMember("Director", "Director", "p2", "t2", "v2", "i2", "plex2", "/d.jpg", "thumb2")),
            studios = listOf("Studio"),
            networks = listOf("Network"),
            countries = listOf("US"),
            lockedFields = listOf(1),
            releaseDate = "2026-01-01",
            firstAirDate = "2026-01-01",
            lastAirDate = "2026-02-01",
            posterUrl = "/poster.jpg",
            posterThumbhash = "p",
            backdropUrl = "/backdrop.jpg",
            backdropThumbhash = "b",
            logoUrl = "/logo.png",
            seasonCount = 1,
            seriesId = "series",
            seriesTitle = "Series",
            seasonNumber = 1,
            episodeNumber = 2,
            episodeCount = 10,
            airDate = "2026-01-02",
            isSpecials = false,
            userData = LeafItemUserData(true, true, 10.0, 120.0, 44, "4k", true, "hevc"),
            userRating = 5,
            versions = listOf(file),
            subtitles = listOf(org.prairieserver.prairie.model.catalog.SubtitleInfo("embedded", "en", "ass", forced = true, "Signs")),
            overlaySummary = overlay,
            intro = TimeRange(1.0, 2.0),
            credits = TimeRange(100.0, 110.0),
        )
        val browse = BrowseItem(
            contentId = "movie-1",
            type = "movie",
            title = "Movie",
            year = 2026,
            genres = listOf("Drama"),
            contentRating = "PG",
            status = "released",
            ratingImdb = 7.5,
            ratingTmdb = 8.0,
            ratingRtCritic = 90,
            ratingRtAudience = 95,
            runtime = 120,
            originalLanguage = "en",
            studios = listOf("Studio"),
            networks = listOf("Network"),
            showStatus = "continuing",
            overview = "Overview",
            posterUrl = "/poster.jpg",
            posterThumbhash = "p",
            backdropUrl = "/backdrop.jpg",
            backdropThumbhash = "b",
            addedAt = "2026-01-01",
            releaseDate = "2026-01-01",
            lastAirDate = "2026-02-01",
            userState = MediaItemUserState(played = true, isFavorite = true, inWatchlist = true),
            overlaySummary = overlay,
        )
        assertEquals("Movie", json.decodeFromString(ItemDetail.serializer(), json.encodeToString(ItemDetail.serializer(), item)).title)
        assertEquals("movie-1", json.decodeFromString(WatchDetail.serializer(), json.encodeToString(WatchDetail.serializer(), WatchDetail("movie-1", "movie", "Movie", 2026, "Overview", listOf(file), item.subtitles, TimeRange(1.0, 2.0), TimeRange(100.0, 110.0), item.userData, "series", "Series", 1, 2, "en", "auto", true, "/poster.jpg", "p", "/backdrop.jpg", "b", "/logo.png"))).contentId)
        assertEquals(1, json.decodeFromString(CatalogResponse.serializer(), json.encodeToString(CatalogResponse.serializer(), CatalogResponse(1, true, false, listOf(browse), "library", "Movies", "snap"))).total)
        assertEquals(2, json.decodeFromString(AudiobookGroupsResponse.serializer(), json.encodeToString(AudiobookGroupsResponse.serializer(), AudiobookGroupsResponse(1, true, false, listOf(AudiobookGroup("Author", 2, 3600L, 1, 1, listOf("/p.jpg")))))).groups.first().itemCount)
        assertEquals("Drama", json.decodeFromString(CatalogFiltersResponse.serializer(), json.encodeToString(CatalogFiltersResponse.serializer(), CatalogFiltersResponse(genres = listOf("Drama"), studios = listOf("Studio"), networks = listOf("Network"), countries = listOf("US"), contentRatings = listOf("PG"), resolutions = listOf("4k"), audioLanguages = listOf("en"), subtitleLanguages = listOf("en"), originalLanguages = listOf("en"), authors = listOf("Author"), narrators = listOf("Narrator"), series = listOf("Series")))).genres.first())
        assertEquals(1, json.decodeFromString(SeasonsResponse.serializer(), """{"seasons":[{"content_id":"s1","season_number":1}]}""").seasons.size)
        assertEquals(1, json.decodeFromString(EpisodesResponse.serializer(), json.encodeToString(EpisodesResponse.serializer(), EpisodesResponse(listOf(EpisodeListItem("e1", 1, 2, "Episode", "Overview", "2026-01-01", 45, "tt", "tmdb", "tvdb", "/still.jpg", "thumb", item.userData, listOf(EpisodeFile(44, "4k", "hevc", true, 8, "mkv", 1024L))))))).episodes.size)
        assertEquals("Ada", json.decodeFromString(Person.serializer(), json.encodeToString(Person.serializer(), Person(1, "Ada", "Bio", "1900", "2000", "Town", "https://example.test", "/photo.jpg", "thumb", "tmdb", "imdb", "tvdb", "plex"))).name)
        assertEquals("title", QuerySort("title").field)
        assertEquals("genre", CatalogQueryGroup("all", listOf(CatalogQueryRule("genre", "is", "Drama", listOf("Drama")))).rules.first().field)
        assertEquals("asc", json.decodeFromString(QueryDefinition.serializer(), json.encodeToString(QueryDefinition.serializer(), QueryDefinition(listOf(QueryGroup("and", listOf(QueryRule("year", "gte", JsonPrimitive(2020))))), QuerySort("title")))).sort?.order)

        val user = User(7, "ada", "ada@example.test", "admin", downloadAllowed = true)
        assertEquals("access", json.decodeFromString(LoginResponse.serializer(), json.encodeToString(LoginResponse.serializer(), LoginResponse("access", "refresh", 3600L, user))).accessToken)
        assertEquals("ada", json.decodeFromString(LoginRequest.serializer(), json.encodeToString(LoginRequest.serializer(), LoginRequest("ada", "pw", "local"))).username)
        assertEquals("refresh", json.decodeFromString(RefreshRequest.serializer(), json.encodeToString(RefreshRequest.serializer(), RefreshRequest("refresh"))).refreshToken)
        assertEquals(3600L, json.decodeFromString(RefreshResponse.serializer(), json.encodeToString(RefreshResponse.serializer(), RefreshResponse("access", "refresh", 3600L))).expiresIn)
        assertEquals("ada", json.decodeFromString(SetupRequest.serializer(), json.encodeToString(SetupRequest.serializer(), SetupRequest("ada", "ada@example.test", "pw"))).username)
        assertFalse(json.decodeFromString(SetupStatusResponse.serializer(), json.encodeToString(SetupStatusResponse.serializer(), SetupStatusResponse(false))).needsSetup)
        assertEquals("invite", json.decodeFromString(SignupRequest.serializer(), json.encodeToString(SignupRequest.serializer(), SignupRequest("ada", "ada@example.test", "pw", "invite"))).inviteCode)
        assertTrue(json.decodeFromString(SignupStatusResponse.serializer(), json.encodeToString(SignupStatusResponse.serializer(), SignupStatusResponse(true))).enabled)
        assertEquals("device", json.decodeFromString(SessionsResponse.serializer(), json.encodeToString(SessionsResponse.serializer(), SessionsResponse(listOf(AuthSession("s1", "device", "127.0.0.1", "now", "later", null))))).sessions.first().deviceName)

        val flags = buildJsonObject {
            put("favorite", true)
            put("watchlist", true)
            put("continue_watching", true)
            put("next_up", true)
        }
        val row = NotificationRow("n1", NotificationType.EpisodeAvailableWire, "profile", 1, "series", "episode", "Series", "Episode", 1, 2, "/poster", "/poster.jpg", "thumb", flags, "now", "later")
        assertTrue(row.reasonFlagsTyped.any)
        assertTrue(row.isRead)
        assertEquals(NotificationType.Unknown, NotificationType.fromWire("future.type"))
        assertEquals(NotificationRealtime.EventCreated, "notification.created")
        assertEquals(1, json.decodeFromString(NotificationListResponse.serializer(), json.encodeToString(NotificationListResponse.serializer(), NotificationListResponse(listOf(row), "cursor"))).notifications.size)
        assertEquals(2, json.decodeFromString(NotificationSyncResponse.serializer(), json.encodeToString(NotificationSyncResponse.serializer(), NotificationSyncResponse(listOf(row), "cursor", unreadCount = 2))).unreadCount)
        assertEquals(2, UnreadCountResponse(2).count)
        assertEquals("ticket", WsTicketResponse("ticket", 10).ticket)
        assertTrue(NotificationReasonFlags.from(JsonObject(emptyMap())).any.not())
        assertEquals("profile", NotificationPreferences("profile", enabled = true, notifyFavorites = true, notifyWatchlist = true, notifyContinueWatching = true, notifyNextUp = true).profileId)
        assertEquals(false, NotificationPreferencesUpdate(enabled = false, notifyFavorites = false, notifyWatchlist = false, notifyContinueWatching = false, notifyNextUp = false).enabled)
        assertTrue(NotificationCapability(CapabilityInApp(true), CapabilityPush(true, "fcm", listOf("immediate")), CapabilityPush(true, "web", listOf("digest")), CapabilityWebPush(true, "key"), CapabilityWebhooks(true, 3, listOf("episode.available")), CapabilityAccountChannel(true, listOf("digest"), 9), CapabilityAccountChannel(true, listOf("immediate"), 10)).inApp.enabled)
        assertEquals("hello", WsHello(schemaVersion = 1, connectionId = "c", availableChannels = listOf("notifications"), requiredAction = "subscribe").type)
        assertEquals("subscribe", WsSubscribe(requestId = "r", channels = listOf("notifications")).type)
        assertEquals("subscribed", WsSubscribed(requestId = "r", channels = listOf("notifications"), rejected = listOf(WsRejectedChannel("bad", "forbidden", "no"))).type)
        assertEquals("profile", NotificationReadPayload("profile", "n1", all = false).profileId)
        assertEquals("notifications", WsFrameEnvelope("event", "notifications", "notification.created", "e1", "now", JsonPrimitive("payload")).channel)
    }

    @Test
    fun watchTogetherAndPlaybackRealtimeModelsRoundTrip() {
        assertEquals(RoomPhase.Lobby, RoomPhase.fromWire("lobby"))
        assertEquals(RoomPlaybackState.Waiting, RoomPlaybackState.fromWire("waiting"))
        assertEquals(RoomSelectionMode.Vote, RoomSelectionMode.fromWire("vote"))
        assertEquals(GuestControlPolicy.GuestPlayPause, GuestControlPolicy.fromWire("guest_play_pause"))
        assertEquals(MemberRole.Host, MemberRole.fromWire("host"))
        assertEquals(TransportAction.Seek, TransportAction.fromWire("seek"))

        val room = RoomSnapshot(
            roomId = "room",
            phase = RoomPhase.Playing,
            playbackState = RoomPlaybackState.Playing,
            selectionMode = RoomSelectionMode.Vote,
            selectionRevision = 2,
            selectedContentId = "content",
            selectedFileId = 44,
            selectedLibraryId = 7,
            code = "ABC123",
            guestControlPolicy = GuestControlPolicy.GuestPlayPause,
            isPaused = false,
            anchorPositionSeconds = 10.0,
            anchorUpdatedAt = "now",
            generation = 3,
            memberCount = 2,
            hostConnected = true,
            selfRole = MemberRole.Host,
            selfCanControlTransport = true,
            selfCanManageRoom = true,
            selfIgnoreWait = true,
            attachedSessionId = "session",
            invitePath = "/join/ABC123",
        )
        val suggestion = Suggestion("s1", "room", 1, "profile", "content", "movie", "Movie", "2026", "/poster.jpg", "note", 2, true, "now")
        val command = TransportCommand("cmd", "session", 2, TransportAction.Seek, 12.5, "later", "now", RoomPlaybackState.Playing)
        assertEquals("room", json.decodeFromString(RoomResponse.serializer(), json.encodeToString(RoomResponse.serializer(), RoomResponse(room, "token"))).room.roomId)
        assertEquals("s1", json.decodeFromString(SuggestionsResponse.serializer(), json.encodeToString(SuggestionsResponse.serializer(), SuggestionsResponse(listOf(suggestion)))).suggestions.first().id)
        assertEquals("cmd", json.decodeFromString(TransportCommand.serializer(), json.encodeToString(TransportCommand.serializer(), command)).commandId)
        assertEquals("host_pick", json.decodeFromString(CreateRoomRequest.serializer(), json.encodeToString(CreateRoomRequest.serializer(), CreateRoomRequest("host_pick"))).selectionMode)
        assertEquals("token", json.decodeFromString(JoinRoomRequest.serializer(), json.encodeToString(JoinRoomRequest.serializer(), JoinRoomRequest("ABC123", "token"))).joinToken)
        assertEquals("content", SetSelectionRequest("content", 44, 7).contentId)
        assertEquals("host_only", UpdatePolicyRequest("host_only").guestControlPolicy)
        assertEquals("content", AddSuggestionRequest("content", "movie", "Movie", "2026", "/poster.jpg", "note").contentId)
        assertEquals("s1", PromoteSuggestionRequest("s1").suggestionId)
        assertEquals("attach_session", WsAttachSession(sessionId = "session").type)
        assertEquals("transport_request", WsTransportRequest(action = "pause", positionSeconds = 12.0, isPaused = true).type)
        assertEquals("state_report", WsStateReport(sessionId = "session", positionSeconds = 12.0, isPaused = false).type)
        assertEquals("ready", WsReady(sessionId = "session", positionSeconds = 12.0, isPaused = false).type)
        assertEquals("buffering", WsBuffering(sessionId = "session", positionSeconds = 12.0, isPaused = true).type)
        assertEquals("ping", WsPing(clientSentAt = "now").type)
        assertEquals("pong", WsPong(clientSentAt = "now", serverReceivedAt = "then", serverSentAt = "later").type)
        assertEquals("room_closed", WsRoomClosed(reason = "ended").type)
        assertEquals("error", WsError("error", "bad", "no").type)

        assertEquals("pause", PlaybackCommandNames.Pause)
        assertTrue(PlaybackCommandNames.Supported.contains(PlaybackCommandNames.SetSubtitleTrack))
        val hello = PlaybackHelloEnvelope("hello", "session", HelloClient("prairie-android", "1"), HelloCapabilities(PlaybackCommandNames.Supported))
        assertEquals("session", json.decodeFromString(PlaybackHelloEnvelope.serializer(), json.encodeToString(PlaybackHelloEnvelope.serializer(), hello)).sessionId)
        assertEquals("accepted", PlaybackAckEnvelope(commandId = "cmd", sessionId = "session").status)
        assertEquals("completed", PlaybackResultEnvelope(commandId = "cmd", sessionId = "session", status = "completed", error = null).status)
        val event: PlaybackRealtimeEvent = PlaybackRealtimeEvent.Command("cmd", "session", "pause", JsonObject(emptyMap()))
        assertEquals("pause", (event as PlaybackRealtimeEvent.Command).name)
        assertEquals("opened", when (PlaybackRealtimeEvent.Opened) {
            PlaybackRealtimeEvent.Opened -> "opened"
            else -> "closed"
        })
        assertEquals("ended", PlaybackRealtimeEvent.ServerEvent("session", "ended", JsonObject(emptyMap())).name)
        assertEquals("bye", PlaybackRealtimeEvent.Closed("bye").reason)
    }
}
