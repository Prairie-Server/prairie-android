# MPV Video Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real MPV/libmpv video backend behind Silo's shared playback boundary while keeping Media3 as the fallback engine.

**Architecture:** MPV must be an actual Media3 `Player` owned by `ContinuumPlaybackService`; otherwise the screens would still drive the existing Media3 player through `MediaController`. Add a Media3-compatible MPV player adapter in `android-shared`, teach the service to create the selected engine, and update `VideoPlaybackBackendFactory` so diagnostics and future selection policy report the engine truthfully.

**Tech Stack:** Kotlin 2.1, Android minSdk 24, Media3 1.10.0, `dev.jdtech.mpv:libmpv:1.0.0`, Koin, Compose phone/TV player screens, existing Silo playback service.

---

## Scope Check

This plan implements the MPV backend path for video playback. It does not rewrite the player controls, add a user-facing backend picker, rebuild subtitle search, or remove Media3. Media3 remains available because the shared service is also used by audiobook playback and because MPV rollout needs a fallback.

## File Structure

Create:

- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/mpv/MpvTrackType.kt`  
  Maps Media3 track types to MPV `vid`/`aid`/`sid` properties.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/mpv/MpvPlayer.kt`  
  Media3 `BasePlayer` adapter backed by `dev.jdtech.mpv.MPVLib`.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/MpvVideoPlaybackBackend.kt`  
  `VideoPlaybackBackend` implementation for MPV-backed players.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendSelector.kt`  
  Pure selection policy that chooses Media3 or MPV from preference, form factor, play method, and future hard-case hints.
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/mpv/MpvPlayerSourceTest.kt`  
  Source guard for the MPV adapter's essential libmpv commands, surface handling, subtitle handling, and cache options.
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendSelectorTest.kt`  
  Unit tests for backend selection policy.
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/MpvVideoPlaybackBackendSourceTest.kt`  
  Source guard proving the MPV backend reports native subtitle rendering and delegates track selection to the MPV player.

Modify:

- `gradle/libs.versions.toml`  
  Add `libmpv = "1.0.0"` and `libmpv = { group = "dev.jdtech.mpv", name = "libmpv", version.ref = "libmpv" }`.
- `android-shared/build.gradle.kts`  
  Add `implementation(libs.libmpv)` to `androidMain`.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendKind.kt`  
  Add `Mpv`.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendPreference.kt`  
  Add `Mpv`.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoBackendCapabilities.kt`  
  Add `supportsHardContainers`, `displayName`, and an `mpv()` factory with native subtitle rendering.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendRequest.kt`  
  Add hard-case hints used by the pure selector without coupling it to UI state.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactory.kt`  
  Use `VideoPlaybackBackendSelector`; return `MpvVideoPlaybackBackend` when the bound player is MPV, otherwise Media3 fallback.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt`  
  Add `createMpvPlayer()` and change shared player creation helpers to return Media3 `Player` where appropriate.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt`  
  Own the selected `Player`, attach analytics only for ExoPlayer, and log MPV vs Media3 truthfully.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt`  
  Include capability booleans and display labels in stats.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt`  
  Show MPV-native subtitle/hard-container capability labels.
- Existing source-guard tests under `androidApp/src/androidUnitTest`, `androidTvApp/src/androidUnitTest`, and `android-shared/src/androidUnitTest`  
  Update tests that currently assert MPV is absent.

---

### Task 1: Dependency And Capability Model

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `android-shared/build.gradle.kts`
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendKind.kt`
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendPreference.kt`
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoBackendCapabilities.kt`
- Modify: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoBackendCapabilitiesTest.kt`

- [ ] **Step 1: Write failing capability/dependency tests**

Update `VideoBackendCapabilitiesTest` with:

```kotlin
@Test
fun mpvCapabilitiesDescribeNativePlaybackBehavior() {
    val capabilities = VideoBackendCapabilities.mpv()

    assertEquals(VideoPlaybackBackendKind.Mpv, capabilities.backendKind)
    assertEquals(PlaybackRoute.Compatibility, capabilities.route)
    assertTrue(capabilities.supportsSidecarSubtitles)
    assertTrue(capabilities.supportsEmbeddedSubtitleSelection)
    assertTrue(capabilities.supportsAudioTrackSelection)
    assertTrue(capabilities.supportsBufferReporting)
    assertTrue(capabilities.supportsHardContainers)
    assertEquals(SubtitleRendering.NativeBackend, capabilities.subtitleRendering)
    assertEquals("MPV", capabilities.displayName)
}
```

Add a dependency assertion test to the same file:

```kotlin
@Test
fun mpvDependencyIsDeclaredInSharedAndroidMain() {
    val catalog = java.io.File("../../gradle/libs.versions.toml").readText()
    val build = java.io.File("build.gradle.kts").readText()

    assertTrue(catalog.contains("libmpv = \"1.0.0\""))
    assertTrue(catalog.contains("dev.jdtech.mpv"))
    assertTrue(build.contains("implementation(libs.libmpv)"))
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.VideoBackendCapabilitiesTest'
```

Expected: FAIL because MPV enum values, capability fields, and dependency declarations are missing.

- [ ] **Step 3: Add dependency declarations**

In `gradle/libs.versions.toml`, add:

```toml
libmpv = "1.0.0"
```

and:

```toml
libmpv = { group = "dev.jdtech.mpv", name = "libmpv", version.ref = "libmpv" }
```

In `android-shared/build.gradle.kts`, under `androidMain.dependencies`, add:

```kotlin
implementation(libs.libmpv)
```

- [ ] **Step 4: Add MPV capability model**

Change `VideoPlaybackBackendKind` to:

```kotlin
enum class VideoPlaybackBackendKind {
    Media3,
    Mpv,
}
```

Change `VideoPlaybackBackendPreference` to:

```kotlin
enum class VideoPlaybackBackendPreference {
    Auto,
    Media3,
    Mpv,
}
```

Update `VideoBackendCapabilities` to include:

```kotlin
val supportsHardContainers: Boolean,
val displayName: String,
```

Set Media3 values:

```kotlin
supportsHardContainers = false,
displayName = "Media3",
```

Add:

```kotlin
fun mpv(
    route: PlaybackRoute = PlaybackRoute.Compatibility,
): VideoBackendCapabilities = VideoBackendCapabilities(
    backendKind = VideoPlaybackBackendKind.Mpv,
    route = route,
    supportsSidecarSubtitles = true,
    supportsEmbeddedSubtitleSelection = true,
    supportsAudioTrackSelection = true,
    supportsBufferReporting = true,
    supportsSubtitleDelay = true,
    supportsAudioDelay = false,
    subtitleRendering = SubtitleRendering.NativeBackend,
    supportsHardContainers = true,
    displayName = "MPV",
)
```

- [ ] **Step 5: Run test and compile**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.VideoBackendCapabilitiesTest'
./gradlew :android-shared:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml android-shared/build.gradle.kts android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoBackendCapabilitiesTest.kt
git commit -m "Add MPV backend capability model"
```

### Task 2: Pure Backend Selection Policy

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendSelector.kt`
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendRequest.kt`
- Create: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendSelectorTest.kt`

- [ ] **Step 1: Write failing selector tests**

Create `VideoPlaybackBackendSelectorTest.kt`:

```kotlin
package com.continuum.app.common.player.backend

import com.continuum.app.model.playback.PlayMethod
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoPlaybackBackendSelectorTest {
    @Test
    fun explicitMedia3PreferenceWins() {
        val request = VideoPlaybackBackendRequest(
            preference = VideoPlaybackBackendPreference.Media3,
            hasHardContainer = true,
            hasStyledSubtitles = true,
        )

        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun explicitMpvPreferenceWins() {
        val request = VideoPlaybackBackendRequest(preference = VideoPlaybackBackendPreference.Mpv)

        assertEquals(VideoPlaybackBackendKind.Mpv, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoUsesMedia3ForTranscode() {
        val request = VideoPlaybackBackendRequest(playMethod = PlayMethod.TRANSCODE)

        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoUsesMpvForHardContainersOrStyledSubtitles() {
        assertEquals(
            VideoPlaybackBackendKind.Mpv,
            VideoPlaybackBackendSelector.select(VideoPlaybackBackendRequest(hasHardContainer = true)),
        )
        assertEquals(
            VideoPlaybackBackendKind.Mpv,
            VideoPlaybackBackendSelector.select(VideoPlaybackBackendRequest(hasStyledSubtitles = true)),
        )
    }
}
```

- [ ] **Step 2: Run the failing selector tests**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.VideoPlaybackBackendSelectorTest'
```

Expected: FAIL because selector and request hints do not exist.

- [ ] **Step 3: Add request hints**

Update `VideoPlaybackBackendRequest`:

```kotlin
val hasHardContainer: Boolean = false,
val hasStyledSubtitles: Boolean = false,
```

- [ ] **Step 4: Implement selector**

Create `VideoPlaybackBackendSelector.kt`:

```kotlin
package com.continuum.app.common.player.backend

import com.continuum.app.model.playback.PlayMethod

object VideoPlaybackBackendSelector {
    fun select(request: VideoPlaybackBackendRequest): VideoPlaybackBackendKind =
        when (request.preference) {
            VideoPlaybackBackendPreference.Media3 -> VideoPlaybackBackendKind.Media3
            VideoPlaybackBackendPreference.Mpv -> VideoPlaybackBackendKind.Mpv
            VideoPlaybackBackendPreference.Auto -> when {
                request.playMethod == PlayMethod.TRANSCODE -> VideoPlaybackBackendKind.Media3
                request.hasHardContainer -> VideoPlaybackBackendKind.Mpv
                request.hasStyledSubtitles -> VideoPlaybackBackendKind.Mpv
                else -> VideoPlaybackBackendKind.Media3
            }
        }
}
```

- [ ] **Step 5: Run selector tests**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.VideoPlaybackBackendSelectorTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendRequest.kt android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendSelector.kt android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendSelectorTest.kt
git commit -m "Add video backend selection policy"
```

### Task 3: MPV Player Adapter

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/mpv/MpvTrackType.kt`
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/mpv/MpvPlayer.kt`
- Create: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/mpv/MpvPlayerSourceTest.kt`

- [ ] **Step 1: Write failing source guard**

Create `MpvPlayerSourceTest.kt`:

```kotlin
package com.continuum.app.common.player.mpv

import kotlin.test.Test
import kotlin.test.assertTrue

class MpvPlayerSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/mpv/MpvPlayer.kt",
    )

    @Test
    fun mpvPlayerWrapsLibmpvAsMedia3Player() {
        val text = source.readText()

        assertTrue(text.contains("class MpvPlayer"))
        assertTrue(text.contains("BasePlayer()"))
        assertTrue(text.contains("MPVLib.create(context)"))
        assertTrue(text.contains("setOptionString(\"gpu-context\", \"android\")"))
        assertTrue(text.contains("setOptionString(\"opengl-es\", \"yes\")"))
        assertTrue(text.contains("setOptionString(\"cache\", \"yes\")"))
        assertTrue(text.contains("setOptionString(\"cache-pause-initial\", \"yes\")"))
        assertTrue(text.contains("setOptionString(\"sub-scale-with-window\", \"yes\")"))
        assertTrue(text.contains("command(arrayOf(\"sub-add\""))
        assertTrue(text.contains("attachSurface(holder.surface)"))
        assertTrue(text.contains("detachSurface()"))
        assertTrue(text.contains("override fun setTrackSelectionParameters"))
        assertTrue(text.contains("override fun getBufferedPosition()"))
        assertTrue(text.contains("override fun release()"))
    }
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.mpv.MpvPlayerSourceTest'
```

Expected: FAIL because the adapter does not exist.

- [ ] **Step 3: Add `MpvTrackType`**

Create a small enum that maps Media3 `C.TRACK_TYPE_VIDEO`, `C.TRACK_TYPE_AUDIO`, and `C.TRACK_TYPE_TEXT` to MPV `vid`, `aid`, and `sid` properties.

- [ ] **Step 4: Add `MpvPlayer`**

Port the Media3 `BasePlayer` adapter shape from `/Users/jimcole/source/AFinity/app/src/main/java/com/makd/afinity/player/mpv/MPVPlayer.kt`, preserving:

- `MPVLib.create(context)` initialization.
- Android GPU context and OpenGL ES options.
- Hardware decode options.
- Initial and rebuffer cache options.
- `sub-add` for sidecar subtitles from `MediaItem.SubtitleConfiguration`.
- `track-list`, `time-pos`, `duration`, and `demuxer-cache-time` observation.
- `setVideoSurfaceView`/`clearVideoSurfaceView` surface attachment.
- Media3 `setMediaItems`, `prepare`, `playWhenReady`, `seekTo`, `currentPosition`, `duration`, `bufferedPosition`, `trackSelectionParameters`, and `release`.

Keep the class in Silo's package and remove Timber/Hilt/project-specific dependencies.

- [ ] **Step 5: Run source guard and compile**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.mpv.MpvPlayerSourceTest'
./gradlew :android-shared:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/mpv android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/mpv/MpvPlayerSourceTest.kt
git commit -m "Add MPV Media3 player adapter"
```

### Task 4: Service-Owned MPV Player Creation

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt`
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt`
- Create: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/ContinuumPlaybackServiceMpvSourceTest.kt`

- [ ] **Step 1: Write failing service source guard**

Create `ContinuumPlaybackServiceMpvSourceTest.kt`:

```kotlin
package com.continuum.app.common.player

import kotlin.test.Test
import kotlin.test.assertTrue

class ContinuumPlaybackServiceMpvSourceTest {
    private val factorySource = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt",
    ).readText()
    private val serviceSource = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt",
    ).readText()

    @Test
    fun serviceCanOwnMpvOrMedia3Player() {
        assertTrue(factorySource.contains("fun createMpvPlayer("))
        assertTrue(factorySource.contains("MpvPlayer.Builder(context)"))
        assertTrue(serviceSource.contains("val player = playerFactory.createMpvPlayer()"))
        assertTrue(serviceSource.contains("if (player is ExoPlayer)"))
        assertTrue(serviceSource.contains("MediaSession.Builder(this, player).build()"))
    }
}
```

- [ ] **Step 2: Run failing test**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.ContinuumPlaybackServiceMpvSourceTest'
```

Expected: FAIL because `createMpvPlayer()` does not exist and the service always creates ExoPlayer.

- [ ] **Step 3: Add `createMpvPlayer()`**

In `ContinuumPlayerFactory`, import `MpvPlayer` and add:

```kotlin
fun createMpvPlayer(): Player =
    MpvPlayer.Builder(context)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(30_000)
        .build()
```

- [ ] **Step 4: Let the service own MPV**

For the first MPV slice, create MPV in `ContinuumPlaybackService` and keep analytics attachment ExoPlayer-only:

```kotlin
val player = playerFactory.createMpvPlayer()
if (player is ExoPlayer) {
    player.addAnalyticsListener(analyticsListener)
}
```

Update logs from "ExoPlayer" to "Playback player" and include `player::class.java.simpleName`.

- [ ] **Step 5: Run service test and compile apps**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.ContinuumPlaybackServiceMpvSourceTest'
./gradlew :androidApp:compileDebugKotlinAndroid :androidTvApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/ContinuumPlaybackServiceMpvSourceTest.kt
git commit -m "Route playback service through MPV player"
```

### Task 5: MPV Backend Wrapper And Diagnostics

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/MpvVideoPlaybackBackend.kt`
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactory.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt`
- Create: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/MpvVideoPlaybackBackendSourceTest.kt`
- Modify: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerBackendCapabilitiesSourceTest.kt`

- [ ] **Step 1: Write failing backend diagnostics tests**

Create `MpvVideoPlaybackBackendSourceTest.kt`:

```kotlin
package com.continuum.app.common.player.backend

import kotlin.test.Test
import kotlin.test.assertTrue

class MpvVideoPlaybackBackendSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/backend/MpvVideoPlaybackBackend.kt",
    )

    @Test
    fun mpvBackendReportsNativeCapabilitiesAndUsesSharedMounting() {
        val text = source.readText()

        assertTrue(text.contains("class MpvVideoPlaybackBackend"))
        assertTrue(text.contains("override val kind: VideoPlaybackBackendKind = VideoPlaybackBackendKind.Mpv"))
        assertTrue(text.contains("override val capabilities: VideoBackendCapabilities = VideoBackendCapabilities.mpv()"))
        assertTrue(text.contains("mountVideoMedia("))
        assertTrue(text.contains("refreshMountedVideoMedia("))
        assertTrue(text.contains("player.trackSelectionParameters"))
    }
}
```

Update `TvPlayerBackendCapabilitiesSourceTest` to assert:

```kotlin
assertTrue(viewModelSource.contains("val backendDisplayName: String? = null"))
assertTrue(viewModelSource.contains("val hardContainers: String? = null"))
assertTrue(hudSource.contains("backendDisplayName?.let { add(\"Backend\" to it) }"))
assertTrue(hudSource.contains("hardContainers?.let { add(\"Hard containers\" to it) }"))
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.MpvVideoPlaybackBackendSourceTest'
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.player.TvPlayerBackendCapabilitiesSourceTest'
```

Expected: FAIL because the MPV backend and richer labels are missing.

- [ ] **Step 3: Add MPV backend**

Create `MpvVideoPlaybackBackend.kt`. It should mirror `Media3VideoPlaybackBackend` for mount/refresh, expose MPV capabilities, and use `TrackSelectionParameters` for audio/subtitle selection because `MpvPlayer` maps those parameters to MPV `aid`/`sid`.

- [ ] **Step 4: Update factory**

Use `VideoPlaybackBackendSelector.select(request)`. If the selected kind is MPV and the bound `Player` is an `MpvPlayer`, return `MpvVideoPlaybackBackend`; if the selected kind is MPV but the service is still Media3, return `Media3VideoPlaybackBackend` as a fallback and keep diagnostics truthful by using the actual player type.

- [ ] **Step 5: Update TV diagnostics labels**

Add `backendDisplayName` and `hardContainers` to `PlayerStatsSnapshot`, set them in `onBackendCapabilities`, and render them in the HUD rows.

- [ ] **Step 6: Run tests and app compiles**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.MpvVideoPlaybackBackendSourceTest'
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.player.TvPlayerBackendCapabilitiesSourceTest'
./gradlew :androidApp:compileDebugKotlinAndroid :androidTvApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerBackendCapabilitiesSourceTest.kt
git commit -m "Add MPV video backend wrapper"
```

### Task 6: Final Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused backend tests**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.*' --tests 'com.continuum.app.common.player.mpv.*' --tests 'com.continuum.app.common.player.ContinuumPlaybackServiceMpvSourceTest'
```

Expected: PASS.

- [ ] **Step 2: Run app compiles**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid :androidTvApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 3: Run broad unit tests**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidTvApp:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 4: Check for accidental JitPack/native vendoring**

Run:

```bash
rg -n "jitpack|com.github|jniLibs|\\.so" settings.gradle.kts gradle/libs.versions.toml android-shared androidApp androidTvApp
```

Expected: no JitPack repository and no vendored `.so` files for MPV.

- [ ] **Step 5: Commit verification cleanup if needed**

```bash
git status --short
git diff --check
```

Expected: clean whitespace. Commit any test-only cleanup with:

```bash
git add .
git commit -m "Verify MPV video backend integration"
```
