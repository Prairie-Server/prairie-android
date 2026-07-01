Repo snapshot date: 2026-04-17

# Current Implementation Review

## 1. Build configuration

- **Media3 version pinned:** `1.10.0` at `gradle/libs.versions.toml:10`.
- **Module declarations** (`gradle/libs.versions.toml:55-59`):
  - `media3-exoplayer` (core)
  - `media3-exoplayer-hls`
  - `media3-datasource-okhttp`
  - `media3-ui`
  - `media3-session`
- **Modules included per buildscript:**
  - `androidApp/build.gradle.kts:35-39` — all five catalog entries (`exoplayer`, `exoplayer-hls`, `datasource-okhttp`, `ui`, `session`).
  - `androidTvApp/build.gradle.kts:35-38` — `exoplayer`, `exoplayer-hls`, `datasource-okhttp`, `ui`. **No `media3-session`.**
  - `android-shared/build.gradle.kts:31-33` — `exoplayer`, `exoplayer-hls`, `datasource-okhttp`. No `ui`, no `session`.
  - `shared/build.gradle.kts` — no Media3 modules (shared is now Android-only networking).
- **Modules conspicuously absent from every buildscript:**
  - `androidx.media3:media3-common` (pulled transitively, never explicit)
  - `androidx.media3:media3-extractor` (not pulled at all; needed for an overridable progressive MKV extractor path)
  - `androidx.media3:media3-container` (same — no MkvExtractor override knob)
  - `androidx.media3:media3-exoplayer-smoothstreaming`, `-dash`, `-rtsp`, `-midi` (not used — not a problem, noting for completeness)
  - `androidx.media3:media3-decoder-ffmpeg` (no FFmpeg audio decoder extension; TrueHD/DTS/DTS-HD software decoding is therefore unavailable — device decoders are the only path)
  - `androidx.media3:media3-exoplayer-leanback` deprecated/rolled in; no TV-specific Media3 module in use
  - `androidx.media3:media3-exoplayer-workmanager` — none
- **Kotlin target:** 2.1.20, JVM 21, compileSdk 36, minSdk 24, targetSdk 35.

## 2. Player construction

- **ExoPlayer instances are built in two places:**
  - `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SiloPlayerFactory.kt:73-77` — `ExoPlayer.Builder(context, renderersFactory).setTrackSelector(trackSelector).setAudioAttributes(audioAttributes, handleAudioFocus = true).setHandleAudioBecomingNoisy(true).build()`. Used by both the phone player (`androidApp/.../PlayerScreen.kt:66`) and the TV player (`androidTvApp/.../TvPlayerScreen.kt:81`).
  - `androidApp/src/androidMain/kotlin/org/siloserver/silo/android/service/PlaybackService.kt:36-39` — a second, separate `ExoPlayer.Builder(this)...build()` inside a `MediaSessionService`. This second player is **never connected** to the UI player; the service player has no RenderersFactory, no TrackSelector, and is wired to a `MediaSession` that the `PlayerScreen` / `TvPlayerScreen` do not reference.
- **Collaborators passed to `ExoPlayer.Builder` (factory path):**
  - `RenderersFactory`: custom `DefaultRenderersFactory` (`SiloPlayerFactory.kt:41-46`) with `extensionRendererMode = EXTENSION_RENDERER_MODE_ON` (or `_PREFER` if `preferFfmpegAudio=true`; `PREFER` is never actually set because no FFmpeg extension is on the classpath — see section 1) and `enableDecoderFallback(true)`.
  - `TrackSelector`: `DefaultTrackSelector` (see section 4).
  - `DataSource.Factory`: **not passed to the player builder**; attached per-MediaSource instead (see section 3).
  - `LoadControl`: **default** — no custom `DefaultLoadControl` override. No tuning for HDR/4K buffering, no `setBufferDurationsMs`, no `setPrioritizeTimeOverSizeThresholds`.
  - `AudioAttributes`: `USAGE_MEDIA` + `AUDIO_CONTENT_TYPE_MOVIE`, audio focus handled.
  - `setHandleAudioBecomingNoisy(true)` is set.
  - **Not set:** `setSeekBackIncrementMs`, `setSeekForwardIncrementMs`, `setWakeMode(C.WAKE_MODE_NETWORK)`, `setPauseAtEndOfMediaItems`, `setDeviceVolumeControlEnabled`, `setSuppressPlaybackOnUnsuitableOutput`.
- **Default vs custom behavior summary:**
  - Custom: renderer factory, track selector, audio attributes, becoming-noisy handling.
  - Default: load control, bandwidth meter, data-source factory on the player, media-source factory on the player, analytics listener, clock, looper.

## 3. Media source configuration

- **Media sources are created directly in `SiloPlayerFactory.createMediaSource` (`SiloPlayerFactory.kt:96-122`) — there is no `DefaultMediaSourceFactory`, no `MediaSource.Factory` on the player.** Each source is constructed per-play-method and passed to `exoPlayer.setMediaSource(...)`:
  - `PlayMethod.DIRECT`, `PlayMethod.REMUX` → `ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)` (`:115-116`).
  - `PlayMethod.TRANSCODE` → `HlsMediaSource.Factory(dataSourceFactory).setAllowChunklessPreparation(true).createMediaSource(mediaItem)` (`:118-120`).
- **Implication for MKV:** `ProgressiveMediaSource` uses `DefaultExtractorsFactory` internally. There is no way to configure `MkvExtractor` flags (e.g. no exposure of `ExtractorsFactory.setMkvExtractorFlags(...)`; there is no subclass / custom factory here). Whatever `DefaultExtractorsFactory` does out-of-the-box for MKV today is what is shipping.
- **Content types currently handled:**
  - Direct-play containers: whatever `DefaultExtractorsFactory` supports — MP4/MKV/MOV/WebM/etc. No allow/deny list.
  - HLS via the HLS module for remux and transcode output.
  - No DASH, no SmoothStreaming, no RTSP, no progressive ProgressiveDownload-from-cache.
- **HTTP / OkHttp integration (`AuthenticatedDataSourceFactory.kt`):**
  - Each call to `createDataSource()` builds an `OkHttpDataSource.Factory(okHttpClient).setDefaultRequestProperties({"Authorization": "Bearer $token"}).createDataSource()`, wrapped in a `RelativeUrlDataSource` that prepends `serverUrl` when the URI has no scheme.
  - The `OkHttpClient` is lazy but **one per factory instance**. A fresh `AuthenticatedDataSourceFactory` is constructed on every `createMediaSource` call (`SiloPlayerFactory.kt:103`), so the underlying OkHttp client is re-created per playback session. No connection pool reuse across sessions.
  - Timeouts, follow-redirects defaults only; no custom connect/read timeouts, no HTTP/2 tuning, no dispatcher tuning.
  - No cache / `CacheDataSource` (no on-disk caching of HLS segments or byte-range reads).
- **Auth header injection:** every segment/byte-range request gets `Authorization: Bearer <token>` via `setDefaultRequestProperties`. Token is a snapshot at session start; if it expires mid-stream the wrapper will not refresh it (there is no 401 interceptor here — unlike the Ktor pipeline in `shared/`).
- **Subtitle side-loading:** `MediaItem.Builder.setSubtitleConfigurations(subtitleManager.buildSubtitleConfigurations(...))` (`SiloPlayerFactory.kt:108-111`, `SubtitleManager.kt:27-44`). Mime-type mapping in `SubtitleManager.kt:87-97` handles SRT, SSA/ASS, WebVTT, TTML, PGS, DVD sub. Falls back to SRT.
- **MediaItem:** only `setUri` + `setSubtitleConfigurations` are set. No `setMimeType`, no `setClippingConfiguration`, no `setDrmConfiguration`, no `setMediaId`, no `setMediaMetadata`.

## 4. Track selection

- `DefaultTrackSelector` is configured at `SiloPlayerFactory.kt:56-66`:
  - `setTunnelingEnabled(isTv)` — enabled on TV, disabled on phones.
  - `setAudioOffloadPreferences(...)` — `AUDIO_OFFLOAD_MODE_ENABLED` on phones, `AUDIO_OFFLOAD_MODE_DISABLED` on TV (explicit comment: offload + tunneling conflict on some chipsets).
  - `setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)` — paired with `AudioCapabilitiesReceiver` so tracks re-select on HDMI hot-plug / AVR power cycle / ENCODED_SURROUND_OUTPUT toggle.
- **Not set on `TrackSelectionParameters`:**
  - `setPreferredVideoMimeTypes(...)` / `setPreferredAudioMimeType(...)` / `setPreferredAudioLanguage(...)` / `setPreferredTextLanguage(...)`
  - `setMaxVideoBitrate(...)` / `setMaxVideoSize(...)` / `setViewportSize(...)` / `setMinVideoBitrate(...)`
  - `setPreferredVideoRoleFlags(...)` / `setPreferredAudioRoleFlags(...)` — no DV-profile or Atmos preference plumbed in
  - `setAllowVideoMixedMimeTypeAdaptiveness(...)` / `setAllowAudioMixedMimeTypeAdaptiveness(...)`
  - `setPreferredAudioMimeTypes(...)` with AC-3 / E-AC-3 / E-AC-3 JOC / DTS / TrueHD prioritization
  - `setDisabledTrackTypes(...)` / `setTrackTypeDisabled(TRACK_TYPE_VIDEO, ...)` for audio-only playback
  - `setForceHighestSupportedBitrate(...)` / `setExceedVideoConstraintsIfNecessary(...)`
  - `setForceLowestBitrate(...)`, `setConstrainAudioChannelCountToDeviceCapabilities(...)`
- **User-facing selectors → `TrackSelectionParameters`:**
  - Audio track selection (`PlayerViewModel.onSelectAudio`, `PlayerViewModel.kt:403-433`) is handled **server-side** — the app calls `playbackSessionManager.changeAudio(...)` and swaps the stream URL. There is no client-side audio-track override through `TrackSelectionOverride` from the phone player. `AudioTrackManager.selectAudioTrack` (`AudioTrackManager.kt:25-43`) does exist but is only used by the TV player (`TvPlayerScreen.kt:278`).
  - Subtitles (`SubtitleManager.selectSubtitle`, `SubtitleManager.kt:52-85`) sets `setTrackTypeDisabled(TRACK_TYPE_TEXT, true)` for "off", or iterates text groups and builds a `TrackSelectionOverride` for the selected group.
  - Quality/version (`PlayerViewModel.onSelectVersion`, `PlayerViewModel.kt:450-506`) triggers a **new playback session** against a different `fileId` — this tears down and rebuilds, not a Media3-level adaptation override. There is no adaptive-bitrate UI (HLS variants are left to the selector's defaults).

## 5. HDR / Dolby Vision handling

- **Capability detection** (`PlaybackCapabilityDetector`, `MediaCodecCapabilitiesProbe`, `DisplayHdrProbe`): the app enumerates decoder HDR profiles (HDR10, HDR10+, HLG, DV profiles 5/7/8, with a multi-instance gate on P7), intersects them with panel-reported `Display.HdrCapabilities.supportedHdrTypes`, and sends the result to the server in `ClientCodecCapabilities.hdrDetails` so the server can pick an HDR direct-play version. This is sophisticated and feeds the server-side resolver.
- **Client-side Media3 HDR handling is near-zero:**
  - No `TrackSelectionParameters.setPreferredVideoRoleFlags` or MIME-based HDR prioritization.
  - No `ColorInfo` plumbing.
  - No `MediaItem` HDR signalling beyond whatever the container exposes.
  - The `preferFfmpegAudio` flag on `createPlayer()` is the only renderer-mode knob, and nothing currently passes `true`.
- **TV-only `HdrDisplayController`** (`HdrDisplayController.kt:36-146`) — this is the one real HDMI-side concession: on the TV player, `onVideoSizeChanged` reads `player.videoFormat?.frameRate` and drives `Window.attributes.preferredDisplayModeId` to a `Display.Mode` that matches content resolution + frame rate (23.976 / 24 / 25 / 29.97 / 30 / 50 / 59.94 / 60), with integer-multiple preference for judder reduction. Restored on disposal. **Phone player does not do this.**
- No `PlayerView.setVideoColorSpace`-style configuration; the `PlayerView` is stock.

## 6. Audio / Atmos handling

- **Passthrough plumbing is present on the *capability* side** via `AudioCapabilityManager` (`AudioCapabilityManager.kt:33-112`): registers a Media3 `AudioCapabilitiesReceiver`, maps supported encodings (`AC3`, `E_AC3`, `E_AC3_JOC` for Atmos-over-EAC3, `DTS`, `DTS_HD`, `DOLBY_TRUEHD`, `AC4`) into `passthroughCodecs`, checks `AudioManager.spatializer.isEnabled` on API 32+, reports `maxChannels`. Exposed as a `StateFlow<AudioPassthroughCapabilities>` and bundled into `ClientCodecCapabilities.audioPassthrough` for the server. The `setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)` on the track selector pairs with this.
- **Offload** is enabled on phones via `setAudioOffloadPreferences(AUDIO_OFFLOAD_MODE_ENABLED)` and disabled on TV (`SiloPlayerFactory.kt:50-54, :59-63`). Nothing tighter — no `setIsGaplessSupportRequired`, no offload-speed-change toggles.
- **Tunneling** is enabled on TV via `setTunnelingEnabled(isTv)` — the standard audio/video sync mechanism for TV, but notable that no renderer mode enforcement or tunneling-mode-per-track logic is in place.
- **Spatializer**: `AudioManager.spatializer.isEnabled` is **read** (API 32+) and sent to the server, but the app never calls `Spatializer.addOnSpatializerStateChangedListener`, never sets `Spatializer.setAvailabilityListener`, and does not constrain channel count based on spatializer head-tracked availability.
- **No explicit Atmos/E-AC-3 JOC path in the Media3 graph.** If the stream is E-AC-3 JOC and the sink supports it, this depends on Media3's `DefaultAudioSink` / `AudioCapabilities` doing the right thing passively — no `TrackSelectionParameters` hint is set to prefer JOC or TrueHD. Server-side, the passthrough capability list would bias version selection; client-side there is nothing helping.
- **No FFmpeg extension** on the classpath, so if the device lacks a hardware TrueHD/DTS-HD decoder AND passthrough is unavailable, the audio track is unplayable — there is no software fallback for these codecs.
- **BecomingNoisy, audio focus**: set, standard.

## 7. Android TV application

- **TV app uses the same `SiloPlayerFactory`** (`androidTvModule.kt:45`) — constructed with `isTv = true`, which flips tunneling on and audio offload off.
- **Separate UI:** `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerScreen.kt` — does not share any Compose code with the phone `PlayerScreen`. Uses `androidx.tv.material3` for focusable controls, D-pad-driven menu flows (`TvTrackMenus.kt`), and owns the Media3 `Player.Listener` inline.
- **Tunneling:** enabled via `setTunnelingEnabled(isTv = true)` in the factory.
- **Refresh-rate / display-mode switching:** yes, via `HdrDisplayController` attached on composition (`TvPlayerScreen.kt:86-89`), driven by `onVideoSizeChanged` + `player.videoFormat?.frameRate` (`TvPlayerScreen.kt:121-131`).
- **Leanback extension usage:** none. The app declares `<uses-feature android:name="android.software.leanback" android:required="true" />` and uses `LEANBACK_LAUNCHER` (`androidTvApp/src/androidMain/AndroidManifest.xml:5-7, :33`), but the old `media3-exoplayer-leanback` (now rolled into core) is not explicitly required and no Leanback player UI is in use — all transport controls are custom Compose.
- **MediaSession on TV:** none. `media3-session` is not in the TV buildscript, there is no `PlaybackService` counterpart, and no lock-screen / Now Playing / Google Assistant wiring. The TV app only has the phone-only `PlaybackService` registered in the phone manifest — the TV `MainTvActivity` has no service at all.
- **AndroidManifest entries (TV):** `INTERNET`, `ACCESS_NETWORK_STATE`. No `WAKE_LOCK`, no `FOREGROUND_SERVICE`, no `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
- **AndroidManifest entries (phone):** adds `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, marks `MainActivity` `supportsPictureInPicture="true"` with `configChanges="orientation|screenSize|screenLayout|keyboardHidden"`. Still no `WAKE_LOCK`.
- **TV `BackHandler` + D-pad key handling** is in-screen (`TvPlayerScreen.kt:192-206`) with a root `FocusRequester` to keep keys flowing while controls are hidden.

## 8. Observed gaps vs. MKV + HDR/DV + Atmos target

| Feature | State | Why it matters |
|---|---|---|
| `DefaultExtractorsFactory` MKV flags (subtitle extraction, chapters, seek behavior) never configured | Absent | Direct-play MKV is what the REMUX-to-HLS shim avoids today; shipping a properly tuned `MkvExtractor` is the whole point of native MKV direct play. Without explicit flags, behavior tracks whatever the default is in 1.10.0 — no way to tune for PGS sub extraction, chapter markers, or seek performance. |
| `media3-decoder-ffmpeg` extension | Absent (no library in classpath) | TrueHD, DTS-HD MA software fallback is impossible. On devices without AVR passthrough, any lossless codec forces transcode. |
| Client-side `TrackSelectionParameters` for HDR / Atmos / DV preference | Absent | The server picks an HDR/Atmos version, but when a stream has multiple audio groups the client has no declared preference. Atmos-carrying E-AC-3 JOC track may lose to a 2-channel AAC track with a higher selection score. |
| `TrackSelectionParameters.setPreferredAudioLanguage` / `setPreferredTextLanguage` | Absent | Profile / user-pref language is not plumbed into the selector. |
| `MediaSource.Factory` on the ExoPlayer.Builder (single-source factory) | Absent — per-call `ProgressiveMediaSource.Factory` / `HlsMediaSource.Factory` | Blocks features that rely on a uniform media-source pipeline (cache, trace id, custom retry logic, DRM later). |
| `CacheDataSource` / on-disk HLS segment cache | Absent | Rewind and scrub across HLS segments re-fetches; no offline prep. |
| Centralized `OkHttpClient` with connection pool | Partial (one per `AuthenticatedDataSourceFactory`, re-created per session) | Cold TLS handshakes per play; segment throughput sensitive to this on 4K HDR streams. |
| OkHttp 401 refresh / token rotation for media requests | Absent | Tokens are snapshotted at session start. Long-running HDR streams that outlive a JWT lifetime will 401 on the next segment and the stream will fail with no refresh attempt. |
| `LoadControl` tuning (`setBufferDurationsMs`, `setPrioritizeTimeOverSizeThresholds`) | Default | 4K HDR bitrates stress the default buffer sizes; no explicit tuning. |
| `AudioCapabilities.getMaxSupportedChannelCountForPassthrough` correct implementation | Partial — `AudioCapabilityManager.kt:110-111` falls back to `maxChannelCount` and explicitly ignores `encoding` | Max channel-count per encoding may be under- or over-reported for edge encodings. |
| `Spatializer.OnSpatializerStateChangedListener` registration | Absent (only initial read) | Plugging/unplugging head-tracked headphones mid-stream will not re-select tracks. |
| `setSuppressPlaybackOnUnsuitableOutput` | Absent | Auto-mute when a non-media-capable route is connected is up to the platform. |
| Single, shared ExoPlayer between `PlayerScreen` and `PlaybackService` | Absent — two independent players | Media notification / lock screen / background continue does not follow the foreground player. `PlaybackService` is effectively dead code. |
| MediaSession integration on Android TV | Absent | No Now Playing card, no Google Assistant control, no system transport integration. |
| HDR display-mode switching on phones | Absent (`HdrDisplayController` TV-only) | Phones with HDR10 panels (Pixel 6+, Samsung S22+, etc.) don't get refresh-rate matching either. |
| `PlayerView.setVideoColorSpace` / HDR tone-map control | Absent | Defaults apply — no hook to disable tone-mapping on SDR panels intentionally. |
| `setPreferredVideoRoleFlags(ROLE_FLAG_MAIN)` + DV profile selection via `setPreferredVideoMimeTypes(VIDEO_DOLBY_VISION)` | Absent | When an MKV contains both a DV track and an HDR10 track, there's no client-side bias. |
| `setAllowVideoMixedMimeTypeAdaptiveness` / `setAllowMultipleAdaptiveSelections` | Default | HLS adaptation across DV + HDR10 variants relies on defaults. |
| `PlayerView.setKeepContentOnPlayerReset`, `setUseController(false)` | `useController = false` set; `keepContentOnPlayerReset` default | Minor visual polish on version switches. |
| `WAKE_LOCK` permission + `setWakeMode(WAKE_MODE_NETWORK)` | Absent | Long-session HDR playback on phones can briefly release wake on net stalls. |
| FFprobe-like inspection of track `Format` for Dolby Vision profile / HDR transfer / channel layout | Minimal — TV reads `player.videoFormat?.frameRate`, nothing else | UI cannot show "Dolby Vision Profile 5" / "Dolby Atmos" badge based on the active track, only based on `FileVersion` metadata returned from the server. |

**Ranked by how much each blocks HDR/DV/Atmos MKV playback (highest blocker first):**

1. **No FFmpeg decoder extension** — forces transcode for lossless audio codecs on any device without hardware / passthrough.
2. **No `DefaultExtractorsFactory` / `MkvExtractor` tuning** — MKV direct play is flying blind.
3. **No client-side `TrackSelectionParameters` preferences for DV MIME / E-AC-3 JOC / preferred languages / max channel count** — the track selector may not pick the Atmos or DV track even when it exists.
4. **Shared `MediaSource.Factory` absent** — blocks caching, consistent retry, DRM later.
5. **Token refresh missing on media requests** — fragile for long HDR films.
6. **Two uncoordinated ExoPlayer instances** — `PlaybackService` can't drive the UI player and vice versa; MediaSession integration is effectively broken.
7. **`LoadControl` untuned** — 4K HDR bitrates may stall on default buffers.
8. **No `CacheDataSource`** — seek-back triggers full re-download of HLS segments.
9. **HDR display-mode switching not available on phones** — cosmetic for most, but blocking for Pixel-class HDR playback parity with TV.
10. **No MediaSession on TV** — functional but no system integration.

## 9. Risk notes

- **Double ExoPlayer construction.** `PlaybackService.onCreate` (`PlaybackService.kt:36-39`) builds a second `ExoPlayer` with no RenderersFactory and no TrackSelector. The UI player in `SiloPlayerFactory` and the service player never share state, never share a `MediaSession`, and the service is not connected from `PlayerScreen.kt`. This is either vestigial scaffolding or a bug waiting to happen if a `MediaController` is wired up later. **Recommendation: either delete `PlaybackService` or refactor `SiloPlayerFactory` to obtain the player from the service.**
- **`AuthenticatedDataSourceFactory` builds a new `OkHttpClient` per player session** (`SiloPlayerFactory.kt:103` constructs a new factory every `createMediaSource` call). The inner OkHttp client is `by lazy` but per-factory, so there is no connection pool reuse across plays; this is invisible on short clips and measurable on 4K HDR first-segment fetch.
- **Auth token is captured at session start** and never refreshed on the media pipeline. The app-level Ktor client has refresh logic; the player's OkHttp chain does not. A 90-minute 4K HDR movie that outlives the JWT will 401 at the next segment fetch, with no recovery.
- **`AudioCapabilities.getMaxSupportedChannelCountForPassthrough` shim in `AudioCapabilityManager.kt:110-111`** — the `@Suppress("UNUSED_PARAMETER")` on `encoding` and fallback to `maxChannelCount` means max channels are approximated rather than per-encoding. Clarification per doc 04 §4.2: Media3 1.10.0 does **not** actually expose a public instance `getMaxSupportedChannelCountForPassthrough(encoding, sampleRate)` on `AudioCapabilities` — the method with that name lives as a `public static` on the private `Api29` inner class and is unreachable from app code. So the stub is not missing a public API; it is falling back because Media3 does not give apps a reliable per-encoding max-channel accessor at this version. If per-encoding channel caps matter, the only options are (a) probe via `AudioTrack.isDirectPlaybackSupported` with an `AudioFormat` built for the encoding, or (b) decode `EXTRA_MAX_CHANNEL_COUNT` from `ACTION_HDMI_AUDIO_PLUG` and assume it applies to every encoding in `EXTRA_ENCODINGS`.
- **Spatializer state is read once, never observed.** `AudioCapabilityManager.kt:76-83` polls `spatializer.isEnabled` inside `mapCapabilities`. Switching headphones mid-play will not refresh this until an `AudioCapabilitiesReceiver` callback fires for another reason.
- **Position polling runs forever on an unguarded `while(true)` in the phone player** (`PlayerScreen.kt:150-157`): the `LaunchedEffect(exoPlayer)` coroutine is keyed on `exoPlayer` which is `remember`-stable, so it lives for the composition. `onDispose { exoPlayer.release() }` (`PlayerScreen.kt:183-188`) releases the player, but the polling loop will still call `exoPlayer.currentPosition` on a released player between dispose and cancellation. Not a crash (Media3 tolerates this), but noisy in telemetry.
- **`DisposableEffect(activity, exoPlayer)` for PiP** (`PlayerScreen.kt:175-180`) calls `pipHelper.updatePipParams(activity)` once. If `videoWidth/height` become known later, params are not re-applied. PiP aspect ratio may therefore stay 16:9 even for 2.39:1 content.
- **`HdrDisplayController.restore()` mode switch is asynchronous.** On activity disposal the mode is set back via `params.preferredDisplayModeId = originalModeId; w.attributes = params`, but the HDMI handshake is async and may not complete before the activity is gone; no teardown synchronization.
- **TV `BackHandler` plus `onPreviewKeyEvent` both intercept KEYCODE_BACK** (`TvPlayerScreen.kt:76-79, :199-206`). The `BackHandler` runs first at the Compose layer; `onPreviewKeyEvent` reached only if Compose's back dispatcher does not consume. Not observably wrong, but duplicated.
- **`media3-session` missing from the TV build** means even if MediaSession integration were added, the TV build would not compile against it.
- **`android-shared` exports `media3-exoplayer-hls` as `implementation`**; consumers (`androidApp`, `androidTvApp`) re-declare it. Fine today, brittle if a consumer forgets and starts getting `NoClassDefFoundError` at runtime for the HLS path.
- **`MediaSessionService` declared only in the phone manifest** (`androidApp/src/androidMain/AndroidManifest.xml:27-34`); the service constructs a second player that nothing in the UI layer ever bridges to.
- **No `AnalyticsListener` registered anywhere** — render/codec/decoder events are invisible. Debugging HDR / DV / codec init failures on real devices will require instrumenting by hand.

## Validation log

- corrected: §9 claim "Media3 1.10 does expose `getMaxSupportedChannelCountForPassthrough(encoding, sampleRate)` on `AudioCapabilities`" → the method with that name in 1.10.0 is a `public static` on the private `Api29` inner class and is **not** reachable from app code. The shim in `AudioCapabilityManager.kt` is therefore justified at this Media3 version, not out-of-date. (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioCapabilities.java) Doc 08 §2.3 and §10 updated to match.
- verified: `media3-session` is not declared in `androidTvApp/build.gradle.kts` lines 35-38 per the current repo state; doc 08 §1.2 addresses this gap with a new dependency line.
- verified: `ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)` and `HlsMediaSource.Factory(dataSourceFactory).setAllowChunklessPreparation(true).createMediaSource(mediaItem)` are legitimate Media3 1.10.0 factory entry points for progressive and HLS respectively.
- verified: `setTunnelingEnabled` and `setAudioOffloadPreferences` usage matches Media3 1.10.0 surface per doc 04 / doc 05 / doc 06.
- still unverified: exact line numbers cited throughout (e.g. `SiloPlayerFactory.kt:96-122`) against the current file state — line offsets drift as the file evolves. Readers should treat the numbers as indicative and grep for method / comment text instead.
- still unverified: the "polling loop runs forever on unguarded `while(true)`" claim in §9 — the text says "Media3 tolerates this" but the only authoritative way to confirm is to run with `-verbose` logging on a real device after `release()`. Flagged for a regression-test candidate.
- still unverified: real-world 401 behaviour mid-stream (§9 "Auth token is captured at session start") — reproducible only on a long-session test with an artificially short JWT.
