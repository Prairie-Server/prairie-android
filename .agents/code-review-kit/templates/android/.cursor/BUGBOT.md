# Bugbot — Silo Android

Project rules for Cursor Bugbot / Agent Review on this repository.

## Product exposure

- Ebooks / Reading: phone only. Never surface Reading on Android TV.
- Mobile bottom nav: Home, Libraries, For You, Calendar, Downloads (when the active profile has downloads). Video/Audio/Reading are library modes — not bottom tabs.
- TV nav: Home, media-type tabs from server libraries, For You, Calendar, search, profile. No Reading/ebooks.
- Requests: only when server `requests_enabled` is true.
- Do **not** expose admin STATS, richer admin screens, Watch Together, or session-revoke UX without an explicit product decision.

## Identity

- `applicationId` is `org.siloserver.silo`. Do not reintroduce legacy package IDs, storage names, or old-brand symbols.

## Playback and offline

- Prefer Media3 shared-session patterns in `android-shared`. Flag wrong-item progress writes, replan loops, and capability claims that do not match probes.
- Downloads / offline playback are phone-only; TV is streaming-only.

## Review focus

Prioritize correctness bugs, security issues, data loss, and contract/product regressions over style. Cite file paths. Skip speculative nits and drive-by refactors.
