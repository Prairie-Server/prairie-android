# Shared Video Player Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Android mobile and Android TV video playback onto one shared Media3 video playback stack while preserving separate mobile touch UI and TV remote-first UI. The first shipped outcome must fix TV resume/subtitle mounting by using the same shared media mounting path already proven on mobile.

**Architecture:** Put shared video playback behavior in `android-shared`: media item construction, start-position mounting, subtitle refresh remounting, route/start-position parsing helpers, and gradually the playback/session/track state machines. Mobile and TV screens remain thin platform surfaces that render controls and forward user intent into shared video-player helpers.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Media3, Koin, Kotlin coroutines, existing `:android-shared`, `:androidApp`, `:androidTvApp`, Gradle unit tests/source guards, Pixel and Shield device verification.

---

## File Structure

Shared Android video core:

```text
android-shared/src/androidMain/kotlin/com/continuum/app/common/player/
  ContinuumPlayerFactory.kt
  PlaybackSessionManager.kt
  SubtitleManager.kt
  VideoPlayerMediaSpec.kt              # new
  VideoPlayerMediaMounter.kt           # new

android-shared/src/androidMain/kotlin/com/continuum/app/common/player/video/
  VideoPlayerRouteArgs.kt              # new
  VideoPlayerUiState.kt                # new
  VideoPlaybackSessionCoordinator.kt   # new after mount migration
  VideoTrackSelectionCoordinator.kt    # new after session migration
```

Shared Android tests:

```text
android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/
  VideoPlayerMediaSpecTest.kt          # new
  VideoPlayerMediaMounterSourceTest.kt # new source guard for Media3 call ordering

android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/video/
  VideoPlayerRouteArgsTest.kt          # new
  VideoPlayerUiStateTest.kt            # new
```

Mobile player files:

```text
androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/
  PlayerScreen.kt
  PlayerViewModel.kt

androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/
  PlayerScreenStartPositionTest.kt
```

TV player and navigation files:

```text
androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/
  TvRoute.kt
  TvAppNavigation.kt

androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/
  TvItemDetailScreen.kt

androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/
  TvPlayerScreen.kt
  TvPlayerViewModel.kt

androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/navigation/
  TvPlayerRouteTest.kt                 # new

androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/
  TvPlayerScreenStartPositionTest.kt   # new
```

---

## Implementation

### 1. Capture Current State

- [ ] Run repository status and note unrelated local changes without reverting them.

```bash
cd /Users/jimcole/projects/prairie/prairie-android
git status --short
git branch --show-current
```

Expected output:

```text
feature/android-parity-and-media-surfaces
```

- [ ] Re-open the current mobile and TV player setup blocks before editing.

```bash
rg -n "setMediaItem|prepare\\(|startPosition|resumePosition" \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvRoute.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvAppNavigation.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailScreen.kt
```

Expected observation:

```text
Mobile uses controller.setMediaItem(mediaItem, startMs) before prepare().
TV still uses controller.setMediaItem(mediaItem), seekTo(startMs), prepare().
TV route does not yet carry resumePosition.
TV detail computes resumePositionSeconds() but onPlay only passes contentId/fileId/type.
```

---

### 2. Add Shared Media Spec

- [ ] Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/VideoPlayerMediaSpecTest.kt`.

```kotlin
package com.continuum.app.common.player

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoPlayerMediaSpecTest {
    @Test
    fun startPositionMsConvertsSecondsToMilliseconds() {
        val spec = baseSpec(startPositionSeconds = 31.427)

        assertEquals(31_427L, spec.startPositionMs)
    }

    @Test
    fun startPositionMsClampsNegativeValuesToZero() {
        val spec = baseSpec(startPositionSeconds = -42.0)

        assertEquals(0L, spec.startPositionMs)
    }

    @Test
    fun startPositionMsClampsInvalidValuesToZero() {
        assertEquals(0L, baseSpec(startPositionSeconds = Double.NaN).startPositionMs)
        assertEquals(0L, baseSpec(startPositionSeconds = Double.POSITIVE_INFINITY).startPositionMs)
    }

    private fun baseSpec(startPositionSeconds: Double) = VideoPlayerMediaSpec(
        streamUrl = "https://lib.strm.cafe/api/stream/movie",
        playMethod = PlayMethod.DirectPlay,
        serverUrl = "https://lib.strm.cafe",
        title = "Michael",
        subtitle = "Movie",
        artworkUrl = "https://lib.strm.cafe/poster.jpg",
        startPositionSeconds = startPositionSeconds,
    )
}
```

- [ ] Run the new test and confirm it fails because `VideoPlayerMediaSpec` does not exist.

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.VideoPlayerMediaSpecTest'
```

Expected failure:

```text
Unresolved reference 'VideoPlayerMediaSpec'
```

- [ ] Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/VideoPlayerMediaSpec.kt`.

```kotlin
package com.continuum.app.common.player

data class VideoPlayerMediaSpec(
    val streamUrl: String,
    val playMethod: PlayMethod,
    val serverUrl: String,
    val subtitles: List<PlayerSubtitleInfo> = emptyList(),
    val title: String? = null,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val startPositionSeconds: Double = 0.0,
) {
    val startPositionMs: Long
        get() {
            val seconds = if (startPositionSeconds.isFinite()) startPositionSeconds else 0.0
            return (seconds * 1000.0).toLong().coerceAtLeast(0L)
        }
}
```

- [ ] Run the focused shared test again.

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.VideoPlayerMediaSpecTest'
```

Expected output:

```text
BUILD SUCCESSFUL
```

---

### 3. Add Shared Media3 Mount Helper

- [ ] Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/VideoPlayerMediaMounterSourceTest.kt`.

```kotlin
package com.continuum.app.common.player

import kotlin.test.Test
import kotlin.test.assertTrue

class VideoPlayerMediaMounterSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/VideoPlayerMediaMounter.kt",
    ).readText()

    @Test
    fun mountUsesMedia3StartPositionOverloadBeforePrepare() {
        val setIndex = source.indexOf(".setMediaItem(mediaItem, startPositionMs")
        val prepareIndex = source.indexOf(".prepare()")
        val seekIndex = source.indexOf(".seekTo(")

        assertTrue(setIndex >= 0, "mount must call setMediaItem(mediaItem, startPositionMs)")
        assertTrue(prepareIndex > setIndex, "prepare must happen after mounted start position")
        assertTrue(seekIndex < 0, "mount must not use post-mount seekTo for initial resume")
    }

    @Test
    fun refreshPreservesCurrentPositionAndPlayingState() {
        assertTrue(
            source.contains("refreshMountedVideoMedia"),
            "subtitle refresh must use a shared helper",
        )
        assertTrue(
            source.contains("val resumePositionMs = player.currentPosition"),
            "refresh must preserve current player position",
        )
        assertTrue(
            source.contains("val wasPlaying = player.playWhenReady"),
            "refresh must preserve playWhenReady",
        )
        assertTrue(
            source.contains(".setMediaItem(mediaItem, resumePositionMs)"),
            "refresh must remount at the preserved position",
        )
    }
}
```

- [ ] Run the source test and confirm it fails because the helper file does not exist.

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.VideoPlayerMediaMounterSourceTest'
```

Expected failure:

```text
No such file or directory
```

- [ ] Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/VideoPlayerMediaMounter.kt`.

```kotlin
package com.continuum.app.common.player

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
fun mountVideoMedia(
    player: Player,
    playerFactory: ContinuumPlayerFactory,
    spec: VideoPlayerMediaSpec,
    startPositionMs: Long = spec.startPositionMs,
    playWhenReady: Boolean = true,
) {
    val mediaItem = playerFactory.buildMediaItem(
        streamUrl = spec.streamUrl,
        playMethod = spec.playMethod,
        serverUrl = spec.serverUrl,
        subtitles = spec.subtitles,
        title = spec.title,
        subtitle = spec.subtitle,
        artworkUrl = spec.artworkUrl,
    )
    player.setMediaItem(mediaItem, startPositionMs.coerceAtLeast(0L))
    player.prepare()
    player.playWhenReady = playWhenReady
}

@OptIn(UnstableApi::class)
fun refreshMountedVideoMedia(
    player: Player,
    playerFactory: ContinuumPlayerFactory,
    spec: VideoPlayerMediaSpec,
) {
    val resumePositionMs = player.currentPosition
    val wasPlaying = player.playWhenReady
    val mediaItem = playerFactory.buildMediaItem(
        streamUrl = spec.streamUrl,
        playMethod = spec.playMethod,
        serverUrl = spec.serverUrl,
        subtitles = spec.subtitles,
        title = spec.title,
        subtitle = spec.subtitle,
        artworkUrl = spec.artworkUrl,
    )
    player.setMediaItem(mediaItem, resumePositionMs)
    player.prepare()
    player.playWhenReady = wasPlaying
}
```

- [ ] Run shared tests.

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.VideoPlayerMediaSpecTest' --tests 'com.continuum.app.common.player.VideoPlayerMediaMounterSourceTest'
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Commit the shared mount primitive.

```bash
git status --short
git add \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/VideoPlayerMediaSpec.kt \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/VideoPlayerMediaMounter.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/VideoPlayerMediaSpecTest.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/VideoPlayerMediaMounterSourceTest.kt
git commit -m "Add shared video media mounting helper"
```

Expected output: commit succeeds with message `Add shared video media mounting helper`.

---

### 4. Move Mobile Media Mounting To Shared Helper

- [ ] Update `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt` imports.

```kotlin
import com.continuum.app.common.player.VideoPlayerMediaSpec
import com.continuum.app.common.player.mountVideoMedia
import com.continuum.app.common.player.refreshMountedVideoMedia
```

- [ ] Replace the existing mobile setup block that directly builds a media item and calls `controller.setMediaItem(mediaItem, startMs)` with this shared mount call. Keep the existing local-download resolution and pass the resolved stream URL into the spec.

```kotlin
val mediaSpec = VideoPlayerMediaSpec(
    streamUrl = streamUrl,
    playMethod = uiState.playMethod,
    serverUrl = serverUrl,
    subtitles = selectedSubtitles,
    title = uiState.title,
    subtitle = uiState.subtitle,
    artworkUrl = uiState.artworkUrl,
    startPositionSeconds = uiState.startPosition,
)
mountVideoMedia(
    player = controller,
    playerFactory = playerFactory,
    spec = mediaSpec,
)
```

- [ ] Replace the mobile subtitle-refresh remount block with the shared refresh helper.

```kotlin
refreshMountedVideoMedia(
    player = controller,
    playerFactory = playerFactory,
    spec = mediaSpec.copy(subtitles = refreshedSubtitles),
)
```

- [ ] Update `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreenStartPositionTest.kt` so the test asserts mobile delegates to the shared mount helper and no longer owns start-position call ordering.

```kotlin
package com.continuum.app.android.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertTrue

class PlayerScreenStartPositionTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt",
    ).readText()

    @Test
    fun playerScreenDelegatesInitialMountToSharedHelper() {
        assertTrue(
            source.contains("VideoPlayerMediaSpec("),
            "mobile player must build the shared video media spec",
        )
        assertTrue(
            source.contains("mountVideoMedia("),
            "mobile player must use the shared mount helper",
        )
        assertTrue(
            !source.contains("controller.setMediaItem(mediaItem, startMs)"),
            "mobile player must not duplicate initial Media3 mount ordering",
        )
    }

    @Test
    fun playerScreenDelegatesSubtitleRefreshToSharedHelper() {
        assertTrue(
            source.contains("refreshMountedVideoMedia("),
            "mobile subtitle refresh must use the shared refresh helper",
        )
    }
}
```

- [ ] Run focused mobile tests.

```bash
./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.player.PlayerScreenStartPositionTest'
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Run the mobile app build.

```bash
./gradlew :androidApp:assembleDebug
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Commit mobile shared mounting.

```bash
git status --short
git add \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreenStartPositionTest.kt
git commit -m "Use shared video media mounting on mobile"
```

Expected output: commit succeeds with message `Use shared video media mounting on mobile`.

---

### 5. Add Shared Video Route Args

- [ ] Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/video/VideoPlayerRouteArgsTest.kt`.

```kotlin
package com.continuum.app.common.player.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoPlayerRouteArgsTest {
    @Test
    fun parseResumePositionAcceptsFinitePositiveSeconds() {
        assertEquals(31.5, VideoPlayerRouteArgs.parseResumePosition("31.5"))
    }

    @Test
    fun parseResumePositionRejectsMissingOrInvalidValues() {
        assertNull(VideoPlayerRouteArgs.parseResumePosition(null))
        assertNull(VideoPlayerRouteArgs.parseResumePosition(""))
        assertNull(VideoPlayerRouteArgs.parseResumePosition("-1"))
        assertNull(VideoPlayerRouteArgs.parseResumePosition("NaN"))
        assertNull(VideoPlayerRouteArgs.parseResumePosition("Infinity"))
        assertNull(VideoPlayerRouteArgs.parseResumePosition("abc"))
    }

    @Test
    fun encodeResumePositionUsesStableDecimalText() {
        assertEquals("31.5", VideoPlayerRouteArgs.encodeResumePosition(31.5))
        assertNull(VideoPlayerRouteArgs.encodeResumePosition(null))
        assertNull(VideoPlayerRouteArgs.encodeResumePosition(Double.NaN))
        assertNull(VideoPlayerRouteArgs.encodeResumePosition(-1.0))
    }
}
```

- [ ] Run the test and confirm it fails because `VideoPlayerRouteArgs` does not exist.

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.video.VideoPlayerRouteArgsTest'
```

Expected failure:

```text
Unresolved reference 'VideoPlayerRouteArgs'
```

- [ ] Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/video/VideoPlayerRouteArgs.kt`.

```kotlin
package com.continuum.app.common.player.video

object VideoPlayerRouteArgs {
    const val RESUME_POSITION = "resumePosition"

    fun parseResumePosition(value: String?): Double? {
        val parsed = value?.toDoubleOrNull() ?: return null
        return parsed.takeIf { it.isFinite() && it >= 0.0 }
    }

    fun encodeResumePosition(value: Double?): String? {
        val valid = value?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        return valid.toString()
    }
}
```

- [ ] Run shared route args test.

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.video.VideoPlayerRouteArgsTest'
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Commit shared route args.

```bash
git status --short
git add \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/video/VideoPlayerRouteArgs.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/video/VideoPlayerRouteArgsTest.kt
git commit -m "Add shared video player route args"
```

Expected output: commit succeeds with message `Add shared video player route args`.

---

### 6. Move TV Initial Mounting To Shared Helper

- [ ] Update `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt` imports.

```kotlin
import com.continuum.app.common.player.VideoPlayerMediaSpec
import com.continuum.app.common.player.mountVideoMedia
import com.continuum.app.common.player.refreshMountedVideoMedia
```

- [ ] Replace the TV setup block that currently calls `controller.setMediaItem(mediaItem)`, `seekTo(startMs)`, and `prepare()` with the shared mount helper.

```kotlin
val mediaSpec = VideoPlayerMediaSpec(
    streamUrl = state.streamUrl,
    playMethod = state.playMethod,
    serverUrl = serverUrl,
    subtitles = selectedSubtitles,
    title = state.title,
    subtitle = state.subtitle,
    artworkUrl = state.artworkUrl,
    startPositionSeconds = state.startPosition,
)
mountVideoMedia(
    player = controller,
    playerFactory = playerFactory,
    spec = mediaSpec,
)
```

- [ ] Replace the TV subtitle-refresh remount block with the shared refresh helper.

```kotlin
refreshMountedVideoMedia(
    player = controller,
    playerFactory = playerFactory,
    spec = mediaSpec.copy(subtitles = refreshedSubtitles),
)
```

- [ ] Create `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreenStartPositionTest.kt`.

```kotlin
package com.continuum.app.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertTrue

class TvPlayerScreenStartPositionTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt",
    ).readText()

    @Test
    fun tvPlayerDelegatesInitialMountToSharedHelper() {
        assertTrue(
            source.contains("VideoPlayerMediaSpec("),
            "TV player must build the shared video media spec",
        )
        assertTrue(
            source.contains("mountVideoMedia("),
            "TV player must use the shared mount helper",
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
    fun tvPlayerDelegatesSubtitleRefreshToSharedHelper() {
        assertTrue(
            source.contains("refreshMountedVideoMedia("),
            "TV subtitle refresh must use the shared refresh helper",
        )
    }
}
```

- [ ] Run focused TV test.

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.player.TvPlayerScreenStartPositionTest'
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Run the TV app build.

```bash
./gradlew :androidTvApp:assembleDebug
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Commit TV shared mounting.

```bash
git status --short
git add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreenStartPositionTest.kt
git commit -m "Use shared video media mounting on TV"
```

Expected output: commit succeeds with message `Use shared video media mounting on TV`.

---

### 7. Pass Resume Position Through TV Navigation

- [ ] Add route coverage in `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/navigation/TvPlayerRouteTest.kt`.

```kotlin
package com.continuum.app.tv.ui.navigation

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class TvPlayerRouteTest {
    @Test
    fun playerRouteIncludesResumePositionWhenPresent() {
        val route = TvRoute.Player(
            contentId = "movie-123",
            fileId = 44,
            roomId = "room one",
            resumePositionSeconds = 1887.25,
        ).route

        assertContains(route, "player/movie-123")
        assertContains(route, "fileId=44")
        assertContains(route, "roomId=room%20one")
        assertContains(route, "resumePosition=1887.25")
    }

    @Test
    fun playerRouteOmitsInvalidResumePosition() {
        val route = TvRoute.Player(
            contentId = "movie-123",
            resumePositionSeconds = Double.NaN,
        ).route

        assertFalse(route.contains("resumePosition="))
    }
}
```

- [ ] Run the route test and confirm it fails because TV route does not accept `resumePositionSeconds`.

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.navigation.TvPlayerRouteTest'
```

Expected failure:

```text
Cannot find a parameter with this name: resumePositionSeconds
```

- [ ] Update `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvRoute.kt`.

```kotlin
import com.continuum.app.common.player.video.VideoPlayerRouteArgs
```

Update `TvRoute.Player`:

```kotlin
data class Player(
    val contentId: String,
    val fileId: Int? = null,
    val roomId: String? = null,
    val resumePositionSeconds: Double? = null,
) : TvRoute(
    buildString {
        append("player/$contentId")
        val query = buildList {
            if (fileId != null) add("fileId=$fileId")
            if (roomId != null) add("roomId=${roomId.routeEncode()}")
            VideoPlayerRouteArgs.encodeResumePosition(resumePositionSeconds)
                ?.let { add("${VideoPlayerRouteArgs.RESUME_POSITION}=$it") }
        }
        if (query.isNotEmpty()) append("?").append(query.joinToString("&"))
    },
) {
    companion object {
        const val ROUTE = "player/{contentId}?fileId={fileId}&roomId={roomId}&resumePosition={resumePosition}"
        const val ARG_CONTENT_ID = "contentId"
        const val ARG_FILE_ID = "fileId"
        const val ARG_ROOM_ID = "roomId"
        const val ARG_RESUME_POSITION = "resumePosition"
    }
}
```

- [ ] Update `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvAppNavigation.kt` player route arguments.

```kotlin
navArgument(TvRoute.Player.ARG_RESUME_POSITION) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}
```

- [ ] Parse and pass the route value into `TvPlayerScreen`.

```kotlin
val resumePositionOverride = VideoPlayerRouteArgs.parseResumePosition(
    backStackEntry.arguments?.getString(TvRoute.Player.ARG_RESUME_POSITION),
)
TvPlayerScreen(
    contentId = contentId,
    preferredFileId = preferredFileId,
    roomId = roomId,
    resumePositionOverride = resumePositionOverride,
)
```

- [ ] Update the `TvItemDetailScreen` play callback signature so detail can pass its computed resume position into navigation.

```kotlin
onPlay: (contentId: String, fileId: Int?, itemType: String?, resumePositionSeconds: Double?) -> Unit,
```

Use the existing detail helper:

```kotlin
val resumePosition = remember(detail.userData) { detail.resumePositionSeconds() }
```

And call:

```kotlin
onClick = { onPlay(detail.contentId, selectedFileId, detail.type, resumePosition) }
```

- [ ] Update the `TvItemDetailScreen` call site in `TvAppNavigation.kt`.

```kotlin
onPlay = { playContentId, fileId, itemType, resumePositionSeconds ->
    navController.navigate(
        tvPlayDestinationFor(
            contentId = playContentId,
            fileId = fileId,
            itemType = itemType,
            resumePositionSeconds = resumePositionSeconds,
        ).route,
    )
}
```

- [ ] Update `tvPlayDestinationFor` to carry resume position only for video playback.

```kotlin
private fun tvPlayDestinationFor(
    contentId: String,
    fileId: Int?,
    itemType: String?,
    resumePositionSeconds: Double? = null,
): TvRoute {
    return when {
        itemType.equals("audiobook", ignoreCase = true) -> TvRoute.AudiobookPlayer(contentId)
        else -> TvRoute.Player(
            contentId = contentId,
            fileId = fileId,
            resumePositionSeconds = resumePositionSeconds,
        )
    }
}
```

- [ ] Update every Watch Together call site that constructs `TvRoute.Player` to pass no resume position unless the room payload already supplies one.

```kotlin
TvRoute.Player(
    contentId = roomState.contentId,
    fileId = roomState.fileId,
    roomId = roomState.roomId,
)
```

- [ ] Add `resumePositionOverride` to `TvPlayerScreen` and pass it into the Koin ViewModel parameter list at creation time. This is required because `TvPlayerViewModel` calls `loadContent()` from `init`.

```kotlin
fun TvPlayerScreen(
    contentId: String,
    onExit: () -> Unit,
    preferredFileId: Int? = null,
    roomId: String? = null,
    resumePositionOverride: Double? = null,
) {
    val viewModel: TvPlayerViewModel = koinViewModel(
        key = "tv-player-$contentId-${preferredFileId ?: "auto"}-${resumePositionOverride ?: "default"}",
        parameters = {
            when {
                preferredFileId != null && resumePositionOverride != null ->
                    parametersOf(contentId, preferredFileId, resumePositionOverride)
                preferredFileId != null ->
                    parametersOf(contentId, preferredFileId)
                resumePositionOverride != null ->
                    parametersOf(contentId, null, resumePositionOverride)
                else ->
                    parametersOf(contentId)
            }
        },
    )
}
```

- [ ] Add `resumePositionOverride` to `TvPlayerViewModel` constructor and use it in the existing initial load.

```kotlin
class TvPlayerViewModel(
    private val catalogRepository: CatalogRepository,
    private val playbackSessionManager: PlaybackSessionManager,
    private val profileRepository: ProfileRepository,
    private val personalDataRepository: PersonalDataRepository,
    private val capabilityDetector: PlaybackCapabilityDetector,
    private val playbackAnalytics: PlaybackAnalyticsListener,
    private val playerSettingsStore: PlayerSettingsStore,
    private val introAutoSkipController: IntroAutoSkipController,
    private val sessionLifecycle: PlaybackSessionLifecycle,
    private val sleepTimer: SleepTimerController,
    private val subtitlesRepository: SubtitlesRepository,
    private val contentId: String,
    private val preferredFileId: Int? = null,
    private val resumePositionOverride: Double? = null,
) : ViewModel() {
    init {
        if (contentId.isNotBlank()) loadContent(startPositionOverride = resumePositionOverride)
    }
}
```

- [ ] Update `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt` to read the third positional parameter.

```kotlin
preferredFileId = params.getOrNull<Int>(),
resumePositionOverride = params.getOrNull<Double>(),
```

- [ ] Run route and TV start-position tests.

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests 'com.continuum.app.tv.ui.navigation.TvPlayerRouteTest' \
  --tests 'com.continuum.app.tv.ui.screens.player.TvPlayerScreenStartPositionTest'
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Run TV build.

```bash
./gradlew :androidTvApp:assembleDebug
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Commit TV resume route contract.

```bash
git status --short
git add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvRoute.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvAppNavigation.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailScreen.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/navigation/TvPlayerRouteTest.kt
git commit -m "Pass video resume position through TV navigation"
```

Expected output: commit succeeds with message `Pass video resume position through TV navigation`.

---

### 8. Add Shared Video UI State

- [ ] Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/video/VideoPlayerUiStateTest.kt`.

```kotlin
package com.continuum.app.common.player.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoPlayerUiStateTest {
    @Test
    fun loadingStateHasNoPlayableMedia() {
        val state = VideoPlayerUiState.Loading(contentId = "movie-123")

        assertEquals("movie-123", state.contentId)
        assertTrue(!state.hasPlayableMedia)
    }

    @Test
    fun readyStateHasPlayableMediaAndResumeMs() {
        val state = VideoPlayerUiState.Ready(
            contentId = "movie-123",
            fileId = 44,
            streamUrl = "https://lib.strm.cafe/api/stream/movie",
            title = "Michael",
            subtitle = "Movie",
            artworkUrl = "https://lib.strm.cafe/poster.jpg",
            startPositionSeconds = 1887.25,
        )

        assertTrue(state.hasPlayableMedia)
        assertEquals(1_887_250L, state.startPositionMs)
    }
}
```

- [ ] Run the test and confirm it fails because `VideoPlayerUiState` does not exist.

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.video.VideoPlayerUiStateTest'
```

Expected failure:

```text
Unresolved reference 'VideoPlayerUiState'
```

- [ ] Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/video/VideoPlayerUiState.kt`.

```kotlin
package com.continuum.app.common.player.video

sealed interface VideoPlayerUiState {
    val contentId: String
    val hasPlayableMedia: Boolean

    data class Loading(
        override val contentId: String,
    ) : VideoPlayerUiState {
        override val hasPlayableMedia: Boolean = false
    }

    data class Error(
        override val contentId: String,
        val message: String,
    ) : VideoPlayerUiState {
        override val hasPlayableMedia: Boolean = false
    }

    data class Ready(
        override val contentId: String,
        val fileId: Int?,
        val streamUrl: String,
        val title: String,
        val subtitle: String?,
        val artworkUrl: String?,
        val startPositionSeconds: Double,
    ) : VideoPlayerUiState {
        override val hasPlayableMedia: Boolean = true

        val startPositionMs: Long
            get() {
                val seconds = if (startPositionSeconds.isFinite()) startPositionSeconds else 0.0
                return (seconds * 1000.0).toLong().coerceAtLeast(0L)
            }
    }
}
```

- [ ] Run the shared state test.

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.video.VideoPlayerUiStateTest'
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Commit shared video UI state.

```bash
git status --short
git add \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/video/VideoPlayerUiState.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/video/VideoPlayerUiStateTest.kt
git commit -m "Add shared video player state model"
```

Expected output: commit succeeds with message `Add shared video player state model`.

---

### 9. Extract Shared Session Coordinator

- [ ] Inspect current mobile and TV ViewModel session-loading code and identify equivalent branches.

```bash
rg -n "PlaybackStart|resolvePlaybackStart|startPlayback|PlaybackSession|fallback|transcode|remux|DirectPlay|startPosition" \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerViewModel.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player \
  shared/src/commonMain/kotlin/com/continuum/app/model/playback/PlaybackStartPosition.kt
```

Expected observation:

```text
Both ViewModels resolve start position, start playback, hold play method/stream URL, handle fallback, and manage progress/session state.
```

- [ ] Add unit tests for the coordinator in `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/video/VideoPlaybackSessionCoordinatorTest.kt`.

Test cases:

```text
1. explicit route resume override wins over detail/user-data progress.
2. detail/user-data progress is used when override is absent.
3. invalid override is ignored.
4. coordinator returns a VideoPlayerUiState.Ready containing streamUrl, fileId, title, artwork, and startPositionSeconds.
5. coordinator exposes fallback result when primary play method fails and fallback succeeds.
```

Use fake interfaces for dependencies rather than mocking Android framework types:

```kotlin
private class FakePlaybackStarter : VideoPlaybackStarter {
    val requests = mutableListOf<VideoPlaybackStartRequest>()
    var result: VideoPlaybackStartResult = VideoPlaybackStartResult.Ready(
        contentId = "movie-123",
        fileId = 44,
        streamUrl = "https://lib.strm.cafe/api/stream/movie",
        playMethod = PlayMethod.DirectPlay,
        title = "Michael",
        subtitle = "Movie",
        artworkUrl = "https://lib.strm.cafe/poster.jpg",
        startPositionSeconds = 1887.25,
    )
    override suspend fun start(request: VideoPlaybackStartRequest): VideoPlaybackStartResult {
        requests += request
        return result
    }
}
```

- [ ] Introduce shared coordinator interfaces and implementation:

```text
android-shared/src/androidMain/kotlin/com/continuum/app/common/player/video/VideoPlaybackStartRequest.kt
android-shared/src/androidMain/kotlin/com/continuum/app/common/player/video/VideoPlaybackStartResult.kt
android-shared/src/androidMain/kotlin/com/continuum/app/common/player/video/VideoPlaybackStarter.kt
android-shared/src/androidMain/kotlin/com/continuum/app/common/player/video/VideoPlaybackSessionCoordinator.kt
```

Core request shape:

```kotlin
data class VideoPlaybackStartRequest(
    val contentId: String,
    val preferredFileId: Int?,
    val roomId: String?,
    val resumePositionOverride: Double?,
)
```

Core result shape:

```kotlin
sealed interface VideoPlaybackStartResult {
    data class Ready(
        val contentId: String,
        val fileId: Int?,
        val streamUrl: String,
        val playMethod: PlayMethod,
        val title: String,
        val subtitle: String?,
        val artworkUrl: String?,
        val startPositionSeconds: Double,
    ) : VideoPlaybackStartResult

    data class Error(
        val contentId: String,
        val message: String,
        val cause: Throwable? = null,
    ) : VideoPlaybackStartResult
}
```

Coordinator contract:

```kotlin
class VideoPlaybackSessionCoordinator(
    private val starter: VideoPlaybackStarter,
) {
    suspend fun start(request: VideoPlaybackStartRequest): VideoPlayerUiState {
        return when (val result = starter.start(request)) {
            is VideoPlaybackStartResult.Ready -> VideoPlayerUiState.Ready(
                contentId = result.contentId,
                fileId = result.fileId,
                streamUrl = result.streamUrl,
                title = result.title,
                subtitle = result.subtitle,
                artworkUrl = result.artworkUrl,
                startPositionSeconds = result.startPositionSeconds,
            )
            is VideoPlaybackStartResult.Error -> VideoPlayerUiState.Error(
                contentId = result.contentId,
                message = result.message,
            )
        }
    }
}
```

- [ ] Run coordinator tests.

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.video.VideoPlaybackSessionCoordinatorTest'
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Commit coordinator primitives.

```bash
git status --short
git add \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/video/VideoPlaybackStartRequest.kt \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/video/VideoPlaybackStartResult.kt \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/video/VideoPlaybackStarter.kt \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/video/VideoPlaybackSessionCoordinator.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/video/VideoPlaybackSessionCoordinatorTest.kt
git commit -m "Add shared video playback session coordinator"
```

Expected output: commit succeeds with message `Add shared video playback session coordinator`.

---

### 10. Delegate TV Session Startup To Shared Coordinator

- [ ] Create a TV adapter class that implements `VideoPlaybackStarter` by wrapping the existing `TvPlayerViewModel` repositories/services. Keep it in the TV module first so extraction is behavior-preserving.

```text
androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvVideoPlaybackStarter.kt
```

The adapter must move code from `TvPlayerViewModel.loadContent(startPositionOverride = resumePositionOverride)` without changing:

```text
content detail lookup
selected file resolution
playback start API call
PlaybackStartPosition resolution
fallback to remux/transcode behavior
room/watch-together adoption
error messages
```

- [ ] Add TV adapter tests or source guards that prove the ViewModel now calls `VideoPlaybackSessionCoordinator.start(request)` instead of duplicating route/detail start-position resolution.

```text
androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModelSharedCoordinatorTest.kt
```

Required assertions:

```text
1. TvPlayerViewModel has a VideoPlaybackSessionCoordinator constructor dependency.
2. TvPlayerViewModel's initial load path passes contentId, preferredFileId, roomId, and resumePositionOverride into VideoPlaybackStartRequest.
3. TvPlayerViewModel no longer directly calls resolvePlaybackStartPosition.
```

- [ ] Update Koin TV module to provide `TvVideoPlaybackStarter` and `VideoPlaybackSessionCoordinator`.

Add this import if the module does not already have it:

```kotlin
import org.koin.core.qualifier.named
```

```kotlin
factory<VideoPlaybackStarter>(named("tvVideoPlaybackStarter")) {
    TvVideoPlaybackStarter(
        catalogRepository = get(),
        playbackSessionManager = get(),
        profileRepository = get(),
        personalDataRepository = get(),
        capabilityDetector = get(),
        playerSettingsStore = get(),
    )
}
factory {
    VideoPlaybackSessionCoordinator(
        starter = get(named("tvVideoPlaybackStarter")),
    )
}
```

Do not add duplicate repository, session manager, or settings-store instances; the adapter must use the existing Koin singletons already injected into `TvPlayerViewModel`.

- [ ] Run TV ViewModel tests.

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.player.TvPlayerViewModelSharedCoordinatorTest'
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Run TV build.

```bash
./gradlew :androidTvApp:assembleDebug
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Commit TV session delegation.

```bash
git status --short
git add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvVideoPlaybackStarter.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModelSharedCoordinatorTest.kt
git commit -m "Delegate TV video session startup to shared coordinator"
```

Expected output: commit succeeds with message `Delegate TV video session startup to shared coordinator`.

---

### 11. Delegate Mobile Session Startup To Shared Coordinator

- [ ] Create a mobile adapter class that implements `VideoPlaybackStarter` by wrapping existing mobile `PlayerViewModel` repositories/services.

```text
androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/AndroidVideoPlaybackStarter.kt
```

The adapter must preserve:

```text
content detail lookup
selected file resolution
download/local media preference
playback start API call
PlaybackStartPosition resolution
fallback to remux/transcode behavior
progress/session state
Watch Together room adoption
```

- [ ] Add mobile adapter tests or source guards that prove `PlayerViewModel` now calls `VideoPlaybackSessionCoordinator.start(request)` instead of owning route/detail start-position resolution.

```text
androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/PlayerViewModelSharedCoordinatorTest.kt
```

Required assertions:

```text
1. PlayerViewModel has a VideoPlaybackSessionCoordinator constructor dependency.
2. PlayerViewModel's initial load path passes contentId, preferredFileId, roomId, and resumePositionOverride into VideoPlaybackStartRequest.
3. PlayerViewModel no longer directly calls resolvePlaybackStartPosition.
```

- [ ] Update mobile Koin module to provide `AndroidVideoPlaybackStarter` and `VideoPlaybackSessionCoordinator`.

Add this import if the module does not already have it:

```kotlin
import org.koin.core.qualifier.named
```

```kotlin
factory<VideoPlaybackStarter>(named("mobileVideoPlaybackStarter")) {
    AndroidVideoPlaybackStarter(
        catalogRepository = get(),
        playbackSessionManager = get(),
        profileRepository = get(),
        serverRegistry = get(),
        personalDataRepository = get(),
        capabilityDetector = get(),
        offlineMediaResolver = get(),
        playerSettingsStore = get(),
    )
}
factory {
    VideoPlaybackSessionCoordinator(
        starter = get(named("mobileVideoPlaybackStarter")),
    )
}
```

Do not add duplicate repository, session manager, resolver, registry, or settings-store instances; the adapter must use the existing Koin singletons already injected into `PlayerViewModel`.

- [ ] Run mobile ViewModel tests.

```bash
./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.player.PlayerViewModelSharedCoordinatorTest'
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Run mobile build.

```bash
./gradlew :androidApp:assembleDebug
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Commit mobile session delegation.

```bash
git status --short
git add \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerViewModel.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/AndroidVideoPlaybackStarter.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/di \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/PlayerViewModelSharedCoordinatorTest.kt
git commit -m "Delegate mobile video session startup to shared coordinator"
```

Expected output: commit succeeds with message `Delegate mobile video session startup to shared coordinator`.

---

### 12. Extract Shared Track Selection Coordinator

- [ ] Add shared tests for audio/subtitle command behavior.

```text
android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/video/VideoTrackSelectionCoordinatorTest.kt
```

Required cases:

```text
1. selecting subtitle Off clears text track selection.
2. selecting external subtitle remounts media with that subtitle and preserves current player position.
3. selecting embedded subtitle updates Media3 track selection parameters.
4. selecting audio track updates Media3 track selection parameters.
5. selected subtitle label includes AI/enhancement marker when metadata indicates generated/enhanced subtitles.
```

- [ ] Create shared coordinator:

```text
android-shared/src/androidMain/kotlin/com/continuum/app/common/player/video/VideoTrackSelectionCoordinator.kt
```

Public API:

```kotlin
class VideoTrackSelectionCoordinator(
    private val subtitleManager: SubtitleManager,
) {
    fun selectSubtitle(
        player: Player,
        playerFactory: ContinuumPlayerFactory,
        mediaSpec: VideoPlayerMediaSpec,
        selectedTrack: PlayerTrackEntry?,
    )

    fun selectAudioTrack(
        player: Player,
        audioTrackManager: AudioTrackManager,
        selectedTrack: PlayerTrackEntry,
    )

    fun describeSubtitle(
        track: PlayerTrackEntry,
        isAiGenerated: Boolean,
        isEnhanced: Boolean,
    ): String
}
```

Use existing `SubtitleManager`, Media3 `TrackSelectionParameters`, and `refreshMountedVideoMedia(player, playerFactory, mediaSpec)`.

- [ ] Replace duplicated subtitle selection logic in `PlayerScreen.kt` and `TvPlayerScreen.kt` with coordinator calls. Keep each UI surface responsible only for focus/touch rendering and menu close behavior.

- [ ] Run focused shared/mobile/TV tests.

```bash
./gradlew \
  :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.player.video.VideoTrackSelectionCoordinatorTest' \
  :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.player.PlayerScreenStartPositionTest' \
  :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.player.TvPlayerScreenStartPositionTest'
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Commit track selection extraction.

```bash
git status --short
git add \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/video/VideoTrackSelectionCoordinator.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/video/VideoTrackSelectionCoordinatorTest.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt
git commit -m "Share video track selection behavior"
```

Expected output: commit succeeds with message `Share video track selection behavior`.

---

### 13. Regression Test The Full Android Surface

- [ ] Run the full relevant unit suite and both debug builds.

```bash
./gradlew \
  :android-shared:testDebugUnitTest \
  :androidApp:testDebugUnitTest \
  :androidTvApp:testDebugUnitTest \
  :androidApp:assembleDebug \
  :androidTvApp:assembleDebug
```

Expected output:

```text
BUILD SUCCESSFUL
```

- [ ] Check that no TV files reference ebook reader routes or ebook playback.

```bash
rg -n "ebook|Ebook|reader|Reader|Reading" androidTvApp/src
```

Expected output may include non-playback labels only. There must be no route/screen that opens an ebook reader on TV.

- [ ] Check that both player screens use the shared mount helper.

```bash
rg -n "mountVideoMedia|refreshMountedVideoMedia|setMediaItem\\(|seekTo\\(startMs" \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt
```

Expected output:

```text
Both screens call mountVideoMedia and refreshMountedVideoMedia.
No screen calls seekTo(startMs) for initial resume.
No TV screen calls setMediaItem(mediaItem) without a start position for initial mount.
```

- [ ] Check for leftover formatting/import changes in files touched by this migration.

```bash
git status --short
```

Expected output:

```text
No unstaged migration changes remain after the task commits above.
```

---

### 14. Install And Verify On Devices

- [ ] Confirm connected devices.

```bash
adb devices -l
```

Expected output:

```text
Pixel 10 XL is listed.
Shield is listed over wireless adb.
```

- [ ] Install mobile debug build on Pixel.

```bash
PIXEL_ID=$(adb devices -l | awk 'tolower($0) ~ /device / && tolower($0) ~ /pixel/ { print $1; exit }')
test -n "$PIXEL_ID"
adb -s "$PIXEL_ID" install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Expected output:

```text
Success
```

- [ ] Install TV debug build on Shield. Use the APK path produced by the current Gradle build variant.

```bash
SHIELD_ID=$(adb devices -l | awk 'tolower($0) ~ /device / && tolower($0) ~ /(shield|nvidia|android_tv)/ { print $1; exit }')
TV_APK=$(find androidTvApp/build/outputs/apk -name '*.apk' | sort | tail -n 1)
test -n "$SHIELD_ID"
test -n "$TV_APK"
adb -s "$SHIELD_ID" install -r "$TV_APK"
```

Expected output:

```text
Success
```

- [ ] Verify mobile against `https://lib.strm.cafe`.

Manual flow:

```text
1. Open mobile app.
2. Sign in as jim.
3. Open Video.
4. Open Michael.
5. Start playback from detail.
6. Seek past 2 minutes.
7. Back out.
8. Re-open Michael.
9. Confirm detail action shows Resume.
10. Start playback and confirm first visible position is near the saved resume time.
11. Open subtitle menu, select English, confirm subtitles display.
12. Switch subtitles Off, confirm subtitles disappear.
```

- [ ] Verify TV against `https://lib.strm.cafe`.

Manual flow:

```text
1. Open TV app on Shield.
2. Sign in as jim.
3. Open Michael.
4. Start playback.
5. Seek past 2 minutes.
6. Back out to detail.
7. Confirm detail action shows Resume.
8. Start playback and confirm playback starts near the saved resume time, not 0.
9. Open dedicated subtitles menu.
10. Move focus down with the D-pad without needing an Up key first.
11. Select English.
12. Confirm the menu closes or visibly confirms selection.
13. Confirm subtitles display on screen.
14. Re-open menu and confirm English remains selected.
15. Select Off and confirm subtitles disappear.
```

- [ ] Capture logcat during TV verification.

```bash
SHIELD_ID=$(adb devices -l | awk 'tolower($0) ~ /device / && tolower($0) ~ /(shield|nvidia|android_tv)/ { print $1; exit }')
test -n "$SHIELD_ID"
adb -s "$SHIELD_ID" logcat -c
adb -s "$SHIELD_ID" logcat | rg -i "continuum|prairie|player|subtitle|media3|exoplayer|resume|error|exception"
```

Expected observation:

```text
No crash stack traces.
Initial player state logs show non-zero resume position after replaying Michael.
Subtitle selection produces Media3 or app logs showing selected text track.
```

- [ ] Capture logcat during Pixel verification.

```bash
PIXEL_ID=$(adb devices -l | awk 'tolower($0) ~ /device / && tolower($0) ~ /pixel/ { print $1; exit }')
test -n "$PIXEL_ID"
adb -s "$PIXEL_ID" logcat -c
adb -s "$PIXEL_ID" logcat | rg -i "continuum|prairie|player|subtitle|media3|exoplayer|resume|error|exception"
```

Expected observation:

```text
No crash stack traces.
Mobile resume and subtitles continue working after shared mount migration.
```

---

### 15. Final Review

- [ ] Review the branch diff.

```bash
BASE=$(git merge-base HEAD main 2>/dev/null || git merge-base HEAD origin/main)
git diff --stat "$BASE"..HEAD
git log --oneline "$BASE"..HEAD
```

Expected commits:

```text
Add shared video media mounting helper
Use shared video media mounting on mobile
Add shared video player route args
Use shared video media mounting on TV
Pass video resume position through TV navigation
Add shared video player state model
Add shared video playback session coordinator
Delegate TV video session startup to shared coordinator
Delegate mobile video session startup to shared coordinator
Share video track selection behavior
```

Every migration commit must contain only files touched for this shared-player migration.

- [ ] Confirm acceptance criteria.

```text
1. Mobile and TV initial Media3 mounting share one helper.
2. Mobile and TV subtitle remounting share one helper.
3. TV route carries resumePosition from detail to player.
4. TV no longer starts resumed content at 0.
5. TV subtitles can be selected and displayed.
6. Mobile resume and subtitle behavior still works.
7. Ebooks remain absent from TV.
8. Debug APKs build and install.
```

- [ ] Leave final status with:

```text
Summary of shared-player changes.
Test commands run and their results.
Device verification performed.
Known remaining risks, if any.
```
