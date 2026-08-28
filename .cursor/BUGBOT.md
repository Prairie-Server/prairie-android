# Bugbot — Prairie Android

Project rules for Cursor Bugbot / Agent Review on this repository.

## Product exposure

- Ebooks / Reading: phone only. Never gate or surface Reading on Android TV.
- Mobile bottom nav: Home, Libraries, For You, Calendar, Downloads (Downloads only when the active profile has downloads). Video, Audio, and Reading are library modes via Libraries — not bottom tabs.
- TV nav: Home, media-type tabs from server libraries, For You (Watchlist/Favorites), Calendar, search, profile. No Reading/ebooks.
- Requests: allow only when server `requests_enabled` is true.
- Admin: STATS dashboard for acting admins only. Do not introduce users/sessions/logs/scans admin screens or Watch Together entry points without an explicit product decision.

## Auth and upgrade identity

- `applicationId` is `org.prairieserver.prairie` (shared phone/TV Play listing).
- Encrypted prefs file `prairie_secure_tokens` must not be renamed or cleared on version bump without a migration — that wipes saved servers/tokens for every user.
- Do not reintroduce legacy package IDs, storage names, or old-brand symbols.

## Playback and offline

- Prefer Media3 shared-session patterns already in `android-shared`. Flag progress written under the wrong item, replan loops, and capability claims that do not match probes.
- Downloads / offline playback are phone-only; TV is streaming-only.

## Review focus

Prioritize correctness bugs, security/auth issues, upgrade data loss, and product-exposure regressions over style. Cite file paths. Skip speculative nits and drive-by refactors.
