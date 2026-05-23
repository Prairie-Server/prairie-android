# Dolby Atmos and High-End Audio Codec Playback with Media3

Document version: Media3 1.10.0

This document covers audio codec coverage for the Silo Android and Android TV clients: what each codec is, what Media3 / `MediaCodec` can decode locally, what has to leave the device as a passthrough bitstream, and how to program around it with `AudioCapabilities`, `TrackSelectionParameters`, and the Android `Spatializer`.

Passthrough mechanics (direct / IEC 61937), tunneled playback, and audio offload are in the sibling document `05-audio-passthrough-tunneling-offload.md`. This document only references those concepts where a codec decision depends on them.

## 1. The codec landscape

| Codec | MIME (Media3) | Typical channels | Lossy / lossless | Atmos-capable | Notes |
|---|---|---|---|---|---|
| AC-3 / Dolby Digital | `audio/ac3` | 5.1 | Lossy (~640 kbps) | No | Legacy, broad AVR support. |
| E-AC-3 / Dolby Digital Plus | `audio/eac3` | Up to 7.1 | Lossy | Only as `eac3-joc` | Extension of AC-3. |
| E-AC-3 JOC | `audio/eac3-joc` | 5.1 bed + objects | Lossy | Yes | Atmos over DD+ on streaming services. JOC = Joint Object Coding. |
| Dolby TrueHD / MLP | `audio/true-hd` | Up to 7.1.4 | Lossless | Yes | Blu-ray / UHD BD Atmos track. Very high bitrate (often 10-18 Mbps on UHD). |
| AC-4 | `audio/ac4` | Varies | Lossy | Yes | Newer Dolby codec for broadcast / streaming. Device support is narrow — see Section 3. |
| DTS | `audio/vnd.dts` | 5.1 | Lossy | No | Legacy DTS. |
| DTS-HD HRA | `audio/vnd.dts.hd` | 7.1 | Lossy (high-bitrate extension) | No | DTS-HD High Resolution. |
| DTS-HD MA | `audio/vnd.dts.hd` | Up to 7.1+ | Lossless | No | DTS-HD Master Audio. `codecs` strings `dtsh` / `dtsl` both map here per the Media3 `MimeTypes` source. |
| DTS Express | `audio/vnd.dts.hd;profile=lbr` | 5.1 / 7.1 | Lossy (low bitrate) | No | DTS-HD LBR. |
| DTS:X | `audio/vnd.dts.uhd;profile=p2` (Media3 `AUDIO_DTS_X`, `@UnstableApi`) | Bed + objects | Lossy | DTS object audio | DTS's Atmos competitor. |
| AAC-LC / HE-AAC / xHE-AAC | `audio/mp4a-latm` | Stereo / 5.1 / 7.1 | Lossy | No | Universally supported locally. |
| FLAC | `audio/flac` | Up to 8 ch | Lossless | No | Universal local decode. |
| Opus | `audio/opus` | Up to 8 ch | Lossy | No | Universal since API 21+, direct passthrough constant `AudioFormat.ENCODING_OPUS` added API 30. |
| Vorbis | `audio/vorbis` | Up to 8 ch | Lossy | No | Matroska / WebM. Universal local decode. |

MIME strings above are taken verbatim from `androidx.media3.common.MimeTypes` at tag `1.10.0` (see [Media3 MimeTypes source](https://github.com/androidx/media/blob/1.10.0/libraries/common/src/main/java/androidx/media3/common/MimeTypes.java)). Note that `AUDIO_DTS_X` is annotated `@UnstableApi` in 1.10.0.

There is no separate `AUDIO_MLP` constant in `MimeTypes`; TrueHD streams are flagged with `audio/true-hd` regardless of whether the underlying MLP container carries Atmos metadata.

## 2. How Dolby Atmos is actually carried

Dolby Atmos is not itself a codec — it is a layer of object metadata carried on top of one of three bitstreams:

1. **E-AC-3 with Joint Object Coding (E-AC-3 JOC)** — streaming Atmos. MIME `audio/eac3-joc`. The decoder recovers the 5.1 bed, then reconstructs height / object channels from the JOC side information.
2. **Dolby TrueHD with Atmos** — Blu-ray Atmos. MIME `audio/true-hd`. Atmos metadata is carried in the TrueHD substream.
3. **AC-4** — newer broadcast/streaming profile. MIME `audio/ac4`. Atmos objects are intrinsic to the codec design.

Implications for this app:

- **"Atmos" in the UI should never mean "7.1.4 channels decoded on the phone".** On Android it is almost always bitstream passthrough to an external AVR, soundbar, or Atmos-capable TV over HDMI / SPDIF / eARC. Some Pixel phones, Galaxy phones, and Chromebooks advertise Atmos for headphones — that is Dolby's virtualizer / head-tracked binaural renderer running on the device, not genuine object rendering to speakers. For the Silo client, flagging a track as "Atmos available" in the UI should be gated on the output route (see `AudioCapabilities` + `Spatializer` in Sections 4 and 8).
- E-AC-3 that is not JOC is still surround but not Atmos. The distinguishing bit is the MIME type (`eac3` vs `eac3-joc`) or the substream descriptor inside the bitstream. Media3's MKV extractor will surface `audio/eac3-joc` when JOC side-data is present, but you should not trust this to be 100% reliable across all muxers — fall back to treating `eac3` as "surround, maybe Atmos after AVR inspection".
- The channel count reported on a `Format` for a JOC track is the 5.1 bed count (6), not the final rendered channel count after object reconstruction. `Format.channelCount == 6` does not mean "no Atmos".

## 3. Media3 / `MediaCodec` local decode vs. passthrough

Local decode support for the compressed Dolby / DTS codecs is a per-device decision made by the SoC vendor and OEM, not by Media3. Media3 asks `MediaCodecList` for a decoder with the matching MIME type; if one is not advertised it falls back to passthrough through `AudioTrack` in an IEC 61937 frame.

| MIME | Local decode (MediaCodec) | Passthrough (AudioTrack direct) | Typical Silo path |
|---|---|---|---|
| `audio/ac3` | Common on Android TV, less common on phones. Check `MediaCodecList.findDecoderForFormat`. | Widely supported on HDMI sinks. | Decode on phones if the decoder exists, passthrough on TV. |
| `audio/eac3` | Patchy on phones, more common on Android TV. | Widely supported. | Passthrough preferred on TV. |
| `audio/eac3-joc` | Decoders are rare. Most devices cannot reconstruct the object bed locally. | Supported when the route announces `ENCODING_E_AC3_JOC`. If not, fall back to `ENCODING_E_AC3` (AVR will play the 5.1 bed without Atmos — this is the codec's intended backwards-compatible behavior; the `ENCODING_E_AC3_JOC` Javadoc in AOSP calls this out explicitly). | Passthrough JOC if route supports it, else passthrough as plain E-AC-3. |
| `audio/true-hd` | Essentially no local decoder on Android devices — the licensing and CPU cost are a non-starter for phones. Verified by the absence of any `TRUEHD` decoder in stock `MediaCodecList.REGULAR_CODECS` on the tested devices; the Media3 FFmpeg extension can provide software TrueHD decoding but is source-only (doc 01 §2). | Supported on Android TV / HDMI routes that advertise `ENCODING_DOLBY_TRUEHD`. | Passthrough only. If the route cannot accept TrueHD, the server must transcode (e.g. to E-AC-3 or AAC). |
| `audio/ac4` | Newer Android TV SoCs only. | Constant `ENCODING_AC4` added in API 28 (verified in AOSP `AudioFormat.java`, int value 17). | Passthrough on AC-4-capable sinks. Per-SoC coverage varies widely — only hardware probing via `AudioCapabilities.supportsEncoding(C.ENCODING_AC4)` is authoritative. |
| `audio/vnd.dts` / `audio/vnd.dts.hd` / `AUDIO_DTS_X` | DTS decoders are licensed, and most Android devices ship without them. | Supported on DTS-capable AVRs via HDMI. | Passthrough when possible, else server-side transcode. |
| `audio/mp4a-latm` (AAC) | Universal local decode. | N/A (always decoded). | Always decode. |
| `audio/flac` / `audio/opus` / `audio/vorbis` | Universal local decode. | N/A. | Always decode. |

Probing at runtime is the safe pattern — never hard-code "TrueHD passthrough works on device X":

```kotlin
import android.media.MediaCodecList
import android.media.MediaFormat
import androidx.media3.common.MimeTypes

fun hasLocalDecoderFor(mimeType: String, sampleRate: Int, channelCount: Int): Boolean {
    val format = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount)
    val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    return list.findDecoderForFormat(format) != null
}

val canDecodeTrueHdLocally =
    hasLocalDecoderFor(MimeTypes.AUDIO_TRUEHD, 48_000, 8)
```

`findDecoderForFormat` is documented at [AudioFormat.java in AOSP — `android.media.MediaCodecList`](https://developer.android.com/reference/android/media/MediaCodecList#findDecoderForFormat(android.media.MediaFormat)).

## 4. `AudioCapabilities` and `AudioCapabilitiesReceiver`

Media3's capability model lives in **`androidx.media3.exoplayer.audio.AudioCapabilities`** — verified in the 1.10.0 source: the class sits in the `exoplayer` module, not in `androidx.media3.common.audio`. See [AudioCapabilities.java @ 1.10.0](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioCapabilities.java).

### 4.1 Getting capabilities

```kotlin
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioCapabilities

// 1.10.0 preferred factory. The earlier getCapabilities(Context) overload is
// deprecated in 1.10.0 (see the @Deprecated annotations in AudioCapabilities.java).
val caps = AudioCapabilities.getCapabilities(
    /* context = */ context,
    /* audioAttributes = */ AudioAttributes.DEFAULT, // or your player's attributes
    /* routedDevice = */ null,                       // null = current default route
    /* spatializerChannelMasks = */ emptyList(),     // supply from SpatializerWrapper or leave empty
)

val supportsEac3Joc = caps.supportsEncoding(C.ENCODING_E_AC3_JOC)
val maxChannels = caps.getMaxChannelCount()
```

The Media3 encoding constants in `androidx.media3.common.C` mirror `android.media.AudioFormat.ENCODING_*` values (same integer values), so `caps.supportsEncoding(C.ENCODING_E_AC3_JOC)` is equivalent to a direct `AudioFormat.ENCODING_E_AC3_JOC` lookup.

Under the hood (from the 1.10.0 source):

- On **API < 29** the capabilities are derived from the sticky broadcast of `AudioManager.ACTION_HDMI_AUDIO_PLUG`, pulling `EXTRA_ENCODINGS` and `EXTRA_MAX_CHANNEL_COUNT` from the intent. See [`ACTION_HDMI_AUDIO_PLUG`](https://developer.android.com/reference/android/media/AudioManager#ACTION_HDMI_AUDIO_PLUG).
- On **API 29+** an `Api29` helper calls `AudioTrack.isDirectPlaybackSupported(AudioFormat, AudioAttributes)` per encoding to probe what the active route will accept as passthrough.
- On **API 33+** an `Api33` helper calls `AudioManager.getDirectProfilesForAttributes(AudioAttributes)` which returns a richer `List<AudioProfile>` describing each supported encoding + channel mask + sample rate combo. This replaces the deprecated `AudioTrack.isDirectPlaybackSupported`. See [`AudioManager.getDirectProfilesForAttributes`](https://developer.android.com/reference/android/media/AudioManager#getDirectProfilesForAttributes(android.media.AudioAttributes)).
- On **API 32+**, spatializer channel masks are merged in via `SpatializerWrapper` so that formats that the `Spatializer` can render are treated as supported even if the physical route cannot passthrough them.

### 4.2 Key public instance methods in 1.10.0

```kotlin
// Whether any of the device's routes (current or via Spatializer) can render this encoding.
fun supportsEncoding(@C.Encoding encoding: Int): Boolean

// Max channel count the sink can render (PCM output ceiling).
fun getMaxChannelCount(): Int

// Which channel masks the Spatializer will virtualize to the current output.
// Only populated on API 32+ via SpatializerWrapper; empty list otherwise.
// (ImmutableList<Integer> in the Java API; translates to List<Int> at a Kotlin call site.)
fun getSpatializerChannelMasks(): com.google.common.collect.ImmutableList<Integer>

// Channel masks the wired output declares as PCM-renderable (speaker layout).
fun getSpeakerLayoutChannelMasks(): com.google.common.collect.ImmutableList<Integer>

// True if this Format can be passed through as a direct bitstream given the
// attributes. Driven by the Api29 / Api33 probes above. 1.10.0 also retains
// a deprecated single-arg overload (Format only) that the non-deprecated
// two-arg form replaces.
fun isPassthroughPlaybackSupported(format: Format, audioAttributes: AudioAttributes): Boolean

// Returns (encoding, channelConfig) if the Format passes; null otherwise.
// This is what DefaultAudioSink uses to pick the AudioTrack encoding for
// passthrough. 1.10.0 also retains a deprecated single-arg overload.
fun getEncodingAndChannelConfigForPassthrough(
    format: Format,
    audioAttributes: AudioAttributes,
): Pair<Int, Int>?
```

Note: `AudioCapabilities` in 1.10.0 does **not** expose a public
`getMaxSupportedChannelCountForPassthrough(encoding, sampleRate)` instance
method. The equivalent logic lives on a `static` method inside the private
`Api29` inner class (`public static int
getMaxSupportedChannelCountForPassthrough(@C.Encoding int encoding, int
sampleRate, AudioAttributes audioAttributes)`), which is not reachable from
outside the class. If you need per-encoding channel-count caps, you either
subclass `AudioCapabilities` (which is final in 1.10.0 — i.e. you cannot) or
fall back to `getMaxChannelCount()` across encodings and accept the
approximation. This directly impacts the `AudioCapabilityManager.kt:110-111`
"stub" flagged in doc 07 §9 — the stub is not actually behind the real API,
because no public instance method is available to call.

(Method list verified against the 1.10.0 source: [AudioCapabilities.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioCapabilities.java).)

### 4.3 Hotplug — `AudioCapabilitiesReceiver`

Plugging an HDMI cable, docking to an AVR, connecting Bluetooth, or changing the routed device changes what encodings the device can emit. `AudioCapabilitiesReceiver` (also in `androidx.media3.exoplayer.audio`) listens for those transitions and re-computes:

```kotlin
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioCapabilitiesReceiver

val receiver = AudioCapabilitiesReceiver(
    /* context = */ applicationContext,
    /* listener = */ object : AudioCapabilitiesReceiver.Listener {
        override fun onAudioCapabilitiesChanged(newCapabilities: AudioCapabilities) {
            // Update UI (e.g. Atmos badge), re-run track selection, or rebuild the sink.
        }
    },
    /* audioAttributes = */ AudioAttributes.DEFAULT,
    /* routedDevice = */ null,
)

val initial: AudioCapabilities = receiver.register()
// ...later:
receiver.unregister()
```

Internally it subscribes to `AudioManager.ACTION_HDMI_AUDIO_PLUG`, an `AudioDeviceCallback` for add/remove events, and the `Spatializer` state listener on API 32+. (Verified against [`AudioCapabilitiesReceiver.java` @ 1.10.0](https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioCapabilitiesReceiver.java).)

`ExoPlayer.Builder` wires a receiver automatically for the default `AudioSink`; you only need to manage one yourself if you are reading capabilities outside the player (for example, deciding which stream variant to request from the Silo server before instantiating the player).

## 5. `AudioFormat` encoding constants

Values and API levels below are taken from the AOSP `AudioFormat.java` source ([platform_frameworks_base/media/java/android/media/AudioFormat.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/AudioFormat.java)) and the [AudioFormat reference page](https://developer.android.com/reference/android/media/AudioFormat). API levels come from the Android SDK API diffs ([api_diff/28](https://developer.android.com/sdk/api_diff/28/), [api_diff/29](https://developer.android.com/sdk/api_diff/29/), [api_diff/30](https://developer.android.com/sdk/api_diff/30/), [api_diff/34](https://developer.android.com/sdk/api_diff/34/)). Where the API level was not reachable via a diff page it is marked "(verify)".

| Constant | Int value | Added in API | Purpose |
|---|---|---|---|
| `ENCODING_PCM_16BIT` | 2 | 3 (AudioFormat baseline) | PCM 16-bit output path. |
| `ENCODING_PCM_FLOAT` | 4 | 21 | PCM 32-bit float. |
| `ENCODING_AC3` | 5 | 21 | Dolby Digital passthrough. |
| `ENCODING_E_AC3` | 6 | 21 | DD+ passthrough. |
| `ENCODING_DTS` | 7 | 23 | DTS passthrough. |
| `ENCODING_DTS_HD` | 8 | 23 | DTS-HD HRA / MA passthrough. |
| `ENCODING_DOLBY_TRUEHD` | 14 | 25 | TrueHD passthrough. |
| `ENCODING_AC4` | 17 | 28 | AC-4 passthrough. |
| `ENCODING_E_AC3_JOC` | 18 | 28 | DD+ with Atmos objects. A device advertising only `ENCODING_E_AC3` can still receive a JOC stream tagged as `ENCODING_E_AC3` — it will play the 5.1 bed per the Javadoc on `ENCODING_E_AC3_JOC`. |
| `ENCODING_DOLBY_MAT` | 19 | 29 | Dolby MAT (Metadata-enhanced Audio Transmission). Used to tunnel TrueHD / PCM-with-metadata over HDMI. |
| `ENCODING_OPUS` | 20 | 30 | Opus passthrough (rarely used — Opus is almost always decoded locally). |
| `ENCODING_DTS_UHD_P1` | 27 | 34 | DTS:X Profile 1. Also exposed under the deprecated alias `ENCODING_DTS_UHD` (same integer). Confirmed in AOSP `AudioFormat.java`: `ENCODING_DTS_UHD` is `@Deprecated` with comment "Use ENCODING_DTS_UHD_P1 instead." |
| `ENCODING_DTS_HD_MA` | 29 | 34 | Explicit lossless-DTS variant. Use this for DTS-HD MA and `ENCODING_DTS_HD` for HRA / 8-ch Discrete / Express. |
| `ENCODING_DTS_UHD_P2` | 30 | 34 | DTS:X Profile 2. |

Integer values verified against the AOSP `AudioFormat.java` source in April 2026 (https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/AudioFormat.java). API-level values correspond to the SDK diff pages referenced below.

AOSP-only or `@hide` constants like `ENCODING_AC4_L4` (AC-4 Level 4) and `ENCODING_DSD` show up in newer branches of AOSP but are not in the public SDK as of Media3 1.10.0's baseline (`compileSdk = 36` in `gradle/libs.versions.toml`). Do not code against them — they won't resolve in `android.jar` and the runtime will not see them even if you write them as integer literals.

The AudioFormat `int` values are intentionally the same as `androidx.media3.common.C.ENCODING_*`, so capabilities queries cross-reference cleanly between `AudioFormat` / `AudioTrack` / Media3 `AudioCapabilities`.

## 6. Channel layout handling

`androidx.media3.common.Format.channelCount` is the decoded or bed channel count as reported by the container / bitstream:

- **AAC 5.1** -> 6.
- **TrueHD 7.1.4 Atmos** -> 8 (the bed) — the extra 4 height channels come from the object metadata when rendered by an Atmos-capable sink. `Format.channelCount` does not reflect them.
- **E-AC-3 JOC** -> 6 (5.1 bed), same caveat.
- **DTS:X** -> 8 (7.1 bed) in most content.

Downmix behavior when the route cannot render the full layout:

1. **Local decode + stereo speakers** — `DefaultAudioSink` downmixes to stereo using the matrix supplied by the decoder. `TrackSelectionParameters.Builder.setMaxAudioChannelCount(2)` will bias selection toward a native stereo track if one exists in the MKV (frequently the case for commentary / director tracks).
2. **Local decode + 5.1 / 7.1 PCM output (HDMI LPCM or USB DAC)** — `DefaultAudioSink` emits multichannel PCM. The receiver must advertise the channel mask in its EDID / wired-headset routing info.
3. **Passthrough + AVR** — decoding is offloaded entirely; the bed / object distinction is re-created by the AVR's Atmos renderer.
4. **Passthrough JOC fallback to E-AC-3** — covered in Section 3. The AVR will get the 5.1 bed, no Atmos.

For dual-mono AC-3 content, `Format.channelCount` will be 2; treat it as a normal stereo stream. There is no Media3 API for dual-mono channel-mode selection in 1.10.0 — if the user needs a specific dual-mono leg (rare), handle it by picking a different audio track in the MKV if the server exposes one.

## 7. Practical usage — wiring it up

### 7.1 Inspecting a `Format`

```kotlin
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes

fun describeAudioFormat(format: Format): String = buildString {
    append("mime=${format.sampleMimeType}")
    append(" ch=${format.channelCount}")
    append(" hz=${format.sampleRate}")
    format.codecs?.let { append(" codecs=$it") }
    format.bitrate.takeIf { it != Format.NO_VALUE }?.let { append(" bps=$it") }
}

val isAtmosCandidate = format.sampleMimeType in setOf(
    MimeTypes.AUDIO_E_AC3_JOC,
    MimeTypes.AUDIO_TRUEHD,
    MimeTypes.AUDIO_AC4,
)
```

`Format.codecs` carries the ISO/IEC codec string (e.g. `ec-3`, `mlpa`, `dtsx`) which `MimeTypes.getMediaMimeType` uses to disambiguate DTS variants — see the switch in `MimeTypes.java` that maps `dtsh` / `dtsl` to `AUDIO_DTS_HD`, `dtse` to `AUDIO_DTS_EXPRESS`, `dtsx` to `AUDIO_DTS_X`.

### 7.2 Decision tree: decode vs. passthrough vs. transcode fallback

```kotlin
import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioCapabilities

enum class AudioPath { Decode, Passthrough, ServerTranscode }

fun choosePath(
    context: Context,
    format: Format,
    attributes: AudioAttributes = AudioAttributes.DEFAULT,
): AudioPath {
    val caps = AudioCapabilities.getCapabilities(
        context, attributes, /* routedDevice = */ null, /* spatializerChannelMasks = */ emptyList()
    )

    // Universally-decodable codecs: always decode locally.
    if (format.sampleMimeType in setOf(
            MimeTypes.AUDIO_AAC,
            MimeTypes.AUDIO_FLAC,
            MimeTypes.AUDIO_OPUS,
            MimeTypes.AUDIO_VORBIS,
        )
    ) {
        return AudioPath.Decode
    }

    // Passthrough-first codecs (Dolby / DTS families).
    if (caps.isPassthroughPlaybackSupported(format, attributes)) {
        return AudioPath.Passthrough
    }

    // Local decoder available? (AC-3 on some SoCs, AAC always, etc.)
    val hasLocalDecoder = hasLocalDecoderFor(
        format.sampleMimeType ?: return AudioPath.ServerTranscode,
        format.sampleRate.takeIf { it != Format.NO_VALUE } ?: 48_000,
        format.channelCount.takeIf { it != Format.NO_VALUE } ?: 2,
    )
    if (hasLocalDecoder) return AudioPath.Decode

    // Last resort: ask the server to transcode (AAC 2.0 is a safe target).
    return AudioPath.ServerTranscode
}
```

In Silo's direct-play negotiation with the server, this path decision should be made once after `AudioCapabilitiesReceiver.register()` returns, and re-run whenever the receiver fires a change event. The resulting capability set is what you send to the server's `/playback/decide` endpoint (or equivalent) to pick between direct-stream, remux, or transcode.

### 7.3 `TrackSelectionParameters` — codec and channel preferences

```kotlin
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters

val params = TrackSelectionParameters.Builder(context)
    // Prefer Atmos-capable MIME types in this order if present in the MKV.
    .setPreferredAudioMimeTypes(
        MimeTypes.AUDIO_TRUEHD,
        MimeTypes.AUDIO_E_AC3_JOC,
        MimeTypes.AUDIO_AC4,
        MimeTypes.AUDIO_E_AC3,
        MimeTypes.AUDIO_AC3,
        MimeTypes.AUDIO_AAC,
    )
    .setPreferredAudioLanguage("eng")
    // Do not downgrade to stereo if a multichannel track is playable.
    .setMaxAudioChannelCount(8)
    .setPreferredAudioRoleFlags(C.ROLE_FLAG_MAIN)
    .build()

player.trackSelectionParameters = params
```

Setter names in 1.10.0 (verified in [TrackSelectionParameters.java @ 1.10.0](https://github.com/androidx/media/blob/1.10.0/libraries/common/src/main/java/androidx/media3/common/TrackSelectionParameters.java)):

- `setPreferredAudioMimeType(String?)` / `setPreferredAudioMimeTypes(vararg String)`
- `setPreferredAudioLanguages(vararg String)`
- `setPreferredAudioLabels(vararg String)`
- `setPreferredAudioRoleFlags(@C.RoleFlags Int)`
- `setMaxAudioChannelCount(Int)`
- `setMaxAudioBitrate(Int)`
- `setAudioOffloadPreferences(AudioOffloadPreferences)` — covered in the offload doc.

### 7.4 Player `AudioAttributes`

```kotlin
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C

player.setAudioAttributes(
    AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .setUsage(C.USAGE_MEDIA)
        .build(),
    /* handleAudioFocus = */ true,
)
```

`contentType = MOVIE` signals to the system that this is film content, which is a prerequisite for the `Spatializer` to engage on API 32+. `USAGE_MEDIA` is also required — `USAGE_GAME` or `USAGE_NOTIFICATION` will not be spatialized. (See Section 8.)

## 8. Spatial audio (Android 12L / API 32+)

### 8.1 The `Spatializer`

`android.media.Spatializer` was added in **API 32 (Android 12L)**. Reference: [Spatializer source in AOSP](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/media/java/android/media/Spatializer.java).

Core API:

```kotlin
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.Spatializer

val spatializer: Spatializer = context.getSystemService(AudioManager::class.java).spatializer

val level: Int = spatializer.immersiveAudioLevel
// SPATIALIZER_IMMERSIVE_LEVEL_NONE             = 0
// SPATIALIZER_IMMERSIVE_LEVEL_MULTICHANNEL     = 1  -> virtualizes bed channels
// SPATIALIZER_IMMERSIVE_LEVEL_OTHER            = -1
// Note: an internal `SPATIALIZER_IMMERSIVE_LEVEL_MCHAN_BED_PLUS_OBJECTS = 2`
// exists in AOSP but is marked `@hide`, i.e. not part of the public SDK. Do
// not reference it by name in app code; check for value 2 by comparison if
// you genuinely need to differentiate bed+objects. (AOSP Spatializer.java)

val available = spatializer.isAvailable   // can the current route host spatialization?
val enabled   = spatializer.isEnabled     // has the user turned it on?

val format7point1 = AudioFormat.Builder()
    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
    .setSampleRate(48_000)
    .setChannelMask(AudioFormat.CHANNEL_OUT_7POINT1)
    .build()

val attrs = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
    .build()

val canSpatializeMovie71 = spatializer.canBeSpatialized(attrs, format7point1)
```

`canBeSpatialized` does not require the content to be Atmos — it tells you whether a given *PCM* channel layout, under the given attributes, will be spatialized on the current route. Per the AOSP Javadoc, a 7.1.2 or 7.1.4 query can return `true` even when the system downmixes the height / side pair, because the channels are still audible. So this method is a "will we engage the virtualizer?" check, not a "will you hear true Atmos?" check.

### 8.2 Media3's integration

`DefaultTrackSelector` wires in the Spatializer automatically on API 32+ via `SpatializerWrapper` (see grep in [DefaultTrackSelector.java @ 1.10.0](https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java)). It:

1. Creates a `SpatializerWrapper` when the selector is first used on API 32+.
2. Registers an `OnSpatializerStateChangedListener` and triggers a re-selection whenever spatialization availability changes (user toggles the setting in Settings > Sound > Spatial audio, headphones connect/disconnect, etc.).
3. During audio-track scoring, treats a multichannel track as playable on a stereo output if the spatializer will render it, so a 5.1 / 7.1 track is preferred over a native stereo track when spatialization is active.

This behavior is on by default. You can opt out through `DefaultTrackSelector.Parameters.Builder.setConstrainAudioChannelCountToDeviceCapabilities(false)` if you want the selector to ignore the Spatializer and pick the largest channel count the container offers. Method verified present on `DefaultTrackSelector.Parameters.Builder` in 1.10.0.

### 8.3 Showing an accurate "Atmos" or "Spatial" badge

The pragmatic truth table for the UI:

| Route | Content | What to show |
|---|---|---|
| Wired / Bluetooth headphones + `spatializer.isAvailable && isEnabled && level == MCHAN_BED_PLUS_OBJECTS` + format in {JOC, TrueHD, AC-4} | Atmos content | "Dolby Atmos (Spatial Audio)". Content gets object-aware virtualization if the decoder emits objects. |
| Speakers on a phone/tablet | Anything | Do not show Atmos. Downmixed PCM. |
| HDMI / eARC to AVR that advertises `ENCODING_E_AC3_JOC` / `ENCODING_DOLBY_TRUEHD` / `ENCODING_AC4` | Matching Atmos track | "Dolby Atmos (passthrough to <device>)" if you can name the route. |
| HDMI to AVR without the JOC / TrueHD encoding bit | JOC content | "Dolby Digital Plus" (the 5.1 bed passes through, AVR renders 5.1). |
| USB DAC / headphones no Spatializer | Anything | Just codec name, no Atmos. |

The "Atmos" badge should therefore be gated on: (a) the track's MIME type, and (b) `AudioCapabilities.isPassthroughPlaybackSupported` for the Atmos-bearing encoding, and/or (c) `Spatializer.canBeSpatialized` for the relevant multichannel PCM layout with `CONTENT_TYPE_MOVIE`.

## Sources

All links below were consulted while writing this document.

- [Media3 1.10.0 `MimeTypes.java`](https://github.com/androidx/media/blob/1.10.0/libraries/common/src/main/java/androidx/media3/common/MimeTypes.java) — authoritative MIME string values, including `@UnstableApi AUDIO_DTS_X`.
- [Media3 1.10.0 `AudioCapabilities.java`](https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioCapabilities.java) — factory methods, `supportsEncoding`, `getMaxChannelCount`, `isPassthroughPlaybackSupported`, `getEncodingAndChannelConfigForPassthrough`, `Api29` / `Api33` probes.
- [Media3 1.10.0 `AudioCapabilitiesReceiver.java`](https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioCapabilitiesReceiver.java) — HDMI, device-callback, and `SpatializerWrapper` integration.
- [Media3 1.10.0 `TrackSelectionParameters.java`](https://github.com/androidx/media/blob/1.10.0/libraries/common/src/main/java/androidx/media3/common/TrackSelectionParameters.java) — `setPreferredAudioMimeTypes`, `setMaxAudioChannelCount`, `setAudioOffloadPreferences`.
- [Media3 1.10.0 `DefaultTrackSelector.java`](https://github.com/androidx/media/blob/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java) — `SpatializerWrapper` usage and re-selection on state change.
- [AOSP `AudioFormat.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/AudioFormat.java) — encoding constants and integer values.
- [AOSP `Spatializer.java`](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/media/java/android/media/Spatializer.java) — immersive levels, `canBeSpatialized`, listener API.
- [AOSP `AudioTrack.java`](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/media/java/android/media/AudioTrack.java) — `isDirectPlaybackSupported` (deprecated), history of the direct-playback probe.
- [Android `AudioFormat` reference](https://developer.android.com/reference/android/media/AudioFormat) — public javadoc for encoding constants.
- [Android `AudioTrack` reference](https://developer.android.com/reference/android/media/AudioTrack) — `isDirectPlaybackSupported` and deprecation note.
- [Android `AudioManager` reference](https://developer.android.com/reference/android/media/AudioManager) — `ACTION_HDMI_AUDIO_PLUG`, `getDirectProfilesForAttributes`, `getDirectPlaybackSupport`.
- [Android `Spatializer` reference](https://developer.android.com/reference/android/media/Spatializer).
- [Android `MediaCodecList` reference](https://developer.android.com/reference/android/media/MediaCodecList) — `findDecoderForFormat`.
- [Android SDK API diff 28](https://developer.android.com/sdk/api_diff/28/) — `ENCODING_AC4`, `ENCODING_E_AC3_JOC`, various AAC variants.
- [Android SDK API diff 29](https://developer.android.com/sdk/api_diff/29/) — `ENCODING_DOLBY_MAT`.
- [Android SDK API diff 30](https://developer.android.com/sdk/api_diff/30/) — `ENCODING_OPUS`.
- [Android SDK API diff 34](https://developer.android.com/sdk/api_diff/34/) — `ENCODING_DTS_UHD`, `ENCODING_DTS_UHD_P1`, `ENCODING_DTS_UHD_P2`, `ENCODING_DTS_HD_MA`.
- [Media3 release notes / CHANGELOG](https://github.com/androidx/media/blob/1.10.0/RELEASENOTES.md) — spatialization and offload entries.

## Validation log

- verified: `AudioCapabilities` lives at `androidx.media3.exoplayer.audio.AudioCapabilities` in 1.10.0, not under `androidx.media3.common.audio`. (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioCapabilities.java)
- verified: `getCapabilities(Context context, AudioAttributes audioAttributes, @Nullable AudioDeviceInfo routedDevice, List<Integer> spatializerChannelMasks)` is the non-deprecated factory; the single-arg `getCapabilities(Context)` overload is `@Deprecated` in 1.10.0. (same source)
- verified: `isPassthroughPlaybackSupported` and `getEncodingAndChannelConfigForPassthrough` each have a two-arg non-deprecated form and a single-arg `@Deprecated` form. Signatures in §4.2 cite the non-deprecated form.
- corrected: implicit claim that `AudioCapabilities.getMaxSupportedChannelCountForPassthrough(encoding, sampleRate)` is a public instance method — it is **not**. The method with that name is a `static` on the private `Api29` inner class (`public static int getMaxSupportedChannelCountForPassthrough(@C.Encoding int encoding, int sampleRate, AudioAttributes audioAttributes)`), not reachable from application code. Added the note in §4.2 and flagged the implication for `AudioCapabilityManager.kt` (doc 07 §9 needs the same correction).
- corrected: `SPATIALIZER_IMMERSIVE_LEVEL_MCHAN_BED_PLUS_OBJECTS` constant name marked as `@hide` in AOSP — removed the public-SDK mention from the Kotlin sample in §8.1 and added an explanatory note. (https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/Spatializer.java)
- verified: `AudioFormat.ENCODING_*` int values — PCM_16BIT=2, PCM_FLOAT=4, AC3=5, E_AC3=6, DTS=7, DTS_HD=8, DOLBY_TRUEHD=14, AC4=17, E_AC3_JOC=18, DOLBY_MAT=19, OPUS=20, DTS_UHD_P1=27, DTS_HD_MA=29, DTS_UHD_P2=30. (https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/AudioFormat.java)
- verified: `ENCODING_DTS_UHD` is an `@Deprecated` alias for `ENCODING_DTS_UHD_P1` (same int value 27); stated in AOSP `AudioFormat.java`. Existing §5 table note retained.
- verified: MIME strings for audio codecs — AUDIO_AC3="audio/ac3", AUDIO_E_AC3="audio/eac3", AUDIO_E_AC3_JOC="audio/eac3-joc", AUDIO_TRUEHD="audio/true-hd", AUDIO_AC4="audio/ac4", AUDIO_DTS="audio/vnd.dts", AUDIO_DTS_HD="audio/vnd.dts.hd", AUDIO_DTS_EXPRESS="audio/vnd.dts.hd;profile=lbr", AUDIO_DTS_X="audio/vnd.dts.uhd;profile=p2". All annotations in 1.10.0 `MimeTypes.java`; `AUDIO_DTS_X` is `@UnstableApi`.
- verified: `Format.pcmEncoding` was promoted out of `@UnstableApi` in 1.10.0 per RELEASENOTES.md.
- still unverified: per-SoC AC-4 passthrough coverage matrix — truly device-dependent and only answerable on hardware via `AudioCapabilities.supportsEncoding(C.ENCODING_AC4)` on the specific device.
- still unverified: JOC → plain E-AC-3 graceful-fallback behaviour on a real AVR — the claim comes from the `ENCODING_E_AC3_JOC` Javadoc and is widely-reported, but only confirmable with an AVR in hand; kept as stated in §3 because the Javadoc is sufficient design-time justification.
- still unverified: whether `AudioManager.spatializer.canBeSpatialized(movieAttrs, 7.1-PCM)` returns `true` on Pixel 8 + USB-C headphones — documented behaviour but worth verifying on each target device.
