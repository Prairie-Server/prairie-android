Document version: Media3 1.10.0

# 06 - Android TV and Track Selection

Scope: how the Silo Android phone/tablet app and the `androidTvApp` should detect the TV form factor, probe display/audio capabilities on an HDMI sink, drive refresh-rate matching, and configure `DefaultTrackSelector` + `DefaultRenderersFactory` so MKV content with HDR/DV/Atmos is picked correctly on set-tops (Chromecast with Google TV, Nvidia Shield, Fire TV, Mi Box S, recent Sony/TCL/Hisense TVs) while still doing the right thing on phones.

Everything below assumes `androidx.media3:media3-exoplayer:1.10.0` (see `gradle/libs.versions.toml`).

---

## Part 1 — Android TV specifics

### 1.1 Detecting "TV mode"

There are three complementary signals; use them together:

1. `UiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION` at runtime. This is the canonical check: it returns one of `UI_MODE_TYPE_NORMAL`, `UI_MODE_TYPE_DESK`, `UI_MODE_TYPE_CAR`, `UI_MODE_TYPE_TELEVISION`, `UI_MODE_TYPE_APPLIANCE`, `UI_MODE_TYPE_WATCH`, `UI_MODE_TYPE_VR_HEADSET`. (https://developer.android.com/reference/android/app/UiModeManager, https://developer.android.com/training/tv/start/hardware)
2. `PackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)` — true on any device that implements the Leanback UI profile.
3. Manifest `<uses-feature>` declarations on the Android TV APK:

```xml
<uses-feature android:name="android.software.leanback" android:required="true" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
```

On a shared APK (phone/TV) keep `android.software.leanback` with `required="false"` and gate TV launcher entries behind a Leanback-launcher intent filter. (https://developer.android.com/training/tv/start/start)

Silo ships two distinct APKs (`androidApp/` and `androidTvApp/`), so the hardware check is usually only needed inside shared code in `android-shared/` that wants to adjust buffer/refresh-rate behavior. A tiny helper:

```kotlin
// android-shared/.../TvMode.kt
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

Impact on player behavior:

- Larger buffers — set-tops have more RAM than phones and users expect buffering to be invisible. Bump `DefaultLoadControl` buffer sizes (see doc 03).
- Tunneling — enabled only on TV by default (see Part 1.4 and the passthrough/tunneling doc).
- Refresh-rate matching — only meaningful on displays with multiple modes; effectively TV-only.
- Surface sizing — on TV the window always fills a real display, so `setViewportSizeToPhysicalDisplaySize(true)` is safe. (https://developer.android.com/reference/androidx/media3/common/TrackSelectionParameters.Builder#setViewportSizeToPhysicalDisplaySize(boolean))

### 1.2 Display capability detection on TV

An HDMI stick such as Chromecast with Google TV typically has exactly one `Display`; the stick's HDMI output is that display. An Android TV with a built-in panel is also a single `Display`. Multi-display is effectively absent on Android TV, so most code can simply pick `DisplayManager.getDisplay(Display.DEFAULT_DISPLAY)` (or use the `Display` attached to the player's `Activity` / `SurfaceView`). (https://developer.android.com/reference/android/view/Display)

HDR probe:

```kotlin
import android.hardware.display.DisplayManager
import android.view.Display

fun probeHdr(context: Context): Set<Int> {
    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptySet()
    val caps = display.hdrCapabilities ?: return emptySet()
    // getSupportedHdrTypes() returns an IntArray of Display.HdrCapabilities.HDR_TYPE_* values.
    return caps.supportedHdrTypes.toSet()
}

// HDR type constants (android.view.Display.HdrCapabilities):
//   HDR_TYPE_DOLBY_VISION   = 1
//   HDR_TYPE_HDR10          = 2
//   HDR_TYPE_HLG            = 3
//   HDR_TYPE_HDR10_PLUS     = 4
```

(https://developer.android.com/reference/android/view/Display.HdrCapabilities)

Refresh-rate / resolution probe:

```kotlin
fun probeDisplayModes(display: Display): List<Display.Mode> =
    display.supportedModes.toList() // each has getModeId(), getPhysicalWidth(), getPhysicalHeight(), getRefreshRate()

fun currentMode(display: Display): Display.Mode = display.mode
```

`Display.Mode.getRefreshRate()` returns a `Float` in Hz. `getPhysicalWidth()` / `getPhysicalHeight()` are the panel's real pixel count (4K panel → 3840x2160 regardless of render resolution). (https://developer.android.com/reference/android/view/Display.Mode)

Audio capabilities at the HDMI sink:

```kotlin
import android.media.AudioDeviceInfo
import android.media.AudioManager

fun hdmiSinkCaps(audio: AudioManager): AudioDeviceInfo? =
    audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
        it.type == AudioDeviceInfo.TYPE_HDMI ||
            it.type == AudioDeviceInfo.TYPE_HDMI_ARC ||
            it.type == AudioDeviceInfo.TYPE_HDMI_EARC
    }

// From a found AudioDeviceInfo:
//   getEncodings() -> IntArray of AudioFormat.ENCODING_* constants
//                     (includes ENCODING_AC3, ENCODING_E_AC3, ENCODING_E_AC3_JOC, ENCODING_DOLBY_TRUEHD, ENCODING_DTS, ENCODING_DTS_HD, ENCODING_DTS_UHD_P2, etc.)
//   getChannelCounts() -> IntArray of supported channel counts at that device
```

(https://developer.android.com/reference/android/media/AudioManager#getDevices(int), https://developer.android.com/reference/android/media/AudioDeviceInfo)

For the typical "AVR is on the other end of ARC/eARC" use-case you also want to react when the user powers the receiver on mid-session:

```kotlin
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager

class HdmiPlugReceiver(val onChanged: (plugged: Boolean, encodings: IntArray?, maxChannels: Int) -> Unit)
    : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != AudioManager.ACTION_HDMI_AUDIO_PLUG) return
        val plugged = intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 1
        val encodings = intent.getIntArrayExtra(AudioManager.EXTRA_ENCODINGS)
        val maxCh = intent.getIntExtra(AudioManager.EXTRA_MAX_CHANNEL_COUNT, 2)
        onChanged(plugged, encodings, maxCh)
    }
}

// Register:
val filter = IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG)
context.registerReceiver(receiver, filter) // sticky: current state delivered immediately
```

`ACTION_HDMI_AUDIO_PLUG` is a sticky broadcast carrying `EXTRA_AUDIO_PLUG_STATE`, `EXTRA_ENCODINGS`, and `EXTRA_MAX_CHANNEL_COUNT`. (https://developer.android.com/reference/android/media/AudioManager#ACTION_HDMI_AUDIO_PLUG)

Media3's own `AudioCapabilities` wraps this for you and is what `DefaultAudioSink` uses internally:

```kotlin
// androidx.media3.exoplayer.audio.AudioCapabilities
val caps = AudioCapabilities.getCapabilities(
    context,
    AudioAttributes.DEFAULT,
    /* routedDevice = */ null,
    /* spatializerChannelMasks = */ emptyList()
)
caps.supportsEncoding(C.ENCODING_E_AC3_JOC)
caps.maxChannelCount
```

(https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioCapabilities.java)

### 1.3 Refresh-rate matching

Why: a 23.976 fps movie on a 60 Hz display forces the compositor to do 3:2 pulldown — every 5 display frames show 2 source frames twice and 1 source frame three times, which produces visible judder on slow pans. Matching the display to 23.976 Hz (or 47.952 / 119.88 Hz) eliminates it. (https://developer.android.com/media/optimize/performance/frame-rate)

Three layered APIs:

#### Legacy — `Window.setPreferredDisplayModeId(int)` (API 23+)

Pre-Android-11 path. You enumerate `display.supportedModes`, pick a mode matching the target refresh rate (and ideally the current physical resolution), and write its id to the Activity's `WindowManager.LayoutParams.preferredDisplayModeId`:

```kotlin
fun applyPreferredMode(activity: Activity, targetHz: Float) {
    val display = activity.windowManager.defaultDisplay
    val current = display.mode
    val best = display.supportedModes
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .minByOrNull { kotlin.math.abs(it.refreshRate - targetHz) }
        ?: return
    val params = activity.window.attributes
    if (params.preferredDisplayModeId != best.modeId) {
        params.preferredDisplayModeId = best.modeId
        activity.window.attributes = params
    }
}
```

Works back to API 23 but forces a full window-level mode switch; on some TVs this causes a black-screen HDMI re-handshake.

#### Modern — `Surface.setFrameRate(float, int)` and `Surface.setFrameRate(float, int, int)` (API 30 / 31+)

```java
// API 30
Surface.setFrameRate(float frameRate, int compatibility)
// API 31+
Surface.setFrameRate(float frameRate, int compatibility, int changeFrameRateStrategy)
```

Constants on `Surface`:

- `FRAME_RATE_COMPATIBILITY_DEFAULT` — the app is flexible (games, non-video UI).
- `FRAME_RATE_COMPATIBILITY_FIXED_SOURCE` — video with a fixed source rate. Use this for playback.
- `CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS` — switch only if the platform flags it as seamless (no black screen).
- `CHANGE_FRAME_RATE_ALWAYS` — switch even if non-seamless; requires the user to have opted into "Match content frame rate" in Android TV settings (queryable via `DisplayManager.getMatchContentFrameRateUserPreference()` returning `MATCH_CONTENT_FRAMERATE_ALWAYS`).

Always pass the exact content rate (e.g. 23.976f, 29.970f), call it once before starting playback, and call `setFrameRate(0f, FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)` to clear when done. (https://developer.android.com/media/optimize/performance/frame-rate)

#### Media3 wiring

Media3's `MediaCodecVideoRenderer` already calls `Surface.setFrameRate` based on the selected video `Format.frameRate`. The strategy constant lives on `androidx.media3.common.C`:

- `C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF` — Media3 will not call `setFrameRate` at all; the app is responsible.
- `C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS` — default; maps onto `Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS`.

Configure on the ExoPlayer builder:

```kotlin
val player = ExoPlayer.Builder(ctx)
    .setTrackSelector(trackSelector)
    .setRenderersFactory(renderersFactory)
    .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
    .build()
```

(https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java)

Note: Media3 as of 1.10.0 only exposes the two strategies above on `ExoPlayer.Builder.setVideoChangeFrameRateStrategy`; there is no public `VIDEO_CHANGE_FRAME_RATE_STRATEGY_ALWAYS`. If you want non-seamless 24 Hz switching on long-form content, turn the built-in strategy off and drive it yourself from the `Format.frameRate` you see arrive:

```kotlin
player.addListener(object : Player.Listener {
    override fun onVideoInputFormatChanged(format: Format) {
        val rate = format.frameRate
        if (rate <= 0f) return
        val surface = surfaceView.holder.surface ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val strategy = if (dm.matchContentFrameRateUserPreference ==
                    DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS)
                Surface.CHANGE_FRAME_RATE_ALWAYS
            else
                Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
            surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, strategy)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
        }
    }
})
```

`ExoPlayer.setVideoFrameMetadataListener(VideoFrameMetadataListener)` is also available if you want per-frame callbacks — useful for custom A/V sync telemetry, not required for rate matching. (https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java)

Trade-off: seamless-only is the safe default; "always" eliminates 3:2 judder but some TVs report 24 Hz as non-seamless even when the HDMI sink could do it, so enabling it without the user's opt-in causes a black-screen flash at every playback start. (https://developer.android.com/media/optimize/performance/frame-rate)

### 1.4 Tunneled video playback on Android TV

Enable it at track-selection time (see the passthrough/tunneling doc for a deeper treatment):

```kotlin
trackSelector.setParameters(
    trackSelector.buildUponParameters().setTunnelingEnabled(true)
)
```

`setTunnelingEnabled(true)` is available on `DefaultTrackSelector.Parameters.Builder`. Verified in the 1.10.0 source: the `setTunnelingEnabled` / `setAllowInvalidateSelectionsOnRendererCapabilitiesChange` setters live only on `DefaultTrackSelector.Parameters.Builder` — they are **not** on the base `TrackSelectionParameters.Builder`. Media3 internally requests an audio session id via `AudioManager.generateAudioSessionId()` and wires the same id to `MediaCodec.CONFIGURE_FLAG_USE_TUNNEL` on the video decoder and to the `AudioTrack`, so the codec-to-sink plumbing stays entirely in the kernel/driver path. (https://developer.android.com/media/media3/exoplayer/track-selection, https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java)

Lip-sync offset: on OEMs that expose it (Sony Bravia, Shield, some Fire TV), `AudioManager.getOutputLatency()` has returned values in the past, but this API is `@hide` / not part of the public SDK. In practice you tune lip-sync by exposing an offset slider (ms) in the player UI and feeding it into `player.setVideoFrameMetadataListener` or a custom `AudioSink.Listener`. There is no officially documented public lip-sync offset API on AOSP; OEM-specific values should only be consumed when the OEM publishes an official SDK extension for them.

### 1.5 Leanback vs Compose for TV

Status in 2026: AOSP's `androidx.leanback` XML/fragment toolkit is officially deprecated in favor of Compose for TV. (https://developer.android.com/training/tv/playback)

Media3 1.10.0 does not ship a `media3-ui-leanback` artifact. (The old ExoPlayer 2.x `extension-leanback` was retired in the Media3 migration and was never re-added.) Verified by inspecting `https://github.com/androidx/media/tree/1.10.0/libraries` — the directory listing has no `ui_leanback` folder, and the 1.10.0 RELEASENOTES.md makes no mention of a Leanback artifact. Doc 01 §2 lists `media3-ui-leanback` as "skip" for Silo.

Silo's `androidTvApp` uses Compose for TV (`androidx.tv.material3`, `androidx.tv.foundation`) and a `PlayerView` host, so the Leanback fragment question doesn't arise. D-pad / IR remote is handled by the Compose focus system — `Modifier.focusable()`, `onKeyEvent` filtering for `KEYCODE_DPAD_CENTER`, `KEYCODE_MEDIA_PLAY_PAUSE`, etc. `PlayerView` (from `media3-ui`) understands a superset of those keys on its own when focused.

---

## Part 2 — Track selection and renderers

### 2.1 `DefaultTrackSelector`

`androidx.media3.exoplayer.trackselection.DefaultTrackSelector` is the flexible `TrackSelector` implementation that ships with Media3 and is what `ExoPlayer.Builder` uses by default. It picks one track per `Tracks.Group` per renderer based on a constraint object (`DefaultTrackSelector.Parameters`, which extends `TrackSelectionParameters`). (https://developer.android.com/media/media3/exoplayer/track-selection, https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java)

#### Constructing

```kotlin
val trackSelector = DefaultTrackSelector(context)
val player = ExoPlayer.Builder(context)
    .setTrackSelector(trackSelector)
    .build()
```

#### Track topology

Each `MediaSource` exposes one or more renderer-typed `Tracks.Group`s, each backed by a `TrackGroup` of `Format`s. For MKV you typically see: one video group with 1+ `Format`s (the video stream), one audio group per audio track (mono, stereo, 5.1, 7.1, commentary, dub), one text group per subtitle track. Inspect them with:

```kotlin
player.addListener(object : Player.Listener {
    override fun onTracksChanged(tracks: Tracks) {
        tracks.groups.forEach { group ->
            val type = group.type // C.TRACK_TYPE_VIDEO / AUDIO / TEXT / IMAGE
            (0 until group.length).forEach { i ->
                val format: Format = group.getTrackFormat(i)
                val supported = group.isTrackSupported(i) // decoder present + device-capable
                val selected  = group.isTrackSelected(i)
            }
        }
    }
})
```

(https://developer.android.com/media/media3/exoplayer/track-selection)

#### Parameters inventory — `TrackSelectionParameters.Builder`

Every setter has a getter and every one of these maps onto a field on `Parameters`. Most setters return the builder for chaining. (https://developer.android.com/reference/androidx/media3/common/TrackSelectionParameters, https://github.com/androidx/media/blob/1.10.0/libraries/common/src/main/java/androidx/media3/common/TrackSelectionParameters.java)

Video constraints:

- `setMaxVideoSize(width: Int, height: Int)`, `setMaxVideoSizeSd()`
- `setMinVideoSize(width: Int, height: Int)`
- `setMaxVideoBitrate(bitrate: Int)`, `setMinVideoBitrate(bitrate: Int)`
- `setMaxVideoFrameRate(fps: Int)`, `setMinVideoFrameRate(fps: Int)`
- `setViewportSize(width: Int, height: Int, orientationMayChange: Boolean)`
- `setViewportSizeToPhysicalDisplaySize(orientationMayChange: Boolean)`
- `setPreferredVideoMimeType(String?)`, `setPreferredVideoMimeTypes(vararg String)`
- `setPreferredVideoRoleFlags(@C.RoleFlags Int)`
- `setPreferredVideoLanguage(String?)`, `setPreferredVideoLanguages(vararg String)`
- `setPreferredVideoLabels(vararg String)`

Audio constraints:

- `setPreferredAudioLanguage(String?)`, `setPreferredAudioLanguages(vararg String)`
- `setPreferredAudioMimeType(String?)`, `setPreferredAudioMimeTypes(vararg String)`
- `setPreferredAudioRoleFlags(@C.RoleFlags Int)`
- `setMaxAudioChannelCount(Int)`
- `setMaxAudioBitrate(Int)`
- `setAudioOffloadPreferences(AudioOffloadPreferences)`

Text constraints:

- `setPreferredTextLanguage(String?)`, `setPreferredTextLanguages(vararg String)`
- `setPreferredTextRoleFlags(@C.RoleFlags Int)`
- `setSelectUndeterminedTextLanguage(Boolean)`
- `setIgnoredTextSelectionFlags(@C.SelectionFlags Int)`
- `setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings()` — no-arg setter that overwrites preferred text language + role flags with the current Android `CaptioningManager` settings. (Verified against 1.10.0 `TrackSelectionParameters.java`; the Builder exposes a backing boolean field `usePreferredTextLanguagesAndRoleFlagsFromCaptioningManager` but the only public setter is the no-arg `…ToCaptioningManagerSettings()` form.)

Structural:

- `setTrackTypeDisabled(@C.TrackType Int, disabled: Boolean)`
- `setDisabledTrackTypes(Set<Int>)`
- `addOverride(TrackSelectionOverride)`, `setOverrideForType(TrackSelectionOverride)`, `clearOverride(TrackGroup)`, `clearOverridesOfType(@C.TrackType Int)`, `clearOverrides()`
- `setForceLowestBitrate(Boolean)`, `setForceHighestSupportedBitrate(Boolean)`

Image prioritization (for mixed video+image items):

- `setPrioritizeImageOverVideoEnabled(Boolean)`

`DefaultTrackSelector.Parameters.Builder` additionally offers:

- `setAllowVideoMixedMimeTypeAdaptiveness(Boolean)`
- `setAllowVideoNonSeamlessAdaptiveness(Boolean)`
- `setAllowVideoMixedDecoderSupportAdaptiveness(Boolean)`
- `setAllowAudioMixedMimeTypeAdaptiveness(Boolean)`
- `setAllowAudioMixedSampleRateAdaptiveness(Boolean)`
- `setAllowAudioMixedChannelCountAdaptiveness(Boolean)`
- `setAllowAudioMixedDecoderSupportAdaptiveness(Boolean)`
- `setAllowAudioNonSeamlessAdaptiveness(Boolean)`
- `setConstrainAudioChannelCountToDeviceCapabilities(Boolean)` — default `true`; set `false` only if you want Atmos-capable selection to survive a device whose reported output is stereo (e.g. local decode for headphone downmix)
- `setExceedAudioConstraintsIfNecessary(Boolean)`
- `setExceedVideoConstraintsIfNecessary(Boolean)`
- `setExceedRendererCapabilitiesIfNecessary(Boolean)`
- `setAllowMultipleAdaptiveSelections(Boolean)`
- `setTunnelingEnabled(Boolean)`
- `setRendererDisabled(rendererIndex: Int, disabled: Boolean)`

(https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java)

There is no first-class "prefer HDR10 over HDR10+ over HLG" flag — HDR/DV selection is driven by `Format.colorInfo` and `Format.codecs`, filtered by device support. Verified: a full search of 1.10.0 `TrackSelectionParameters.java` and `DefaultTrackSelector.java` turns up no setter containing `Hdr`, `hdr`, `tonemap`, `toneMap`, `DynamicMetadata`, or `DolbyVision` in its name. The practical path is: probe `Display.HdrCapabilities` once, then either accept everything the panel reports (Media3's default capability gate will filter unplayable tracks) or pre-filter via `setPreferredVideoMimeTypes` / `addOverride` if you want a hard order of preference. See the example in 2.4.

#### Build/apply pattern

```kotlin
player.trackSelectionParameters = player.trackSelectionParameters
    .buildUpon()
    .setPreferredAudioLanguage("en")
    .setMaxVideoSize(3840, 2160)
    .build()
```

Or on the selector directly when you need `DefaultTrackSelector`-specific fields:

```kotlin
trackSelector.setParameters(
    trackSelector.buildUponParameters()
        .setTunnelingEnabled(true)
        .setAllowVideoMixedMimeTypeAdaptiveness(true)
        .build()
)
```

#### `TrackSelectionOverride`

A hard override — "use exactly these track indices from this `TrackGroup`". Empty `trackIndices` disables the group entirely. (https://developer.android.com/media/media3/exoplayer/track-selection)

```kotlin
val override = TrackSelectionOverride(audioGroup.mediaTrackGroup, /* trackIndex = */ 2)
player.trackSelectionParameters = player.trackSelectionParameters
    .buildUpon()
    .setOverrideForType(override) // replaces any existing override of the same TrackType
    .build()
```

### 2.2 `DefaultRenderersFactory`

`androidx.media3.exoplayer.DefaultRenderersFactory` builds the list of `Renderer`s the player owns: video, audio, text, metadata, and image. (https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultRenderersFactory.java)

Public surface used by app code:

- Constants
  - `EXTENSION_RENDERER_MODE_OFF = 0` — ignore extension decoders entirely; platform only.
  - `EXTENSION_RENDERER_MODE_ON = 1` — platform first, extensions fallback.
  - `EXTENSION_RENDERER_MODE_PREFER = 2` — extensions first.
  - `DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS = 5000`
  - `MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY = 50`
- Setters (all return the factory for chaining)
  - `setExtensionRendererMode(mode: Int)`
  - `setEnableDecoderFallback(enabled: Boolean)`
  - `setMediaCodecSelector(selector: MediaCodecSelector)`
  - `setEnableAudioFloatOutput(enabled: Boolean)`
  - `setEnableAudioOutputPlaybackParameters(enabled: Boolean)`
  - `setAllowedVideoJoiningTimeMs(ms: Long)`
  - `forceEnableMediaCodecAsynchronousQueueing()` / `forceDisableMediaCodecAsynchronousQueueing()`
  - `experimentalSetMediaCodecAsyncCryptoFlagEnabled(enabled: Boolean)`

(https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultRenderersFactory.java)

Factory usage:

```kotlin
val renderersFactory = DefaultRenderersFactory(context)
    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
    .setEnableDecoderFallback(true)
```

`EXTENSION_RENDERER_MODE_PREFER` is the right setting when you include `media3-decoder-ffmpeg` (or any of the `media3-decoder-*` extensions) and want FFmpeg to handle codecs the platform decoder doesn't, e.g. TrueHD on older Fire TV or DTS on devices without a licensed DTS decoder. `EXTENSION_RENDERER_MODE_ON` is the safer compromise: platform first (keeps hardware video decode), extension only when the platform rejects the format.

Per-renderer notes:

- Video: `MediaCodecVideoRenderer` drives `MediaCodec` + `Surface`. Decoder selection goes through `MediaCodecSelector`, with `setEnableDecoderFallback(true)` letting it try the next decoder in the list after a decoder init failure (common with flaky OEM HEVC decoders).
- Audio: `MediaCodecAudioRenderer` feeds `DefaultAudioSink`. Passthrough/offload / Atmos are handled by the sink; see the passthrough doc.
- Text: `TextRenderer` parses subtitle tracks. Media3 1.10.0 defaults to cue-by-cue parsing on the extractor side; the renderer consumes parsed cues.
- Metadata: `MetadataRenderer` emits `Metadata` events through `Player.Listener.onMetadata` (ID3, EMSG, SCTE-35, etc.).
- Image: `ImageRenderer` (Media3 1.2+) plays `MediaItem`s whose mime type is `image/*`, useful for slideshow / poster items. (https://developer.android.com/jetpack/androidx/releases/media3)

### 2.3 Capability-based pre-filtering

`DefaultTrackSelector` already filters tracks against `RendererCapabilities`, so you rarely need to pre-filter, but for UIs that let the user pick a track before playback starts (common in MKV players) you want to mark tracks as "this won't work on this box" up front.

Enumerating decoders:

```kotlin
import android.media.MediaCodecInfo
import android.media.MediaCodecList

val codecs: Array<MediaCodecInfo> =
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
```

Testing a decoder against a specific `MediaFormat`:

```kotlin
fun firstSupportedDecoder(format: MediaFormat): MediaCodecInfo? {
    val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
    return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, true) } }
        .firstOrNull { runCatching { it.getCapabilitiesForType(mime).isFormatSupported(format) }.getOrDefault(false) }
}
```

(https://developer.android.com/reference/android/media/MediaCodecList, https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities#isFormatSupported(android.media.MediaFormat))

Audio filtering via `AudioCapabilities`:

```kotlin
val audioCaps = AudioCapabilities.getCapabilities(
    context, AudioAttributes.DEFAULT, /* routedDevice = */ null, /* spatializerChannelMasks = */ emptyList()
)
val sinkSupportsAtmos = audioCaps.supportsEncoding(C.ENCODING_E_AC3_JOC)
val sinkSupportsTrueHD = audioCaps.supportsEncoding(C.ENCODING_DOLBY_TRUEHD)
val maxChannels = audioCaps.maxChannelCount
```

Video filtering via `Display.HdrCapabilities`:

```kotlin
fun canPlayHdr(format: Format, hdrTypes: Set<Int>): Boolean {
    val colorSpace = format.colorInfo?.colorSpace ?: return true // SDR passes
    val colorTransfer = format.colorInfo?.colorTransfer
    return when {
        MimeTypes.VIDEO_DOLBY_VISION == format.sampleMimeType ->
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in hdrTypes
        colorTransfer == C.COLOR_TRANSFER_ST2084 ->
            Display.HdrCapabilities.HDR_TYPE_HDR10 in hdrTypes ||
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in hdrTypes
        colorTransfer == C.COLOR_TRANSFER_HLG ->
            Display.HdrCapabilities.HDR_TYPE_HLG in hdrTypes
        else -> true
    }
}
```

This is deliberately permissive — HDR10+ content falls back gracefully to HDR10 on most panels, so accepting either is usually right. `C.COLOR_TRANSFER_ST2084` and `C.COLOR_TRANSFER_HLG` are the Media3 color-transfer constants. (https://github.com/androidx/media/blob/1.10.0/libraries/common/src/main/java/androidx/media3/common/C.java)

### 2.4 Kotlin examples

#### Example A — Android TV preset (4K, DV > HDR10 > HLG > SDR; Atmos > 5.1 > stereo)

```kotlin
fun tvTrackSelector(context: Context): DefaultTrackSelector {
    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
    val hdrTypes = display?.hdrCapabilities?.supportedHdrTypes?.toSet().orEmpty()
    val mode = display?.mode

    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val audioCaps = AudioCapabilities.getCapabilities(
        context, AudioAttributes.DEFAULT, null, emptyList()
    )

    // Preferred video mime order: DV first only if the panel actually supports it.
    val preferredVideoMimes = buildList {
        if (Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in hdrTypes) add(MimeTypes.VIDEO_DOLBY_VISION)
        add(MimeTypes.VIDEO_H265) // HDR10/HDR10+/HLG live here via colorInfo
        add(MimeTypes.VIDEO_AV1)
        add(MimeTypes.VIDEO_H264)
    }.toTypedArray()

    // Preferred audio mime order: Atmos > 5.1 > stereo; only keep what the sink reports.
    val preferredAudioMimes = buildList {
        if (audioCaps.supportsEncoding(C.ENCODING_E_AC3_JOC)) add(MimeTypes.AUDIO_E_AC3_JOC)
        if (audioCaps.supportsEncoding(C.ENCODING_DOLBY_TRUEHD)) add(MimeTypes.AUDIO_TRUEHD)
        if (audioCaps.supportsEncoding(C.ENCODING_E_AC3)) add(MimeTypes.AUDIO_E_AC3)
        if (audioCaps.supportsEncoding(C.ENCODING_AC3)) add(MimeTypes.AUDIO_AC3)
        add(MimeTypes.AUDIO_AAC)
    }.toTypedArray()

    val selector = DefaultTrackSelector(context)
    selector.setParameters(
        selector.buildUponParameters()
            // Video
            .apply { mode?.let { setMaxVideoSize(it.physicalWidth, it.physicalHeight) } }
            .setViewportSizeToPhysicalDisplaySize(/* orientationMayChange = */ false)
            .setPreferredVideoMimeTypes(*preferredVideoMimes)
            // Audio
            .setPreferredAudioMimeTypes(*preferredAudioMimes)
            .setMaxAudioChannelCount(audioCaps.maxChannelCount)
            .setConstrainAudioChannelCountToDeviceCapabilities(true)
            .setPreferredAudioRoleFlags(C.ROLE_FLAG_MAIN)
            // Text
            .setPreferredTextRoleFlags(C.ROLE_FLAG_SUBTITLE)
            .setSelectUndeterminedTextLanguage(false)
            // Adaptation / tunneling
            .setAllowVideoMixedMimeTypeAdaptiveness(true)
            .setTunnelingEnabled(true)
            .build()
    )
    return selector
}

fun tvRenderersFactory(context: Context): DefaultRenderersFactory =
    DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setEnableDecoderFallback(true)

fun tvPlayer(context: Context): ExoPlayer =
    ExoPlayer.Builder(context, tvRenderersFactory(context))
        .setTrackSelector(tvTrackSelector(context))
        .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
        .build()
```

Note: `setTunnelingEnabled(true)` is TV-only. On a phone it will often fall back to non-tunneled because no audio sink supports the tunnel, but it still costs decoder init time. Leave it false on the phone preset. (https://developer.android.com/media/media3/exoplayer/track-selection)

#### Example B — phone/tablet preset (no tunneling, spatialized stereo Atmos when possible)

```kotlin
fun phoneTrackSelector(context: Context): DefaultTrackSelector {
    val spatializer = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).spatializer
    val spatialCaps: List<Int> = if (spatializer.isAvailable && spatializer.isEnabled)
        listOf(AudioFormat.CHANNEL_OUT_5POINT1, AudioFormat.CHANNEL_OUT_7POINT1_SURROUND)
    else emptyList()

    val audioCaps = AudioCapabilities.getCapabilities(
        context, AudioAttributes.DEFAULT, /* routedDevice = */ null, spatialCaps
    )

    val preferredAudioMimes = buildList {
        // Media3 will downmix Atmos to spatialized stereo on devices whose AudioSink reports it.
        if (audioCaps.supportsEncoding(C.ENCODING_E_AC3_JOC)) add(MimeTypes.AUDIO_E_AC3_JOC)
        add(MimeTypes.AUDIO_AAC)
    }.toTypedArray()

    val selector = DefaultTrackSelector(context)
    selector.setParameters(
        selector.buildUponParameters()
            .setPreferredAudioMimeTypes(*preferredAudioMimes)
            // Phones are usually stereo unless Bluetooth reports multichannel.
            .setMaxAudioChannelCount(audioCaps.maxChannelCount)
            .setConstrainAudioChannelCountToDeviceCapabilities(true)
            .setTunnelingEnabled(false)
            .setViewportSizeToPhysicalDisplaySize(true)
            .build()
    )
    return selector
}
```

`AudioManager.getSpatializer()` returns a `Spatializer`; on API 32+ it gates Atmos downmix to stereo. Passing the reported channel masks into `AudioCapabilities.getCapabilities` lets the sink know the system will accept a multichannel decode for spatialization. (Per doc 04 §4.1 this is how `AudioCapabilities.getCapabilities` is wired on API 32+; exact channel-mask plumbing still varies by OEM and should be confirmed on the target device via `Spatializer.canBeSpatialized`.)

#### Example C — detect and bind to HDMI hotplug, re-apply track selection

```kotlin
class HdmiAudioWatcher(
    private val context: Context,
    private val selector: DefaultTrackSelector,
    private val player: ExoPlayer,
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != AudioManager.ACTION_HDMI_AUDIO_PLUG) return
            // Rebuild caps; push a no-op parameter update so DefaultTrackSelector re-evaluates
            // against the new AudioCapabilities.
            selector.setParameters(selector.buildUponParameters().build())
        }
    }
    fun start() = context.registerReceiver(receiver, IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG))
    fun stop()  = context.unregisterReceiver(receiver)
}
```

`DefaultTrackSelector` automatically invalidates selections when `RendererCapabilities` change, provided `setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)` is set on the `DefaultTrackSelector.Parameters` — the setter is verified present in 1.10.0 source, but note that its default is **not** universally guaranteed `true`; the Silo code explicitly sets it (doc 07 §4 / doc 08 §3). If you rely on the auto-invalidate behaviour, set the flag explicitly.

#### Example D — apply a refresh-rate switch when `Format.frameRate` arrives

```kotlin
class FrameRateMatcher(
    private val activity: Activity,
    private val surface: Surface,
) : Player.Listener {
    private val dm by lazy {
        activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    override fun onVideoInputFormatChanged(format: Format) {
        val rate = format.frameRate
        if (rate <= 0f) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val allowAlways = dm.matchContentFrameRateUserPreference ==
                DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS
            val strategy = if (allowAlways) Surface.CHANGE_FRAME_RATE_ALWAYS
                           else Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
            surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, strategy)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
        } else {
            // API <= 29: fall back to preferredDisplayModeId.
            applyPreferredMode(activity, rate)
        }
    }
}
```

Register with `player.addListener(FrameRateMatcher(activity, playerView.videoSurfaceView!!.holder.surface!!))`. Clear with `surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)` on `onPlaybackStateChanged(STATE_ENDED)` or when the `SurfaceHolder` is released.

---

## Sources

- https://developer.android.com/training/tv/start
- https://developer.android.com/training/tv/start/hardware
- https://developer.android.com/training/tv/start/start
- https://developer.android.com/training/tv/playback
- https://developer.android.com/media/media3/exoplayer
- https://developer.android.com/media/media3/exoplayer/track-selection
- https://developer.android.com/media/media3/exoplayer/supported-formats
- https://developer.android.com/media/optimize/performance/frame-rate
- https://developer.android.com/reference/androidx/media3/common/TrackSelectionParameters
- https://developer.android.com/reference/androidx/media3/common/TrackSelectionParameters.Builder
- https://developer.android.com/reference/androidx/media3/exoplayer/trackselection/DefaultTrackSelector
- https://developer.android.com/reference/androidx/media3/exoplayer/DefaultRenderersFactory
- https://developer.android.com/reference/android/view/Display
- https://developer.android.com/reference/android/view/Display.Mode
- https://developer.android.com/reference/android/view/Display.HdrCapabilities
- https://developer.android.com/reference/android/view/Surface
- https://developer.android.com/reference/android/app/UiModeManager
- https://developer.android.com/reference/android/content/res/Configuration
- https://developer.android.com/reference/android/media/AudioManager
- https://developer.android.com/reference/android/media/AudioDeviceInfo
- https://developer.android.com/reference/android/media/MediaCodecList
- https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities
- https://github.com/androidx/media/blob/1.10.0/libraries/common/src/main/java/androidx/media3/common/TrackSelectionParameters.java
- https://github.com/androidx/media/blob/1.10.0/libraries/common/src/main/java/androidx/media3/common/MimeTypes.java
- https://github.com/androidx/media/blob/1.10.0/libraries/common/src/main/java/androidx/media3/common/C.java
- https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultRenderersFactory.java
- https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java
- https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java
- https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/MediaCodecVideoRenderer.java
- https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioCapabilities.java

## Validation log

- corrected: "`setUsePreferredTextLanguagesAndRoleFlagsFromCaptioningManager(Boolean)`" → the public setter on 1.10.0 `TrackSelectionParameters.Builder` is actually `setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings()` (no-arg). Text updated in §2.1 Text constraints.
- corrected: URL anchors pointing at the moving `release` branch of `androidx/media` repointed to the pinned `1.10.0` tag so they align with the document version header.
- verified: `setTunnelingEnabled` and `setAllowInvalidateSelectionsOnRendererCapabilitiesChange` live only on `DefaultTrackSelector.Parameters.Builder`, not on the base `TrackSelectionParameters.Builder`. (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java and https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/TrackSelectionParameters.java)
- verified: `DefaultTrackSelector.Parameters.Builder` does have every setter listed in §2.1 "Structural" and "DefaultTrackSelector.Parameters.Builder additionally offers" — `setAllowVideoMixedMimeTypeAdaptiveness`, `setAllowVideoNonSeamlessAdaptiveness`, `setAllowVideoMixedDecoderSupportAdaptiveness`, `setAllowAudioMixedMimeTypeAdaptiveness`, `setAllowAudioMixedSampleRateAdaptiveness`, `setAllowAudioMixedChannelCountAdaptiveness`, `setAllowAudioMixedDecoderSupportAdaptiveness`, `setAllowAudioNonSeamlessAdaptiveness`, `setConstrainAudioChannelCountToDeviceCapabilities`, `setExceedAudioConstraintsIfNecessary`, `setExceedVideoConstraintsIfNecessary`, `setExceedRendererCapabilitiesIfNecessary`, `setAllowMultipleAdaptiveSelections`, `setRendererDisabled`. (DefaultTrackSelector.java @ 1.10.0)
- verified: `DefaultRenderersFactory` exposes `EXTENSION_RENDERER_MODE_OFF = 0`, `_ON = 1`, `_PREFER = 2`, plus `DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS = 5000` and `MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY = 50`. (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultRenderersFactory.java)
- verified: `setViewportSizeToPhysicalDisplaySize(boolean)` is the non-deprecated form on 1.10.0 `TrackSelectionParameters.Builder`; the two-arg `(Context, boolean)` overload is `@Deprecated` and delegates.
- verified: `C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF = Integer.MIN_VALUE`, `C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS = Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS`. No `STRATEGY_ALWAYS` exists in 1.10.0 `C.java`. Matches §1.3.
- verified: no HDR-preference setter on `TrackSelectionParameters.Builder` or `DefaultTrackSelector.Parameters.Builder` in 1.10.0. Removed the "(unverified)" wrapper on §2.1.
- verified: Leanback UI module is not shipped in Media3 1.10.0 — directory listing at `https://github.com/androidx/media/tree/1.10.0/libraries` has no `ui_leanback` folder, matching §1.5.
- still unverified: whether `setAllowInvalidateSelectionsOnRendererCapabilitiesChange` is default-true without being explicitly set; 1.10.0 source indicates the constructor default, but the Silo implementation sets it explicitly — safer to continue doing so. Text updated in §2.4.
- still unverified: per-OEM lip-sync offset APIs (Sony, Fire TV). No public-SDK surface to rely on; the doc correctly flags this.
- still unverified: whether Surface.setFrameRate(float, int, int) (three-arg, API 31+) produces the expected seamless/non-seamless switch on every target TV — only confirmable on hardware.
