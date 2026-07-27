# PR 108 Slice D: Player Foundation Plan

## Goal

Create a reviewable slice stacked on slice C that contains only player
lifecycle, performance, ownership, and durable final-write foundations from
closed PR 108. Transactional subtitle/PGS/libass/letterbox behavior remains in
slice E.

## Source mapping

The performance work entered PR 108 through squash `65c4b316`; its focused
source commits are:

- TV clock/exit: `f4444698`, `582b554c`, `94cb09b1`, `49cae1ff`
- mobile clock/lifecycle/recreation: `177b5fe7`, `491eded9`, `93d7318d`,
  `ee59fa77`, `94c60922`
- identity-bound final writes: `fade71e8`

Later focused PR 108 corrections to port after that foundation:

- `5811f0bf`, `cc5d2b2c`, `8403b8b8`, `c8481f1c`, `ac14e51a`,
  `b64b7c04`
- evaluate `a36c7211` by behavior: include only the session-ownership cleanup,
  leaving subtitle transaction semantics for slice E.

## Steps

1. Compare each focused source commit against current slice-C/upstream state;
   omit changes already present and document path-purpose mapping.
2. Port TV/mobile clock isolation, non-blocking teardown, recreation ownership,
   and identity-captured final writes in dependency order.
3. Add or preserve deterministic lifecycle tests for start-vs-exit,
   publication settlement, final-write failure retention, identity switches,
   and recreation.
4. Port the later lifecycle corrections without pulling subtitle rendering or
   Watch Together behavior forward.
5. Run focused red/green suites, then all debug unit tests and phone/TV release
   assemblies with the supply-chain gate.
6. Obtain independent code/lifecycle review, fix findings, rerun the full gate,
   publish a draft stacked PR, and record commit/range-diff traceability.

## Guardrails

- No merge and no changes to closed PR 108.
- No subtitle renderer/PGS/libass/letterbox behavior in this slice.
- Final writes must use the identity captured when playback began.
- Screen recreation must not duplicate or tear down an adopted session.
- Teardown must not block the UI thread or publish stale terminal state.

## Implemented traceability

This branch is a forward port against slice C, so the local commit ids differ
from the archival PR 108 ids. The path/purpose mapping is:

- TV clock, seek, duration, and non-blocking exit:
  `f4444698`, `582b554c`, `94cb09b1`, `49cae1ff` ->
  `072c61ab`, `676c6b87`, `d44a4f3b`, `7d401ac8`, `7b481d31`.
- Mobile clock isolation, recreation ownership, lifecycle-safe teardown, and
  episode rollup:
  `177b5fe7`, `491eded9`, `93d7318d`, `ee59fa77`, `94c60922` ->
  `ec0f0dd2`, `63c5db6d`, `8ae2a4b1`, `5ca6a48a`, `7b481d31`.
- Identity-captured final writes and failure retention:
  `fade71e8`, `c8481f1c` -> `cec1f7ed`, `c2a63d5f`.
- Start-versus-exit ownership, retry accounting, and authenticated PiP actions:
  `5811f0bf`, `cc5d2b2c`, `b64b7c04` ->
  `a1d9e491`, `c888efe3`, `78015de8`, with the D-only lifecycle reconstruction
  in `e3e3b043`.

The following fixes are intentionally deferred intact to slice E because their
contracts depend on transactional subtitle publication and staged replans:
`8403b8b8`, `a36c7211`, `ac14e51a`, and the staged-publication portion of
`c8481f1c`. Pulling `8403b8b8` into D would otherwise import roughly 1,300
lines of the subtitle transaction implementation.

## Verification record

- Focused lifecycle race test observed failing before the epoch guard and
  passing afterward.
- Queued-stop ownership acquisition, canceled post-allocation adoption cleanup,
  and stale failure publication were each observed failing before their fixes
  and passing afterward.
- Shared player, phone player/detail, and TV player focused unit suites pass.
- Final independent review: **Ready: YES**, with no remaining
  Important/Critical findings. The proposed second Room staging queue was
  withdrawn because the target write is already the atomic Room state/outbox
  transaction; an asynchronous staging write has the same pre-persistence
  process-death gap and cannot survive failure of that same store.
- Final gate:
  `./scripts/test-check-build-supply-chain.sh`,
  `./scripts/check-build-supply-chain.sh`, and
  `./gradlew -Dorg.gradle.jvmargs="-Xmx4g -Dfile.encoding=UTF-8"
  testDebugUnitTest :androidApp:assembleRelease
  :androidTvApp:assembleRelease --max-workers=2`.
  Result: **BUILD SUCCESSFUL**, 334 tasks, 2m50s.
