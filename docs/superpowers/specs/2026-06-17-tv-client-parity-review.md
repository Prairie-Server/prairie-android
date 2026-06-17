# TV client review — solidity + mobile parity (2026-06-17)

Joint review (Claude + Codex, 4 parallel passes: playback, focus/navigation, breadth/crashes, TV-vs-phone parity) of `androidTvApp` (168 files, ~31k LOC). Findings are static-analysis + targeted reads; **playback criticals warrant device verification**. Severity: Critical / High / Med / Low. Line refs as of this date.

## What's already solid (parity confirmed)
Player has parity for: playback speed, sleep timer, subtitle search/download, AI subtitle translation, chapters/markers + intro-skip, HDR controls. Audio/subtitle track selection correctly uses the **shared mounted backend** (`videoBackend.selectAudioTrack/selectSubtitle`); only external-subtitle add rebuilds the same MediaItem (matches the shared mounter) — the "screen rebuilds MediaItem" comments in `TvPlayerViewModel` are **stale**. `mark-watched` is a no-op on TV *and* phone (pending a shared API) — not a TV regression.

## Critical
1. **Video surface bound to `MediaController`, not the real session player.** `TvPlayerScreen.kt:701` sets `PlayerView.player = mediaController`; phone binds the PlayerView to the actual `ExoPlayer/MpvPlayer` via `ActivePlayerHolder` (`PlayerScreen.kt:654-657`) because surface lifecycle must attach to the real player. Risk: **black screen after seek / surface recreate / engine swap, especially MPV**. Fix: inject `ActivePlayerHolder`, bind the PlayerView to its `sessionPlayer`, keep the MediaController only for transport/session commands. *(Verify on-device with the MPV backend.)*

## High
2. **Final resume position lost on exit (regression).** `TvPlayerViewModel.onCleared` (`:1220`) cancels jobs and fire-and-forgets `sessionLifecycle.stop()`; it never persists the final position. Phone fixed this (`PlayerViewModel.kt:1290`) with `runBlocking(NonCancellable + Dispatchers.IO)` recordPosition. *(Confirmed independently.)* Fix: mirror the phone teardown.
3. **Screen dispose leaves the service playing.** `TvPlayerScreen.kt:253` only `mediaController.release()`s — no pause/stop/clear or VM teardown. Navigating away outside the BackHandler path can leave service playback + session alive. Fix: stop/clear on dispose + durable VM teardown.
4. **Playback errors not surfaced.** `TvPlayerScreen.kt:421` passes only `onUnsupported` (not `onError`) to `PlaybackPreflightListener`; `TvAudiobookPlayerScreen.kt:161` lacks `onPlayerError`. Decoder/source/401 after prepare → stale spinner. Fix: add VM `onPlayerError`, map decoder-init to transcode fallback, else show an actionable error.
5. **Back falls through from the top menu.** `TvMainShell.kt:255` / `TvTopMenuBar.kt:180` — Back is only handled when focus is in content; focused on the top menu, Back can reach the activity (exit). Fix: shell-level `BackHandler` ancestoring both menu + content.
6. **Secondary routes flatten the back stack.** `TvMainShell.kt:155/388` uses `popUpTo(start)` for Settings→Favorites/Watchlist/History/Collections + profile-menu Inbox, so Back returns Home, not the parent. Fix: only root tabs `popUpTo(start)`; secondary routes push normally.
7. **D-pad Up double-moves focus.** `TvMainShell.kt:257` manually `moveFocus(Up)` then returns `false`, so Compose processes Up again. Fix: consume when handled.
8. **Hidden top menu can take invisible focus.** `TvMainShell.kt:209` / `TvTopMenuBar.kt:163` — at `menuVisibility==0`, Up can focus an off-screen/faded control. Fix: snap visibility to 1f before focusing, or block focus while hidden.
9. **Hero "Down" targets an unattached FocusRequester.** `TvHomeHeroCarousel.kt:249`, `TvLibraryDetailScreen.kt:228` — featured-only pages pass `firstRowFocusRequester` with no row → dead-end / uninitialized-requester crash. Fix: only set the down target when attached.
10. **Detail focuses invisible passive text.** `TvItemDetailScreen.kt:243/640` — Down from Cast focuses non-actionable Details/About columns (no focus ring) → "nothing focused." Fix: don't make passive text focusable, or give it visible focus + traversal.
11. **Watch Together host-leave can be cancelled mid-teardown.** `TvWatchTogetherLobbyScreen.kt:134` + VM `:71` — `leave(closeRoom=true)` runs in `viewModelScope`, then `onBack()` pops immediately; VM clear can cancel before `closeRoom()`/`reset()`, stranding guests + dirtying the singleton repo. Fix: await leave (or app-scope it) before navigating.
12. **Device-login polling never stops backgrounded/offline.** `TvLoginViewModel.kt:62` + `DeviceLoginRepository.kt:96` — QR poll starts in `init`, treats network errors as transient forever, ignores `expiresAt`. Fix: lifecycle-scope the poll; cap at session expiry.
13. **Music/audio libraries misclassified as "movie."** `TvMediaTypeFilters.kt:35` (`tvCatalogMediaTypeFor`) falls through to `"movie"`; shared taxonomy maps music/audio to `MediaMode.Audio` (`MediaMode.kt:39`). Fix: use the shared taxonomy; don't force `mediaType` when a `libraryId` is present (phone doesn't).

## Medium — divergent / stale / missing
- **No player retry.** `TvPlayerScreen.kt:675` `onRetry=null`, `loadContent()` private → stranded on transient failure. Add `retry()`.
- **Watch-Together seek authority bypass.** Chapter focus seeks directly (`TvPlayerHud.kt:684`/`TvPlayerScreen.kt:838`); `transportEnabled`/`playPauseEnabled` aren't applied to the scrubber/transport (`:1039`). A guest can desync. Fix: gate via `tvRoomTransportGate`/`roomController.onUserSeek`.
- **HUD capabilities can lie after MPV swap.** Backend derived from MediaController (`TvPlayerScreen.kt:193`) → reports Media3 even on MPV. Fix: derive engine from the service/`ActivePlayerHolder`.
- **Subtitle UI updates before `selectSubtitle()` succeeds** (`TvPlayerScreen.kt:228`). Apply only on success.
- **Initial audio/subtitle selection dropped.** `TvVideoPlaybackStarter.kt:69` ignores `request.audioTrackIndex`; TV `Player` route (`TvRoute.kt:52`) lacks audio/subtitle indices that phone carries (`Routes.kt:130`, wired in `ItemDetailScreen.kt:401`).
- **Audio switch is local-only.** TV `selectAudioTrack` (`TvPlayerScreen.kt:817`) vs phone server `changeAudio` (`PlayerViewModel.kt:672`) — single-audio transcoded streams may not switch on TV.
- **Video-track picker is a no-op.** Rendered (`TvPlayerHud.kt:389`) but ignored (`TvPlayerScreen.kt:825`). Hide it or implement.
- **Cast cards non-navigable.** `TvCastCrewSection.kt:135` `onClick={}` — no person detail (phone routes `PersonDetail`).
- **"More Like This" is a browse approximation.** `TvItemDetailViewModel.kt:281` (`browse sort=rating_imdb`) vs phone similar-recommendations.
- **Requests bypass the shared flow.** `TvRequestsScreen.kt:165` calls `repository.create()` from a composable scope, builds `CreateMediaRequest` from search results lacking `tvdbId/imdbId`; phone uses shared `RequestDetailViewModel`. Risk: cancelled POST, poorer/duplicate requests.
- **Card action state keyed only by `contentId`.** `TvCardActionsHelpers.kt:30` → stale watched/favorite badges after refresh; phone keys on `(contentId, userState)` (`CardActionsHelpers.kt:33`).
- **Collection detail bypasses TV media filter.** `TvLibraryCollectionDetailViewModel.kt:41` / `TvCollectionDetailViewModel.kt:58` render raw items → ebooks can appear on TV and lead to a reader-less detail/player. Use `visibleOnTv()`.
- **Plain `collectAsState()`** across `TvLoginScreen:76`, `TvRequestsScreen:95`, `TvSettingsScreen:70`, `TvWatchTogetherLobbyScreen:97` — should be `collectAsStateWithLifecycle()`.
- **Focus restoration gaps.** Library filter sheet (`TvLibraryDetailScreen.kt:385`) and raw `LazyVerticalGrid`s (`:540/:639`) lack `focusRestorer`; detail hero↔section traversal underwired (`TvItemDetailScreen.kt:150`, `TvDetailHero.kt:162`).
- **Nav robustness.** Missing args → blank destinations (`TvAppNavigation.kt:343/377`); rapid-OK stacks duplicates (no `launchSingleTop`, `:163/:231`); deep links consumed before auth/profile ready (`:59`).
- **Search drift.** Hard-coded filters (`TvSearchViewModel.kt:20`) vs phone's library-derived modes; no auto-advance when a filtered page empties (`:138`).
- **Forced-subtitle setting missing** on TV (`TvSettingsViewModel.kt:378`) — phone persists `showForcedSubtitles`.
- **Profile management** select/PIN only (`TvProfileSelectionViewModel.kt:41`) — no create/edit/delete.
- **No first-time server setup / signup** on TV (`TvServerSetupViewModel.kt:24`).
- **Collection detail read-only** (`TvCollectionDetailViewModel.kt:18`) — no remove/rename/delete.

## Missing surfaces — product decisions
- **Ebook reading** (`book`/`reader`/`reading`): TV hides ebook media + errors on ebook detail (`TvItemDetailViewModel.kt:86`). Likely intentional (reading on a TV is unusual), but the server now converts Kindle→EPUB — decide whether TV should read at all.
- **Calendar** (full screen) — TV has only a home "upcoming" row.
- **Browse** (global) — folded into library-detail filters.
- **Admin** — stats only; user/library management deferred.
- **Downloads** — intentionally absent (TV is always online).

## Low
- Settings `/me` failure shows "Signed in as —" with no error/retry (`TvSettingsViewModel.kt:55`).

## Suggested priority
1. **Verify + fix the playback criticals** (surface binding #1, position #2, dispose #3, errors #4) — these are correctness/UX regressions vs phone.
2. **TV-feel fixes** (focus/back: #5–#10, nav robustness) — what makes the app feel broken.
3. **Lifecycle/correctness** (#11–#13, lifecycle-aware collection, card-state key).
4. **Triage the missing surfaces** as product decisions (ebook? calendar? profile mgmt? admin?).
