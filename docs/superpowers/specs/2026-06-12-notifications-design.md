# Notifications (In-App Inbox + Realtime + Preferences) — Design

**Date:** 2026-06-12
**Status:** Approved
**Scope:** Sub-project 3 of the feature-parity push. Mobile + TV. Built against prairie-server open PR #136 (`feat/notifications-v1`, head `5d7ef15f`) — the contract may shift before merge; isolate the unmerged-surface risk behind a REST base with the websocket as an accelerator.

## Goal

Close the "Notifications inbox" parity gap: an in-app inbox with unread badge, per-profile preference settings, and live foreground updates, on both Android players' apps.

## Server contract (PR #136, verified)

All under `/api/v1`, profile-scoped (auth headers injected automatically by the existing AuthInterceptor).

### Inbox REST
- `GET /notifications?limit=&status=unread&before=<cursor>` → `{"notifications": [DeliveryRow], "next_cursor"?}` (newest-first; cursor opaque base64url).
- `GET /notifications/sync?since=<cursor>&limit=` → ascending catch-up; adds `"unread_count"`.
- `GET /notifications/{id}` → single row (404 cross-profile).
- `GET /notifications/unread-count` → `{"count": Int}`.
- `POST /notifications/{id}/read` → 204 (idempotent). `POST /notifications/read-all` → 204.
- **No delete endpoint** — do not build swipe-to-delete.
- `DeliveryRow = {id, type, profile_id, library_id?, series_id?, episode_id?, series_title?, episode_title?, season_number?, episode_number?, poster_path?, poster_url?, poster_thumbhash?, reason_flags(JSON object, default {}), created_at, read_at?}`.
- Event `type` registry is extensible: `episode.available` (primary), `request.fulfilled`, `webhook.auto_disabled`, and future types — **render unknown types with a generic fallback card.**

### Realtime
- `POST /events/ws-ticket` → `{"ticket", "expires_in"}` (single-use, short-lived).
- Connect `GET /events/ws?ticket=<ticket>`. Server frames: `{"type":"hello",...}` → client sends `{"type":"subscribe","channels":["notifications"]}` within 5s → `{"type":"subscribed",...}` → `{"type":"snapshot","channel":"notifications","data":[DeliveryRow...]}` (≤25 recent unread) → `{"type":"event","channel":"notifications","event":"notification.created"|"notification.read","data":...}`. `notification.created` data = one DeliveryRow; `notification.read` data = `{profile_id, id}` or `{profile_id, all:true}`.

### Preferences / capability
- `GET/PUT /notifications/preferences` → `{enabled, notify_favorites, notify_watchlist, notify_continue_watching, notify_next_up}` (PUT partial, returns full).
- `GET /notifications/capability` → `{in_app, apple_push, android_push, web_push, webhooks, email, discord}` blocks; `android_push.available` is currently false. Drive the settings UI from this (show the in-app toggles; do not show push toggles while `android_push.available == false`).

## Realtime strategy

**REST is the source of truth; the websocket is a foreground accelerator.**
- The singleton `NotificationsRepository` exposes `StateFlow<Int> unreadCount` and `StateFlow<List<NotificationRow>>`.
- On app foreground (and on inbox open) it refreshes via REST (`unread-count` + first page; `/sync?since=` when it holds a last-seen cursor).
- While the app is foregrounded (`androidx.lifecycle.ProcessLifecycleOwner`), it opens the events websocket and folds `notification.created`/`notification.read` (and the initial `snapshot`) into the same StateFlows. Reconnect with capped backoff; ticket re-fetched per connect.
- If the socket never connects or the (unmerged) frame contract mismatches, the feature is fully functional on REST + on-resume refresh — the websocket only makes the badge instant.
- **No background/push delivery.** FCM/APNS are server-v2 design-only (no device-token endpoint exists), so there is nothing to register; closed-app notifications are out of scope.
- Profile switch clears and reloads repository state (badge is per-profile); the socket is reconnected for the new profile.

## Client design

### Shared (`shared/`)
- `model/notifications/NotificationModels.kt`: `NotificationRow` (DeliveryRow wire mapping; `reasonFlags` as `JsonObject` or a typed flags object with defaults), `NotificationListResponse` (+ next_cursor), `NotificationSyncResponse` (+ unread_count), `UnreadCountResponse`, `NotificationPreferences`, `NotificationCapability`, and realtime frame models (`WsHello`, `WsSubscribe`, `WsSubscribed`, `WsSnapshot`, `WsEvent` with a sealed/parsed payload). Serialization tests, incl. an unknown `type` decoding to a row the UI can render generically.
- `network/api/NotificationsApi.kt` (interface+Default like CalendarApi): list/sync/get/unreadCount/markRead/markAllRead/preferences(get+put)/capability/wsTicket — registered in NetworkModule.
- `network/NotificationsRealtimeClient.kt`: wraps the Ktor `webSocket` session — ticket handshake, subscribe, frame decode → `Flow<NotificationRealtimeEvent>` (Snapshot/Created/Read/Closed). Resilient; testable behind an interface with a fake transport. Requires adding the Ktor WebSockets plugin to the shared HttpClient + `ktor-client-websockets` dependency.
- `repository/NotificationsRepository.kt` (singleton): owns the StateFlows; `refresh()`, `markRead(id)`, `markAllRead()`, `loadMore(cursor)`, `preferences`/`updatePreferences`, `capability`; `connectRealtime(scope)` / lifecycle control; pure fold logic (`applyEvent(state, event)`) extracted and unit-tested. Registered in RepositoryModule.
- A small `ProcessLifecycle`-driven starter wired in each app's Application/DI so the repository connects while foregrounded.

### Mobile (`androidApp`)
- `MainAppTopBar`: bell icon + unread badge before the profile button, observing `unreadCount`; tap → Inbox route.
- `InboxScreen` + `Route.Inbox` + nav entry: paginated `LazyColumn`; `episode.available` rich card (poster+thumbhash, series + SxEy + episode title, relative time, unread dot); **generic fallback card** for `request.fulfilled`/`webhook.auto_disabled`/unknown types (title from type + available fields); tap → mark-read + deep-link to the item (series/episode/library target); "Mark all read" action; empty + loading + error states. `loadMore` on scroll-end via cursor.
- Settings "Notifications" section (capability-gated): toggles for `enabled` + favorites/watchlist/continue-watching/next-up via `GET/PUT /preferences`; hidden entirely if `capability.in_app` is unavailable.

### TV (`androidTvApp`)
- Unread badge on the TV top bar (profile cluster).
- `TvInboxScreen` reachable from the profile menu / shell: focusable list with the same rich/fallback card split, OK marks-read + navigates, a "Mark all read" focusable row.
- Notification preference toggles in TV settings (same capability-gated set).
- Realtime + repository are shared, so TV badges/inbox update live with no extra transport code.

## Error handling
- REST failures surface via `ApiResult.errorMessage` in the inbox; the badge silently keeps its last value on a failed refresh.
- Websocket failures are silent (REST covers correctness); log + backoff-reconnect.
- Capability/preferences fetch failure → hide the settings section (no error surfaced), matching the web's capability-driven approach.
- Mark-read is optimistic with revert on failure (badge + row state).

## Testing
- Shared: model serialization (incl. unknown type, absent optionals, reason_flags default), frame decoding, `applyEvent` fold (created/read/read-all/snapshot merge), cursor pagination accumulation, preference PUT partial — realtime transport behind a fake.
- UI: compile gates + manual checklists on both form factors (badge updates live + on resume, inbox pagination, mark-read deep-link, mark-all, preference toggles round-trip, profile switch resets, socket-down REST fallback).

## Out of scope (v1)
- FCM/APNS push + device-token registration (server v2); email/Discord/webhook channel management; notification deletion (no API); web-push.
