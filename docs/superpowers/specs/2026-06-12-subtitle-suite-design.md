# Subtitle Suite (Provider Search/Download + AI Translation) — Design

**Date:** 2026-06-12
**Status:** Approved
**Scope:** Sub-project 2 of the feature-parity push. Mobile AND TV video players (revised 2026-06-12: neither feature needs text input — search is a language picker, AI translate is pure pickers — so TV is in scope). Server: prairie-server `origin/main`.

## Goal

Close the parity gaps "Subtitle search and download" and "AI subtitle translation and transcription" on the Android mobile video player, mirroring the web reference UX.

## Server contracts (verified against prairie-server main)

### Provider search/download
- `POST /api/v1/subtitles/search` body `{"media_file_id": Int, "languages": [String]}` → `{"results": [SubtitleResult], "warnings": [String]?}`; `SubtitleResult = {id, provider(opensubtitles|subdl|subsource), language, release_name, format, score(0-100), downloads, hearing_impaired, upload_date?}`.
- `POST /api/v1/subtitles/download` body `{media_file_id, provider, subtitle_id, language, release_name, format, score, hearing_impaired}` → `{"subtitle": DownloadedSubtitle}`; `DownloadedSubtitle = {id, media_file_id, provider, language, format, release_name, score, hearing_impaired, created_at}`.
- `GET /api/v1/subtitles/{media_file_id}` → `{"subtitles": [DownloadedSubtitle]}`.
- No capability probe: search simply errors when no providers are configured; surface the server error text.

### AI translation/transcription
- `GET /api/v1/subtitles/ai/status` → `{"enabled": Bool, "transcribe_enabled": Bool}` (both false when AI unconfigured).
- `GET /api/v1/subtitles/ai/quota` → `{"limited", "limit", "used", "remaining", "period"(day|week|month)}` — applies to transcribe kinds only; admins exempt (`limited: false`).
- `POST /api/v1/subtitles/ai/translate` body `{media_file_id, kind(translate|transcribe|transcribe_translate; default translate), source_index, source_language?, target_language, session_id?, start_position?}` → 202 `{"job": Job}`; 429 on quota exhaustion; 503 when engine unconfigured. `source_index` = combined subtitle index for translate, audio track index for transcribe.
- `GET /api/v1/subtitles/ai/jobs?media_file_id=N` → `{"jobs": [Job]}`; `GET /api/v1/subtitles/ai/jobs/{id}` → `{"job": Job}`; `POST /api/v1/subtitles/ai/jobs/{id}/cancel` → 204.
- `Job = {id, media_file_id, kind, source_index, source_language, target_language, engine, model, status(pending|running|completed|failed|cancelled), progress(0..1), progress_message, result_subtitle_id?, error_message?, created_at, updated_at}`.

### Track refresh after download/AI completion (web reference behavior)
The web player does NOT restart the playback session: it refetches `GET /subtitles/{media_file_id}`, merges the downloaded entries into the session's `subtitle_urls` track list, and the new track becomes selectable in place (`web/src/hooks/usePlaybackSession.ts` `refreshSubtitles`, lines ~274-295). Android mirrors this. The URL for a downloaded subtitle track is constructed the same way the web does it — pin the exact construction from `usePlaybackSession.ts` during planning.

## Client design

### Shared (`shared/`)
- `model/subtitles/SubtitleModels.kt`: SubtitleResult/SearchResponse, DownloadedSubtitle(+request), AiStatus, AiQuota, SubtitleAiJob — `@SerialName` wire mapping + serialization tests mirroring ContinuumJson.
- `network/api/SubtitlesApi.kt`: search/download/list + aiStatus/aiQuota/translate/listJobs/getJob/cancelJob, registered in `NetworkModule`.
- `repository/SubtitlesRepository.kt`, registered in `RepositoryModule`. Includes `pollJob(jobId, intervalMs, onUpdate): TerminalJobResult` — a suspend loop modeled on `DeviceLoginRepository.runPollLoop`: transient ApiResult errors retry after the interval, 404 and terminal statuses (completed/failed/cancelled) end the loop. Unit-tested with a fake API (completion, failure, transient-error retry, cancellation via coroutine cancellation).

### Mobile (`androidApp`)
- `TracksSheet`: subtitle section gains two non-selecting action rows under the tracks — "Search subtitles…" (shown when the player knows its media file id) and "Translate with AI…" (shown only when AI status enabled || transcribe_enabled). Tapping dismisses TracksSheet and opens the respective sheet.
- AI status probed once per player session (PlayerViewModel fetches lazily on first TracksSheet open; on failure both flags false, row hidden) — mirrors the web.
- `SubtitleSearchSheet`: language dropdown (default = effective subtitle language from WatchDetail, else "en"), Search action, result rows: score badge color-coded (≥70 green, ≥40 amber, else red), release name, optional HI badge, downloads count, provider abbreviation badge (OS/SDL/SS with the web's colors). Tap row → download with inline progress → on success: `refreshSubtitles(autoSelectSubtitleId = subtitle.id)` and dismiss. Errors inline.
- `AiTranslateSheet`: mode tabs "From subtitles" / "From audio" (tabs only when both enabled; otherwise the single available mode; neither → explanatory empty text). Source picker (translatable text subtitle tracks / audio tracks with language+layout labels). Target language dropdown. Quota line in audio mode ("X of Y left this {period}" / exhausted state amber + submit disabled; quota refreshed on open and after a 429). Submit posts `translate` with the current playhead as `start_position` (no `session_id` — Android polls instead of streaming live cues). In-sheet job progress (percent + progress_message + Cancel → cancelJob). Completed → `refreshSubtitles(autoSelect = result_subtitle_id)` and dismiss; failed → error_message inline.
- `PlayerViewModel.refreshSubtitles(autoSelectSubtitleId: Int?)`: GET list, build PlayerSubtitleInfo entries for downloaded subtitles not already present (URL per the web's construction; label "{Language} ({provider})" matching web conventions; source = "downloaded"), append to `subtitleTracks`, rebuild Media3 subtitle configurations through the existing `SubtitleManager` path, preserve current selection, then select the new track when `autoSelectSubtitleId` matches.
- `LanguageNames` helper in androidApp ui/util (ISO 639-1 → English display name, fallback uppercased code) used by both pickers' dropdowns.

### TV (`androidTvApp`)
- The TV player HUD's subtitles pane gains two focusable action rows beneath the track list: "Search subtitles" and "Translate with AI" (latter gated on AI status, probed once per player session like mobile).
- `TvSubtitleSearchDialog`: D-pad dialog — language picker (default = effective subtitle language), Search action, focusable result rows with the same score/HI/downloads/provider presentation adapted to TV styling; OK on a row downloads with inline progress → refresh + auto-select → dialog closes.
- `TvAiTranslateDialog`: D-pad dialog — mode rows (From subtitles / From audio per availability), source picker, target-language picker, quota line for transcription, submit → in-dialog job progress with Cancel; completion → refresh + auto-select.
- `TvPlayerViewModel` gains the same `refreshSubtitles(autoSelectSubtitleId)` merge logic as mobile — extract the merge as a SHARED pure function (e.g. in shared/ or android-shared/) so both players use one implementation, unit-tested once.
- Presentation follows the existing TV dialog idioms (TvOptionDialog/TvRatingDialog patterns).

## Error handling
- Search/download/AI calls surface server messages via `ApiResult.errorMessage` inline in the sheets.
- Quota 429 → refresh quota + show exhausted state.
- AI status fetch failure → feature hidden (no error surfaced), matching web.
- Poll loop resilient to transient network errors; player exit cancels any in-flight poll (structured concurrency in viewModelScope).

## Testing
- Shared: serialization tests for all new models; repository tests; poll-loop tests (terminal states, transient retry, cancellation).
- Mobile: pure helpers unit-tested where extracted (score-badge bucketing, track-merge logic if extracted as a pure function — prefer extracting `mergeDownloadedSubtitles(existing, downloaded, serverUrl): List<PlayerSubtitleInfo>` precisely so it IS unit-testable).
- Sheets/Compose: compile gates + manual checklist on BOTH form factors (search/download/select happy path, AI translate from subtitle, transcribe quota display, cancel mid-job, player exit during job; TV: full D-pad traversal).

## Out of scope
- Subtitle file upload + language detection; subtitle deletion; live translation cue streaming (websocket); metadata translation (admin, sub-project 4).
