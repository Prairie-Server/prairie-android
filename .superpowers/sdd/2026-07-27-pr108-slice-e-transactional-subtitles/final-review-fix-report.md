# Slice E final-review fix report

## Status

Implemented all three Important findings from `final-review-fix-brief.md`:

1. final track-selection writes now carry the playback's captured
   `PlaybackWriteScope` through the phone/TV adapter context into Room, which
   rejects the write after a server, profile, credential-overlay, or auth
   identity-generation switch;
2. phone and TV adapters now obtain ordering tickets from one process-global
   `PlaybackTrackSelectionWriteCoordinator`, keyed by captured auth scope plus
   content/file identity, so a retired adapter cannot overwrite a replacement
   adapter's newer durable selection;
3. `PgsSupExtractor` now fails closed once one display set exceeds 16 MiB or
   512 segments, before allocating/reading the segment that crosses the bound.

The pre-existing modification to
`docs/superpowers/plans/2026-07-27-pr108-slice-e-transactional-subtitles.md`
was preserved and excluded from this fix.

## Design

### Captured auth ownership

- Added the scoped `UserItemStatePort.recordTrackSelection(...)` overload,
  parallel to the existing scoped final-position API.
- `RoomUserItemStateRepository` compares server id, profile id, credential
  generation, and identity generation against one current auth snapshot. A
  mismatch returns `false` without writing either the old or current partition.
- `PlayerViewModel` and `TvPlayerViewModel` attach the scope captured at playback
  load to every subtitle playback context. A context without a captured scope
  fails closed and does not create a persistence request.

### Cross-adapter latest-write-wins

- Each persistence request captures a monotonically increasing ticket when the
  committed selection is captured, not when its delayed coroutine happens to
  run.
- The process coordinator retains per-key started/durable sequence state and a
  per-key mutex. This preserves existing adapter FIFO/retry behavior, permits
  unrelated content keys to proceed independently, and suppresses an older
  retired-adapter ticket after a newer replacement-adapter ticket is durable.
- Phone and TV use the same coordinator implementation and the same process
  singleton within their respective app process.

### Bounded PGS framing

- The extractor counts the container-shaped bytes and segments accumulated for
  the current display set.
- Crossing either bound discards the incomplete display set, marks the
  extractor failed closed, and returns end-of-input without reading the
  offending payload.
- Complete display sets, timestamp/offset behavior, identity preservation,
  truncation behavior, seek reset, and END-segment parsing are unchanged.

## Files

Production:

- `shared/src/commonMain/kotlin/org/prairieserver/prairie/repository/port/UserItemStatePort.kt`
- `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/data/repository/RoomUserItemStateRepository.kt`
- `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/PlaybackTrackSelectionWriteCoordinator.kt`
- `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/subtitle/PgsSupExtractor.kt`
- `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/player/MobileSubtitleTransactionAdapter.kt`
- `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/player/PlayerViewModel.kt`
- `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvSubtitleTransactionAdapter.kt`
- `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerViewModel.kt`

Tests:

- `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/data/repository/RoomUserItemStateRepositoryTest.kt`
- `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/player/PlaybackTrackSelectionWriteCoordinatorTest.kt`
- `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/player/subtitle/PgsSupExtractorTest.kt`
- `androidApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/android/ui/screens/player/MobileSubtitleTransactionAdapterTest.kt`
- `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TestPlaybackWriteScope.kt`
- TV transactional test context helpers updated to provide an explicit captured
  test scope: `SubtitleTransactionIntegrationTest`,
  `TvSubtitleFinalRollbackTest`, `TvSubtitleMountDeadlineTest`,
  `TvSubtitleRefreshOwnershipTest`, `TvSubtitleSettlementOwnershipTest`, and
  `TvSubtitleTransactionAdapterTest`.

## TDD evidence

### RED: malformed PGS

Command:

```text
./gradlew :android-shared:testDebugUnitTest --tests 'org.prairieserver.prairie.common.player.subtitle.PgsSupExtractorTest'
```

Observed:

```text
PgsSupExtractorTest > anOversizedDisplaySetWithoutEndFailsClosedBeforeConsumingTheStream FAILED
PgsSupExtractorTest > tooManySegmentsWithoutEndFailClosedBeforeConsumingTheStream FAILED
8 tests completed, 2 failed
BUILD FAILED
```

The byte-bound test showed the current extractor consumed the full oversized
stream. The segment-bound test hit the test drain guard because the extractor
continued accepting segments indefinitely.

### RED: auth scope and process-global ordering

Command:

```text
./gradlew :android-shared:testDebugUnitTest \
  --tests 'org.prairieserver.prairie.common.data.repository.RoomUserItemStateRepositoryTest' \
  --tests 'org.prairieserver.prairie.common.player.PlaybackTrackSelectionWriteCoordinatorTest'
```

Observed compile RED:

```text
RoomUserItemStateRepositoryTest: no applicable scoped recordTrackSelection
PlaybackTrackSelectionWriteCoordinatorTest: unresolved reference
Task :android-shared:compileDebugUnitTestKotlinAndroid FAILED
BUILD FAILED
```

This established that neither the scope-bound repository contract nor shared
ordering owner existed before implementation.

## GREEN evidence

Fresh focused verification command:

```text
./gradlew \
  :android-shared:testDebugUnitTest \
    --tests 'org.prairieserver.prairie.common.data.repository.RoomUserItemStateRepositoryTest' \
    --tests 'org.prairieserver.prairie.common.player.PlaybackTrackSelectionWriteCoordinatorTest' \
    --tests 'org.prairieserver.prairie.common.player.subtitle.PgsSupExtractorTest' \
  :androidApp:testDebugUnitTest \
    --tests 'org.prairieserver.prairie.android.ui.screens.player.MobileSubtitleTransactionAdapterTest' \
  :androidTvApp:testDebugUnitTest \
    --tests 'org.prairieserver.prairie.tv.ui.screens.player.TvSubtitleTransactionAdapterTest' \
  --rerun-tasks
```

Observed:

```text
RoomUserItemStateRepositoryTest: 33 tests, 0 failures/errors
PlaybackTrackSelectionWriteCoordinatorTest: 1 test, 0 failures/errors
PgsSupExtractorTest: 8 tests, 0 failures/errors
MobileSubtitleTransactionAdapterTest: 49 tests, 0 failures/errors
TvSubtitleTransactionAdapterTest: 79 tests, 0 failures/errors
BUILD SUCCESSFUL in 26s
118 actionable tasks: 118 executed
```

Total focused result: 170 tests, 0 failures, 0 errors.

## Round 2 review fixes

### Status and design

- Phone and TV persistence ports now return the scoped Room write result.
  A coordinator ticket becomes durable only when Room returns `true`; `false`
  results are retried by the existing bounded adapter policy and remain
  non-durable when every attempt is rejected.
- TV teardown now reserves its ordering ticket synchronously before
  `invalidateAndSettleAsync`. The callback pairs that reserved ticket and
  captured playback context with the exact committed subtitle identity visible
  after settlement, preventing a retired TV adapter from overwriting a newer
  replacement adapter.
- Coordinator state is retained only while tickets for a key remain
  outstanding. Successful, suppressed, rejected, cancelled, and unqueued
  requests resolve or abandon their ticket, allowing the per-key state to be
  removed without weakening stale-write suppression.

Deterministic tests cover rejected scoped writes, bounded retry/flush behavior,
two real TV adapters separated by a gated teardown callback, and reclamation
across 2,000 distinct coordinator keys.

### RED evidence

Command:

```text
./gradlew \
  :android-shared:testDebugUnitTest \
    --tests 'org.prairieserver.prairie.common.player.PlaybackTrackSelectionWriteCoordinatorTest' \
  :androidApp:testDebugUnitTest \
    --tests 'org.prairieserver.prairie.android.ui.screens.player.MobileSubtitleTransactionAdapterTest' \
  :androidTvApp:testDebugUnitTest \
    --tests 'org.prairieserver.prairie.tv.ui.screens.player.TvSubtitleTransactionAdapterTest'
```

Observed compile RED:

```text
PlaybackTrackSelectionWriteCoordinatorTest: unresolved reference activeKeyCount
Mobile/TV test persistence implementations returning Boolean were incompatible
  with the Unit-returning persistence ports
TvSubtitleTransactionAdapterTest: unresolved durable reservation API
BUILD FAILED
```

### GREEN evidence

Fresh focused verification command:

```text
./gradlew \
  :android-shared:testDebugUnitTest \
    --tests 'org.prairieserver.prairie.common.data.repository.RoomUserItemStateRepositoryTest' \
    --tests 'org.prairieserver.prairie.common.player.PlaybackTrackSelectionWriteCoordinatorTest' \
    --tests 'org.prairieserver.prairie.common.player.subtitle.PgsSupExtractorTest' \
  :androidApp:testDebugUnitTest \
    --tests 'org.prairieserver.prairie.android.ui.screens.player.MobileSubtitleTransactionAdapterTest' \
  :androidTvApp:testDebugUnitTest \
    --tests 'org.prairieserver.prairie.tv.ui.screens.player.TvSubtitleTransactionAdapterTest' \
    --tests 'org.prairieserver.prairie.tv.ui.screens.player.TvSubtitleSettlementOwnershipTest' \
  --rerun-tasks
```

Observed:

```text
RoomUserItemStateRepositoryTest: 33 tests, 0 failures/errors
PlaybackTrackSelectionWriteCoordinatorTest: 2 tests, 0 failures/errors
PgsSupExtractorTest: 8 tests, 0 failures/errors
MobileSubtitleTransactionAdapterTest: 51 tests, 0 failures/errors
TvSubtitleTransactionAdapterTest: 82 tests, 0 failures/errors
TvSubtitleSettlementOwnershipTest: 32 tests, 0 failures/errors
BUILD SUCCESSFUL in 24s
118 actionable tasks: 118 executed
```

Round 2 focused result: 208 tests, 0 failures, 0 errors.

## Round 3 review fix

### Status and design

The coordinator now counts every active or mutex-waiting `write` invocation
for a key. Registration is atomic with the ticket's unresolved check, and
unregistration runs in an exception/cancellation-safe `finally` block. The
key's state and mutex are removed only after both its outstanding-ticket count
and write-invocation count reach zero. A replacement capture therefore reuses
the old mutex until every abandoned primary/fallback invocation has exited.

The deterministic regression test gates an old primary inside persistence,
queues a same-ticket fallback, abandons the ticket twice, and begins a
replacement write. It verifies the replacement cannot enter persistence before
the primary is released, the abandoned fallback never persists, the final
durable value is the replacement's `B`, and the state is reclaimed afterward.

### RED evidence

Command:

```text
./gradlew :android-shared:testDebugUnitTest \
  --tests 'org.prairieserver.prairie.common.player.PlaybackTrackSelectionWriteCoordinatorTest'
```

Observed:

```text
PlaybackTrackSelectionWriteCoordinatorTest >
  abandonedBlockedWriteKeepsReplacementSerializedUntilOldWriteExits FAILED
3 tests completed, 1 failed
BUILD FAILED in 2s
```

The replacement entered persistence while the old primary was still gated,
demonstrating that abandonment had created a second mutex for the same key.

### GREEN evidence

Fresh focused verification command:

```text
./gradlew :android-shared:testDebugUnitTest \
  --tests 'org.prairieserver.prairie.common.player.PlaybackTrackSelectionWriteCoordinatorTest' \
  --rerun-tasks
```

Observed:

```text
PlaybackTrackSelectionWriteCoordinatorTest: 3 tests, 0 failures/errors
BUILD SUCCESSFUL in 15s
65 actionable tasks: 65 executed
```

## Concerns

- Coordinator memory is bounded by unresolved tickets and active/waiting write
  invocations rather than all keys seen during the process. A genuinely
  outstanding request keeps its small per-key ordering record until the ticket
  is resolved and every invocation using its mutex has exited.
- Existing Gradle/Kotlin deprecation and opt-in warnings remain; the focused
  run introduced no new warning from the changed production/test files.
- No push, merge, or PR action was performed.
