# Playback Buffer Architecture — Design

**Date:** 2026-07-30
**Status:** Approved in design conversation; this document is its record
**Scope:** `android-shared` playback buffering (phone + TV share it)

## Why

Two problems, one of which masks the other.

**1. The buffer fills, the socket idles, the proxy kills the connection.**
`DefaultLoadControl` loads until `maxBufferMs`, then stops reading the socket
until the buffer drains below `minBufferMs`. The connection therefore sits idle
for roughly `max − min` of playback time. Today's hardcoded policy is
`min 50s / max 120s` — a **70-second idle window** against a proxy
`send_timeout` that defaults to 60s. The connection is dropped whenever the
buffer fills on a long direct-play file, and the client only discovers it when
it comes back for more data. This is arithmetic, not a race.

**2. The byte cap silently overrides the time target.**
`prioritizeTimeOverSizeThresholds` is `false`, so whichever limit binds first
wins. With device-class caps of 48/96/160 MiB, a 40–80 Mbps remux gets roughly
10–34 seconds of buffer while the configuration claims 50. Nothing surfaces the
discrepancy.

These interact: the premature byte cap has been *shortening the idle window*,
partly hiding problem 1. Raising the caps without fixing the window would make
dropped connections dramatically more common.

Additionally, the three-mode `PlaybackBufferMode` enum is dead code —
`SiloPlayerFactory` hardcodes `Balanced`, so `QuickStart`, `SmoothPlayback` and
the `fromWire` parsing are unreachable.

## Decisions

Taken in conversation with Jim:

- **Automatic, from measured conditions.** No user setting, no server-driven
  wire value. The player derives the policy from what it can observe.
- **Start fast, then deepen.** Begin on a small cushion and fill in the
  background; users judge a player on time-to-first-frame.
- **Depth and idle window are independent.** Extend the buffer as far as memory
  and throughput allow, while holding the idle window fixed.

## Architecture

### The invariant

`maxBufferMs` stops being a free parameter:

```
maxBufferMs = minBufferMs + MAX_LOAD_IDLE_MS
```

`MAX_LOAD_IDLE_MS = 30_000`, chosen to sit well under the assumed 60s upstream
proxy `send_timeout`. The assumed timeout is a named constant with its
reasoning beside it, so a deployment behind a 30s proxy has an obvious dial
rather than a mystery.

This makes the failure structurally unrepresentable: no matter how deep the
buffer grows, the socket cannot idle long enough to be dropped. Depth is
`minBufferMs`; the window is the gap.

### Depth is governed by memory and throughput

Depth grows toward a ceiling, bounded by:

- **Memory budget** — bytes needed = target seconds × observed bitrate,
  clamped to a fraction of the app heap. When the budget cannot fund the target
  seconds, the *target seconds are reduced explicitly* to what fits, never
  below a **20s floor**. `maxBufferMs` follows `min` down, so the idle window
  only ever shrinks.
- **Delivery throughput** — the bandwidth meter already reports delivery rate.
  Delivery ≫ media bitrate means the source can outrun playback (direct file,
  or a fast/GPU transcode) and depth may extend. Delivery ≈ bitrate means the
  producer is realtime-bound and the buffer cannot grow regardless of target.
- **Ceiling: 180s.** Beyond this we are mostly pre-fetching content the user may
  seek away from — wasted bandwidth, and wasted allowance on mobile data.

### Transcode needs no special case

Investigated and deliberately dropped. Two findings:

1. A deep target does **not** make the client wait on the encoder — ExoPlayer
   simply receives more slowly. If the encoder is realtime-bound the buffer
   never reaches `max`, so the socket never idles and problem 1 cannot occur on
   transcoded streams at all. A deep target on a slow encoder is inert.
2. The **server already bounds it**. `TranscodeThrottler`
   (`internal/playback/throttle.go` in silo-server) pauses ffmpeg once it is
   `transcode_throttle_seconds` ahead of the client's fetch position — default
   **300s**, clamped to a 60s minimum, gated by `enable_transcode_throttle`.
   That is the real ceiling for transcoded content, and it is the server's to
   enforce.

So throughput-driven depth handles transcode without the client knowing what it
is talking to: a GPU-transcoding server behaves like direct play, a CPU-bound
one degrades gracefully, and nothing breaks when stream nodes are enabled later.

Note that HLS delivery (remux or transcode) fetches discrete segments, so each
request is short-lived and the idle-window problem does not arise there. The
invariant is harmless in that case and load-bearing for `ORIGINAL_HTTP`
progressive direct play — which is exactly where the reported drops occur.

### Numbers

| | Start | After stall | Depth (min) | Idle window |
|---|---|---|---|---|
| All delivery | 2s | 5s | 20s floor → 180s ceiling, memory/throughput governed | 30s |

Start drops 3s → 2s. Stall recovery drops 10s → 5s: after a stall the user is
watching a spinner, and ten seconds is a long time to withhold the picture for
insurance.

## Components

Three units, each independently testable:

- **`PlaybackBufferPolicy`** — the value type, plus a pure
  `forConditions(deviceProfile, ...)` replacing `forMode(...)`. Where the
  numbers live. `PlaybackBufferMode` and its `fromWire` parsing are deleted.
- **`SiloLoadControl`** — keeps bitrate-aware byte sizing; gains the
  seconds-fit-to-budget reduction and enforces the idle-window invariant when
  constructing its `DefaultLoadControl` parameters.
- **`SiloPlayerFactory`** — stops naming a mode; passes observed conditions.

## Testing

Pure functions, following the existing `PlaybackBufferPolicyTest` /
`SiloLoadControlTest` pattern. Per this repo's guidelines, focused tests on
high-risk behaviour only:

- The idle-window invariant holds for every reachable policy — including after
  the memory budget has forced depth down.
- Seconds-fit-to-budget reduction, and the 20s floor holding on a low-RAM device
  with a 60 Mbps stream.
- The 180s ceiling holding when memory would allow more.
- A regression pinning `max − min ≤ 30s`, since that is the property that
  prevents the dropped connections.

## Out of scope

- Any user-facing or server-driven buffer setting.
- LAN-vs-remote branching: no such signal exists in the player today.
- HLS-vs-progressive policy split: the throughput signal covers what matters.
- Changing `proxy_send_timeout` on openresty. That would help only servers Jim
  controls; the client-side invariant holds against any proxy, including
  users' own reverse proxies and CDNs.
