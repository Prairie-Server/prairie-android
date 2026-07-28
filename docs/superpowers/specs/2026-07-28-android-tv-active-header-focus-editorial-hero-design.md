# Android TV Active Header Focus and Editorial Hero Design

**Date:** 2026-07-28  
**Status:** Approved for implementation planning  
**Scope:** Android TV browsing shell and browsing heroes only

## Context

External testing of the TV navigation work in PR #126 confirmed that cold
navigation and For You behavior improved. It also exposed two related
presentation defects:

1. Pressing Up from the first content row can focus Search instead of the
   currently active top-menu destination. Pressing Back from the same page
   correctly focuses the active destination.
2. Browsing heroes prioritize technical stream badges such as resolution,
   HDR, and audio format ahead of editorial information. This can crowd or
   truncate the year, runtime, episode identity, and rating that people use to
   decide what to watch.

Issue #78 asks for richer title metadata, but its current wording refers to the
player. This change applies the approved behavior to browsing heroes only and
does not change the player or item-detail surfaces.

## Goals

- A fresh Up press from the first content row focuses the active top-menu
  destination on Home, each library section, For You, and Calendar.
- Search receives this focus only when Search is the active route.
- Preserve the existing held-Up boundary: a held key stops on the first
  content row and requires a fresh Up press before entering the menu.
- Browsing heroes describe the focused title using editorial metadata instead
  of video/audio delivery characteristics.
- Keep the change inside the existing shared TV shell and marquee model.

## Non-goals

- No server, API, database, or payload changes.
- No Android phone changes.
- No player-overlay, playback-settings, or item-detail redesign.
- No changes to stream selection, transcoding, subtitle behavior, or technical
  metadata availability outside the browsing hero.
- No new user preference or display toggle.

## Focus Behavior

The shell remains the single owner of content-to-menu focus transitions. When
the active content feed reports that a fresh Up press has reached its first
row, the shell requests the menu destination derived from the current route:

- Home → Home
- Movies, Series, Music, or Audiobooks → the matching library-type pill
- For You → For You
- Calendar → Calendar
- Search → Search

The request must target the active destination explicitly and complete through
the existing menu focus-request mechanism. It must not depend on Compose
geometric focus search or on the physical proximity of Search to the content
card. The same mapping is used by Back-to-menu behavior so the two entry paths
cannot drift.

Repeated Up events at the first content row remain consumed. Off-screen
previous-row relocation and ordinary row-to-row Up movement are unchanged.
Panel preview, profile menu, Left/Right menu traversal, and Down-to-content
behavior are unchanged.

## Browsing Hero Metadata

The browsing marquee stops rendering resolution, HDR, and audio-format badges.
Technical overlay data remains in the model for other consumers but is not
converted into hero badges.

The hero uses the following ordered editorial fields when present:

### Movies and other non-episode titles

1. Release year
2. Runtime
3. IMDb rating
4. Primary genre

Content classification, such as PG-13, remains as the only badge adjacent to
that ordered metadata line.

### Episodes

1. Season and episode token, such as `S2 E7`
2. Episode name
3. Runtime
4. Air date when available from existing enrichment
5. Rating when present

Content classification, such as TV-MA, remains as the only badge adjacent to
that ordered metadata line.

The series name remains the episode hero title, with the episode name in the
metadata line. Missing values are omitted without placeholders or redundant
separators. Existing synopsis, cast enrichment, artwork, cache-first loading,
and crossfade behavior remain unchanged.

The implementation may keep air date and cast on the existing quieter detail
line if the current payload/enrichment boundary does not expose air date early
enough for the primary metadata line. It must not add another detail request or
delay first paint to rearrange those fields.

## Data Flow and Boundaries

- `TvMainShell` derives the active root destination from the current route.
- `TvShellFocusState` carries the explicit menu-focus request.
- `TvTopMenuBar` resolves that destination to its existing `FocusRequester`.
- `TvSkylineSectionFeed` retains ownership of row traversal and the held-Up
  boundary, but does not choose a menu target.
- `TvMarqueeContent.from` converts the existing `SectionItem` payload into
  ordered editorial metadata.
- Existing detail enrichment may continue to add air-date/cast information
  without blocking or re-fetching on focus.

No parallel focus coordinator or marquee data source is introduced.

## Error and Edge Handling

- If the active route has no top-menu destination, preserve its existing
  route-specific behavior rather than silently selecting Home.
- If a requested library pill is temporarily absent, use the existing safe
  requester fallback and do not crash.
- Invalid, zero, blank, or unavailable metadata is omitted.
- Ratings and runtimes keep the existing formatting and rounding rules unless
  a focused test demonstrates an incorrect value.
- Removing technical badges must not create an empty visual row; the row is
  omitted when no editorial badge or metadata value exists.

## Verification

Focused tests should cover:

- route-to-menu target mapping for every root destination and Search;
- the held-Up first-row boundary remains unchanged;
- movie metadata ordering and omission of resolution/HDR/audio;
- episode metadata ordering, series/episode naming, runtime, rating, and
  content-classification handling;
- absent or invalid metadata without dangling separators.

Regression verification should include the complete Android TV unit suite,
supply-chain checks, and the minified Android TV release assembly. A TV
emulator or external-device smoke should verify:

- Up from the first row lands on the active pill across at least Home, a
  library section, For You, and Calendar;
- Search is selected only on the Search route;
- held Up stops at the first content row;
- representative movie and episode heroes contain editorial metadata and no
  resolution/HDR/audio badges.

## Rollout

Implement this as a focused follow-up on PR #126 while it remains open. Update
the tester APK after automated verification. Do not merge or deploy as part of
implementation.
