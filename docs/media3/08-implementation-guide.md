Document version: Media3 1.10.0

# Implementation Guide — MKV + HDR/DV/Atmos Playback

This is the synthesis document for the `docs/media3/` suite: a concrete, end-to-end recipe
for landing MKV direct-play with HDR / Dolby Vision video and Dolby Atmos audio on the
Silo Android phone client (`androidApp/`) and Android TV client (`androidTvApp/`).
Every section should be read against its source document — this guide compresses the
verified facts from docs 01–07 into change-order form. When a fact is marked
"(unverified)" in a source, it stays "(unverified)" here.

---

## 0. Prerequisites and assumptions

- **Media3 pin:** `1.10.0`, set once at `gradle/libs.versions.toml:10` and used from every
  module. Never mix versions. (doc 01 §2 and §8.)
- **Language / toolchain:** Kotlin 2.1.20, JVM 21, compileSdk 36, minSdk 26, targetSdk 35
  (matches the current state in `androidApp/build.gradle.kts`, `androidTvApp/build.gradle.kts`,
  `android-shared/build.gradle.kts`).
- **UI:** Jetpack Compose on phone (`androidApp`), Compose for TV (`androidx.tv:tv-material`)
  on the TV app. The iOS / tvOS targets use a custom `PlayerCore` path (FFmpeg + VideoToolbox) plus AVFoundation routes and are out of scope for this guide — see the top-level `CLAUDE.md`.
- **Primary path is direct-play MKV.** The Silo server exposes the original MKV over
  `/user/stream/<fileId>`; `ProgressiveMediaSource` + `MatroskaExtractor` demux it, and
  `MediaCodec` decodes the elementary streams. This is the only path that preserves
  Dolby Vision metadata, HDR10+ dynamic metadata, and the bitstream payload for Atmos
  passthrough — transcoding to HLS on the server intrinsically strips dynamic HDR and
  (unless the server chooses `copy` for audio) strips Atmos. (doc 02 §1, doc 03 §4,
  doc 04 §2, doc 05 §1.)
- **HLS fallback is for genuinely unplayable direct-play content**, or a user-initiated
  quality override. The server decides via `/playback/decide`; the client supplies
  `ClientCodecCapabilities` so the server picks the right variant. The `REMUX` variant
  (video copy + audio copy, delivered as HLS) is what the server picks when the container
  is the only problem — we keep that path because it preserves Atmos in the HLS payload.
  (`PlayerViewModel.handleSessionStarted`, doc 07 §3.)
- **Current gap:** `androidApp/src/androidMain/kotlin/com/continuum/app/android/service/PlaybackService.kt`
  constructs a second, uninitialised `ExoPlayer` that is never wired to the UI player in
  `PlayerScreen` / `TvPlayerScreen`. The recommendation below is to **consolidate onto a
  single `MediaSessionService`-owned player** that both the phone UI and, once `media3-session`
  is added to the TV build, the TV UI bind to through `MediaController`. This also removes
  the duplicated `RenderersFactory` / `TrackSelector` construction. (doc 07 §2, §8, §9.)

---

## 1. Gradle changes

### 1.1 Version catalog — new entries

Add the following libraries to `gradle/libs.versions.toml` under `[libraries]`. The
`media3` version already exists at `gradle/libs.versions.toml:10`; reuse it.

```toml
# Media3 — existing (keep):
media3-exoplayer         = { module = "androidx.media3:media3-exoplayer",         version.ref = "media3" }
media3-exoplayer-hls     = { module = "androidx.media3:media3-exoplayer-hls",     version.ref = "media3" }
media3-datasource-okhttp = { module = "androidx.media3:media3-datasource-okhttp", version.ref = "media3" }
media3-ui                = { module = "androidx.media3:media3-ui",                version.ref = "media3" }
media3-session           = { module = "androidx.media3:media3-session",           version.ref = "media3" }

# Media3 — ADD. These are shipped on Google's Maven; no source build required.
media3-common            = { module = "androidx.media3:media3-common",            version.ref = "media3" }
media3-common-ktx        = { module = "androidx.media3:media3-common-ktx",        version.ref = "media3" }
media3-extractor         = { module = "androidx.media3:media3-extractor",         version.ref = "media3" }
media3-container         = { module = "androidx.media3:media3-container",         version.ref = "media3" }
media3-decoder           = { module = "androidx.media3:media3-decoder",           version.ref = "media3" }
media3-ui-compose        = { module = "androidx.media3:media3-ui-compose",        version.ref = "media3" }
media3-ui-compose-material3 = { module = "androidx.media3:media3-ui-compose-material3", version.ref = "media3" }
```

Rationale per-module, verified against doc 01 §2 and doc 02 §1:

- `media3-common` / `media3-common-ktx` — declared explicitly so modules that consume
  `Player`, `MediaItem`, `Format`, or the `Flow`/coroutine helpers don't rely on a
  transitive pull. `media3-common-ktx` is what the ViewModels want for `Player` state
  as `Flow`.
- `media3-extractor` / `media3-container` — explicit so the progressive MKV direct-play
  path can configure `DefaultExtractorsFactory` / `MatroskaExtractor` flags (see §6).
  They are pulled transitively today but the current code cannot reach them to set flags.
- `media3-decoder` — carries `Decoder` / `SimpleDecoder` base classes used by the
  extension renderers. Only relevant if you accept §1.3 (FFmpeg) below.
- `media3-ui-compose` / `-material3` — the Compose integration replaces the
  `AndroidView(PlayerView)` wrappers in `PlayerScreen.kt:221-234` and
  `TvPlayerScreen.kt:221-232`.

### 1.2 Per-module build.gradle.kts additions

**`android-shared/build.gradle.kts`** (lines 31-33 today). Add the extractor / container /
session / common / common-ktx entries, because this module owns `SiloPlayerFactory`,
the capability probes, and will own the `MediaSession`-bound player:

```kotlin
dependencies {
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.common)
    implementation(libs.media3.common.ktx)
    implementation(libs.media3.extractor)
    implementation(libs.media3.container)
    implementation(libs.media3.session)        // NEW — needed so the factory can
                                               // construct MediaSession-aware players
}
```

**`androidApp/build.gradle.kts`** (lines 35-39). Add the Compose UI integrations:

```kotlin
implementation(libs.media3.exoplayer)
implementation(libs.media3.exoplayer.hls)
implementation(libs.media3.datasource.okhttp)
implementation(libs.media3.ui)
implementation(libs.media3.ui.compose)                // NEW
implementation(libs.media3.ui.compose.material3)      // NEW — optional, matches phone style
implementation(libs.media3.session)
implementation(libs.media3.common.ktx)                // NEW
```

**`androidTvApp/build.gradle.kts`** (lines 35-38). **This is the fix for the missing
`media3-session` on TV flagged in doc 07 §7.** Also add the Compose UI integration and
common-ktx; leave Material3 off since the TV app uses `androidx.tv:tv-material`:

```kotlin
implementation(libs.media3.exoplayer)
implementation(libs.media3.exoplayer.hls)
implementation(libs.media3.datasource.okhttp)
implementation(libs.media3.ui)
implementation(libs.media3.ui.compose)                // NEW
implementation(libs.media3.session)                   // NEW — fix doc 07 §7 gap
implementation(libs.media3.common.ktx)                // NEW
```

**`shared/build.gradle.kts`.** No change — the KMP shared module stays out of the Media3
graph by design (see top-level `CLAUDE.md`).

### 1.3 FFmpeg decoder extension (optional, source-only)

The FFmpeg audio decoder extension (`libraries/decoder_ffmpeg`) would give us a software
path for TrueHD / DTS-HD on devices without hardware decode and without passthrough. It
**cannot be added to Gradle** — the module is not published to Maven; you must clone
`androidx/media` at tag `1.10.0` and build it from source, enabling each codec in
`libraries/decoder_ffmpeg/src/main/jni/build_ffmpeg.sh` (source quoted in doc 01 §2 and
doc 04 §3).

Decision for initial release: **do not ship the FFmpeg extension.** Keep
`DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF` (see §3) and, when passthrough is
not available on the device's current route, redirect playback to server-side transcoded
HLS at a codec the device can decode (AAC). This is what §6 and §7 formalise as the
fallback matrix.

If we accept the FFmpeg extension later (see §10 step 7):

1. Clone `androidx/media` at `1.10.0`, build `decoder_ffmpeg` with TrueHD, DTS, DTS-HD,
   MLP enabled (per the README at
   `https://github.com/androidx/media/tree/1.10.0/libraries/decoder_ffmpeg`).
2. Publish the AAR to a private Maven / mavenLocal.
3. Reference it from `android-shared` only.
4. Flip the factory to `EXTENSION_RENDERER_MODE_PREFER` for audio when the extension is
   on the classpath.

### 1.4 Manifest additions

**`androidApp/src/androidMain/AndroidManifest.xml`** is already correct for foreground
media playback (lines 3-6): `INTERNET`, `ACCESS_NETWORK_STATE`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Add:

```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

…to back `ExoPlayer.Builder.setWakeMode(C.WAKE_MODE_NETWORK)` (§3). This was flagged as
missing in doc 07 §7.

**`androidTvApp/src/androidMain/AndroidManifest.xml`** needs several additions. It is
currently missing everything required to host a `MediaSessionService`:

```xml
<uses-feature
    android:name="android.software.leanback"
    android:required="true" />
<uses-feature
    android:name="android.hardware.touchscreen"
    android:required="false" />

<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

Then, inside `<application>`, declare the (shared) service. We do **not** keep the
service class in `androidTvApp/` — we move it to `android-shared/` so both apps register
the same class (see §5):

```xml
<service
    android:name="com.continuum.app.common.player.SiloPlaybackService"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

**Hardware acceleration:** both apps inherit the default `android:hardwareAccelerated="true"`
from the manifest merger. Do not add `largeHeap="true"` unless profiling shows codec
init OOM — Media3 does not require it for 4K HDR playback.

---

## 2. Capability probes at startup

The existing probes (`MediaCodecCapabilitiesProbe`, `DisplayHdrProbe`,
`AudioCapabilityManager`, `PlaybackCapabilityDetector`) are sophisticated and already
feed the server-side resolver — **do not rewrite them.** What is missing is the
client-side wiring that feeds those same probes into the player's `TrackSelectionParameters`
so the client-side selector picks the Atmos / DV track when the server has shipped a
multi-track MKV. This section shows how to connect them.

### 2.1 TV-mode detection

Per doc 06 §1.1, combine `UiModeManager` with `FEATURE_LEANBACK`. Add the helper once in
`android-shared`:

```kotlin
// android-shared/src/androidMain/kotlin/com/continuum/app/common/player/TvMode.kt
package com.continuum.app.common.player

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

fun Context.isTvUi(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    val isLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION || isLeanback
}
```

This lets the shared factory stop taking `isTv: Boolean` as a constructor arg — it can
derive it from the Context at build time. `androidTvModule.kt:45` already passes
`isTv = true`; replacing that with a runtime detection is safer because it also handles
the rare case of the phone APK running on a leanback chassis.

### 2.2 Display probes

The existing `DisplayHdrProbe.probe(context)` returns the panel-side `HdrCapabilities`
(doc 03 §7.1 semantics). For the TV refresh-rate path, extract a separate read of
`Display.getSupportedModes()` — this is already used inside `HdrDisplayController.applyForMedia`
but is not surfaced to the probe layer. Add a thin helper:

```kotlin
// android-shared/.../DisplayModeProbe.kt
object DisplayModeProbe {
    data class Result(
        val modes: List<Display.Mode>,
        val currentModeId: Int,
        val supportsSeamlessRateSwitch: Boolean,
    )

    fun probe(context: Context): Result {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return Result(emptyList(), 0, false)
        val modes = display.supportedModes.toList()
        val current = display.mode
        val seamless = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            // matchContentFrameRateUserPreference is API 31+; on older Rs we assume seamless.
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                dm.matchContentFrameRateUserPreference != DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER)
        return Result(modes, current.modeId, seamless)
    }
}
```

(API references for `Display.Mode` and `matchContentFrameRateUserPreference` are in
doc 06 §1.2 and §1.3.)

### 2.3 Audio sink probe and hotplug

`AudioCapabilityManager` is already correct:

- It constructs an `AudioCapabilitiesReceiver` with `mediaAttrs` (doc 04 §4.3).
- It registers in `init { receiver.register() }` (matches the pattern in doc 05 §5.4).
- It maps encodings to the server's `AudioPassthroughCapabilities` DTO.

Two follow-ups against doc 05 and doc 04:

1. **The `getMaxSupportedChannelCountForPassthrough` shim**
   (`AudioCapabilityManager.kt:110-111`) — verification against the Media3
   1.10.0 source shows there is **no** public instance method by that name on
   `AudioCapabilities`; the implementation is a `public static` inside the
   private `Api29` inner class, so it is not reachable from app code (doc 04
   §4.2, doc 07 §9 clarification). The shim therefore cannot be replaced with
   a "real" Media3 call at 1.10.0. Options: fall through to
   `AudioTrack.isDirectPlaybackSupported(AudioFormat, AudioAttributes)` for
   per-encoding channel probing, or accept the approximation and wait for a
   future Media3 release that promotes the API-29 probe to public.
2. **Register a `Spatializer.OnSpatializerStateChangedListener`** on API 32+. Today
   `spatializer.isEnabled` is polled once inside `mapCapabilities` — flipping headphones
   mid-session changes nothing. Add:

```kotlin
// Inside AudioCapabilityManager, gated on Build.VERSION.SDK_INT >= S_V2 (= 32).
private val spatializerListener = Spatializer.OnSpatializerStateChangedListener { sp, _ ->
    _capabilities.update { it.copy(spatializerEnabled = sp.isEnabled) }
}

init {
    receiver.register()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.spatializer.addOnSpatializerStateChangedListener(
            /* executor = */ Runnable::run, spatializerListener
        )
    }
}

fun close() {
    receiver.unregister()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.spatializer.removeOnSpatializerStateChangedListener(spatializerListener)
    }
}
```

### 2.4 Feeding probes into `TrackSelectionParameters`

`SiloPlayerFactory` currently does not use any of the probe outputs when building
`TrackSelectionParameters`. That is the root cause of doc 07's #3 blocker ("no client-side
`TrackSelectionParameters` preferences for DV / JOC / language / channel count"). §4 of
this guide gives the phone-preset and TV-preset builders that read from the probes.

---

## 3. Constructing the ExoPlayer instance — evolved `SiloPlayerFactory`

This section replaces `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/SiloPlayerFactory.kt`.
It addresses doc 07 blockers #3, #4, #5, #6, and #7. Fields and setters below are
verified against doc 01 §3, doc 03 §7.2, doc 05 §5, and doc 06 §2.

```kotlin
package com.continuum.app.common.player

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import com.continuum.app.model.playback.PlayMethod
import com.continuum.app.model.playback.PlayerSubtitleInfo
import com.continuum.app.network.TokenManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for ExoPlayer construction. The previous per-screen
 * factory path has been collapsed into a single builder whose output is held
 * by [SiloPlaybackService] and exposed to Compose via MediaController.
 *
 * Accepts detected capabilities so the TrackSelectionParameters reflect the
 * display + audio sink, and so TV-only knobs (tunneling, display-mode switch)
 * are off on phone hardware.
 */
@UnstableApi
class SiloPlayerFactory(
    private val context: Context,
    private val tokenManager: TokenManager,
    private val subtitleManager: SubtitleManager,
    private val audioCapabilityManager: AudioCapabilityManager,
) {
    private val appContext: Context = context.applicationContext

    // Pooled OkHttp — one per factory instance, survives the factory's lifetime.
    // Fixes doc 07 §9 "AuthenticatedDataSourceFactory builds a new OkHttpClient
    // per player session".
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            // HTTP/2 by default. No dispatcher tuning required.
            .build()
    }

    private val isTv: Boolean = run {
        val uiMode = appContext.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val leanback = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION || leanback
    }

    fun createPlayer(): ExoPlayer {
        // ---- Renderers ----
        // EXTENSION_RENDERER_MODE_OFF is correct because the FFmpeg extension
        // is not on the classpath (doc 01 §2, doc 04 §3). If / when it is
        // added, switch to _PREFER for audio only.
        val renderersFactory = DefaultRenderersFactory(appContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)   // DV 8.1 / 8.4 → HEVC HDR10 / HLG fallback
                                              // (doc 03 §4, §7.2)

        // ---- Track selector ----
        val trackSelector = DefaultTrackSelector(appContext).apply {
            parameters = if (isTv) tvTrackSelectionParameters(this)
            else phoneTrackSelectionParameters(this)
        }

        // ---- Media source factory ----
        // Single DefaultMediaSourceFactory for the player — replaces the two
        // ad-hoc factories created in createMediaSource() today. All MKV flags
        // on MatroskaExtractor live here.
        val extractorsFactory = DefaultExtractorsFactory()
            .setSubtitleParserFactory(DefaultSubtitleParserFactory())
            // Do NOT set FLAG_DISABLE_SEEK_FOR_CUES — we want seeks on cues.
            .setMatroskaExtractorFlags(0)
            // Leave in-extractor subtitle transcoding ON (Media3 1.4+ default).

        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(
            appContext,
            /* baseDataSourceFactory = */ OkHttpDataSource.Factory(okHttpClient)
                .setDefaultRequestProperties(authHeaders()),
        )

        val mediaSourceFactory = DefaultMediaSourceFactory(appContext, extractorsFactory)
            .setDataSourceFactory(dataSourceFactory)

        // ---- Load control ----
        // Defaults in Media3 1.10.0 are 50s min / 50s max / 2.5s after rebuffer.
        // For 4K HDR (~40-80 Mbps) bump the "max" so we have a comfortable lead
        // on sustained loads without forcing the player to block. Values below
        // are conservative — verify on a Shield 4K HDR stream before widening.
        // (Default values cited from DefaultLoadControl.Builder in Media3 1.10.0
        // source; project-specific choice.)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs              = */ 60_000,
                /* maxBufferMs              = */ 120_000,
                /* bufferForPlaybackMs      = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // ---- AudioAttributes ----
        // CONTENT_TYPE_MOVIE is the Spatializer gate (doc 04 §7.4, §8.1).
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        // ---- Player ----
        return ExoPlayer.Builder(appContext, renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            // Keep the network alive on screen off — doc 07 §7 wake-lock gap.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // Doc 06 §1.3 — seamless-only is safe. Users who want forced
            // non-seamless switching opt in via the Android TV "Match content
            // frame rate" system setting and we drive it from HdrDisplayController.
            .setVideoChangeFrameRateStrategy(
                C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            )
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
    }

    private fun authHeaders(): Map<String, String> {
        val token = tokenManager.accessToken() ?: return emptyMap()
        return mapOf("Authorization" to "Bearer $token")
    }

    // Phone / TV track-selection presets defined in §4. Left as stubs here.
    private fun phoneTrackSelectionParameters(
        selector: DefaultTrackSelector
    ): TrackSelectionParameters = selector.buildUponParameters()
        .setTunnelingEnabled(false)
        .setAudioOffloadPreferences(
            AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                .build()
        )
        .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
        // Body filled in by §4.
        .build()

    private fun tvTrackSelectionParameters(
        selector: DefaultTrackSelector
    ): TrackSelectionParameters = selector.buildUponParameters()
        .setTunnelingEnabled(true)
        .setAudioOffloadPreferences(
            AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                .build()
        )
        .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
        // Body filled in by §4.
        .build()

    // --- MediaSource construction -------------------------------------------
    // Kept separate from createPlayer() because the ViewModel fetches a new
    // stream URL per play session; the Player instance is long-lived.
    fun createMediaItem(
        streamUrl: String,
        playMethod: PlayMethod,
        serverUrl: String,
        mediaId: String,
        subtitles: List<PlayerSubtitleInfo> = emptyList(),
    ): MediaItem {
        val absoluteUrl = buildAbsoluteUrl(serverUrl, streamUrl)
        val subtitleConfigurations =
            subtitleManager.buildSubtitleConfigurations(subtitles, serverUrl)
        val builder = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(absoluteUrl)
            .setSubtitleConfigurations(subtitleConfigurations)
        // Hint the MIME type so DefaultMediaSourceFactory selects the right
        // MediaSource without sniffing — matters for the HLS/progressive split.
        when (playMethod) {
            PlayMethod.DIRECT, PlayMethod.REMUX -> {
                // Progressive. Leave MIME unset and let the container sniff.
            }
            PlayMethod.TRANSCODE ->
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        return builder.build()
    }

    private fun buildAbsoluteUrl(serverUrl: String, streamUrl: String): String {
        val base = serverUrl.trimEnd('/')
        return if (streamUrl.startsWith("http://") || streamUrl.startsWith("https://")) {
            streamUrl
        } else {
            "$base/api/v1$streamUrl"
        }
    }
}
```

Key differences from the current `SiloPlayerFactory.kt`:

- The `OkHttpClient` is constructed **once**, inside the factory, and pooled across every
  playback session. The previous code built a fresh client per `createMediaSource` call
  (doc 07 §9).
- A single `DefaultMediaSourceFactory` is attached to the `ExoPlayer.Builder`. That is
  what `setMediaItem(...)` / `prepare()` expect, and it eliminates the per-`createMediaSource`
  `ProgressiveMediaSource.Factory` / `HlsMediaSource.Factory` instantiation.
- `DefaultExtractorsFactory().setMatroskaExtractorFlags(0)` explicitly documents our MKV
  flags. Today the code cannot reach them because `media3-extractor` isn't a declared
  dependency of `android-shared`.
- `setLoadControl(DefaultLoadControl.Builder()…)` raises the buffer window so 4K HDR
  progressive fetch has headroom without blocking the playback start. The values above
  are the minimum recommendation; doc 07 §8 flags the default as untuned.
- `setWakeMode(C.WAKE_MODE_NETWORK)`, `setSeekBackIncrementMs(10_000)`,
  `setSeekForwardIncrementMs(10_000)` cover the "Not set" bullets in doc 07 §2.
- The factory **does not** take `isTv` as a constructor arg. It derives it from the
  context, which also means the same injected factory works in the unified
  `MediaSessionService` without a separate phone / TV binding.
- `createMediaSource(...) → MediaSource` has been collapsed into `createMediaItem(...) → MediaItem`.
  The player consumes media items directly because the builder now has a
  `setMediaSourceFactory`.

`AuthenticatedDataSourceFactory.kt` in its current shape is no longer needed once the
factory owns the `OkHttpDataSource.Factory` itself. Keep the `RelativeUrlDataSource`
wrapping if you still need to resolve scheme-less server responses; it can move inside
the `DataSource.Factory` chain without changing behavior. Token refresh is covered in
§10 (step 5).

---

## 4. Track selection parameters — phone preset vs TV preset

Below are the two builder functions alluded to in §3. Both read from the existing
probes. Sources: doc 03 §7.4, doc 04 §7.3, doc 06 §2.4 Example A and B.

### 4.1 TV preset

```kotlin
@UnstableApi
private fun DefaultTrackSelector.ParametersBuilder.applyTvPreset(
    context: Context,
    audioCapabilityManager: AudioCapabilityManager,
): TrackSelectionParameters {
    val displayHdr = DisplayHdrProbe.probe(context)
    val displayModes = DisplayModeProbe.probe(context)
    val audioCaps = audioCapabilityManager.capabilities.value

    // Video MIME preference. DV first only if the panel + a DV decoder both
    // advertise support — otherwise keep HEVC on top so the selector's
    // decoder-fallback path kicks in (doc 03 §4 "Alternative MIME" fallback).
    val preferredVideoMimes = buildList {
        if (displayHdr.dolbyVisionProfiles.isNotEmpty()) add(MimeTypes.VIDEO_DOLBY_VISION)
        add(MimeTypes.VIDEO_H265)
        add(MimeTypes.VIDEO_AV1)
        add(MimeTypes.VIDEO_H264)
    }

    // Audio MIME preference. Only advertise encodings the sink can carry.
    // Channel count capped to the sink's max so we never select an 8ch track
    // into a 2ch output (doc 04 §4.2 + §6).
    val preferredAudioMimes = buildList {
        if ("truehd" in audioCaps.passthroughCodecs) add(MimeTypes.AUDIO_TRUEHD)
        if ("eac3_joc" in audioCaps.passthroughCodecs) add(MimeTypes.AUDIO_E_AC3_JOC)
        if ("ac4" in audioCaps.passthroughCodecs) add(MimeTypes.AUDIO_AC4)
        if ("eac3" in audioCaps.passthroughCodecs) add(MimeTypes.AUDIO_E_AC3)
        if ("ac3" in audioCaps.passthroughCodecs) add(MimeTypes.AUDIO_AC3)
        add(MimeTypes.AUDIO_AAC)
    }

    // Panel resolution cap. Leave uncapped if the panel didn't report a mode
    // (very rare on Android TV).
    val currentMode = displayModes.modes.firstOrNull { it.modeId == displayModes.currentModeId }
    if (currentMode != null) {
        setMaxVideoSize(currentMode.physicalWidth, currentMode.physicalHeight)
    }

    return setViewportSizeToPhysicalDisplaySize(/* orientationMayChange = */ false)
        .setPreferredVideoMimeTypes(*preferredVideoMimes.toTypedArray())
        .setPreferredAudioMimeTypes(*preferredAudioMimes.toTypedArray())
        .setMaxAudioChannelCount(audioCaps.maxChannels.coerceAtLeast(2))
        .setConstrainAudioChannelCountToDeviceCapabilities(true)
        .setPreferredAudioRoleFlags(C.ROLE_FLAG_MAIN)
        .setPreferredAudioLanguage(defaultAudioLanguage(context))
        .setPreferredTextLanguage(defaultTextLanguage(context))
        .setPreferredTextRoleFlags(C.ROLE_FLAG_SUBTITLE)
        .setSelectUndeterminedTextLanguage(false)
        .setAllowVideoMixedMimeTypeAdaptiveness(true)
        .setAllowVideoNonSeamlessAdaptiveness(false)   // TV: avoid black-screen switches
        .setTunnelingEnabled(true)                     // doc 05 §2, §3
        .setAudioOffloadPreferences(
            AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                .build()
        )
        .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
        .build()
}
```

### 4.2 Phone preset

```kotlin
@UnstableApi
private fun DefaultTrackSelector.ParametersBuilder.applyPhonePreset(
    context: Context,
    audioCapabilityManager: AudioCapabilityManager,
): TrackSelectionParameters {
    val displayHdr = DisplayHdrProbe.probe(context)
    val audioCaps = audioCapabilityManager.capabilities.value

    val preferredVideoMimes = buildList {
        // On phones with DV decoders (Pixel 8+, some Samsung/Sony) prefer DV;
        // otherwise fall through to HEVC HDR10.
        if (displayHdr.dolbyVisionProfiles.isNotEmpty()) add(MimeTypes.VIDEO_DOLBY_VISION)
        add(MimeTypes.VIDEO_H265)
        add(MimeTypes.VIDEO_AV1)
        add(MimeTypes.VIDEO_H264)
    }

    // Most phones output stereo. Spatialized Atmos on headphones uses JOC
    // with the Media3 Spatializer wrapper (doc 04 §8.2 + §7.3).
    val preferredAudioMimes = buildList {
        if ("eac3_joc" in audioCaps.passthroughCodecs || audioCaps.spatializerEnabled) {
            add(MimeTypes.AUDIO_E_AC3_JOC)
        }
        add(MimeTypes.AUDIO_AAC)
    }

    return setPreferredVideoMimeTypes(*preferredVideoMimes.toTypedArray())
        .setPreferredAudioMimeTypes(*preferredAudioMimes.toTypedArray())
        .setMaxAudioChannelCount(audioCaps.maxChannels.coerceAtLeast(2))
        .setConstrainAudioChannelCountToDeviceCapabilities(true)
        .setPreferredAudioLanguage(defaultAudioLanguage(context))
        .setPreferredTextLanguage(defaultTextLanguage(context))
        .setPreferredTextRoleFlags(C.ROLE_FLAG_SUBTITLE)
        .setSelectUndeterminedTextLanguage(false)
        .setViewportSizeToPhysicalDisplaySize(/* orientationMayChange = */ true)
        .setTunnelingEnabled(false)                    // doc 06 §1.4
        .setAudioOffloadPreferences(
            AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                .setIsGaplessSupportRequired(false)
                .setIsSpeedChangeSupportRequired(false)
                .build()
        )
        .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
        .build()
}
```

### 4.3 User-driven overrides

The existing `SubtitleManager.selectSubtitle(player, index)` and
`AudioTrackManager.selectAudioTrack(player, index)` already use `TrackSelectionOverride`
correctly. Keep them — they complement the preferences above, they do not replace them.
Make two small adjustments aligned with doc 02 §6 and doc 06 §2.1:

- `SubtitleManager.selectSubtitle(player, -1)` already issues `setTrackTypeDisabled(TRACK_TYPE_TEXT, true)`;
  also `clearOverridesOfType(C.TRACK_TYPE_TEXT)` to avoid a stale override sticking after
  the user disables subtitles.
- Forced-only subtitle handling: when the server marks a subtitle track `forced = true`,
  `SubtitleManager.buildSubtitleConfigurations` sets `SELECTION_FLAG_FORCED` correctly
  (`SubtitleManager.kt:39-41`). The default `setSelectUndeterminedTextLanguage(false)` in
  the preset, combined with `preferredTextRoleFlags = ROLE_FLAG_SUBTITLE`, causes
  unforced subtitles to only play if the user picks them — matches standard behavior.

### 4.4 Language defaults

Pull from the `ProfileRepository` / user settings. If the user has not set one, default
to the device locale:

```kotlin
private fun defaultAudioLanguage(context: Context): String =
    context.resources.configuration.locales[0].language.ifBlank { "en" }
private fun defaultTextLanguage(context: Context): String =
    defaultAudioLanguage(context)
```

`setPreferredAudioLanguage` and `setPreferredTextLanguage` accept an ISO 639-1 two-letter
code (`"en"`), ISO 639-2/T three-letter code (`"eng"`), or a BCP 47 tag. Media3 will
match heuristically against `Format.language`. Doc 06 §2.1 verified against the 1.10.0
source.

---

## 5. Playback lifecycle and MediaSessionService

The goal here is to fix doc 07's ranked blocker #6 ("two uncoordinated ExoPlayer
instances"). The approach follows doc 01 §6 and the Media3 background-playback guide.

### 5.1 Move the service into `android-shared`

Rename `androidApp/.../service/PlaybackService.kt` to
`android-shared/.../common/player/SiloPlaybackService.kt` so both the phone and TV
manifests can declare the same service class:

```kotlin
package com.continuum.app.common.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.koin.android.ext.android.inject

@UnstableApi
class SiloPlaybackService : MediaSessionService() {

    private val playerFactory: SiloPlayerFactory by inject()
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player: ExoPlayer = playerFactory.createPlayer()
        val sessionActivityIntent: PendingIntent? =
            // Intent launched when the user taps the media notification.
            // Filled in by the phone/TV app — below is a placeholder.
            packageManager
                .getLaunchIntentForPackage(packageName)
                ?.let { intent ->
                    PendingIntent.getActivity(
                        this, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }
        mediaSession = MediaSession.Builder(this, player)
            .apply { sessionActivityIntent?.let { setSessionActivity(it) } }
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
```

Manifest entries were given in §1.4; both apps point at this shared class.

### 5.2 Bind the Compose UI via `MediaController`

Replace the `remember { playerFactory.createPlayer() }` in `PlayerScreen.kt:65-67` (and
the corresponding `TvPlayerScreen.kt:89`) with a `MediaController` that connects to the
service's session. Outline (full binding is in the Media3 background-playback guide
linked from doc 01 §6):

```kotlin
@OptIn(UnstableApi::class)
@Composable
fun rememberMediaController(): State<MediaController?> {
    val context = LocalContext.current
    val controllerState = remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(
            context,
            ComponentName(context, SiloPlaybackService::class.java),
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controllerState.value = future.get() },
            MoreExecutors.directExecutor(),
        )
        onDispose {
            controllerState.value?.release()
            controllerState.value = null
            MediaController.releaseFuture(future)
        }
    }
    return controllerState
}
```

`PlayerScreen` / `TvPlayerScreen` then use the controller for `setMediaItem`, `prepare`,
`seekTo`, etc. The controller exposes the `Player` interface so existing event listener
code (`onIsPlayingChanged`, `onTracksChanged`, `onVideoSizeChanged`) does not change.

### 5.3 Foreground service notification

`DefaultMediaNotificationProvider` handles this automatically once `MediaMetadata` is
populated (title, subtitle, artwork URI). The `PlayerViewModel` already computes
`displayTitle` / `displaySubtitle` (`PlayerViewModel.kt:124-127, :569-576`). Populate
`MediaMetadata` on the `MediaItem`:

```kotlin
val item = playerFactory.createMediaItem(
    streamUrl = uiState.streamUrl!!,
    playMethod = uiState.playMethod!!,
    serverUrl = uiState.serverUrl,
    mediaId = uiState.contentId,
    subtitles = uiState.subtitleTracks,
).buildUpon()
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(uiState.title)
            .setSubtitle(uiState.subtitle)
            .setArtworkUri(posterUri(uiState.contentId))
            .build()
    )
    .build()
```

### 5.4 PiP and background playback

The phone manifest already declares `supportsPictureInPicture="true"` and
`configChanges="orientation|screenSize|screenLayout|keyboardHidden"`. The PiP helper
(`PictureInPictureHelper.updatePipParams(activity)`) must be re-invoked when
`videoWidth/height` becomes known (doc 07 §9 "PiP aspect ratio may stay 16:9"). Switch
to a listener-driven pattern:

```kotlin
player.addListener(object : Player.Listener {
    override fun onVideoSizeChanged(videoSize: VideoSize) {
        if (activity != null && videoSize.width > 0 && videoSize.height > 0) {
            pipHelper.updatePipParams(activity, videoSize.width, videoSize.height)
        }
    }
})
```

For background audio (music-only content), the `MediaSessionService` on its own keeps
the player running when the Activity is destroyed.

---

## 6. Per-track pre-flight

Fires on `onTracksChanged` / `onMediaItemTransition`. Drives two decisions: **display-mode
switching** (TV only) and **unplayability detection** (both platforms). Sources: doc 02
§9, doc 03 §7.3, doc 06 §2.3.

```kotlin
@OptIn(UnstableApi::class)
private class PlaybackPreflightListener(
    private val player: Player,
    private val isTv: Boolean,
    private val hdrDisplayController: HdrDisplayController?,
    private val onUnsupported: (reason: String) -> Unit,
) : Player.Listener {

    override fun onTracksChanged(tracks: Tracks) {
        val videoGroup = tracks.groups.firstOrNull {
            it.type == C.TRACK_TYPE_VIDEO && it.isSelected
        }
        val audioGroup = tracks.groups.firstOrNull {
            it.type == C.TRACK_TYPE_AUDIO && it.isSelected
        }

        val videoFormat = videoGroup?.let { group ->
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }
                ?.let { group.getTrackFormat(it) }
        }
        val audioFormat = audioGroup?.let { group ->
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }
                ?.let { group.getTrackFormat(it) }
        }

        videoFormat?.let { logVideo(it) }
        audioFormat?.let { logAudio(it) }

        // DV Profile 7: unplayable on any Android device (doc 02 §4.1, doc 03 §4).
        val codecs = videoFormat?.codecs.orEmpty()
        if (codecs.startsWith("dvhe.07") || codecs.startsWith("dvh1.07")) {
            onUnsupported("Dolby Vision Profile 7 requires server-side remux to 8.1.")
            return
        }

        // All video and audio groups report FORMAT_UNSUPPORTED_TYPE → nothing
        // on this container can play. The selector has already tried every
        // renderer capability, so ask the server for transcoded HLS.
        val anyVideoPlayable = tracks.groups.any {
            it.type == C.TRACK_TYPE_VIDEO && (0 until it.length).any(it::isTrackSupported)
        }
        val anyAudioPlayable = tracks.groups.any {
            it.type == C.TRACK_TYPE_AUDIO && (0 until it.length).any(it::isTrackSupported)
        }
        if (!anyVideoPlayable) {
            onUnsupported("No renderer can decode the video track.")
            return
        }
        if (!anyAudioPlayable) {
            onUnsupported("No renderer can decode / passthrough the audio track.")
            return
        }

        // Only TV drives display-mode switching — doc 07 §5 currently TV-only.
        if (isTv && hdrDisplayController != null && videoFormat != null) {
            val frameRate = videoFormat.frameRate
            val width = videoFormat.width
            val height = videoFormat.height
            if (frameRate > 0f && width > 0 && height > 0) {
                hdrDisplayController.applyForMedia(width, height, frameRate)
            }
        }
    }

    private fun logVideo(f: Format) {
        val color = f.colorInfo
        val isHdr = ColorInfo.isTransferHdr(color)
        val transfer = when (color?.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> "PQ"
            C.COLOR_TRANSFER_HLG -> "HLG"
            C.COLOR_TRANSFER_SDR -> "SDR"
            else -> "unknown"
        }
        Log.i(
            "Preflight",
            "video mime=${f.sampleMimeType} codecs=${f.codecs} " +
                "size=${f.width}x${f.height}@${f.frameRate} " +
                "hdr=$isHdr transfer=$transfer bit=${color?.lumaBitdepth}"
        )
    }

    private fun logAudio(f: Format) {
        val atmos = f.sampleMimeType in setOf(
            MimeTypes.AUDIO_E_AC3_JOC,
            MimeTypes.AUDIO_TRUEHD,
            MimeTypes.AUDIO_AC4,
        )
        Log.i(
            "Preflight",
            "audio mime=${f.sampleMimeType} codecs=${f.codecs} " +
                "ch=${f.channelCount} sr=${f.sampleRate} atmos=$atmos"
        )
    }
}
```

`onUnsupported` is wired back to the ViewModel, which in turn asks the server for a
transcoded HLS variant (`PlayerViewModel.handleSessionStarted` already has the branch
for `PlayMethod.TRANSCODE` — reuse it).

---

## 7. Fallback matrix

Concrete code paths per track type, synthesized from doc 05 §4 and doc 02 §9.

```kotlin
sealed class Playability {
    object Ok : Playability()
    data class RequiresServerRemux(val reason: String) : Playability()
    data class RequiresServerTranscode(val reason: String) : Playability()
    data class Unplayable(val reason: String) : Playability()
}

@OptIn(UnstableApi::class)
fun resolveAudioPath(
    format: Format,
    audioCaps: AudioCapabilities,
    fallbackEnabled: Boolean,
    attrs: AudioAttributes,
): Playability {
    val mime = format.sampleMimeType ?: return Playability.Unplayable("no mime")

    // Universally-decodable local codecs.
    if (mime in setOf(
            MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_FLAC,
            MimeTypes.AUDIO_OPUS, MimeTypes.AUDIO_VORBIS,
            MimeTypes.AUDIO_MPEG,
        )
    ) return Playability.Ok

    // Atmos family: require passthrough of the bearing encoding.
    if (mime == MimeTypes.AUDIO_TRUEHD) {
        return if (audioCaps.supportsEncoding(C.ENCODING_DOLBY_TRUEHD)) Playability.Ok
        else Playability.RequiresServerTranscode("No TrueHD passthrough on route.")
    }
    if (mime == MimeTypes.AUDIO_E_AC3_JOC) {
        // JOC can gracefully passthrough as plain E-AC-3 (doc 04 §3 + §7.4).
        return if (audioCaps.supportsEncoding(C.ENCODING_E_AC3_JOC) ||
            audioCaps.supportsEncoding(C.ENCODING_E_AC3)
        ) Playability.Ok
        else Playability.RequiresServerTranscode("No E-AC-3 passthrough on route.")
    }
    if (mime == MimeTypes.AUDIO_AC4) {
        return if (audioCaps.supportsEncoding(C.ENCODING_AC4)) Playability.Ok
        else Playability.RequiresServerTranscode("No AC-4 passthrough on route.")
    }
    // DTS family — AOSP has no SW decoder. Require passthrough.
    if (mime in setOf(MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS)) {
        return if (audioCaps.isPassthroughPlaybackSupported(format, attrs)) Playability.Ok
        else Playability.RequiresServerTranscode("No DTS passthrough on route.")
    }
    // AC-3 / E-AC-3 (non-JOC): local decoder on most devices, or passthrough.
    if (mime in setOf(MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3)) {
        return Playability.Ok
    }
    return Playability.Unplayable("unhandled audio mime=$mime")
}

@OptIn(UnstableApi::class)
fun resolveVideoPath(format: Format, displayHdr: HdrCapabilities): Playability {
    val codecs = format.codecs.orEmpty()
    if (codecs.startsWith("dvhe.07") || codecs.startsWith("dvh1.07")) {
        return Playability.RequiresServerRemux(
            "Dolby Vision Profile 7 cannot be decoded on Android; remux to 8.1."
        )
    }
    // HDR → SDR pre-Android 13: the player will attempt tone-map, but some
    // phones wash out. On that path let the preflight veto the track rather
    // than falling back to transcode — doc 03 §6.
    return Playability.Ok
}
```

When the resolver returns `RequiresServerRemux` or `RequiresServerTranscode`, the
ViewModel calls `playbackSessionManager.startTranscode(...)` with the appropriate
`TranscodeStartRequest` — `PlayerViewModel.handleSessionStarted` already has most of
this logic (`PlayerViewModel.kt:209-288`). Wire preflight → transcode as a fallback on
`onPlayerError` too, so a runtime codec failure (rare but possible with flaky OEM
decoders) recovers automatically on the next retry.

User-facing messages for profile-7 remux:

> "This release uses Dolby Vision Profile 7, which Android cannot decode. Ask the
> server admin to re-mux to Profile 8.1, or switch the app's playback preferences to
> 'Compatibility' to fall back to HDR10."

---

## 8. Diagnostics and logging

### 8.1 `AnalyticsListener`

Register one on the shared player. At minimum:

```kotlin
@OptIn(UnstableApi::class)
class SiloAnalyticsListener(
    private val onCodecChosen: (rendererType: Int, codec: String) -> Unit,
    private val onDroppedFrames: (count: Int, elapsedMs: Long) -> Unit,
    private val onAudioUnderrun: (bufferSizeMs: Int) -> Unit,
) : AnalyticsListener {
    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime, decoderName: String,
        initializedTimestampMs: Long, initializationDurationMs: Long,
    ) = onCodecChosen(C.TRACK_TYPE_VIDEO, decoderName)

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime, decoderName: String,
        initializedTimestampMs: Long, initializationDurationMs: Long,
    ) = onCodecChosen(C.TRACK_TYPE_AUDIO, decoderName)

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long,
    ) = onDroppedFrames(droppedFrames, elapsedMs)

    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long,
    ) = onAudioUnderrun(bufferSizeMs.toInt())
}
```

Also register Media3's built-in `EventLogger` under a BuildConfig.DEBUG gate while
bringing this up (`logcat -s EventLogger`). That alone often identifies a wrong codec
pick.

### 8.2 Debug overlay (phone `PlayerOverlay.kt`, TV `TvPlayerControls.kt`)

Surface on a dev-build-only bottom chip, not user-visible:

- Video: `format.sampleMimeType`, `format.codecs`, `width × height @ frameRate`, HDR
  transfer, `hdrStaticInfo` present y/n, Dolby Vision profile (parsed from
  `Format.codecs`).
- Audio: `sampleMimeType`, `channelCount`, `sampleRate`, `encoding` chosen by the sink
  (Media3 exposes this via `AudioSink.Listener` — relay through
  `SiloAnalyticsListener.onAudioSinkInitialized`).
- Decoder: the `decoderName` from `onAudioDecoderInitialized` / `onVideoDecoderInitialized`.
- Dropped frames / bandwidth: cumulative over the current session.

### 8.3 Telemetry to the Silo server

Report after the session ends (non-blocking POST to `/playback/telemetry` or equivalent):

- Session id, content id, file version id.
- Chosen video codec / HDR type / DV profile.
- Chosen audio codec / channel count / passthrough y/n.
- Dropped-frame count and longest rebuffer duration.
- Any `PlaybackException.errorCode` values observed.
- `HdrCapabilities` snapshot at session start.
- `AudioPassthroughCapabilities` snapshot at session start.

This pairs with the existing `ClientCodecCapabilities` the server already receives.

---

## 9. Testing checklist

### 9.1 Content matrix

Maintain a small, stable test library on the dev server. Each row is one MKV; expected
result is what the client should do on the test device.

| Test file | Container | Video | Audio | Phone (Pixel 8 A14) | TV (Chromecast GTV 4K) |
|---|---|---|---|---|---|
| `planet-earth-ii-s01e01-1080p.mkv` | MKV | HEVC Main10 HDR10 | E-AC-3 5.1 | Direct-play, HEVC HDR10; passthrough E-AC-3 if connected to AVR else SW decode to stereo | Direct-play, HEVC HDR10; E-AC-3 passthrough; tunneled |
| `avp-dv81-truehd-4k.mkv` | MKV | HEVC DV Profile 8.1 | TrueHD 7.1 Atmos | DV if Pixel 8+; else HEVC HDR10 fallback. Audio: JOC-level not present → transcode to AAC 5.1 | DV 8.1 direct-play, TrueHD passthrough if AVR advertises, else transcode |
| `dune-dv5-eac3joc.mkv` | MKV | HEVC DV Profile 5 (no HDR10 base) | E-AC-3 JOC | If device has no DV decoder: unplayable — requires server transcode (doc 03 §4) | DV 5 direct-play if panel supports; JOC passthrough |
| `cosmos-av1-hdr10-flac.mkv` | MKV | AV1 Main10 HDR10 | FLAC 5.1 | AV1 HDR10 direct-play; FLAC SW decode, downmix to stereo | AV1 HDR10 direct-play; FLAC SW decode; tunneled |
| `nature-av1-hdr10p-opus.mkv` | MKV | AV1 Main10 HDR10+ | Opus stereo | AV1 HDR10+ direct-play on SoCs with HDR10+ decode; else HDR10. Opus SW decode | Same |
| `bluray-dv7-truehd.mkv` | MKV | HEVC DV Profile 7 (MEL+FEL) | TrueHD Atmos | Unsupported — user-facing "requires Profile 8.1 remux" (doc 02 §4.1) | Unsupported — same message |
| `anime-h264-aac.mkv` | MKV | H.264 Main | AAC-LC stereo | Direct-play trivially | Direct-play trivially |
| `docs-hevc-ac3-pgs.mkv` | MKV | HEVC Main | AC-3 5.1 + PGS subs | Direct-play; PGS bitmap subs render (doc 02 §6.1 — watch for aspect-ratio bug #2849) | Direct-play; PGS + AC-3 passthrough |

### 9.2 Device coverage

| Device | OS | Notes |
|---|---|---|
| Pixel 8 | Android 14 | DV decoder + JOC spatializer. Target phone. |
| Pixel 6 | Android 13 | Older DV decoder, no AV1 HDR10+. |
| Samsung S23 | Android 14 | Known JOC route quirks with some Bluetooth codecs. |
| Chromecast with Google TV 4K | Android TV 12L | Flagship TV target; Atmos eARC if paired with AVR. |
| Nvidia Shield Pro (2019) | Android TV 11 | Broadest codec coverage — DV P5/P7 attempted, TrueHD passthrough, AC-4. |
| Fire TV 4K Max | Fire OS 8 / Android 11 | JOC on some SoCs, no DV on others. |
| Xiaomi Mi Box S | Android TV 9 | Low-end baseline. Watch for tunneled-playback underruns. |

### 9.3 Test invariants per row

For every row, verify in logcat / analytics:

1. `DefaultTrackSelector` chose the expected codec (check
   `onVideoDecoderInitialized.decoderName`).
2. HDR transfer matches: `Format.colorInfo.colorTransfer` matches the source.
3. For Atmos rows, the sink opened with the expected `ENCODING_*` (audio sink init
   event carries it).
4. `HdrDisplayController` applied the expected `Display.Mode` for 24 fps sources on TV.
5. No dropped frames over the first 30 s (target: < 5 per 30 s on 4K HDR).

---

## 10. Migration plan

Each PR is atomic, reviewable on its own, and addresses doc 07 §8 in ranked order.

1. **PR #1 — Gradle: add missing Media3 modules.** §1.1 + §1.2. No code changes beyond
   build files. Verifies the extractor / container / common-ktx paths compile on phone,
   TV, and shared.
2. **PR #2 — Pooled OkHttp + `DefaultMediaSourceFactory`.** §3 subset. Replace the
   per-call `AuthenticatedDataSourceFactory` construction with a factory-scoped
   `OkHttpClient`. Addresses doc 07 §9 "new `OkHttpClient` per player session" and
   blocker #5.
3. **PR #3 — Capability-aware `SiloPlayerFactory`.** §3 full. Adds
   `DefaultExtractorsFactory`, `DefaultLoadControl` tuning, `setWakeMode`, seek
   increments, `setVideoChangeFrameRateStrategy`. Removes the `isTv` constructor arg.
4. **PR #4 — Phone and TV `TrackSelectionParameters` presets.** §4. Wires
   `DisplayHdrProbe` / `AudioCapabilityManager` / `DisplayModeProbe` into the builder.
   Addresses blocker #3.
5. **PR #5 — Consolidate onto `MediaSessionService`.** §5. Move `PlaybackService` into
   `android-shared`, rename to `SiloPlaybackService`, register in both manifests,
   replace the direct `createPlayer()` in `PlayerScreen` and `TvPlayerScreen` with a
   `MediaController`. Deletes the second ExoPlayer in the service. Addresses blocker #6.
6. **PR #6 — HDR display-mode switching on phone.** §2 + §6. Reuse
   `HdrDisplayController` on phone paths where `Display.supportedModes.size > 1`
   (Pixel 6+, S22+). Addresses the cosmetic gap in doc 07 §8.
7. **PR #7 — FFmpeg extension build (optional, requires product approval).** §1.3.
   Ship as a private AAR and flip the renderers mode to
   `EXTENSION_RENDERER_MODE_PREFER` for audio. Guard behind a BuildConfig flag so
   the extension can be toggled per APK flavour.
8. **PR #8 — Preflight + HLS fallback.** §6 + §7. Add `PlaybackPreflightListener` and
   the `Playability` resolver. Wire `onUnsupported` into the existing
   `PlayerViewModel.handleSessionStarted` transcode branch. Addresses blocker #1 for
   the lossless-audio-no-passthrough case.
9. **PR #9 — Analytics + debug overlay.** §8. Gated on BuildConfig.DEBUG.
10. **PR #10 — Spatializer listener and per-encoding channel-cap probe.**
    §2.3. Adds the `Spatializer.OnSpatializerStateChangedListener`.
    For the per-encoding channel-count concern, replace the stub with an
    `AudioTrack.isDirectPlaybackSupported` probe per encoding rather than
    attempting to call a non-existent Media3 public API (see doc 04 §4.2
    and the correction appended to doc 07 §9). If Media3 exposes a public
    per-encoding accessor in a later release, migrate to that.

A good stopping point after PRs 1–5 is "MKV direct-play of HDR10 / HEVC / E-AC-3 works
on phone and TV with one player"; after 6–8 it is "DV / Atmos / fallback all
covered"; 9–10 are polish.

---

## 11. Open questions / follow-ups

- **Should we ship the FFmpeg audio extension?** Trade-off between APK size (+4-8 MB
  depending on codec set) and the ability to decode TrueHD on devices without
  passthrough. Needs product call. Until then, transcode is the fallback.
- **Do we expose a "prefer HDR10 over HDR10+" user setting?** Some older HDR10+
  decoders produce worse output than HDR10 on the same device. Media3 does not let us
  gate this cleanly (doc 06 §2.1 "no explicit HDR-preference setter on
  `TrackSelectionParameters.Builder` in 1.10.0"). Needs a custom
  `TrackSelectionOverride` informed by user preference.
- **Do we enforce seamless-only display mode switching on TV, or expose the "Always"
  opt-in?** Doc 06 §1.3 flags the device-compat issues with non-seamless switching.
  Default seamless-only per `setVideoChangeFrameRateStrategy(ONLY_IF_SEAMLESS)` in §3;
  product should decide whether to expose the override.
- **Server-side: does the resolver already veto Profile 7 sources?** The existing
  `PlaybackCapabilityDetector` sends the DV profile list; confirm the server does not
  pick a Profile 7 file for the client when only 5/8 are advertised. If it does, the
  preflight unsupported message in §7 is a safety net rather than the primary guard.
- **Token refresh on the media data source.** The data-source chain does not see the
  app's 401 refresh logic (doc 07 §9). Options: (a) recreate the `MediaSource` on
  401, (b) wrap the OkHttp chain in an Interceptor that mirrors the Ktor
  `AuthInterceptorImpl` behavior. Option (b) is preferred for long-session parity.
- **PGS subtitle aspect-ratio bug (issue #2849).** Doc 02 §6.1 notes the fix has not
  shipped as of 1.10.0. Evaluate writing a custom `SubtitleView`-style renderer if
  user reports start coming in.
- **Android TV minimum API.** Current `minSdk = 26` excludes some early Mi Box units.
  Confirm the business requirement before relying on APIs ≥ 29 for offload / passthrough
  probing.
- **DRM.** Out of scope today; the `MediaItem` builder above intentionally has no
  `DrmConfiguration`. If Widevine L1 support is added later, the same
  `DefaultMediaSourceFactory` will carry the DRM session manager.

---

## Sources

- Media3 1.10.0 tag — https://github.com/androidx/media/tree/1.10.0
- Media3 1.10.0 RELEASENOTES — https://raw.githubusercontent.com/androidx/media/1.10.0/RELEASENOTES.md
- Media3 `AudioCapabilities` 1.10.0 — https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioCapabilities.java
- Media3 `TrackSelectionParameters` 1.10.0 — https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/TrackSelectionParameters.java
- Media3 `DefaultTrackSelector` 1.10.0 — https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java
- Media3 `ExoPlayer` 1.10.0 — https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java
- Media3 `DefaultRenderersFactory` 1.10.0 — https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultRenderersFactory.java
- Media3 background playback guide — https://developer.android.com/media/media3/session/background-playback

## Validation log

- verified: all `TrackSelectionParameters.Builder` setters used in §4 preset functions (`setPreferredVideoMimeTypes`, `setPreferredAudioMimeTypes`, `setMaxAudioChannelCount`, `setConstrainAudioChannelCountToDeviceCapabilities`, `setPreferredAudioRoleFlags`, `setPreferredAudioLanguage`, `setPreferredTextLanguage`, `setPreferredTextRoleFlags`, `setSelectUndeterminedTextLanguage`, `setAllowVideoMixedMimeTypeAdaptiveness`, `setAllowVideoNonSeamlessAdaptiveness`, `setTunnelingEnabled`, `setAudioOffloadPreferences`, `setAllowInvalidateSelectionsOnRendererCapabilitiesChange`) are present on the 1.10.0 surface (in `TrackSelectionParameters.Builder` or inherited in `DefaultTrackSelector.Parameters.Builder`). See doc 06 §2.1 for the split.
- verified: `ExoPlayer.Builder` setters used in §3 (`setRenderersFactory`, `setTrackSelector`, `setMediaSourceFactory`, `setLoadControl`, `setAudioAttributes`, `setHandleAudioBecomingNoisy`, `setWakeMode`, `setVideoChangeFrameRateStrategy`, `setSeekBackIncrementMs`, `setSeekForwardIncrementMs`) are all present in 1.10.0 `ExoPlayer.java`. (doc 01 Validation log cross-references)
- verified: `AudioOffloadPreferences` constants (`AUDIO_OFFLOAD_MODE_DISABLED = 0`, `_ENABLED = 1`, `_REQUIRED = 2`) and Builder setters (`setAudioOffloadMode`, `setIsGaplessSupportRequired`, `setIsSpeedChangeSupportRequired`) match doc 05 §3.
- verified: `DefaultExtractorsFactory().setMatroskaExtractorFlags(0)` and `setSubtitleParserFactory(DefaultSubtitleParserFactory())` both work on 1.10.0 — methods present (doc 02 §3).
- corrected: §2.3 "Remove the stubbed `getMaxSupportedChannelCountForPassthrough` extension... Media3 1.10 exposes the real API" → Media3 1.10.0 does not expose a public instance method by that name; the method is a `public static` on the private `Api29` inner class. Text updated to reflect `AudioTrack.isDirectPlaybackSupported` as the practical replacement.
- corrected: §10 PR #10 — rewrote the cleanup item to match the correction above.
- verified: `DefaultLoadControl.Builder` and its setters (`setBufferDurationsMs`, `setPrioritizeTimeOverSizeThresholds`) are part of `androidx.media3.exoplayer`; the 60s / 120s tuning in §3 is a project choice, not an API claim.
- verified: `MediaSession.Builder(context, player).setSessionActivity(intent).build()` is the valid 1.10.0 signature for the session construction in §5.1; `MediaSessionService.onGetSession(ControllerInfo)` and `onTaskRemoved(Intent?)` are overridable entry points.
- verified: `MediaController.Builder(context, SessionToken).buildAsync()` returns a `ListenableFuture<MediaController>`; the Compose binding pattern in §5.2 is correct for 1.10.0.
- still unverified: `DefaultLoadControl` default buffer values mentioned in §3 ("50s min / 50s max / 2.5s after rebuffer") — Media3's defaults evolve across releases; treat as indicative and verify against `DefaultLoadControl.java` 1.10.0 if the specific numbers matter for a shipping decision.
- still unverified: per-device Atmos / DV test matrix in §9.2 — requires hardware runs to populate "pass / fail" cells.
