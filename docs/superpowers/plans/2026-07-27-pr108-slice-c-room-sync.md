# PR 108 Slice C: Room, Identity Storage, and Sync Plan

## Goal

Create a reviewable vertical slice stacked on slice B that upgrades Room to
schema v8, makes local/download storage identity-scoped and race-safe, purges
removed-server data only after a trustworthy registry load, and preserves
per-item outbox ordering across identity changes.

## Archival source map

Use the net behavior from PR 108 commits:

- `9597eeee` — Room v8 download-quality fields and related download persistence
- `05df9c34` — dirty-operation selection/order and repository integration
- `293e8207` — profile-triggered drain and removed-server purge
- `d0b8a1b6` — per-item outbox head-of-line ordering
- `e07946fd` — fail-safe registry-load/purge contract
- `1ba834a5`, `ccc41c2a`, `16f69ff4` — contained, serialized,
  identity-scoped storage paths
- `34f72451` — credential purge behavior only where required by the removed
  server lifecycle

Document path-level deviations and patch-ID/commit mappings in the slice
traceability note.

## Implementation order

1. Add failing Room 7→8 migration coverage that proves existing rows survive
   and new nullable quality columns read correctly; then port the v8 entity,
   schema, and auto-migration.
2. Add/port failing storage containment, identity-isolation, public-root
   collision, and cancellation/concurrency regressions; then port the minimum
   storage implementation required to pass them.
3. Add/port failing removed-server purge tests, including failed registry-load
   behavior and database/filesystem cleanup; then wire the purger into both app
   lifecycles.
4. Add/port failing profile-change drain and per-item outbox ordering tests;
   then port the DAO/starter/engine/repository changes.
5. Run focused tests after each behavior, then all debug unit tests and phone/TV
   release assemblies with the supply-chain gate.
6. Record original commit/path mapping and conflict decisions, request an
   independent correctness/security review, resolve findings test-first, push,
   and open a draft stacked PR based on `split/108-b-auth-epub`.

## Guardrails

- Do not import player, subtitle, Watch Together, or catalog/TV polish changes.
- Preserve slice B auth/origin changes when resolving shared files.
- Never purge when registry load trust is unknown or failed.
- Cancellation must not publish partial identity-scoped state or release a
  serialization guard before the underlying write finishes.
- Do not merge any PR.
