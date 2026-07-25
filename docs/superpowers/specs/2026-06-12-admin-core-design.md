# Admin Core — Design

**Date:** 2026-06-12
**Status:** Approved
**Scope:** Sub-project 4 of the feature-parity push. Restores the deleted admin surface and extends it to the current server contracts. Mobile = core admin; TV = stats dashboard only. Server: prairie-server `origin/main`. Live data via REST + pull-to-refresh (events-ws admin channels are a later enhancement).

## Goal

Close the admin parity gaps: stats dashboard (both apps), and on mobile — user management, active-session monitoring + remote control, app/audit logs, and library scans. Scoped to "core admin": no plugins, nodes, tasks, or server-settings UIs. **Metadata editing/identification is explicitly out of scope** (dropped 2026-06-12 by user decision).

## Admin gating

The server gates every admin route on **acting admin** = `user.role == "admin"` AND the active `profile.is_primary == true` (router `RequireActingAdmin`; web `isActingAdmin(user, profile)`). Client mirror:
- Verify the shared `User` model has `role` (it does) and the `Profile` model exposes `is_primary` (`@SerialName("is_primary")`) — add the field if missing.
- Add a shared `isActingAdmin(user, profile): Boolean` (role == "admin" && (profile == null || profile.isPrimary)).
- Settings "Admin" entry (mobile + TV) renders only when acting-admin; admin calls otherwise 403 server-side anyway (defense in depth).

## Server contracts (verified against prairie-server main)

All admin endpoints under `/api/v1/admin/*` require acting-admin. Key shapes:
- **Stats** `GET /admin/stats[?refresh=true]` → `{total_items, total_files, total_users, total_movies, total_movie_files, total_shows, total_show_files, active_streams, total_storage_bytes, watch_provider_activity:{trakt_connected_profiles, ..., scrobbles_24h}}`.
- **Users** `GET/POST /admin/users`, `GET/PUT/DELETE /admin/users/{id}`. User: `{id, username, email, role, permissions[], enabled, library_ids[]?, max_playback_quality, max_streams, max_transcodes, max_profiles, download_allowed, download_transcode_allowed, created_at, updated_at, last_active_at?}`. Create adds `password`, `create_default_profile`, `default_profile_name`. Update = all fields optional (partial).
- **Sessions** `GET /admin/sessions` → list of session rows (user/profile/media/play_method/position/is_paused/has_playback_control/client_ip + full source/target transcode detail). Controls: `POST /admin/sessions/{id}/{pause|resume|stop|terminate|message}` body `{reason?, title?, message?, deadline_ms?}` → `{command_id, status}`.
- **Logs** `GET /admin/logs/app` + `/admin/logs/audit`, query `level/component/node_id/request_id/session_id/user_id/from/to/q/cursor/limit(≤200)` → `{entries:[...], next_cursor?}`. App entry: `{id, timestamp, level, component, message, request_id?, user_id?, session_id?, client_ip?, node_id?, attrs?}`. Audit entry: `{id, timestamp, method, path, status_code, client_ip, request_id, user_id?, session_id?}`.
- **Scans** `POST /libraries/scan` body `{library_id?, path?}` → `{status, mode, library_id}`; `POST /libraries/scan/cancel` body `{library_id}` → `{cancelled, library_id}`. Library list via existing `GET /libraries`. (Live progress is on the events-ws `scans` channel — out of scope; v1 reflects scan state on refresh.)

## Client design

### Shared (`shared/`)
- `model/admin/AdminModels.kt`: AdminStats (+ WatchProviderActivity), AdminUser (+ Create/Update requests), AdminSession (full transcode detail), log entry models (app + audit) + LogPage(next_cursor), scan request/response. Serialization tests.
- `network/api/AdminApi.kt` (interface+Default): stats, users CRUD, sessions + 5 controls, logs (app/audit with query params + cursor), scan trigger/cancel. Registered in NetworkModule.
- `repository/AdminRepository.kt` (thin pass-throughs; the existing scan endpoints live under /libraries — keep there or proxy). Registered in RepositoryModule.
- `isActingAdmin` helper + `Profile.isPrimary` field if absent.

### Mobile (`androidApp`)
An **Admin hub** screen (reached from Settings "Admin" entry, acting-admin-gated) linking to sub-screens; each sub-screen has its own ViewModel + pull-to-refresh + loading/error/empty states, reusing existing list idioms (requests/inbox screens):
- **Stats dashboard** — cards for counts/storage/active streams + a Trakt activity section.
- **Users** — list → user detail/edit form (create via FAB); role/enabled/libraries/quota fields; delete with confirm; password reset field on edit.
- **Sessions** — list of now-playing rows (poster, user/profile, media, play method, progress, bitrate/transcode summary); per-row action menu → pause/resume/stop/terminate/message (message opens a small dialog); only when `has_playback_control`.
- **Logs** — app/audit tab switch, filter bar (level, search, component), cursor-paginated list; tap a row → detail (attrs/request id).
- **Scans** — library list with per-library Scan + Scan-all + Cancel; show last-scanned/status; refresh to update.

### TV (`androidTvApp`)
- Restore **TvAdminScreen** (2-column stat grid) wired to live `/admin/stats`, reachable from TV Settings when acting-admin. No other admin surfaces on TV.

## Error handling
- All admin calls surface server messages via `ApiResult.errorMessage` in-screen; 403 (non-admin) shouldn't occur because the UI is gated, but a 403 → a "not authorized" state rather than a crash.
- Session control actions show success/failure toasts; the list refreshes after a control action.
- Destructive actions (delete user, terminate session) confirm first.

## Testing
- Shared: serialization tests for all admin models (incl. optional/absent fields, the rich session row, log pages); repository tests; `isActingAdmin` unit test.
- Mobile/TV UI: compile gates + manual checklists per sub-screen (admin gating shows/hides; CRUD round-trips; session control dispatches; log pagination + filters; scan trigger/cancel).

## Out of scope
- Metadata editing/identification (match/apply/images/field-edit) — dropped this round; revisit as a follow-up.
- Plugins, transcode nodes, background tasks, server settings UIs.
- Live events-ws admin channels (sessions/scans/logs streaming) — REST + refresh for v1.
- TV admin beyond the stats dashboard.
