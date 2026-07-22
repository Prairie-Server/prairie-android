# Android TV Detail Hero Geometry Design

**Date:** 2026-07-22
**Status:** Approved

## Context

The tvOS detail hero is a fixed 980-point frame on a 1080-point canvas. Android
correctly maps that ratio to `0.907 * screenHeightDp`, but currently applies it
as a minimum height. When the editorial and action content asks for more room,
the whole hero grows and its backdrop grows with it. This makes Android detail
pages look materially larger than tvOS and pushes the first content rail too far
below the fold.

The minimum-height behavior was introduced to prevent the action and selector
rows from receiving zero remaining height after a tall editorial column was
measured. Restoring a fixed height without changing that measurement order would
reintroduce invisible-but-focusable controls.

## Goals

- Match the tvOS detail hero height at 90.7% of the viewport.
- Keep the backdrop, horizontal scrim, and bottom fade inside that fixed frame.
- Keep every action and selector control visible and focusable.
- Preserve the current TV typography readability floors.
- Keep the first below-hero section at the existing, intentional handoff gap.

## Non-goals

- Changing root Home or library-landing hero geometry.
- Redesigning detail-page typography, metadata, actions, or focus order.
- Changing audiobook detail geometry.
- Launching or installing the app on the Shield as part of implementation.

## Design

### Fixed outer frame

`TvDetailHero` will use a fixed height of
`screenHeightDp * HERO_HEIGHT_FRACTION` and clip its backdrop and gradients to
that frame. `HERO_HEIGHT_FRACTION` remains `0.907f`, preserving the tvOS
`980 / 1080` proportion on every Android TV viewport.

### Action-first vertical budgeting

The bottom editorial/action area will no longer depend on a normal `Column`
measuring all editorial content before its trailing controls. It will reserve
the action and selector cluster first, pinned above `TvDetailHeroBottomInset`.
The editorial column receives only the remaining height above that reserved
cluster. This keeps controls painted, focusable, and in the same visual order
without allowing them to enlarge the hero.

The implementation may use a small dedicated two-pass Compose layout or an
equivalent action-first measurement boundary. It must not use delayed
`onSizeChanged` state that causes a visible first-frame jump.

### Constrained editorial content

The title, source row, synopsis, translation affordance, and facts row retain
their current order and font sizes. Normal cases keep the existing spacing and
three-line collapsed synopsis. If their measured height exceeds the available
editorial budget, the constrained presentation will:

1. reduce inter-block gaps from 12dp to 8dp;
2. clamp collapsed synopsis copy to two lines;
3. keep title typography, action geometry, and focus targets unchanged.

Optional copy and whitespace absorb constrained cases. Controls and font-size
floors do not. Expanded synopsis behavior remains unchanged and is clipped to
the hero's editorial budget rather than resizing the page.

### Below-hero handoff

`TvDetailHeroBottomInset`, `TvDetailSectionGap`, and the body section padding
remain unchanged. Once the hero stops growing, the first rail will again begin
at a stable tvOS-like position relative to the viewport.

## Validation

Per the repository policy for small UI changes, no new committed UI test is
required. Implementation verification will include:

- a red/green source assertion proving the hero no longer uses a minimum height;
- Android TV unit tests;
- Android TV debug compilation;
- diff and whitespace validation;
- inspection at both normal and constrained logical viewport heights without
  installing or launching the application on the Shield.

## PR placement

The change belongs in PR #89, which owns Android TV navigation and detail-page
presentation. Downstream PRs #90 through #93 will be restacked after the
implementation is verified.
