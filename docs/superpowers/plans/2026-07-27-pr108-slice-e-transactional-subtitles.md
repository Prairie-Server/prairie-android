# PR 108 Slice E: Transactional Subtitles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Forward-port PR 108's transactional subtitle stack, client-side PGS
support, libass lifetime fixes, and safe letterbox/title-area placement onto
slice D without importing Watch Together or slice-G catalog/TV polish.

**Architecture:** A shared typed transition coordinator owns committed versus
pending subtitle identity. Playback replans are staged without replacing the
working session, then committed or discarded atomically by thin mobile and TV
adapters. Media3 mounting resolves stable artifact identity; PGS extraction,
libass lifetime, sync offsets, and picture/title-safe insets sit below that
transaction boundary.

**Tech Stack:** Kotlin, coroutines/Flow, Media3, Room-backed selection state,
Java/JNI libass bridge, Android local unit tests.

## Global Constraints

- Base is draft slice D (`split/108-d-player-foundation`).
- Preserve closed PR 108 and its branch as archival references; do not merge.
- Do not import Watch Together, TV request UX, catalog polish, or home/startup
  performance work.
- The active session and committed subtitle remain visible until a candidate
  is validated and atomically published.
- Failed, canceled, stale, or superseded candidates must be stopped without
  stopping the working session.
- Persistence occurs only for the exact committed typed identity.
- Every behavioral correction follows a red/green test cycle.

---

### Task 1: Port the shared typed identity and transition model

**Files:**
- Modify: `shared/src/commonMain/kotlin/org/siloserver/silo/model/playback/PlaybackModels.kt`
- Modify: `shared/src/commonMain/kotlin/org/siloserver/silo/model/playback/PlaybackProtocolV3.kt`
- Create: `shared/src/commonMain/kotlin/org/siloserver/silo/model/playback/SubtitleTransition.kt`
- Create: `shared/src/commonMain/kotlin/org/siloserver/silo/playback/SubtitleCodecFamily.kt`
- Create: `shared/src/commonMain/kotlin/org/siloserver/silo/playback/SubtitleLanguage.kt`
- Create: `shared/src/commonMain/kotlin/org/siloserver/silo/playback/TrackSelectionFingerprint.kt`
- Test: matching `shared/src/commonTest` model/playback suites

**Interfaces:**
- Produces typed Off/server-sidecar/server-burn-in/embedded/downloaded/local
  identity and latest-intent-wins transition effects.
- Consumed by shared mounting plus mobile/TV adapters.

- [ ] Port only the subtitle model/fingerprint paths from squash `65c4b316`.
- [ ] Run the focused common tests and observe any missing-identity failures.
- [ ] Reconcile current slice-D model changes with the archival implementation.
- [ ] Run focused tests green and commit.

### Task 2: Port staged session publication and settlement

**Files:**
- Modify: `android-shared/.../player/PlaybackSessionManager.kt`
- Modify: `android-shared/.../player/PlaybackSessionLifecycle.kt`
- Create/modify: staged-replan and publication-settlement tests under
  `android-shared/src/androidUnitTest/.../player`

**Interfaces:**
- Produces `StagedVideoReplan`, one-use commit/discard, deferred publication,
  confirmation, rollback, bounded orphan cleanup, and active-session abandon.
- Preserves slice-D external-start epoch/cancellation ownership rules.

- [ ] Restore real staged-replan tests from `65c4b316`; run them red.
- [ ] Port the minimum shared manager/lifecycle foundation from `65c4b316`.
- [ ] Apply corrections `d7241141`, `8403b8b8`, the manager portion of
  `c8481f1c`, and `ac14e51a` in order.
- [ ] Verify content reset cannot wedge, in-place rollback preserves the server
  cursor, and cancellation cannot orphan either candidate or predecessor.
- [ ] Run shared player suites green and commit.

### Task 3: Port shared subtitle mounting and Media3 contracts

**Files:**
- Create/modify: `SubtitleMountResolver.kt`, `SubtitleManager.kt`,
  `SiloPlayerFactory.kt`, `VideoPlayerMediaSpec.kt`,
  `VideoPlayerMediaMounter.kt`, backend interfaces, and their tests.

**Interfaces:**
- Maps typed committed identity to exact Media3 tracks and planned artifacts.
- Rejects coincidental same-label matches when stable identity is known.

- [ ] Restore resolver/track-selection tests and observe red failures.
- [ ] Port shared mount/parser paths from `65c4b316`.
- [ ] Apply `f609c431`, `4add279c`, `1ad970c9`, `274ffc61`, and `823ca359`.
- [ ] Verify Off omits `subtitle_track_index`, bitmap tracks choose burn-in
  unless client-side PGS is enabled, and catalog-only rows remain selectable.
- [ ] Run focused shared tests green and commit.

### Task 4: Port mobile and TV transaction adapters

**Files:**
- Create/modify mobile `MobileSubtitleTransactionAdapter`,
  `MobilePlayerLoadOwner`, restore/auto-selection helpers, ViewModel/screen.
- Create/modify TV `TvSubtitleTransactionAdapter`, load owner, identity,
  policy, HUD state, remount reselection, ViewModel/screen/HUD.
- Restore the corresponding real integration/ownership/settlement tests.

**Interfaces:**
- Consumes shared coordinator and staged session manager.
- Publishes committed selection only after mount and publication settlement.

- [ ] Restore adapter/integration tests from `65c4b316`; run representative
  latest-intent, failure rollback, and content-reset cases red.
- [ ] Port the adapter/view-model paths while preserving slice-D clock,
  recreation, and exit ownership fixes.
- [ ] Apply `a36c7211`, `370c5a79`, `38584061`, and exact mobile/TV corrections
  from later subtitle commits.
- [ ] Verify A→B→Off, stale refresh, downloaded rebasing, same-label tracks,
  publication rollback, and fresh-load ownership on both platforms.
- [ ] Run mobile/TV subtitle suites green and commit.

### Task 5: Add PGS extraction and libass lifetime/timeline fixes

**Files:**
- Create: `android-shared/.../player/subtitle/PgsSupExtractor.kt`
- Modify: player factory/service/backend/settings and `libass-bridge` handler.
- Restore PGS binary fixture and focused parser/probe/lifetime tests.

**Interfaces:**
- Provides framed PGS sidecars to Media3 when capability/policy permits.
- Gives each player a bounded libass handler lifetime and releases it at end.

- [ ] Restore PGS tests/fixture; run red before extractor implementation.
- [ ] Apply `f0bc39fe`, `6a904254`, `eebe9dd3`, and `a02d5622`.
- [ ] Apply libass fixes `05df9c34`, `e080df48`, and `f3888bee`, retaining
  slice-C FIFO outbox semantics.
- [ ] Verify PGS END sections, mount/select/reconcile, per-player font cleanup,
  release-on-end, and source/player timeline offset.
- [ ] Run focused PGS/libass suites green and commit.

### Task 6: Port appearance, sync, diagnostics gating, and safe insets

**Files:**
- Modify subtitle appearance/sync models, manager/service, phone/TV screens.
- Add/restore `LetterboxInsetTest`, `TitleSafeInsetTest`, appearance and sync
  tests.

**Interfaces:**
- Applies per-item sync and web-parity appearance to committed subtitles.
- Clamps cues to server-provided picture bounds and TV title-safe bounds.

- [ ] Restore appearance/sync/inset tests; run red.
- [ ] Apply `8e42f428`, `8cb8ed3f`, `a910f0d9`, and `4b87b24c`.
- [ ] Apply diagnostics changes `8c2339c4`, `dcfbece5`, and final gate/delete
  behavior from `e22e3190`.
- [ ] Verify baked-in bars, title-safe clamping, selectable CC rows, and
  diagnostics disabled by default.
- [ ] Run focused suites green and commit.

### Task 7: Audit, review, verify, and publish

**Files:**
- Update this plan with final source-to-local commit mapping and verification.

- [ ] Audit the net diff for Watch Together, request UX, home/startup, reader,
  and catalog-only leakage; remove any unrelated paths.
- [ ] Run `git diff --check` and all focused subtitle/player suites.
- [ ] Run supply-chain policy, `testDebugUnitTest`, and phone/TV release
  assemblies.
- [ ] Obtain independent code/lifecycle/security review; fix every
  Critical/Important finding test-first and repeat the full gate.
- [ ] Push `split/108-e-subtitles` and create a draft PR based on
  `split/108-d-player-foundation`; do not merge.

## Forward-port mapping

| Local commit | PR 108 sources |
| --- | --- |
| `e1f7730f` | `65c4b316` typed subtitle identity/transition subset |
| `fbb001fd` | `65c4b316`, `d7241141`, `8403b8b8`, `c8481f1c`, `ac14e51a` |
| `5096cb7a` | `65c4b316`, `f609c431`, `4add279c`, `1ad970c9`, `274ffc61`, `823ca359` |
| `74907f1c` | `65c4b316`, `a36c7211`, `370c5a79`, `38584061`, corrected `4add279c` boundary |
| `23df27e4` | `f0bc39fe`, `6a904254`, `eebe9dd3`, `a02d5622`, `05df9c34`, `e080df48`, `f3888bee` |
| pending appearance/insets commit | `8e42f428`, `8cb8ed3f`, `a910f0d9`, `4b87b24c`, `8c2339c4`, `dcfbece5`, `e22e3190` |
