# Android Accessibility Review Fixes Design

## Scope

Resolve the eight still-valid review findings on Android PR #87 without changing the feature intent of the accessibility remediation. The implementation lands once on `pr/accessibility-foundations-v2`; because PRs #88 through #93 descend linearly from that branch, the resulting commit is then propagated through the stack by rebasing each descendant branch in order.

This work does not modify PRs outside the `RXWatcher` Android stack and does not add observability or telemetry.

## Responsive phone layouts

### Audiobook transport

`AudiobookTransport` keeps all five controls visible when chapters exist. The 48dp chapter buttons, 50dp skip buttons, and 82dp play button remain unchanged. A small pure layout policy selects the inter-button spacing from the available width:

- use the existing 28dp spacing when the full 390dp arrangement fits;
- otherwise use the largest spacing that fits, clamped to a compact floor suitable for a 312dp content width.

The composable reads its actual maximum width with `BoxWithConstraints`, so the decision follows the space supplied by `AudiobookPlayerScreen` rather than the device's nominal screen size.

### Catalog alphabet rail

The alphabet rail remains a `LazyColumn`, which already provides scrolling. Each row becomes a 48dp-high interactive target and the rail width becomes at least 48dp. The catalog reserves the corresponding trailing inset so cards do not render beneath the enlarged rail. The list intentionally scrolls on ordinary phones; showing all 27 entries simultaneously is secondary to providing reachable targets.

### Player top toolbar

The phone player retains its current individual action buttons when the available width can accommodate them and a readable title. On narrow widths, the toolbar renders Back, the flexible title, and one 48dp overflow button. Orientation, Chapters, Tracks, Quality, and Settings remain available as overflow menu entries, with unavailable or inapplicable actions retaining the same visibility and enabled rules as their toolbar equivalents.

A pure toolbar policy uses available width and the visible optional-action count to choose expanded or compact presentation. This policy is unit-tested independently from Compose rendering.

## Direct accessibility fixes

- Constrain scrub-preview chapter text to the intended 160dp window, keep it single-line, and apply ellipsis.
- Give the TV PIN dialog's Cancel `Surface` a minimum 48dp height while preserving its existing focus styling.
- Remove the redundant terminal 24dp spacer from the four admin lists; their existing bottom content padding remains the single safe-area gap.
- Replace the episode overview's fixed 60dp height with `heightIn(min = 60.dp)` so increased font scale can expand the three-line synopsis.
- Replace the integer-only tiny-font checks with one matcher that detects every numeric `fontSize` literal below 14sp, including fractional values, and use it for both theme and screen-source validation.

## Testing and verification

The responsive spacing and toolbar-presentation policies are introduced test-first as pure Kotlin functions. The existing typography readability test is strengthened test-first with representative fractional values. The remaining Compose-only modifier changes are verified by compilation in accordance with the repository instruction not to add tests for small UI changes.

Verification consists of:

1. focused unit tests for the new layout policies and typography matcher;
2. the complete Gradle unit test suite;
3. `:androidApp:assembleDebug` for the phone UI;
4. `:androidTvApp:assembleDebug` for the TV UI;
5. `git diff --check`.

## Stack propagation

After the #87 branch is verified and pushed, rebase the descendant branches in this order: #88, #89, #90, #91, #92, and #93. Push rewritten descendant heads with `--force-with-lease`, never plain force. Re-run at least the unit suite and both compile tasks at the final #93 head to confirm the entire reconstructed stack still builds.

No review threads are resolved and no GitHub comments are posted until the fixes are pushed and their current-head behavior has been verified.
