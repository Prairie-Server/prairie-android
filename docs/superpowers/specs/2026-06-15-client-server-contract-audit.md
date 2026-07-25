# Client ↔ Server API Contract Audit

**Date:** 2026-06-15
**Client:** prairie-android (KMP, this repo) · **Server:** prairie-server (Go/chi), `origin/main` @ `a4bee92`
**Method:** 7 parallel domain agents cross-referenced every client `network/api/*.kt` endpoint (116 paths / 158 calls) against the server's `internal/api/router.go` + handlers + DTOs. Top findings then **runtime-verified** with read-only GETs against a live latest-`main` server (logged in as a real profile).

Wire-name comparison = client `@SerialName` (kotlinx) vs server Go `json:"…"` tags. Client `Json` config is `ignoreUnknownKeys = true`, `isLenient = true`, `coerceInputValues = true`, `explicitNulls = false`, `encodeDefaults = true` — this matters: extra server fields are ignored, and **quoted numbers decode into numeric fields** (which downgrades the frame-rate finding).

## Verification legend
- **✅ runtime-confirmed** — reproduced against the live server.
- **📄 code-level** — confirmed by reading server handler/DTO; not exercised at runtime (no data / feature disabled).
- **⬇️ downgraded** — runtime showed it is not actually a problem.

---

## 🔴 High — breaks a feature

| # | Finding | Status | Fix side |
|---|---|---|---|
| 4 | **Ebook progress decode fails on unread books.** `GET /api/v1/ebooks/{id}/progress` returns **`200 {}`** when no progress exists; client `EbookReaderProgress` has non-nullable `content_id`/`file_id`/`location` → `MissingFieldException`. | ✅ runtime (`200 {}`) | **client** |
| 5 | **Favorite state never readable.** `GET /api/v1/favorites/{id}` returns **`404`, empty body** when not favorited (204 when favorited); client `checkFavorite` expects a JSON `Boolean`. | ✅ runtime (`404`, 0 bytes) | **client** |
| 6 | **Watchlist state never readable.** `GET /api/v1/watchlist/{id}` — identical `404`/no-body pattern. | ✅ runtime (`404`, 0 bytes) | **client** |
| 7 | **Rating fetch decode mismatch.** `getRating` expects `RatingEntry.mediaItemId` (required) but the rated response is `{rating, rated_at}` (no `media_item_id`); also field is `rated_at`, client reads `updated_at`. Unrated → `404`. | ✅ runtime (404 unrated); 📄 rated-shape code-level | **client** |
| 2 | **User-collection items listing.** `getCollectionItems` decodes `CatalogResponse` (needs `content_id`), but server returns `{collection_id, media_item_id, position, added_at}`. | 📄 code-level (no collections on test account) | **client** |
| 8 | **Audio-track switch restarts transcode from 0.** Server `changeAudioRequest` uses `position` as the transcode re-seek point; client sends only `audio_track_index`. | 📄 code-level | **client** |
| 3 | **Requests for audiobook/ebook rejected.** Client sends `media_type=audiobook`/`ebook`; server `MediaType` only accepts `movie`/`series`(/`tv`) → 400. | 📄 code-level (requests **disabled** server-wide → got `403 requests_disabled`) | **server** decision |
| 1 | ~~Catalog decode fails on fractional frame rates~~ | ⬇️ **downgraded — not a bug.** Runtime value is `"23.976"` (decimal **string**); `normalizeFrameRate` emits decimals and client `isLenient=true` decodes quoted numbers into `Double`. | — |

## 🟠 Med — silent data loss / wrong values

| Finding | Status | Fix side |
|---|---|---|
| **AudioTrack fields never populate.** Server emits `layout`/`default`; client `@SerialName` is `channel_layout`/`is_default`. Server has no `index` → client `index` always 0. | ✅ runtime (keys: `bit_depth,channels,codec,default,language,layout,sample_rate,title`) | client |
| **SubtitleTrack `isDefault` never populates.** Server `default`; client `is_default`. Server uses `external` (bool), client expects `external_path`. | ✅ runtime (keys: `codec,default,external,forced,hearing_impaired,index,title`) | client |
| **Catalog pagination snapshot dropped.** Client sends `?snapshot_at=`; server reads `?snapshot=`. | 📄 code-level | client |
| **New users created with no permissions.** `CreateUserRequest.permissions` is non-null `emptyList()` + `encodeDefaults=true` → always sends `permissions:[]`; server treats "present" as authoritative → bypasses `DefaultUserPermissions()`. | 📄 code-level | client |
| **Favorites/Watchlist/History list paging metadata always 0/false.** Server returns `{items}` only; client decodes `CatalogResponse` (no `total`/`has_more`). | 📄 code-level | client |
| **Collection-items paging is a no-op.** Client sends `offset`/`limit`; handler ignores them. | 📄 code-level | server/none |

## ✅ Clean (no mismatches found)
- **Auth / Profile / Device-login.**
- **Notifications / Watch-Together** (REST + WebSocket): WS paths, all realtime frame `type` discriminators, payload fields, and every REST endpoint match.

## 🟢 VideoTrack (low)
Server omits `index`/`resolution`/`hdr`/`hdr_format`/`language` (uses `pixel_format`/`profile`/`level`/`aspect_ratio`/`interlaced`/`reference_frames`); client fields stay null. Cosmetic.

---

## Fix plan (client-side, TDD)

Confirmed client-side fixes, in priority order:

1. **`EbookReaderProgress`** — make `content_id`/`file_id`/`location` nullable (or default), so `200 {}` decodes to "no progress". *(#4)*
2. **`checkFavorite` / `checkWatchlist`** — stop expecting a JSON boolean; map `2xx`→`true`, `404`→`false` (no-body). *(#5/#6)*
3. **`RatingEntry`** — make `mediaItemId` nullable; rename timestamp `@SerialName("updated_at")`→`("rated_at")`. *(#7)*
4. **`AudioTrack`** — `@SerialName("channel_layout")`→`("layout")`, `("is_default")`→`("default")`. *(med)*
5. **`SubtitleTrack`** — `@SerialName("is_default")`→`("default")`; map `external` bool (drop/repurpose `external_path`). *(med)*
6. **`choose* / catalog`** — send `snapshot` not `snapshot_at`. *(med)*
7. **`CreateUserRequest.permissions`** — make nullable / omit when empty so server applies defaults. *(med)*
8. **Collection items** — model the real `{collection_id, media_item_id, position, added_at}` shape (or a dedicated DTO) instead of `CatalogResponse`. *(#2)*
9. **`ChangeAudioRequest`** — add `position` and send current playback position. *(#8)*
10. **Favorites/Watchlist/History lists** — decode an `{items}` envelope, not `CatalogResponse`. *(med)*

**Server-side decisions (not client fixes):** #3 (support `audiobook`/`ebook` request media types — and note requests are currently disabled server-wide), and the collection-items `offset/limit` being ignored.

Each client fix gets a failing test first (model decode tests for the `{}`/field-name cases; behavior tests for the favorite/watchlist mapping) following the repo's `kotlin.test` conventions.
