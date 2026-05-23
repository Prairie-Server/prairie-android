Document version: pinned to Media3 1.10.0, Android SDK 35 target.

# AndroidX Media3 — Overview and Setup

This is the first document in the `docs/media3/` suite. Its job is to orient a developer touching the Silo Android or Android TV client for the first time. Scope: what Media3 is, the module graph, the core APIs, threading, events, session integration, and Gradle setup. Downstream documents cover MKV direct-play, HDR/Dolby Vision, and Dolby Atmos / TrueHD / E-AC-3 JOC audio pipelines in depth.

Silo streams MKV files from a self-hosted server. On Android the primary goal is direct-play of MKV (container: Matroska; video: HEVC/AV1/H.264 optionally with Dolby Vision or HDR10/HDR10+; audio: AAC/EAC3/EAC3-JOC/TrueHD/AC-4), with HLS remux/transcode as a fallback when direct-play is not possible. Media3 is the exclusive playback engine on Android; iOS/tvOS use a custom `PlayerCore` path (FFmpeg + VideoToolbox + `AVSampleBufferDisplayLayer`) plus AVFoundation routes and are not covered here.

---

## 1. What Media3 is

AndroidX Media3 is Google's unified media stack for Android. It merges what used to be several separate libraries (standalone ExoPlayer, `androidx.media`, `MediaSessionCompat`, `MediaBrowserCompat`, `PlayerNotificationManager`, etc.) into a single cohesive package under the `androidx.media3.*` namespace (https://developer.android.com/media/media3).

The central architectural move is that every component — player, session, UI, controller — now shares the same `androidx.media3.common.Player` interface, which eliminates the connector/adapter glue code that ExoPlayer 2.x required (https://developer.android.com/media/media3).

### Relationship to legacy ExoPlayer 2.x

- Standalone ExoPlayer (`com.google.android.exoplayer2:*`) is **discontinued**. The last 2.x version is **2.19.1**; there are no further releases under the old coordinates (https://developer.android.com/media/media3/exoplayer/migration-guide).
- The package prefix moves from `com.google.android.exoplayer2.*` to `androidx.media3.*` (https://developer.android.com/media/media3/exoplayer/migration-guide).
- Gradle coordinates move from `com.google.android.exoplayer:exoplayer:2.19.1` to `androidx.media3:media3-exoplayer:1.X.Y` (and sibling modules such as `media3-session`, `media3-ui`, etc.) (https://developer.android.com/media/media3/exoplayer/migration-guide).
- `MediaSessionConnector` and `PlayerNotificationManager` are **removed**. Their work is done by the new `MediaSession` / `MediaSessionService` directly (https://developer.android.com/media/media3/exoplayer/migration-guide).
- `MediaBrowserServiceCompat` + `MediaBrowserCompat` become `MediaLibraryService` + `MediaBrowser`, but the new service remains wire-compatible with the old `MediaBrowserCompat` / `MediaControllerCompat` clients (https://developer.android.com/media/media3/exoplayer/migration-guide).
- Google publishes an automated migration script (`media3-migration.sh`) for the 2.19.1 → Media3 jump (https://developer.android.com/media/media3/exoplayer/migration-guide).

### What Media3 unifies

The Media3 repository (https://github.com/androidx/media) ships as one coordinated release train. A single `1.10.0` tag covers ExoPlayer, the session layer, UI views, Compose UI, transformer/editing, cast, effects, muxer, and the decoder extensions. Bumping the `media3` version in `libs.versions.toml` keeps every module lockstep-compatible, which is the main reason to never mix Media3 versions across modules.

---

## 2. Module breakdown

Every Media3 library lives under the `androidx.media3:*` Gradle group. The full list of modules at tag `1.10.0` is enumerated in the source tree (https://github.com/androidx/media/tree/1.10.0/libraries).

### Currently used by Silo

| Artifact | Purpose |
|---|---|
| `androidx.media3:media3-exoplayer` | Core `ExoPlayer` implementation, renderers, track selection, load control, progressive/image support (https://developer.android.com/media/media3). |
| `androidx.media3:media3-exoplayer-hls` | `HlsMediaSource` and HLS playlist parsing for the server's transcode fallback (https://github.com/androidx/media/tree/1.10.0/libraries). |
| `androidx.media3:media3-datasource-okhttp` | `OkHttpDataSource.Factory` — plugs the OkHttp stack (which the rest of the app already uses) into Media3 for HTTP fetching, including auth headers and connection reuse. |
| `androidx.media3:media3-ui` | `PlayerView` and supporting view-based UI primitives (https://developer.android.com/media/media3). |
| `androidx.media3:media3-session` | `MediaSession`, `MediaSessionService`, `MediaLibraryService`, `MediaController`, `MediaBrowser` — background playback, notification, lock screen, Bluetooth transport controls (https://developer.android.com/media/media3/session/background-playback). |

### Also published — evaluate for Silo

| Artifact | Purpose | Recommendation for Silo |
|---|---|---|
| `androidx.media3:media3-common` | Core interfaces (`Player`, `MediaItem`, `Format`, `C`, `MimeTypes`) (https://developer.android.com/media/media3). | Pulled transitively by `media3-exoplayer`. Declare it explicitly only if a module needs `Player`/`MediaItem` without the full ExoPlayer dependency. |
| `androidx.media3:media3-common-ktx` | Kotlin coroutine/flow extensions for `Player` state (https://github.com/androidx/media/tree/1.10.0/libraries). | Recommended for Compose UI state — converts `Player` listeners to `Flow`/coroutines. |
| `androidx.media3:media3-exoplayer-dash` | `DashMediaSource` for MPEG-DASH manifests (https://github.com/androidx/media/tree/1.10.0/libraries). | Add if/when the server exposes DASH. Not needed today. |
| `androidx.media3:media3-extractor` | Standalone extractor classes (`MatroskaExtractor`, `Mp4Extractor`, etc.). MKV/WebM demuxing lives here (https://developer.android.com/media/media3/exoplayer/supported-formats). | Pulled transitively by `media3-exoplayer`. Declare explicitly only when writing custom extraction outside playback. |
| `androidx.media3:media3-container` | Low-level container parsing helpers used by extractors and the muxer (https://github.com/androidx/media/tree/1.10.0/libraries). | Pulled transitively. No direct dependency needed. |
| `androidx.media3:media3-decoder` | Base `Decoder` / `SimpleDecoder` classes used by extension renderers (https://github.com/androidx/media/tree/1.10.0/libraries). | Pulled transitively. |
| `androidx.media3:media3-ui-compose` | Compose-first playback UI primitives — `PlayerSurface`, and in 1.10.0 new composables `PlaybackSpeedControl`, `ProgressSlider` (https://github.com/androidx/media/releases/tag/1.10.0). | Recommended — the phone and TV apps are Jetpack Compose. Replaces hand-rolled `AndroidView(PlayerView)` wrappers. |
| `androidx.media3:media3-ui-compose-material3` | Material3-themed Compose controls on top of `media3-ui-compose` (https://github.com/androidx/media/releases/tag/1.10.0). | Optional — useful for the phone app's control overlay; TV app typically rolls its own. |
| `androidx.media3:media3-ui-leanback` | Leanback (Android TV) integration (https://github.com/androidx/media/tree/1.10.0/libraries). | Silo's TV app uses Compose for TV (`androidx.tv:tv-material`), not Leanback. Skip unless direction changes. |
| `androidx.media3:media3-datasource-cronet` | Cronet-backed `DataSource` (https://github.com/androidx/media/tree/1.10.0/libraries). | Skip. OkHttp already covers HTTP. |
| `androidx.media3:media3-cast` | `CastPlayer` for Google Cast (https://developer.android.com/media/media3). | Future work if Chromecast support is ever added. |
| `androidx.media3:media3-transformer` / `media3-effect` / `media3-muxer` | Media editing/transformation pipeline (https://developer.android.com/media/media3). | Not needed for playback. |
| `androidx.media3:media3-exoplayer-workmanager` | WorkManager integration for scheduled downloads (https://github.com/androidx/media/tree/1.10.0/libraries). | Only if offline download lands. |

### Decoder extensions — almost all must be built from source

The decoder extensions expose software decoders that fill gaps in the platform codecs. Google explicitly does **not** publish most of them to Maven; they must be cloned and built from the Media3 repo (https://github.com/androidx/media/tree/1.10.0/libraries/decoder_ffmpeg) — the FFmpeg extension README says "The module is not provided via Google's Maven repository (see ExoPlayer issue 2781 for more information)."

Modules present under `libraries/` at the `1.10.0` tag (https://github.com/androidx/media/tree/1.10.0/libraries):

- `decoder_av1`, `decoder_vp9` — software video decoders.
- `decoder_ffmpeg` — software audio decoder renderer (`FfmpegAudioRenderer`).
- `decoder_flac`, `decoder_opus` — software FLAC / Opus.
- `decoder_iamf`, `decoder_mpegh`, `decoder_midi` — IAMF, MPEG-H, MIDI decoders.

**Relevance to MKV direct-play with Dolby audio:** the platform decoders on current Android phones and Android TV devices handle E-AC-3, E-AC-3 JOC, AC-4, and TrueHD natively on devices that are Atmos-capable, and those same decoders surface as `MediaCodec` backends to Media3's `MediaCodecAudioRenderer`. The FFmpeg extension is only required when you need software fallback on devices without platform support for those codecs, and it must be built from source with the relevant codecs enabled (https://developer.android.com/media/media3/exoplayer/supported-formats). Silo's current strategy relies on hardware codecs only; the FFmpeg extension is explicitly out of scope for the initial release. See `docs/media3/` Atmos document for the device-capability matrix.

### What the Silo apps actually need

- **MKV direct-play with HDR/Dolby Vision video and Atmos audio:** `media3-exoplayer` (brings `MatroskaExtractor`, `DefaultRenderersFactory`, `MediaCodecVideoRenderer`, `MediaCodecAudioRenderer`) + `media3-datasource-okhttp` + `media3-ui` or `media3-ui-compose` + `media3-session`. No extra artifact needed for MKV itself — MKV/WebM demuxing is built into `media3-exoplayer` via `MatroskaExtractor` (https://developer.android.com/media/media3/exoplayer/supported-formats). HDR extraction for HDR10/HDR10+ in Matroska/WebM and Dolby Vision in MP4 is handled by ExoPlayer out of the box; actual display depends on the device (https://developer.android.com/media/media3/exoplayer/supported-formats).
- **HLS fallback:** `media3-exoplayer-hls` (already present).
- **Compose UI parity:** adding `media3-ui-compose` (and optionally `media3-ui-compose-material3`) is recommended so the phone and TV apps stop needing `AndroidView` wrappers.
- **Kotlin ergonomics:** `media3-common-ktx` is recommended for the shared ViewModel layer.

---

## 3. Core APIs

All signatures verified against the Media3 1.10.0 source and the Android Developers reference.

### `androidx.media3.common.Player` (interface)

The top-level abstraction for anything that plays media. `ExoPlayer`, `MediaController`, `CastPlayer`, and `ForwardingPlayer` all implement it (https://developer.android.com/media/media3). Key methods:

```kotlin
interface Player {
    fun setMediaItem(mediaItem: MediaItem)
    fun setMediaItems(mediaItems: List<MediaItem>)
    fun prepare()
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlayWhenReady(playWhenReady: Boolean)
    fun release()
    fun addListener(listener: Player.Listener)
    fun removeListener(listener: Player.Listener)
    // plus dozens more — see the reference for the full surface
}
```

`Player.mute()` / `Player.unmute()` were promoted from `@UnstableApi` to stable in 1.10.0 (https://github.com/androidx/media/releases/tag/1.10.0).

### `androidx.media3.exoplayer.ExoPlayer`

The default `Player` implementation. Instantiated via `ExoPlayer.Builder` (https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer.Builder).

### `ExoPlayer.Builder`

Constructor: `ExoPlayer.Builder(context: Context)` (https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer.Builder).

Configurable collaborators — each setter returns the `Builder` for chaining:

- `setRenderersFactory(factory: RenderersFactory)` — supplies the `Renderer[]` used for audio, video, text, metadata, image.
- `setTrackSelector(trackSelector: TrackSelector)` — decides which track in each `TrackGroup` to play.
- `setMediaSourceFactory(factory: MediaSource.Factory)` — converts `MediaItem`s into `MediaSource` instances (progressive, HLS, DASH, etc.).
- `setLoadControl(loadControl: LoadControl)` — buffering policy (how much to buffer ahead, when to start playback after buffering).
- `setBandwidthMeter(bandwidthMeter: BandwidthMeter)` — throughput estimation for adaptive streaming.
- `setAnalyticsCollector(analyticsCollector: AnalyticsCollector)` — event fan-out to `AnalyticsListener`s.
- `setClock(clock: Clock)` — injectable wall clock; tests use `FakeClock`.
- `setLooper(looper: Looper)` — the "application looper" that the `Player` API must be called from. Defaults to the looper of the thread that calls `build()` (or `Looper.getMainLooper()` if that thread has none).
- `setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean)` — audio focus and routing.
- `setHandleAudioBecomingNoisy(handle: Boolean)` — pauses on `ACTION_AUDIO_BECOMING_NOISY`.
- `setWakeMode(wakeMode: Int)` — `WakeLock` / `WifiLock` strategy for background playback.
- `setUseLazyPreparation(useLazyPreparation: Boolean)` — delays per-item preparation until it is about to play.
- `setSeekBackIncrementMs(ms: Long)` / `setSeekForwardIncrementMs(ms: Long)` — step sizes for the seek-back/seek-forward commands.
- `setReleaseTimeoutMs(ms: Long)` — bound on `release()` waiting for the playback thread.
- `setUsePlatformDiagnostics(enabled: Boolean)` — opt in/out of Android's platform `MediaMetrics` reporting.

Terminal: `build(): ExoPlayer` (https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer.Builder).

### `MediaItem` / `MediaItem.Builder`

The descriptor consumed by the player. Minimal construction: `MediaItem.fromUri(uri)` or the builder for more fields:

```kotlin
val item: MediaItem = MediaItem.Builder()
    .setMediaId("catalog-item-42")
    .setUri(streamUri)
    .setMimeType(MimeTypes.APPLICATION_M3U8)   // hint the source factory
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle("The Empire Strikes Back")
            .setArtworkUri(posterUri)
            .build()
    )
    .build()
```

(https://developer.android.com/media/media3/session/background-playback)

### `MediaSource`, `ProgressiveMediaSource`, `HlsMediaSource`, `DefaultMediaSourceFactory`

`MediaSource` is the internal abstraction ExoPlayer uses once a `MediaItem` is resolved (https://developer.android.com/media/media3). You rarely construct one directly; instead:

- `DefaultMediaSourceFactory(context)` — picks the right source (Progressive / HLS / DASH / SmoothStreaming / RTSP) based on the `MediaItem` URI + MIME type + the extractors/factories present on the classpath. Add `media3-exoplayer-hls` and DASH is automatically wired the same way if `media3-exoplayer-dash` is also on the classpath.
- `ProgressiveMediaSource.Factory(dataSourceFactory)` — for plain progressive downloads (MP4, MKV, MP3) — what a direct-play MKV ends up using.
- `HlsMediaSource.Factory(dataSourceFactory)` — for HLS.

Usage:

```kotlin
val dataSourceFactory: DataSource.Factory = OkHttpDataSource.Factory(okHttpClient)
    .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))

val mediaSourceFactory = DefaultMediaSourceFactory(context)
    .setDataSourceFactory(dataSourceFactory)

val player = ExoPlayer.Builder(context)
    .setMediaSourceFactory(mediaSourceFactory)
    .build()
```

### `DataSource` / `DataSource.Factory` / `DefaultHttpDataSource.Factory` / `OkHttpDataSource.Factory`

`DataSource` is the byte-stream abstraction used by every extractor and media source. `DataSource.Factory` is the SPI (https://developer.android.com/media/media3).

- `DefaultHttpDataSource.Factory` — built-in, uses `HttpURLConnection`.
- `OkHttpDataSource.Factory` — from `media3-datasource-okhttp`, wraps an `OkHttpClient`. Silo should use this so Media3 shares the same HTTP stack, auth headers, certificate pinning, and connection pool as the rest of the app.

### `RenderersFactory` / `DefaultRenderersFactory`

Creates the `Renderer[]` for audio, video, text, metadata, image (https://developer.android.com/media/media3). `DefaultRenderersFactory` is what you want 99% of the time. Key configurator:

```kotlin
val renderersFactory = DefaultRenderersFactory(context)
    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
    .setEnableDecoderFallback(true)
```

Extension renderer mode is `OFF` / `ON` / `PREFER` and is what decides whether the FFmpeg/AV1/Opus extension renderers are tried ahead of `MediaCodec` renderers (https://github.com/androidx/media/tree/1.10.0/libraries/decoder_ffmpeg). Leave it `OFF` unless you ship extension renderers.

### `TrackSelector` / `DefaultTrackSelector` / `TrackSelectionParameters`

`TrackSelector` picks which track in each `TrackGroup` to use. `DefaultTrackSelector` is the default; it is configured through `TrackSelectionParameters` (https://developer.android.com/media/media3).

```kotlin
val trackSelector = DefaultTrackSelector(context).apply {
    parameters = buildUponParameters()
        .setMaxVideoSizeSd()                       // cap bitrate for cellular, for example
        .setPreferredAudioLanguage("eng")
        .setTunnelingEnabled(true)                 // Android TV tunneling path
        .build()
}
```

`TrackSelectionParameters` is also the control point for HDR and audio-channel overrides — this is where the HDR/Atmos documents dig in.

### `LoadControl` / `DefaultLoadControl`

`LoadControl` decides when to buffer more and when to start playback after a rebuffer (https://developer.android.com/media/media3). `DefaultLoadControl.Builder` tunes:

- `setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)`
- `setTargetBufferBytes(bytes)`
- `setPrioritizeTimeOverSizeThresholds(prioritizeTime)`

For long-form local network MKV, the defaults are usually fine.

### `BandwidthMeter` / `DefaultBandwidthMeter`

`BandwidthMeter` tracks sustained HTTP throughput so the track selector can pick the right adaptive bitrate (https://developer.android.com/media/media3). `DefaultBandwidthMeter.Builder(context).build()` is enough for almost everything, and caches recent estimates keyed by network type.

---

## 4. Player lifecycle and threading

Media3 enforces a strict threading model (https://developer.android.com/media/media3/exoplayer/listening-to-player-events):

- Every `Player` method must be called from the **application looper**. That is the `Looper` you passed to `ExoPlayer.Builder.setLooper()`, or — if you did not set one — the looper of the thread that called `build()`, or `Looper.getMainLooper()` if that thread had no looper. In practice, for Android apps, just build and drive the player from the main thread.
- If you call a `Player` method from the wrong thread, Media3 throws `IllegalStateException` immediately — do not ignore this in production.
- Listener callbacks (`Player.Listener`, `AnalyticsListener`) are delivered on the application looper. You can update UI directly from them. The Media3 background/threading guidance (https://developer.android.com/media/media3/exoplayer/threading-model) is unambiguous: callbacks run on the application looper passed to `Builder.setLooper` (or the thread that called `build()` / `Looper.getMainLooper()` by default). In practice for this project the application looper IS the main thread, so UI updates are safe.
- There is a separate internal playback thread owned by `ExoPlayer`. You never touch it directly; renderers, extractors, and the `LoadControl` run there.
- `release()` must be called when the player is no longer needed — typically in `onDestroy()` of the screen's `ViewModel` (via `onCleared`) or `Activity`. It blocks until the internal playback thread drains, bounded by `setReleaseTimeoutMs`. Failing to release leaks the codec, the surface, and the audio session.
- After `release()`, the `Player` instance is dead; re-create a new `ExoPlayer` for the next session rather than trying to reuse.
- Removing listeners manually is optional — `release()` drops all registered listeners (https://developer.android.com/media/media3/exoplayer/listening-to-player-events).

### Minimal Compose pattern (verified shape)

```kotlin
@Composable
fun PlayerScreen(videoUri: Uri) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(OkHttpDataSource.Factory(okHttpClient))
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(videoUri))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    // Bind player to a PlayerSurface (media3-ui-compose) or AndroidView(PlayerView).
}
```

(based on https://developer.android.com/media/media3/exoplayer/hello-world)

---

## 5. Events — `Player.Listener`, `Player.Events`, `AnalyticsListener`

Two layers of event reporting (https://developer.android.com/media/media3/exoplayer/listening-to-player-events):

### `Player.Listener`

Callback interface with default no-op implementations. Register with `player.addListener(listener)`; unregister with `player.removeListener(listener)`. The callbacks you actually care about:

- `onPlaybackStateChanged(state: Int)` — one of `Player.STATE_IDLE`, `STATE_BUFFERING`, `STATE_READY`, `STATE_ENDED`.
- `onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int)` — user intent to play.
- `onIsPlayingChanged(isPlaying: Boolean)` — the composite "is the playhead actually moving" signal; combines state + `playWhenReady` + suppression (audio focus, offload sleep, etc.). Prefer this for UI.
- `onPlayerError(error: PlaybackException)` — player transitions to `STATE_IDLE` on error.
- `onMediaItemTransition(mediaItem: MediaItem?, reason: Int)` — playlist advance / seek across item boundary.
- `onMediaMetadataChanged(mediaMetadata: MediaMetadata)` — metadata from the stream (ID3, in-manifest, etc.) or from updated `MediaItem`s.
- `onPositionDiscontinuity(old: PositionInfo, new: PositionInfo, reason: Int)` — seeks, period transitions, ad insertion.
- `onTracksChanged(tracks: Tracks)` — selected-track set changed; hook into this for audio/subtitle track menus.
- `onVideoSizeChanged(videoSize: VideoSize)` — natural resolution for aspect ratio.

### `Player.Events` batch delivery

Implement `onEvents(player: Player, events: Player.Events)` to get all flags that fired in the same iteration of the message loop. This is how you avoid redundant UI redraws when five callbacks fire in sequence.

```kotlin
override fun onEvents(player: Player, events: Player.Events) {
    if (events.containsAny(
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_PLAY_WHEN_READY_CHANGED,
            Player.EVENT_IS_PLAYING_CHANGED,
        )
    ) {
        updatePlayPauseUi(player)
    }
}
```

(https://developer.android.com/media/media3/exoplayer/listening-to-player-events)

### `AnalyticsListener`

A strict superset of `Player.Listener`. Every event carries a `EventTime` struct with window/period/timestamp context, plus extra events the end-user listener does not see (dropped-frames counters, decoder init latency, bandwidth samples, codec format chosen, etc.) (https://developer.android.com/media/media3/exoplayer/listening-to-player-events). Register with `player.addAnalyticsListener(listener)`. The built-in `EventLogger` implementation is the first thing to attach when debugging — it dumps everything to logcat.

### What you do **not** get

- Raw `MediaCodec` events.
- Audio HAL / AudioTrack underrun counts (outside what `AnalyticsListener` surfaces as audio sink positions).
- Low-level TCP/HTTP timing beyond what `BandwidthMeter` reports.

---

## 6. MediaSession integration (`androidx.media3:media3-session`)

Purpose: let the player keep running in the background, integrate with system media controls, and accept commands from external controllers (https://developer.android.com/media/media3/session/background-playback).

### Components

- `MediaSession` — wraps a `Player` and exposes it to controllers. Even foreground activities benefit from owning one.
- `MediaSessionService` — `Service` that hosts a `MediaSession` so playback survives the Activity lifecycle.
- `MediaLibraryService` — extends `MediaSessionService` for apps that also want to expose a browsable catalog (Android Auto, Wear, Assistant).
- `MediaController` — client-side handle inside an Activity / Fragment / Composable; talks to a `MediaSession` over Binder.
- `MediaBrowser` — extended `MediaController` that can browse a `MediaLibraryService`'s catalog.
- `MediaNotification` / `MediaNotification.Provider` — the notification layer; `DefaultMediaNotificationProvider` ships out of the box.

### What you get for free

From https://developer.android.com/media/media3/session/background-playback :

- A `MediaStyle` foreground notification, updated automatically from `Player` + `MediaSession` state (title, artist, art, transport buttons). Cannot be swipe-dismissed while the foreground service is running; auto-removes after ~10 min of inactivity.
- Automatic foreground service promotion when `Player` has media items.
- Bluetooth headset transport control (play/pause, next/prev), lock-screen controls, and Google Assistant integration via the platform session.
- Backwards compatibility — legacy `MediaControllerCompat` / `MediaBrowserCompat` clients connect to the Media3 service transparently.
- Multi-controller support — Wear OS, Android Auto, other apps can be connected at the same time.

### What you must wire up

- Declare the service in the manifest with `android:foregroundServiceType="mediaPlayback"` and both the `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions (https://developer.android.com/media/media3/session/background-playback).
- Register the service class with the `androidx.media3.session.MediaSessionService` (or `MediaLibraryService`) intent action — plus `android.media.browse.MediaBrowserService` for legacy clients.
- Build the `MediaSession` in `onCreate` and release it (and the wrapped `Player`) in `onDestroy`.
- Populate `MediaMetadata` on every `MediaItem` (title, artist, artwork URI) — without this the notification is blank.
- Implement `MediaSession.Callback.onPlaybackResumption` if you want the Play button on the notification to restart playback after the process was killed (https://developer.android.com/media/media3/session/background-playback).
- Connect a `MediaController` (or `MediaBrowser`) from your UI using `SessionToken` + `MediaController.Builder(context, sessionToken).buildAsync()`. The controller exposes the same `Player` interface your Composables already bind to.

In Media3 1.10.0, `MediaSessionService` and `MediaLibraryService` became lifecycle-aware services, fixing a long-standing class of bugs where stale intents could crash the foreground service on cold start (https://github.com/androidx/media/releases/tag/1.10.0).

---

## 7. Media3 1.10.0 highlights (relevant to Silo)

Verified against https://github.com/androidx/media/releases/tag/1.10.0 and the release notes summary.

### Directly relevant

- **Dolby Vision Profile 10 support added.** This is significant for MKV direct-play because Profile 10 (AV1-based Dolby Vision) is increasingly present in remuxed UHD MKVs. Previous Media3 releases only covered the H.265-based profiles. (https://github.com/androidx/media/releases/tag/1.10.0)
- **Dynamic scheduling in `MediaCodecVideoRenderer`** — aligns CPU wake cycles with the video frame cadence to reduce power draw; opt-in via `DefaultRenderersFactory.setEnableMediaCodecVideoRendererDurationToProgressUs(boolean)` (verified against the 1.10.0 `DefaultRenderersFactory.java` source — note there is no `experimental` prefix on this method, though it does require MediaCodec asynchronous mode to take effect). Worth piloting on Android TV where devices are often always-on. (https://github.com/androidx/media/releases/tag/1.10.0)
- **Gapless compressed-offload audio stall fixed.** Audio offload is how Android delegates decoding to the DSP for battery reasons; the fix matters for Atmos/E-AC-3 JOC where offload is the normal path on TV. (https://github.com/androidx/media/releases/tag/1.10.0)
- **`DefaultAudioSink` retries `AudioOutput` initialization more robustly.** Reduces a known class of initialization races seen when switching between passthrough and PCM mid-session. (https://github.com/androidx/media/releases/tag/1.10.0)
- **Format API surface stabilized:** `Player.mute()`, `Player.unmute()`, `Format.pcmEncoding`, and `C.PcmEncoding` promoted out of `@UnstableApi`. You can now call them without the opt-in annotation. (https://github.com/androidx/media/releases/tag/1.10.0)
- **AC-4 profile handling fix** for automotive scenarios — relevant background for understanding the codec path even if Silo is not automotive. (https://github.com/androidx/media/releases/tag/1.10.0)
- **`MediaSessionService` / `MediaLibraryService` now lifecycle-aware**, with `PendingIntent` builders added for home-screen widgets and stale-intent detection to prevent foreground-service crashes. (https://github.com/androidx/media/releases/tag/1.10.0)

### Indirectly relevant

- VVC (H.266) track extraction in MP4 containers added. Not widely produced yet, but Media3 will now at least recognize the track.
- IAMF decoder swapped from `libiamf` to `iamf_tools` with binaural output via the Android Spatializer. IAMF is an MP4-container immersive audio format that competes with E-AC-3 JOC; relevant if Silo ever transcodes to IAMF.
- HLS: X-PLAYOUT-LIMIT support for interstitials, QUERYPARAM attribute handling with `#EXT-X-DEFINE`, regex caching in playlist parsing, ID3 EMSG exposure in audio renditions, redundant-location fallback.
- DASH: unaligned segment start fixes, EMSG v0 timestamp correction.
- UI Compose: new `PlaybackSpeedControl` and `ProgressSlider` composables, Material3 controls module.

(All from https://github.com/androidx/media/releases/tag/1.10.0)

### Things **not** changed in 1.10.0

- The Matroska extractor received no listed changes in the 1.10.0 notes. MKV handling is stable from prior releases.
- The FFmpeg extension distribution policy is unchanged — still source-only.

---

## 8. Gradle setup

The Silo project uses Gradle Version Catalogs (`gradle/libs.versions.toml`). Current entries (`gradle/libs.versions.toml:10` and `54-59`):

```toml
[versions]
media3 = "1.10.0"

[libraries]
media3-exoplayer          = { module = "androidx.media3:media3-exoplayer",          version.ref = "media3" }
media3-exoplayer-hls      = { module = "androidx.media3:media3-exoplayer-hls",      version.ref = "media3" }
media3-datasource-okhttp  = { module = "androidx.media3:media3-datasource-okhttp",  version.ref = "media3" }
media3-ui                 = { module = "androidx.media3:media3-ui",                 version.ref = "media3" }
media3-session            = { module = "androidx.media3:media3-session",            version.ref = "media3" }
```

### Recommended additions

Add the Compose UI integrations and the common-ktx extensions:

```toml
[libraries]
media3-ui-compose           = { module = "androidx.media3:media3-ui-compose",           version.ref = "media3" }
media3-ui-compose-material3 = { module = "androidx.media3:media3-ui-compose-material3", version.ref = "media3" }
media3-common-ktx           = { module = "androidx.media3:media3-common-ktx",           version.ref = "media3" }
```

### `androidApp/build.gradle.kts` — dependencies block

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.continuum.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.continuum.app"
        minSdk = 24
        targetSdk = 35
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.ui.compose.material3)
    implementation(libs.media3.session)
    implementation(libs.media3.common.ktx)

    // Existing dependencies (OkHttp, Coil, Compose, Koin, etc.)
}
```

### Manifest — required bits for a `MediaSessionService`

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.INTERNET" />

<application ...>
    <service
        android:name=".playback.SiloPlaybackService"
        android:foregroundServiceType="mediaPlayback"
        android:exported="true">
        <intent-filter>
            <action android:name="androidx.media3.session.MediaSessionService" />
            <action android:name="android.media.browse.MediaBrowserService" />
        </intent-filter>
    </service>
</application>
```

(https://developer.android.com/media/media3/session/background-playback)

### Gradle notes

- Media3 1.10.0 targets Java 8 at minimum; the project's Java 17 / Kotlin 2.1.20 config is more than sufficient (https://developer.android.com/media/media3/exoplayer/hello-world).
- Many Media3 symbols are annotated `@UnstableApi`. Prefer localized opt-in (`@OptIn(UnstableApi::class)` on the method or class that uses them) over a module-wide compiler argument — the annotation is Google's intentional signal that a signature might change between minor versions.
- Never mix Media3 module versions — bump the single `media3 = "..."` entry and rebuild. If a module is published at a later version than another, there is no guarantee the ABI matches.

---

## Sources

- https://developer.android.com/media/media3
- https://developer.android.com/media/media3/exoplayer/migration-guide
- https://developer.android.com/media/media3/exoplayer/hello-world
- https://developer.android.com/media/media3/exoplayer/listening-to-player-events
- https://developer.android.com/media/media3/exoplayer/supported-formats
- https://developer.android.com/media/media3/session/background-playback
- https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer.Builder
- https://github.com/androidx/media
- https://github.com/androidx/media/tree/1.10.0/libraries
- https://github.com/androidx/media/tree/1.10.0/libraries/decoder_ffmpeg
- https://github.com/androidx/media/releases/tag/1.10.0
- https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultRenderersFactory.java
- https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java
- https://raw.githubusercontent.com/androidx/media/1.10.0/RELEASENOTES.md

## Validation log

- corrected: "experimentalSetEnableMediaCodecVideoRendererDurationToProgressUs()" → the method is named `setEnableMediaCodecVideoRendererDurationToProgressUs(boolean)` in Media3 1.10.0 (no `experimental` prefix). (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultRenderersFactory.java)
- verified: `ExoPlayer.Builder` setter list — `setRenderersFactory`, `setTrackSelector`, `setMediaSourceFactory`, `setLoadControl`, `setBandwidthMeter`, `setAnalyticsCollector`, `setClock`, `setLooper`, `setAudioAttributes`, `setHandleAudioBecomingNoisy`, `setWakeMode`, `setUseLazyPreparation`, `setSeekBackIncrementMs`, `setSeekForwardIncrementMs`, `setReleaseTimeoutMs`, `setUsePlatformDiagnostics`, `setPauseAtEndOfMediaItems`, `setDeviceVolumeControlEnabled`, `setSuppressPlaybackOnUnsuitableOutput`, `setVideoChangeFrameRateStrategy` all present and match the signatures given. (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java)
- verified: `Player.mute()` / `Player.unmute()` and `Format.pcmEncoding` + `C.PcmEncoding` promoted out of `@UnstableApi` in 1.10.0. (https://raw.githubusercontent.com/androidx/media/1.10.0/RELEASENOTES.md) `Format.pcmEncoding` has no `@UnstableApi` annotation on the 1.10.0 `Format.java`.
- verified: Dolby Vision Profile 10 added; dynamic scheduling in `MediaCodecVideoRenderer`; gapless compressed-offload playlist stall fix; `AudioOutput` retry improvement; `MediaSessionService`/`MediaLibraryService` promoted to `LifecycleService`; VVC track extraction in MP4; IAMF `iamf_tools` swap; AC-4 automotive profile-filtering fix — all present in `RELEASENOTES.md` for 1.10.0. (https://raw.githubusercontent.com/androidx/media/1.10.0/RELEASENOTES.md)
- corrected: "Listener callbacks execute on the playback thread by default" — the developer.android.com wording in the old `listening-to-player-events` page has been superseded; the authoritative threading model says callbacks fire on the application looper (https://developer.android.com/media/media3/exoplayer/threading-model). Removed the "(unverified)" note in §4.
- still unverified: exact integer values and API-level introduction for Android's `WAKE_MODE_*` constants — the docs reference them by Media3's `C.WAKE_MODE_NETWORK` constant (verified present in `C.java`, value 2); underlying `WakeLock` / `WifiLock` strategy is verifiable only at runtime on a device.
