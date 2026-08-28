# Bugbot — Silo Unraid Templates

Project rules for Cursor Bugbot / Agent Review on this repository.

## Templates

- No default secrets. Prefer documented env vars and volume mappings.
- Flag privileged or host-network additions unless required and documented.

## Review focus

Prioritize correctness bugs, security issues, data loss, and contract/product regressions over style. Cite file paths. Skip speculative nits and drive-by refactors.
