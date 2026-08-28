# Android Specials-First Season Order Design

## Goal

Match the web client’s season-selector presentation on Android phone and TV:

`Specials, Season 1, Season 2, …`

The visible label remains **Specials**. Android must not relabel it as
“Season 0.”

## Scope

- Android phone and Android TV series-detail season selectors.
- The existing shared `List<Season>.sortedForDisplay()` ordering contract.
- Initial season selection when opening a series.
- Focused unit tests for ordering and selection behavior.

The Prairie server, web client, Apple clients, API schema, and playback sequencing
are unchanged.

## Ordering Contract

A season is treated as Specials when either:

- `isSpecials` is `true`; or
- `seasonNumber` is `0`.

Specials sorts before every regular season. Regular seasons sort by
`seasonNumber` ascending. Existing deterministic title and content-ID
tie-breakers remain in place.

Recognizing Season 0 independently of `isSpecials` protects the UI when reading
older cached responses or a response that omitted the optional semantic flag.

## Initial Selection

Display order and automatic selection are separate:

- A requested/deep-linked season remains selected, including Specials.
- On an ordinary series opening, select the first regular season.
- If the series contains only Specials, select Specials.

This prevents the reordered selector from making a series open on bonus
material by default while still placing Specials first visually.

## Implementation Shape

Update the existing shared season comparator rather than reordering separately
inside phone and TV composables. Keep the phone and TV view models responsible
for choosing the initial season, using the same “first regular, otherwise
first” rule after applying the shared display order.

No new repository, model, route, or server behavior is introduced.

## Verification

Focused tests cover:

- Season 0 before Seasons 1 and 2.
- `isSpecials = true` before regular seasons even with a nonzero number.
- Season 0 recognized when `isSpecials` is false or absent.
- Regular seasons remain ascending and deterministically ordered.
- Phone and TV initially select the first regular season.
- A requested Specials season remains selected.
- Specials-only series still select Specials.

Run the affected shared, phone-detail, and TV-detail unit tests, followed by
phone and TV debug compilation.
