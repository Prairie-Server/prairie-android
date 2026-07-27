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
