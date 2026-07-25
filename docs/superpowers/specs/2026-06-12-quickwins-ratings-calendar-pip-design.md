# Quick Wins: Personal Ratings, Calendar, Picture-in-Picture — Design

**Date:** 2026-06-12
**Status:** Approved — PiP section dropped by user decision on 2026-06-12 (Sections 1–2 only)
**Scope:** Sub-project 1 of the feature-parity push (mobile-first, natural TV). Server: prairie-server `origin/main`.

## Goal

Close three parity-page gaps with full server support today: personal ratings (mobile + TV detail screens), a calendar/upcoming surface (mobile screen + TV home row), and picture-in-picture video playback (mobile).

## 1. Personal ratings

### Server contract (verified against prairie-server main)

- `PUT /api/v1/ratings/{item_id}` body `{"rating": N}`, integer **1–5**; 400 outside range; 204 on success.
- `GET /api/v1/ratings/{item_id}` → `{"rating": 1-5, "rated_at": "..."}` or 404.
- `DELETE /api/v1/ratings/{item_id}` → 204.
- `GET /api/v1/ratings/` → `{"ratings": [{"media_item_id", "rating", "rated_at"}]}` (limit/offset; not used in v1 UI).
- Item detail already embeds `user_rating: Int?` (`user_rating,omitempty`) — no per-item GET needed.

### Client changes

**Shared:**
- Fix the existing `PersonalDataApi.setRating` / `PersonalDataRepository.setRating` signatures from `Double` to `Int` (kotlinx serializes `4.0`, which Go's `int` unmarshal rejects — latent bug). `SetRatingRequest(rating: Int)`.
- Add `userRating: Int?` (`@SerialName("user_rating")`) to the shared `ItemDetail` model + serialization test.

**Mobile (`androidApp`):**
- `HeroActionStack` gains a star `CircleActionButton` (filled icon when rated, outline otherwise), opening a `RatingSheet` bottom sheet: five large tappable stars reflecting the current rating, plus a "Remove rating" action shown only when rated.
- `ItemDetailViewModel`: `userRating: Int?` in `ItemDetailUiState` seeded from `detail.userRating`; `setRating(stars: Int)` and `clearRating()` follow the existing favorite optimistic-update pattern (update state → repository call → revert on non-Success).
- Applies to all detail content types that show the action stack (movie, series, book, audiobook). Episodes inherit wherever the action stack is already shown.

**TV (`androidTvApp`):**
- A focusable star button in the TV detail action row (same row as favorite/watchlist), opening a D-pad rating dialog: five stars navigable left/right, OK to set, plus a clear action. `TvItemDetailViewModel` mirrors the favorite toggle pattern (`isTogglingRating` guard, rollback on failure).

## 2. Calendar / upcoming

### Server contract (verified)

- `GET /api/v1/calendar?start=YYYY-MM-DD&end=YYYY-MM-DD&filter=&library_id=&timezone=` — max 31-day span; `filter ∈ all|everything|following|favorites|watchlist|popular|trending` (web exposes Following/Trending/All).
- Response: `{"events": [{"date", "items": [{content_id, type(movie|episode), title, episode_title?, series_id?, season_number?, episode_number?, air_date, air_time?, air_at?, air_timezone?, local_air_date, poster_url, poster_thumbhash, watched, badges[series_premiere|season_premiere|finale]}]}]}`.

### Client changes

**Shared:**
- `model/calendar/CalendarModels.kt` (response models, `@SerialName` wire mapping) + serialization test.
- `network/api/CalendarApi.kt` (`getCalendar(start, end, filter, libraryId?, timezone)`), registered in `NetworkModule`.
- `repository/CalendarRepository.kt`, registered in `RepositoryModule`.
- `viewmodel/CalendarViewModel.kt` in shared (pattern: `RequestsViewModels`): state = week anchor, filter preset, library filter, day-grouped events, loading/error; actions = next/prev week, today, set filter, set library, refresh. Timezone supplied by the platform caller.

**Mobile:**
- `CalendarScreen`: sticky week strip (7 day chips + prev/next + Today), day-grouped vertical list with event cards (poster + thumbhash, title, SxEy + episode title for episodes, air time, premiere/finale badge chips, dimmed when `watched`), filter preset row (Following / Trending / All), library dropdown when >1 library. Tap → existing item-detail route (`series_id` for episodes, `content_id` otherwise). Empty states per preset, mirroring the web copy.
- Route `Routes.Calendar` + nav graph entry; entry point added alongside the existing Favorites/Watchlist/History entries (same menu surface).

**TV:**
- A client-side "Coming this week" row appended to TV home after server sections: fetched via the shared ViewModel/repository with `filter=following` (fallback to `all` when the following set is empty), rendered with the existing `TvMediaRow` card pattern, click → TV item detail. No full calendar page on TV.

## 3. Picture-in-picture (mobile)

Client-only; the ExoPlayer already lives in `ContinuumPlaybackService`, so playback continues across activity backgrounding.

- Manifest: `android:supportsPictureInPicture="true"` and add `smallestScreenSize` to MainActivity `configChanges`.
- A small `PipController` (androidApp, player package) owning `PictureInPictureParams` updates: aspect ratio from the current video size (clamped to platform-legal range), `setAutoEnterEnabled(true)` while the player route is active and playing (Android 12+), `onUserLeaveHint` → `enterPictureInPictureMode` fallback for API 26–30. PlayerScreen registers/unregisters its activity participation; no PiP outside the video player route.
- System media-session controls supply play/pause in the PiP window (no custom RemoteActions).
- PlayerScreen observes `isInPictureInPictureMode` (activity callback bridged via `OnPictureInPictureModeChangedProvider`) and hides all overlay chrome while in PiP; restores on exit.
- Dismissing the PiP window finishes the player route → the existing dispose path stops playback. Returning from PiP restores the full player.
- Out of scope: PiP for audiobooks/readers; TV.

## Error handling

- Ratings: optimistic update with rollback + existing snackbar/error idioms per screen.
- Calendar: standard loading/error/empty states via the shared ViewModel; `ApiResult.errorMessage` for messages.
- PiP: guard all PiP calls with API-level checks and `runCatching` (some OEMs throw on `enterPictureInPictureMode`).

## Testing

- Shared: serialization tests (calendar models, `user_rating` on ItemDetail, `SetRatingRequest` as integer), CalendarViewModel unit tests (week math, filter switching, day grouping) with a fake repository.
- Rating range guard (1..5) unit-tested where enforced client-side (sheet/dialog only emit valid values; ViewModel clamps defensively).
- UI + PiP: compile gates + manual checklist (rate/clear on mobile + TV, calendar week nav + presets, auto-enter PiP, controls hidden in PiP, dismiss stops playback).

## Out of scope

- "My ratings" list screen (no web equivalent).
- Server-driven home "upcoming" section (server has none; TV row is client-side).
- PiP custom actions, PiP on TV.
