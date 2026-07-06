Document version: July 2026 survey. Reference checkouts live at `/Volumes/NVMe/dev/github/reference-players/` (shallow clones; not part of this repo).

# Reference Player Comparison — Silo Android vs. Open-Source Video Players

This document records a code-level survey of seven open-source Android video
players, focused on the axes that matter for Silo's direct-play goals: full
codec coverage, 4K Dolby Vision profiles 5/7/8, Atmos/TrueHD/DTS audio
passthrough, and playback robustness (error recovery, fallback ladders,
display-mode switching). It closes with the verdict on our own player and the
changes made as a result.

Surveyed (all analyzed at their July 2026 default branches):

| App | Engine(s) | Why it was surveyed |
|---|---|---|
| jellyfin-androidtv | Media3 (legacy `VideoManager` path + flagged rewrite) | The most mature OSS Android TV client for 4K DV + Atmos direct play |
| Findroid | Media3 + libmpv (`dev.jdtech.mpv`) | Dual-backend Jellyfin client, closest architecture to ours |
| jellyfin-android | Media3 | Official phone client; runtime `MediaCodecList` device profile |
| Streamyfin | libmpv via custom Expo module | mpv-first design, tiered hwdec, careful native lifecycle |
| NextPlayer | Media3 + NextLib (FFmpeg audio+video decoders) | Local player known for broad codec support |
| Just Player (moneytoo/Player) | Patched Media3 fork + FFmpeg/AV1/IAMF/MPEG-H AARs | The only app with a real DV Profile 7 fallback |
| mpv-android (is.xyz) | libmpv | Canonical libmpv frontend; surface-lifecycle reference |

## 1. Comparison matrix

"Silo" refers to this repo's player stack (`android-shared/.../common/player/`).

| Capability | Silo | jellyfin-androidtv | Findroid | jellyfin-android | Streamyfin | NextPlayer | Just Player |
|---|---|---|---|---|---|---|---|
| Engine strategy | Media3 + mpv, **auto-selected per route** with server plan | Media3 only | Media3 or mpv, global user pref | Media3 only | mpv only | Media3 only | Media3 (patched fork) |
| Capability report to server | Decoder+panel+audio-sink probes → `ClientCodecCapabilities` + per-engine envelopes | Decoder-derived `DeviceProfile` with `VideoRangeType` exclusions | **"Direct play all"** (empty profiles) | Runtime `MediaCodecList` intersection | Static "play everything" profile, DV force-excluded | n/a (local files) | n/a (local files) |
| DV P5 | Direct when decoder+panel claim it | Direct when decoder claims it | Undeclared (decoder's problem) | Transcoded (never advertised) | Transcoded (`VideoRangeType != DOVI`) | None | Native decoder only |
| DV P7 | **Now:** native (multi-instance gate) or mpv base-layer; was: always transcode | Direct only with `DvheDtb` + multi-instance HEVC | Undeclared | Transcoded | Transcoded | None | `mapDV7ToHevc` renderer patch (BL as HDR10) |
| DV P8 | Direct (base layer renders as HDR10) | Direct when DV **or** HDR10 decoder present | Undeclared | Transcoded | Transcoded | None | Media3 built-in BL fallback |
| Atmos (E-AC-3 JOC) / TrueHD passthrough | Capability-probed (`AudioCapabilitiesReceiver` + `isDirectPlaybackSupported`), MIME-preference presets, mpv `audio-spdif` | Advertised broadly; stock `DefaultAudioSink` negotiates | None (PCM decode) | None | None ("passthrough" = channel cap) | None | None |
| FFmpeg audio fallback | Bundled `media3-decoder-ffmpeg` AAR, `MODE_PREFER` | Compiled in, `MODE_ON`/`MODE_PREFER` pref | Jellyfin fork AAR, `MODE_ON` | Extension, `MODE_ON`→`PREFER` after failure | mpv/FFmpeg native | NextLib (audio+video) | FFmpeg AAR (audio+experimental video) |
| Error recovery | Plan-driven ladder: alternate direct engine → remux → transcode (TV; **now also mobile**) | 3-strike direct→direct-stream→transcode, 30s stabilization reset | **None** | Decoder swap, then 3-step server degrade | None native | None | Release on SOURCE error only |
| Decoder fallback | `setEnableDecoderFallback(true)` | Same | Not set | Same + custom `MediaCodecSelector` | n/a | Same | Selector re-invalidation only |
| Tunneling | Off (deliberate; Google TV stalls) | Off (deliberate) | **On** always | Off | n/a | Off | User toggle |
| Refresh-rate matching | TV (`HdrDisplayController`) + phone (`RefreshRateMatcher`), integer-multiple scoring | Mode scoring incl. 2×/2.5×, scale-on-TV/device pref | None | None | None | None | Integer-multiple match, defers playback until switch completes |
| Buffering | Device-profile-aware staged `LoadControl` | User pref (default/50s/80s) | Default | User pref | mpv 64MiB demuxer cache | Default | Default |

## 2. What each app taught us

### jellyfin-androidtv (the load-bearing reference)
- **Capability detection is decoder-first, not panel-first.** The device
  profile is built from `MediaCodecList` profile/level enumeration; panel
  `Display.HdrCapabilities` appears only in a diagnostic report. Our
  `MediaCodecCapabilitiesProbe` already follows this model (it was in fact
  modeled on theirs).
- **DV P7 requires `DolbyVisionProfileDvheDtb` plus multi-instance HEVC**
  (`maxSupportedInstances >= 2`) — the enhancement layer needs a second
  concurrent decode. Our probe already had this exact gate; our *policy* was
  ignoring the result (fixed, see §4).
- **P8's base layer is HDR10**: they allow P8 direct play whenever the device
  has DV *or plain HDR10* decode. Our policy (`P8 → always direct, recover on
  failure`) is a more aggressive version of the same idea.
- **Audio: advertise broadly, let the stock `DefaultAudioSink` negotiate
  passthrough at runtime.** No AVR-model detection. Their only knobs are
  direct-vs-downmix and an AC3 toggle. Our approach (probing
  `AudioCapabilitiesReceiver` and biasing track selection by reachable MIMEs)
  is strictly more precise; theirs is simpler and relies on the recovery
  ladder when the sink refuses.
- **Tunneling off everywhere** — matches our choice; reported, never requested.
- The 3-strike error ladder with a 30-second "playback stabilized" retry-reset
  is the pattern our TV player already implements (bounded transient retries
  reset by `onPositionChanged`); mobile now has it too.
- A `KnownDefects` hardcoded model blocklist (Fire TV DV+HDR10+ quirks) is a
  concept worth adopting if/when device-specific DV reports come in.

### Just Player (the DV P7 trick)
- Ships a **patched Media3 fork** whose `MediaCodecVideoRenderer` gains
  `setMapDV7ToHevc`: when the display/decoder can't do native DV, a
  `video/dolby-vision` P7 format is fed to the plain **HEVC HDR10 decoder**
  (the P7 base layer is HDR10-compatible; the EL rides in unspec-62 NALs the
  decoder ignores). Stock Media3 1.10 maps only P8→HEVC / P9→AVC — P7 is not
  mapped, which is exactly why they patched it.
- We chose **not** to fork Media3 for this. Our architecture already has a
  second engine whose decoder stack (FFmpeg) handles P7 base layers natively:
  mpv. Routing non-native P7 to mpv gets the same user outcome (HDR10
  playback of the base layer instead of a 4K transcode) with zero fork
  maintenance.
- Their TS extractor hardening (`FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS`, widened
  timestamp search — ExoPlayer issue #8571) was directly adopted (§4).
- Their frame-rate matcher **defers playback until the display-mode switch
  completes** — a polish item ours doesn't do yet.

### Findroid / Streamyfin / jellyfin-android / NextPlayer / mpv-android
- None of these attempt DV handling or real audio passthrough; Findroid and
  Streamyfin delegate everything to the decoder ("direct play all" profiles),
  jellyfin-android and Streamyfin explicitly exclude DV (transcode), and all
  mpv users run PCM-only audio (`ao=aaudio`/`audiotrack` with no
  `audio-spdif`).
- Findroid's `MPVPlayer` is the ancestor of our `MpvPlayer` wrapper (same
  `BasePlayer` bridge, same option skeleton). Notable deltas: Findroid
  defaults `hwdec=mediacodec` (direct); we shipped `mediacodec-copy` (fixed,
  §4). Findroid exposes raw `mpv.conf` editing as a power-user escape hatch.
- Streamyfin's tiered hwdec (emulator→`no`, TV→`mediacodec`,
  phone→`mediacodec-copy`) exists because direct hwdec can wedge the emulator;
  worth remembering if emulator reports come in after the hwdec change.
- Streamyfin and Findroid both **never call `MPVLib.destroy()`** (libmpv 1.0
  JNI use-after-free); our wrapper manages its own lifecycle — keep an eye on
  teardown crash telemetry.
- mpv-android's value is its init ordering (`force-window=no` → init →
  surface callbacks last) and surface teardown sequence (`vo=null` →
  `force-window=no` → detach), including a self-documented race: `vo=null`
  does not synchronously wait for VO deinit. Our `MpvPlayer` follows the same
  shape and inherits the same theoretical race.
- jellyfin-android's two-tier recovery (local decoder swap via
  `EXTENSION_RENDERER_MODE_PREFER` rebuild, then progressive server
  degradation) validates our alternate-engine-then-server ladder.

## 3. Verdict on the Silo player

**No rewrite.** After reading all seven codebases, ours is the most capable
stack of the group on the axes that matter here:

- We are the only client with **route-aware dual engines** (Media3 for
  HLS/DRM/cast, mpv for hard containers/ASS/compatibility) selected per
  playback plan with state-transfer on switch.
- Our audio-capability probing (per-encoding `AudioTrack.isDirectPlaybackSupported`,
  HDMI hot-plug re-detection, spatializer awareness, passthrough-biased track
  selection) is more precise than anything surveyed — jellyfin-androidtv gets
  by with less; everyone else has nothing.
- Our server-negotiated execution plan with fallback candidates is a superset
  of jellyfin's device-profile negotiation.
- The probes (decoder DV profile/level enumeration with the multi-instance P7
  gate) already matched the best-in-class implementation.

The roughness was concentrated in five specific gaps, all fixed in this pass
(§4). The structural criticisms that remain (a 1,700-line hand-written
`MpvPlayer`, duplicated phone/TV ViewModel logic) are real but are refactors,
not correctness issues, and none of the surveyed apps do better (Findroid's
wrapper is the same shape; jellyfin-androidtv maintains two full player
stacks).

## 4. Changes made from this survey

1. **Mobile player-error recovery (parity with TV)** — the phone player had
   no `onPlayerError` wiring at all and forced a FULL transcode on any
   preflight failure. It now runs the same ladder as TV: bounded
   transient-network same-route retry → `PlaybackRecoveryPlanner` → alternate
   direct engine (with `attemptedEngines` ping-pong guard and route-event
   telemetry) → server remux → transcode, single-flighted.
   (`PlayerViewModel.kt`, `PlayerScreen.kt`)
2. **DV Profile 7 direct play** — policy no longer hard-refuses P7:
   - Native path: allowed when the decoder claims a dual-layer profile *and*
     multi-instance HEVC (the probe's existing gate; jellyfin-androidtv's
     model). `DisplayHdrProbe` no longer strips P7 from the intersection —
     Android panels can't report per-profile DV support, so fabricating
     `[5, 8]` there silently vetoed legitimate decoder claims.
   - Fallback path: `PlaybackRecoveryPlanner` now prefers an **alternate
     direct engine** for `UnsupportedDvProfile` (mpv renders the P7/P8 HDR10
     base layer and tone-maps P5) and excludes remux (it would re-send the
     same rejected stream), before conceding a transcode — Just Player's
     `mapDV7ToHevc` outcome without a Media3 fork.
   (`PlaybackCapabilityDetector.kt`, `DisplayHdrProbe.kt`,
   `PlaybackRecoveryPlanner.kt`, tests updated)
3. **mpv audio passthrough actually bitstreams now** — `ao` was `aaudio`,
   whose mpv AO cannot open compressed (IEC61937) streams, so our
   `audio-spdif` configuration silently decoded Atmos/TrueHD to PCM. Default
   is now `audiotrack,aaudio`. (`MpvPlayer.kt`)
4. **mpv hwdec is direct-first** — `mediacodec,mediacodec-copy` instead of
   forced copy-back (which doubles memory bandwidth on 4K); mpv falls back
   per-codec automatically. Matches mpv-android/Findroid/Streamyfin-TV.
   (`MpvPlayer.kt`)
5. **TS extractor hardening** — `FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS` (DTS in
   Blu-ray-sourced M2TS) and a 1500-packet timestamp search window for
   high-bitrate/sparse-PTS transport streams. (`SiloPlayerFactory.kt`)

## 5. Ideas noted but deliberately not adopted (yet)

- **Media3 fork with `mapDV7ToHevc`** — mpv routing covers the same content;
  a fork is a standing maintenance tax.
- **Tunneling toggle** (Just Player) — we ship tunneling-off for cause
  (Google TV Streamer stalls); revisit only with a per-device allowlist.
- **`KnownDefects` device blocklist** (jellyfin-androidtv) — no confirmed
  device-specific DV defects in our telemetry yet; adopt the pattern when the
  first one lands.
- **Defer playback until display-mode switch completes** (Just Player) —
  polish; our seamless-only `VIDEO_CHANGE_FRAME_RATE_STRATEGY` avoids the
  worst of the mode-switch black-frame problem already.
- **Audio night mode / loudness normalization** (jellyfin-androidtv
  `DynamicsProcessing` limiter; NextPlayer `LoudnessEnhancer`) — feature work,
  not a fix; note that applying an audio effect to the session **forces PCM**
  and is therefore mutually exclusive with passthrough.
- **User-editable `mpv.conf`** (Findroid) — power-user escape hatch worth
  considering once the mpv route stabilizes.
