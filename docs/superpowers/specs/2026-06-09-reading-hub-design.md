# Reading Hub Design

## Goal

Create a mobile-only Reading Hub that makes ebooks and audiobooks feel like one literary surface. The hub should be useful before the server-owned work-linking contract exists, and should prepare the client for unified ebook+audiobook work detail later.

The hub is not available on Android TV. Ebooks, comics, manga, and Reading routes remain hidden from TV.

## Product Shape

The mobile `Reading` tab should become a dedicated hub for:

- Ebooks
- Audiobooks
- Books
- Comics and manga later
- Literary authors, series, and collections later

For v1, the hub uses existing user libraries and catalog APIs. It does not infer that an ebook and audiobook are the same work. When the server exposes a work identity, the hub can show combined cards and route to unified work detail pages.

## Navigation

Mobile capability rules remain:

- Audiobook-only accounts show `Reading`, not `Audio`.
- Ebook-only accounts show `Reading`.
- Music/audio-native accounts show `Audio`.
- `Downloads` remains global.

The existing mobile `Reading` tab route should render `ReadingHubScreen` instead of the generic `LibrariesScreen`. `Audio` can keep using the generic library surface until music has a dedicated design.

## Data Source

Use `PersonalDataRepository.listUserLibraries()` to find reading libraries. The v1 reading library type set is:

- `audiobook`
- `audiobooks`
- `ebook`
- `ebooks`
- `book`
- `books`
- `comic`
- `comics`
- `manga`
- `reading`

The hub should only show libraries from that set. It should not expose video or music libraries through the Reading tab.

Use current catalog endpoints:

- Library sections through `SectionRepository.getLibrarySections()` and section item loading.
- Catalog browse through `CatalogRepository.browse(libraryId = ...)`.
- Filters through `CatalogRepository.getFilters(libraryId = ...)`.
- Collections through `SectionRepository.getLibraryCollectionsGrouped()`.

If multiple reading libraries exist, the hub should support selecting between them. If one reading library exists, the selector can be de-emphasized but should still be reachable.

## Hub Layout

The first implementation should reuse the current mobile library visual language instead of creating a large new design system.

Recommended structure:

- Header: `Reading`
- Subtitle or supporting copy should be minimal and mode-specific, not instructional.
- Search action routes to existing global search.
- Library selector shows only reading libraries.
- Format chips:
  - `All`
  - `Ebooks`
  - `Audiobooks`
  - `Comics` and `Manga` only if those library types exist, or can be reserved for later.
- Primary content tabs:
  - `Recommended`
  - `Browse`
  - `Collections`

The existing `LibrariesScreen` already has Recommended, Browse, and Collections behavior. The hub can reuse that logic, but it should filter the library list to reading libraries and use Reading-specific labels and empty states.

## Continue Reading And Listening

The mockups point toward a `Continue Reading & Listening` section. That is the desired direction, but this v1 should not fabricate progress groupings beyond what current APIs expose.

If existing section data includes in-progress/continue rows for selected reading libraries, show them with reading-friendly labels. If not available, the hub should still work with recommended and browse content. A future slice can add a dedicated reading progress endpoint or work-aware progress rails.

## Card Behavior

Cards should continue to route to existing item detail by content id:

- Ebook items route to the existing ebook detail/reader path.
- Audiobook items route to existing audiobook detail/player path.

Cards can show simple type badges such as `Ebook`, `Audiobook`, `EPUB`, or `M4B` if that data is already available. They should not show `Ebook + Audiobook` until the server work-linking contract exists.

## Search

The global mobile Search screen already supports mode scopes. The Reading Hub search entry can route there. A future enhancement can preselect the `Reading` scope when launched from the Reading tab, but that is not required for v1 unless it is cheap and does not complicate routing.

## Empty And Error States

If a user reaches Reading while no reading libraries are available, show a small empty state and keep navigation stable. This should be rare because the Reading tab is capability-driven.

If reading library loading fails, show the existing error pattern with retry. Do not reveal TV-only or video/music content as fallback in the Reading Hub.

## Testing

Tests should cover:

- Reading library type filtering includes ebooks and audiobooks.
- Non-reading library types are excluded from the Reading Hub.
- Single reading library selection.
- Multiple reading library selection.
- Empty reading library list.
- Existing mobile capability behavior still maps audiobook-only accounts to `Reading`.

Compile verification should include Android mobile and shared tests touched by the implementation.

## Out Of Scope

This design does not implement the future server-owned literary work contract.

This design does not locally link ebook and audiobook items as the same work.

This design does not add Reading to Android TV.

This design does not build a music/audio hub.
