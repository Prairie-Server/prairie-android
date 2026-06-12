# Reading Hub Production Pass Design

## Goal

Make the mobile Reading hub feel production-ready for profiles with ebooks, audiobooks, comics, and manga while preserving the current platform split:

- Android mobile shows literary media under Reading.
- Android TV never exposes ebooks, comics, manga, or Reading.
- Android TV can still expose audiobooks under Audio.

This pass turns the Reading hub from a first working surface into a polished mobile library destination with useful format filtering, stable library selection, clearer empty/error states, and search entry that lands users in the right context.

## Current State

The mobile app now routes the Reading tab to `ReadingHubScreen`. The screen loads only reading-capable libraries through `UserLibrary.readingLibraries()` and presents one selected library at a time across Recommended, Browse, and Collections. That matches existing repository APIs, which are library-id based.

The hub already has:

- a compact top bar with active profile, server/profile switching, settings, and search;
- a library selector when multiple reading libraries are visible;
- Recommended, Browse, and Collections tabs;
- genre and sort controls for Browse;
- selected-library guards around async loads.

The biggest remaining product gap is that the hub does not yet understand user intent at the format level. A user with both ebooks and audiobooks sees a library selector but no clear "show me audiobooks" or "show me ebooks" mode. A user with only one type should not see dead controls.

## Product Behavior

### Format Filter

The Reading hub will add a functional format filter with these options:

- All
- Ebooks
- Audiobooks

The filter is visible only when it can change the result set. For example, a profile with only ebook-like libraries does not need an Audiobooks chip, and a profile with only one effective reading format can omit the whole filter row.

The filter operates over the existing library-id model:

- All includes every mobile reading library.
- Ebooks includes ebook, book, comic, comics, manga, reading, and equivalent server type strings.
- Audiobooks includes audiobook and audiobooks library types.

When the user changes the filter, the selected library remains selected if it still belongs to the filtered set. Otherwise, the hub selects the first matching library by `sortOrder`. The active tab, genre, sort, and loaded content reset only when the selected library changes or the current loaded data is no longer valid.

This keeps v1 production-ready without pretending the client can aggregate server sections across multiple libraries. Cross-library "all reading" aggregation can come later if the server adds a first-class endpoint.

### Library Selector

The library selector will show only libraries that match the active format filter. If the active format has no matching libraries, the content area shows a format-specific empty state instead of falling back to another format silently.

Examples:

- Ebooks selected with no ebook libraries: "No ebooks in this profile".
- Audiobooks selected with no audiobook libraries: "No audiobooks in this profile".
- No reading libraries at all: keep the existing top-level "No reading libraries" state.

### Recommended

Recommended remains section-driven through `SectionRepository.getLibrarySections(libraryId)`. Section titles that represent progress continue to normalize to "Continue Reading & Listening".

For this pass, Recommended content reflects the selected library after format filtering. It does not client-filter section items across other libraries because the current API call already scopes by library.

Empty, loading, and error states should be specific enough that users understand whether the profile has no content, a selected format has no libraries, or the server failed to load sections.

### Browse

Browse remains catalog-driven through `CatalogRepository.browse(libraryId, ...)`. Genre and sort controls continue to apply to the selected library.

The item count should describe the currently selected library and format context, not imply cross-library totals. Pagination must remain stable when the user changes format, library, genre, or sort while a request is in flight.

### Collections

Collections remain library-scoped through `SectionRepository.getLibraryCollections(libraryId)`.

The format filter narrows which libraries can be selected, so Collections naturally follows the selected format. No extra collection-level item filtering is required in this pass.

### Search Entry

The Reading hub search action should open mobile search with Reading preselected when the route supports it. The search screen should still gracefully default to the existing behavior when no scope is supplied.

The search production pass can later deepen ranking, filters, and mixed-result presentation. This pass only needs the Reading entry point to feel intentional.

## Data Model

Add a small reading-format model near the existing media-mode helpers:

- `ReadingFormatFilter.All`
- `ReadingFormatFilter.Ebooks`
- `ReadingFormatFilter.Audiobooks`

Add helper functions that classify library type strings consistently:

- ebook-like: `ebook`, `ebooks`, `book`, `books`, `comic`, `comics`, `manga`, `reading`
- audiobook-like: `audiobook`, `audiobooks`
- reading: either ebook-like or audiobook-like

These helpers should be shared by navigation capability code and the Reading hub so the app does not accumulate competing string lists.

## UX Constraints

- Do not add Reading to Android TV.
- Do not move mobile audiobooks into the mobile Audio tab yet.
- Do not add a music surface in this pass.
- Do not implement local ebook+audiobook work linking before the server contract lands.
- Do not create a cross-library aggregator by making multiple per-library calls and stitching results together.
- Keep the hub dense and app-like, not a marketing screen.

## Error Handling

The ViewModel should preserve the user's selected format and selected tab across retry attempts. Failed content loads should not erase unrelated successfully loaded state unless that state belongs to a library that is no longer selected.

Async responses must be ignored when they no longer match the active library, format, genre, or sort request.

## Testing

Add focused tests for shared reading-format classification:

- ebook-like library types match Ebooks and Reading;
- audiobook-like library types match Audiobooks and Reading;
- non-reading audio/video library types do not match Reading;
- `readingLibraries()` continues to include both ebook-like and audiobook-like libraries;
- format-filtered library lists preserve sort order through caller sorting.

Run the existing Android mobile, Android TV, and shared verification suite after implementation.

## Out Of Scope

- Android TV ebooks, comics, manga, or Reading surfaces.
- Unified ebook+audiobook detail pages.
- Server-side work-linking contracts.
- Offline downloads changes.
- Music library UX.
- Admin/request management.
