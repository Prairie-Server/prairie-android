# TV Requests Hub Design

## Context

Prairie server currently exposes user-facing request endpoints for movie and series requests through `/api/v1/requests/*`. The current request contract is TMDB-shaped: `media_type` is `movie`, `series`, or `all`, and request detail/create flows are keyed by `tmdb_id`. Android shared already has request models, API, repository, and view models for this contract.

Android TV already has a Requests top-menu destination, a discover-first `TvRequestsScreen`, reusable request card/status components, and `TvMyRequestsScreen`. The next slice refines that work into an elegant search-led TV Requests Hub rather than a bare utility screen.

## Goals

- Make TV Requests search-led and polished.
- Include both available library results and missing/requestable results.
- Let available results open existing TV item detail.
- Let missing requestable movie/series results submit requests through the existing API.
- Keep My Requests reachable from the hub.
- Preserve TV-native D-pad ergonomics, focus behavior, and restrained visual style.

## Non-Goals

- Audiobook requests.
- Ebook requests.
- Admin request moderation.
- New server request contract.
- New book/audiobook request providers.
- Replacing the existing My Requests screen unless a small integration fix is needed.

## Architecture

Use the existing shared request stack:

- `RequestsApi.search()` for search results.
- `RequestsRepository.search()` as the data boundary.
- `RequestSearchViewModel` for TV search state.
- Existing `RequestsViewModel` may continue to provide secondary discover rows or feature-status loading if needed.
- Existing `TvRequestCard`, `TvRequestStatusChip`, and `TvRequestActionPill` remain the primary reusable TV request components.
- `TvRequestsScreen` becomes the TV Requests Hub.

Android TV DI must provide `RequestSearchViewModel` if it is not already registered. No server changes are required for this slice.

## TV Requests Hub Behavior

The Requests tab opens to a calm, living-room-friendly hub:

1. Header with `Requests`, a primary search field, media-type chips, and a nearby `My Requests` action.
2. Search input receives first focus.
3. Media-type chips support `All`, `Movies`, and `Series`.
4. Search results appear as large TV request cards.
5. Before search, secondary discover rows may remain as ambient content below the search area.
6. Empty search must not feel broken; show quiet helper/empty copy and keep focus predictable.

Search results include both available and missing titles:

- Available result with `library_content_id`: open existing TV item detail.
- Missing and requestable result: open request confirmation and submit on confirm.
- Already requested result: show status chip and prevent duplicate submission.
- Missing but not requestable result: show the reason/status quietly and do not submit.

The request confirmation should be short and D-pad safe: title, media type/year, confirm, cancel. After successful submission, refresh search results or update the selected card state so the user sees the new request status.

## Error Handling

- Requests disabled: keep the Requests hub visible but show a quiet disabled state.
- Blank query submit: keep focus in the search field and show a short prompt.
- Network/search failure: show an inline error with retry/search still available.
- Create request failure: keep the result visible and show an inline error.
- Result missing `library_content_id` despite available state: treat as non-openable and show status rather than crashing.

## Follow-Up: Generic Request Contract

The future product matrix is broader than the current server contract:

- TV should eventually request movies, series, and audiobooks.
- Mobile should eventually request movies, series, audiobooks, and ebooks.

That requires a separate server/client design. The request identity cannot stay universally TMDB-only. The next contract should introduce provider-aware identities such as `provider`, `provider_id`, and `external_ids`, plus media-specific metadata for books and audiobooks.

This TV hub slice must not force book requests through TMDB fields. It keeps its UI/model boundaries clear so audiobook and ebook request types can slot in after the server contract expands.

## Testing

Add or update focused tests where practical:

- `RequestSearchViewModel` behavior for blank query, media type filter, success, error, and result refresh after request.
- TV screen/component behavior for available vs requestable vs already-requested result state if Compose test infrastructure supports it.
- Android TV compile to verify DI/navigation wiring.

Verification commands after implementation:

- `./gradlew :shared:testDebugUnitTest`
- `./gradlew :androidTvApp:testDebugUnitTest`
- `./gradlew :androidTvApp:compileDebugKotlinAndroid`
