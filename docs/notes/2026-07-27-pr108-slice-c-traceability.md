# PR 108 Forward Split: Slice C Traceability

Slice C is stacked on slice B:

```text
base  06a775a32a66c8b602ab0068310e54cb11f655bf
head  split/108-c-room-sync
```

Closed, unmerged PR 108 and its preserved `integrate/ship-everything` branch
remain the archival integration reference.

## Local commits

- `4fb21b89` — Room schema v8, exported schema, download-quality columns,
  migration-test harness, locked/verified test dependencies
- `e5991a53` — identity-contained storage, race-safe staged writes, removed
  server purge, download cancellation/sidecar cleanup, phone/TV lifecycle wiring
- `51d90f92` — profile-triggered drains and creation-ordered per-item outbox

## PR 108 source mapping

| PR 108 commit | Slice C area |
| --- | --- |
| `9597eeee` | Room v8 quality columns; download sidecar/cancel behavior; MediaStore root |
| `05df9c34` | dirty-operation order restoration before a new position write |
| `293e8207` | profile-scope drain trigger and removed-server purge |
| `34f72451` | complete per-server credential namespace purge |
| `d0b8a1b6` | per-item FIFO while an older operation is backing off |
| `e07946fd` | suppress destructive purge after registry decode failure |
| `1ba834a5` | contained identity path encoding |
| `ccc41c2a` | no-follow staged writes, collision/race hardening |
| `16f69ff4` | identity-scoped paths on current collection roots |

This is a forward net-diff split, so rewritten commits do not have identical
patch IDs: unrelated player/subtitle/TV changes from the original commits are
intentionally excluded. The table above is the path-purpose mapping.

## Review corrections beyond PR 108

- Added a real Room 7→8 migration regression. It creates a schema-7 database,
  inserts an existing download, runs the generated auto-migration, validates
  schema 8, and proves both new quality columns are nullable without losing the
  row.
- Added a cancellation regression for removed-server cleanup. Once filesystem
  deletion starts, each server purge completes its paired Room transaction in a
  `NonCancellable` section so app-scope teardown cannot strand a half-purged
  identity after the one removal event has been consumed.
- Added Room's migration test harness with dependency locks and SHA-256
  verification independently reproduced from Google Maven and Maven Central.

## Verification

Focused red/green suites cover:

- schema 7→8 migration and existing-row preservation;
- path traversal, encoded-namespace collisions, symlink/no-follow staged writes,
  public collection roots, profile/server deletion scope;
- corrupt-registry suppression, active-playback deferral, idempotent purge, and
  cancellation during destructive cleanup;
- profile-scope drain triggers, per-item outbox FIFO, and retained retry state.

The final full repository gate and independent review results are recorded on
the draft PR.
