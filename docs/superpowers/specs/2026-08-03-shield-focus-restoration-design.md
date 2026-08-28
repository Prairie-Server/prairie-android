# Shield Focus Restoration Design

## Purpose

Fix the remaining Android TV focus failures reported on Shield Pro without changing Watchlist placement, behavior, navigation, or rendering:

- Returning from a For You item detail must preserve the feed position and restore D-pad focus to the card that launched the detail screen.
- The For You top chrome must use the same solid visual foundation already visible in Watchlist and Favorites, without editing those saved-list views.
- Calendar must allow Up navigation from the weekday row through its controls and back to the selected Calendar tab.
- The Diagnostics page's crash-report choices must reliably receive and move focus with a remote.

This branch is based on the head of PR #162, so the event-driven first-row top-anchor correction remains part of the resulting change.

## Non-goals

- Do not promote Watchlist to a top-level tab.
- Do not alter Watchlist or Favorites behavior, navigation, layout, or rendering.
- Do not redesign For You, Calendar, Diagnostics, or the global shell.
- Do not retain every nested route in composition or replace Navigation Compose.
- Do not add touch or keyboard-specific interaction models unrelated to TV D-pad navigation.

## Root Causes

### For You detail return

The For You vertical `LazyListState` is retained, but the shell restores only the generic content focus group after the outer detail route disposes and recreates the shell. Unlike Home, For You does not attach the shell's return requester to the exact launch card. The generic restorer therefore has no durable descendant target and can select a filter, a different card, or no usable row focus after recreation. A subsequent focus-driven bring-into-view pass can make the retained list appear to have lost its position.

### Calendar exit

The shell suppresses the top menu while Calendar performs its initial content handoff. Calendar clears that suppression only when one imperative filter request reports success. If that request misses during route composition but Android's default search still focuses a weekday, the screen looks usable while the menu remains suppressed indefinitely. The shell also treats every control as the same focus zone, so Up from a weekday has no deterministic intermediate target.

### Diagnostics crash-report controls

The Diagnostics page performs one immediate request to the first consent action and discards the result. A request that races route layout is never retried. The consent actions also rely on geometric focus search, so there is no deterministic Up/Down path through the crash-report choices when the page enters without a focused descendant.

### For You top chrome

Watchlist and Favorites render on an opaque full-page saved-list surface. For You relies on the shell gradient and its scrolling feed, producing visibly different top chrome. The inconsistency belongs to For You; changing the saved-list views would expand the visual impact unnecessarily.

## Design

### 1. Exact For You return target

For You will maintain a saveable return target containing stable section and content identities, plus the most recent row/card indices as fallbacks. `TvMediaRow` already supports an indexed item-focus callback and an exact-card restore requester; the screen will use those existing interfaces instead of creating a second card component.

When a recommendation card gains focus, For You records its section ID, content ID, row index, and card index. When that card opens detail, the screen marks the recorded target as pending before delegating navigation to the shell. While pending, the matching `TvMediaRow` attaches a dedicated return `FocusRequester` to the exact card and uses it as that row's restorer fallback.

The shell will distinguish a For You detail return from Home and generic content returns. During resume it will enter the content group using the For You return requester as the fallback, following the existing Home pattern. A screen-level post-composition handoff will ensure the saved vertical row is composed before retrying the exact card request.

Resolution order on return:

1. The same section and content ID.
2. The same section and the closest valid card index.
3. The closest surviving recommendation row's first card.
4. The For You filter pill if the recommendation feed is now empty.

The existing saved vertical list state and each row's saved horizontal list state remain authoritative. Focus restoration must not reset either list to zero. PR #162's first-row anchor watcher remains limited to the case where the first recommendation row itself has focus.

### 2. Calendar focus zones and suppression acknowledgement

Calendar will explicitly identify focus in two control zones: filter segments and week-strip controls. The shell's Up fallback will route based on the active zone:

- From a poster shelf, preserve the existing return-to-week-strip behavior.
- From the weekday/week-strip zone, request the active filter segment.
- From the filter zone, request the selected Calendar tab in the top menu.
- Preserve the existing held-key repeat guard so one long press cannot skip multiple layers.

Any successful focus gain inside Calendar's controls will acknowledge that Calendar content owns focus. This acknowledgement clears `calendarFocusHandoffPending` even when the original imperative filter request failed. The shell can then accept the next Up request. The imperative initial request remains useful, but it is no longer the sole authority allowed to release menu suppression.

### 3. Diagnostics crash-report focus routing

The crash-report consent actions will receive stable requesters. On page entry, Diagnostics will target the currently selected consent mode, rather than always targeting `Ask`, after at least one layout frame. A bounded retry handles route-transition timing; success ends the retry immediately.

Up and Down will route explicitly through the enabled crash-report actions in visual order. The last consent choice routes Down to Debug logging when that action is enabled; disabled actions are skipped. At the upper boundary, focus remains on the first crash-report choice instead of escaping to a non-focusable status block. Navigation from the end of the crash-report section into the existing Capture actions remains available through normal focus search.

No consent values, upload behavior, report data, or diagnostics visuals change.

### 4. For You-only top underlay

For You will paint an opaque background under the top-menu region before drawing its feed. The color will be the existing TV theme background, matching the solid foundation visible in Watchlist and Favorites. This underlay is conditional on the recommendations selection only. Watchlist and Favorites remain byte-for-byte unchanged.

The global shell gradient remains in place for other routes. No button, typography, spacing, or focus styling changes.

## State and Failure Handling

- Return targets use stable IDs first because recommendation refreshes can reorder rows and cards.
- Missing or filtered content follows the explicit fallback order and never loops indefinitely.
- Focus retries are bounded and frame-based; they stop on success, disposal, or target removal.
- A failed Calendar initial request cannot leave the menu permanently suppressed once any Calendar control receives focus.
- Disabled Diagnostics actions are never requested as focus destinations.
- Repeated D-pad key events retain the existing one-layer-per-press policy.

## Testing

Add focused unit tests for pure routing and resolution logic:

- For You exact target, reordered target, missing-card fallback, missing-row fallback, and empty-feed fallback.
- Calendar shelf-to-week-strip, week-strip-to-filter, filter-to-menu, and repeat-event behavior.
- Diagnostics selected-consent entry, Up/Down ordering, disabled Debug logging, and boundary behavior.
- Preserve PR #162's delayed first-row relocation tests.

Run the complete Android TV unit-test task and assemble the TV debug APK. On Shield Pro, manually verify:

1. Scroll several For You rows, open a non-first card, return, and confirm the same card and both scroll axes are restored.
2. Repeat after a recommendations refresh or reorder and confirm the stable-ID/fallback behavior.
3. Focus the first For You row and confirm a delayed bring-into-view cannot displace it from the top.
4. Move from a Calendar shelf to weekdays, Up to filters, then Up to the Calendar top-menu tab.
5. Enter Diagnostics and confirm the selected crash-report consent choice is focused; traverse every enabled choice with Up/Down.
6. Compare For You top chrome with Watchlist/Favorites and confirm only For You changed.

## Delivery

The implementation stays on the isolated `fix/shield-focus-restoration` branch. It will not modify or rewrite the separate unpushed episode-selector branch. Installation on the Shield and opening the app remain separate explicit delivery steps after the build passes.
