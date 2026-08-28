# Bugbot — Silo Plugin

Project rules for Cursor Bugbot / Agent Review on this repository.

## SDK and CI

- No committed local `replace` or go.work hacks; CI runs `GOWORK=off` with `GOPRIVATE=github.com/Silo-Server/*`.
- Keep manifest + RPC handlers aligned with `silo-plugin-sdk`.

## Provider quality

- Exercise rate-limit/retry and failure mapping in tests.
- Do not ship credentials or leak upstream tokens in logs.

## Review focus

Prioritize correctness bugs, security issues, data loss, and contract/product regressions over style. Cite file paths. Skip speculative nits and drive-by refactors.
