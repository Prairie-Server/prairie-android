# Bugbot — Silo Plugin SDK

Project rules for Cursor Bugbot / Agent Review on this repository.

## Contracts

- Treat protobuf and public Go surfaces as multi-repo contracts (host + every plugin).
- Prefer additive RPC/field changes; breaking changes require coordinated plugin and host updates.

## Review focus

Prioritize correctness bugs, security issues, data loss, and contract/product regressions over style. Cite file paths. Skip speculative nits and drive-by refactors.
