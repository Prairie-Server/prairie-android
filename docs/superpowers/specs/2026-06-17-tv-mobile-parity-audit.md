# TV ↔ Mobile parity audit (2026-06-17)

Goal: bring Android **TV** to full parity with Android **mobile** (gold standard) in EVERY way except **ebooks** and **downloads**.

Method: Codex full-app pass (independent) + Claude player deep-dive, reconciled. Codex's findings cross-validated the manual player findings exactly. Full file-referenced source: Codex transcript `/tmp/codex_parity_result.md` (report section ~line 52839).

Legend: status from mobile's perspective — **MISSING** (TV lacks it), **WORSE** (TV has a lesser version), **DIFFERENT** (behaves differently). ✓ = Claude-verified.

---

## HIGH severity (whole feature/control missing or broken on TV)

### Player
- **Playback speed** — no TV player UI; VM has `onSetPlaybackSpeed` (unused). ✓
- **Quality / version switching in player** — TV video-selection callback is a no-op `TODO` (TvPlayerScreen.kt:849). ✓
- **In-player subtitle style editor** — mobile SubtitleStyleSheet (size/family/color/bg/outline/position); TV only applies stored appearance + coarse size in Settings. ✓
- **Sleep timer in video player** — VM has state/setters, no TV UI (only audiobook has it). ✓
- **Server-side audio track switching** — mobile calls session `changeAudio`; TV only switches local backend track (wrong for transcoded streams). [verify]

### Audiobook
- **Bookmarks** — add/jump/delete; TV audiobook has none.

### Detail
- **Audio track pre-selector** (before playback) — TV detail action row lacks it.
- **Subtitle track pre-selector** (incl. Off) — TV detail lacks it.

### Collections
- **Collection groups** — create/rename/delete groups; TV is a flat grid (no groups at all).

### Requests
- **Request Detail screen + route** — overview/facts/recommendations/status actions; TV has none (inline cards only).

### Admin
- **Logs** screen (App/Audit tabs, filters, search, pagination) — absent on TV.
- **Scans** screen (scan-all, per-library, cancel) — absent on TV.
- **Create user** + **Edit user** (role/enabled/library access/limits) — deferred on TV (delete only).

### Auth & Settings
- **Pair Device** — route + `prairie://device` deep links + Settings row; TV has none.
- **Manage sessions / revoke session** — mobile Account section; TV only switch-profile + sign-out.

---

## MEDIUM severity (option/behavior gap)

### Player
- **Audio + subtitle delay controls** *(user-requested fix)* — mobile = one Sync section, signed current-offset spinners, audio step 50 / subtitle step 100. TV = audio in HUD (−50/−10/reset/+10/+50), subtitle in menu (±100 with "Advance/Delay" wording). **Target: both show CURRENT signed offset, 50 ms step, on TV AND mobile.** ✓
- **Aspect/fill** — mobile Fit/Fill/Stretch; TV only Letterbox/Zoom, session-only (not persisted). ✓
- **In-player Auto-skip-intro toggle** and **Auto-play-next toggle** — TV only has global Settings toggles, not in-player.
- **Next Episode prompt** overlay — TV has none.
- **Media Info sheet** (video/audio/subtitle track details) — TV More menu only has series/season links.
- **Subtitle search / AI-translate language list** — TV uses a smaller language set than mobile's full list.
- **Version picker** — TV collapses files sharing a quality key (mobile lists every file).

### Detail
- **Direct episode play** from series/season (mobile: thumbnail plays, text opens detail) — TV Select opens detail only.
- **Series-level Watch Together** — mobile supports it on next/playable episode; TV limited to movie/episode.

### Discovery / Library
- **Home hero Play/Resume action** — TV hero only opens detail.
- **Browse sort order** (asc/desc) — absent on TV.
- **Multi-select browse filters** — TV is single genre + single rating (mobile multi). *(Note: catalog API takes a single value; true multi needs server work — see caveat below.)*
- **Default audio language** + **Default quality** in Settings — TV Playback section lacks audio-language.
- **Subtitle language choices** — TV offers fewer (Off/en/es/fr/de/ja) vs mobile's 10.
- **Theme preference** (System/Dark/Light) — TV Settings has no Appearance section.

### Collections / Requests / Admin / Servers
- **Collection** move-to-group / delete-from-grid / manual-vs-smart type / detail rename+delete — TV lacks.
- **My Requests** — TV rows clickable only when a library item exists (mobile always opens request detail).
- **Admin session "Send message"** — deferred on TV.
- **Server rename** — TV omits (select/remove only).
- **Player route preserves chosen audio/subtitle track indexes** — TV route lacks the args (ties to detail pre-selectors).

---

## LOW severity (polish / minor)
Combined audio+subtitle Tracks sheet (TV splits them) · subtitle provider warnings · WT room-indicator persistence · audiobook active-sleep-timer label + About/description · full genre tags in detail facts · Home/Library row "See All" · Browse "Release Date" vs "Release Year" naming · Browse Reset/Apply buttons · Search media-type deep-link arg + library-derived filter list · person bio scroll (6 vs 8 lines) · WT share-invite-code action · combined Favorites&Watchlist nav entry · Account email/role display · "Manage Servers" settings row · Library default sort (Title vs Recently-Added).

---

## Judgment calls — NOT real TV gaps (won't-fix / N/A)
- **Orientation lock** (player) — meaningless on TV (always landscape). Skip.
- **Share invite code via Android share sheet** — TV has no share sheet; a QR/copy affordance is the TV-appropriate equivalent (Low, optional).
- **Combined Favorites&Watchlist entry** — TV intentionally uses separate routes; nav-style choice, not a feature gap.

## Caveats
- **Multi-select browse filters**: the catalog API (`/api/v1/catalog`) takes a single `genre`/`content_rating` param, and even mobile only sends `firstOrNull()`. True multi-select needs a **server** change (out of scope; PR). TV's single-select is functionally equivalent today.

---

## Proposed fix order (phased; each Codex-reviewed + committed, no push)
1. **Player Sync controls** (user-requested, both clients): delay = signed current offset, 50 ms step. *(quick, do first)*
2. **Player HIGH**: speed control, sleep-timer UI, quality/version switching, in-player subtitle-style, server-side audio switching.
3. **Detail HIGH**: audio/subtitle pre-selectors (+ player-route track args).
4. **Admin/Requests/Auth HIGH**: Logs, Scans, user create/edit, Request Detail, Pair Device, Manage Sessions.
5. **Collections groups** + collection management actions.
6. **MED batch**: aspect options, in-player toggles, next-episode prompt, media-info, browse sort-order, settings (theme, audio-language, subtitle-language), version picker, direct-episode-play, etc.
7. **LOW batch / polish.**
