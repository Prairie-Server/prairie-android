# Production Playback Backend Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a shared video playback backend boundary for Android mobile and Android TV while keeping Media3/ExoPlayer as the only active backend.

**Architecture:** Add a thin `android-shared` backend package that wraps a bound Media3 `Player`/`MediaController` and delegates mount, refresh, subtitle selection, audio selection, and track preset operations to existing helpers. Mobile and TV screens obtain a backend adapter after their `MediaController` binds; `ContinuumPlaybackService` remains Media3-only in this slice.

**Tech Stack:** Kotlin, Android, Media3 1.10.0, Koin, Compose, existing `ContinuumPlayerFactory`, `VideoPlayerMediaMounter`, `VideoTrackSelectionCoordinator`, and JVM source-guard tests.

---

## File Structure

Create:

- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendKind.kt`
  Backend identity enum. Current value: `Media3`.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendPreference.kt`
  Future-facing user/admin preference enum. Current values: `Auto`, `Media3`.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackFormFactor.kt`
  Request metadata enum: `Unknown`, `Mobile`, `Tv`.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/SubtitleRendering.kt`
  Backend subtitle rendering strategy enum.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoBackendCapabilities.kt`
  Capability model and `media3()` factory.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendRequest.kt`
  Small future-proof backend selection request.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackend.kt`
  Thin backend interface over existing player operations.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/Media3VideoPlaybackBackend.kt`
  Media3 implementation that delegates to existing helpers.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactory.kt`
  Creates/wraps a Media3 backend for the bound player.
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoBackendCapabilitiesTest.kt`
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendInterfaceTest.kt`
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/Media3VideoPlaybackBackendSourceTest.kt`
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactorySourceTest.kt`
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoBackendNativeDependencyGuardTest.kt`

Modify:

- `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`
  Register `VideoPlaybackBackendFactory`.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`
  Register `VideoPlaybackBackendFactory`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt`
  Use backend adapter for mount, refresh, track presets, subtitle selection, audio selection if present.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`
  Use backend adapter for mount, refresh, track presets, subtitle selection, and audio selection.
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreenStartPositionTest.kt`
  Update source guards from direct mounter/coordinator usage to backend usage.
- `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreenStartPositionTest.kt`
  Update source guards from direct mounter/coordinator usage to backend usage.

Do not modify:

- `ContinuumPlaybackService.kt` in this slice. The service still creates the single Media3 `ExoPlayer`. A future MPV branch can move service creation behind a backend factory once the backend seam is proven at the surface edge.
- Gradle dependencies. This branch must not add MPV, libass, JitPack, or native AARs.

---

### Task 1: Backend Capability Data Model

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendKind.kt`
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendPreference.kt`
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackFormFactor.kt`
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/SubtitleRendering.kt`
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoBackendCapabilities.kt`
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendRequest.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoBackendCapabilitiesTest.kt`

- [ ] **Step 1: Write the failing capability test**

Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoBackendCapabilitiesTest.kt`:

```kotlin
package com.continuum.app.common.player.backend

import com.continuum.app.common.player.route.PlaybackRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoBackendCapabilitiesTest {

    @Test
    fun media3CapabilitiesDescribeCurrentPlayerBehavior() {
        val capabilities = VideoBackendCapabilities.media3()

        assertEquals(VideoPlaybackBackendKind.Media3, capabilities.backendKind)
        assertEquals(PlaybackRoute.Compatibility, capabilities.route)
        assertTrue(capabilities.supportsSidecarSubtitles)
        assertTrue(capabilities.supportsEmbeddedSubtitleSelection)
        assertTrue(capabilities.supportsAudioTrackSelection)
        assertTrue(capabilities.supportsBufferReporting)
        assertTrue(capabilities.supportsSubtitleDelay)
        assertTrue(capabilities.supportsAudioDelay)
        assertEquals(SubtitleRendering.Media3Text, capabilities.subtitleRendering)
    }

    @Test
    fun backendRequestDefaultsToAutoMedia3CompatibleSelection() {
        val request = VideoPlaybackBackendRequest()

        assertEquals(null, request.contentId)
        assertEquals(null, request.fileId)
        assertEquals(null, request.playMethod)
        assertEquals(VideoPlaybackFormFactor.Unknown, request.formFactor)
        assertEquals(VideoPlaybackBackendPreference.Auto, request.preference)
    }
}
```

- [ ] **Step 2: Run the capability test and verify it fails**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.VideoBackendCapabilitiesTest'
```

Expected: FAIL with unresolved references for `VideoBackendCapabilities`, `VideoPlaybackBackendKind`, `SubtitleRendering`, `VideoPlaybackBackendRequest`, `VideoPlaybackFormFactor`, or `VideoPlaybackBackendPreference`.

- [ ] **Step 3: Add backend enum and request model files**

Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendKind.kt`:

```kotlin
package com.continuum.app.common.player.backend

enum class VideoPlaybackBackendKind {
    Media3,
}
```

Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendPreference.kt`:

```kotlin
package com.continuum.app.common.player.backend

enum class VideoPlaybackBackendPreference {
    Auto,
    Media3,
}
```

Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackFormFactor.kt`:

```kotlin
package com.continuum.app.common.player.backend

enum class VideoPlaybackFormFactor {
    Unknown,
    Mobile,
    Tv,
}
```

Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/SubtitleRendering.kt`:

```kotlin
package com.continuum.app.common.player.backend

enum class SubtitleRendering {
    Media3Text,
    ExternalView,
    NativeBackend,
}
```

Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendRequest.kt`:

```kotlin
package com.continuum.app.common.player.backend

import com.continuum.app.model.playback.PlayMethod

data class VideoPlaybackBackendRequest(
    val contentId: String? = null,
    val fileId: Int? = null,
    val playMethod: PlayMethod? = null,
    val formFactor: VideoPlaybackFormFactor = VideoPlaybackFormFactor.Unknown,
    val preference: VideoPlaybackBackendPreference = VideoPlaybackBackendPreference.Auto,
)
```

- [ ] **Step 4: Add the capability model**

Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoBackendCapabilities.kt`:

```kotlin
package com.continuum.app.common.player.backend

import com.continuum.app.common.player.route.PlaybackRoute

data class VideoBackendCapabilities(
    val backendKind: VideoPlaybackBackendKind,
    val route: PlaybackRoute,
    val supportsSidecarSubtitles: Boolean,
    val supportsEmbeddedSubtitleSelection: Boolean,
    val supportsAudioTrackSelection: Boolean,
    val supportsBufferReporting: Boolean,
    val supportsSubtitleDelay: Boolean,
    val supportsAudioDelay: Boolean,
    val subtitleRendering: SubtitleRendering,
) {
    companion object {
        fun media3(
            route: PlaybackRoute = PlaybackRoute.Compatibility,
        ): VideoBackendCapabilities = VideoBackendCapabilities(
            backendKind = VideoPlaybackBackendKind.Media3,
            route = route,
            supportsSidecarSubtitles = true,
            supportsEmbeddedSubtitleSelection = true,
            supportsAudioTrackSelection = true,
            supportsBufferReporting = true,
            supportsSubtitleDelay = true,
            supportsAudioDelay = true,
            subtitleRendering = SubtitleRendering.Media3Text,
        )
    }
}
```

- [ ] **Step 5: Run the capability test and verify it passes**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.VideoBackendCapabilitiesTest'
```

Expected: PASS.

- [ ] **Step 6: Commit Task 1**

Run:

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoBackendCapabilitiesTest.kt
git commit -m "Add video backend capability model"
```

---

### Task 2: Backend Interface

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackend.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendInterfaceTest.kt`

- [ ] **Step 1: Write the failing interface source test**

Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendInterfaceTest.kt`:

```kotlin
package com.continuum.app.common.player.backend

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPlaybackBackendInterfaceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackend.kt",
    )

    @Test
    fun backendInterfaceExposesOnlySharedPlaybackOperations() {
        val text = source.readText()

        assertTrue(text.contains("interface VideoPlaybackBackend"))
        assertTrue(text.contains("val kind: VideoPlaybackBackendKind"))
        assertTrue(text.contains("val capabilities: VideoBackendCapabilities"))
        assertTrue(text.contains("val player: Player"))
        assertTrue(text.contains("fun mount("))
        assertTrue(text.contains("fun refresh("))
        assertTrue(text.contains("fun selectSubtitle("))
        assertTrue(text.contains("fun selectMountedSubtitle("))
        assertTrue(text.contains("fun selectAudioTrack("))
        assertTrue(text.contains("fun applyTrackSelection("))
        assertTrue(text.contains("fun release()"))
        assertFalse(text.contains("mpv", ignoreCase = true))
        assertFalse(text.contains("libass", ignoreCase = true))
    }
}
```

- [ ] **Step 2: Run the interface test and verify it fails**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.VideoPlaybackBackendInterfaceTest'
```

Expected: FAIL because `VideoPlaybackBackend.kt` does not exist.

- [ ] **Step 3: Add the backend interface**

Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackend.kt`:

```kotlin
package com.continuum.app.common.player.backend

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.continuum.app.common.player.VideoPlayerMediaSpec
import com.continuum.app.common.player.video.VideoPlayerTrackEntry
import com.continuum.app.model.playback.AudioPassthroughCapabilities
import com.continuum.app.model.playback.HdrCapabilities
import com.continuum.app.model.playback.PlayerSubtitleInfo

@UnstableApi
interface VideoPlaybackBackend {
    val kind: VideoPlaybackBackendKind
    val capabilities: VideoBackendCapabilities
    val player: Player

    fun mount(
        spec: VideoPlayerMediaSpec,
        startPositionMs: Long = spec.startPositionMs,
        playWhenReady: Boolean = true,
    )

    fun refresh(spec: VideoPlayerMediaSpec)

    fun selectSubtitle(track: VideoPlayerTrackEntry?): Boolean

    fun selectMountedSubtitle(
        subtitles: List<PlayerSubtitleInfo>,
        selectedIndex: Int,
    ): Boolean

    fun selectAudioTrack(track: VideoPlayerTrackEntry)

    fun applyTrackSelection(
        audioCaps: AudioPassthroughCapabilities,
        displayHdr: HdrCapabilities = HdrCapabilities(),
        preferredAudioLanguage: String? = null,
        preferredTextLanguage: String? = null,
        hdrEnabled: Boolean = true,
    )

    fun release()
}
```

- [ ] **Step 4: Run the interface test and verify it passes**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.VideoPlaybackBackendInterfaceTest'
```

Expected: PASS.

- [ ] **Step 5: Commit Task 2**

Run:

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackend.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendInterfaceTest.kt
git commit -m "Add video playback backend interface"
```

---

### Task 3: Media3 Backend Adapter

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/Media3VideoPlaybackBackend.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/Media3VideoPlaybackBackendSourceTest.kt`

- [ ] **Step 1: Write the failing Media3 adapter source test**

Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/Media3VideoPlaybackBackendSourceTest.kt`:

```kotlin
package com.continuum.app.common.player.backend

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Media3VideoPlaybackBackendSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/backend/Media3VideoPlaybackBackend.kt",
    )

    @Test
    fun media3BackendDelegatesToExistingSharedHelpers() {
        val text = source.readText()

        assertTrue(text.contains("class Media3VideoPlaybackBackend"))
        assertTrue(text.contains("VideoPlaybackBackend"))
        assertTrue(text.contains("override val kind: VideoPlaybackBackendKind = VideoPlaybackBackendKind.Media3"))
        assertTrue(text.contains("VideoBackendCapabilities.media3()"))
        assertTrue(text.contains("mountVideoMedia("))
        assertTrue(text.contains("refreshMountedVideoMedia("))
        assertTrue(text.contains("trackSelectionCoordinator.selectSubtitle("))
        assertTrue(text.contains("trackSelectionCoordinator.selectMountedSubtitle("))
        assertTrue(text.contains("trackSelectionCoordinator.selectAudioTrack("))
        assertTrue(text.contains("playerFactory.applyTrackSelectionPresets("))
        assertFalse(text.contains("createPlayer("), "surface backend must wrap an already-bound Player")
        assertFalse(text.contains("mpv", ignoreCase = true))
    }
}
```

- [ ] **Step 2: Run the Media3 adapter source test and verify it fails**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.Media3VideoPlaybackBackendSourceTest'
```

Expected: FAIL because `Media3VideoPlaybackBackend.kt` does not exist.

- [ ] **Step 3: Add the Media3 backend adapter**

Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/Media3VideoPlaybackBackend.kt`:

```kotlin
package com.continuum.app.common.player.backend

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.continuum.app.common.player.AudioTrackManager
import com.continuum.app.common.player.ContinuumPlayerFactory
import com.continuum.app.common.player.VideoPlayerMediaSpec
import com.continuum.app.common.player.mountVideoMedia
import com.continuum.app.common.player.refreshMountedVideoMedia
import com.continuum.app.common.player.video.VideoPlayerTrackEntry
import com.continuum.app.common.player.video.VideoTrackSelectionCoordinator
import com.continuum.app.model.playback.AudioPassthroughCapabilities
import com.continuum.app.model.playback.HdrCapabilities
import com.continuum.app.model.playback.PlayerSubtitleInfo

@UnstableApi
class Media3VideoPlaybackBackend(
    private val playerFactory: ContinuumPlayerFactory,
    private val audioTrackManager: AudioTrackManager,
    private val trackSelectionCoordinator: VideoTrackSelectionCoordinator,
    override val player: Player,
) : VideoPlaybackBackend {
    override val kind: VideoPlaybackBackendKind = VideoPlaybackBackendKind.Media3
    override val capabilities: VideoBackendCapabilities = VideoBackendCapabilities.media3()

    private var mountedSpec: VideoPlayerMediaSpec? = null

    override fun mount(
        spec: VideoPlayerMediaSpec,
        startPositionMs: Long,
        playWhenReady: Boolean,
    ) {
        mountedSpec = spec
        mountVideoMedia(
            player = player,
            playerFactory = playerFactory,
            spec = spec,
            startPositionMs = startPositionMs,
            playWhenReady = playWhenReady,
        )
    }

    override fun refresh(spec: VideoPlayerMediaSpec) {
        mountedSpec = spec
        refreshMountedVideoMedia(
            player = player,
            playerFactory = playerFactory,
            spec = spec,
        )
    }

    override fun selectSubtitle(track: VideoPlayerTrackEntry?): Boolean =
        trackSelectionCoordinator.selectSubtitle(
            player = player,
            playerFactory = playerFactory,
            mediaSpec = requireMediaSpecForExternalSubtitle(track),
            selectedTrack = track,
        )

    override fun selectMountedSubtitle(
        subtitles: List<PlayerSubtitleInfo>,
        selectedIndex: Int,
    ): Boolean = trackSelectionCoordinator.selectMountedSubtitle(
        player = player,
        subtitles = subtitles,
        selectedIndex = selectedIndex,
    )

    override fun selectAudioTrack(track: VideoPlayerTrackEntry) {
        trackSelectionCoordinator.selectAudioTrack(
            player = player,
            audioTrackManager = audioTrackManager,
            selectedTrack = track,
        )
    }

    override fun applyTrackSelection(
        audioCaps: AudioPassthroughCapabilities,
        displayHdr: HdrCapabilities,
        preferredAudioLanguage: String?,
        preferredTextLanguage: String?,
        hdrEnabled: Boolean,
    ) {
        playerFactory.applyTrackSelectionPresets(
            player = player,
            audioCaps = audioCaps,
            displayHdr = displayHdr,
            preferredAudioLanguage = preferredAudioLanguage,
            preferredTextLanguage = preferredTextLanguage,
            hdrEnabled = hdrEnabled,
        )
    }

    override fun release() {
        player.release()
    }

    private fun requireMediaSpecForExternalSubtitle(track: VideoPlayerTrackEntry?): VideoPlayerMediaSpec {
        val spec = mountedSpec
        if (spec != null) return spec
        if (track?.subtitle == null) {
            return VideoPlayerMediaSpec(
                streamUrl = "",
                playMethod = com.continuum.app.model.playback.PlayMethod.DIRECT,
                serverUrl = "",
            )
        }
        error("Cannot select an external subtitle before video media has been mounted.")
    }
}
```

- [ ] **Step 4: Run the Media3 adapter source test**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.Media3VideoPlaybackBackendSourceTest'
```

Expected: PASS.

- [ ] **Step 5: Run the existing mounter and track selection tests**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.VideoPlayerMediaMounterSourceTest' --tests 'com.continuum.app.common.player.video.VideoTrackSelectionCoordinatorTest'
```

Expected: PASS.

- [ ] **Step 6: Commit Task 3**

Run:

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/Media3VideoPlaybackBackend.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/Media3VideoPlaybackBackendSourceTest.kt
git commit -m "Wrap Media3 playback operations in backend adapter"
```

---

### Task 4: Backend Factory and Dependency Injection

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactory.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactorySourceTest.kt`

- [ ] **Step 1: Write the failing backend factory source test**

Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactorySourceTest.kt`:

```kotlin
package com.continuum.app.common.player.backend

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPlaybackBackendFactorySourceTest {
    private val factorySource = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactory.kt",
    )

    @Test
    fun factoryWrapsBoundPlayerAsMedia3BackendForAllCurrentRequests() {
        val text = factorySource.readText()

        assertTrue(text.contains("class VideoPlaybackBackendFactory"))
        assertTrue(text.contains("fun create("))
        assertTrue(text.contains("player: Player"))
        assertTrue(text.contains("request: VideoPlaybackBackendRequest = VideoPlaybackBackendRequest()"))
        assertTrue(text.contains("return Media3VideoPlaybackBackend("))
        assertTrue(text.contains("VideoTrackSelectionCoordinator(subtitleManager)"))
        assertFalse(text.contains("when (request.preference)"))
        assertFalse(text.contains("mpv", ignoreCase = true))
    }
}
```

- [ ] **Step 2: Run the backend factory source test and verify it fails**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.VideoPlaybackBackendFactorySourceTest'
```

Expected: FAIL because `VideoPlaybackBackendFactory.kt` does not exist.

- [ ] **Step 3: Add the backend factory**

Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactory.kt`:

```kotlin
package com.continuum.app.common.player.backend

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.continuum.app.common.player.AudioTrackManager
import com.continuum.app.common.player.ContinuumPlayerFactory
import com.continuum.app.common.player.SubtitleManager
import com.continuum.app.common.player.video.VideoTrackSelectionCoordinator

@UnstableApi
class VideoPlaybackBackendFactory(
    private val playerFactory: ContinuumPlayerFactory,
    private val audioTrackManager: AudioTrackManager,
    private val subtitleManager: SubtitleManager,
) {
    fun create(
        player: Player,
        request: VideoPlaybackBackendRequest = VideoPlaybackBackendRequest(),
    ): VideoPlaybackBackend {
        @Suppress("UNUSED_VARIABLE")
        val selectionRequest = request
        return Media3VideoPlaybackBackend(
            playerFactory = playerFactory,
            audioTrackManager = audioTrackManager,
            trackSelectionCoordinator = VideoTrackSelectionCoordinator(subtitleManager),
            player = player,
        )
    }
}
```

- [ ] **Step 4: Register the backend factory in mobile DI**

In `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`, add this import near the other player imports:

```kotlin
import com.continuum.app.common.player.backend.VideoPlaybackBackendFactory
```

Then add this binding immediately after the existing `ContinuumPlayerFactory` binding:

```kotlin
    single {
        VideoPlaybackBackendFactory(
            playerFactory = get(),
            audioTrackManager = get(),
            subtitleManager = get(),
        )
    }
```

- [ ] **Step 5: Register the backend factory in TV DI**

In `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`, add this import near the other player imports:

```kotlin
import com.continuum.app.common.player.backend.VideoPlaybackBackendFactory
```

Then add this binding immediately after the existing `ContinuumPlayerFactory` binding:

```kotlin
    single {
        VideoPlaybackBackendFactory(
            playerFactory = get(),
            audioTrackManager = get(),
            subtitleManager = get(),
        )
    }
```

- [ ] **Step 6: Run factory test and compile app modules**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.VideoPlaybackBackendFactorySourceTest' :androidApp:compileDebugKotlinAndroid :androidTvApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 7: Commit Task 4**

Run:

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactory.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactorySourceTest.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt
git commit -m "Register video playback backend factory"
```

---

### Task 5: Move Mobile Player Surface to Backend Adapter

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt`
- Modify: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreenStartPositionTest.kt`

- [ ] **Step 1: Update the mobile source guard test first**

In `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreenStartPositionTest.kt`, replace the mount/refresh helper assertions with backend assertions:

```kotlin
    @Test
    fun playerScreenDelegatesInitialMountToBackend() {
        assertTrue(
            source.contains("VideoPlayerMediaSpec("),
            "mobile player must build the shared video media spec",
        )
        assertTrue(
            source.contains("playbackBackend?.mount("),
            "mobile player must mount media through the backend",
        )
        assertFalse(
            source.contains("mountVideoMedia("),
            "mobile player must not call the Media3 mount helper directly",
        )
        assertTrue(
            !source.contains("controller.setMediaItem(mediaItem, startMs)"),
            "mobile player must not duplicate initial Media3 mount ordering",
        )
    }

    @Test
    fun playerScreenDelegatesSubtitleRefreshToBackend() {
        assertTrue(
            source.contains("playbackBackend?.refresh("),
            "mobile subtitle refresh must use the backend",
        )
        assertFalse(
            source.contains("refreshMountedVideoMedia("),
            "mobile subtitle refresh must not call the Media3 refresh helper directly",
        )
    }
```

Also update the track-change assertion body to expect backend selection:

```kotlin
        assertTrue(
            trackChangeBody.contains("playbackBackend?.selectMountedSubtitle("),
            "track changes must reselect the already-mounted subtitle through the backend",
        )
        assertFalse(
            trackChangeBody.contains("trackSelectionCoordinator.selectSubtitle("),
            "track changes must not use the remounting subtitle selection path",
        )
```

- [ ] **Step 2: Run the mobile source guard and verify it fails**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.player.PlayerScreenStartPositionTest'
```

Expected: FAIL because `PlayerScreen.kt` still imports/calls `mountVideoMedia`, `refreshMountedVideoMedia`, and `VideoTrackSelectionCoordinator` directly.

- [ ] **Step 3: Update mobile imports**

In `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt`, remove these imports:

```kotlin
import com.continuum.app.common.player.ContinuumPlayerFactory
import com.continuum.app.common.player.video.VideoTrackSelectionCoordinator
import com.continuum.app.common.player.mountVideoMedia
import com.continuum.app.common.player.refreshMountedVideoMedia
```

Add these imports:

```kotlin
import com.continuum.app.common.player.backend.VideoPlaybackBackend
import com.continuum.app.common.player.backend.VideoPlaybackBackendFactory
import com.continuum.app.common.player.backend.VideoPlaybackBackendRequest
import com.continuum.app.common.player.backend.VideoPlaybackFormFactor
```

- [ ] **Step 4: Replace direct player factory/coordinator injection with backend factory**

In `PlayerScreen`, replace:

```kotlin
    val playerFactory: ContinuumPlayerFactory = koinInject()
```

with:

```kotlin
    val backendFactory: VideoPlaybackBackendFactory = koinInject()
```

Keep the existing local `SubtitleManager` because the screen still uses it for `applyAppearance`. Remove only the local track-selection coordinator:

```kotlin
    val trackSelectionCoordinator = remember(subtitleManager) {
        VideoTrackSelectionCoordinator(subtitleManager)
    }
```

Add near `var mediaController by remember { mutableStateOf<MediaController?>(null) }`:

```kotlin
    var playbackBackend by remember { mutableStateOf<VideoPlaybackBackend?>(null) }
```

- [ ] **Step 5: Create the backend when the MediaController binds**

In the `future.addListener` block, replace:

```kotlin
                    mediaController = runCatching { future.get() }.getOrNull()
```

with:

```kotlin
                    val controller = runCatching { future.get() }.getOrNull()
                    mediaController = controller
                    playbackBackend = controller?.let {
                        backendFactory.create(
                            player = it,
                            request = VideoPlaybackBackendRequest(
                                contentId = contentId,
                                fileId = initialFileId,
                                formFactor = VideoPlaybackFormFactor.Mobile,
                            ),
                        )
                    }
```

In `onDispose`, after stopping and clearing media items, replace `controller.release()` with:

```kotlin
                playbackBackend?.release()
```

Then set both state holders to null:

```kotlin
            playbackBackend = null
            mediaController = null
```

- [ ] **Step 6: Route track preset application through the backend**

Replace:

```kotlin
        playerFactory.applyTrackSelectionPresets(
            player = controller,
            audioCaps = audioCaps,
            displayHdr = if (hdrEnabled) displayHdr else com.continuum.app.model.playback.HdrCapabilities(),
            preferredAudioLanguage = uiState.preferredAudioLanguage,
            preferredTextLanguage = uiState.preferredTextLanguage,
        )
```

with:

```kotlin
        playbackBackend?.applyTrackSelection(
            audioCaps = audioCaps,
            displayHdr = if (hdrEnabled) displayHdr else com.continuum.app.model.playback.HdrCapabilities(),
            preferredAudioLanguage = uiState.preferredAudioLanguage,
            preferredTextLanguage = uiState.preferredTextLanguage,
            hdrEnabled = hdrEnabled,
        )
```

- [ ] **Step 7: Route initial mount through the backend**

Replace:

```kotlin
        mountVideoMedia(player = controller, playerFactory = playerFactory, spec = mediaSpec)
```

with:

```kotlin
        playbackBackend?.mount(spec = mediaSpec)
```

- [ ] **Step 8: Route subtitle refresh through the backend**

Replace:

```kotlin
        refreshMountedVideoMedia(player = controller, playerFactory = playerFactory, spec = mediaSpec)
```

with:

```kotlin
        playbackBackend?.refresh(spec = mediaSpec)
```

- [ ] **Step 9: Route subtitle selection through the backend**

Replace the explicit `trackSelectionCoordinator.selectSubtitle(...)` call in the subtitle-selection `LaunchedEffect` with:

```kotlin
        playbackBackend?.selectSubtitle(
            subtitleTrackEntry(uiState.subtitleTracks, uiState.selectedSubtitleIndex),
        )
```

Inside the `onTracksChanged` listener body, replace `trackSelectionCoordinator.selectMountedSubtitle(...)` with:

```kotlin
                            playbackBackend?.selectMountedSubtitle(
                                subtitles = uiState.subtitleTracks,
                                selectedIndex = uiState.selectedSubtitleIndex,
                            )
```

- [ ] **Step 10: Run mobile source guard and compile**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.player.PlayerScreenStartPositionTest' :androidApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 11: Commit Task 5**

Run:

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreenStartPositionTest.kt
git commit -m "Route mobile video surface through playback backend"
```

---

### Task 6: Move TV Player Surface to Backend Adapter

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`
- Modify: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreenStartPositionTest.kt`

- [ ] **Step 1: Update the TV source guard test first**

In `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreenStartPositionTest.kt`, replace the helper assertions with:

```kotlin
    @Test
    fun tvPlayerDelegatesInitialMountToBackend() {
        assertTrue(
            source.contains("VideoPlayerMediaSpec("),
            "TV player must build the shared video media spec",
        )
        assertTrue(
            source.contains("playbackBackend?.mount("),
            "TV player must mount media through the backend",
        )
        assertTrue(
            !source.contains("mountVideoMedia("),
            "TV player must not call the Media3 mount helper directly",
        )
        assertTrue(
            !source.contains("controller.setMediaItem(mediaItem)"),
            "TV player must not call the no-position Media3 mount overload",
        )
        assertTrue(
            !source.contains("controller.seekTo(startMs)"),
            "TV player must not use post-mount seekTo for initial resume",
        )
    }

    @Test
    fun tvPlayerDelegatesSubtitleRefreshToBackend() {
        assertTrue(
            source.contains("playbackBackend?.refresh("),
            "TV subtitle refresh must use the backend",
        )
        assertTrue(
            !source.contains("refreshMountedVideoMedia("),
            "TV subtitle refresh must not call the Media3 refresh helper directly",
        )
    }
```

- [ ] **Step 2: Run the TV source guard and verify it fails**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.player.TvPlayerScreenStartPositionTest'
```

Expected: FAIL because `TvPlayerScreen.kt` still imports/calls `mountVideoMedia`, `refreshMountedVideoMedia`, and `VideoTrackSelectionCoordinator` directly.

- [ ] **Step 3: Update TV imports**

In `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`, remove:

```kotlin
import com.continuum.app.common.player.ContinuumPlayerFactory
import com.continuum.app.common.player.video.VideoTrackSelectionCoordinator
import com.continuum.app.common.player.mountVideoMedia
import com.continuum.app.common.player.refreshMountedVideoMedia
```

Add:

```kotlin
import com.continuum.app.common.player.backend.VideoPlaybackBackend
import com.continuum.app.common.player.backend.VideoPlaybackBackendFactory
import com.continuum.app.common.player.backend.VideoPlaybackBackendRequest
import com.continuum.app.common.player.backend.VideoPlaybackFormFactor
```

- [ ] **Step 4: Replace direct player factory/coordinator injection with backend factory**

Replace the `PlayerScreen` parameter:

```kotlin
    playerFactory: ContinuumPlayerFactory = koinInject(),
```

with:

```kotlin
    backendFactory: VideoPlaybackBackendFactory = koinInject(),
```

Keep the existing `subtitleManager: SubtitleManager = koinInject()` parameter because the screen still uses it for `applyAppearance`. Remove only the remembered `VideoTrackSelectionCoordinator` block. Add near `var mediaController by remember { mutableStateOf<MediaController?>(null) }`:

```kotlin
    var playbackBackend by remember { mutableStateOf<VideoPlaybackBackend?>(null) }
```

- [ ] **Step 5: Create the backend when the MediaController binds**

In the TV `future.addListener` block, replace:

```kotlin
                    mediaController = runCatching { future.get() }.getOrNull()
```

with:

```kotlin
                    val controller = runCatching { future.get() }.getOrNull()
                    mediaController = controller
                    playbackBackend = controller?.let {
                        backendFactory.create(
                            player = it,
                            request = VideoPlaybackBackendRequest(
                                contentId = contentId,
                                fileId = preferredFileId,
                                formFactor = VideoPlaybackFormFactor.Tv,
                            ),
                        )
                    }
```

In `onDispose`, replace `mediaController?.release()` with:

```kotlin
            playbackBackend?.release()
```

Then set:

```kotlin
            playbackBackend = null
            mediaController = null
```

- [ ] **Step 6: Route TV subtitle menu selection through the backend**

In `applyTvSubtitleSelection`, replace the `trackSelectionCoordinator.selectSubtitle(...)` call with:

```kotlin
            if (playbackBackend?.selectSubtitle(selectedTrack) == true) {
                viewModel.onSubtitleSelectionApplied(idx)
                if (dismiss) viewModel.closeSubtitleMenu()
            }
```

- [ ] **Step 7: Route track preset application through the backend**

Replace:

```kotlin
        playerFactory.applyTrackSelectionPresets(
            player = controller,
            audioCaps = audioCaps,
            displayHdr = displayHdr,
            preferredAudioLanguage = state.preferredAudioLanguage,
            preferredTextLanguage = state.preferredTextLanguage,
            hdrEnabled = hdrEnabled,
        )
```

with:

```kotlin
        playbackBackend?.applyTrackSelection(
            audioCaps = audioCaps,
            displayHdr = displayHdr,
            preferredAudioLanguage = state.preferredAudioLanguage,
            preferredTextLanguage = state.preferredTextLanguage,
            hdrEnabled = hdrEnabled,
        )
```

- [ ] **Step 8: Route mount and refresh through the backend**

Replace:

```kotlin
        mountVideoMedia(player = controller, playerFactory = playerFactory, spec = mediaSpec)
```

with:

```kotlin
        playbackBackend?.mount(spec = mediaSpec)
```

Replace:

```kotlin
        refreshMountedVideoMedia(player = controller, playerFactory = playerFactory, spec = mediaSpec)
```

with:

```kotlin
        playbackBackend?.refresh(spec = mediaSpec)
```

- [ ] **Step 9: Route auto-select and HUD audio selection through the backend**

In the `subtitleSelectRequests` collector, replace the coordinator call with:

```kotlin
            if (playbackBackend?.selectSubtitle(selectedTrack) == true) {
                viewModel.onSubtitleSelectionApplied(idx)
            }
```

In the `TvPlayerHud` `onSelectAudio` block, replace the direct audio selection with:

```kotlin
                                if (selectedTrack != null) {
                                    playbackBackend?.selectAudioTrack(selectedTrack)
                                }
```

- [ ] **Step 10: Run TV source guard and compile**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.player.TvPlayerScreenStartPositionTest' :androidTvApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 11: Commit Task 6**

Run:

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreenStartPositionTest.kt
git commit -m "Route TV video surface through playback backend"
```

---

### Task 7: Native Dependency Guard and Integration Verification

**Files:**
- Create: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoBackendNativeDependencyGuardTest.kt`

- [ ] **Step 1: Add native dependency guard test**

Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoBackendNativeDependencyGuardTest.kt`:

```kotlin
package com.continuum.app.common.player.backend

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoBackendNativeDependencyGuardTest {

    @Test
    fun backendBoundaryDoesNotAddMpvOrLibassDependencyYet() {
        val files = listOf(
            java.io.File("../gradle/libs.versions.toml"),
            java.io.File("../android-shared/build.gradle.kts"),
            java.io.File("../androidApp/build.gradle.kts"),
            java.io.File("../androidTvApp/build.gradle.kts"),
            java.io.File("../settings.gradle.kts"),
        )

        val combined = files.joinToString("\n") { file ->
            if (file.exists()) file.readText() else ""
        }

        assertFalse(combined.contains("libmpv", ignoreCase = true))
        assertFalse(combined.contains("dev.jdtech.mpv", ignoreCase = true))
        assertFalse(combined.contains("wholphin-mpv", ignoreCase = true))
        assertFalse(combined.contains("libass", ignoreCase = true))
        assertTrue(
            combined.contains("media3"),
            "Media3 remains the active backend dependency in this slice",
        )
    }
}
```

- [ ] **Step 2: Run the native dependency guard**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.VideoBackendNativeDependencyGuardTest'
```

Expected: PASS.

- [ ] **Step 3: Run focused playback boundary tests**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest \
  --tests 'com.continuum.app.common.player.backend.*' \
  --tests 'com.continuum.app.common.player.VideoPlayerMediaMounterSourceTest' \
  --tests 'com.continuum.app.common.player.video.VideoTrackSelectionCoordinatorTest' \
  :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.player.PlayerScreenStartPositionTest' \
  :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.player.TvPlayerScreenStartPositionTest'
```

Expected: PASS.

- [ ] **Step 4: Commit Task 7**

Run:

```bash
git add android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoBackendNativeDependencyGuardTest.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreenStartPositionTest.kt \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreenStartPositionTest.kt
git commit -m "Guard playback backend boundary against native deps"
```

---

### Task 8: Full Verification

**Files:**
- No new files expected. This task verifies the full branch.

- [ ] **Step 1: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no output and exit code 0.

- [ ] **Step 2: Run full Gradle verification**

Run:

```bash
./gradlew :shared:test :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidTvApp:testDebugUnitTest :androidApp:assembleDebug :androidTvApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Inspect final branch state**

Run:

```bash
git status --short --branch
git log --oneline -8
```

Expected:

- status shows `feature/production-playback-architecture`
- working tree is clean
- recent commits include the backend model, interface, adapter, DI, mobile surface, TV surface, and guard commits

- [ ] **Step 4: Push branch**

Run:

```bash
git push
```

Expected: push succeeds to `origin/feature/production-playback-architecture`.

---

## Self-Review Checklist

- Spec coverage: Tasks 1-4 create the shared backend package, factory, Media3 adapter, and capabilities. Tasks 5-6 move mobile and TV mount/refresh/selection call sites. Task 7 guards against MPV/libass/native dependency creep. Task 8 verifies the whole branch.
- Scope check: MPV is intentionally excluded; `ContinuumPlaybackService` remains Media3-only for this slice.
- Type consistency: The same names are used throughout the plan: `VideoPlaybackBackend`, `VideoPlaybackBackendFactory`, `Media3VideoPlaybackBackend`, `VideoBackendCapabilities`, `VideoPlaybackBackendRequest`, `VideoPlaybackFormFactor`, and `SubtitleRendering`.
- Risk control: Mobile and TV migrations are separate commits with focused tests before full verification.
