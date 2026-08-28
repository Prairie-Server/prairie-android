# Bugbot — Silo Server

Project rules for Cursor Bugbot / Agent Review on this repository.

## Absolute hazards

- Goose migrations: timestamped via `make migrate-create`; never `goose fix`, paired up/down files, or renumbering legacy versions.
- Encrypted `server_settings` values are GCM-bound to key names — renames brick decryption.
- Permanent non-goals: Live TV, OTA/DVB, IPTV, EPG/XMLTV, DVR, `.strm` remote-URL shortcuts — in core, plugins, or clients.

## API and clients

- Pre-1.0 `/api/v1` breaks need Apple + Android coordination and removals tracking.
- Prefer capability endpoints for new features.
- Consider jellycompat when user-facing catalog/playback behavior changes.

## Hygiene

- No secrets or `.silo-dev.env` in commits. No local absolute paths in docs.

## Review focus

Prioritize correctness bugs, security issues, data loss, and contract/product regressions over style. Cite file paths. Skip speculative nits and drive-by refactors.
