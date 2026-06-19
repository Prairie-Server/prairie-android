# Person Detail Parity - Design

**Source brainstorm:** in-session, 2026-06-19. Prompted by the product rule that selecting a person should show the full details for that person, not a dead-end or thin filmography-only page.

## Goal

Android mobile and Android TV should treat people as first-class catalog entities. Selecting a cast member, crew member, author, narrator, musician, or other credited person opens a real person-detail page with profile information and all linked works that the current surface can support.

## Current State

- Shared Android models already decode `/api/v1/people/{id}` into `Person` with name, bio, birth/death dates, birthplace, homepage, photo, thumbhash, and external IDs.
- Android mobile and Android TV already have `PersonDetail` routes and screens.
- TV item details now route cast cards through `TvRoute.PersonDetail` when `person_id` is present, although `TvCastCrewSection` still has a stale comment saying the tap is not wired.
- Android person view models load only the first 60 catalog items and ignore `total`, `has_more`, and `snapshot`.
- Apple's `PersonDetailView` is the master behavior: formatted metadata badges, portrait fallback, biography, filter bar, total/loaded count, paginated works, stable snapshot paging, and TV poster prefetching.
- The server web app also treats person detail as a profile plus catalog window, and exposes edit/refresh controls for admin users. Android clients should not add admin editing in this pass.

## Scope

This pass updates Android clients only.

### Mobile

The mobile person page should show:

- Back navigation.
- Portrait or initials fallback.
- Name.
- Metadata badges:
  - `Born <formatted date>` when `birth_date` exists.
  - `<age> years old` when alive and birth date is parseable.
  - `Died <formatted date>` and death age when `death_date` exists and birth date is parseable.
  - Birthplace when present.
- Biography when present.
- Optional compact external/homepage section when any of homepage, TMDB, IMDb, TVDB, or Plex GUID exists.
- A "Works" section with a count label derived from server `total`, loaded item count, and `has_more`.
- Filters for media types that make sense on mobile: All, Movies, TV, Audiobooks, Music, Reading. Reading covers ebook/comic/manga items and is shown only if returned data or available counts indicate reading content.
- Infinite/paginated loading using the catalog endpoint's `offset`, `limit`, `has_more`, and `snapshot`.

### Android TV

The TV person page should show:

- A scrollable TV detail surface, matching tvOS scale rather than oversized Android defaults.
- Large portrait or icon fallback.
- Name, formatted metadata badges, and biography.
- A "Works" section with count and TV-focusable filter chips.
- Filters for TV-supported media only: All, Movies, TV, Audiobooks, Music. Ebooks, comics, manga, and other reading media remain hidden on TV.
- Paginated loading as the grid nears the end.
- Poster prefetch or equivalent low-risk image warming when available through existing image components.
- D-pad focus that starts on the first meaningful work card after data loads, while still allowing the header/filter chips to be reachable.

## Data Flow

1. Person routes receive a numeric `personId`.
2. View models load `/api/v1/people/{id}` once per route instance.
3. View models load `/api/v1/catalog` with:
   - `source=person`
   - `person_id=<id>`
   - `sort=year`
   - `order=desc`
   - `offset`
   - `limit=60`
   - `snapshot_at=<first response snapshot>` for subsequent pages
   - an optional `type` parameter for simple server-supported filters.
4. If the server cannot yet express a combined client filter such as Reading, the client may request All and locally filter the already-loaded page, but TV must always apply `visibleOnTv()` before rendering.
5. A generation token prevents stale filter/page responses from overwriting newer selections.

## Navigation Rules

- Cast/crew/person cards are clickable only when `person_id` parses to a valid numeric ID.
- Mobile person taps use `Route.PersonDetail`.
- TV person taps use `TvRoute.PersonDetail`.
- Selecting a work from person detail opens the normal item detail route for that work.
- Back returns to the prior detail/search/library route.

## Error And Empty States

- If the profile fails and no profile is loaded, show the existing full-screen retry state.
- If a later works page fails, keep loaded items visible and expose a retry affordance near the grid footer.
- If no works exist for the selected filter, show a surface-appropriate empty state.
- Missing profile fields are hidden, not shown as blank labels.

## Tests

Add or update tests for:

- `CatalogApi.getPersonItems` passes `snapshot_at` on subsequent pages.
- Mobile `PersonDetailViewModel` preserves profile data while paging and appends items when `has_more` is true.
- Mobile filter changes reset items, offset, snapshot, total, and stale responses.
- TV `TvPersonDetailViewModel` applies `visibleOnTv()` so reading media never appears on TV.
- Person metadata formatting returns human badges, including age when dates are parseable.
- TV cast/person card navigation stays wired when `person_id` exists and stays disabled when it does not.
- Source-token tests guard against oversized TV person typography and stale non-wired comments.

## Non-Goals

- Admin person metadata editing on Android.
- Server schema changes.
- Ebooks or reading surfaces on Android TV.
- Changing the server's people search behavior.

## Risks

- The server currently exposes simple `type` filtering. Rich categories like Reading or Music may need client grouping until the server supports broader media scopes for person catalog queries.
- Some old cast rows may lack `person_id`; those remain display-only.
- Long biographies can dominate TV vertical space. TV should cap the initial bio block and rely on page scroll for the full context rather than creating an inner scroll trap.

## Acceptance Criteria

- Tapping a valid person from Android mobile and Android TV opens a person-detail page.
- The page shows profile metadata, biography, and all linked works through pagination.
- TV never shows ebooks/comics/manga on person detail.
- Counts, loading, empty, and retry states behave correctly.
- Android TV sizing aligns with the tvOS master instead of using oversized Material defaults.
