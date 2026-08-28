# Ebook Reader — Phase 4: Highlights & Notes (client + server) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Deliver synced highlights & notes across every reflowable ebook format. Generalize the server annotation model from CFI-only to carry a typed `ReaderLocator` *range* (start + end) while keeping `cfiRange` accepted for backward compatibility, then build the client text-selection toolbar, highlight overlays, and a Highlights sheet that lists/jumps/edits/deletes — syncing through the updated `EbookReaderApi`, with a client-local fallback (no sync) behind the same UI for use before the server change lands.

**Architecture:** Server lands first (Go + Goose/Postgres): a Goose migration adds nullable `locator_range JSONB` to `ebook_reader_annotations` (no destructive change to existing rows; `cfi_range` retained), the Go handler/model/store carry the new column, and the create/list/patch validation accepts *either* a `cfi_range` (legacy) *or* a `locator_range` for highlights/notes. The client adds a `ReaderLocator` range model in `shared`, maps annotations to/from it, renders highlight overlays per format (EPUB via epub.js JS injection — Phase 2 bridge assumed; text/FB2 via Compose spans), and presents a selection toolbar + Highlights sheet mirroring the existing `BookmarkSheet`. When the server lacks `locator_range` support, the client still functions: it writes `cfi_range` for EPUB and falls back to a client-local store (no sync) for non-CFI formats.

**Tech Stack:** Kotlin/Compose client (`shared` KMP module + `androidApp`), kotlinx.serialization, Ktor; Go server with chi router, pgx/Postgres, Goose migrations.

This plan implements **Phase 4 only** of `docs/superpowers/specs/2026-06-12-ebook-reader-enhancements-design.md` (§5 highlights, §6 server changes, §8 phase 4, §9). Commands assume the repository root is the cwd: `prairie-server` for SERVER tasks, `prairie-android` for CLIENT tasks. The two repos are siblings under the same parent directory.

**Assumptions carried from earlier phases (per spec §9):**
- Phase 1 shipped a typed `ReaderLocator` in `shared` and a backward-compatible `"page:N"` reader. **This plan does NOT assume that type already exists** — Phase 1 has not landed in the read codebase, so Task 6 below defines the minimal `ReaderLocator` range types this phase needs (`ReaderLocator`, `ReaderLocatorRange`) and notes they should be folded into / reconciled with Phase 1's model if Phase 1 lands first. Reconciliation is explicit in Task 6 so there is no silent drift.
- Phase 2 shipped the epub.js WebView JS bridge with text-selection events. The EPUB overlay/selection wiring (Task 11) is written against that bridge contract; where the bridge is not yet present, the steps are gated behind a `// PHASE 2 BRIDGE` marker and the client-local fallback path (Task 10) keeps the feature usable.

---

## File Structure

### SERVER (prairie-server, Go) — lands first

| Path | Responsibility |
| --- | --- |
| `migrations/sql/<timestamp>_ebook_annotation_locator_range.sql` | NEW Goose migration: add nullable `locator_range JSONB` column to `ebook_reader_annotations`; relax the table CHECK so highlights/notes may anchor via `cfi_range` OR `locator_range`. No data rewrite. |
| `internal/api/handlers/ebook_reader.go` | EDIT: add `LocatorRange json.RawMessage` to `EbookReaderAnnotation`, request, and patch structs; thread it through `buildEbookReaderAnnotation`, `mergeEbookReaderAnnotationPatch`, `validateEbookReaderAnnotation`; update the PG store List/Create/Update SQL + `scanEbookReaderAnnotation`. |
| `internal/api/handlers/ebook_reader_test.go` | EDIT: table-driven validation tests for locator-range-anchored annotations + legacy CFI; create/list/patch round-trips carrying `locator_range`. |

### CLIENT (prairie-android, Kotlin/Compose)

| Path | Responsibility |
| --- | --- |
| `shared/src/commonMain/kotlin/com/continuum/app/model/reader/ReaderLocator.kt` | NEW: typed `ReaderLocator` + `ReaderLocatorRange` (start/end) serializable models. The annotation anchor type. |
| `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookReaderModels.kt` | EDIT: add `locatorRange: ReaderLocatorRange?` to `EbookAnnotation` and `SaveEbookAnnotationRequest`; add `highlightAnnotation(...)` factory mirroring `localBookmarkAnnotation`. |
| `shared/src/commonMain/kotlin/com/continuum/app/repository/EbookReaderRepository.kt` | EDIT: add `createHighlight` / `updateHighlight` convenience methods over the existing annotation API. |
| `shared/src/commonMain/kotlin/com/continuum/app/common/ebook/HighlightColor.kt` | NEW: the highlight color palette (shared so Apple/TV can adopt). |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/AnnotationController.kt` | NEW: VM-facing controller holding highlights state, create/edit/delete with optimistic local + sync, and the client-local fallback store. |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt` | EDIT: own highlights list, selection state, and `AnnotationController`; expose add/edit/delete/jump; load highlights alongside bookmarks. |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt` | EDIT: add a Highlights toolbar button + `HighlightsSheet` (mirrors `BookmarkSheet`), a selection toolbar, and an editor dialog. |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/TextReader.kt` & `FictionBookReader.kt` | EDIT: emit selection ranges and render highlight spans for text/FB2. |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt` | EDIT: `// PHASE 2 BRIDGE` — wire selection events + highlight injection through the epub.js bridge. |

**Test files (CLIENT):**
- `shared/src/commonTest/kotlin/com/continuum/app/model/reader/ReaderLocatorSerializationTest.kt` (NEW)
- `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookAnnotationSerializationTest.kt` (NEW)

---

## SERVER tasks (land and deploy before client sync)

### Task 1 — Goose migration: add `locator_range` and relax the annotation CHECK

**Files:**
- `migrations/sql/<timestamp>_ebook_annotation_locator_range.sql` (NEW — generated)

- [ ] From `prairie-server` repo root, generate the migration:
  ```
  make migrate-create NAME=ebook_annotation_locator_range
  ```
  (Do NOT run `goose fix`; do NOT hand-author the timestamp or create paired up/down files — the single generated file holds both `-- +goose Up` and `-- +goose Down`.)
- [ ] Fill the generated file with:
  ```sql
  -- +goose Up
  -- +goose StatementBegin
  ALTER TABLE ebook_reader_annotations
      ADD COLUMN locator_range JSONB;

  -- Highlights/notes may now anchor via cfi_range (legacy) OR locator_range.
  -- Bookmarks still require location. Existing rows keep cfi_range and pass.
  ALTER TABLE ebook_reader_annotations
      DROP CONSTRAINT IF EXISTS ebook_reader_annotations_check;

  ALTER TABLE ebook_reader_annotations
      ADD CONSTRAINT ebook_reader_annotations_anchor_check CHECK (
          (kind = 'bookmark' AND location IS NOT NULL)
          OR (kind <> 'bookmark' AND (cfi_range IS NOT NULL OR locator_range IS NOT NULL))
      );
  -- +goose StatementEnd

  -- +goose Down
  -- +goose StatementBegin
  -- Restore the original CFI-only anchor invariant. Safe only because no
  -- highlight should rely solely on locator_range before this migration is
  -- rolled back in practice; the down path is for dev/test parity.
  ALTER TABLE ebook_reader_annotations
      DROP CONSTRAINT IF EXISTS ebook_reader_annotations_anchor_check;

  ALTER TABLE ebook_reader_annotations
      ADD CONSTRAINT ebook_reader_annotations_check CHECK (
          (kind = 'bookmark' AND location IS NOT NULL)
          OR (kind <> 'bookmark' AND cfi_range IS NOT NULL)
      );

  ALTER TABLE ebook_reader_annotations
      DROP COLUMN IF EXISTS locator_range;
  -- +goose StatementEnd
  ```
  > **CHECK constraint name note:** The original inline `CHECK (...)` in `20260608000300_ebook_reader_state.sql` is unnamed, so Postgres auto-names it `ebook_reader_annotations_check`. The `DROP CONSTRAINT IF EXISTS ebook_reader_annotations_check` above targets that auto-name. Verify the live name before relying on the Down path:
  > ```
  > make migrate-up
  > docker compose exec -T postgres psql -U prairie -d prairie -c "\d+ ebook_reader_annotations" | grep -i check
  > ```
  > If the auto-name differs in the target DB, adjust the `DROP CONSTRAINT IF EXISTS` argument in the Up block before applying. The `IF EXISTS` guard keeps the migration safe either way.
- [ ] Validate annotations parse without a DB:
  ```
  make migrate-validate
  ```
- [ ] Apply up, confirm the new column and constraint, then confirm Down restores cleanly (migration up/down safety):
  ```
  docker compose up -d postgres redis
  make migrate-up
  make migrate-status
  ```
- [ ] Manually verify down/up round-trip in dev (do NOT do this against prod):
  ```
  go run ./cmd/prairie/ --env .env --migrate-down-one   # if supported; else use goose directly per Makefile GOOSE var
  make migrate-up
  ```
  Confirm `\d+ ebook_reader_annotations` shows `locator_range jsonb` and the `_anchor_check` constraint after re-up.

### Task 2 — TDD: handler/model validation tests for locator-range annotations

**Files:**
- `internal/api/handlers/ebook_reader_test.go` (EDIT)

Write these tests FIRST (they will fail to compile until Task 3 adds the fields). Mirror the existing table-driven style around `existingEbookReaderAnnotation()` and `fakeEbookReaderAnnotationStore`.

- [ ] Add a table-driven `TestBuildEbookReaderAnnotationLocatorRange` exercising `buildEbookReaderAnnotation` directly:
  ```go
  func TestBuildEbookReaderAnnotationLocatorRange(t *testing.T) {
      validRange := json.RawMessage(`{"start":{"type":"cfi","value":"epubcfi(/6/4!/4/2)"},"end":{"type":"cfi","value":"epubcfi(/6/4!/4/8)"}}`)
      tests := []struct {
          name    string
          req     ebookReaderAnnotationRequest
          wantErr bool
      }{
          {
              name: "highlight with locator range and no cfi",
              req:  ebookReaderAnnotationRequest{Kind: "highlight", LocatorRange: validRange},
          },
          {
              name: "highlight with legacy cfi only still valid",
              req:  ebookReaderAnnotationRequest{Kind: "highlight", CFIRange: "epubcfi(/6/4!/4/2,/4/8)"},
          },
          {
              name:    "highlight with neither cfi nor locator range",
              req:     ebookReaderAnnotationRequest{Kind: "highlight"},
              wantErr: true,
          },
          {
              name:    "locator range that is not a JSON object",
              req:     ebookReaderAnnotationRequest{Kind: "note", LocatorRange: json.RawMessage(`"oops"`)},
              wantErr: true,
          },
          {
              name: "bookmark unaffected by locator range rules",
              req:  ebookReaderAnnotationRequest{Kind: "bookmark", Location: "page:3"},
          },
      }
      for _, tc := range tests {
          t.Run(tc.name, func(t *testing.T) {
              _, err := buildEbookReaderAnnotation(tc.req)
              if tc.wantErr && err == nil {
                  t.Fatalf("expected error, got nil")
              }
              if !tc.wantErr && err != nil {
                  t.Fatalf("unexpected error: %v", err)
              }
          })
      }
  }
  ```
- [ ] Add `TestEbookReaderCreatesLocatorRangeHighlight` posting a highlight with `locator_range` (no `cfi_range`) through `HandleCreateAnnotation` and asserting 201 plus the stored annotation carries the range. Reuse the create-path setup from `TestEbookReaderCreatesAnnotationForAccessibleEbook`.
- [ ] Add a patch test `TestEbookReaderPatchSetsLocatorRange` that PATCHes a legacy CFI highlight to add a `locator_range`, asserting both anchors persist and validation passes (uses `patchEbookReaderAnnotation` helper + `existingEbookReaderAnnotation()`).
- [ ] Extend `existingEbookReaderAnnotation()` only if needed (keep CFI as its default anchor so legacy-path tests stay meaningful).
- [ ] Run (expect FAIL/no-compile until Task 3):
  ```
  go test ./internal/api/handlers/...
  ```

### Task 3 — Generalize the Go annotation model + store

**Files:**
- `internal/api/handlers/ebook_reader.go` (EDIT)

- [ ] Add the field to `EbookReaderAnnotation` (after `Location`):
  ```go
  LocatorRange json.RawMessage `json:"locator_range,omitempty"`
  ```
- [ ] Add to `ebookReaderAnnotationRequest`:
  ```go
  LocatorRange json.RawMessage `json:"locator_range"`
  ```
- [ ] Add to `ebookReaderAnnotationPatchRequest` (raw message: `nil` means absent/keep, present means set; empty-clear is represented by `null`/`{}` per existing metadata handling):
  ```go
  LocatorRange json.RawMessage `json:"locator_range"`
  ```
- [ ] Update `validateEbookReaderAnnotation` so a highlight/note is valid with EITHER anchor, and validate the range shape when present:
  ```go
  func validateEbookReaderAnnotation(annotation EbookReaderAnnotation) error {
      if annotation.Kind != "highlight" && annotation.Kind != "note" && annotation.Kind != "bookmark" {
          return fmt.Errorf("kind must be highlight, note, or bookmark")
      }
      if annotation.Kind == "bookmark" {
          if annotation.Location == "" {
              return fmt.Errorf("location is required for bookmarks")
          }
      } else if annotation.CFIRange == "" && len(annotation.LocatorRange) == 0 {
          return fmt.Errorf("cfi_range or locator_range is required for annotations")
      }
      if len(annotation.LocatorRange) != 0 && !jsonObject(annotation.LocatorRange) {
          return fmt.Errorf("locator_range must be a JSON object")
      }
      if !jsonObject(annotation.Metadata) {
          return fmt.Errorf("metadata must be a JSON object")
      }
      return nil
  }
  ```
- [ ] In `buildEbookReaderAnnotation`, carry the range onto the built annotation (normalize empty to nil so the column stays NULL):
  ```go
  annotation := EbookReaderAnnotation{
      Kind:         kind,
      CFIRange:     strings.TrimSpace(req.CFIRange),
      LocatorRange: trimRawJSON(req.LocatorRange),
      Location:     strings.TrimSpace(req.Location),
      SelectedText: strings.TrimSpace(req.SelectedText),
      Note:         strings.TrimSpace(req.Note),
      Style:        style,
      Color:        color,
      Metadata:     metadata,
  }
  ```
  Add the helper near `jsonObject`:
  ```go
  // trimRawJSON returns nil for empty or whitespace-only raw JSON so absent
  // optional JSON columns persist as SQL NULL rather than empty bytes.
  func trimRawJSON(raw json.RawMessage) json.RawMessage {
      if len(raw) == 0 {
          return nil
      }
      if len(strings.TrimSpace(string(raw))) == 0 {
          return nil
      }
      return raw
  }
  ```
- [ ] In `mergeEbookReaderAnnotationPatch`, set the range when present (mirrors the `Metadata` `!= nil` handling — present-clears-with-empty semantics; an explicit `null` clears the anchor):
  ```go
  if req.LocatorRange != nil {
      merged.LocatorRange = trimRawJSON(req.LocatorRange)
  }
  ```
- [ ] Update the PG store SQL. In `List` and the `Update` SELECT/RETURNING, add `locator_range` after `location`:
  ```sql
  SELECT id, user_id, profile_id, content_id, kind,
         COALESCE(cfi_range, ''), COALESCE(location, ''), locator_range,
         selected_text, note, style, color, metadata, created_at, updated_at
  ```
  In `Create`, add the column + a `$N::jsonb` slot using the same `NULLIF` pattern is wrong for jsonb — bind directly (a nil `json.RawMessage` becomes SQL NULL):
  ```sql
  INSERT INTO ebook_reader_annotations
      (id, user_id, profile_id, content_id, kind, cfi_range, location, locator_range,
       selected_text, note, style, color, metadata, created_at, updated_at)
  VALUES ($1, $2, $3, $4, $5, NULLIF($6, ''), NULLIF($7, ''), $8,
          $9, $10, $11, $12, $13::jsonb, $14, $15)
  ```
  (Renumber the remaining placeholders accordingly: `selected_text`=$9 … `updated_at`=$15. Pass `annotation.LocatorRange` for `$8`.)
  In `Update`'s `UPDATE ... SET`, add `locator_range = $8` and renumber the trailing params; in the `RETURNING` add `locator_range` after `COALESCE(location, '')`.
- [ ] Update `scanEbookReaderAnnotation` to scan `&annotation.LocatorRange` after `&annotation.Location` (pgx scans a SQL NULL jsonb into a nil `json.RawMessage`, which the `omitempty` JSON tag then drops from responses).
- [ ] Run server tests green and full suite:
  ```
  go test ./internal/api/handlers/...
  go test ./...
  ```
- [ ] Self-review vs spec §6: payloads carry a typed `ReaderLocator` JSON range; `cfi_range` still accepted; no destructive change to existing rows (column added nullable, CHECK relaxed not tightened, existing CFI rows still satisfy it). ✅

### Task 4 — Deploy server, confirm backward compatibility

**Files:** none (deploy + verify)

- [ ] Deploy `prairie-server` to the target environment per the deployment runbook (`.claude/skills/deployment-debugging/SKILL.md`) so `migrate-up` runs on boot.
- [ ] Smoke-test against the deployed API: create a legacy `cfi_range` highlight (200/201), create a `locator_range` highlight (200/201), list returns both, PATCH one to add the other anchor. Capture the responses; this is the contract the client codes against.

---

## CLIENT tasks (after server deploy; with client-local fallback before then)

### Task 5 — TDD: `ReaderLocator` range serialization tests

**Files:**
- `shared/src/commonTest/kotlin/com/continuum/app/model/reader/ReaderLocatorSerializationTest.kt` (NEW)

Write these FIRST (fail until Task 6). Use the same `Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false; coerceInputValues = true }` config as `EbookMetadataSerializationTest`.

- [ ] Round-trip a CFI locator range:
  ```kotlin
  package com.continuum.app.model.reader

  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlinx.serialization.json.Json

  class ReaderLocatorSerializationTest {
      private val json = Json {
          ignoreUnknownKeys = true
          isLenient = true
          explicitNulls = false
          coerceInputValues = true
      }

      @Test
      fun roundTripsCfiRange() {
          val range = ReaderLocatorRange(
              start = ReaderLocator.Cfi("epubcfi(/6/4!/4/2)"),
              end = ReaderLocator.Cfi("epubcfi(/6/4!/4/8)"),
          )
          val encoded = json.encodeToString(ReaderLocatorRange.serializer(), range)
          val decoded = json.decodeFromString(ReaderLocatorRange.serializer(), encoded)
          assertEquals(range, decoded)
      }

      @Test
      fun decodesServerLocatorRangeJson() {
          val decoded = json.decodeFromString(
              ReaderLocatorRange.serializer(),
              """{"start":{"type":"text","value":"42"},"end":{"type":"text","value":"58"}}""",
          )
          assertEquals(ReaderLocator.Text(42), (decoded.start))
          assertEquals(ReaderLocator.Text(58), (decoded.end))
      }

      @Test
      fun roundTripsPageRectForFixedLayout() {
          val range = ReaderLocatorRange(
              start = ReaderLocator.PageRect(page = 3, x = 0.1, y = 0.2, width = 0.4, height = 0.05),
              end = ReaderLocator.PageRect(page = 3, x = 0.1, y = 0.2, width = 0.4, height = 0.05),
          )
          val decoded = json.decodeFromString(
              ReaderLocatorRange.serializer(),
              json.encodeToString(ReaderLocatorRange.serializer(), range),
          )
          assertEquals(range, decoded)
      }
  }
  ```
- [ ] Run (expect FAIL/no-compile):
  ```
  ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.reader.ReaderLocatorSerializationTest"
  ```

### Task 6 — Add the `ReaderLocator` range model

**Files:**
- `shared/src/commonMain/kotlin/com/continuum/app/model/reader/ReaderLocator.kt` (NEW)

> **Phase 1 reconciliation:** If Phase 1's `ReaderLocator` already exists in `shared/.../model/reader/` when this task runs, do NOT create a second type — instead add the `ReaderLocatorRange` wrapper and any missing `ReaderLocator` variants to the existing file, and keep the `@SerialName("type")` discriminator values (`cfi`, `text`, `page_rect`, `page`) consistent with what Phase 1 chose. The variants below are the minimum Phase 4 requires.

- [ ] Create the file with a polymorphic, discriminator-tagged locator and a range wrapper. The `type` discriminator strings MUST match the server `locator_range` JSON used in Task 2/3 tests (`cfi`, `text`, `page_rect`, `page`):
  ```kotlin
  package com.continuum.app.model.reader

  import kotlinx.serialization.SerialName
  import kotlinx.serialization.Serializable
  import kotlinx.serialization.json.JsonClassDiscriminator

  /**
   * A single typed reading position. Every format maps to and from one of
   * these. EPUB / FB2 / TXT / MD use [Cfi] (HTML-harness equivalent) or
   * [Text] character offsets; fixed-layout PDF uses [PageRect]; comics use
   * [Page]. Serializes with a "type" discriminator so the server's
   * locator_range JSON and the client agree on shape.
   */
  @Serializable
  @JsonClassDiscriminator("type")
  sealed interface ReaderLocator {
      @Serializable
      @SerialName("cfi")
      data class Cfi(val value: String) : ReaderLocator

      @Serializable
      @SerialName("text")
      data class Text(val value: Int) : ReaderLocator

      @Serializable
      @SerialName("page_rect")
      data class PageRect(
          val page: Int,
          val x: Double,
          val y: Double,
          val width: Double,
          val height: Double,
      ) : ReaderLocator

      @Serializable
      @SerialName("page")
      data class Page(val value: Int) : ReaderLocator
  }

  /** A start..end span used to anchor highlights/notes. */
  @Serializable
  data class ReaderLocatorRange(
      val start: ReaderLocator,
      val end: ReaderLocator,
  )
  ```
  > Note: `Json` config for ebook models already sets `ignoreUnknownKeys`/`isLenient`; the default class discriminator key is `type`, matching `@JsonClassDiscriminator("type")`. Confirm the shared `ContinuumJson` instance does not set a conflicting `classDiscriminator`.
- [ ] Run the Task 5 tests green:
  ```
  ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.reader.ReaderLocatorSerializationTest"
  ```

### Task 7 — TDD: annotation ↔ locator-range mapping tests

**Files:**
- `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookAnnotationSerializationTest.kt` (NEW)

Write FIRST (fail until Task 8).

- [ ] Cover: (a) decoding a server highlight with `locator_range`; (b) decoding a legacy highlight with `cfi_range` only and `locator_range` absent; (c) encoding a `SaveEbookAnnotationRequest` highlight built from a `ReaderLocatorRange`:
  ```kotlin
  package com.continuum.app.model.ebook

  import com.continuum.app.model.reader.ReaderLocator
  import com.continuum.app.model.reader.ReaderLocatorRange
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertNull
  import kotlinx.serialization.json.Json

  class EbookAnnotationSerializationTest {
      private val json = Json {
          ignoreUnknownKeys = true
          isLenient = true
          explicitNulls = false
          coerceInputValues = true
      }

      @Test
      fun decodesHighlightWithLocatorRange() {
          val a = json.decodeFromString<EbookAnnotation>(
              """
              {"id":"a1","content_id":"c1","kind":"highlight","color":"#facc15",
               "locator_range":{"start":{"type":"cfi","value":"epubcfi(/6/4!/4/2)"},
                                "end":{"type":"cfi","value":"epubcfi(/6/4!/4/8)"}}}
              """.trimIndent(),
          )
          assertEquals(
              ReaderLocatorRange(
                  ReaderLocator.Cfi("epubcfi(/6/4!/4/2)"),
                  ReaderLocator.Cfi("epubcfi(/6/4!/4/8)"),
              ),
              a.locatorRange,
          )
      }

      @Test
      fun decodesLegacyCfiHighlightWithoutLocatorRange() {
          val a = json.decodeFromString<EbookAnnotation>(
              """{"id":"a2","content_id":"c1","kind":"highlight","cfi_range":"epubcfi(/6/4!/4/2,/4/8)"}""",
          )
          assertNull(a.locatorRange)
          assertEquals("epubcfi(/6/4!/4/2,/4/8)", a.cfiRange)
      }

      @Test
      fun encodesHighlightRequestFromRange() {
          val req = highlightSaveRequest(
              range = ReaderLocatorRange(
                  ReaderLocator.Text(10),
                  ReaderLocator.Text(20),
              ),
              selectedText = "hello",
              note = "a note",
              color = "#22c55e",
          )
          val encoded = json.encodeToString(SaveEbookAnnotationRequest.serializer(), req)
          // Round-trips back to the same request.
          assertEquals(req, json.decodeFromString(SaveEbookAnnotationRequest.serializer(), encoded))
          assertEquals("highlight", req.kind)
      }
  }
  ```
- [ ] Run (expect FAIL):
  ```
  ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.ebook.EbookAnnotationSerializationTest"
  ```

### Task 8 — Extend `EbookReaderModels` + repository for highlights

**Files:**
- `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookReaderModels.kt` (EDIT)
- `shared/src/commonMain/kotlin/com/continuum/app/repository/EbookReaderRepository.kt` (EDIT)

- [ ] In `EbookReaderModels.kt`, import `ReaderLocatorRange` and add the field to `EbookAnnotation` (after `location`):
  ```kotlin
  @SerialName("locator_range") val locatorRange: ReaderLocatorRange? = null,
  ```
- [ ] Add the same field to `SaveEbookAnnotationRequest` (after `location`):
  ```kotlin
  @SerialName("locator_range") val locatorRange: ReaderLocatorRange? = null,
  ```
- [ ] Add a `highlightSaveRequest` factory (mirrors `localBookmarkAnnotation`'s style; used by tests + controller):
  ```kotlin
  fun highlightSaveRequest(
      range: ReaderLocatorRange,
      selectedText: String? = null,
      note: String? = null,
      color: String? = null,
      cfiRange: String? = null,
  ): SaveEbookAnnotationRequest =
      SaveEbookAnnotationRequest(
          kind = if (note.isNullOrBlank()) "highlight" else "note",
          cfiRange = cfiRange,
          locatorRange = range,
          selectedText = selectedText,
          note = note,
          color = color,
      )
  ```
- [ ] In `EbookReaderRepository.kt`, add convenience methods:
  ```kotlin
  suspend fun createHighlight(contentId: String, request: SaveEbookAnnotationRequest) =
      api.createAnnotation(contentId, request)

  suspend fun updateHighlight(contentId: String, annotationId: String, request: SaveEbookAnnotationRequest) =
      api.updateAnnotation(contentId, annotationId, request)
  ```
  (`listAnnotations`/`deleteAnnotation` already exist; reuse them. The generic `createAnnotation` is already exposed via `EbookReaderApi`.)
- [ ] Run Task 7 tests green + the existing ebook model suite:
  ```
  ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.ebook.*"
  ```

### Task 9 — Shared highlight color palette

**Files:**
- `shared/src/commonMain/kotlin/com/continuum/app/common/ebook/HighlightColor.kt` (NEW)

- [ ] Define the palette in `shared` (so Apple/TV adopt the same hex set; matches the server default `#facc15`):
  ```kotlin
  package com.continuum.app.common.ebook

  /** Highlight palette shared across platforms. Hex strings match what the
   *  server stores in EbookReaderAnnotation.color (default #facc15). */
  enum class HighlightColor(val hex: String, val label: String) {
      Yellow("#facc15", "Yellow"),
      Green("#22c55e", "Green"),
      Blue("#3b82f6", "Blue"),
      Pink("#ec4899", "Pink"),
      Orange("#f97316", "Orange");

      companion object {
          val Default = Yellow
          fun fromHex(hex: String?): HighlightColor =
              entries.firstOrNull { it.hex.equals(hex, ignoreCase = true) } ?: Default
      }
  }
  ```
- [ ] Compile shared:
  ```
  ./gradlew :shared:compileDebugKotlinAndroid
  ```

### Task 10 — `AnnotationController`: highlight state, sync, client-local fallback

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/AnnotationController.kt` (NEW)

The controller owns highlight CRUD with optimistic local state and graceful fallback: when sync fails OR the format has no CFI and the server rejects `locator_range` (older server), it keeps the highlight in the client-local store (reusing `EbookLocalStateStore` scope) and surfaces a non-blocking sync notice. Mirror `ReaderViewModel.addBookmark/deleteBookmark` optimistic patterns.

- [ ] Implement:
  ```kotlin
  package com.continuum.app.android.ui.screens.reader

  import com.continuum.app.model.ebook.EbookAnnotation
  import com.continuum.app.model.ebook.SaveEbookAnnotationRequest
  import com.continuum.app.model.ebook.highlightSaveRequest
  import com.continuum.app.model.reader.ReaderLocatorRange
  import com.continuum.app.network.ApiResult
  import com.continuum.app.repository.EbookReaderRepository

  /** Result of a highlight mutation for the VM to fold into UI state. */
  sealed interface HighlightSyncResult {
      data class Synced(val annotation: EbookAnnotation) : HighlightSyncResult
      /** Persisted client-side only (no server, or sync failed). */
      data class LocalOnly(val annotation: EbookAnnotation, val reason: String) : HighlightSyncResult
  }

  class AnnotationController(
      private val repository: EbookReaderRepository,
  ) {
      suspend fun create(
          contentId: String,
          localId: String,
          range: ReaderLocatorRange,
          selectedText: String?,
          note: String?,
          color: String,
          cfiRange: String?,
      ): HighlightSyncResult {
          val request = highlightSaveRequest(
              range = range,
              selectedText = selectedText,
              note = note,
              color = color,
              cfiRange = cfiRange,
          )
          return when (val r = repository.createHighlight(contentId, request)) {
              is ApiResult.Success -> HighlightSyncResult.Synced(r.data)
              else -> HighlightSyncResult.LocalOnly(
                  annotation = localHighlight(localId, contentId, range, selectedText, note, color, cfiRange),
                  reason = "Highlight saved on this device only.",
              )
          }
      }

      suspend fun update(
          contentId: String,
          annotationId: String,
          range: ReaderLocatorRange,
          selectedText: String?,
          note: String?,
          color: String,
          cfiRange: String?,
      ): HighlightSyncResult {
          val request = highlightSaveRequest(range, selectedText, note, color, cfiRange)
          return when (val r = repository.updateHighlight(contentId, annotationId, request)) {
              is ApiResult.Success -> HighlightSyncResult.Synced(r.data)
              else -> HighlightSyncResult.LocalOnly(
                  annotation = localHighlight(annotationId, contentId, range, selectedText, note, color, cfiRange),
                  reason = "Highlight edit saved on this device only.",
              )
          }
      }

      /** Returns true when the delete reached the server (or the id was local). */
      suspend fun delete(contentId: String, annotation: EbookAnnotation): Boolean {
          if (annotation.id.startsWith("local-")) return true
          return when (repository.deleteAnnotation(contentId, annotation.id)) {
              is ApiResult.Success -> true
              else -> false
          }
      }

      private fun localHighlight(
          id: String,
          contentId: String,
          range: ReaderLocatorRange,
          selectedText: String?,
          note: String?,
          color: String,
          cfiRange: String?,
      ): EbookAnnotation = EbookAnnotation(
          id = id,
          contentId = contentId,
          kind = if (note.isNullOrBlank()) "highlight" else "note",
          cfiRange = cfiRange,
          locatorRange = range,
          selectedText = selectedText,
          note = note,
          color = color,
          style = "highlight",
      )
  }
  ```
- [ ] Decide local-id scheme: prefix `local-` so `delete`/sync reconciliation matches the existing bookmark convention (`ReaderViewModel` already keys on `id.startsWith("local-")`).
- [ ] Compile:
  ```
  ./gradlew :androidApp:compileDebugKotlin
  ```

### Task 11 — Wire highlights into `ReaderViewModel`

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt` (EDIT)

- [ ] Add to `ReaderUiState`:
  ```kotlin
  val highlights: List<EbookAnnotation> = emptyList(),
  val activeSelection: ReaderSelection? = null,
  ```
  and define `ReaderSelection`:
  ```kotlin
  data class ReaderSelection(
      val range: ReaderLocatorRange,
      val selectedText: String,
      val cfiRange: String? = null,
  )
  ```
- [ ] Construct `AnnotationController(ebookReaderRepository)` in the VM.
- [ ] In `loadReaderState`, after the existing `listAnnotations` call, split items by kind: feed `kind == "bookmark"` into `bookmarks` (unchanged) and `kind in ("highlight","note")` into a new `highlights` field on `InitialReaderState`; merge into UI state in `loadDetail`/`loadOfflineOnly` the same way bookmarks are merged. (One list call already exists — reuse its result; do NOT add a second network round-trip.)
- [ ] Add VM API:
  ```kotlin
  fun onSelectionChanged(selection: ReaderSelection?) { _uiState.update { it.copy(activeSelection = it.activeSelection.takeIf { selection == null } ?: selection).copy(activeSelection = selection) } }

  fun addHighlight(color: HighlightColor, note: String?) {
      val selection = _uiState.value.activeSelection ?: return
      val localId = "local-" + System.currentTimeMillis()
      val optimistic = /* build local EbookAnnotation from selection */ ...
      _uiState.update { it.copy(highlights = it.highlights + optimistic, activeSelection = null) }
      viewModelScope.launch {
          when (val result = annotationController.create(
              contentId, localId, selection.range, selection.selectedText, note, color.hex, selection.cfiRange,
          )) {
              is HighlightSyncResult.Synced -> _uiState.update { st ->
                  st.copy(highlights = st.highlights.map { if (it.id == localId) result.annotation else it }, syncError = null)
              }
              is HighlightSyncResult.LocalOnly -> _uiState.update { st ->
                  st.copy(highlights = st.highlights.map { if (it.id == localId) result.annotation else it }, syncError = result.reason)
              }
          }
      }
  }

  fun editHighlight(annotation: EbookAnnotation, color: HighlightColor, note: String?) { /* annotationController.update; replace by id */ }

  fun deleteHighlight(annotation: EbookAnnotation) {
      _uiState.update { it.copy(highlights = it.highlights.filterNot { h -> h.id == annotation.id }) }
      viewModelScope.launch {
          if (!annotationController.delete(contentId, annotation)) {
              _uiState.update { it.copy(syncError = "Highlight delete could not sync.") }
          }
      }
  }

  fun jumpToHighlight(annotation: EbookAnnotation) { /* resolve range.start to a page/locator and reuse jumpToPage or a new jumpToLocator hook */ }
  ```
  (Simplify `onSelectionChanged` to a plain `_uiState.update { it.copy(activeSelection = selection) }` — the nested copy above is illustrative noise; use the single-copy form.)
- [ ] For `jumpToHighlight`, derive a page from `ReaderLocatorRange.start`: for `ReaderLocator.Page`/`PageRect` use the page; for `Cfi`/`Text` reuse the existing `"page:N"` fallback via the format reader's locator→page mapping if available, else no-op with a toast. Keep it minimal — Phase 5 (search) deepens locator navigation.
- [ ] Compile + run existing reader VM/unit tests:
  ```
  ./gradlew :androidApp:compileDebugKotlin
  ./gradlew :androidApp:testDebugUnitTest
  ```

### Task 12 — Selection toolbar, Highlights sheet, editor (UI)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt` (EDIT)

- [ ] Add a Highlights toolbar `IconButton` (use `Icons.Default.FormatColorFill` or `Icons.Default.Highlight`) next to the existing Bookmarks button, enabled when `state.capabilities.supportsBookmarks` (highlights track the same reflowable-capability gate; for now reuse it and refine via `ReaderCapabilities` if a dedicated flag is added).
- [ ] Add `var showHighlights by remember { mutableStateOf(false) }` and a `HighlightsSheet` composable mirroring `BookmarkSheet`:
  - List `state.highlights` (key by `id`).
  - `headlineContent` = `selectedText` truncated; `supportingContent` = `note` or color label.
  - Tap → `viewModel.jumpToHighlight(it); showHighlights = false`.
  - Trailing edit + delete icons → open editor dialog / `viewModel.deleteHighlight(it)`.
- [ ] Add a selection toolbar: when `state.activeSelection != null`, show a small surface (anchored bottom or as a `Popup`) with the `HighlightColor` swatches and a "Note" action. Color tap → `viewModel.addHighlight(color, note = null)`. "Note" → open the editor dialog pre-filled from the selection.
- [ ] Add a `HighlightEditorDialog` (`AlertDialog`) with a color row (HighlightColor swatches) + a note `TextField`; confirm calls `addHighlight`/`editHighlight`. Dismiss clears `activeSelection` via `viewModel.onSelectionChanged(null)`.
- [ ] Compile:
  ```
  ./gradlew :androidApp:compileDebugKotlin
  ```

### Task 13 — Text/FB2 selection + highlight span rendering

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/TextReader.kt` (EDIT)
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/FictionBookReader.kt` (EDIT)

- [ ] For text/FB2 (Compose `Text`/`SelectionContainer`), capture selection as character offsets and emit `ReaderSelection(range = ReaderLocatorRange(ReaderLocator.Text(start), ReaderLocator.Text(end)), selectedText = ...)` via a new `onSelection: (ReaderSelection?) -> Unit` callback added to each reader's signature and wired in `ReaderScreen` to `viewModel::onSelectionChanged`.
- [ ] Render existing highlights as background spans: build an `AnnotatedString` applying `SpanStyle(background = Color(highlight.color.toColorInt()))` over `[Text.start, Text.end)` for each highlight whose `locatorRange.start`/`end` are `ReaderLocator.Text`. Pass `highlights` into the text/FB2 readers.
- [ ] Keep behavior unchanged when there are no highlights (empty list → plain text).
- [ ] Compile:
  ```
  ./gradlew :androidApp:compileDebugKotlin
  ```

### Task 14 — EPUB selection + overlay via epub.js bridge (PHASE 2 BRIDGE)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt` (EDIT)

> The current `EpubReader` is the pre-Phase-2 chapter-WebView (`javaScriptEnabled = false`). These steps target the Phase 2 epub.js bridge. Where that bridge is absent, gate behind `// PHASE 2 BRIDGE` and rely on the Task 13 path + client-local fallback so the feature still ships for text/FB2.

- [ ] Add an `@JavascriptInterface` callback receiving `onTextSelected(cfiRange: String, text: String)` from epub.js `rendition.on("selected", ...)`; map to `ReaderSelection(range = ReaderLocatorRange(ReaderLocator.Cfi(startCfi), ReaderLocator.Cfi(endCfi)), selectedText = text, cfiRange = cfiRange)` and forward via the `onSelection` callback. (epub.js gives a single `cfiRange`; split into start/end via `EpubCFI` or store the same CFI on both ends and keep the combined string in `cfiRange` for legacy compatibility.)
- [ ] On highlight create/load, inject `rendition.annotations.highlight(cfiRange, {}, cb, "hl", { fill: color })` for each EPUB highlight via `evaluateJavascript`; remove on delete.
- [ ] Pass `highlights` (filtered to those with a CFI anchor) + `onSelection` into `EpubReader` from `ReaderScreen`.
- [ ] Compile:
  ```
  ./gradlew :androidApp:compileDebugKotlin
  ```

### Task 15 — Full client build + on-device manual verification

**Files:** none (build + adb verify)

- [ ] Full client checks:
  ```
  ./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest
  ./gradlew :androidApp:assembleDebug
  ```
- [ ] Install on a device/emulator:
  ```
  ./gradlew :androidApp:installDebug
  adb shell am start -n <applicationId>/.MainActivity
  ```
  (Resolve `<applicationId>` from `androidApp/build.gradle.kts` `applicationId`.)
- [ ] Manual verify (spec §10), capture results:
  - Open a TXT/Markdown book → select text → color swatch appears → pick yellow → span renders → reopen book → highlight persists (synced row visible via `GET /api/v1/ebooks/{id}/annotations`).
  - Add a note via the editor → Highlights sheet lists it with the note → edit color → tap to jump → delete → row gone on server.
  - FB2 book → same select/highlight/persist flow.
  - EPUB (if Phase 2 bridge present) → select → highlight overlay via epub.js → reopen persists.
  - Cross-device sync: highlight on device A → appears on device B after reopen.
  - Offline / old-server fallback: with airplane mode (or pre-Task-4 server), create a highlight → "saved on this device only" notice → it stays in the Highlights sheet; on reconnect a fresh highlight syncs.
  - adb logcat sanity for sync errors:
    ```
    adb logcat | grep -i "annotation\|highlight"
    ```

---

## Self-review vs spec (fix inline before merge)

- [ ] §6 server: payloads carry typed `ReaderLocator` range JSON (`locator_range`), `cfi_range` still accepted, progress `location` unchanged (still accepts `"page:N"` — out of Phase 4 scope, untouched), Goose migration timestamped via `make migrate-create`, no destructive change to existing rows. → Tasks 1, 3. ✅
- [ ] §5 highlights: selection toolbar with color picker + optional note (Task 12); overlays EPUB via epub.js injection (Task 14) and text/FB2 via spans (Task 13); Highlights sheet list/jump/edit/delete (Task 12); sync via updated `EbookReaderApi` with `ReaderLocator` ranges (Tasks 8, 10, 11). ✅
- [ ] §9 risk — server lands first; client-local fallback (no sync) behind the same UI until then. → Task 10 `HighlightSyncResult.LocalOnly`, Task 15 fallback verify. ✅
- [ ] §9 assumption — models in `shared` so Apple/TV adopt: `ReaderLocator`, `ReaderLocatorRange`, `HighlightColor`, `EbookAnnotation.locatorRange` all in `shared/commonMain`. ✅
- [ ] Tests: Go table-driven model/handler + migration up/down safety (Tasks 1–3); Kotlin locator-range serialization + annotation mapping (Tasks 5, 7). ✅
- [ ] Phase-1 drift guard: Task 6 reconciliation note prevents a duplicate `ReaderLocator`. ✅

## Deferred (NOT in Phase 4)

- PDF/comic highlight overlays (page + rect) — `ReaderLocator.PageRect`/`Page` variants are defined and serialize, but PDF/comic overlay rendering + selection are Phase 7 polish.
- In-text search (Phase 5) — deeper locator→navigation; `jumpToHighlight` here is intentionally minimal.
- Progress `location` migration to typed locator (Phase 1) — untouched; this phase only generalizes the *annotation* anchor.
- Adaptive WPM / reading-time (Phase 6).
