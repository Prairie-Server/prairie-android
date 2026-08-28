# For You Late Focus-Relocation Recovery Design

## Problem

When focus enters the first recommendation row while the For You list is
already at item zero, the current correction effect exits immediately. Compose
can run the row's focus-driven bring-into-view relocation afterward and move the
list below its intended top anchor. Moving down and back up re-enters the row and
re-arms the correction, which is why the screen then recovers.

## Design

Keep a scroll-position observer active only while the first recommendation row
owns focus. The observer remains suspended while the list is correctly anchored
and reacts only when the list position changes. If a later focus-relocation pass
moves the list away from item zero, wait for that relocation to settle, confirm
the row still owns focus and the list is still displaced, then animate back to
item zero. Leaving the row cancels the observer through the existing
focus-keyed `LaunchedEffect`.

The observer and correction loop will be extracted behind a small suspend
helper that accepts position events and scroll callbacks. This keeps the
timing policy testable without a Compose UI harness while production continues
to obtain positions from `snapshotFlow` over the real `LazyListState`.

## Alternatives Rejected

- A fixed number of settling polls adds arbitrary timing and can still miss a
  slower Fire TV relocation.
- Changing the shared bring-into-view policy affects every recommendation row
  and risks wider D-pad navigation regressions.
- Continuous polling for the entire focus lifetime wakes unnecessarily even
  when the list does not move; an event-driven observer has no such activity.

## Verification

Add a coroutine regression test that emits an initially correct top position,
then emits a delayed displaced position while focus remains in the first row.
The test must fail against the current early-exit behavior and pass only when
the delayed displacement triggers one top-anchor correction. Also cover that a
top-only sequence is a no-op and that focus loss prevents a pending correction.

Run the focused test, the complete Android TV unit suite, release-workflow and
supply-chain checks, and assemble the Android TV debug APK with the full RC
display version.
