# Fire TV Sideload Launcher Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the malformed 16:9 legacy launcher icons with crisp square Silo icons while preserving the separate 16:9 TV banner and modern adaptive icon.

**Architecture:** Keep the existing manifest contract and resource names so no runtime code or package identity changes. Generate density-specific opaque square PNG fallbacks from the canonical blue gradient and colorful Silo mark, retain the API 26+ adaptive icon, and enforce the icon/banner distinction with JVM asset and manifest tests.

**Tech Stack:** Android resource qualifiers, PNG assets, Kotlin/JVM tests with `ImageIO`, Gradle, ImageMagick, ADB.

## Global Constraints

- Android 7 remains supported with `minSdk = 24`.
- `android:icon` remains `@mipmap/ic_launcher`.
- `android:banner` remains `@drawable/tv_banner` on both the application and `MainTvActivity`.
- Legacy launcher icons are opaque square PNG files.
- The TV banner remains an opaque 320x180 wordmark image with its complete logo inside the centered 180x180 square-safe crop.
- The API 26+ adaptive icon and transparent foreground remain unchanged.
- Fire OS's gray framing for sideloaded apps is platform-owned and is not removed by this change.
- Mobile launcher assets are out of scope.

---

### Task 1: Correct and lock down TV launcher assets

**Files:**
- Modify: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/theme/TvLauncherIconAssetsTest.kt`
- Modify: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/TvAndroidManifestPolicyTest.kt`
- Modify: `androidTvApp/src/androidMain/res/mipmap-mdpi/ic_launcher.png`
- Modify: `androidTvApp/src/androidMain/res/mipmap-hdpi/ic_launcher.png`
- Modify: `androidTvApp/src/androidMain/res/mipmap-xhdpi/ic_launcher.png`
- Modify: `androidTvApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher.png`
- Modify: `androidTvApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png`
- Modify: `androidTvApp/src/androidMain/res/drawable/tv_banner.png`

**Interfaces:**
- Consumes: Existing `tv_banner.png`, `ic_launcher_foreground.png`, and manifest resource names.
- Produces: Opaque square pre-adaptive launcher icons at 80, 120, 160, 240, and 320 pixels plus a square-safe 16:9 banner; regression tests for icon dimensions, brand visibility, banner dimensions, banner crop safety, and manifest separation.

- [ ] **Step 1: Replace the incorrect legacy icon tests with failing square-icon and banner tests**

Keep `adaptiveForegroundUsesMarkOnlySafeZoneInsteadOfBakedSquareTile()`. Replace the two legacy tests with:

```kotlin
@Test
fun legacyTvLauncherIconsAreOpaqueSquaresAtRequiredDensities() {
    val expected = mapOf(
        "mipmap-mdpi/ic_launcher.png" to 80,
        "mipmap-hdpi/ic_launcher.png" to 120,
        "mipmap-xhdpi/ic_launcher.png" to 160,
        "mipmap-xxhdpi/ic_launcher.png" to 240,
        "mipmap-xxxhdpi/ic_launcher.png" to 320,
    )

    expected.forEach { (path, size) ->
        val image = ImageIO.read(File("src/androidMain/res/$path"))
        assertEquals(size, image.width, path)
        assertEquals(size, image.height, path)
        assertTrue(!image.colorModel.hasAlpha(), "$path must be opaque for legacy TV launchers.")
        assertEquals(255, image.alphaAt(0, 0), "$path top-left corner must be opaque.")
        assertEquals(255, image.alphaAt(size - 1, size - 1), "$path bottom-right corner must be opaque.")
    }
}

@Test
fun legacyTvLauncherIconContainsCenteredColorSiloMark() {
    val icon = ImageIO.read(File("src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png"))
    val accentPixels = buildList {
        for (y in 0 until icon.height) {
            for (x in 0 until icon.width) {
                val rgb = icon.getRGB(x, y)
                if (rgb.red() > 180 && rgb.red() > rgb.blue() * 1.15 && rgb.green() < 180) {
                    add(x to y)
                }
            }
        }
    }

    assertTrue(accentPixels.size > icon.width * icon.height / 100)
    val averageX = accentPixels.sumOf { it.first }.toDouble() / accentPixels.size
    assertTrue(averageX in icon.width * 0.42..icon.width * 0.58, "Brand accent must remain centered: $averageX")
}

@Test
fun tvBannerRemainsOpaqueWordmarkAtRequiredDimensions() {
    val banner = ImageIO.read(File("src/androidMain/res/drawable/tv_banner.png"))
    assertEquals(320, banner.width)
    assertEquals(180, banner.height)
    assertTrue(!banner.colorModel.hasAlpha())

    var nearWhitePixels = 0
    for (y in 0 until banner.height) {
        for (x in 0 until banner.width) {
            val rgb = banner.getRGB(x, y)
            if (rgb.red() > 230 && rgb.green() > 230 && rgb.blue() > 230) nearWhitePixels++
        }
    }
    assertTrue(nearWhitePixels > 500, "TV banner must retain the white Silo wordmark.")
}
```

Add these helpers beside `alphaAt`:

```kotlin
private fun Int.red(): Int = (this ushr 16) and 0xff
private fun Int.green(): Int = (this ushr 8) and 0xff
private fun Int.blue(): Int = this and 0xff
```

- [ ] **Step 2: Add a failing manifest-separation test**

Add this test to `TvAndroidManifestPolicyTest`:

```kotlin
@Test
fun tvManifestKeepsSquareIconAndBannerAsDistinctResources() {
    assertTrue(manifest.contains("""android:icon="@mipmap/ic_launcher""""))
    assertTrue(manifest.contains("""android:banner="@drawable/tv_banner""""))
    assertFalse(manifest.contains("""android:icon="@drawable/tv_banner""""))
}
```

- [ ] **Step 3: Run the focused tests and confirm the legacy dimensions fail**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests org.siloserver.silo.tv.ui.theme.TvLauncherIconAssetsTest \
  --tests org.siloserver.silo.tv.TvAndroidManifestPolicyTest
```

Expected: `legacyTvLauncherIconsAreOpaqueSquaresAtRequiredDensities` fails because the existing files are 16:9 and larger than the required square dimensions. The manifest and unchanged adaptive/banner tests pass.

- [ ] **Step 4: Generate the square legacy icons from canonical existing artwork**

Preserve the current highest-resolution blank gradient and colorful adaptive foreground before replacing targets:

```bash
cp androidTvApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png /tmp/silo-tv-icon-gradient.png
cp androidTvApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher_foreground.png /tmp/silo-tv-icon-mark.png

for density_and_size in mdpi:80 hdpi:120 xhdpi:160 xxhdpi:240 xxxhdpi:320; do
  density=${density_and_size%%:*}
  size=${density_and_size##*:}
  mark_height=$((size * 68 / 100))

  magick /tmp/silo-tv-icon-gradient.png \
    -gravity center -crop 360x360+0+0 +repage \
    -resize "${size}x${size}!" \
    /tmp/silo-tv-icon-background.png

  magick /tmp/silo-tv-icon-mark.png \
    -trim +repage -resize "x${mark_height}" \
    /tmp/silo-tv-icon-foreground.png

  magick /tmp/silo-tv-icon-background.png \
    /tmp/silo-tv-icon-foreground.png \
    -gravity center -composite \
    "PNG24:androidTvApp/src/androidMain/res/mipmap-${density}/ic_launcher.png"
done
```

Expected: five opaque square files with a centered colorful Silo mark on the existing blue gradient. Do not modify `mipmap-anydpi-v26/ic_launcher.xml` or any `ic_launcher_foreground.png`.

Generate the banner separately so Fire OS's centered square crop contains the complete canonical logo:

```bash
magick -size 320x180 canvas:'#1718c9' /tmp/silo-tv-banner-background.png
magick androidTvApp/src/androidMain/res/drawable/silo_wordmark.png \
  -trim +repage -resize '180x' /tmp/silo-tv-banner-wordmark.png
magick /tmp/silo-tv-banner-background.png /tmp/silo-tv-banner-wordmark.png \
  -gravity center -composite \
  'PNG24:androidTvApp/src/androidMain/res/drawable/tv_banner.png'
```

Expected: an opaque 320x180 banner with the full Silo logo centered inside the 180x180 crop-safe area.

- [ ] **Step 5: Run focused tests and inspect the generated master icon**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests org.siloserver.silo.tv.ui.theme.TvLauncherIconAssetsTest \
  --tests org.siloserver.silo.tv.TvAndroidManifestPolicyTest
```

Expected: `BUILD SUCCESSFUL`. Visually inspect `mipmap-xxxhdpi/ic_launcher.png` and confirm the mark is sharp, centered, uncropped, and balanced.

- [ ] **Step 6: Run the complete TV unit suite and build the debug APK**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; the APK is produced under `androidTvApp/build/outputs/apk/debug/`.

- [ ] **Step 7: Commit the tested asset correction**

```bash
git add \
  androidTvApp/src/androidMain/res/mipmap-mdpi/ic_launcher.png \
  androidTvApp/src/androidMain/res/mipmap-hdpi/ic_launcher.png \
  androidTvApp/src/androidMain/res/mipmap-xhdpi/ic_launcher.png \
  androidTvApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher.png \
  androidTvApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png \
  androidTvApp/src/androidMain/res/drawable/tv_banner.png \
  androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/theme/TvLauncherIconAssetsTest.kt \
  androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/TvAndroidManifestPolicyTest.kt
git commit -m "fix(tv): correct sideloaded launcher icon aspect ratio"
```

### Task 2: Verify Fire TV and Google TV launcher behavior

**Files:**
- Verify: `androidTvApp/build/outputs/apk/debug/androidTvApp-universal-debug.apk`
- Verify: Fire TV `192.168.1.179:5555`
- Verify: attached Google TV Streamer `61071HFAG1FWQX`

**Interfaces:**
- Consumes: Debug APK and launcher assets produced by Task 1.
- Produces: Device evidence that API 25 uses the square fallback and modern Google TV keeps the adaptive icon.

- [ ] **Step 1: Locate the universal debug APK and record its packaged assets**

```bash
apk=$(find androidTvApp/build/outputs/apk/debug -name '*universal*debug*.apk' -print -quit)
test -n "$apk"
"$HOME/Library/Android/sdk/build-tools/36.0.0/aapt" dump badging "$apk" |
  grep -E "application:|leanback-launchable-activity"
```

Expected: the application reports distinct icon and banner resources and exposes `MainTvActivity` as leanback-launchable. The packaged banner remains 320x180.

- [ ] **Step 2: Clean-install on the Fire Stick**

```bash
adb -s 192.168.1.179:5555 shell am force-stop org.siloserver.silo
adb -s 192.168.1.179:5555 uninstall org.siloserver.silo || true
adb -s 192.168.1.179:5555 install "$apk"
adb -s 192.168.1.179:5555 shell am start -n com.amazon.venezia/.grid.AppsGridLauncherActivity
```

Expected: installation succeeds and the Fire TV app grid opens.

- [ ] **Step 3: Capture and inspect the Fire TV launcher**

```bash
adb -s 192.168.1.179:5555 exec-out screencap -p > /tmp/fire-tv-silo-square-launcher.png
```

Expected: Fire OS retains its outer gray sideload frame, but the inner Silo icon is square, the colorful mark is not compressed, and the mark is materially larger and sharper than before.

- [ ] **Step 4: Clean-install on the Google TV Streamer**

```bash
adb -s 61071HFAG1FWQX shell am force-stop org.siloserver.silo
adb -s 61071HFAG1FWQX uninstall org.siloserver.silo || true
adb -s 61071HFAG1FWQX install "$apk"
adb -s 61071HFAG1FWQX shell monkey -p com.google.android.apps.tv.launcher 1
```

Expected: installation succeeds and the Google TV launcher opens.

- [ ] **Step 5: Capture and inspect the Google TV launcher**

```bash
adb -s 61071HFAG1FWQX exec-out screencap -p > /tmp/google-tv-silo-adaptive-launcher.png
```

Expected: Google TV uses the existing adaptive icon with its normal launcher mask; there is no baked square border, stretched banner, or cropped mark.

- [ ] **Step 6: Re-run final verification and confirm a clean branch**

```bash
./gradlew :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug
git diff --check
git status --short --branch
```

Expected: Gradle reports `BUILD SUCCESSFUL`, `git diff --check` emits nothing, and the branch contains only the committed design, plan, tests, and launcher assets.
