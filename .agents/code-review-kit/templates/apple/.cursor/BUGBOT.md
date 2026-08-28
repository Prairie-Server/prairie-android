# Bugbot — Silo Apple

Project rules for Cursor Bugbot / Agent Review on this repository.

## Scope

- Apple clients only (iOS, tvOS, macOS). Flag client-visible API/auth/playback changes that lack server or Android coordination notes.
- Honor org non-goals: no Live TV/IPTV/EPG/DVR/`.strm` remote URLs.

## Review focus extras

- Keychain/auth persistence and upgrade safety
- tvOS focus/playback regressions
- Divergence from documented Android parity when the change is meant to be shared behavior

## Review focus

Prioritize correctness bugs, security issues, data loss, and contract/product regressions over style. Cite file paths. Skip speculative nits and drive-by refactors.
