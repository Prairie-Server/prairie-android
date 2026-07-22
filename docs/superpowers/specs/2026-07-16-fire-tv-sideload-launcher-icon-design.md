# Fire TV Sideload Launcher Icon Design

## Goal

Make the Silo tile on sideloaded Fire TV devices look intentional, sharp, and correctly proportioned without changing the Android TV application identity or degrading Google TV launcher behavior.

## Current Behavior

The TV manifest correctly exposes two launcher surfaces:

- `android:icon="@mipmap/ic_launcher"`
- `android:banner="@drawable/tv_banner"`

However, the legacy `ic_launcher.png` files are 16:9 banner images. Fire OS 6 on the tested AFTMM device normalizes the application icon into a square area inside its sideloaded-app tile. Feeding that square area a 16:9 source compresses the Silo artwork and leaves Fire OS's gray framing visually dominant.

The current `TvLauncherIconAssetsTest` codifies the incorrect behavior by requiring legacy launcher icons to use Fire TV banner dimensions.

## Platform Constraints

Android TV distinguishes two assets:

- The launcher icon is square and is used by launchers, settings, media sessions, and other system surfaces.
- The TV banner is 16:9 and includes the product name.

Fire OS applies its own gray framing to sideloaded applications. The APK cannot force Amazon's full-bleed launcher treatment. That treatment is supplied through the Amazon Appstore using a separate 1280x720 listing asset. This change therefore improves the sideloaded tile inside the area Fire OS controls; it does not claim to remove Fire OS's outer framing.

## Design

### Square launcher icon

Replace the legacy density-specific `mipmap-*/ic_launcher.png` files with opaque square assets at Android TV's required sizes:

| Density | Size |
| --- | --- |
| mdpi | 80x80 |
| hdpi | 120x120 |
| xhdpi | 160x160 |
| xxhdpi | 240x240 |
| xxxhdpi | 320x320 |

The icon uses the existing Silo blue background and white Silo mark. Artwork remains centered inside a conservative safe area so Fire OS normalization does not crop it. The density files are the fallback for API 24-25 devices, including the tested Fire OS 6 device.

### Adaptive launcher icon

Keep the existing API 26+ adaptive icon structure and its transparent foreground safe zone. This preserves the correctly masked square treatment on modern Android TV and Google TV devices.

### TV banner

Keep `tv_banner.png` as an opaque 320x180 wordmark banner, but compose the complete canonical Silo logo inside the centered 180x180 square-safe crop. The manifest continues to reference it from both the application and TV activity. This preserves Android TV's 16:9 banner contract while making Fire OS's sideloaded square crop show a complete, legible logo.

### Manifest wiring

No product flavor or runtime manufacturer detection is introduced. The existing manifest references remain separate and explicit:

- `android:icon` points to the square launcher icon family.
- `android:banner` points to the 16:9 wordmark banner.

## Validation

Update the launcher asset tests to verify:

1. Every legacy launcher icon is square and has the expected density-specific dimensions.
2. Legacy icons are opaque and preserve the centered Silo artwork within the safe area.
3. The adaptive foreground remains transparent at its edges and safe-zone compliant.
4. The TV banner remains opaque, 320x180, and visibly contains non-background wordmark pixels.
5. The manifest keeps distinct `android:icon` and `android:banner` references.

Run the TV unit tests and build a fresh TV APK. Clean-install that APK on the Fire Stick so the launcher cannot reuse the prior package's cached artwork. Capture the Fire TV app-grid screen and confirm that the Silo artwork is square, undistorted, centered, and legible. Also inspect the Google TV Streamer launcher to ensure the adaptive icon remains correctly masked.

## Future Amazon Appstore Work

If Silo is later distributed through the Amazon Appstore, provide Amazon's required 1280x720 opaque listing artwork using the same 16:9 wordmark composition. That store-managed asset is the supported path to a full-bleed Fire TV tile.

## Non-Goals

- Removing Fire OS's gray framing for sideloaded apps.
- Creating a separate Fire TV package or application ID.
- Adding manufacturer checks or Fire-specific runtime code.
- Changing mobile launcher assets.
