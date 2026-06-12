# Mobile Requests Hub Design

## Goal

Make the existing Android mobile movie/series request flow feel first-class and elegant without crowding the permanent bottom navigation. The feature should make Requests easier to find from the main app chrome, then polish the existing Requests, Request Detail, and My Requests screens into a coherent mobile hub.

This slice uses the server's current request contract only: `movie`, `series`, and `all` with TMDB IDs. Ebook and audiobook requests are intentionally out of scope until the server exposes a provider-aware request model.

## Current Client Shape

Mobile already has the core request stack:

- Shared request models, API, repository, and view models.
- Mobile Koin bindings for `RequestsViewModel`, `RequestSearchViewModel`, `MyRequestsViewModel`, and parameterized `RequestDetailViewModel`.
- Mobile routes for `requests`, `requests/mine`, and `requests/detail/{mediaType}/{tmdbId}`.
- Mobile screens under `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests`.
- A Settings account entry that can open Requests.

The main gap is product fit: the route exists, but it is not discoverable enough from the main content loop, and the screens are functional rather than polished.

## Product Decision

Use a header/profile entry plus a polished hub, not a permanent fifth bottom tab.

The current bottom navigation is tuned around Home, Libraries, For You, and conditional Downloads. Adding Requests as a permanent fifth item would crowd the phone layout and compete with core library consumption. Requests should instead appear in the global app chrome where users already look for search, personal lists, settings, profile, and server actions.

## Entry Points

Add Requests to the main app chrome:

- Add a Requests action in the global profile/dropdown menu near Favorites & Watchlist and Settings.
- Keep the existing Settings account entry as a secondary route.
- Do not add a bottom-nav tab in this slice.

Home and Libraries use their own floating chrome, so their local header calls should also expose Requests if their header API supports it. If that local chrome would require broad refactoring, keep the first implementation to `MainAppTopBar` and Settings, then add Home/Libraries header parity as a follow-up.

## Requests Hub Screen

`RequestsScreen` becomes a polished mobile hub:

- Header/top bar remains simple: title `Requests`, back navigation, and `My Requests`.
- Search becomes the primary interaction, visually first in the content.
- Filters remain `All`, `Movies`, and `Series`.
- Search result status shows loading, total count, no-results, or error without pushing content around awkwardly.
- Search results appear above discovery rows when a query is active.
- Server-provided discovery rows remain below search for blank query and as fallback context during search errors.
- Pull-to-refresh refreshes discover content. Search can be re-run explicitly through the search action.
- Disabled/unavailable request status shows a quiet full-screen state only when there is no useful cached/discover content.

Card behavior:

- Available results with `library_content_id` open the existing library item detail.
- Missing results open `RequestDetailScreen`.
- Already-requested or non-requestable results still open detail so the user can see state and reason.
- Cards should keep stable dimensions and readable title/status text on narrow phone widths.

## Request Detail Screen

`RequestDetailScreen` should feel like a real title detail page, not a form:

- Backdrop at the top, poster/title/meta/CTA below.
- Metadata includes media type, year, runtime/seasons, genres, and TMDB rating when available.
- CTA area has one clear primary action:
  - `Open Library Item` when available.
  - `Request` when requestable.
  - Disabled/status chip or button when already requested or unavailable.
- Server notice/error messages appear near the CTA and are retained until the next action/load.
- Overview and recommendations remain below.
- Recommendation cards use the same routing behavior as hub cards.

Do not add an extra confirmation dialog in this slice unless the existing view model/server behavior makes accidental submission likely. The request CTA should be clear enough for mobile.

## My Requests Screen

`MyRequestsScreen` should become a status-focused queue:

- Keep pull-to-refresh.
- Keep cancel for pending active requests.
- Show status/outcome chips, target summary, integration errors, and title metadata.
- Move the manual Refresh button into top-level actions or remove it if pull-to-refresh is available and reliable.
- Empty state should guide users back to the hub: requested movies and series will appear here.
- Request taps should keep existing behavior: open library item when present, otherwise open request detail.

## Error Handling

- Blank search: show concise helper copy, not an error.
- Search network/server error: show inline message and leave discover content visible when available.
- Create request error: show server/network message on detail and keep CTA usable when appropriate.
- Requests disabled: hide request CTA and show server-provided disabled reason if available.
- Missing `library_content_id` on an available result should not crash; route to request detail or show non-actionable state.
- Unknown server status strings should render as labels rather than breaking the UI.

## Accessibility And UX Constraints

- Use existing dark Material 3 styling and the current request components as the base.
- Avoid new one-off visual systems.
- Text must not overflow in buttons, cards, or chips.
- Touch targets should remain comfortable on phones.
- Keep ebook/audiobook labels out of request filters for now.

## Testing

Prefer focused tests where logic changes:

- Shared tests already cover request search behavior; add only if shared behavior changes.
- Add mobile compile verification for all UI changes.
- Add small pure helper tests only if routing/status helper logic is moved into testable shared or Android JVM code.
- Avoid brittle Compose screenshot tests unless the project already has a lightweight pattern nearby.

Primary verification:

```bash
./gradlew :shared:testDebugUnitTest :androidApp:compileDebugKotlinAndroid
```

Final broader verification:

```bash
git diff --check
./gradlew :shared:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
git status --short --branch
```

## Out Of Scope

- Ebook requests.
- Audiobook requests.
- Admin request moderation or admin settings.
- New server request contract work.
- Permanent bottom-nav Requests tab.
