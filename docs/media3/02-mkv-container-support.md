Document version: Media3 1.10.0

# MKV / Matroska Container Support in AndroidX Media3

This note captures how Silo's Android phone and Android TV clients (both on
Media3 1.10.0) should reason about streaming MKV files from the Silo
server. It distinguishes what the extractor can *parse* (produce samples and a
`Format` for) from what the underlying `MediaCodec` stack can actually
*decode*/*render* on a given device.

---

## 1. Where MKV/Matroska/WebM lives in the module graph

The Matroska/WebM parser is a single class:

- Class: `androidx.media3.extractor.mkv.MatroskaExtractor`
- Module: `androidx.media3:media3-extractor`
- Source: `libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java`

The same extractor handles both the Matroska (.mkv, .mka) and WebM (.webm, .weba)
streams — WebM is a strict subset of Matroska
(https://github.com/androidx/media/blob/1.10.0/libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java).

For progressive (non-adaptive) playback, `ProgressiveMediaSource` pulls the
extractor from `DefaultExtractorsFactory`, which is in turn supplied to
`DefaultMediaSourceFactory`
(https://developer.android.com/media/media3/exoplayer/media-sources,
https://developer.android.com/media/media3/exoplayer/customization). In the
default extractor ordering array, Matroska sits at position 8 — after OGG / TS
(positions 6 and 7) and before ADTS, AC-3, AC-4, MP3 (positions 9–12). Verified
against `DefaultExtractorsFactory` 1.10.0; the complete order is FLV, FLAC, WAV,
MP4, AMR, PS, OGG, TS, **MATROSKA**, ADTS, AC3, AC4, MP3, AVI, MIDI, JPEG, PNG,
WEBP, BMP, HEIF, AVIF.
(https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/extractor/src/main/java/androidx/media3/extractor/DefaultExtractorsFactory.java)

A minimal wiring in Kotlin:

```kotlin
val extractorsFactory = DefaultExtractorsFactory()
    .setMatroskaExtractorFlags(0)  // see section 3 for flags

val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)

val player = ExoPlayer.Builder(context)
    .setMediaSourceFactory(mediaSourceFactory)
    .build()
```

When the URI has a `.mkv`/`.webm` suffix or when `FileTypes.matchesExtension(...)`
matches, `MatroskaExtractor` is tried first; otherwise each extractor in the
default order is probed via `sniff(...)`.

---

## 2. Codecs the extractor parses vs. codecs the device can render

"Parsing" means `MatroskaExtractor` recognises the track's `CodecID` string (or
`A_MS/ACM`, `V_MS/VFW/FOURCC`), constructs a `Format` with the right
`sampleMimeType` and CSD, and emits samples to a `TrackOutput`. It does not
guarantee the device has a matching `MediaCodec`. Rendering requires that the
platform (or a software fallback) exposes a decoder for the `Format.sampleMimeType`
with matching profile/level via `MediaCodecList`.

### 2.1 Video codecs parsed by `MatroskaExtractor` (1.10.0)

| Matroska `CodecID` / marker | Media3 `MimeTypes` constant | Hardware decoder on modern Android? |
|---|---|---|
| `V_VP8` | `VIDEO_VP8` | Yes, usually SW or HW (Android 4.3+) |
| `V_VP9` | `VIDEO_VP9` | Yes on most devices Android 7+ |
| `V_AV1` | `VIDEO_AV1` | HW on 2021+ SoCs; otherwise SW (libgav1) if enabled |
| `V_MPEG4/ISO/AVC` | `VIDEO_H264` | Universal |
| `V_MPEGH/ISO/HEVC` | `VIDEO_H265` | Android 5.0+, universal on TV |
| `V_MPEG2` | `VIDEO_MPEG2` | Device-dependent |
| `V_MPEG4/ISO/SP` / `ASP` / `AP` | `VIDEO_MP4V` | Device-dependent |
| `V_THEORA` | `VIDEO_UNKNOWN` (not renderable) | No Android decoder |
| `V_MS/VFW/FOURCC` | FOURCC-dependent | Depends on FOURCC |

Constants verified in source: `CODEC_ID_VP8`, `CODEC_ID_VP9`, `CODEC_ID_AV1`,
`CODEC_ID_H264`, `CODEC_ID_H265`, `CODEC_ID_MPEG2`, `CODEC_ID_MPEG4_SP`,
`CODEC_ID_MPEG4_ASP`, `CODEC_ID_MPEG4_AP`, `CODEC_ID_THEORA`, `CODEC_ID_FOURCC`
(https://github.com/androidx/media/blob/1.10.0/libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java).

Dolby Vision is NOT a separate CodecID — DV is signaled via the video track's
`BlockAddIDType` carrying a `dvcC`/`dvvC` configuration box, discussed in
section 4.

### 2.2 Audio codecs parsed by `MatroskaExtractor`

| Matroska `CodecID` | Media3 `MimeTypes` | Hardware/system decoder on Android? |
|---|---|---|
| `A_VORBIS` | `AUDIO_VORBIS` | SW (built into Media3 if FFmpeg extension added; otherwise platform on some builds) |
| `A_OPUS` | `AUDIO_OPUS` | Android 10+ system decoder |
| `A_AAC` / `A_AAC/MPEG*` | `AUDIO_AAC` | Universal |
| `A_MPEG/L3` | `AUDIO_MPEG` (MP3) | Universal |
| `A_MPEG/L2` | `AUDIO_MPEG_L2` | Platform-dependent |
| `A_AC3` | `AUDIO_AC3` | Most TV / many phones; passthrough over HDMI/SPDIF |
| `A_EAC3` | `AUDIO_E_AC3` (+ `AUDIO_E_AC3_JOC` when JOC extension detected) | Android TV commonly supports passthrough; phones usually decode but do not surface Atmos |
| `A_TRUEHD` | `AUDIO_TRUEHD` | Passthrough only — no software Atmos renderer in Media3 (unverified for SW TrueHD decoder presence on most devices) |
| `A_DTS` | `AUDIO_DTS` | Passthrough / device-dependent |
| `A_DTS/EXPRESS` | `AUDIO_DTS_EXPRESS` | Passthrough / device-dependent |
| `A_DTS/LOSSLESS` | `AUDIO_DTS_HD` | DTS-HD MA; passthrough only, and detection was improved in 1.9.0 (see section 8) |
| `A_FLAC` | `AUDIO_FLAC` | Android 6+ system decoder |
| `A_MS/ACM` | wav-encoded; WAVE format id decides MIME | Device-dependent |
| `A_PCM/INT/LIT` / `BIG` / `A_PCM/FLOAT/IEEE` | `AUDIO_RAW` | Rendered by `DefaultAudioSink` |

Constants verified: `CODEC_ID_VORBIS`, `CODEC_ID_OPUS`, `CODEC_ID_AAC`,
`CODEC_ID_MP3`, `CODEC_ID_MP2`, `CODEC_ID_AC3`, `CODEC_ID_E_AC3`,
`CODEC_ID_TRUEHD`, `CODEC_ID_DTS`, `CODEC_ID_DTS_EXPRESS`, `CODEC_ID_DTS_LOSSLESS`,
`CODEC_ID_FLAC`, `CODEC_ID_ACM`, `CODEC_ID_PCM_INT_LIT`, `CODEC_ID_PCM_INT_BIG`,
`CODEC_ID_PCM_FLOAT`
(https://github.com/androidx/media/blob/1.10.0/libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java).

**Atmos (practical):**

- E-AC-3 JOC (Dolby Digital Plus + Atmos) — Media3 sets
  `sampleMimeType = MimeTypes.AUDIO_E_AC3_JOC` when it detects the JOC
  extension. On Android TV / Shield / capable soundbars this will be offered
  for passthrough; on phones it almost always decodes as stereo E-AC-3.
- TrueHD Atmos — no software Atmos renderer. Renders only when the
  `AudioCapabilities` report TrueHD passthrough on the current output (HDMI/eARC).
  Otherwise tracks are selected-out by `DefaultTrackSelector` with an
  `UNSUPPORTED_SUBTYPE` reason.
- AC-4 — MKV does not define an official `A_AC4` CodecID in the 1.10.0
  extractor. Verified against the 1.10.0 source: `MatroskaExtractor.java` has
  no `CODEC_ID_AC4` identifier among its `CODEC_ID_*` constants (search of the
  file returned no match). AC-4 handling in Media3 is exercised through MP4
  (`Mp4Extractor`) and MPEG-TS paths. Plan to remux or transcode AC-4 out of
  MKV server-side, or transcode to E-AC-3.
  (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java)

### 2.3 Subtitle codecs parsed by `MatroskaExtractor`

| Matroska `CodecID` | Media3 `MimeTypes` | Rendered by `TextRenderer`/`SubtitleParser`? |
|---|---|---|
| `S_TEXT/UTF8` (SubRip / SRT) | `APPLICATION_SUBRIP` | Yes |
| `S_TEXT/WEBVTT` | `TEXT_VTT` | Yes |
| `S_TEXT/ASS`, `S_TEXT/SSA` | `TEXT_SSA` | Yes (SSA since 1.9.0; layer property since 1.8.0) |
| `S_VOBSUB` | `APPLICATION_VOBSUB` | Yes (added 1.9.0) |
| `S_HDMV/PGS` | `APPLICATION_PGS` | Yes (bitmap) |
| `S_DVBSUB` | `APPLICATION_DVBSUBS` | Yes |

Constants verified: `CODEC_ID_SUBRIP`, `CODEC_ID_ASS`, `CODEC_ID_SSA`,
`CODEC_ID_VTT`, `CODEC_ID_VOBSUB`, `CODEC_ID_PGS`, `CODEC_ID_DVBSUB`
(https://github.com/androidx/media/blob/1.10.0/libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java,
https://github.com/androidx/media/blob/release/CHANGELOG.md).

---

## 3. `DefaultExtractorsFactory` wiring of `MatroskaExtractor`

`MatroskaExtractor` has two public flag bits (verified in source):

```kotlin
// androidx.media3.extractor.mkv.MatroskaExtractor

const val FLAG_DISABLE_SEEK_FOR_CUES     = 1        // = 1 << 0
const val FLAG_EMIT_RAW_SUBTITLE_DATA    = 1 shl 1  // = 2
```

- `FLAG_DISABLE_SEEK_FOR_CUES` — disables seeking to the Cues element; the
  media is treated as unseekable if Cues appears after the first Cluster. Only
  set this to work around malformed files.
- `FLAG_EMIT_RAW_SUBTITLE_DATA` — if present, subtitle samples are passed
  through untouched; if absent, the extractor transcodes each subtitle sample
  to `MimeTypes.APPLICATION_MEDIA3_CUES` using the configured
  `SubtitleParser.Factory`. `DefaultExtractorsFactory` sets this flag for you
  whenever `experimentalSetTextTrackTranscodingEnabled(false)` is requested.

The construction inside `DefaultExtractorsFactory` (as of 1.10.0) is effectively:

```kotlin
val extractor = MatroskaExtractor(
    subtitleParserFactory,
    matroskaFlags or
        if (textTrackTranscodingEnabled) 0
        else MatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA
)
```

(verified against
`libraries/extractor/src/main/java/androidx/media3/extractor/DefaultExtractorsFactory.java`
at tag 1.10.0).

Configuring from Kotlin:

```kotlin
val factory = DefaultExtractorsFactory()
    .setMatroskaExtractorFlags(MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES)
    // optional: inject a custom SubtitleParser.Factory
    .setSubtitleParserFactory(DefaultSubtitleParserFactory())
    // opt out of in-extractor subtitle transcoding if you need raw samples:
    // .experimentalSetTextTrackTranscodingEnabled(false)
```

Note: `FLAG_DISABLE_WEBVTT` **does not exist** on `MatroskaExtractor` in 1.10.0;
that flag lives on `Mp4Extractor` / `FragmentedMp4Extractor`
(`FLAG_DISABLE_WEBVTT_IN_MP4`) to suppress the ISO-BMFF WebVTT box. For MKV, the
S_TEXT/WEBVTT track is always enabled.

---

## 4. Dolby Vision configuration box extraction from MKV

Matroska carries the DV configuration as a `BlockAdditionMapping`/`BlockAddIDExtraData`
pair on the video track. The extractor tags incoming BlockAddID extra data by
`BlockAddIdType` FourCC:

```kotlin
// Values verified in MatroskaExtractor.java @ 1.10.0
private const val BLOCK_ADD_ID_TYPE_DVCC = 0x64766343  // "dvcC" — DV profile <= 7
private const val BLOCK_ADD_ID_TYPE_DVVC = 0x64767643  // "dvvC" — DV profile  > 7
```

(https://github.com/androidx/media/blob/1.10.0/libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java)

When either BlockAddIDType matches, the extractor copies the payload into
`track.dolbyVisionConfigBytes`. During `Track.initializeFormat(...)`, those
bytes are converted into a DV codec-string (e.g. `dvh1.05.06`, `dvhe.08.09`) and
surfaced on `Format.codecs` / `Format.sampleMimeType = MimeTypes.VIDEO_DOLBY_VISION`.
CSD is preserved through the parent HEVC/AV1 track (`hvcC` / `av1C` still carry
the base-layer parameter sets) so that `MediaCodec` receives the correct
SPS/PPS or AV1 sequence OBUs.

Additional DV box names you may encounter in MP4 — `hvcE`, `dvwC` — are handled
in the MP4 extractor (`Mp4BoxTypes.TYPE_dvcC`, `TYPE_dvvC`, `TYPE_hvcE`,
`TYPE_dvwC`) rather than in MKV. In MKV today (1.10.0) only `dvcC`/`dvvC` are
recognised. HEVC-with-EL (`hvcE`) and AV1-DV (`dvwC`) equivalents in MKV BlockAdd
are **not surfaced** by `MatroskaExtractor` 1.10.0 (unverified whether server-side
MKV typically carries them — most DV MKVs in the wild use `dvcC`/`dvvC`).

**Profile 10 (DV over AV1)**: Media3 1.10.0 added Dolby Vision Profile 10 support
(https://github.com/androidx/media/blob/release/CHANGELOG.md). This relies on
`dvvC` (profile > 7) attached to an AV1 base track.

### 4.1 Practical: what profiles actually play

- **Single-track profiles** (the ones the Android `MediaCodec` HEVC/AV1 DV
  decoder exposes) are the only ones Media3 can render end-to-end:
  - Profile 5 (IPT-PQ, HEVC; no BL-compatible side-decode path)
  - Profile 8.1 (HEVC, BL-compat with HDR10)
  - Profile 8.2 (HEVC, BL-compat with SDR)
  - Profile 8.4 (HEVC, HLG-compat)
  - Profile 10 (AV1) — 1.10.0+
- **Profile 7 (dual-layer MEL/FEL)** is NOT supported: Android's `MediaCodec`
  does not expose a decoder that composes the enhancement layer back onto the
  base layer. `MatroskaExtractor` parses the base-layer HEVC and the DV metadata,
  but the EL track is discarded or unrenderable. Practically, MKVs with Profile
  7 need to be converted server-side to Profile 8.1 (for HDR10-compatible DV
  fallback) before the Android client can render them. This is a hard platform
  limit, not a Media3 extractor limitation (unverified beyond widely-reported
  device behaviour; no OEM has shipped a Profile 7 decoder).

### 4.2 Codec reuse across DV profiles

Media3 1.9.0 disables codec reuse for DV content with different profiles, so
switching clips across Profile 5 and Profile 8.1 during a playlist no longer
tries to keep the same codec instance alive
(https://github.com/androidx/media/blob/release/CHANGELOG.md). 1.9.1 added a
fix for DV fallbacks to AVC/HEVC when the DV decoder is unavailable.

---

## 5. HDR static metadata (SMPTE ST 2086 + MaxCLL/MaxFALL) from MKV

Matroska's `MasteringMetadata` element and `Colour` element carry the Mastering
Display Colour Volume (ST 2086) primaries plus `MaxCLL`/`MaxFALL`.
`MatroskaExtractor` parses these into a `ColorInfo` on the track's `Format`:

```kotlin
// androidx.media3.common.ColorInfo (verified 1.10.0)
class ColorInfo(
    val colorSpace: Int,       // C.COLOR_SPACE_BT2020 etc.
    val colorRange: Int,
    val colorTransfer: Int,    // C.COLOR_TRANSFER_ST2084, HLG, etc.
    val lumaBitdepth: Int,
    val chromaBitdepth: Int,
    val hdrStaticInfo: ByteArray?   // CTA-861.3 HDR Static Metadata Descriptor
)
```

(https://github.com/androidx/media/blob/1.10.0/libraries/common/src/main/java/androidx/media3/common/ColorInfo.java)

`hdrStaticInfo` is the 25-byte CTA-861.3 HDR Static Metadata Descriptor payload
that `MediaCodec` expects under `MediaFormat.KEY_HDR_STATIC_INFO`. Media3's
`MediaCodecVideoRenderer` (and the audio/video rendererer infrastructure) wires
this through automatically when building the `MediaFormat`:

```kotlin
// Approximate — actual code lives in MediaFormatUtil
val mediaFormat = MediaFormat()
format.colorInfo?.hdrStaticInfo?.let {
    mediaFormat.setByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO, ByteBuffer.wrap(it))
}
```

`KEY_HDR10_PLUS_INFO` is per-frame metadata (not static); MKV does not carry HDR10+
dynamic metadata inside static track descriptors. If a stream has HDR10+ it is
embedded in the HEVC/AV1 bitstream itself as SEI/OBU metadata and forwarded to
`MediaCodec` frame-by-frame by the HEVC/AV1 reader. Media3 supports out-of-band
HDR10+ metadata in a few places, but in MKV specifically the extractor surfaces
only the static profile.

To inspect what the extractor actually gave you:

```kotlin
val colorInfo = format.colorInfo
Log.d("MKV", "transfer=${colorInfo?.colorTransfer} bitdepth=${colorInfo?.lumaBitdepth} " +
    "hasStaticHdr=${colorInfo?.hdrStaticInfo != null}")
```

---

## 6. Subtitle handling

Media3 has two subtitle data paths. Which one a track goes through depends on
whether `FLAG_EMIT_RAW_SUBTITLE_DATA` is set on `MatroskaExtractor`:

1. **In-extractor transcoding (default)** — subtitle samples are parsed to
   `Cue` lists at extraction time via a `SubtitleParser.Factory` and handed to
   the renderer as `MimeTypes.APPLICATION_MEDIA3_CUES`. This is the Media3 1.4+
   default path and is what `DefaultExtractorsFactory` ships.
2. **Pass-through to TextRenderer** — extractor emits raw subtitle bytes; the
   `TextRenderer` drives a legacy `SubtitleDecoder` on its own thread.

### 6.1 PGS (HDMV PGS — bitmap)

- CodecID: `S_HDMV/PGS` → `MimeTypes.APPLICATION_PGS`.
- Renderer: `PgsParser` produces bitmap `Cue`s (with `bitmap` and
  `bitmapHeight`/`bitmapWidth` set).
- Rendered by `SubtitleView` (or your custom view) by drawing the bitmap; text
  styling options do not apply.
- **Known bug**: PGS bitmaps are stretched to the video's aspect ratio on some
  configurations — issue
  [#2849](https://github.com/androidx/media/issues/2849) (1.8.0+, status closed
  on GitHub but no fix committed as of 1.10.0; treat as open). Workaround in
  your renderer: override the `Cue.bitmap` scaling path to honor the PGS
  presentation size rather than the video frame aspect.

### 6.2 ASS / SSA (styled text)

- CodecIDs: `S_TEXT/ASS`, `S_TEXT/SSA` → `MimeTypes.TEXT_SSA`.
- 1.8.0: added the `layer` property (z-ordering of cues)
  (https://github.com/androidx/media/blob/release/CHANGELOG.md).
- 1.9.0: extended `S_TEXT/SSA` CodecID support specifically in MKV
  (https://github.com/androidx/media/blob/release/CHANGELOG.md).
- Known: issue
  [#2800](https://github.com/androidx/media/issues/2800) — incorrect
  `initializationData` when the ASS header contains a null character (closed as
  "bad media"). Validate server-side or normalise the header when exporting.
- Fallback: Media3's `SsaParser` handles a common subset of v4+ styling. Exotic
  styles (complex positioning, karaoke `\k` tags, 3D effects) degrade to plain
  text.

### 6.3 SubRip (SRT) and WebVTT

- CodecIDs: `S_TEXT/UTF8` → `APPLICATION_SUBRIP`; `S_TEXT/WEBVTT` →
  `TEXT_VTT`. Both are rendered through `SubripParser` / `WebvttParser`.
- 1.9.0 tightened timestamp validation to exactly 3 decimal places
  (https://github.com/androidx/media/blob/release/CHANGELOG.md). Malformed
  SRT/VTT with `,00` or `.0000` will now be rejected at parse time — relevant
  if you ingest user-uploaded subtitles.

### 6.4 What works out of the box vs. custom glue

- Out-of-box: SRT, WebVTT, ASS/SSA, PGS (modulo the scaling bug), VobSub, DVBSUB.
- Custom glue required: merging external sidecar subtitle files (Silo
  server exposes these) with the in-container tracks. Use
  `MergingMediaSource` with a `SingleSampleMediaSource` per sidecar. The
  Silo Android app already has this pattern — see
  `PlayerViewModel.kt` if applicable to your feature.

---

## 7. Seek behavior on MKV

`MatroskaExtractor` produces a `ChunkIndex` (cueing index) by reading the Cues
element:

- If the file has a `SeekHead` that locates `Cues`, and `Cues` is parsed
  successfully, seeks land on I-frames at cue-point granularity. Accuracy is
  "seek-to-cue-and-then-parse-forward" — the player will render exactly at the
  requested position because `ExoPlayer.seekTo(ms)` internally seeks to the
  nearest cue and then drops samples before the target PTS.
- If `FLAG_DISABLE_SEEK_FOR_CUES` is set, or `Cues` is missing / corrupt, the
  extractor reports `SeekMap.SeekPoints(startPosition)` and playback is
  effectively unseekable.
- Media3 1.9.0 fixed a bug where cues were not correctly associated with their
  source track in multi-track files, producing inaccurate seeks
  (https://github.com/androidx/media/blob/release/CHANGELOG.md). Upgrading to
  1.10.0 carries that fix.
- Known issue [#2780](https://github.com/androidx/media/issues/2780) — MKV
  files with endianness mismatches between the `SeekHead` ID and the `Cues` ID
  confuse the parser and the seek bar greys out. No fix shipped; re-mux with
  `mkvmerge` to produce a normalised SeekHead.

---

## 8. Known limitations and bugs in Media3 1.10.0

- Issue [#3131](https://github.com/androidx/media/issues/3131) — "A MKV video
  cannot be played" (open, "bad media") — certain MKVs report all tracks as
  unplayable ("Media includes video tracks, but none are playable by device")
  even though they render in ExoPlayer2. No root cause filed; server-side
  remux is the workaround.
- Issue [#2780](https://github.com/androidx/media/issues/2780) — fast
  forward / rewind disabled on endianness-mismatched seekhead (see section 7).
- Issue [#2849](https://github.com/androidx/media/issues/2849) — PGS subtitles
  stretched to video aspect (see section 6.1).
- Issue [#2935](https://github.com/androidx/media/issues/2935) — intermittent
  VobSub subtitles are missed (closed but referenced for completeness — most
  Silo MKVs use PGS/ASS, not VobSub).
- Issue [#2737](https://github.com/androidx/media/issues/2737) — audio track
  not recognised correctly (closed "bad media"; validate against your server
  output with `ffprobe` before blaming the client).
- AC-4 in MKV is not parsed (no `CODEC_ID_AC4` in `MatroskaExtractor` 1.10.0).
- TrueHD Atmos has no software decoder: playback requires HDMI/eARC
  passthrough capability.
- Dolby Vision Profile 7 (dual-layer MEL/FEL) is not decodable on any Android
  device Media3 currently runs on.

CHANGELOG and release notes consulted:
https://github.com/androidx/media/blob/release/CHANGELOG.md.

---

## 9. Pre-flight capability checks before starting playback

Before committing a session to a Silo item you can cheaply answer "will
this MKV actually play?" by combining the server's pre-probed `MediaInfo`
with a `MediaCodecList` lookup on the client. Do this before you instantiate
`ExoPlayer` so you can redirect the user to a transcode stream if needed.

```kotlin
import android.media.MediaCodecList
import android.media.MediaFormat
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes

data class Playability(
    val canPlayVideo: Boolean,
    val canPlayAudio: Boolean,
    val dolbyVisionSafe: Boolean,
    val notes: List<String>,
)

/**
 * Given the pre-parsed track info (from the Silo server's MediaInfo DTO,
 * or from a local pre-extraction pass), decide if we should attempt direct play.
 */
fun checkMkvPlayability(
    videoFormat: Format,
    audioFormat: Format,
    context: android.content.Context,
): Playability {
    val notes = mutableListOf<String>()
    val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

    // ---- Video ----
    val videoMime = videoFormat.sampleMimeType ?: MimeTypes.VIDEO_UNKNOWN
    val videoMf = MediaFormat.createVideoFormat(
        videoMime,
        videoFormat.width.coerceAtLeast(1),
        videoFormat.height.coerceAtLeast(1),
    ).apply {
        videoFormat.codecs?.let { setString(MediaFormat.KEY_CODECS_STRING, it) }
    }
    val videoDecoder = codecList.findDecoderForFormat(videoMf)
    val videoOk = videoDecoder != null

    // Dolby Vision side-check: Profile 7 is unsupported on all Android devices.
    val dvProfileSeven = videoFormat.codecs?.let { it.startsWith("dvhe.07") || it.startsWith("dvh1.07") } == true
    if (dvProfileSeven) notes += "Dolby Vision Profile 7 (dual-layer) is not decodable on Android."

    // ---- Audio ----
    val audioMime = audioFormat.sampleMimeType ?: MimeTypes.AUDIO_UNKNOWN
    val audioMf = MediaFormat.createAudioFormat(
        audioMime,
        audioFormat.sampleRate.coerceAtLeast(1),
        audioFormat.channelCount.coerceAtLeast(1),
    )
    val audioDecoder = codecList.findDecoderForFormat(audioMf)
    val audioOk = audioDecoder != null || audioMime == MimeTypes.AUDIO_TRUEHD || audioMime == MimeTypes.AUDIO_E_AC3_JOC
    // For TrueHD / EAC3-JOC the renderer will rely on AudioCapabilities passthrough,
    // not a software decoder.

    if (!videoOk) notes += "No MediaCodec decoder for $videoMime (${videoFormat.codecs})"
    if (!audioOk) notes += "No MediaCodec decoder for $audioMime (${audioFormat.codecs})"

    return Playability(
        canPlayVideo = videoOk && !dvProfileSeven,
        canPlayAudio = audioOk,
        dolbyVisionSafe = !dvProfileSeven,
        notes = notes,
    )
}
```

Two caveats on this pattern:

1. `MediaCodecList.findDecoderForFormat` uses
   `MediaCodecList.REGULAR_CODECS` and therefore sees only OEM+Google codecs,
   not Media3 extension decoders (libgav1, libvpx, ffmpeg). If you have
   extensions wired in, add a fallback check against `ExoPlayer`'s
   `DefaultRenderersFactory.setExtensionRendererMode(...)` setting.
2. DV profile strings inside `Format.codecs` are produced by
   `MatroskaExtractor` after it parses `dvcC`/`dvvC` — they are reliable.
3. Passthrough eligibility for TrueHD / E-AC-3 JOC / DTS-HD is more accurately
   asked via `AudioCapabilities.getCapabilities(context)` once you know the
   currently-bound `AudioManager` output. If the user is on Bluetooth
   headphones, TrueHD passthrough is unavailable even on a Shield TV.

---

## Sources

- `MatroskaExtractor` source (1.10.0) —
  https://github.com/androidx/media/blob/1.10.0/libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java
- `DefaultExtractorsFactory` source (1.10.0) —
  https://github.com/androidx/media/blob/1.10.0/libraries/extractor/src/main/java/androidx/media3/extractor/DefaultExtractorsFactory.java
- `ColorInfo` source (1.10.0) —
  https://github.com/androidx/media/blob/1.10.0/libraries/common/src/main/java/androidx/media3/common/ColorInfo.java
- Media3 1.10.0 release tag — https://github.com/androidx/media/tree/1.10.0
- Media3 CHANGELOG — https://github.com/androidx/media/blob/release/CHANGELOG.md
- Media3 media sources docs — https://developer.android.com/media/media3/exoplayer/media-sources
- Media3 customization docs — https://developer.android.com/media/media3/exoplayer/customization
- Media3 landing — https://developer.android.com/media/media3
- Issue #2780 "Unable to fast forward/rewind mkv video" — https://github.com/androidx/media/issues/2780
- Issue #2849 "PGS subtitles are stretched to match video aspect ratio" — https://github.com/androidx/media/issues/2849
- Issue #2800 "initializationData not correct when there is null character in ass subtitle" — https://github.com/androidx/media/issues/2800
- Issue #2935 "Intermittent vobsub subtitles are missed" — https://github.com/androidx/media/issues/2935
- Issue #3131 "A MKV video cannot be played" — https://github.com/androidx/media/issues/3131
- Issue search used — https://github.com/androidx/media/issues?q=is%3Aissue+mkv+OR+matroska
- `DefaultExtractorsFactory` 1.10.0 source — https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/extractor/src/main/java/androidx/media3/extractor/DefaultExtractorsFactory.java
- `MimeTypes` 1.10.0 source — https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/MimeTypes.java
- Media3 1.10.0 RELEASENOTES — https://raw.githubusercontent.com/androidx/media/1.10.0/RELEASENOTES.md

## Validation log

- corrected: "Matroska sits at position 8 (after ADTS, before AC-3)" → Matroska at position 8 is **after** OGG/TS and **before** ADTS/AC3/AC4. Full order verified: FLV, FLAC, WAV, MP4, AMR, PS, OGG, TS, MATROSKA, ADTS, AC3, AC4, MP3, AVI, MIDI, JPEG, PNG, WEBP, BMP, HEIF, AVIF. (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/extractor/src/main/java/androidx/media3/extractor/DefaultExtractorsFactory.java)
- verified: `FLAG_DISABLE_SEEK_FOR_CUES = 1` and `FLAG_EMIT_RAW_SUBTITLE_DATA = 1 << 1` (= 2) on `MatroskaExtractor` 1.10.0. (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java)
- verified: `BLOCK_ADD_ID_TYPE_DVCC = 0x64766343` and `BLOCK_ADD_ID_TYPE_DVVC = 0x64767643` on `MatroskaExtractor` 1.10.0. (same source)
- verified: Media3 1.10.0 RELEASENOTES entry "DTS-HD detection" for Matroska, confirming §8 claim about 1.9.0 DTS-HD lossless detection work was extended in 1.10.0. (https://raw.githubusercontent.com/androidx/media/1.10.0/RELEASENOTES.md)
- verified: AC-4 is not parsed — the 1.10.0 `MatroskaExtractor` file has no `CODEC_ID_AC4` constant. Removed the "(unverified)" marker on the AC-4 claim in §2.2.
- verified: MIME strings — `AUDIO_E_AC3_JOC = "audio/eac3-joc"`, `AUDIO_TRUEHD = "audio/true-hd"`, `APPLICATION_PGS = "application/pgs"`, `TEXT_SSA = "text/x-ssa"`, `APPLICATION_SUBRIP = "application/x-subrip"`, `TEXT_VTT = "text/vtt"`, `APPLICATION_VOBSUB = "application/vobsub"`, `APPLICATION_DVBSUBS = "application/dvbsubs"`, `APPLICATION_MEDIA3_CUES = "application/x-media3-cues"`. (https://raw.githubusercontent.com/androidx/media/1.10.0/libraries/common/src/main/java/androidx/media3/common/MimeTypes.java)
- still unverified: Profile 7 decode support exclusion on Android MediaCodec — the underlying claim is that no OEM has shipped a Profile 7 decoder. This is true at the time of writing but would need per-device `MediaCodecList` enumeration to falsify definitively; kept `(unverified)` inline as before.
- still unverified: whether `hvcE` / `dvwC` MKV BlockAddIDs appear in real Silo library content — answerable only by `ffprobe` / `mkvinfo` on the server's media.
