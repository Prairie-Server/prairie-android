# Android Media-Aware Buffer Sizing Design

## Goal

Prevent fast network delivery from inflating Android phone and TV byte-buffer targets while preserving existing playback time thresholds, device byte floors and caps, and HTTP Range resume/retry behavior.

The change is limited to the shared `android-shared` `SiloLoadControl`. It does not alter Silo Server, Apple clients, production proxy configuration, or the progressive data-source retry path introduced for issue #80.

## Bitrate Selection

For each selected audio or video track:

1. Use a positive `Format.averageBitrate`.
2. If average bitrate is absent or invalid, use a positive `Format.peakBitrate`.
3. Do not treat `Format.bitrate` as an independent input because Media3 defines it as peak bitrate when available, otherwise average bitrate.

Sum the selected tracks' known media bitrates. If any selected track supplies valid media metadata, that media sum is the sizing estimate and all `ExoTrackSelection.latestBitrateEstimate` values are ignored. This prevents delivery capacity on a fast LAN from being mistaken for encoded media consumption.

Only when no selected track has valid average or peak metadata may the largest positive `latestBitrateEstimate` be used as a last-resort estimate. The maximum is used rather than a sum because adaptive selections commonly share one bandwidth estimate. If neither metadata nor a positive network estimate exists, retain `DefaultLoadControl`'s target-buffer calculation.

## Buffer Calculation

The selected estimate continues through the existing calculation:

- enough bytes for `minBufferMs`;
- the existing 15 percent container/protocol overhead;
- the existing 16 MiB minimum target;
- the existing device-specific maximum target.

Startup, rebuffer, and back-buffer time thresholds are unchanged.

## Adaptation and Reset

Media3's `LoadControl` boundary exposes buffered duration and allocator bytes, but not a reliable encoded-byte consumption rate. Allocator growth is retained buffer, not consumption, and `latestBitrateEstimate` represents delivery capacity. Using either as an observed-consumption proxy would recreate the bug under a different name.

This implementation therefore remains deliberately stateless. Track or session changes invoke target calculation with the new selections, naturally discarding the previous estimate. Upward adaptation and decay are deferred until a reliable encoded-consumption signal is available at this boundary.

## Verification

Focused tests cover:

- average bitrate taking precedence over peak bitrate;
- peak bitrate when average is absent;
- invalid or absent metadata;
- multi-track media-rate summation;
- network estimate used only when all media metadata is absent;
- network capacity not inflating a metadata-derived target;
- unchanged byte floors, caps, and unknown-bitrate fallback.

The existing progressive Range-resume integration test protects issue #80 behavior. Full shared tests and phone/TV debug and release compilation verify the common load-control wiring. A local short-timeout canary may be used to exercise retry behavior without changing production proxy settings; inability to produce genuine socket backpressure with a small local fixture will be recorded as a validation limitation rather than replaced with a misleading proxy.

## Alternatives Rejected

- Metadata-only sizing with no network fallback conflicts with the approved last-resort behavior for metadata-poor media.
- Using allocator growth or bandwidth estimates as observed consumption is not a valid encoded-consumption measurement.
- Adding transport instrumentation or duplicating private `DefaultLoadControl` loading logic would be disproportionate and risks changing issue #80 behavior.
