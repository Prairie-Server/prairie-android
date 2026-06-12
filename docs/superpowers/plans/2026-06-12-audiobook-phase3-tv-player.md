# Audiobook Player — Phase 3: TV Player — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Give the Android TV app (`androidTvApp`) a first-class, 10-foot, D-pad-navigable audiobook player it does not have today. The new `TvAudiobookPlayerScreen` consumes the **same** `AudiobookPlayerViewModel` that Phase 1 relocated into `android-shared` (`com.continuum.app.common.audiobook`) — no playback, chapter, sleep, skip, or speed logic is duplicated. Audiobook-type items selected on `TvItemDetailScreen` route to this screen instead of the video `TvPlayerScreen`.

**Architecture:** The screen is a thin Compose + focus view over the shared VM. It binds a Media3 `MediaController` to the shared `ContinuumPlaybackService` (identical async-bind pattern to `TvPlayerScreen`/phone `AudiobookPlayerScreen`), polls position at 4 Hz into the VM, and mirrors VM intent (play/pause, speed, pending seeks) back into the controller. Chapter math comes from the VM (Phase 1's `currentChapterIndex` / `chapterProgress` / `chapterCountLabel` / `skipToPreviousChapter` / `skipToNextChapter`, delegating to pure `shared` `AudiobookChapters`). Chapters / speed / sleep are **focusable full-screen side-panel overlays** (mirroring `TvFullScreenPicker` / `TvPlayerHud` patterns), not phone bottom sheets. A new TV nav destination (`TvRoute.AudiobookPlayer`) is added; the routing decision (audiobook → audiobook route, else → video player route) is a **pure, unit-tested top-level function** in the detail nav layer, mirroring `shouldEnterSyncedPlayer`.

**Tech Stack:** Kotlin Multiplatform; `androidTvApp` is `androidMain`-only. UI is **androidx.tv Compose** (`androidx.tv.material3.*` for `Text`/`Icon`/`MaterialTheme`/`Surface`), `androidx.compose.foundation` for layout + `focusable` + `onPreviewKeyEvent`, Media3 `MediaController` + `androidx.media3.session.SessionToken`, Koin (`koinViewModel` + `parametersOf`), Jetpack Navigation Compose. Tests are plain JVM unit tests in `androidTvApp/src/androidUnitTest` run with `./gradlew :androidTvApp:testDebugUnitTest`.

---

## File Structure

**Created**

- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/audiobook/TvAudiobookPlayerScreen.kt` — the 10-foot screen: MediaController bind, position poll, VM mirroring, layout (cover + metadata, current-chapter header, chapter progress bar, D-pad transport row), and overlay hosting. Mirrors `TvPlayerScreen.kt`.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/audiobook/TvAudiobookTransportRow.kt` — the five-button D-pad transport cluster (prev-chapter / skip-back / play-pause / skip-forward / next-chapter). Mirrors `TvPlayerTransportCluster.kt` focus/visual idiom.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/audiobook/TvAudiobookChaptersPanel.kt` — focusable full-screen side panel: lazy chapter list, highlights + auto-scrolls to the current chapter, Select jumps. Replaces the phone `ChaptersSheet`.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/audiobook/TvAudiobookSpeedPanel.kt` — focusable speed-preset panel (0.5×–3.0×). Replaces the phone `SpeedSheet`.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/audiobook/TvAudiobookSleepPanel.kt` — focusable sleep-timer panel (Off / fixed minutes / End of chapter). Replaces the phone `SleepTimerSheet`.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvAudiobookRouting.kt` — pure `tvPlayDestinationFor(itemType, contentId, fileId): String` deciding audiobook vs video route. Unit-tested.
- `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/navigation/TvAudiobookRoutingTest.kt` — JVM unit tests for the routing decision.

**Modified**

- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvRoute.kt` — add `TvRoute.AudiobookPlayer` data class + `ROUTE` / arg constants (mirrors `TvRoute.Player`, no `roomId`).
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvAppNavigation.kt` — change the `ItemDetail` `onPlay` callback to route via `tvPlayDestinationFor(...)`; add the `composable(TvRoute.AudiobookPlayer.ROUTE)` destination hosting `TvAudiobookPlayerScreen`; route the `"play"` deep-link branch through the same decision.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailScreen.kt` — pass the item's `type` through `onPlay` so the nav layer can decide. (Signature change: `onPlay: (contentId, fileId, itemType)`.)
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/<Tv module>.kt` (the file containing `viewModel { params -> TvItemDetailViewModel(...) }`) — register `AudiobookPlayerViewModel` for `androidTvApp` via Koin with `contentId` + `fileId` supplied through `SavedStateHandle` (nav args), mirroring the phone registration.

---

### Task 1 — Pure TV play-routing decision (TDD)

The detail screen currently always routes Play to the video `TvRoute.Player`. Audiobooks must route to the new audiobook destination. Extract the decision into a pure function so it is route-independent and unit-testable (mirrors `shouldEnterSyncedPlayer` in `LobbyNavigationDecisionTest.kt`).

**Files:**
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvAudiobookRouting.kt` (new)
- `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/navigation/TvAudiobookRoutingTest.kt` (new)

- [ ] Write the failing test first. Create `TvAudiobookRoutingTest.kt`:

```kotlin
package com.continuum.app.tv.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure routing decision: audiobook-type items go to the audiobook player route,
 * everything else to the video player route. Mirrors the catalog's
 * `isAudiobookItemType` taxonomy (case/whitespace-insensitive, singular "audiobook").
 * Route-independent — returns the route string, no Android types — so it stays a
 * plain JVM unit test.
 */
class TvAudiobookRoutingTest {

    @Test
    fun audiobookTypeRoutesToAudiobookPlayer() {
        assertEquals(
            TvRoute.AudiobookPlayer("ab-1", fileId = 7).route,
            tvPlayDestinationFor(itemType = "audiobook", contentId = "ab-1", fileId = 7),
        )
    }

    @Test
    fun audiobookTypeIsCaseAndWhitespaceInsensitive() {
        assertEquals(
            TvRoute.AudiobookPlayer("ab-2", fileId = null).route,
            tvPlayDestinationFor(itemType = "  AudioBook ", contentId = "ab-2", fileId = null),
        )
    }

    @Test
    fun movieTypeRoutesToVideoPlayer() {
        assertEquals(
            TvRoute.Player("m-1", fileId = 3).route,
            tvPlayDestinationFor(itemType = "movie", contentId = "m-1", fileId = 3),
        )
    }

    @Test
    fun nullTypeRoutesToVideoPlayer() {
        assertEquals(
            TvRoute.Player("x-1", fileId = null).route,
            tvPlayDestinationFor(itemType = null, contentId = "x-1", fileId = null),
        )
    }
}
```

- [ ] Run `./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.ui.navigation.TvAudiobookRoutingTest"` and confirm it fails to compile (function + route not yet defined). RED.
- [ ] Implement `TvAudiobookRouting.kt`. (Depends on `TvRoute.AudiobookPlayer` from Task 2 — do Task 2's route addition first, then return here; or stub the route now and let Task 2 fill it. Recommended: implement Task 2 step 1 before this implementation step.) Use the shared catalog predicate so the taxonomy stays single-sourced:

```kotlin
package com.continuum.app.tv.ui.navigation

import com.continuum.app.model.catalog.isAudiobookItemType

/**
 * Decide the playback route for a Play action on the TV detail screen.
 *
 * Audiobook-type items ([isAudiobookItemType]) open the dedicated
 * [TvRoute.AudiobookPlayer]; everything else opens the video [TvRoute.Player].
 * Pure (returns the route string, no Android/Nav types) so the decision is
 * unit-tested independently of navigation — see `TvAudiobookRoutingTest`.
 */
fun tvPlayDestinationFor(
    itemType: String?,
    contentId: String,
    fileId: Int?,
): String =
    if (isAudiobookItemType(itemType)) {
        TvRoute.AudiobookPlayer(contentId, fileId).route
    } else {
        TvRoute.Player(contentId, fileId).route
    }
```

- [ ] Re-run the test task. GREEN. Verify all four cases pass.

---

### Task 2 — Add the TV audiobook nav destination

**Files:**
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvRoute.kt` (modified)

- [ ] In `TvRoute.kt`, add a new route alongside `TvRoute.Player` (no `roomId` — audiobooks have no Watch Together). Place it directly after the `Player` data class:

```kotlin
    /**
     * Audiobook playback route. Optional `fileId` query param pre-selects a
     * specific version (mirrors [Player]); absent ⇒ the VM auto-selects the
     * first version. No `roomId` — audiobooks have no synced playback.
     */
    data class AudiobookPlayer(
        val contentId: String,
        val fileId: Int? = null,
    ) : TvRoute(
        buildString {
            append("audiobook/$contentId")
            if (fileId != null) append("?fileId=$fileId")
        },
    ) {
        companion object {
            const val ROUTE = "audiobook/{contentId}?fileId={fileId}"
            const val ARG_CONTENT_ID = "contentId"
            const val ARG_FILE_ID = "fileId"
        }
    }
```

- [ ] Confirm the `route` strings produced match what `TvAudiobookRoutingTest` asserts (`audiobook/ab-1?fileId=7` and `audiobook/ab-2`). Re-run Task 1's test if it was stubbed.

---

### Task 3 — Register `AudiobookPlayerViewModel` in the TV Koin module

The phone registers `AudiobookPlayerViewModel` with `savedStateHandle = get()` so the `contentId` / `fileId` nav args flow in (see `androidApp/.../di/AndroidModule.kt:210`). Phase 1 moved the VM to `com.continuum.app.common.audiobook.AudiobookPlayerViewModel` in `android-shared`. Register the **same** class in the TV module.

**Files:**
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/<Tv module>.kt` (the file with `viewModel { params -> TvItemDetailViewModel(...) }`; locate with `grep -rn "TvItemDetailViewModel(" androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/`)

- [ ] Confirm the VM's collaborators (`CatalogRepository`, `PlaybackSessionManager`, `PlaybackCapabilityDetector`, `AudiobookBookmarksStore`, `AudiobookPositionStore`, `ServerRegistry`, `ProfileRepository`, `OfflineMediaResolver`) are already provided as Koin singles in the TV graph (they are shared/`android-shared` deps the TV player + detail already use). If `AudiobookBookmarksStore` / `AudiobookPositionStore` are not yet `single { ... }` in the TV graph, add them mirroring `androidApp/.../di/AndroidModule.kt:201-203`:

```kotlin
    single {
        com.continuum.app.common.audiobook.AudiobookPositionStore(androidContext().filesDir)
    }
    single { com.continuum.app.common.audiobook.AudiobookBookmarksStore(androidContext().filesDir) }
```

- [ ] Add the ViewModel registration. The VM reads nav args from `SavedStateHandle` (keys `"contentId"`, `"fileId"`), so use the auto-injected handle exactly like the phone:

```kotlin
    // Audiobook player VM is shared (android-shared). SavedStateHandle is
    // auto-injected by Koin's viewModel scope so the contentId/fileId nav args
    // (TvRoute.AudiobookPlayer) flow through unchanged.
    viewModel {
        com.continuum.app.common.audiobook.AudiobookPlayerViewModel(
            catalogRepository = get(),
            playbackSessionManager = get(),
            capabilityDetector = get(),
            bookmarksStore = get(),
            positionStore = get(),
            serverRegistry = get(),
            profileRepository = get(),
            offlineMediaResolver = get(),
            savedStateHandle = get(),
        )
    }
```

- [ ] Build the module: `./gradlew :androidTvApp:compileDebugKotlin`. Resolve any unresolved-dependency Koin wiring before proceeding. (Build error here means a collaborator single is missing — add it.)

---

### Task 4 — Detail screen passes item type to `onPlay`

The nav layer needs the item's `type` to choose the route. Widen the `onPlay` callback to carry it. `HeroActionRow` already has `detail` in scope.

**Files:**
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailScreen.kt` (modified)

- [ ] Change the `TvItemDetailScreen` param `onPlay: (contentId: String, fileId: Int?) -> Unit` to `onPlay: (contentId: String, fileId: Int?, itemType: String?) -> Unit`. Update the same signature on the private `HeroActionRow` composable and wherever `onPlay` is declared in this file (`grep -n "onPlay" TvItemDetailScreen.kt` lists every site — they are at the screen param, the hero `actions` lambda forwarding, and `HeroActionRow`).
- [ ] Update the two `onPlay(...)` call sites inside `HeroActionRow` (the "Play"/"Resume" pill at `:334` and the "Start Over" pill at `:346`) to pass `detail.type`:

```kotlin
                onClick = { onPlay(detail.contentId, selectedFileId, detail.type) },
```

- [ ] Build: `./gradlew :androidTvApp:compileDebugKotlin`. (The nav-layer caller is updated in Task 5; expect this to fail at the `TvAppNavigation` call site until Task 5 lands — that is fine, do Tasks 4 and 5 together.)

---

### Task 5 — Wire the destination + routing in `TvAppNavigation`

**Files:**
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvAppNavigation.kt` (modified)

- [ ] Add imports near the existing screen imports:

```kotlin
import com.continuum.app.tv.ui.screens.audiobook.TvAudiobookPlayerScreen
```

- [ ] In the `composable(TvRoute.ItemDetail.ROUTE)` block, change the `onPlay` lambda to use the pure decision so audiobooks route to the audiobook player:

```kotlin
                onPlay = { playContentId, fileId, itemType ->
                    navController.navigate(
                        tvPlayDestinationFor(itemType, playContentId, fileId),
                    )
                },
```

- [ ] Route the Watch Next `"play"` deep-link branch through the same decision. Today it hard-codes `TvRoute.Player`. The deep link only carries a `contentId`, not a type, so fetch-then-decide is out of scope here; keep deep-link plays on the video route **unless** the URI host already disambiguates. Leave the existing `"play"` branch as-is (video) and add a code comment noting audiobook deep links are a follow-up (the launcher only surfaces video Continue-Watching today). Document this in "Deferred items".
- [ ] Add the audiobook player destination after the existing `composable(TvRoute.Player.ROUTE)` block. It mirrors `Player` but drops `roomId`:

```kotlin
        composable(
            route = TvRoute.AudiobookPlayer.ROUTE,
            arguments = listOf(
                navArgument(TvRoute.AudiobookPlayer.ARG_CONTENT_ID) { type = NavType.StringType },
                navArgument(TvRoute.AudiobookPlayer.ARG_FILE_ID) {
                    // Query param is serialized as a string and may be absent;
                    // the shared VM parses it off SavedStateHandle ("fileId").
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            // contentId/fileId reach AudiobookPlayerViewModel via SavedStateHandle,
            // so the screen needs no explicit args here.
            TvAudiobookPlayerScreen(
                onExit = { navController.popBackStack() },
            )
        }
```

- [ ] Build: `./gradlew :androidTvApp:compileDebugKotlin`. (Will fail until `TvAudiobookPlayerScreen` exists — Task 6. Do Task 6 next, then this compiles.)

---

### Task 6 — `TvAudiobookTransportRow` (D-pad transport cluster)

Build the transport row first so the main screen can host it. Mirror `TvPlayerTransportCluster.kt` exactly for the focus model (white-on-black ↔ black-on-white, 66 dp circles, `onPreviewKeyEvent` on KeyUp for Select, Up returns focus upward). Five buttons: prev-chapter, skip-back, play-pause, skip-forward, next-chapter.

**Files:**
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/audiobook/TvAudiobookTransportRow.kt` (new)

- [ ] Create the file with the transport row + a private `TransportIconButton` copied from `TvPlayerTransportCluster.kt` (same focus visuals). Chapter buttons disable (dim + ignore Select) when `chaptersEnabled` is false (single-chapter / no chapters — spec §8 degrade rule). Material icons: `SkipPrevious`, `Replay30` (skip-back), `Pause`/`PlayArrow`, `Forward30` (skip-fwd), `SkipNext`.

```kotlin
package com.continuum.app.tv.ui.screens.audiobook

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon

/**
 * Five-button audiobook transport, D-pad navigable. Mirrors
 * `TvPlayerTransportCluster` focus visuals (white↔black flip, 66 dp circles,
 * KeyUp-driven Select). Chapter buttons dim + no-op when [chaptersEnabled] is
 * false (book has no chapters — single-chapter degrade, spec §8).
 */
@Composable
fun TvAudiobookTransportRow(
    isPlaying: Boolean,
    chaptersEnabled: Boolean,
    onPrevChapter: () -> Unit,
    onSkipBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onNextChapter: () -> Unit,
    playPauseFocus: FocusRequester,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TransportIconButton(
            icon = Icons.Filled.SkipPrevious,
            description = "Previous chapter",
            enabled = chaptersEnabled,
            onClick = onPrevChapter,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
        )
        TransportIconButton(
            icon = Icons.Filled.Replay30,
            description = "Skip back 30 seconds",
            onClick = onSkipBack,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
        )
        TransportIconButton(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            description = if (isPlaying) "Pause" else "Play",
            isPrimary = true,
            focusRequester = playPauseFocus,
            onClick = onPlayPause,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
        )
        TransportIconButton(
            icon = Icons.Filled.Forward30,
            description = "Skip forward 30 seconds",
            onClick = onSkipForward,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
        )
        TransportIconButton(
            icon = Icons.Filled.SkipNext,
            description = "Next chapter",
            enabled = chaptersEnabled,
            onClick = onNextChapter,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
        )
    }
}

@Composable
private fun TransportIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val buttonSize = 66.dp
    val symbolSize = if (isPrimary) 30.dp else 25.dp
    val focusBg = if (isFocused) Color.White else Color.Black.copy(alpha = 0.35f)
    val iconTint = when {
        !enabled -> Color.White.copy(alpha = 0.30f)
        isFocused -> Color.Black
        else -> Color.White
    }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.025f else 1f,
        animationSpec = tween(120),
        label = "abTransportScale",
    )

    Box(
        modifier = Modifier
            .size(buttonSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(focusBg)
            .border(
                width = 1.dp,
                color = if (isFocused) Color.Transparent else Color.White.copy(alpha = 0.22f),
                shape = CircleShape,
            )
            .let { mod -> if (focusRequester != null) mod.focusRequester(focusRequester) else mod }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (enabled) onClick()
                        true
                    }
                    Key.DirectionUp -> { onMoveUp(); true }
                    Key.DirectionDown -> { onMoveDown(); true }
                    else -> false
                }
            }
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(symbolSize),
        )
    }
}
```

- [ ] Build: `./gradlew :androidTvApp:compileDebugKotlin`. Confirm all Material icon imports resolve (`Replay30`, `Forward30`, `SkipPrevious`, `SkipNext` exist in `material-icons-extended`, already a dependency used by `TvPlayerTransportCluster`).

---

### Task 7 — Chapters / Speed / Sleep focusable overlay panels

Three full-screen, focusable overlays (not bottom sheets — spec §4.9). Each is dismissed with Back (handled by the screen). Mirror the `TvFullScreenPicker` / `TvPlayerHud` pattern: a scrim `Box` + a focusable column of rows, first row grabs focus on open.

**Files:**
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/audiobook/TvAudiobookChaptersPanel.kt` (new)
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/audiobook/TvAudiobookSpeedPanel.kt` (new)
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/audiobook/TvAudiobookSleepPanel.kt` (new)

- [ ] Create `TvAudiobookChaptersPanel.kt`. A right-aligned panel (≈520 dp) over a dimming scrim; a `LazyColumn` of chapters with `rememberLazyListState`; auto-scroll to `currentChapterIndex` on open; current row highlighted; Select calls `onSelectChapter(index)`:

```kotlin
package com.continuum.app.tv.ui.screens.audiobook

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.model.catalog.VersionChapter

/**
 * Full-screen, focusable chapters overlay. Right-aligned list panel; auto-scrolls
 * to + highlights [currentChapterIndex]; Select jumps. Back is handled by the
 * host screen (closes the panel). Replaces the phone ChaptersSheet (spec §4.9).
 */
@Composable
fun TvAudiobookChaptersPanel(
    chapters: List<VersionChapter>,
    currentChapterIndex: Int,
    onSelectChapter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex in chapters.indices) {
            runCatching { listState.scrollToItem(currentChapterIndex) }
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(520.dp)
                .background(Color(0xFF101010))
                .padding(horizontal = 28.dp, vertical = 32.dp),
        ) {
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(chapters) { index, chapter ->
                    ChapterRow(
                        title = chapter.title.ifBlank { "Chapter ${index + 1}" },
                        isCurrent = index == currentChapterIndex,
                        onClick = { onSelectChapter(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(
    title: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bg = when {
        isFocused -> Color.White
        isCurrent -> Color.White.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val fg = if (isFocused) Color.Black else Color.White
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onClick(); true }
                    else -> false
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = fg,
        )
    }
}
```

- [ ] Create `TvAudiobookSpeedPanel.kt`: identical scrim + panel skeleton, a fixed preset list `listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)`, each a focusable row showing e.g. `"1.5×"`; the current speed highlighted; Select calls `onSelectSpeed(speed)`. Reuse the `ChapterRow` visual idiom (copy a small `SpeedRow` private composable; label = `"${speed}×".replace(".0×", "×")`).
- [ ] Create `TvAudiobookSleepPanel.kt`: same skeleton; options `Off`, `5`, `10`, `15`, `30`, `45`, `60` minutes, and `End of chapter`. Map each row to the shared VM's sleep API: rows call `onSelectSleep(choice)` where `choice` is the shared `SleepTimerChoice` (`SleepTimerChoice.Off`, `SleepTimerChoice.Minutes(n)`, `SleepTimerChoice.EndOfChapter`) the VM already exposes (`applySleepTimer`). Highlight the currently selected choice from `sleepTimerChoice`.
- [ ] Build: `./gradlew :androidTvApp:compileDebugKotlin`.

---

### Task 8 — `TvAudiobookPlayerScreen` (main 10-foot screen)

The screen owns the `MediaController` bind, position poll, VM mirroring, layout, and overlay hosting. Copy the controller/poll/mirror idioms verbatim from the phone `AudiobookPlayerScreen.kt` (they are MediaController-generic) and the focus/Back idioms from `TvPlayerScreen.kt`. Use the **shared** `AudiobookPlayerViewModel` via `koinViewModel`.

**Files:**
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/audiobook/TvAudiobookPlayerScreen.kt` (new)

- [ ] Declare the composable. Inject the shared VM; bind the controller; collect state. Key the VM by `contentId` + `fileId` is unnecessary here because nav args flow via `SavedStateHandle` (Task 3) — a plain `koinViewModel()` reuses the route-scoped handle (matches the phone screen's `koinViewModel()`).

```kotlin
package com.continuum.app.tv.ui.screens.audiobook

import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.common.audiobook.AudiobookPlayerViewModel
import com.continuum.app.common.player.ContinuumPlaybackService
import com.continuum.app.tv.ui.components.TvPoster
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TvAudiobookPlayerScreen(
    onExit: () -> Unit,
    viewModel: AudiobookPlayerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
```

- [ ] Add the controller bind, prepare-on-streamUrl, position poll (4 Hz), play/pause mirror, speed mirror, and pending-seek mirror — copy directly from `AudiobookPlayerScreen.kt:87-197` (they reference only `controller`, `state`, and `viewModel`, all in scope). Reuse the `MediaMetadata` builder with title/artist/artwork. Keep the `onDispose { flushPosition(); stopPlaybackSession() }` teardown so progress is reported and the server session stops on Back (parity with phone).
- [ ] Track overlay state with a single enum to keep focus restoration simple:

```kotlin
    var activePanel by remember { mutableStateOf(AudiobookPanel.None) }
    val playPauseFocus = remember { FocusRequester() }
    LaunchedEffect(state.isLoading, activePanel) {
        if (!state.isLoading && activePanel == AudiobookPanel.None) {
            runCatching { playPauseFocus.requestFocus() }
        }
    }
```
where `enum class AudiobookPanel { None, Chapters, Speed, Sleep }` is declared at file scope.

- [ ] Add the Back handler: close an open panel first, else exit (mirrors `TvPlayerScreen` Back precedence):

```kotlin
    BackHandler(enabled = true) {
        if (activePanel != AudiobookPanel.None) {
            activePanel = AudiobookPanel.None
        } else {
            onExit()
        }
    }
```

- [ ] Build the layout. Root `Box(fillMaxSize, background Black)`. When `state.isLoading` show a centered `CircularProgressIndicator` (reuse `androidx.compose.material3.CircularProgressIndicator` as `TvPlayerScreen` does). When `state.error != null` show `TvErrorScreen(message = state.error!!, onRetry = null)` (already imported in TV). Otherwise the player body:

```kotlin
        Row(modifier = Modifier.fillMaxSize().padding(64.dp)) {
            // Left: cover.
            TvPoster(
                imageUrl = state.coverUrl,
                contentDescription = state.title,
                modifier = Modifier.size(340.dp).clip(RoundedCornerShape(12.dp)),
                cornerRadius = 12.dp,
            )
            Spacer(Modifier.width(56.dp))
            // Right: metadata + chapter header + progress + transport + secondary row.
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                (state.author ?: state.narrator)?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.75f))
                }
                Spacer(Modifier.height(24.dp))
                // Current-chapter header (hidden when no chapters — degrade rule).
                if (state.chapters.size > 1) {
                    Text(
                        text = viewModel.chapterCountLabel(),  // e.g. "Chapter 7 of 111"
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                TvAudiobookProgressBar(
                    fraction = if (state.chapters.size > 1) viewModel.chapterProgress()
                               else (state.positionSeconds / state.durationSeconds.coerceAtLeast(1.0)).toFloat(),
                    positionLabel = formatClock(state.positionSeconds),
                    durationLabel = formatClock(state.durationSeconds),
                )
                Spacer(Modifier.height(28.dp))
                TvAudiobookTransportRow(
                    isPlaying = state.isPlaying,
                    chaptersEnabled = state.chapters.size > 1,
                    onPrevChapter = { viewModel.skipToPreviousChapter() },
                    onSkipBack = { viewModel.seekBy(-30.0) },
                    onPlayPause = { viewModel.togglePlay() },
                    onSkipForward = { viewModel.seekBy(30.0) },
                    onNextChapter = { viewModel.skipToNextChapter() },
                    playPauseFocus = playPauseFocus,
                    onMoveUp = {},
                    onMoveDown = {},
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvAudiobookChip("${state.playbackSpeed}×") { activePanel = AudiobookPanel.Speed }
                    TvAudiobookChip("Sleep") { activePanel = AudiobookPanel.Sleep }
                    TvAudiobookChip("Chapters") { activePanel = AudiobookPanel.Chapters }
                }
            }
        }
```

> NOTE on VM API: `chapterCountLabel()`, `chapterProgress()`, `skipToPreviousChapter()`, `skipToNextChapter()` are the Phase 1 additions on the shared `AudiobookPlayerViewModel` (spec §4.2, delegating to `shared`'s `AudiobookChapters`). If a worker finds Phase 1 did not yet land these exact names, STOP and align with the Phase 1 VM surface before continuing — do not re-derive chapter math in the TV layer (spec §4.0/§4.9 forbid duplication). `seekBy(±30.0)`, `togglePlay()`, `setSpeed`, `applySleepTimer`, `jumpToChapter` already exist on the relocated VM (verified in the current `androidApp` VM that Phase 1 moves).

- [ ] Add private helpers in the same file: `TvAudiobookProgressBar` (a `Box` track + filled fraction `Box`, with a time `Row` underneath — purely presentational, not focusable), `TvAudiobookChip` (a focusable pill mirroring `TransportIconButton`'s focus flip but with a text label; Select calls `onClick`), and `formatClock(seconds: Double): String` (copy the H:MM:SS logic from `TvPlayerScreen.formatPlayerTime`). Chips' Up moves focus back to `playPauseFocus`; wire via `onMoveUp` if you factor the chip's key handling like the transport buttons.
- [ ] Host the overlays after the body, inside the root `Box`, gated on `activePanel`:

```kotlin
        when (activePanel) {
            AudiobookPanel.Chapters -> TvAudiobookChaptersPanel(
                chapters = state.chapters,
                currentChapterIndex = viewModel.currentChapterIndex(),
                onSelectChapter = { idx ->
                    state.chapters.getOrNull(idx)?.let { viewModel.jumpToChapter(it) }
                    activePanel = AudiobookPanel.None
                },
            )
            AudiobookPanel.Speed -> TvAudiobookSpeedPanel(
                currentSpeed = state.playbackSpeed,
                onSelectSpeed = { viewModel.setSpeed(it); activePanel = AudiobookPanel.None },
            )
            AudiobookPanel.Sleep -> {
                val sleepChoice by viewModel.sleepTimerChoice.collectAsState()
                TvAudiobookSleepPanel(
                    currentChoice = sleepChoice,
                    onSelectSleep = { viewModel.applySleepTimer(it); activePanel = AudiobookPanel.None },
                )
            }
            AudiobookPanel.None -> Unit
        }
```

- [ ] Build: `./gradlew :androidTvApp:compileDebugKotlin`. Then full module assemble to catch resource/manifest issues: `./gradlew :androidTvApp:assembleDebug`.

---

### Task 9 — Manual on-device verification (D-pad / focus)

UI focus traversal cannot be meaningfully asserted in unit tests; verify on a TV device/emulator with adb keyevents. Keyevent reference: `19`=UP, `20`=DOWN, `21`=LEFT, `22`=RIGHT, `23`=SELECT/DPAD_CENTER, `4`=BACK, `85`=MEDIA_PLAY_PAUSE.

**Files:** none (verification only).

- [ ] Install the debug build on a connected Android TV emulator/device: `./gradlew :androidTvApp:installDebug` then launch the app. Authenticate and pick a profile if needed.
- [ ] Navigate to an **audiobook** item's detail (an Audio-library item; audiobooks surface because `MediaMode.tvModes()` permits `Audio`). Press SELECT on the Play pill: `adb shell input keyevent 23`. **Verify** the new `TvAudiobookPlayerScreen` opens (cover left, metadata + "Chapter N of M" header + progress + transport), NOT the video `TvPlayerScreen`.
- [ ] **Transport focus traversal**: the play/pause button should hold initial focus. Press LEFT twice (`adb shell input keyevent 21; adb shell input keyevent 21`) → focus lands on prev-chapter (skip-back between). Press RIGHT four times to reach next-chapter. **Verify** each button shows the white-on-black focus flip and scale bump.
- [ ] **Play/pause**: focus play/pause, `adb shell input keyevent 23` → audio toggles; the icon swaps Pause↔Play. Also test the media key fallback `adb shell input keyevent 85`.
- [ ] **Chapter nav**: focus next-chapter, SELECT → position jumps to the next chapter start and the header increments. Prev-chapter SELECT within the first 3 s of a chapter → goes to the previous chapter; after >3 s → restarts the current chapter (Phase 1 "restart if >3s" rule). **Verify** the header label updates.
- [ ] **Chapters panel**: focus the "Chapters" chip (DOWN from transport, then RIGHT/LEFT to the chip), SELECT → the right side panel opens already scrolled to + highlighting the current chapter. DOWN/UP moves the list focus; SELECT on a row jumps and closes. BACK (`adb shell input keyevent 4`) closes the panel and returns focus to the body (play/pause), and does NOT exit the screen.
- [ ] **Speed panel**: open via the speed chip; arrow to `1.5×`; SELECT → playback speed changes audibly and the chip label updates to `1.5×`; panel closes.
- [ ] **Sleep panel**: open via the Sleep chip; pick `End of chapter` → confirm playback pauses when the current chapter ends (or pick `5` min and confirm it pauses). BACK closes the panel.
- [ ] **Back-to-exit + teardown**: with no panel open, BACK → returns to the detail screen; **verify** audio stops (session torn down via `stopPlaybackSession`) and re-opening the item resumes near the last position (resume-on-open).
- [ ] **No-chapters degrade**: open an audiobook with no chapters (single embedded file). **Verify** the "Chapter N of M" header is hidden, the progress bar shows book-relative progress, and prev/next-chapter buttons are dimmed and do nothing on SELECT.

---

### Task 10 — Self-review against spec §4.9 + run full TV test/lint

**Files:** all Task 1–8 files.

- [ ] Re-read spec §4.9 and confirm each bullet is satisfied:
  - cover + metadata ✓ (Task 8 layout)
  - current-chapter header ✓ (`chapterCountLabel()`)
  - chapter progress ✓ (`TvAudiobookProgressBar` fed by `chapterProgress()`)
  - D-pad transport row prev-ch/skip-back/play-pause/skip-fwd/next-ch ✓ (Task 6)
  - chapters/speed/sleep as focusable overlays/side-panels, NOT bottom sheets ✓ (Task 7, full-screen overlays)
  - entry point: audiobook Play from `TvItemDetailScreen`/VM routes here ✓ (Tasks 1, 4, 5)
  - nav destination added to the TV shell ✓ (Task 2 route + Task 5 `composable`)
  - reuses the shared `AudiobookPlayerViewModel` directly, no duplicated logic ✓ (Tasks 3, 8 — chapter/sleep/speed/skip all delegate to the VM)
- [ ] Confirm NO chapter/sleep/speed math was reimplemented in `androidTvApp`. Grep for accidental duplication: `grep -rn "startSeconds\|endSeconds\|coerceIn(0.5f\|EndOfChapter" androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/audiobook/` — only presentational reads (e.g. highlighting) should appear; any computed chapter index or sleep-duration math is a violation → move it to the VM.
- [ ] Run the unit test task: `./gradlew :androidTvApp:testDebugUnitTest`. Confirm `TvAudiobookRoutingTest` passes and nothing else regressed.
- [ ] Run lint: `./gradlew :androidTvApp:lintDebug` (and the repo's `make lint` if convenient). Fix any new warnings introduced by the new files.
- [ ] Final build gate: `./gradlew :androidTvApp:assembleDebug`. Must be green.

---

## Deferred items

- **Watch Next "play" deep links for audiobooks** (Task 5): the launcher deep link carries only `contentId`, not a type, so the `"play"` branch in `TvAppNavigation` stays on the video route. Audiobook deep-link routing requires a type lookup before navigating and is deferred to a Watch Next follow-up (the TV launcher surfaces only video Continue-Watching today).
- **Bookmarks UI on TV**: the shared VM exposes `bookmarks` / `addBookmark` / `jumpToBookmark`, but a TV bookmarks overlay is not in spec §4.9's TV deliverables (the phone keeps bookmarks top-right). Out of scope for Phase 3.
- **Listening-quality controls (skip-silence, volume boost), rich notification, Android Auto, widget**: spec Phases 5–8, not Phase 3. They benefit TV automatically via the shared service once shipped; no TV-screen work needed here.
- **`onMoveUp`/`onMoveDown` focus chaining between the transport row, the secondary chip row, and the cover** is wired as no-ops/simple requesters in this plan; refine vertical focus order during Task 9 if traversal feels wrong on-device (presentational only, no logic change).
