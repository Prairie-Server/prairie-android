# Audiobook Player — Phase 8: Phone Home-Screen Widget — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Add a phone home-screen widget that mirrors the active audiobook now-playing state — cover, title, current chapter ("Chapter N of M — title"), and play/pause + skip-back/skip-forward actions — bound to the shared media session. Tapping the widget opens the audiobook player for the playing item. Android TV has no equivalent home-screen widget, so this work is `androidApp`-only (see spec §8 — widget is phone-only; a TV recommendation-channel row is a separate future effort).

**Architecture:** A Jetpack Glance `AudiobookWidget` (`GlanceAppWidget`) renders from a pure, serializable `WidgetUiState` snapshot persisted in the widget's Glance state (`PreferencesGlanceStateDefinition`). A `WidgetMediaBridge` (Glance side does not hold a long-lived `MediaController`, which would leak across the widget worker boundary) binds a short-lived `MediaController` to the existing `com.continuum.app.common.player.ContinuumPlaybackService` session, reads a snapshot via a pure `mapSessionToWidgetState(...)` mapper, writes it into Glance state, and calls `AudiobookWidget.updateAll(context)`. The bridge is invoked (a) on demand when the widget requests a refresh, and (b) reactively when session playback state changes, via a `Player.Listener` registered in the service that calls the bridge. Widget action buttons send Media3 transport commands (and the Phase 6 custom chapter commands `SKIP_TO_PREV_CHAPTER` / `SKIP_TO_NEXT_CHAPTER`) through a short-lived controller inside a Glance `ActionCallback`. Chapter labels reuse the pure `AudiobookChapters` math added in Phase 1.

**Tech Stack:** Kotlin, Jetpack Glance (`androidx.glance:glance-appwidget`), Media3 session (`MediaController` bound to `ContinuumPlaybackService`), DataStore-backed Glance state, Coil (cover bitmap fetch off the main thread).

---

## File Structure

All new code is phone-only, under `androidApp`. Real paths:

- `gradle/libs.versions.toml` — add Glance version + library aliases (catalog edit).
- `androidApp/build.gradle.kts` — add Glance dependencies to `androidMain`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/widget/WidgetUiState.kt` — pure, serializable now-playing snapshot data class + `WidgetUiState.EMPTY`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/widget/SessionWidgetMapper.kt` — pure `mapSessionToWidgetState(...)`: Media3 `MediaMetadata` + position + chapters → `WidgetUiState`. **TDD this.**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/widget/WidgetStateRepository.kt` — read/write `WidgetUiState` into Glance `PreferencesGlanceStateDefinition` (JSON blob under one key).
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/widget/WidgetMediaBridge.kt` — binds a short-lived `MediaController`, builds the snapshot, persists it, fetches cover bitmap, triggers `updateAll`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/widget/AudiobookWidget.kt` — the `GlanceAppWidget`: `provideGlance` + `Content()` composable rendering cover/title/chapter + action buttons.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/widget/AudiobookWidgetReceiver.kt` — `GlanceAppWidgetReceiver` (manifest-registered provider) that refreshes via the bridge on `onUpdate` / `onEnabled`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/widget/WidgetActions.kt` — Glance `ActionCallback`s: `PlayPauseAction`, `SkipBackAction`, `SkipForwardAction` (transport via short-lived controller).
- `androidApp/src/androidMain/res/xml/audiobook_widget_info.xml` — `appwidget-provider` metadata (sizing, resize, preview, update period).
- `androidApp/src/androidMain/res/drawable/widget_preview.xml` — vector preview shown in the widget picker.
- `androidApp/src/androidMain/AndroidManifest.xml` — register the `AudiobookWidgetReceiver` provider.
- `androidApp/src/androidMain/res/values/strings.xml` — widget label + content descriptions.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt` — register a `Player.Listener` that pings a phone-supplied `PlaybackStateObserver` so the widget refreshes on play/pause/chapter changes (additive; no video behavior change).
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ContinuumApplication.kt` — wire the observer into the service on app start (existing file; verify path in Task 7).
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/widget/SessionWidgetMapperTest.kt` — unit tests for the mapper.

**Dependencies on earlier phases:** Phase 1 supplies the pure `shared` `AudiobookChapters` math (current index / count label) reused by the mapper. Phase 6 supplies chapter info on the session's `MediaMetadata` (extras) and the custom session commands `SKIP_TO_PREV_CHAPTER` / `SKIP_TO_NEXT_CHAPTER`. If a worker reaches this plan and either is missing, see the per-task fallbacks (the mapper degrades to title-only; skip buttons fall back to ±skip-interval seeks).

---

### Task 1 — Add Glance dependency to the version catalog and `androidApp`

**Files:**
- `gradle/libs.versions.toml`
- `androidApp/build.gradle.kts`

Glance is **not** currently on the classpath (verified: no `glance` entries in `gradle/` or any build file). Add it. Glance `1.1.1` is the current stable release, requires `compileSdk >= 34` (repo is 36) and `minSdk >= 21` (repo `androidApp` minSdk is 24), and is compatible with Kotlin 2.1.20 / the repo's Compose setup (Glance ships its own runtime and does not require the Compose Multiplatform compiler on the widget code path).

- [ ] In `gradle/libs.versions.toml`, under `[versions]`, add: `glance = "1.1.1"`.
- [ ] In `gradle/libs.versions.toml`, under `[libraries]`, add:
  ```toml
  glance-appwidget = { module = "androidx.glance:glance-appwidget", version.ref = "glance" }
  glance-material3 = { module = "androidx.glance:glance-material3", version.ref = "glance" }
  ```
- [ ] In `androidApp/build.gradle.kts`, inside `androidMain.dependencies { ... }`, add:
  ```kotlin
  implementation(libs.glance.appwidget)
  implementation(libs.glance.material3)
  ```
- [ ] Run `./gradlew :androidApp:help` (cheap config-only task) to confirm the catalog + build script resolve with the new coordinates. Expect `BUILD SUCCESSFUL`.

---

### Task 2 — Define `WidgetUiState` (pure, serializable snapshot)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/widget/WidgetUiState.kt`

This is the single source of truth the Glance composable renders. It must be plain/serializable (JSON) so it can round-trip through Glance `PreferencesGlanceStateDefinition`.

- [ ] Create `WidgetUiState.kt` with package `com.continuum.app.android.widget`.
- [ ] Define a `@Serializable data class WidgetUiState`:
  ```kotlin
  @Serializable
  data class WidgetUiState(
      val contentId: String? = null,
      val fileId: Int? = null,
      val title: String = "",
      val author: String? = null,
      val coverUrl: String? = null,
      val chapterLabel: String? = null, // e.g. "Chapter 7 of 111 · The Crossing"
      val isPlaying: Boolean = false,
      val hasActiveBook: Boolean = false,
  ) {
      companion object {
          val EMPTY = WidgetUiState()
      }
  }
  ```
- [ ] Use `kotlinx.serialization.Serializable` (the `kotlinx-serialization-json` artifact is already on `androidApp`'s test classpath; confirm it is also on the main classpath — `:shared` is depended on via `implementation`, so add `implementation(libs.kotlinx.serialization.json)` to `androidApp` `androidMain.dependencies` if compilation fails for missing `@Serializable`).

---

### Task 3 — TDD the pure session→widget mapper

**Files:**
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/widget/SessionWidgetMapperTest.kt`
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/widget/SessionWidgetMapper.kt`

REQUIRED SUB-SKILL: Use superpowers:test-driven-development — write the test first, watch it fail, then implement.

The mapper is pure: it takes already-extracted primitives (no Android framework types in the signature, so it runs under plain JVM unit tests) and produces a `WidgetUiState`. Chapter labelling delegates to the Phase 1 pure `AudiobookChapters` in `:shared`.

Signature to implement:
```kotlin
fun mapSessionToWidgetState(
    contentId: String?,
    fileId: Int?,
    title: String?,
    author: String?,
    coverUrl: String?,
    positionSeconds: Double,
    chapters: List<com.continuum.app.model.catalog.VersionChapter>,
    isPlaying: Boolean,
    hasMediaItem: Boolean,
): WidgetUiState
```

- [ ] Write `SessionWidgetMapperTest.kt` first. Cases:
  - [ ] `hasMediaItem = false` (nothing loaded) → returns `WidgetUiState.EMPTY` (`hasActiveBook == false`, `title == ""`).
  - [ ] `hasMediaItem = true`, blank title → `hasActiveBook == true`, `title == ""` still allowed but `hasActiveBook` true (book loaded, metadata sparse).
  - [ ] Title + author present, **empty** chapter list → `chapterLabel == null` (chapter chrome hidden), `title`/`author` passed through, `hasActiveBook == true`.
  - [ ] Chapters present, `positionSeconds` inside chapter index 6 of 111 → `chapterLabel == "Chapter 7 of 111 · <title>"` (1-based display; uses `AudiobookChapters.currentIndex(...)` from Phase 1). If the resolved chapter title is blank, label is `"Chapter 7 of 111"` (no trailing separator).
  - [ ] `positionSeconds` exactly on a chapter boundary resolves to the chapter that **starts** at that second (matches Phase 1 boundary rule — assert against the same `AudiobookChapters` helper, do not re-implement).
  - [ ] `isPlaying` passes straight through to `WidgetUiState.isPlaying`.
- [ ] Run `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.widget.SessionWidgetMapperTest"` and confirm it FAILS to compile / fails assertions (mapper not yet written).
- [ ] Implement `SessionWidgetMapper.kt`:
  - [ ] If `!hasMediaItem` → return `WidgetUiState.EMPTY`.
  - [ ] Compute `chapterLabel`: if `chapters.isEmpty()` → `null`; else `val idx = com.continuum.app.common.audiobook.AudiobookChapters.currentIndex(positionSeconds, chapters)` (use the exact Phase 1 symbol — grep `shared/` and `android-shared/` for `AudiobookChapters` to confirm the package/function name before wiring; the spec §4.2/§6 Phase 1 places it in `shared` commonMain). Build `"Chapter ${idx + 1} of ${chapters.size}"`, append `" · ${chapters[idx].title}"` only when that title is non-blank.
  - [ ] Return a `WidgetUiState` with `hasActiveBook = true` and the passed fields.
  - [ ] **Fallback if Phase 1 is not yet merged:** compute the index inline with a documented TODO referencing Phase 1 — `chapters.indexOfLast { positionSeconds >= it.startSeconds }.coerceAtLeast(0)` — and keep the same label format so the eventual swap is mechanical. Do not invent a new shared symbol.
- [ ] Run the same `--tests` command again and confirm all cases PASS.

---

### Task 4 — Glance state repository (persist the snapshot)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/widget/WidgetStateRepository.kt`

Glance composables can only read state via `currentState`. We persist the whole `WidgetUiState` as a JSON string under one preferences key in the widget's own Glance state store.

- [ ] Create `WidgetStateRepository.kt`.
- [ ] Define the key: `val WIDGET_STATE_KEY = stringPreferencesKey("audiobook_widget_state_json")`.
- [ ] `suspend fun writeState(context: Context, state: WidgetUiState)` — for every glance id of `AudiobookWidget`, call `updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs -> prefs.toMutablePreferences().apply { this[WIDGET_STATE_KEY] = Json.encodeToString(state) } }`. Enumerate glance ids via `GlanceAppWidgetManager(context).getGlanceIds(AudiobookWidget::class.java)`.
- [ ] Provide `fun readState(prefs: Preferences): WidgetUiState` — decode `prefs[WIDGET_STATE_KEY]` (null/blank → `WidgetUiState.EMPTY`), wrapped in `runCatching { Json.decodeFromString<WidgetUiState>(it) }.getOrDefault(WidgetUiState.EMPTY)` so a malformed blob never crashes the widget host.
- [ ] Use a single private `Json { ignoreUnknownKeys = true }` instance.

---

### Task 5 — `WidgetMediaBridge` (read session → persist → updateAll)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/widget/WidgetMediaBridge.kt`

This is the only place that talks to the media session for *reads*. It binds a short-lived `MediaController` (same pattern as `AudiobookPlayerScreen.kt:88` — `SessionToken(context, ComponentName(context, ContinuumPlaybackService::class.java))`), snapshots, releases, persists, and refreshes the widget. It must never retain the controller (avoids the leak Glance workers are prone to).

- [ ] Create `object WidgetMediaBridge`.
- [ ] `suspend fun refresh(context: Context)`:
  - [ ] Build a `MediaController` via `MediaController.Builder(context, SessionToken(context, ComponentName(context, ContinuumPlaybackService::class.java))).buildAsync()`; `await()` it (wrap the `ListenableFuture` with `kotlinx.coroutines.guava.await` if available, else suspend via `suspendCancellableCoroutine` + `future.addListener(..., MoreExecutors.directExecutor())` mirroring the screen). On any failure persist `WidgetUiState.EMPTY` and return.
  - [ ] On the main thread, read from the controller: `hasMediaItem = controller.currentMediaItem != null`, `mediaMetadata` (title/artist/artworkUri), `currentPosition` (ms → seconds), `isPlaying`.
  - [ ] Extract `contentId` / `fileId` / chapter list from the media item's `MediaMetadata.extras` **as written by Phase 6** (grep `ContinuumPlayerFactory.kt` around line 235 and the Phase 6 work for the exact extras keys before wiring; do not invent keys). If extras are absent (Phase 6 not merged), pass `contentId = null`, `fileId = null`, `chapters = emptyList()` — the mapper degrades to title-only and the widget still works.
  - [ ] Call `mapSessionToWidgetState(...)`.
  - [ ] Release the controller (`controller.release()`).
  - [ ] Persist via `WidgetStateRepository.writeState(context, state)`.
  - [ ] Call `AudiobookWidget().updateAll(context)`.
  - [ ] All controller interaction must run on `Dispatchers.Main` (Media3 `MediaController` is main-thread-confined); the cover fetch (Task 6) and persistence run off-main.

---

### Task 6 — Cover bitmap loading for the widget

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/widget/WidgetMediaBridge.kt` (extend)

Glance cannot load a remote URL directly into an `Image`; it needs a `Bitmap` (`ImageProvider(bitmap)`) or a resource. Fetch the cover with Coil (already a dependency) off the main thread and cache the result so updates don't re-download.

- [ ] In `WidgetMediaBridge`, after computing `state`, if `state.coverUrl` is non-blank, load it with Coil: build an `ImageRequest` with `.allowHardware(false)` (Glance/RemoteViews cannot serialize hardware bitmaps) and a bounded size (e.g. 256×256 via `.size(256)`), execute against the app's Coil `ImageLoader`, and on success write the bitmap to a stable cache file `context.cacheDir/audiobook_widget_cover.png`.
- [ ] Store the cover file path (or a content-hash suffix) in `WidgetUiState` is unnecessary — instead the widget composable reads the fixed cache file each render. Add `val coverCacheStamp: Long = 0L` to `WidgetUiState` so the composable's `remember`/decode key changes when the cover updates; bump it to `System.currentTimeMillis()` whenever a new cover is written.
- [ ] If the cover fails to load or `coverUrl` is blank, leave the cache file as-is and the composable falls back to a placeholder drawable (Task 8).
- [ ] Update `SessionWidgetMapperTest` only if the mapper signature changes (it should not — cover stamping happens in the bridge, not the pure mapper). Re-run `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.widget.SessionWidgetMapperTest"`.

---

### Task 7 — Reactive refresh: observe session playback-state changes

**Files:**
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt`
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ContinuumApplication.kt`

The widget must update on play/pause and chapter changes, not only on the system's ~30-min update period. The shared service exposes a hook the phone app fills; TV passes nothing, so no TV behavior changes.

- [ ] In `ContinuumPlaybackService.kt`, add a process-level, nullable observer the app can set:
  ```kotlin
  fun interface PlaybackStateObserver { fun onPlaybackStateChanged() }
  companion object { @Volatile var playbackStateObserver: PlaybackStateObserver? = null }
  ```
  (Placed in the existing `companion object`; keep it additive — do not touch `onTaskRemoved`, `onGetSession`, or the existing position/audio/subtitle jobs.)
- [ ] In `onCreate()`, after the player is built, register a `Player.Listener`:
  ```kotlin
  player.addListener(object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) { playbackStateObserver?.onPlaybackStateChanged() }
      override fun onMediaItemTransition(item: MediaItem?, reason: Int) { playbackStateObserver?.onPlaybackStateChanged() }
      override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) { playbackStateObserver?.onPlaybackStateChanged() }
  })
  ```
  Chapter-crossing refreshes piggyback on the existing 500 ms `positionJob`: add `playbackStateObserver?.onPlaybackStateChanged()` inside that loop **only when the computed chapter index changed** (cache last index in a local var) so the widget label tracks chapters without spamming `updateAll` four times a second.
- [ ] Remove the listener in `onDestroy()` is unnecessary (player is released there), but null nothing global — leave `playbackStateObserver` owned by the app.
- [ ] In `ContinuumApplication.kt` (`onCreate`), set the observer to debounce-and-refresh:
  ```kotlin
  ContinuumPlaybackService.playbackStateObserver = ContinuumPlaybackService.PlaybackStateObserver {
      WidgetRefreshScheduler.requestRefresh(this) // coalesces bursts, runs WidgetMediaBridge.refresh off-main
  }
  ```
- [ ] Add a tiny `WidgetRefreshScheduler` (in `androidApp/.../widget/`) that coalesces calls within ~300 ms (e.g. a single `MutableSharedFlow` + `conflate()` collected on a process `CoroutineScope`, or a debounced `Handler`) and invokes `WidgetMediaBridge.refresh(context)`. Reuse `lifecycle-process` `ProcessLifecycleOwner` scope already on the classpath (`libs.lifecycle.process`).
- [ ] Verify the exact `ContinuumApplication.kt` path with `ls androidApp/src/androidMain/kotlin/com/continuum/app/android/ContinuumApplication.kt`; if `onCreate` wiring lives in a Koin module instead, wire the observer where the other app singletons are initialized.
- [ ] Build: `./gradlew :android-shared:compileDebugKotlinAndroid :androidApp:compileDebugKotlinAndroid` — expect `BUILD SUCCESSFUL`, and re-run the full video-relevant unit tests `./gradlew :android-shared:testDebugUnitTest` to confirm the additive service change broke nothing.

---

### Task 8 — The `AudiobookWidget` Glance composable

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/widget/AudiobookWidget.kt`
- `androidApp/src/androidMain/res/drawable/widget_preview.xml`

Real Glance code rendering cover + title + chapter + the three transport buttons, plus the tap-to-open target.

- [ ] Create `class AudiobookWidget : GlanceAppWidget()`.
- [ ] Set `override val stateDefinition = PreferencesGlanceStateDefinition`.
- [ ] Implement `override suspend fun provideGlance(context: Context, id: GlanceId)`:
  ```kotlin
  provideContent {
      val prefs = currentState<Preferences>()
      val state = WidgetStateRepository.readState(prefs)
      WidgetContent(context, state)
  }
  ```
- [ ] Implement the `@Composable private fun WidgetContent(context: Context, state: WidgetUiState)` using Glance composables (`androidx.glance.layout.*`, `androidx.glance.text.Text`, `androidx.glance.Image`, `androidx.glance.appwidget.Button`/`Image` with `clickable`). Layout: a `Row` with cover `Image` (left) and a `Column` (title `Text` maxLines 1, chapter `Text` maxLines 1 shown only when `state.chapterLabel != null`) over a transport `Row` (skip-back, play/pause, skip-forward).
- [ ] Empty state: when `!state.hasActiveBook`, render a single centered `Text` "No audiobook playing" and a cover placeholder; the whole widget opens the app to the audiobook library (or Home).
- [ ] Cover: decode the cached file from Task 6 with `BitmapFactory.decodeFile(File(context.cacheDir, "audiobook_widget_cover.png").path)` keyed on `state.coverCacheStamp`; if null, use `ImageProvider(R.drawable.widget_preview)` placeholder. Wrap in `ImageProvider(bitmap)`.
- [ ] Play/pause icon: choose `R.drawable`/material icon by `state.isPlaying` (use existing `androidx.media3` or app drawables; if none suitable exist, add two small vector drawables `ic_widget_play.xml` / `ic_widget_pause.xml` to `res/drawable/`). Skip icons similarly (`ic_widget_skip_back.xml`, `ic_widget_skip_forward.xml`).
- [ ] Tap-to-open: set `modifier = GlanceModifier.clickable(actionStartActivity(...))` on the cover/title region. Build an `Intent` to `MainActivity` with the audiobook deep-link route. **The route** is `Route.AudiobookPlayer(contentId, fileId).route` → `"audiobook/$contentId?fileId=$fileId"` (verified in `androidApp/.../ui/navigation/Routes.kt:162`). Deep-link into the app via the existing `prairie://device` scheme is for device-login only; instead launch `MainActivity` with an extra (e.g. `intent.putExtra("startRoute", "audiobook/${state.contentId}?fileId=${state.fileId}")`) and have `MainActivity` consume it (Task 9). When `state.contentId == null`, open `MainActivity` plainly (no extra).
- [ ] Transport buttons set `onClick = actionRunCallback<PlayPauseAction>()` etc. (Task 10), with `contentDescription` from string resources.
- [ ] Create `res/drawable/widget_preview.xml` — a simple vector (book + play glyph) used both as the picker preview and the cover placeholder.

---

### Task 9 — `MainActivity` consumes the widget's start-route extra

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/MainActivity.kt`

The widget opens the player by handing `MainActivity` a nav route. Reuse the existing external-route plumbing (`pendingExternalRoute` / `incomingDeviceLoginRoutes`) rather than inventing a parallel path.

- [ ] In `MainActivity.onCreate`, after `resolveStartDestination()` plumbing, read `intent.getStringExtra("startRoute")`; if present and the user is past auth (`resolveStartDestination()` returned `Route.Home.route`), emit it through `incomingDeviceLoginRoutes` (rename is optional; the flow already feeds `pendingExternalRoute` → `AppNavigation(pendingExternalRoute = ...)`). If the user is not authenticated, ignore the extra (land on the auth flow).
- [ ] In `onNewIntent`, also check `intent.getStringExtra("startRoute")` and `tryEmit` it (the widget may launch while the app is already running).
- [ ] Confirm `AppNavigation` navigates to a raw route string from `pendingExternalRoute`; the route `"audiobook/{contentId}?fileId={fileId}"` is already a registered `composable` (`AppNavigation.kt:485`), so no nav-graph change is needed.
- [ ] Build: `./gradlew :androidApp:compileDebugKotlinAndroid`.

---

### Task 10 — Widget action callbacks (transport)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/widget/WidgetActions.kt`

Each button binds a short-lived `MediaController`, issues the command, releases, then triggers a refresh so the widget reflects the new state.

- [ ] Implement `class PlayPauseAction : ActionCallback`:
  ```kotlin
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
      WidgetMediaBridge.withController(context) { controller ->
          if (controller.isPlaying) controller.pause() else controller.play()
      }
      WidgetMediaBridge.refresh(context)
  }
  ```
- [ ] Add a helper `suspend fun withController(context: Context, block: suspend (MediaController) -> Unit)` to `WidgetMediaBridge` (binds on `Dispatchers.Main`, runs `block`, releases) so the actions and `refresh` share one binding path.
- [ ] `SkipBackAction` / `SkipForwardAction`:
  - [ ] Preferred: send the Phase 6 chapter commands? No — skip buttons in §4.7 are skip-back / skip-forward (the ±interval transport), not chapter nav. Use `controller.seekTo(controller.currentPosition ± skipMs)`.
  - [ ] Read the skip interval from the Phase 4 `AudiobookSettingsStore` (`skipBackSeconds` / `skipForwardSeconds`) if available; grep `android-shared/.../player/AudiobookSettingsStore.kt`. If that store is not yet merged, default to ±30 s (the spec's current behavior) with a TODO referencing Phase 4. Clamp to `[0, duration]`.
  - [ ] After seeking, call `WidgetMediaBridge.refresh(context)`.
- [ ] If a binding fails (no active session), the callbacks no-op gracefully (the `withController` helper returns without throwing) and still call `refresh` (which will persist `EMPTY`).

---

### Task 11 — Widget provider info XML + manifest registration + receiver

**Files:**
- `androidApp/src/androidMain/res/xml/audiobook_widget_info.xml`
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/widget/AudiobookWidgetReceiver.kt`
- `androidApp/src/androidMain/AndroidManifest.xml`
- `androidApp/src/androidMain/res/values/strings.xml`

- [ ] Create `res/xml/audiobook_widget_info.xml`:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
      android:minWidth="250dp"
      android:minHeight="110dp"
      android:targetCellWidth="4"
      android:targetCellHeight="2"
      android:resizeMode="horizontal|vertical"
      android:widgetCategory="home_screen"
      android:updatePeriodMillis="1800000"
      android:previewImage="@drawable/widget_preview"
      android:description="@string/audiobook_widget_description"
      android:initialLayout="@layout/glance_default_loading_layout" />
  ```
  (`@layout/glance_default_loading_layout` is provided by the Glance library; `updatePeriodMillis` is a coarse safety net — real-time updates come from Task 7's observer.)
- [ ] Create `class AudiobookWidgetReceiver : GlanceAppWidgetReceiver()` with `override val glanceAppWidget: GlanceAppWidget = AudiobookWidget()`. Override `onUpdate` and `onEnabled` to launch `WidgetMediaBridge.refresh(context)` on a `goAsync()`-backed coroutine so a freshly-placed widget populates immediately.
- [ ] In `AndroidManifest.xml`, inside `<application>`, register the provider (additive — leave the existing `ContinuumPlaybackService`, FileProvider, WorkManager entries untouched):
  ```xml
  <receiver
      android:name=".widget.AudiobookWidgetReceiver"
      android:exported="false">
      <intent-filter>
          <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
      </intent-filter>
      <meta-data
          android:name="android.appwidget.provider"
          android:resource="@xml/audiobook_widget_info" />
  </receiver>
  ```
  Note: the package is `com.continuum.app.android` (manifest `namespace`), so `.widget.AudiobookWidgetReceiver` resolves correctly.
- [ ] In `res/values/strings.xml`, add:
  ```xml
  <string name="audiobook_widget_label">Continuum Audiobook</string>
  <string name="audiobook_widget_description">Now-playing audiobook controls</string>
  <string name="audiobook_widget_play">Play</string>
  <string name="audiobook_widget_pause">Pause</string>
  <string name="audiobook_widget_skip_back">Skip back</string>
  <string name="audiobook_widget_skip_forward">Skip forward</string>
  ```
- [ ] Build the debug APK: `./gradlew :androidApp:assembleDebug`. Expect `BUILD SUCCESSFUL`.

---

### Task 12 — Manual on-device verification (Pixel phone)

The widget needs a real home screen; it cannot be asserted in a unit test. Run these adb steps on a connected phone (`adb devices` shows one device). Commands assume the repository root is the cwd.

- [ ] Install: `./gradlew :androidApp:installDebug`.
- [ ] Start playing an audiobook (open the app → an audiobook detail → Play). Leave it playing.
- [ ] Add the widget: long-press the home screen → Widgets → find "Continuum Audiobook" (label from strings.xml) → drag to the home screen. **Verify** the picker shows the `widget_preview` image and the label.
- [ ] **Verify metadata:** the placed widget shows the cover, the book title, and "Chapter N of M · <title>" matching the in-app current chapter. Cross-check against the in-app player's current-chapter header (Phase 2).
- [ ] **Verify play/pause:** tap the play/pause button on the widget → app audio pauses/resumes and the widget icon flips within ~1 s (Task 7 observer + Task 10 refresh). Confirm with `adb shell dumpsys media_session | grep -A3 Continuum` showing the state change.
- [ ] **Verify skip:** tap skip-forward / skip-back → position jumps by the configured interval (±30 s if Phase 4 not merged); the widget and the in-app slider agree.
- [ ] **Verify chapter tracking:** let playback cross a chapter boundary (or skip to one) → the widget's "Chapter N of M" label advances without manual refresh.
- [ ] **Verify tap-to-open:** tap the cover/title region → the app opens directly on the audiobook player for the playing book (route `audiobook/<contentId>?fileId=<fileId>`). Confirm via `adb logcat | grep -i "audiobook"` showing the nav.
- [ ] **Verify empty state:** stop playback / swipe the app away (triggers `onTaskRemoved` teardown) → trigger a widget refresh (wait for the observer or `adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n com.continuum.app/.widget.AudiobookWidgetReceiver`) → widget shows "No audiobook playing"; tapping opens the app to Home/library.
- [ ] **Regression (shared service):** play a **video** (the service is shared) and confirm lock-screen controls, headset play/pause, and the swipe-away teardown still work — the Task 7 listener is additive and must not regress video. Spec §4.6 flags this service as the highest-risk shared component.

---

### Task 13 — Self-review against the spec, fix inline

- [ ] §4.7 requires cover, title, current chapter, **play/pause + skip actions**, bound to session playback state, tap-opens-player — confirm Tasks 8/10/9 cover all five. ✔ if the manual pass (Task 12) verified each.
- [ ] §4.5 requires chapter info to flow from session `MediaMetadata` — confirm Task 5 reads Phase 6 extras (or degrades cleanly). If Phase 6 keys differ from assumptions, fix the extraction in `WidgetMediaBridge` and re-run Task 12's metadata check.
- [ ] §8 requires **phone-only** — confirm nothing was added to `androidTvApp` and the receiver lives only in `androidApp`'s manifest. Grep `androidTvApp/src/androidMain/AndroidManifest.xml` to confirm no widget provider leaked in.
- [ ] §6 Phase 8 "update on playback-state changes" — confirm Task 7's observer fires `updateAll` on play/pause/chapter, not only on the 30-min system period.
- [ ] Run `./gradlew :androidApp:testDebugUnitTest` (full module) + `./gradlew :android-shared:testDebugUnitTest` — both green.
- [ ] Run the repo's lint per CLAUDE.md before MR: `./gradlew :androidApp:lintDebug` (or the project's `make lint` equivalent if defined for Android).

---

## Deferred / out of scope

- **TV widget** — Android TV has no home-screen widget; explicitly excluded (spec §8). A TV recommendation-channel row is a separate future effort, not part of this plan.
- **Chapter-skip buttons on the widget** — §4.7 specifies skip-back/skip-forward (±interval), not prev/next-chapter, so the Phase 6 `SKIP_TO_*_CHAPTER` session commands are not surfaced on the widget. Could be added later as a 5th/6th button.
- **Live cover crossfade / multiple widget sizes beyond resize** — single responsive layout only.
- **Hard dependency on Phases 1, 4, 6** — the plan degrades gracefully (title-only labels, ±30 s default skip, inline index math) so Phase 8 can be built and shipped even if those phases land afterward; swap the fallbacks for the real shared symbols once merged.
