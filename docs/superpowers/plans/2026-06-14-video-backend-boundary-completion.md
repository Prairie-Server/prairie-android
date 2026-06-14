# Video Backend Boundary Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the current video backend boundary slice by routing mobile and TV video player operations through `VideoPlaybackBackend` while keeping Media3 as the only active backend.

**Architecture:** The backend model and `Media3VideoPlaybackBackend` already exist in `android-shared`. This plan adds a small factory, registers it in both Android Koin modules, migrates mobile and TV screens from direct `ContinuumPlayerFactory`/mounter/coordinator calls to the backend, and surfaces backend capabilities in TV diagnostics.

**Tech Stack:** Kotlin 2.1, Android, Compose, Media3 1.10.0, Koin, existing Silo player helpers, source-guard unit tests.

---

## Scope Check

This plan implements only Phase 1 from `docs/superpowers/specs/2026-06-14-best-of-breed-client-architecture-design.md`: finish the shared video backend boundary. Music, audiobooks, reader engines, downloads polish, and unified hubs get separate plans.

Already implemented before this plan:

- `VideoPlaybackBackendKind`
- `VideoPlaybackBackendPreference`
- `VideoPlaybackFormFactor`
- `SubtitleRendering`
- `VideoBackendCapabilities`
- `VideoPlaybackBackendRequest`
- `VideoPlaybackBackend`
- `Media3VideoPlaybackBackend`
- Backend capability/interface/source tests

## File Structure

Create:

- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactory.kt`  
  Factory that wraps an already-bound Media3 `Player` in `Media3VideoPlaybackBackend`.
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactorySourceTest.kt`  
  Source guard proving the factory returns Media3 and does not introduce native/MPV dependencies.
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/di/AndroidVideoBackendDiSourceTest.kt`  
  Source guard proving mobile DI registers the backend factory.
- `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/di/AndroidTvVideoBackendDiSourceTest.kt`  
  Source guard proving TV DI registers the backend factory.

Modify:

- `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`  
  Add `VideoPlaybackBackendFactory` import and singleton.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`  
  Add `VideoPlaybackBackendFactory` import and singleton.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt`  
  Replace direct mount, refresh, track-preset, and subtitle-selection calls with backend calls.
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreenStartPositionTest.kt`  
  Update source guards to require backend usage and forbid raw mounter/coordinator usage in the screen.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`  
  Replace direct mount, refresh, track-preset, subtitle-selection, and audio-selection calls with backend calls.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt`  
  Add backend capability rows to the stats snapshot.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt`  
  Render backend capability rows in the stats pane.
- `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreenStartPositionTest.kt`  
  Update source guards to require backend usage and forbid raw mounter/coordinator usage in the screen.
- `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerBackendCapabilitiesSourceTest.kt`  
  Verify the TV stats path includes backend capability metadata.

Do not modify:

- `ContinuumPlaybackService.kt`. It remains Media3-only in this slice.
- Gradle dependencies. This plan must not add MPV, libass, JitPack, or native AARs.
- Audiobook, music, reader, or download flows.

---

### Task 1: Backend Factory And DI

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactory.kt`
- Create: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactorySourceTest.kt`
- Create: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/di/AndroidVideoBackendDiSourceTest.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/di/AndroidTvVideoBackendDiSourceTest.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`

- [ ] **Step 1: Write the failing factory source test**

Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactorySourceTest.kt`:

```kotlin
package com.continuum.app.common.player.backend

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPlaybackBackendFactorySourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactory.kt",
    )

    @Test
    fun factoryWrapsBoundMedia3PlayerOnly() {
        val text = source.readText()

        assertTrue(text.contains("class VideoPlaybackBackendFactory"))
        assertTrue(text.contains("fun create("))
        assertTrue(text.contains("player: Player"))
        assertTrue(text.contains("request: VideoPlaybackBackendRequest = VideoPlaybackBackendRequest()"))
        assertTrue(text.contains("Media3VideoPlaybackBackend("))
        assertTrue(text.contains("VideoTrackSelectionCoordinator(subtitleManager)"))
        assertFalse(text.contains("createPlayer("), "factory must wrap the bound MediaController, not create a service player")
        assertFalse(text.contains("mpv", ignoreCase = true), "MPV is out of scope for this slice")
        assertFalse(text.contains("libass", ignoreCase = true), "libass is out of scope for this slice")
    }
}
```

- [ ] **Step 2: Write the failing DI source tests**

Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/di/AndroidVideoBackendDiSourceTest.kt`:

```kotlin
package com.continuum.app.android.di

import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidVideoBackendDiSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt",
    ).readText()

    @Test
    fun mobileRegistersVideoPlaybackBackendFactory() {
        assertTrue(source.contains("import com.continuum.app.common.player.backend.VideoPlaybackBackendFactory"))
        assertTrue(source.contains("VideoPlaybackBackendFactory("))
        assertTrue(source.contains("playerFactory = get()"))
        assertTrue(source.contains("audioTrackManager = get()"))
        assertTrue(source.contains("subtitleManager = get()"))
    }
}
```

Create `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/di/AndroidTvVideoBackendDiSourceTest.kt`:

```kotlin
package com.continuum.app.tv.di

import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidTvVideoBackendDiSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt",
    ).readText()

    @Test
    fun tvRegistersVideoPlaybackBackendFactory() {
        assertTrue(source.contains("import com.continuum.app.common.player.backend.VideoPlaybackBackendFactory"))
        assertTrue(source.contains("VideoPlaybackBackendFactory("))
        assertTrue(source.contains("playerFactory = get()"))
        assertTrue(source.contains("audioTrackManager = get()"))
        assertTrue(source.contains("subtitleManager = get()"))
    }
}
```

- [ ] **Step 3: Run the new tests and verify they fail**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.VideoPlaybackBackendFactorySourceTest'
./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.di.AndroidVideoBackendDiSourceTest'
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.di.AndroidTvVideoBackendDiSourceTest'
```

Expected: each test fails because the factory file or DI registration does not exist yet.

- [ ] **Step 4: Add the backend factory**

Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactory.kt`:

```kotlin
package com.continuum.app.common.player.backend

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import com.continuum.app.common.player.AudioTrackManager
import com.continuum.app.common.player.ContinuumPlayerFactory
import com.continuum.app.common.player.SubtitleManager
import com.continuum.app.common.player.video.VideoTrackSelectionCoordinator

class VideoPlaybackBackendFactory(
    private val playerFactory: ContinuumPlayerFactory,
    private val audioTrackManager: AudioTrackManager,
    private val subtitleManager: SubtitleManager,
) {
    @OptIn(UnstableApi::class)
    fun create(
        player: Player,
        request: VideoPlaybackBackendRequest = VideoPlaybackBackendRequest(),
    ): VideoPlaybackBackend = when (request.preference) {
        VideoPlaybackBackendPreference.Auto,
        VideoPlaybackBackendPreference.Media3,
        -> Media3VideoPlaybackBackend(
            playerFactory = playerFactory,
            audioTrackManager = audioTrackManager,
            trackSelectionCoordinator = VideoTrackSelectionCoordinator(subtitleManager),
            player = player,
        )
    }
}
```

- [ ] **Step 5: Register the factory in mobile DI**

Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`.

Add import near the other player imports:

```kotlin
import com.continuum.app.common.player.backend.VideoPlaybackBackendFactory
```

Add this singleton immediately after `single { AudioTrackManager() }` and before `single { AudioCapabilityManager(androidContext()) }`:

```kotlin
    single {
        VideoPlaybackBackendFactory(
            playerFactory = get(),
            audioTrackManager = get(),
            subtitleManager = get(),
        )
    }
```

- [ ] **Step 6: Register the factory in TV DI**

Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`.

Add import near the other player imports:

```kotlin
import com.continuum.app.common.player.backend.VideoPlaybackBackendFactory
```

Add this singleton immediately after `single { AudioTrackManager() }` and before `single { AudioCapabilityManager(androidContext()) }`:

```kotlin
    single {
        VideoPlaybackBackendFactory(
            playerFactory = get(),
            audioTrackManager = get(),
            subtitleManager = get(),
        )
    }
```

- [ ] **Step 7: Run Task 1 tests and verify they pass**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.VideoPlaybackBackendFactorySourceTest'
./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.di.AndroidVideoBackendDiSourceTest'
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.di.AndroidTvVideoBackendDiSourceTest'
```

Expected: PASS.

- [ ] **Step 8: Commit Task 1**

Run:

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactory.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactorySourceTest.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/di/AndroidVideoBackendDiSourceTest.kt \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/di/AndroidTvVideoBackendDiSourceTest.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt
git commit -m "Add video playback backend factory"
```

---

### Task 2: Mobile Player Backend Migration

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt`
- Modify: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreenStartPositionTest.kt`

- [ ] **Step 1: Update the mobile source guard first**

Modify `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreenStartPositionTest.kt`.

Replace `playerScreenDelegatesInitialMountToSharedHelper` with:

```kotlin
    @Test
    fun playerScreenDelegatesInitialMountToVideoBackend() {
        assertTrue(
            source.contains("VideoPlayerMediaSpec("),
            "mobile player must build the shared video media spec",
        )
        assertTrue(
            source.contains("VideoPlaybackBackendFactory"),
            "mobile player must inject the shared backend factory",
        )
        assertTrue(
            source.contains("backend.mount(mediaSpec"),
            "mobile player must mount through the backend",
        )
        assertFalse(
            source.contains("mountVideoMedia("),
            "mobile player must not call the raw Media3 mounter directly",
        )
    }
```

Replace `playerScreenDelegatesSubtitleRefreshToSharedHelper` with:

```kotlin
    @Test
    fun playerScreenDelegatesSubtitleRefreshToVideoBackend() {
        assertTrue(
            source.contains("backend.refresh(mediaSpec"),
            "mobile subtitle refresh must use the backend refresh path",
        )
        assertFalse(
            source.contains("refreshMountedVideoMedia("),
            "mobile subtitle refresh must not call the raw Media3 refresh helper directly",
        )
    }
```

Replace `playerScreenTrackSelectionPreservesEffectiveMountedMediaSpec` with:

```kotlin
    @Test
    fun playerScreenTrackSelectionUsesMountedBackendState() {
        assertTrue(
            source.contains("backend.selectSubtitle("),
            "mobile subtitle selection must go through the mounted backend",
        )
        assertFalse(
            source.contains("trackSelectionMediaSpec("),
            "mobile player must not rebuild media specs for track selection once the backend owns mounted state",
        )
    }
```

Replace `playerScreenTrackChangeReselectsMountedSubtitleWithoutRemountingMedia` with:

```kotlin
    @Test
    fun playerScreenTrackChangeReselectsMountedSubtitleWithoutRemountingMedia() {
        val trackChangeBody = source
            .substringAfter("override fun onTracksChanged(tracks: androidx.media3.common.Tracks)")
            .substringBefore("controller.addListener(listener)")

        assertTrue(
            trackChangeBody.contains("videoBackend?.selectMountedSubtitle("),
            "track changes must reselect the already-mounted subtitle through the backend",
        )
        assertFalse(
            trackChangeBody.contains("selectSubtitle("),
            "track changes must not use the remounting subtitle selection path",
        )
        assertFalse(
            trackChangeBody.contains("VideoPlayerMediaSpec("),
            "track changes must not rebuild the media spec or remount media",
        )
    }
```

- [ ] **Step 2: Run the mobile source guard and verify it fails**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.player.PlayerScreenStartPositionTest'
```

Expected: FAIL because `PlayerScreen.kt` still calls `mountVideoMedia`, `refreshMountedVideoMedia`, and `VideoTrackSelectionCoordinator` directly.

- [ ] **Step 3: Update mobile imports and injection**

Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt`.

Remove these imports:

```kotlin
import com.continuum.app.common.player.ContinuumPlayerFactory
import com.continuum.app.common.player.video.VideoTrackSelectionCoordinator
import com.continuum.app.common.player.mountVideoMedia
import com.continuum.app.common.player.refreshMountedVideoMedia
```

Add these imports:

```kotlin
import com.continuum.app.common.player.backend.VideoPlaybackBackendFactory
import com.continuum.app.common.player.backend.VideoPlaybackBackendRequest
import com.continuum.app.common.player.backend.VideoPlaybackFormFactor
```

Replace:

```kotlin
    val playerFactory: ContinuumPlayerFactory = koinInject()
```

with:

```kotlin
    val backendFactory: VideoPlaybackBackendFactory = koinInject()
```

Remove:

```kotlin
    val trackSelectionCoordinator = remember(subtitleManager) {
        VideoTrackSelectionCoordinator(subtitleManager)
    }
    var mountedVideoMediaSpec by remember { mutableStateOf<VideoPlayerMediaSpec?>(null) }
```

- [ ] **Step 4: Add the remembered backend**

After `var mediaController by remember { mutableStateOf<MediaController?>(null) }`, add:

```kotlin
    val videoBackend = remember(mediaController, backendFactory, contentId, initialFileId) {
        mediaController?.let { controller ->
            backendFactory.create(
                player = controller,
                request = VideoPlaybackBackendRequest(
                    contentId = contentId,
                    fileId = initialFileId,
                    formFactor = VideoPlaybackFormFactor.Mobile,
                ),
            )
        }
    }
```

- [ ] **Step 5: Remove the obsolete mobile media-spec helper**

Delete the entire local function:

```kotlin
    fun trackSelectionMediaSpec(state: PlayerViewModel.PlayerUiState): VideoPlayerMediaSpec? {
        mountedVideoMediaSpec?.let { mountedSpec ->
            return mountedSpec.copy(
                subtitles = state.subtitleTracks,
                title = state.title.ifBlank { null },
                subtitle = state.subtitle.ifBlank { null },
                artworkUrl = state.artworkUrl,
                startPositionSeconds = state.startPosition,
            )
        }
        val streamUrl = state.streamUrl ?: return null
        val playMethod = state.playMethod ?: return null
        return VideoPlayerMediaSpec(
            streamUrl = streamUrl,
            playMethod = playMethod,
            serverUrl = state.serverUrl,
            subtitles = state.subtitleTracks,
            title = state.title.ifBlank { null },
            subtitle = state.subtitle.ifBlank { null },
            artworkUrl = state.artworkUrl,
            startPositionSeconds = state.startPosition,
        )
    }
```

- [ ] **Step 6: Route track presets through the backend**

In the capability-aware track selection `LaunchedEffect`, replace:

```kotlin
        val controller = mediaController ?: return@LaunchedEffect
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
        val backend = videoBackend ?: return@LaunchedEffect
        backend.applyTrackSelection(
            audioCaps = audioCaps,
            displayHdr = if (hdrEnabled) displayHdr else com.continuum.app.model.playback.HdrCapabilities(),
            preferredAudioLanguage = uiState.preferredAudioLanguage,
            preferredTextLanguage = uiState.preferredTextLanguage,
            hdrEnabled = hdrEnabled,
        )
```

Add `videoBackend` to that effect's key list.

- [ ] **Step 7: Clear no obsolete mounted-spec state**

In the load-content `LaunchedEffect`, remove this line:

```kotlin
        mountedVideoMediaSpec = null
```

- [ ] **Step 8: Mount initial mobile media through the backend**

Change the setup effect header from:

```kotlin
    LaunchedEffect(mediaController, uiState.streamUrl, uiState.playMethod, uiState.startPosition) {
        val controller = mediaController ?: return@LaunchedEffect
```

to:

```kotlin
    LaunchedEffect(videoBackend, uiState.streamUrl, uiState.playMethod, uiState.startPosition) {
        val backend = videoBackend ?: return@LaunchedEffect
```

Replace:

```kotlin
        mountedVideoMediaSpec = mediaSpec
        mountVideoMedia(player = controller, playerFactory = playerFactory, spec = mediaSpec)
```

with:

```kotlin
        backend.mount(mediaSpec)
```

- [ ] **Step 9: Refresh mobile subtitles through the backend**

Change the subtitle refresh effect header from:

```kotlin
    LaunchedEffect(mediaController, uiState.subtitleRefreshNonce) {
```

to:

```kotlin
    LaunchedEffect(videoBackend, uiState.subtitleRefreshNonce) {
```

Replace:

```kotlin
        val controller = mediaController ?: return@LaunchedEffect
```

with:

```kotlin
        val backend = videoBackend ?: return@LaunchedEffect
```

Replace:

```kotlin
        mountedVideoMediaSpec = mediaSpec
        refreshMountedVideoMedia(player = controller, playerFactory = playerFactory, spec = mediaSpec)
```

with:

```kotlin
        backend.refresh(mediaSpec)
```

- [ ] **Step 10: Reselect mobile mounted subtitles through the backend**

Inside `onTracksChanged`, replace:

```kotlin
                    trackSelectionCoordinator.selectMountedSubtitle(
                        player = controller,
                        subtitles = liveState.subtitleTracks,
                        selectedIndex = liveState.selectedSubtitleIndex,
                    )
```

with:

```kotlin
                    videoBackend?.selectMountedSubtitle(
                        subtitles = liveState.subtitleTracks,
                        selectedIndex = liveState.selectedSubtitleIndex,
                    )
```

- [ ] **Step 11: Select mobile subtitles through the backend**

Change the subtitle selection effect header from:

```kotlin
    LaunchedEffect(mediaController, uiState.subtitleTracks, uiState.selectedSubtitleIndex) {
        val controller = mediaController ?: return@LaunchedEffect
        val mediaSpec = trackSelectionMediaSpec(uiState) ?: return@LaunchedEffect
        trackSelectionCoordinator.selectSubtitle(
            player = controller,
            playerFactory = playerFactory,
            mediaSpec = mediaSpec,
            selectedTrack = subtitleTrackEntry(uiState.subtitleTracks, uiState.selectedSubtitleIndex),
        )
    }
```

to:

```kotlin
    LaunchedEffect(videoBackend, uiState.subtitleTracks, uiState.selectedSubtitleIndex) {
        val backend = videoBackend ?: return@LaunchedEffect
        backend.selectSubtitle(
            subtitleTrackEntry(uiState.subtitleTracks, uiState.selectedSubtitleIndex),
        )
    }
```

- [ ] **Step 12: Run mobile tests and compile**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.player.PlayerScreenStartPositionTest'
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: PASS and compile succeeds.

- [ ] **Step 13: Commit Task 2**

Run:

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreenStartPositionTest.kt
git commit -m "Route mobile video playback through backend"
```

---

### Task 3: TV Player Backend Migration

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`
- Modify: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreenStartPositionTest.kt`

- [ ] **Step 1: Update the TV source guard first**

Modify `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreenStartPositionTest.kt`.

Replace `tvPlayerDelegatesInitialMountToSharedHelper` with:

```kotlin
    @Test
    fun tvPlayerDelegatesInitialMountToVideoBackend() {
        assertTrue(
            source.contains("VideoPlayerMediaSpec("),
            "TV player must build the shared video media spec",
        )
        assertTrue(
            source.contains("VideoPlaybackBackendFactory"),
            "TV player must inject the shared backend factory",
        )
        assertTrue(
            source.contains("backend.mount(mediaSpec"),
            "TV player must mount through the backend",
        )
        assertTrue(
            !source.contains("mountVideoMedia("),
            "TV player must not call the raw Media3 mounter directly",
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
```

Replace `tvPlayerDelegatesSubtitleRefreshToSharedHelper` with:

```kotlin
    @Test
    fun tvPlayerDelegatesSubtitleRefreshToVideoBackend() {
        assertTrue(
            source.contains("backend.refresh(mediaSpec"),
            "TV subtitle refresh must use the backend refresh path",
        )
        assertTrue(
            !source.contains("refreshMountedVideoMedia("),
            "TV subtitle refresh must not call the raw Media3 refresh helper directly",
        )
    }
```

Add:

```kotlin
    @Test
    fun tvPlayerRoutesTrackSelectionThroughBackend() {
        assertTrue(
            source.contains("videoBackend?.selectSubtitle("),
            "TV subtitle selection must go through the mounted backend",
        )
        assertTrue(
            source.contains("videoBackend?.selectAudioTrack("),
            "TV audio selection must go through the mounted backend",
        )
        assertTrue(
            !source.contains("trackSelectionCoordinator.selectSubtitle("),
            "TV player must not call the subtitle coordinator directly",
        )
        assertTrue(
            !source.contains("trackSelectionCoordinator.selectAudioTrack("),
            "TV player must not call the audio coordinator directly",
        )
    }
```

- [ ] **Step 2: Run the TV source guard and verify it fails**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.player.TvPlayerScreenStartPositionTest'
```

Expected: FAIL because `TvPlayerScreen.kt` still calls raw mounters/coordinators.

- [ ] **Step 3: Update TV imports and composable parameters**

Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`.

Remove these imports:

```kotlin
import com.continuum.app.common.player.AudioTrackManager
import com.continuum.app.common.player.ContinuumPlayerFactory
import com.continuum.app.common.player.video.VideoTrackSelectionCoordinator
import com.continuum.app.common.player.mountVideoMedia
import com.continuum.app.common.player.refreshMountedVideoMedia
```

Add these imports:

```kotlin
import com.continuum.app.common.player.backend.VideoPlaybackBackendFactory
import com.continuum.app.common.player.backend.VideoPlaybackBackendRequest
import com.continuum.app.common.player.backend.VideoPlaybackFormFactor
```

Replace these parameters:

```kotlin
    playerFactory: ContinuumPlayerFactory = koinInject(),
    subtitleManager: SubtitleManager = koinInject(),
    audioTrackManager: AudioTrackManager = koinInject(),
```

with:

```kotlin
    backendFactory: VideoPlaybackBackendFactory = koinInject(),
    subtitleManager: SubtitleManager = koinInject(),
```

Remove:

```kotlin
    val trackSelectionCoordinator = remember(subtitleManager) {
        VideoTrackSelectionCoordinator(subtitleManager)
    }
```

- [ ] **Step 4: Add the remembered TV backend**

After `var mediaController by remember { mutableStateOf<MediaController?>(null) }`, add:

```kotlin
    val videoBackend = remember(mediaController, backendFactory, contentId, preferredFileId) {
        mediaController?.let { controller ->
            backendFactory.create(
                player = controller,
                request = VideoPlaybackBackendRequest(
                    contentId = contentId,
                    fileId = preferredFileId,
                    formFactor = VideoPlaybackFormFactor.Tv,
                ),
            )
        }
    }
```

- [ ] **Step 5: Route immediate TV subtitle selection through the backend**

In `applyTvSubtitleSelection`, delete this line:

```kotlin
            val mediaSpec = tvTrackSelectionMediaSpec(state) ?: return@let
```

Then replace:

```kotlin
            if (trackSelectionCoordinator.selectSubtitle(
                    player = controller,
                    playerFactory = playerFactory,
                    mediaSpec = mediaSpec,
                    selectedTrack = selectedTrack,
                )
            ) {
```

with:

```kotlin
            if (videoBackend?.selectSubtitle(selectedTrack) == true) {
```

- [ ] **Step 6: Route TV track presets through the backend**

In the capability-aware track selection `LaunchedEffect`, add `videoBackend` to the key list.

Replace:

```kotlin
        val controller = mediaController ?: return@LaunchedEffect
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
        val backend = videoBackend ?: return@LaunchedEffect
        backend.applyTrackSelection(
            audioCaps = audioCaps,
            displayHdr = displayHdr,
            preferredAudioLanguage = state.preferredAudioLanguage,
            preferredTextLanguage = state.preferredTextLanguage,
            hdrEnabled = hdrEnabled,
        )
```

- [ ] **Step 7: Mount initial TV media through the backend**

Change the prepare effect header from:

```kotlin
    LaunchedEffect(mediaController, state.streamUrl, state.sessionId) {
        val controller = mediaController ?: return@LaunchedEffect
```

to:

```kotlin
    LaunchedEffect(videoBackend, state.streamUrl, state.sessionId) {
        val backend = videoBackend ?: return@LaunchedEffect
```

Replace:

```kotlin
        mountVideoMedia(player = controller, playerFactory = playerFactory, spec = mediaSpec)
```

with:

```kotlin
        backend.mount(mediaSpec)
```

- [ ] **Step 8: Refresh TV subtitles through the backend**

Change the subtitle refresh effect header from:

```kotlin
    LaunchedEffect(mediaController, state.subtitleRefreshNonce) {
```

to:

```kotlin
    LaunchedEffect(videoBackend, state.subtitleRefreshNonce) {
```

Replace:

```kotlin
        val controller = mediaController ?: return@LaunchedEffect
```

with:

```kotlin
        val backend = videoBackend ?: return@LaunchedEffect
```

Replace:

```kotlin
        refreshMountedVideoMedia(player = controller, playerFactory = playerFactory, spec = mediaSpec)
```

with:

```kotlin
        backend.refresh(mediaSpec)
```

- [ ] **Step 9: Route subtitle request stream through the backend**

Change the subtitle request stream effect header from:

```kotlin
    LaunchedEffect(mediaController) {
        val controller = mediaController ?: return@LaunchedEffect
```

to:

```kotlin
    LaunchedEffect(videoBackend) {
        val backend = videoBackend ?: return@LaunchedEffect
```

In the `viewModel.subtitleSelectRequests.collect` block, delete:

```kotlin
            val mediaSpec = tvTrackSelectionMediaSpec(viewModel.uiState.value) ?: return@collect
```

Replace:

```kotlin
            if (trackSelectionCoordinator.selectSubtitle(
                    player = controller,
                    playerFactory = playerFactory,
                    mediaSpec = mediaSpec,
                    selectedTrack = selectedTrack,
                )
            ) {
```

with:

```kotlin
            if (backend.selectSubtitle(selectedTrack)) {
```

- [ ] **Step 10: Route TV audio selection through the backend**

Inside the `TvPlayerHud` `onSelectAudio` callback, replace:

```kotlin
                                    mediaController?.let {
                                        trackSelectionCoordinator.selectAudioTrack(
                                            player = it,
                                            audioTrackManager = audioTrackManager,
                                            selectedTrack = selectedTrack,
                                        )
                                    }
```

with:

```kotlin
                                    videoBackend?.selectAudioTrack(selectedTrack)
```

- [ ] **Step 11: Remove obsolete TV track-selection helper if unused**

Run:

```bash
rg -n "tvTrackSelectionMediaSpec" androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt
```

If the only remaining match is the helper declaration, delete the entire `tvTrackSelectionMediaSpec` function from `TvPlayerScreen.kt`.

Expected after deletion:

```bash
rg -n "tvTrackSelectionMediaSpec" androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt
```

prints no matches.

- [ ] **Step 12: Run TV tests and compile**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.player.TvPlayerScreenStartPositionTest'
./gradlew :androidTvApp:compileDebugKotlinAndroid
```

Expected: PASS and compile succeeds.

- [ ] **Step 13: Commit Task 3**

Run:

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreenStartPositionTest.kt
git commit -m "Route TV video playback through backend"
```

---

### Task 4: TV Backend Capability Diagnostics

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerBackendCapabilitiesSourceTest.kt`

- [ ] **Step 1: Write the failing diagnostics source test**

Create `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerBackendCapabilitiesSourceTest.kt`:

```kotlin
package com.continuum.app.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertTrue

class TvPlayerBackendCapabilitiesSourceTest {
    private val viewModelSource = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt",
    ).readText()
    private val hudSource = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt",
    ).readText()
    private val screenSource = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt",
    ).readText()

    @Test
    fun backendCapabilitiesReachStatsHud() {
        assertTrue(viewModelSource.contains("import com.continuum.app.common.player.backend.VideoBackendCapabilities"))
        assertTrue(viewModelSource.contains("val backendKind: String? = null"))
        assertTrue(viewModelSource.contains("val backendRoute: String? = null"))
        assertTrue(viewModelSource.contains("val subtitleRendering: String? = null"))
        assertTrue(viewModelSource.contains("fun onBackendCapabilities(capabilities: VideoBackendCapabilities)"))
        assertTrue(hudSource.contains("backendKind?.let { add(\"Backend\" to it) }"))
        assertTrue(hudSource.contains("backendRoute?.let { add(\"Route\" to it) }"))
        assertTrue(hudSource.contains("subtitleRendering?.let { add(\"Subtitles\" to it) }"))
        assertTrue(screenSource.contains("viewModel.onBackendCapabilities(backend.capabilities)"))
    }
}
```

- [ ] **Step 2: Run the diagnostics source test and verify it fails**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.player.TvPlayerBackendCapabilitiesSourceTest'
```

Expected: FAIL until the stats fields and HUD rows are added.

- [ ] **Step 3: Add capability fields to the TV stats snapshot**

Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt`.

Add import:

```kotlin
import com.continuum.app.common.player.backend.VideoBackendCapabilities
```

Add these fields to `PlayerStatsSnapshot` before `videoDecoderName`:

```kotlin
    val backendKind: String? = null,
    val backendRoute: String? = null,
    val subtitleRendering: String? = null,
```

Add this method inside `TvPlayerViewModel` near the other player-state mutation methods:

```kotlin
    fun onBackendCapabilities(capabilities: VideoBackendCapabilities) {
        _uiState.update { state ->
            state.copy(
                stats = state.stats.copy(
                    backendKind = capabilities.backendKind.name,
                    backendRoute = capabilities.route.name,
                    subtitleRendering = capabilities.subtitleRendering.name,
                ),
            )
        }
    }
```

- [ ] **Step 4: Render capability rows in the TV HUD stats pane**

Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt`.

At the top of `private fun PlayerStatsSnapshot.hudRows()`, before `videoCodec?.let`, add:

```kotlin
    backendKind?.let { add("Backend" to it) }
    backendRoute?.let { add("Route" to it) }
    subtitleRendering?.let { add("Subtitles" to it) }
```

- [ ] **Step 5: Connect the TV screen diagnostics hook**

Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`.

Immediately after the remembered `videoBackend` block, add:

```kotlin
    LaunchedEffect(videoBackend) {
        videoBackend?.let { backend ->
            viewModel.onBackendCapabilities(backend.capabilities)
        }
    }
```

- [ ] **Step 6: Run diagnostics tests and compile**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.player.TvPlayerBackendCapabilitiesSourceTest'
./gradlew :androidTvApp:compileDebugKotlinAndroid
```

Expected: PASS and compile succeeds.

- [ ] **Step 7: Commit Task 4**

Run:

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerBackendCapabilitiesSourceTest.kt
git commit -m "Surface video backend capabilities in TV stats"
```

---

### Task 5: Slice Verification

**Files:**
- Verify all files touched by Tasks 1-4.
- No code changes should be needed in this task.

- [ ] **Step 1: Run focused unit tests**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.backend.*'
./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.di.AndroidVideoBackendDiSourceTest' --tests 'com.continuum.app.android.ui.screens.player.PlayerScreenStartPositionTest'
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.di.AndroidTvVideoBackendDiSourceTest' --tests 'com.continuum.app.tv.ui.screens.player.TvPlayerScreenStartPositionTest' --tests 'com.continuum.app.tv.ui.screens.player.TvPlayerBackendCapabilitiesSourceTest'
```

Expected: PASS.

- [ ] **Step 2: Compile both apps**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid :androidTvApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run broad player-related tests if focused checks pass**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidTvApp:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. If unrelated pre-existing tests fail, record the failing test names and keep the focused checks from Step 1 as the minimum proof for this slice.

- [ ] **Step 4: Inspect for forbidden dependencies and raw call-site regressions**

Run:

```bash
rg -n "mpv|libass|JitPack" build.gradle.kts settings.gradle.kts gradle/libs.versions.toml android-shared androidApp androidTvApp
rg -n "mountVideoMedia\\(|refreshMountedVideoMedia\\(|VideoTrackSelectionCoordinator\\(" androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt
```

Expected:

- First command prints only pre-existing documentation or no matches; it must not show new dependency declarations.
- Second command prints no matches.

- [ ] **Step 5: Commit verification notes only if code changed**

If Step 1-4 required code fixes, commit them:

```bash
git add android-shared androidApp androidTvApp
git commit -m "Verify video backend boundary migration"
```

If no code changed, do not create an empty commit.
