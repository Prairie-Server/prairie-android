# PR #164 Review Remediation Design

## Goal

Make PR #164 safe to merge by correcting four verified Android TV focus-state defects without broadening the feature or changing unrelated navigation behavior.

## Scope

The remediation covers:

1. Calendar held-Up navigation from a non-boundary shelf.
2. Diagnostics initial-focus retries when a `FocusRequester` is temporarily detached.
3. Home detail-return fallback lifetime across its deferred retry.
4. Stale For You detail-return state after an explicit top-menu selection.

The following review observations are intentionally outside this change:

- Diagnostics Up at the first crash-report option remains a consumed boundary because the STATUS section above it has no focusable control.
- Keyless recommendation section kinds remain stable singleton identities under the current server contract.
- Legacy-server duplicate fallback identities are a separate compatibility-hardening concern.
- The unused `shouldFallbackForYouReturnToFilter` predicate is cleanup rather than a behavioral blocker.

## Design

### Calendar repeat routing

`calendarUpFallbackAction` will distinguish content from control boundaries. A repeated Up event with no focused shelf may remain within the current control layer, and a repeated Up event at the first focusable shelf may remain in content. A repeated Up event from any deeper shelf must return `MoveWithinContent`, matching the pre-PR behavior and allowing held D-pad movement to continue one shelf at a time.

The existing action enum and event pipeline remain unchanged. The correction is limited to the predicate ordering and a regression test combining `isRepeat = true` with a non-null, non-boundary shelf index.

### Diagnostics focus retry

`FocusRequester.requestFocus()` returning `false` or throwing while the target is not attached will both map to `RETRY`. The existing six-frame bound prevents an infinite loop. Actual screen disposal is represented by cancellation of the `LaunchedEffect`, so no synthetic `DISPOSED` result is needed for caught focus-request exceptions.

The result enum may retain `DISPOSED` only if another production path still produces it; otherwise it will be removed with the now-unreachable branch. A regression test will require a failed `Result<Boolean>` to map to `RETRY`.

### Home detail-return fallback lifetime

Home will gain an explicit pending lifetime for its card-specific detail-return fallback, parallel in purpose to the For You pending state but limited to the existing one-frame Home retry flow. The fallback must remain the Home launch-card requester through the synchronous resume attempt and, when needed, through the deferred frame retry. It will be cleared after the retry flow finishes, or immediately when no retry is required.

Explicit Home selection will continue clearing the Home return token and retry state. The state transition logic will be extracted or represented by a small pure helper only where needed to make the lifetime regression test deterministic; no generalized focus coordinator will be introduced.

### For You explicit-selection reset

Selecting the For You root from the top menu will clear both `forYouDetailReturnFocusRequest` and `forYouDetailReturnFocusPending` before issuing the normal top-level entry request. This prevents an interrupted detail-return request from suppressing or redirecting the explicit first-content focus handoff.

The reset will apply to explicit root selection only. Returning naturally from item detail will preserve the pending request until the recommendation screen consumes the matching request ID.

## Error and lifecycle behavior

- Focus requests remain best-effort and bounded; failures do not escape the composing coroutine.
- Coroutine cancellation remains authoritative for disposal.
- A stale completion ID cannot consume a newer For You request.
- Explicit menu navigation takes precedence over stale detail-return state.
- No persisted server, profile, or media state changes.

## Testing

Each production change will follow a separate red-green cycle:

1. Add a Calendar test proving repeated Up from a deeper shelf returns `MoveWithinContent`.
2. Change the Diagnostics failure test to require `RETRY` and confirm it fails before implementation.
3. Add a Home state-transition test proving the launch-card fallback remains active through a requested retry and clears afterward.
4. Add a For You explicit-selection reset test proving both request ID and pending state clear together.

After the focused tests pass, run:

- `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvCalendarFocusRoutingTest'`
- `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDiagnosticsStateTest'`
- the focused shell/recommendation state tests introduced or updated by this remediation
- `./gradlew :androidTvApp:testDebugUnitTest`
- `./gradlew :androidTvApp:assembleDebug`
- `git diff --check`

A physical Shield D-pad smoke test remains the release gate for Calendar held-Up movement, Diagnostics initial focus, and Home/For You detail-return restoration.
