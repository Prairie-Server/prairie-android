# Research notes: prairie-server PR #138 (manga) — Android client implications

**Date:** 2026-06-12. Audited at `origin/codex/rebase-pr-137` (82 files, +5284/−349) vs `origin/main`. `manga` does not exist anywhere on current main — all of this is new surface. Feeds the future manga sub-project; no client work has started.

## Server model

- New library type string `"manga"` and new item type string `"manga"` (a **series container**, like a TV series — never directly readable). Each chapter file (`.cbz`/`.epub`) is a plain `type="ebook"` item linked via a `manga_chapters` join table.
- Chapter items are hidden from all listing surfaces (browse/sections/search) server-side; only series cards appear. Chapters remain fetchable by id.
- Browse items gain optional `manga_chapter_count` / `manga_volume_count` fields (nil for non-manga).
- `media_scope: "manga"` added to query definitions/sections/smart collections.

## Endpoints

- One new endpoint: `GET /catalog/items/{id}/manga-files` → file listing for a "View Details" dialog (paths permission-stripped).
- Extended: `GET /catalog/items/{id}` for a manga series carries `manga: { chapters: MangaChapter[] }` — `{content_id, title, chapter_index?: float, volume?: string, read: bool, progress?: 0..1, poster_url?}`, ordered `chapter_index NULLS LAST, sort_title`, read state derived from existing ebook progress.
- Chapter item detail now populates `series_id`/`series_title` with the owning manga series (back-navigation + continue-reading collapse).
- **Progress is the existing ebook API unchanged** (`GET/PUT /ebooks/{content_id}/progress`, fraction 0..1, per chapter). `read = progress >= finished-threshold`. Mark read/unread reuses the watched-state mutation with `type:"ebook"` per chapter (series-level with `type:"manga"`).
- Status badge is **publication status, not user reading status**: reuses `show_status` with values `Ongoing|Completed|Hiatus|Cancelled|Upcoming` (sourced via the external `prairie-plugin-manga-metadata` plugin; requires plugin-sdk v0.7.0 — treat as optional).

## Web reference UX

- Series page `MangaContent.tsx`: hero + `N Volumes / N Chapters / <status>` badges; single Continue/Start Reading/Read Again CTA (first chapter with `read !== true`); chapter list with per-row poster, read checkmark, progress bar, mark-read toggle, per-chapter download; files dialog.
- Volume bucketing rules in `web/src/lib/mangaChapters.ts` (canonicalize "v01"/"01"/"1" → Volume 1; single-file volumes flat; multi-chapter volumes collapsible).
- Reader: the existing ebook reader with a `backTo` param (back goes to series), "Next chapter" pill, end-of-book overlay at progress ≥ 0.995; comic mode hides prose chrome for cbz/cbr; RTL is the pre-existing toggle; **no long-strip mode added**.
- Cards: top-left color-coded status chip (Ongoing=emerald, Completed=sky, Hiatus=amber, Cancelled=red, Upcoming=violet) + top-right "12 Vol · 3 Ch" chip.

## Android risk / breakage

- Additive PR: no renamed fields, no changed type strings, no removed endpoints.
- **Taxonomy collision:** the Android reading taxonomy treats `manga` as ebook-like; server `manga` = non-readable series container. Android must route manga cards to a detail screen, never a reader, and must not assume `versions`/files on the series item (downloads are per chapter).
- `type: "manga"` will appear in browse/sections/search the moment a manga library exists — our models use plain strings (no closed enums), so parsing is safe, but UI dispatch must handle it.
- Continue Reading rows will contain `type="ebook"` chapter cards with `series_id` populated — Reading hub should show series art/title and navigate to the series page.

## Android work items (ordered)

Must-have: (1) tolerate/dispatch `"manga"` item type everywhere; (2) parse `manga.chapters` + manga series detail screen with volume grouping + Continue/Start Reading CTA; (3) open chapters in the existing reader via chapter `content_id` + existing progress API; (4) back-to-series + next-chapter loop; (5) Reading-hub chapter-card collapse to series.
Should-have: (6) volume/chapter count chips + publication-status badge; (7) per-chapter mark read/unread + progress bars; (8) comic-mode reader polish for cbz chapters.
Polish: (9) manga-files dialog + per-chapter downloads; (10) manga-aware sort/filter options. TV: parsing tolerance only (no reading on TV).
