Document version: Media3 1.10.0

# HDR and Dolby Vision playback with AndroidX Media3

This document covers how the Silo Android phone and Android TV clients handle HDR10, HDR10+, HLG, and Dolby Vision video delivered from the Silo server (typically MKV, remuxed to fMP4/HLS for direct play where possible). It focuses on the guarantees the Media3 1.10.0 runtime provides, the Android platform surfaces underneath it, and the concrete Kotlin code patterns we should use.

## 1. HDR transfer functions and containers

### HDR10
Perceptual Quantizer (PQ, SMPTE ST 2084) transfer on BT.2020 primaries, 10 bits per channel, with **static** mastering metadata in two blocks:

- Mastering display color volume — SMPTE ST 2086 (display primaries, white point, min/max luminance).
- Content light level — CTA-861.3 MaxCLL / MaxFALL.

In Media3 these show up on `androidx.media3.common.ColorInfo` as `colorSpace = COLOR_SPACE_BT2020`, `colorTransfer = COLOR_TRANSFER_ST2084`, with the CTA-861.3 block in `hdrStaticInfo: byte[]` (see `ColorInfo.java` in the Media3 1.10.0 source — [raw](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/ColorInfo.java)). The platform-level equivalent is `MediaFormat.KEY_HDR_STATIC_INFO` (API 24+) ([MediaFormat reference](https://developer.android.com/reference/android/media/MediaFormat)).

Carried inside HEVC (Main10 + HDR10 SEI) or AV1 (Main 10). Container-wise, HDR10 rides cleanly in MKV, Matroska/WebM, fMP4 and HLS.

### HDR10+
HDR10 base plus **per-scene / per-frame dynamic metadata** (SMPTE ST 2094-40, carried as ITU-T T.35 SEI with country code 0xB5 / provider 0x003C / terminal provider oriented code 0x0001 / application identifier 4 — verified in `MediaCodecVideoRenderer#handleInputBufferSupplementalData`, [Media3 source](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/MediaCodecVideoRenderer.java)).

Platform surface: `MediaFormat.KEY_HDR10_PLUS_INFO` (added in API 29 — [MediaFormat](https://developer.android.com/reference/android/media/MediaFormat)). Display surface: `Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS` (added in API 30 — [Display.HdrCapabilities](https://developer.android.com/reference/android/view/Display.HdrCapabilities)).

Media3 does not expose the dynamic metadata on `ColorInfo` directly; the underlying `MediaCodecVideoRenderer` pulls T.35 SEIs out of the bitstream and pushes them to the decoder as out-of-band metadata when `codecHandlesHdr10PlusOutOfBandMetadata` is true (verified in Media3 1.10.0 source).

### HLG
Hybrid Log-Gamma (ITU-R BT.2100 HLG transfer) on BT.2020 primaries, 10-bit. Designed for broadcast — no static or dynamic metadata needed; the transfer curve is self-describing. HLG is HDR **and** approximately SDR-compatible on older displays (the gamma portion is close enough to a conventional display curve to look usable on SDR, although not color-accurate).

On Media3: `COLOR_TRANSFER_HLG`, `COLOR_SPACE_BT2020`, `hdrStaticInfo == null`. Platform: `HDR_TYPE_HLG` (API 24+). Android 13+ mandates HLG10 as the **minimum** HDR type that HDR-capable devices must support ([HDR video playback guide](https://developer.android.com/guide/topics/media/hdr-playback)).

### Dolby Vision (DV)

Dolby Vision layers a 12-bit IPT-PQ-c4 image on top of an 8–10 bit base layer. Profile numbers encode base codec, layer count, and cross-compatibility:

| Profile | Base codec | Layers | Cross-compat | Android MediaCodec playability |
|---------|-----------|--------|--------------|-------------------------------|
| 4 (DVHE.04) | HEVC | Dual (BL+EL, 10-bit EL) | HDR10 | Not supported on consumer devices (unverified outside broadcast use) |
| 5 (DVHE.05) | HEVC | Single | None (DV-only) | Supported where hardware decoder advertises DV Profile 5 |
| 7 (DVHE.07) | HEVC | Dual (BL+EL, MEL or FEL) | Blu-ray HDR10 | **Not playable on Android MediaCodec** — cross-layer decode is not supported, server-side remux to 8.1 required ([Media3 source comments](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/mediacodec/MediaCodecUtil.java) note Profile 7 exclusions in `getAlternativeCodecMimeType`) |
| 8.1 (DVHE.08) | HEVC | Single with RPU | HDR10 | Supported; on non-DV devices, Media3 falls back to HEVC Main10 HDR10 decode of the base layer (see §4) |
| 8.2 (DVHE.08) | HEVC | Single with RPU | SDR | Supported where DV is supported; SDR-compat base plays without DV on non-DV devices |
| 8.4 (DVHE.08) | HEVC | Single with RPU | HLG | Supported; base layer is an HLG stream on non-DV devices |
| 9 (DVAV.09) | AVC/H.264 | Single with RPU | SDR | Supported where hardware supports it; base is H.264 SDR |
| 10 (DVAV1.10) | AV1 | Single with RPU | HDR10 | Added to Media3 in **1.10.0** (release notes: "Add support for Dolby Vision Profile 10" — [Media3 RELEASENOTES.md](https://github.com/androidx/media/blob/1.10.0/RELEASENOTES.md)) |

Supplemental-codec detection (HLS/DASH manifests, fMP4 `dvcC`/`dvvC` boxes) was wired in Media3 1.6.0; it lets the extractor tag profile 8/9/10 streams with `supplementalCodecs`. See `MimeTypes.isDolbyVisionCodec` — it accepts `dvhe*`/`dvh1*`/`dav1*` for profiles 5, 10.0, 20.0 and uses `supplementalCodecs` to recognize profiles 8/9/10 ([MimeTypes.java 1.10.0](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/MimeTypes.java)).

Media3 1.9.0 fixed two DV-specific bugs: "some playbacks of Dolby Vision files fail when attempting to use a fallback AVC or HEVC codec" and "disable codec reuse for Dolby-Vision content with different profiles" ([RELEASENOTES.md](https://github.com/androidx/media/blob/1.10.0/RELEASENOTES.md)). 1.10.0 inherits both fixes, so Silo clients should be on 1.10.0 to get correct DV fallback behaviour.

Profiles 4, 7 are effectively Blu-ray-era formats. If the server holds a Profile 7 source, the **only** path that produces correct color on Android is to remux/transcode to Profile 8.1 (HDR10-compatible single-layer) server-side before streaming.

## 2. Android platform support by API level

| Feature | First available |
|---------|----------------|
| `Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION` (= 1) | API 24 ([ref](https://developer.android.com/reference/android/view/Display.HdrCapabilities)) |
| `HDR_TYPE_HDR10` (= 2) | API 24 |
| `HDR_TYPE_HLG` (= 3) | API 24 |
| `HDR_TYPE_HDR10_PLUS` (= 4) | API 30 |
| `MediaFormat.KEY_HDR_STATIC_INFO` | API 24 |
| `MediaFormat.KEY_COLOR_STANDARD` / `KEY_COLOR_RANGE` / `KEY_COLOR_TRANSFER` | API 24 (raw keys); strongly-typed consumer APIs from API 29 ([MediaFormat](https://developer.android.com/reference/android/media/MediaFormat)) |
| `MediaFormat.KEY_HDR10_PLUS_INFO` | API 29 |
| `MediaFormat.KEY_COLOR_TRANSFER_REQUEST` (OS-level tone mapping on the decoder) | API 33, Android 13. Constant verified present in AOSP `MediaFormat.java`: `public static final String KEY_COLOR_TRANSFER_REQUEST = "color-transfer-request";` (https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/MediaFormat.java) |
| `MediaCodecInfo.CodecCapabilities.FEATURE_HdrEditing` | API 33. Constant verified in AOSP: `public static final String FEATURE_HdrEditing = "hdr-editing";` — documented as an **encoder** feature only, so do not use it to gate decode capability. (https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/MediaCodecInfo.java) |
| `MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback` | API 21. Constant verified: `public static final String FEATURE_TunneledPlayback = "tunneled-playback";` (same AOSP source) |
| Android-mandated HLG10 minimum for HDR-capable devices | API 33 ([HDR playback guide](https://developer.android.com/guide/topics/media/hdr-playback) — "Starting in Android 13, HLG10 is the minimum standard that device makers must support if the device is capable of HDR playback") |

Notes:
- `Display.isHdr()` is a coarse boolean; use `getHdrCapabilities().getSupportedHdrTypes()` for the actual set.
- `KEY_COLOR_TRANSFER_REQUEST` is how Android 13+ exposes **decoder-side tone mapping** — you ask the decoder to output SDR even when the input is PQ/HLG, and the decoder does the tone map before handing frames to the Surface. Below API 33 there is no portable request key; behaviour on SDR displays is device-dependent.
- `FEATURE_DynamicTimestamp` (singular) and `FEATURE_DynamicColorAspects` are platform features. `FEATURE_DynamicColorAspects` is a **decoder** feature that lets a video decoder handle mid-stream color-aspect changes; `FEATURE_DynamicTimestamp` applies to both encoders and decoders. Neither is relevant to gating HDR direct playback on Silo — the tone-map and HDR-passthrough decisions are made from `ColorInfo` / `HdrCapabilities`, not from codec features. Names verified in the AOSP `MediaCodecInfo.java` (https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/MediaCodecInfo.java).

## 3. Media3 APIs that matter for HDR playback

### `androidx.media3.common.ColorInfo`
Key fields (verified against Media3 1.10.0 [ColorInfo.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/ColorInfo.java)):

```kotlin
// Fields (final vals in Kotlin terms)
val colorSpace: Int        // @C.ColorSpace, e.g. COLOR_SPACE_BT2020
val colorRange: Int        // @C.ColorRange, COLOR_RANGE_LIMITED | COLOR_RANGE_FULL
val colorTransfer: Int     // @C.ColorTransfer, e.g. COLOR_TRANSFER_ST2084, COLOR_TRANSFER_HLG
val hdrStaticInfo: ByteArray?  // CTA-861.3 block for HDR10
val lumaBitdepth: Int      // e.g. 10 for HDR10
val chromaBitdepth: Int    // e.g. 10 for HDR10
```

Constants on `androidx.media3.common.C` (verified from 1.10.0 `C.java`):

```kotlin
C.COLOR_SPACE_BT601
C.COLOR_SPACE_BT709
C.COLOR_SPACE_BT2020

C.COLOR_RANGE_LIMITED
C.COLOR_RANGE_FULL

C.COLOR_TRANSFER_LINEAR
C.COLOR_TRANSFER_SDR        // = MediaFormat.COLOR_TRANSFER_SDR_VIDEO (value 3) — SMPTE 170M curve used by BT.601/BT.709/BT.2020; the conventional SDR video transfer
C.COLOR_TRANSFER_SRGB       // value 2
C.COLOR_TRANSFER_GAMMA_2_2  // value 10
C.COLOR_TRANSFER_ST2084     // = MediaFormat.COLOR_TRANSFER_ST2084 — PQ (HDR10 / HDR10+ / DV profile 5/8.1/10)
C.COLOR_TRANSFER_HLG        // = MediaFormat.COLOR_TRANSFER_HLG — HLG (broadcast HDR, DV profile 8.4)
```

Predefined `ColorInfo` instances in 1.10.0: `ColorInfo.SDR_BT709_LIMITED` (BT.709 + LIMITED + SDR) and `ColorInfo.SRGB_BT709_FULL` (BT.709 + FULL + SRGB). These are the only two predefined instances — there is no `HDR_PQ_BT2020_10BIT` constant in 1.10.0, so build one via `ColorInfo.Builder` when you need it. Verified by reading the 1.10.0 `ColorInfo.java` source (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/ColorInfo.java).

Helper: `ColorInfo.isTransferHdr(colorInfo: ColorInfo?): Boolean` returns true for `COLOR_TRANSFER_ST2084` and `COLOR_TRANSFER_HLG`. This is the simplest "is this clip HDR?" check.

### `androidx.media3.common.Format`

`Format.colorInfo: ColorInfo?` is the authoritative description of a video track's color metadata and is the input used by `MediaCodecVideoRenderer` and `DefaultTrackSelector`. It is `@UnstableApi` in Media3 1.10.0 — you can read it but annotate the call site with `@OptIn(UnstableApi::class)`.

Other relevant fields: `Format.sampleMimeType` (`MimeTypes.VIDEO_DOLBY_VISION` == `"video/dolby-vision"` for DV content), `Format.codecs`. `MimeTypes.isDolbyVisionCodec(codecs, supplementalCodecs)` (1.10.0) accepts the companion `supplementalCodecs` string separately — the DV supplemental-codec detection for HLS/DASH manifests wired in Media3 1.6.0 flows through that second argument rather than a `Format.supplementalCodecs` field (a public `supplementalCodecs` field is not present on `Format` in 1.10.0; verified by searching the 1.10.0 `Format.java` source). The HLS / DASH extractors keep the value in their own state and pass it alongside `codecs` when invoking `isDolbyVisionCodec`.

### `DefaultRenderersFactory`
Extension renderer mode does **not** affect HDR or DV decode — HDR and DV are handled by the core `MediaCodecVideoRenderer` using platform MediaCodec decoders. `EXTENSION_RENDERER_MODE_{OFF,ON,PREFER}` in `DefaultRenderersFactory.java` (value 0, 1, 2; verified in 1.10.0 source) toggles software AV1/VP9/FFmpeg video extensions, not HDR behaviour. Leave it at the default (`OFF`) unless you specifically need the software extensions.

`DefaultRenderersFactory.experimentalSetEnableMediaCodecVideoRendererDurationToProgressUs(...)` was added in 1.10.0 and controls frame-scheduling cadence; it is a scheduling knob, not an HDR toggle (verified in 1.10.0 release notes).

### `MediaCodecVideoRenderer`
Responsible for choosing a decoder from the format and for handling DV fallback and HDR10+ SEI injection. Two behaviours relevant to us, both verified against 1.10.0 source:

1. When `sampleMimeType == VIDEO_DOLBY_VISION` and `SDK_INT >= 26` and `Api26.doesDisplaySupportDolbyVision(context) == false`, the renderer queries `MediaCodecUtil.getAlternativeDecoderInfos(...)` and plays the base layer via HEVC/AV1/H.264 as appropriate. `MediaCodecUtil.getAlternativeCodecMimeType(format)` returns the fallback MIME:
   - DVHE profiles (DTR, ST) -> `VIDEO_H265` (HEVC Main10 + HDR10 for 8.1 or Main10 + HLG for 8.4)
   - DVAV profile (SE, Profile 9) -> `VIDEO_H264`
   - DVAV1.10 (Profile 10) -> `VIDEO_AV1`
   - Profile 5 (`DvheStn`) and Profile 4 (`DvheDtb`) are deliberately excluded — there is no HDR10/HLG base to fall back to for a true DV-only stream.
2. When HDR10+ dynamic metadata is present as T.35 SEI, the renderer extracts it per input buffer and forwards it to the codec via supplemental data when `codecHandlesHdr10PlusOutOfBandMetadata` is true.

### `DefaultTrackSelector` / `TrackSelectionParameters`
Media3 1.10.0 `TrackSelectionParameters.Builder` does **not** expose `setAllowedHdrDynamicMetadataTypes(...)`. We verified this by searching the full source of `TrackSelectionParameters.java` in the `1.10.0` tag — there is no method with `Hdr`, `hdr`, `tonemap`, `toneMap`, `DynamicMetadata`, or `DolbyVision` in its name.

HDR-aware track selection in practice relies on:
- Renderer capabilities: `RendererCapabilities.isFormatSupported(...)` (ints from `C.FORMAT_*`) — the `MediaCodecVideoRenderer` returns `FORMAT_UNSUPPORTED_TYPE` for a DV stream on a non-DV device once fallback is exhausted, so the default selector will naturally skip HDR/DV tracks the device cannot play.
- Explicit app-side filtering in track-selection overrides, based on reading `Format.colorInfo` against `Display.HdrCapabilities.getSupportedHdrTypes()` (see §7 code example).

## 4. Dolby Vision specifics

- **MIME:** `androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION` == `"video/dolby-vision"` ([MimeTypes.java](https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/MimeTypes.java)).
- **CSD:** The decoder requires the DV configuration box (`dvcC` for HEVC-based profiles, `dvvC` for AV1 profile 10) as codec-specific data. Media3's fMP4 / Matroska extractors parse this and hand it to the renderer on `Format.initializationData`. A raw HEVC elementary stream without the `dvcC` config cannot be decoded as DV — this is a remux concern on the server side.
- **Display capability:** walk `MediaCodecList.REGULAR_CODECS` and look for a decoder whose `CodecProfileLevel.profile` matches one of the Dolby Vision profile constants on `MediaCodecInfo.CodecProfileLevel`. Common profile constant names (from Android source, as used in Media3's `MediaCodecUtil` DV fallback paths): `DolbyVisionProfileDvavPer`, `DolbyVisionProfileDvavPen`, `DolbyVisionProfileDvheDer`, `DolbyVisionProfileDvheDen`, `DolbyVisionProfileDvheDtr`, `DolbyVisionProfileDvheStn`, `DolbyVisionProfileDvheDth`, `DolbyVisionProfileDvheDtb`, `DolbyVisionProfileDvheSt`, `DolbyVisionProfileDvavSe`, `DolbyVisionProfileDvav110`. (unverified against the live `developer.android.com` reference — the page returned only the navigation shell during this research, so constant-to-API-level mapping should be double-checked against the Android SDK's `android.jar` at the project's `compileSdk` before gating client behaviour on any individual constant.)
- **Profile 7 MEL/FEL:** Dual-layer decoding with an enhancement layer is **not** supported by Android MediaCodec. There is no code path in `MediaCodecVideoRenderer` that demuxes the EL and feeds a second decoder. The `getHevcBaseLayerCodecProfileAndLevel(format)` helper in `MediaCodecUtil` exists for fMP4 L-HEVC fallbacks but it still plays a single-layer HEVC stream — the DV image is lost. For Silo, any Profile 7 source must be **server-side remuxed to Profile 8.1** (if you want DV-cap devices to keep DV) or transcoded to plain HDR10/HEVC Main10.
- **8.1 / 8.4 fallback:** On a non-DV display/decoder, Profile 8.1 falls back to HEVC Main10 + HDR10; Profile 8.4 falls back to HEVC Main10 + HLG. Media3 handles this automatically via `getAlternativeDecoderInfos` + `getAlternativeCodecMimeType`. The client does not need special code — just make sure the HEVC HDR10 decoder is present on the device (universal on Android 10+ phones and on every listed TV target).

## 5. Display capability detection (system level)

Canonical call:

```kotlin
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Display.HdrCapabilities

fun getSupportedHdrTypes(context: Context): IntArray {
    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return IntArray(0)
    // isHdr() is a fast coarse check; if false, there is no HDR path at all.
    if (!display.isHdr) return IntArray(0)
    return display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
}
```

Returned ints map to:

- `HdrCapabilities.HDR_TYPE_DOLBY_VISION` (value 1, API 24) — the display is announcing full DV support.
- `HdrCapabilities.HDR_TYPE_HDR10` (value 2, API 24).
- `HdrCapabilities.HDR_TYPE_HLG` (value 3, API 24).
- `HdrCapabilities.HDR_TYPE_HDR10_PLUS` (value 4, API 30).

Integer values per [Display.HdrCapabilities reference](https://developer.android.com/reference/android/view/Display.HdrCapabilities). On Android TV boxes the capability set **is the HDMI sink's capability set**, not the box's — plug a TV-box into a 1080p SDR display and `supportedHdrTypes` will be empty even though the box silicon could decode PQ. This is what we want: let the selector route to SDR when the sink is SDR.

Refresh-rate matching for HDR content (24 Hz film -> 24 Hz panel) is covered by `Display.Mode` + `Window.setPreferredDisplayModeId(...)`. Details live in the TV-targets document, not here.

External HDMI attach/detach on Android TV: listen for `DisplayManager.DisplayListener`. When `onDisplayChanged` fires for `Display.DEFAULT_DISPLAY`, re-query `hdrCapabilities` and, if the HDR profile changed mid-stream, either request an Android 13+ tone-map on the decoder or force a track reselection.

## 6. Tone mapping fallback (HDR source -> SDR display)

- **Android 13+ (API 33+):** The decoder can tone-map HDR -> SDR itself. You pass `MediaFormat.KEY_COLOR_TRANSFER_REQUEST = COLOR_TRANSFER_SDR_VIDEO` in the `MediaFormat` used at `configure(...)` time. Media3 1.10.0 does **not** expose this on `TrackSelectionParameters` — if we need it, the current path is a custom `MediaCodecSelector` + custom `MediaCodecVideoRenderer` that adds the key to the `MediaFormat` just before `configure`. (unverified: Media3 may add a first-class API in a later release; there is no `setTonemapHdrToSdr(...)` on `TrackSelectionParameters.Builder` in 1.10.0.)
- **Android 10–12 (API 29–32):** No portable decoder-level tone map. Device behaviour varies: some silicon (e.g. MediaTek/Amlogic TV boxes) tone-maps internally in the video pipeline even without a request key; many phones do not, and PQ pixels sent to an SDR panel produce washed-out/banded/over-bright output. The safe play is to avoid selecting HDR tracks on SDR displays — i.e. check `getSupportedHdrTypes()` and filter (`ColorInfo.isTransferHdr`) before passing to ExoPlayer.
- **SurfaceView vs TextureView:** `TextureView` forces HDR-to-SDR in the view layer on Android 13+ with banding ([HDR playback guide](https://developer.android.com/guide/topics/media/hdr-playback)). Use `SurfaceView` for all HDR content. Silo's player uses `androidx.media3.ui.PlayerView` which defaults to `SurfaceView` (confirm `surface_type="surface_view"` in layout) — fine for HDR.

## 7. Practical Kotlin code

### 7.1 Capability snapshot at app startup

```kotlin
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION
import android.view.Display.HdrCapabilities.HDR_TYPE_HDR10
import android.view.Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS
import android.view.Display.HdrCapabilities.HDR_TYPE_HLG

data class DisplayHdrSupport(
    val hdr10: Boolean,
    val hdr10Plus: Boolean,
    val hlg: Boolean,
    val dolbyVision: Boolean,
) {
    val anyHdr: Boolean get() = hdr10 || hdr10Plus || hlg || dolbyVision
}

fun probeDisplayHdr(context: Context): DisplayHdrSupport {
    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        ?: return DisplayHdrSupport(false, false, false, false)
    if (!display.isHdr) return DisplayHdrSupport(false, false, false, false)
    val types = display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
    return DisplayHdrSupport(
        hdr10 = HDR_TYPE_HDR10 in types,
        hdr10Plus = HDR_TYPE_HDR10_PLUS in types,
        hlg = HDR_TYPE_HLG in types,
        dolbyVision = HDR_TYPE_DOLBY_VISION in types,
    )
}
```

Call this from the player screen's `onStart`/`onResume` (HDMI sinks can change while the activity is alive). Wire a `DisplayManager.DisplayListener` on `DEFAULT_DISPLAY` if you want to react to HDMI hot-plug without requiring a full resume cycle.

Also detect **decoder** DV capability independently of display — this matters for "record" paths or for diagnostics:

```kotlin
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import androidx.media3.common.MimeTypes

fun deviceHasDolbyVisionDecoder(): Boolean {
    val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    return list.codecInfos.any { info ->
        !info.isEncoder && info.supportedTypes.any { it.equals(MimeTypes.VIDEO_DOLBY_VISION, ignoreCase = true) }
    }
}
```

### 7.2 Configuring ExoPlayer for HDR / DV

Minimal configuration — Media3 1.10.0 handles HDR and DV fallback without any special flags:

```kotlin
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer

@UnstableApi
fun buildPlayer(context: Context): ExoPlayer {
    val renderers = DefaultRenderersFactory(context)
        // Leave EXTENSION_RENDERER_MODE_OFF — HDR/DV go through platform MediaCodec.
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        .setEnableDecoderFallback(true)  // important: allows HEVC fallback of DV 8.1/8.4.
    return ExoPlayer.Builder(context, renderers).build()
}
```

`setEnableDecoderFallback(true)` is the knob that lets `MediaCodecVideoRenderer` try HEVC/AV1/H.264 when the primary DV decoder refuses — which is what makes the Profile 8.1 -> HDR10 and 8.4 -> HLG fallbacks actually fire on non-DV devices. Without it, you get a `MediaCodec.IllegalStateException` on unsupported DV content.

### 7.3 Reading `Format.colorInfo` after `onTracksChanged`

```kotlin
import androidx.media3.common.ColorInfo
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi

@UnstableApi
private val hdrListener = object : Player.Listener {
    override fun onTracksChanged(tracks: Tracks) {
        val videoGroup = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            ?: return
        val selected = (0 until videoGroup.length)
            .firstOrNull { videoGroup.isTrackSelected(it) }
            ?.let { videoGroup.getTrackFormat(it) }
            ?: return

        val color: ColorInfo? = selected.colorInfo
        val isHdr = ColorInfo.isTransferHdr(color)
        val transfer = when (color?.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> "PQ (HDR10/HDR10+/DV-PQ)"
            C.COLOR_TRANSFER_HLG -> "HLG (BT.2100 HLG / DV 8.4)"
            C.COLOR_TRANSFER_SDR -> "SDR"
            else -> "unknown"
        }
        val bitDepth = color?.lumaBitdepth?.takeIf { it != androidx.media3.common.Format.NO_VALUE }
        android.util.Log.i("PlayerHdr", "isHdr=$isHdr transfer=$transfer bit=$bitDepth mime=${selected.sampleMimeType}")
    }
}
player.addListener(hdrListener)
```

This gives you the authoritative description of what is actually on screen, including after a mid-stream ABR switch or DV->HEVC fallback.

### 7.4 Filtering tracks against the current display

Media3 1.10.0 does not expose an HDR-type allow-list on `TrackSelectionParameters`. If you need to veto HDR tracks on an SDR sink (because a particular device mis-handles HDR->SDR pre-Android 13), use a track-selection override built from the current `Tracks` snapshot.

```kotlin
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi

@UnstableApi
fun enforceDisplayCompatibleVideoTrack(
    player: androidx.media3.exoplayer.ExoPlayer,
    displayHdr: DisplayHdrSupport,
) {
    val tracks = player.currentTracks
    val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
    if (videoGroups.isEmpty()) return

    val overrides = videoGroups.mapNotNull { group ->
        // Pick the best supported index that the display can show.
        val allowable = (0 until group.length).filter { idx ->
            if (!group.isTrackSupported(idx)) return@filter false
            val fmt = group.getTrackFormat(idx)
            val color = fmt.colorInfo
            val isHdr = ColorInfo.isTransferHdr(color)
            // SDR on SDR display => ok; HDR only if display reports any HDR type.
            !isHdr || displayHdr.anyHdr
        }
        if (allowable.isEmpty()) null else {
            TrackSelectionOverride(group.mediaTrackGroup, allowable)
        }
    }

    val params: TrackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
        // Clear previous video overrides, then apply.
        clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        overrides.forEach { setOverrideForType(it) }
    }.build()

    player.trackSelectionParameters = params
}
```

Call this from `onTracksChanged` after each preparation. This is a belt-and-braces filter — on Android 13+ you might prefer the decoder tone-map path instead (§6) and let HDR tracks through.

## 8. Recommended defaults for Silo

- Media3 version: **1.10.0** — required for the DV fallback fixes from 1.9.0 and Profile 10 support added in 1.10.0.
- `setEnableDecoderFallback(true)` on the renderers factory.
- `PlayerView` surface type: `surface_view` (default).
- Read `Format.colorInfo` after `onTracksChanged` and record HDR transfer + bit depth in analytics; this is a cheap signal for support-triage.
- Gate HDR track selection on `Display.getHdrCapabilities().getSupportedHdrTypes()` until we explicitly opt into Android 13 decoder tone mapping.
- Server side: transcode/remux any DV Profile 7 (MEL or FEL) source to Profile 8.1 before offering it to the Android clients.

## Sources

- Media3 ExoPlayer HDR playback guide (Android developer site): https://developer.android.com/media/media3/exoplayer/hdr-video — **currently 404**. The live equivalent is the generic Android HDR playback guide at https://developer.android.com/guide/topics/media/hdr-playback
- `Display.HdrCapabilities` reference: https://developer.android.com/reference/android/view/Display.HdrCapabilities
- `MediaFormat` reference: https://developer.android.com/reference/android/media/MediaFormat
- `MediaCodecInfo.CodecProfileLevel` reference: https://developer.android.com/reference/android/media/MediaCodecInfo.CodecProfileLevel (page returned a navigation shell in our fetch — Dolby Vision constant names taken from the Android source but unverified against the live reference page)
- `MediaCodecInfo.CodecCapabilities` reference: https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities (same fetch limitation)
- Media3 1.10.0 `ColorInfo.java`: https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/ColorInfo.java
- Media3 1.10.0 `C.java` (COLOR_* constants): https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/C.java
- Media3 1.10.0 `MimeTypes.java` (`VIDEO_DOLBY_VISION`, `isDolbyVisionCodec`): https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/MimeTypes.java
- Media3 1.10.0 `Format.java` (`colorInfo`, `supplementalCodecs`): https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/Format.java
- Media3 1.10.0 `TrackSelectionParameters.java`: https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/TrackSelectionParameters.java
- Media3 1.10.0 `DefaultRenderersFactory.java`: https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultRenderersFactory.java
- Media3 1.10.0 `MediaCodecVideoRenderer.java`: https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/MediaCodecVideoRenderer.java
- Media3 1.10.0 `MediaCodecUtil.java`: https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/mediacodec/MediaCodecUtil.java
- Media3 1.10.0 `DefaultTrackSelector.java`: https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java
- Media3 RELEASENOTES: https://github.com/androidx/media/blob/1.10.0/RELEASENOTES.md
- Media3 1.10.0 release page: https://github.com/androidx/media/releases/tag/1.10.0
- Android AOSP HDR page: https://source.android.com/docs/core/display/hdr
- AOSP `MediaCodecInfo.java` (FEATURE_* constants): https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/MediaCodecInfo.java
- AOSP `MediaFormat.java` (`KEY_COLOR_TRANSFER_REQUEST`, `KEY_HDR_STATIC_INFO`, `KEY_HDR10_PLUS_INFO`, `COLOR_TRANSFER_SDR_VIDEO`): https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/MediaFormat.java
- AOSP `Display.java` (HdrCapabilities HDR_TYPE_* constants): https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/Display.java

## Validation log

- corrected: "Format.supplementalCodecs (added in 1.6.0)" — verified there is no public `supplementalCodecs` field on `Format` in 1.10.0 (searched the 1.10.0 `Format.java` source; no `supplemental` occurrences). The DV supplemental-codec detection instead flows through the `supplementalCodecs` parameter of `MimeTypes.isDolbyVisionCodec(codecs, supplementalCodecs)` — verified in 1.10.0 `MimeTypes.java`. Text updated in §3.
- verified: `Format.colorInfo` annotated `@UnstableApi` in 1.10.0, `Format.pcmEncoding` is stable (no `@UnstableApi`). (1.10.0 `Format.java`)
- verified: `ColorInfo` predefined instances are only `SDR_BT709_LIMITED` and `SRGB_BT709_FULL` in 1.10.0; no `HDR_PQ_BT2020_10BIT`. `ColorInfo.isTransferHdr(ColorInfo?)` is a public static method. Text updated in §3.
- verified: `C.COLOR_TRANSFER_SDR = MediaFormat.COLOR_TRANSFER_SDR_VIDEO` which is the SMPTE 170M transfer curve (value 3 in `MediaFormat.java`). Existing description in §3 is correct.
- verified: `codecHandlesHdr10PlusOutOfBandMetadata` boolean and `handleInputBufferSupplementalData` exist on `MediaCodecVideoRenderer` 1.10.0. (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/MediaCodecVideoRenderer.java)
- verified: `HDR_TYPE_DOLBY_VISION = 1`, `HDR_TYPE_HDR10 = 2`, `HDR_TYPE_HLG = 3`, `HDR_TYPE_HDR10_PLUS = 4` per AOSP `Display.java` (section 5). Updated table in §2 to annotate the int values.
- verified: `KEY_COLOR_TRANSFER_REQUEST`, `KEY_HDR_STATIC_INFO`, `KEY_HDR10_PLUS_INFO` all present in AOSP `MediaFormat.java`. Removed the "(unverified)" note on §2.
- verified: `FEATURE_TunneledPlayback = "tunneled-playback"` and `FEATURE_HdrEditing = "hdr-editing"` per AOSP `MediaCodecInfo.java`. `FEATURE_DynamicTimestamp` is singular, not plural; noted in §2.
- verified: Dolby Vision Profile 10 added in Media3 1.10.0 per RELEASENOTES.md "Add support for Dolby Vision Profile 10."
- still unverified: exact constant names for `DolbyVisionProfile*` on `MediaCodecInfo.CodecProfileLevel` at specific API levels — live developer.android.com reference returned only navigation shell. These names are derivable from AOSP source and used by Media3 `MediaCodecUtil` internally; list preserved for reference but gated with an inline caution.
- still unverified: whether Android 13's `KEY_COLOR_TRANSFER_REQUEST` + `COLOR_TRANSFER_SDR_VIDEO` path on a real Pixel / Shield device successfully tone-maps an HDR10 MKV to SDR with Media3 1.10.0's default `MediaCodecVideoRenderer` — only answerable on hardware (doc 08 §6 / §7 flags this as a preflight/fallback concern).
