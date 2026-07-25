# Amazon Fire TV listing assets

Fire TV sideloads share the same `androidTvApp` package as Android TV / Google TV. Fire OS applies its own gray framing to sideloaded tiles; Amazon’s full-bleed launcher treatment is only available for Appstore-distributed apps via a separate listing asset.

| Asset | Size | Path |
| --- | --- | --- |
| Appstore listing icon (16:9) | 1280×720 | [`firetv-listing-1280x720.png`](./firetv-listing-1280x720.png) |

In-APK launcher surfaces (unchanged contract):

- `android:icon` → square `@mipmap/ic_launcher` (density PNGs + adaptive foreground)
- `android:banner` → 16:9 `@drawable/tv_banner` with logo + wordmark inside the centered 180×180 square crop

See [`docs/superpowers/specs/2026-07-16-fire-tv-sideload-launcher-icon-design.md`](../../superpowers/specs/2026-07-16-fire-tv-sideload-launcher-icon-design.md).
