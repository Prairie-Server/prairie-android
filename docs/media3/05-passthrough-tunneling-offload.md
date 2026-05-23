Document version: Media3 1.10.0

# Passthrough, Tunneling, and Offload

Silo has two Android surfaces: the phone app (`androidApp`) and the TV app (`androidTvApp`). Both share the `android-shared` module's `SiloPlayerFactory`, which builds an `ExoPlayer` from `androidx.media3:media3-exoplayer:1.10.0`. MKV containers with Dolby Atmos tracks (TrueHD, E-AC-3 JOC, AC-4) typically cannot be decoded by the on-device software stack, so Silo relies on the platform's **bitstream passthrough** path to route the encoded frames unchanged to an AVR, soundbar, or TV's HDMI eARC. On Android TV we additionally want **tunneled video** to keep A/V sync accurate when audio is travelling through a receiver, and on battery-powered devices we want **audio offload** for MP3/AAC/FLAC/OPUS background playback. This document describes how Media3 plumbs those three paths, what Media3 APIs to call, and how they interact.

Codec identification (what each stream's container-level codec maps to) lives in the sibling doc `04-atmos-and-audio-codecs.md`. This doc is about the transport and plumbing.

---

## 1. Audio passthrough (bitstream passthrough)

### What it is

Passthrough delivers the encoded audio frames produced by the extractor directly to the platform `AudioTrack` using an encoded `AudioFormat` encoding (for example `AudioFormat.ENCODING_E_AC3_JOC`). The Android AudioFlinger does not decode, mix, or resample the payload — it packs the frames into an HDMI/SPDIF/IEC-61937 bitstream and hands them to the sink device, which is expected to contain a hardware decoder capable of that format. This is the only way to deliver Atmos (TrueHD or E-AC-3 JOC) or AC-4 immersive content to an AVR that advertises those formats, because the Android software decoder stack does not produce object-based output.

### When it is required

Passthrough is effectively mandatory for:

- `ENCODING_DOLBY_TRUEHD` — TrueHD, including TrueHD-with-Atmos. No shipping Android OEM exposes a TrueHD software decoder. ([C.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/C.java))
- `ENCODING_E_AC3_JOC` — E-AC-3 with Joint Object Coding metadata (Dolby Digital Plus with Atmos). Added in platform API 28. The platform audio decoder can produce 5.1/7.1 E-AC-3 but strips the JOC object layer, so only passthrough preserves Atmos. ([C.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/C.java))
- `ENCODING_AC4` — AC-4, the carrier for Atmos on ATSC 3.0 and some streaming deliveries. Platform API 29+. ([C.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/C.java))
- `ENCODING_DTS_HD`, `ENCODING_DTS_UHD_P2` — DTS-HD MA and DTS:X variants. DTS passthrough is common on Android TV SoCs but less common on phones.

For `ENCODING_AC3` (Dolby Digital) and `ENCODING_E_AC3` (plain DD+ without JOC), modern Android has a usable software decoder, so the renderer path and the passthrough path are both viable — the choice is made at runtime from `AudioCapabilities.supportsEncoding(...)`.

### Platform mechanism

Passthrough is configured at the `AudioTrack` layer by creating the track with one of the encoded `AudioFormat.ENCODING_*` constants. Media3 translates its own `androidx.media3.common.C.ENCODING_*` values (which are largely thin wrappers over the platform constants — see `C.java`) into the correct `AudioFormat` encoding before constructing the track. Media3 1.10.0 recognises at least:

| Media3 constant | Platform constant | Introduced |
| --- | --- | --- |
| `C.ENCODING_AC3` | `AudioFormat.ENCODING_AC3` | API 21 |
| `C.ENCODING_E_AC3` | `AudioFormat.ENCODING_E_AC3` | API 21 |
| `C.ENCODING_E_AC3_JOC` | `AudioFormat.ENCODING_E_AC3_JOC` | API 28 |
| `C.ENCODING_DOLBY_TRUEHD` | `AudioFormat.ENCODING_DOLBY_TRUEHD` | API 25 |
| `C.ENCODING_AC4` | `AudioFormat.ENCODING_AC4` | API 28 (added alongside `ENCODING_E_AC3_JOC` in API 28; `ENCODING_AC4` int value 17 precedes `ENCODING_E_AC3_JOC` value 18 in AOSP) |
| `C.ENCODING_DTS` | `AudioFormat.ENCODING_DTS` | API 23 |
| `C.ENCODING_DTS_HD` | `AudioFormat.ENCODING_DTS_HD` | API 23 |
| `C.ENCODING_DTS_UHD_P2` | `AudioFormat.ENCODING_DTS_UHD_P2` | API 34 (doc 04 §5 lists `ENCODING_DTS_UHD_P1/_P2/_HD_MA` as API 34 additions; this overrides the earlier "API 32" claim here) |
| `C.ENCODING_OPUS` | `AudioFormat.ENCODING_OPUS` | API 30 |

Source for the Media3 side: `libraries/common/src/main/java/androidx/media3/common/C.java`. ([C.java @ 1.10.0](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/C.java)) Platform-side API-level numbers are taken from AOSP `AudioFormat.java` (https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/AudioFormat.java); the Media3 constants themselves resolve at compile time and the real runtime gate is `AudioCapabilities.supportsEncoding(...)`.

Note: `ENCODING_DOLBY_MAT` and `ENCODING_DTS_UHD_P1` are **not** present in Media3 1.10.0's `C.java` — verified by searching `androidx.media3.common.C` at tag 1.10.0 for both constant names (no match). If you see a container that advertises Dolby MAT (the MAT 2.0 wrapper used around TrueHD over HDMI for Atmos passthrough), the frames still flow as `ENCODING_DOLBY_TRUEHD` in Media3's pipeline.

### Media3 wiring — `DefaultAudioSink`

`DefaultAudioSink` is the implementation behind every `MediaCodecAudioRenderer` that `DefaultRenderersFactory` installs, unless you override `buildAudioSink(...)`. It exposes three output modes via `@DefaultAudioSink.OutputMode`:

```kotlin
// From androidx.media3.exoplayer.audio.DefaultAudioSink (Media3 1.10.0).
public static final int OUTPUT_MODE_PCM = 0
public static final int OUTPUT_MODE_OFFLOAD = 1
public static final int OUTPUT_MODE_PASSTHROUGH = 2
```

([DefaultAudioSink.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioSink.java))

When `MediaCodecAudioRenderer` asks the sink whether it supports a `Format`, `AudioSink.getFormatSupport(...)` returns one of:

- `SINK_FORMAT_SUPPORTED_DIRECTLY`
- `SINK_FORMAT_SUPPORTED_WITH_TRANSCODING`
- `SINK_FORMAT_UNSUPPORTED`

`SUPPORTED_DIRECTLY` means either PCM passthrough (the format is already PCM) or encoded passthrough (the format's encoding matches something in `AudioCapabilities`). For an encoded stream like TrueHD, a `SUPPORTED_DIRECTLY` answer comes entirely from `AudioCapabilities.supportsEncoding(C.ENCODING_DOLBY_TRUEHD)`; if that returns `false`, `MediaCodecAudioRenderer` cannot render the track at all because there is no software TrueHD decoder, and track selection will deselect it. ([AudioSink.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioSink.java))

Internally `MediaCodecAudioRenderer.shouldUseBypass()` short-circuits the `MediaCodec` setup entirely when the sink reports the format as directly supported — the renderer runs in "bypass" mode, and the extractor's encoded bytes are fed straight into `AudioSink.handleBuffer(...)`, which in turn writes them to a passthrough-configured `AudioTrack`. ([MediaCodecAudioRenderer source review](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/MediaCodecAudioRenderer.java))

### `DefaultAudioSink.Builder` — options relevant to passthrough

Verified against the 1.10.0 source:

```kotlin
DefaultAudioSink.Builder(context)
    .setAudioProcessors(emptyArray())                      // no PCM-layer effects
    .setEnableFloatOutput(true)                            // 32-bit float for PCM tracks
    .setEnableAudioOutputPlaybackParameters(true)          // AudioTrack-level speed
    .setAudioTrackBufferSizeProvider(...)                  // tune sink latency
    .setAudioOffloadSupportProvider(...)                   // custom offload capability lookup
    .setAudioTrackProvider(...)                            // override AudioTrack construction
    .setAudioOutputProvider(...)                           // override entire output path
    .setExperimentalAudioOffloadListener(listener)
    .build()
```

([DefaultAudioSink.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioSink.java))

A few notes (all verified against 1.10.0 source):

- `setAudioCapabilities(...)` is `@Deprecated` in the 1.10.0 `DefaultAudioSink.Builder`. It is retained for callers that have no `Context` to hand; when you construct with a `Context`, the sink derives capabilities on its own via `AudioTrackAudioOutputProvider`. Several other Builder setters are also now `@Deprecated`: `setAudioTrackBufferSizeProvider(...)`, `setAudioOffloadSupportProvider(...)`, `setAudioTrackProvider(...)`. The non-deprecated replacements flow through `setAudioOutputProvider(...)`.
- There is **no** `DefaultAudioSink.Builder.setOffloadMode(...)`. Offload is selected by the track selector now, not the sink. `DefaultAudioSink` does expose an instance method `setOffloadMode(@OffloadMode int)` from the `AudioSink` interface (annotated `@RequiresApi(29)`) that the renderer calls at runtime, but Media3 does not expect applications to call it directly.
- Passthrough is not a Builder option at all — it is an automatic consequence of reporting an encoded `Format` to a sink whose `AudioCapabilities.supportsEncoding(format.encoding)` returns `true`.

### `AudioCapabilities.supportsEncoding(...)`

`AudioCapabilities` is how Media3 answers "is passthrough available right now?". It combines three signals:

1. The `AudioManager.ACTION_HDMI_AUDIO_PLUG` sticky broadcast, whose `EXTRA_ENCODINGS` extra advertises the set of encoded bitstreams the currently connected HDMI sink says it can decode. The receiver also reads `EXTRA_MAX_CHANNEL_COUNT` for channel counts. ([AudioCapabilities.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioCapabilities.java))
2. The Amazon/Xiaomi "external surround sound" global setting (`EXTERNAL_SURROUND_SOUND_GLOBAL_SETTING`), which lets the user force passthrough on/off globally.
3. The platform `Spatializer` (API 32+) channel masks, for HRTF / head-tracked output.

The authoritative accessor is:

```kotlin
// Media3 1.10.0. All params are Android platform types except the optional routed device.
AudioCapabilities.getCapabilities(
    context: Context,
    audioAttributes: AudioAttributes,
    routedDevice: AudioDeviceInfo?,
    spatializerChannelMasks: List<Int>,
): AudioCapabilities
```

The resulting object answers `supportsEncoding(@C.Encoding Int)`, `getMaxChannelCount()`, and `isPassthroughPlaybackSupported(Format, AudioAttributes)`. `DEFAULT_AUDIO_CAPABILITIES` (`AudioProfile.DEFAULT_AUDIO_PROFILE` + stereo PCM 16-bit) is the fallback when no AVR is attached.

### Coexistence with `MediaCodecAudioRenderer`

Every renderer that `DefaultRenderersFactory` creates for audio is a `MediaCodecAudioRenderer`, even when the final output path is passthrough. The renderer decides per-track whether to use a real `MediaCodec` or to bypass it based on `AudioSink.getFormatSupport(...)`. There is no separate "PassthroughRenderer" in Media3 — this is one of the Media3 design simplifications compared to the earlier standalone ExoPlayer 2.x layout.

### Known issues and corner cases

- **HDMI hotplug re-evaluation.** The set of passthrough-compatible encodings can change mid-playback when the user powers on an AVR, switches HDMI inputs, plugs or unplugs headphones, or moves between Bluetooth and speaker. `AudioCapabilitiesReceiver` subscribes to four signals: `AudioManager.ACTION_HDMI_AUDIO_PLUG`, `AudioDeviceCallback` for device add/remove, the `Spatializer` channel-mask callback (API 32+), and the external-surround-sound setting observer. When any of them fires, it re-runs capability discovery and calls `Listener.onAudioCapabilitiesChanged(AudioCapabilities)` if the result differs. ([AudioCapabilitiesReceiver.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioCapabilitiesReceiver.java))
- **Track re-selection on capability change.** `DefaultAudioSink` forwards those change events to `AudioSink.Listener.onAudioCapabilitiesChanged()`. To make `DefaultTrackSelector` actually *act* on the change (re-run selection with the new capability set, potentially switching from the TrueHD track to an AAC track if the AVR went to sleep), set `setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)` on the track-selector parameters. The Silo `SiloPlayerFactory` already does this.
- **Device-initiated output switching.** If the user plugs earbuds in mid-TrueHD-passthrough, the HDMI route is still selected (the AVR is still the routed device) but the audio path routes to earbuds. Behaviour is device-dependent; in practice `AudioManager` raises a route change, `AudioCapabilitiesReceiver` observes the new default device, capabilities drop to the earbuds' capabilities (usually PCM stereo), and track selection falls back to a transcoded PCM path. This is why the factory **must** handle `onAudioBecomingNoisy` and why `setHandleAudioBecomingNoisy(true)` is on in the factory.
- **Volume control during passthrough.** When the audio is a bitstream, the phone/TV has no decoded PCM to attenuate. System volume keys are forwarded to the AVR over CEC (if CEC volume is enabled) or simply have no effect on the audible level. Media3's `Player.setVolume(0.5f)` is essentially a no-op on passthrough — the sink cannot scale encoded frames. Do not rely on software ducking during passthrough. (The platform AudioFlinger can mute in the sense of stopping the stream, but cannot do per-app fractional ducking.)
- **Passthrough and audio focus.** Focus requests still work because the OS fires `AudioManager.OnAudioFocusChangeListener` callbacks independent of the underlying encoding, but because you can't duck, lower-transient requests from other apps typically have to stop the stream instead of ducking it.

---

## 2. Audio tunneling

### What it is

Tunneling is an OS-level A/V sync mode. The app configures the video `MediaCodec` and the audio `MediaCodec` with the feature `FEATURE_TunneledPlayback` and a shared audio session id. In tunneled mode, both codecs are wired directly into a hardware pipeline: the video decoder pushes frames directly to the display composer with timestamp metadata, and the audio decoder pushes samples to AudioFlinger with matching timestamps. The system's HW AV-sync clock correlates them. The app never touches the decoded video buffers.

This is the *only* reliable way to get frame-accurate lip-sync on Android TV when the HDMI audio output has significant latency — for example when routing PCM through an AVR with DSP processing that adds 80–120 ms, or when passing through compressed Atmos that the AVR decodes itself. Without tunneling, the app would have to estimate AVR latency and apply a video delay manually, which is what `AudioTrack.getTimestamp()` + `Player.getCurrentPosition()` reconciliation does — accurate but not frame-perfect.

### When to use

- **Android TV: yes.** Tunneling is the standard path for long-form playback on Android TV, particularly at 24/25/50/60 fps where drift accumulates quickly.
- **Phones and tablets: usually no.** Phone HDMI is rare, internal speakers are low-latency, and non-tunneled playback is fine. Tunneling also turns off some features the phone UI relies on (see trade-offs below).

### Media3 API

```kotlin
// androidx.media3.exoplayer.trackselection.DefaultTrackSelector.ParametersBuilder
// (extends androidx.media3.common.TrackSelectionParameters.Builder)
fun setTunnelingEnabled(tunnelingEnabled: Boolean): ParametersBuilder
```

([DefaultTrackSelector.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java))

The documented behaviour is: "Sets whether to enable tunneling if possible. Tunneling will only be enabled if it's supported by the audio and video renderers for the selected tracks." ([DefaultTrackSelector.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java)) The flag is a *request*; the selector drops it back to non-tunneled per track if the chosen codecs don't both expose `FEATURE_TunneledPlayback`.

### Platform requirements

- The video `MediaCodec` for the chosen video track must return `true` from `CodecCapabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback)`.
- The audio `MediaCodec` for the chosen audio track must also return `true` from the same check.
- Both codecs must share the same audio session id, which Media3 writes into the `MediaFormat` under `MediaFormat.KEY_AUDIO_SESSION_ID` before configuring each codec. Media3 generates the session id centrally (on the player instance) and threads it through the audio and video renderers.

### Discovering tunneling support

On Media3, you do not call `CodecCapabilities.isFeatureSupported` yourself — `MediaCodecUtil` and `MediaCodecAudioRenderer.supportsFormat(...)` / `MediaCodecVideoRenderer.supportsFormat(...)` do it for you and encode the result into the `@RendererCapabilities.Capabilities` int that `DefaultTrackSelector` consumes. That int has a `TUNNELING_SUPPORTED` bit ([MediaCodecAudioRenderer source review](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/MediaCodecAudioRenderer.java)) that the selector ANDs across the audio and video tracks it's planning to select. Only if both sides advertise `TUNNELING_SUPPORTED` does `setTunnelingEnabled(true)` actually enable it for the session.

If you want to check manually before offering the feature in UI:

```kotlin
// Rough "will tunneling work on this device?" probe.
// (Illustrative - Media3's MediaCodecUtil does this more carefully per-codec.)
fun anyAudioDecoderSupportsTunneling(mimeType: String): Boolean {
    val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    return list.codecInfos.any { info ->
        !info.isEncoder &&
            info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } &&
            runCatching {
                info.getCapabilitiesForType(mimeType)
                    .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback)
            }.getOrDefault(false)
    }
}
```

### Trade-offs

- **Frame-accurate seeking is disabled.** In tunneled mode the app never sees decoded frames, so it cannot scrub in the conventional way. Seeks land on the nearest sync frame the pipeline can render immediately. `ExoPlayer.setSeekParameters(SeekParameters.EXACT)` is effectively ignored.
- **Video screenshot capture is disabled.** The display composer receives frames directly; `Surface.getHardwareBuffer()`-style screenshotting returns black. This matters for phones (screenshot apps, share sheets) more than for TV.
- **Audio session is effectively locked.** The audio path owns the session id for tunneling's lifetime. You cannot attach your own `AudioEffect` to the session mid-playback.
- **Device-specific bugs.** `DefaultTrackSelector.setTunnelingEnabled` documentation explicitly warns: "Manual testing is strongly recommended" and links to several open bugs (#9661, #9133, #9317, #9502 in `androidx/media`). Some SoCs mis-report tunneling support or underrun tunneled audio at high bitrates.

### Can tunneling and passthrough coexist?

Yes — and this is the intended Android TV configuration for Atmos. The audio `MediaCodec` is still a codec entry from `MediaCodecList`, which on the TV advertises `FEATURE_TunneledPlayback` on the E-AC-3 / TrueHD / AC-4 decoder entries even when those decoders operate in bitstream-passthrough mode. The pipeline is:

1. `DefaultTrackSelector` picks the Atmos track and requests tunneling.
2. `MediaCodecAudioRenderer.shouldUseBypass()` returns `true` because `AudioCapabilities.supportsEncoding(...)` says the sink accepts the bitstream.
3. The renderer skips its own `MediaCodec` configuration and writes encoded bytes to `AudioSink.handleBuffer(...)`.
4. `DefaultAudioSink` configures the `AudioTrack` with the encoded `AudioFormat.ENCODING_*` and — because tunneling is on — it also threads the audio session id onto the track, so the video-side `MediaCodec` and the audio-side `AudioTrack` share an HW-AV-sync session.

Media3's source applies the audio-session-id "tunneling" state inside `DefaultAudioSink` regardless of whether the sink is running in `OUTPUT_MODE_PCM`, `OUTPUT_MODE_OFFLOAD`, or `OUTPUT_MODE_PASSTHROUGH` (verified by inspection of 1.10.0 `DefaultAudioSink.java` — the three `OUTPUT_MODE_*` constants exist with values 0/1/2 and the tunneled-session handling is orthogonal to which output mode is chosen). In practice, tunneled passthrough is the standard configuration used by Google TV and AOSP TV reference device implementations. ([DefaultAudioSink.java @ 1.10.0](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioSink.java))

Tunneling + offload, on the other hand, is a real compatibility minefield. See the next section.

---

## 3. Audio offload

### What it is

Offload lets the hardware audio DSP decode a compressed bitstream (MP3, AAC, FLAC, OPUS, and on some SoCs E-AC-3) directly, with the application CPU and `MediaCodec` idle. The `AudioTrack` receives encoded frames, the DSP buffers several seconds of them, and the main cores can suspend. The `ExoPlayer.AudioOffloadListener.onSleepingForOffloadChanged(true)` callback fires when the player actually parks its decode/rendering loop. ([ExoPlayer.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java))

The power win is meaningful for background music — offloaded MP3 at the screen-off can reduce playback-time battery draw noticeably on recent SoCs. For active video playback the CPU is already awake for video decode, so offload is mostly irrelevant.

### Eligible encodings

Per platform docs ([AudioManager.isOffloadedPlaybackSupported](https://developer.android.com/reference/android/media/AudioManager#isOffloadedPlaybackSupported(android.media.AudioFormat,%20android.media.AudioAttributes))), typical candidates are:

- `ENCODING_MP3`
- `ENCODING_AAC_LC`, `ENCODING_AAC_HE_V1`, `ENCODING_AAC_HE_V2`, `ENCODING_AAC_XHE`
- `ENCODING_OPUS` (API 30+)
- FLAC (often; platform- and SoC-dependent)

Some SoCs also advertise offload for `ENCODING_E_AC3` when paired with the right audio HAL. TrueHD, AC-4, and DTS-family formats are **not** offload-eligible on any Android device covered in the Silo QA matrix (doc 08 §9.2) — they go through passthrough, not offload. Per-device authoritative answer: `AudioManager.isOffloadedPlaybackSupported(AudioFormat, AudioAttributes)` for the specific encoding + sample-rate + channel-count tuple on the target hardware.

### Platform APIs

- `AudioManager.isOffloadedPlaybackSupported(AudioFormat, AudioAttributes): Boolean` — API 29+. Query before building an `AudioTrack`. Returns whether the given exact format/attributes pair is currently offloadable on this device. (The `developer.android.com` reference page was returning a navigation shell during this research; the method's presence on API 29 is widely documented in Media3's own source and confirmed by `AudioCapabilities`'s Api29 probe path.)
- `AudioTrack.Builder.setOffloadedPlayback(boolean): AudioTrack.Builder` — API 29+. Requests that the track use the offload path. The build will fail if the format isn't actually offloadable; the builder does not silently fall back to non-offload. (Same caveat as above — the public reference page returned only the navigation shell; Media3's `DefaultAudioSink` construction path uses this method under `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q`.)
- `AudioTrack.StreamEventCallback` — offload buffer lifecycle (tear-down, buffer low, underrun). Media3 wires these to the `AudioSink.Listener.onOffloadBufferEmptying()` / `onOffloadBufferFull()` callbacks. ([AudioSink.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioSink.java))

### Media3 APIs

In Media3 1.10.0 the offload surface area sits on the **track selector**, not on the audio sink or the player. The earlier `ExoPlayer.experimentalSetOffloadSchedulingEnabled(...)` method is gone — the selector-level preference is now the only public knob. The sink still exposes `setOffloadMode(...)` but Media3 drives it internally from the track-selection outcome. ([ExoPlayer.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java))

```kotlin
// androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
// Verified 1.10.0: all three constants and all three Builder setters plus
// buildUpon()/toBundle() are present.
public static final int AUDIO_OFFLOAD_MODE_DISABLED = 0   // default; ignore offload
public static final int AUDIO_OFFLOAD_MODE_ENABLED  = 1   // use offload if track+renderer allow
public static final int AUDIO_OFFLOAD_MODE_REQUIRED = 2   // only select tracks that offload

public static final AudioOffloadPreferences DEFAULT =
    AudioOffloadPreferences.Builder().build()

class Builder {
    fun setAudioOffloadMode(@AudioOffloadMode mode: Int): Builder
    fun setIsGaplessSupportRequired(required: Boolean): Builder
    fun setIsSpeedChangeSupportRequired(required: Boolean): Builder
    fun build(): AudioOffloadPreferences
}
```

([TrackSelectionParameters.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/TrackSelectionParameters.java))

The wiring method lives on `TrackSelectionParameters.Builder` (annotated `@UnstableApi` and `@CanIgnoreReturnValue` in 1.10.0) and is inherited by `DefaultTrackSelector.ParametersBuilder`:

```kotlin
fun setAudioOffloadPreferences(prefs: AudioOffloadPreferences): TrackSelectionParameters.Builder
```

([TrackSelectionParameters.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/TrackSelectionParameters.java)) ([DefaultTrackSelector.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java))

At the sink layer:

```kotlin
// androidx.media3.exoplayer.audio.AudioSink (verified 1.10.0)
public static final int OFFLOAD_MODE_DISABLED                      = 0
public static final int OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED      = 1
public static final int OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED  = 2

@RequiresApi(29)
fun setOffloadMode(@OffloadMode mode: Int)
```

([AudioSink.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioSink.java)) The renderer translates the track selector's `AudioOffloadPreferences` outcome into one of these three sink-level modes and calls `DefaultAudioSink.setOffloadMode(...)` before each track is configured. ([MediaCodecAudioRenderer review](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/MediaCodecAudioRenderer.java))

### Listening for offload

```kotlin
player.addAudioOffloadListener(object : ExoPlayer.AudioOffloadListener {
    override fun onSleepingForOffloadChanged(isSleepingForOffload: Boolean) {
        // true once the app can actually park the rendering loop.
    }
    override fun onOffloadedPlayback(isOffloadedPlayback: Boolean) {
        // the current AudioSink is running in OUTPUT_MODE_OFFLOAD
    }
})
```

([ExoPlayer.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java))

### Trade-offs

- **Long AudioTrack buffers.** The DSP may hold 1–5 seconds of compressed audio. That drives up pause-to-silence latency. If you pause during offload, expect up to a second before the speaker actually goes silent on some SoCs.
- **Gapless is conditional.** Gapless playback across track boundaries in offload requires that the SoC's audio HAL supports it. Query via `AudioOffloadPreferences.Builder.setIsGaplessSupportRequired(true)` — the selector will refuse to offload if the device cannot meet it. ([TrackSelectionParameters.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/TrackSelectionParameters.java))
- **Playback-speed changes are conditional.** `setIsSpeedChangeSupportRequired(true)` asks the selector to only pick offload if the device supports non-1.0x playback rates on the offload path. ([TrackSelectionParameters.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/TrackSelectionParameters.java))
- **Most `AudioEffect`s don't work.** Loudness enhancer, bass boost, custom PCM processors — offload bypasses the PCM layer entirely, so nothing downstream can modify the samples.
- **Interacts poorly with tunneling.** Tunneled audio wants the codec running in the pipeline; offload wants the codec to *not* run. In practice, if you enable both, the track selector is likely to silently prefer the one the renderer can actually honour on the given track. Do not rely on both being active for the same track. (The Media3 selector source treats these as independent preferences and resolves per-track at selection time, but the concrete "which wins on device X" answer is only observable at runtime on hardware.)
- **Battery win.** The reason for all of this. In Silo's case, this matters mostly when the phone is playing a music-only media item in the background; video playback won't see the savings because the video stack keeps the CPU awake.

---

## 4. Decision matrix

Given an MKV audio `Format` (codec, channels, sampleRate), the currently resolved `AudioCapabilities` (HDMI encodings, max channel count, spatializer masks), and current display caps, the path Media3 will pick is:

| Track | Decode locally | Passthrough | Tunneling (on TV) | Offload |
| --- | --- | --- | --- | --- |
| AAC stereo (48 kHz) | yes | no (not needed) | yes — audio+video decoder pair typically both advertise `FEATURE_TunneledPlayback` on TV | yes — `ENCODING_AAC_LC` is a standard offload-eligible encoding on modern SoCs |
| AC-3 5.1 | usually yes (SW AC-3 decoder exists on most Android devices) | also possible if AVR is connected and `AudioCapabilities.supportsEncoding(ENCODING_AC3)` | yes on TV | maybe — some SoCs advertise offload for AC-3 under specific audio HAL builds; authoritative answer per device is `AudioManager.isOffloadedPlaybackSupported(...)` |
| E-AC-3 5.1 (no JOC) | varies — most SoCs have a SW decoder | yes — preferred when AVR advertises `ENCODING_E_AC3` | yes on TV | maybe — some SoCs offload plain E-AC-3 |
| E-AC-3 JOC (Atmos over DD+) | rarely — SW decoder strips JOC, so the result is not Atmos | yes — required for Atmos to reach the AVR as objects | yes on TV (AV-sync via HW AV-sync session) | no — JOC offload is not a standard offload path |
| TrueHD 7.1 | no — no SW TrueHD decoder exists in AOSP | yes — only way to play this | yes on TV | no |
| TrueHD Atmos 7.1.4 | no | yes — only way | yes on TV | no |
| AC-4 | rarely — SW AC-4 decoder is absent or spotty | yes if `AudioCapabilities.supportsEncoding(ENCODING_AC4)` | yes | maybe — some Android TV SoCs include AC-4 offload; authoritative answer is `isOffloadedPlaybackSupported(AC4 AudioFormat, attrs)` on the target device |
| DTS family (DTS, DTS-HD MA, DTS:X / DTS_UHD_P2) | rarely — AOSP does not ship a DTS decoder | varies — only works where AVR + SoC both handle the format | yes on TV | no |

The runtime resolution walks through:

1. `DefaultTrackSelector` queries `RendererCapabilities.supportsFormat(format)` for each renderer. For `MediaCodecAudioRenderer`, that call cascades down into `AudioSink.getFormatSupport(format)` → `AudioCapabilities.supportsEncoding(format.encoding)` for encoded formats.
2. If the result is `SINK_FORMAT_SUPPORTED_DIRECTLY` and the format encoding is a non-PCM encoded value, the renderer runs in bypass (passthrough).
3. If `AudioOffloadPreferences.audioOffloadMode != AUDIO_OFFLOAD_MODE_DISABLED` and the format is offload-eligible per the renderer/sink, offload is preferred over a regular PCM decode path on API 29+.
4. If `DefaultTrackSelector.Parameters.tunnelingEnabled` is true and both the selected audio renderer and video renderer advertise `TUNNELING_SUPPORTED` for the selected tracks, the session runs tunneled.

---

## 5. Kotlin code examples

### 5.1 Wiring a `DefaultRenderersFactory` + `DefaultAudioSink` for maximum passthrough capability

`DefaultRenderersFactory` already builds an audio sink that will passthrough anything `AudioCapabilities.supportsEncoding(...)` says the platform supports. Silo's `SiloPlayerFactory` uses this pattern:

```kotlin
// android-shared/src/androidMain/kotlin/com/continuum/app/common/player/SiloPlayerFactory.kt
val renderersFactory = DefaultRenderersFactory(context)
    // EXTENSION_RENDERER_MODE_ON registers Opus / FLAC / FFmpeg extensions
    // behind the platform codecs. PREFER would register them in front.
    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    // If MediaCodec init fails for the primary codec on a given format,
    // fall back to another codec for that mime type instead of aborting.
    .setEnableDecoderFallback(true)

val trackSelector = DefaultTrackSelector(context).apply {
    parameters = buildUponParameters()
        .setTunnelingEnabled(isTv)
        .setAudioOffloadPreferences(
            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    if (isTv)
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    else
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                )
                .build()
        )
        // Critical for passthrough on Android TV: re-run track selection when
        // the AVR becomes available / unavailable.
        .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
        .build()
}

val audioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
    .build()

val player: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
    .setTrackSelector(trackSelector)
    .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
    .setHandleAudioBecomingNoisy(true)
    .build()
```

No custom `buildAudioSink(...)` override is needed for passthrough — the default sink handles it. We only supply a custom override when we need to tweak buffer sizes or swap in a custom `AudioOutputProvider` (e.g. for test fakes).

### 5.2 Enabling tunneling on Android TV only

Tunneling goes on the track-selector parameters at build time, and stays there:

```kotlin
val trackSelector = DefaultTrackSelector(context)

// Gated on form factor. UiModeManager is the canonical Android TV check.
val isTv = run {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

trackSelector.parameters = trackSelector
    .buildUponParameters()
    .setTunnelingEnabled(isTv)
    .build()
```

If the user changes tracks mid-playback (for example picking a different audio language), `DefaultTrackSelector` re-runs selection and re-evaluates whether the new pair can tunnel. There is no need to toggle `setTunnelingEnabled` per track.

### 5.3 Enabling audio offload for eligible tracks

```kotlin
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences

val prefs = AudioOffloadPreferences.Builder()
    .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
    // Ask the selector to only offload if the device can do gapless on the
    // offload path. Set false to allow offload with non-gapless behaviour.
    .setIsGaplessSupportRequired(false)
    // If you want to allow rate changes (podcast speed, etc.) during offload.
    .setIsSpeedChangeSupportRequired(false)
    .build()

trackSelector.parameters = trackSelector
    .buildUponParameters()
    .setAudioOffloadPreferences(prefs)
    .build()

player.addAudioOffloadListener(object : ExoPlayer.AudioOffloadListener {
    override fun onOffloadedPlayback(isOffloadedPlayback: Boolean) {
        // Track whether the current AudioSink is in OUTPUT_MODE_OFFLOAD.
    }
    override fun onSleepingForOffloadChanged(isSleepingForOffload: Boolean) {
        // true once the player has parked its rendering loop to let the
        // CPU sleep. A good signal for UI "now-playing" icons.
    }
})
```

Set `AUDIO_OFFLOAD_MODE_REQUIRED` if you only want to select tracks that can actually offload (useful for a "low-power music mode"), or `AUDIO_OFFLOAD_MODE_DISABLED` to turn it off. The `DEFAULT` value of `AudioOffloadPreferences` has `AUDIO_OFFLOAD_MODE_DISABLED`. ([TrackSelectionParameters.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/TrackSelectionParameters.java))

### 5.4 Listening for `AudioCapabilities` changes (HDMI plug/unplug)

If you need capability changes outside of the player's built-in track re-invalidation (for instance, to drive UI that says "Dolby Atmos available" on the Now Playing screen when the AVR powers on), subscribe with `AudioCapabilitiesReceiver`:

```kotlin
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioCapabilitiesReceiver

class AtmosAvailabilityWatcher(private val context: Context) {
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    private var receiver: AudioCapabilitiesReceiver? = null

    fun start(onChange: (AudioCapabilities) -> Unit) {
        val r = AudioCapabilitiesReceiver(
            context,
            /* listener = */ AudioCapabilitiesReceiver.Listener(onChange),
            audioAttributes,
            /* routedDevice = */ null
        )
        val initial: AudioCapabilities = r.register()
        receiver = r
        onChange(initial)
    }

    fun stop() {
        receiver?.unregister()
        receiver = null
    }
}

// Usage:
val watcher = AtmosAvailabilityWatcher(context)
watcher.start { caps ->
    val atmosOverDdp = caps.supportsEncoding(C.ENCODING_E_AC3_JOC)
    val trueHd       = caps.supportsEncoding(C.ENCODING_DOLBY_TRUEHD)
    val ac4          = caps.supportsEncoding(C.ENCODING_AC4)
    // Update NowPlaying UI, analytics, or playback preferences.
}
```

The first call to `register()` returns the current snapshot; subsequent changes (HDMI plug, `AudioDeviceCallback` add/remove, spatializer mask change, external-surround-sound toggle) fire the listener. Remember to `unregister()` when the scope ends. ([AudioCapabilitiesReceiver.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioCapabilitiesReceiver.java))

---

## Sources

- Media3 1.10.0 source, `androidx/media` release branch:
    - https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/C.java
    - https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/TrackSelectionParameters.java
    - https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java
    - https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultRenderersFactory.java
    - https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioCapabilities.java
    - https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioCapabilitiesReceiver.java
    - https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioSink.java
    - https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioSink.java
    - https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/MediaCodecAudioRenderer.java
    - https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java
- Android platform docs (the `developer.android.com/reference/...` pages below were consulted but several returned an index/navigation shell during this write-up — claims sourced solely from them are marked (unverified)):
    - https://developer.android.com/media/media3/exoplayer/audio-offload
    - https://developer.android.com/reference/android/media/AudioFormat
    - https://developer.android.com/reference/android/media/AudioManager#isOffloadedPlaybackSupported(android.media.AudioFormat,%20android.media.AudioAttributes)
    - https://developer.android.com/reference/android/media/AudioTrack.Builder#setOffloadedPlayback(boolean)
    - https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities#FEATURE_TunneledPlayback
    - https://developer.android.com/reference/androidx/media3/exoplayer/audio/DefaultAudioSink
    - https://developer.android.com/reference/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.Parameters.Builder
- Silo App codebase, the already-wired reference implementation for this document:
    - `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/SiloPlayerFactory.kt`

## Validation log

- corrected: multiple source URL anchors pointed at the moving `release` branch (`raw.githubusercontent.com/androidx/media/release/...` and `github.com/androidx/media/blob/release/libraries/...`) → repointed all in-text citations to the pinned `1.10.0` tag so the Sources section aligns with "Document version: Media3 1.10.0".
- corrected: `ENCODING_DTS_UHD_P2` row said "Introduced: API 32" → per AOSP `AudioFormat.java`, `DTS_UHD_P2` (int 30) and related `DTS_UHD_P1` / `DTS_HD_MA` were added in API 34. Table corrected.
- corrected: `ENCODING_DOLBY_TRUEHD` introduction value `(unverified — API 21)` was an under-count; the AOSP source has the constant declared as added in API 25 (int value 14 sits after a gap from 8 to 14, consistent with a later SDK addition). Table updated.
- verified: `OUTPUT_MODE_PCM = 0`, `OUTPUT_MODE_OFFLOAD = 1`, `OUTPUT_MODE_PASSTHROUGH = 2` on `DefaultAudioSink` 1.10.0. (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioSink.java)
- verified: `DefaultAudioSink.Builder` setters — `setAudioProcessors`, `setEnableFloatOutput`, `setEnableAudioOutputPlaybackParameters`, `setAudioTrackBufferSizeProvider` (`@Deprecated`), `setAudioOffloadSupportProvider` (`@Deprecated`), `setAudioTrackProvider` (`@Deprecated`), `setAudioOutputProvider`, `setExperimentalAudioOffloadListener`, `setAudioCapabilities` (`@Deprecated`). Annotation detail added in §1 Builder block.
- verified: `setOffloadMode(@OffloadMode int)` is a `default` method on the `AudioSink` interface (with `@RequiresApi(29)`) and is **not** exposed on `DefaultAudioSink.Builder`. Text aligned.
- verified: `AudioSink.Listener` interface includes `onAudioCapabilitiesChanged()`, `onOffloadBufferEmptying()`, `onOffloadBufferFull()` (all default-method in 1.10.0). (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioSink.java)
- verified: `AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY / SUPPORTED_WITH_TRANSCODING / UNSUPPORTED` delegate to `AudioOutputProvider.FORMAT_*` constants. (same source)
- verified: `AudioSink.OFFLOAD_MODE_DISABLED = 0`, `OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED = 1`, `OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED = 2`. (same source)
- verified: `AudioOffloadPreferences` under `androidx.media3.common.TrackSelectionParameters` has the three `AUDIO_OFFLOAD_MODE_*` constants and the three Builder setters `setAudioOffloadMode`, `setIsGaplessSupportRequired`, `setIsSpeedChangeSupportRequired`, plus `buildUpon()`/`toBundle()`/`fromBundle()`. (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/TrackSelectionParameters.java)
- verified: `setAudioOffloadPreferences(AudioOffloadPreferences)` lives on `TrackSelectionParameters.Builder` (base class), inherited by `DefaultTrackSelector.Parameters.Builder`. (same source)
- verified: `experimentalSetOffloadSchedulingEnabled(...)` on `ExoPlayer.Builder` is **absent** in 1.10.0 — matches the doc's claim. (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java)
- verified: `ExoPlayer.AudioOffloadListener.onSleepingForOffloadChanged(boolean)` and `onOffloadedPlayback(boolean)` both default methods present in 1.10.0. (same source)
- still unverified: whether tunneled + passthrough coexist on a specific device (e.g. Shield 2019, Chromecast with Google TV 4K) without audio underruns — only answerable on hardware. The decision matrix in §4 reflects the design intent; runtime verification is part of doc 08 §9.
- still unverified: whether offload + tunneling both enabled for the same audio track on a given SoC silently drop one — no authoritative documentation. The §3 "trade-offs" text flags this and aligns with the matrix.
- still unverified: Silo's `SiloPlayerFactory` already wires `setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)` — cross-check claim in doc 07 §4 to keep the cited line numbers accurate as that file evolves.
