# Four-client feature comparison — iOS / tvOS / Android mobile / Android TV (2026-06-17)

Feature parity across all four Prairie clients. Inventories: Apple (iOS + tvOS) via Codex reads of `prairie-apple`; Android (mobile + TV) from the TV parity review + screen maps. UI/visual styling is a *separate* axis (see the tvOS-UI-match sizing work) — this doc is **features only**.

✅ Full · 🟡 Partial · ❌ Absent

| Feature | iOS | tvOS | Android mobile | Android TV |
|---|:--:|:--:|:--:|:--:|
| Password login | ✅ | ✅ | ✅ | ✅ |
| QR / device login | ❌ | ✅ | — | ✅ |
| First-time server setup / signup | 🟡 (browser) | 🟡 (placeholder) | ✅ | ❌ |
| Companion / cast pairing | ✅ | ✅ | — | — |
| Profiles: select / PIN | ✅ | ✅ | ✅ | ✅ |
| Profiles: create / edit / delete | 🟡 (edit TODO) | ❌ | ✅ | ❌ (select/PIN only) |
| Home | ✅ | ✅ | ✅ | ✅ |
| Global Browse (+content-rating filters) | 🟡 | 🟡 | ✅ | ❌ |
| Libraries | ✅ | 🟡 | ✅ | ✅ |
| Search | ✅ (All/Movie/Series) | 🟡 (media-type only) | ✅ (library-derived modes) | 🟡 (hard-coded modes) |
| Recommendations | ✅ (tab) | 🟡 (no root tab) | ✅ | ✅ |
| Calendar (full week UI) | ✅ | ✅ | ✅ | ❌ (home "upcoming" row only) |
| Detail (play / version / favorite / watchlist / watched / resume) | ✅ | ✅ | ✅ | ✅ |
| Cast → Person detail nav | ✅ | ✅ | ✅ | ❌ (cards are no-op) |
| Collections: view | ✅ | ✅ | ✅ | ✅ |
| Collections: edit / rename / delete / remove-item | 🟡 | 🟡 | ✅ | ❌ (read-only) |
| Requests | ❌ | ❌ | ✅ | 🟡 (bypasses shared flow) |
| Notifications / Inbox | ❌ | ❌ | ✅ | ✅ |
| Watch Together | ❌ | ❌ | ✅ | ✅ |
| Video player core (tracks, chapters, intro-skip, HDR, speed, sleep, resume, retry, transcode) | ✅ | ✅ | ✅ | ✅ (resume has an exit-save regression) |
| Subtitle search / download | ❌ | ❌ | ✅ | ✅ |
| AI subtitle translation | ❌ | ❌ | ✅ | ✅ |
| Audiobook player | ✅ | ✅ | ✅ | ✅ |
| Ebook reader (epub / pdf / comic / fb2) | ❌ | ❌ | ✅ | ❌ |
| Kindle → EPUB conversion (server, just built) | ❌ | ❌ | ✅ | ❌ |
| Downloads / offline | ❌ | ❌ | ✅ | ❌ (intentional — TV always online) |
| Settings: subtitle prefs incl. forced subtitles | ✅ | ✅ | ✅ | 🟡 (no forced-subtitle toggle) |
| Settings: playback prefs | ✅ | ✅ | ✅ | ✅ |
| Personal lists (Favorites / Watchlist / History) | ✅ | ✅ | ✅ | ✅ |
| Multi-server switching | ✅ | ✅ | ✅ | ✅ |
| Admin: stats dashboard | 🟡 | 🟡 | ✅ | 🟡 |
| Admin: users / sessions / logs / scans | ❌ | ❌ | ✅ | ❌ |

## Strategic read
- **Android mobile is the feature leader** — the only client with the full set.
- **Apple (iOS + tvOS) is feature-light:** both lack Requests, Notifications, Watch Together, Ebook reading, Downloads, Subtitle search, and AI subtitle translation. Polished, but narrower.
- **Copying the tvOS *UI* ≠ copying tvOS *features*.** The visual-match project must **not** shrink Android's richer feature set to match Apple. Several Android features (Requests, Notifications, Watch Together, Ebook, Downloads, Subtitle-AI) have **no Aurora design** — restyling them means *designing new Aurora-style screens*, which the pure-reskin estimate didn't include.
- **Android TV is the weakest of the four on features it *should* have:** no full Calendar, no person-detail nav, profiles select-only, collections read-only, no forced-subtitle setting, plus the player correctness regressions in `2026-06-17-tv-client-parity-review.md`. This is the "get it up to snuff" backlog (functional first; visuals later).

## Caveats
Static + targeted-read inventories; Apple cells not device-verified. Android mobile cells from prior review + screen maps. "—" = not applicable to that form factor.
