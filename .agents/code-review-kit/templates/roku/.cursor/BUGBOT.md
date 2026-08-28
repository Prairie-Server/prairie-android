# Bugbot — Prairie Roku

Project rules for Cursor Bugbot / Agent Review on this repository.

## Quality gates

- No new BrighterScript diagnostic filters/ignores for app code.
- Maintain Rooibos coverage ≥75% on `src/source/lib/**`.
- Prefer `/api/v1` over Jellyfin-primary paths.

## Review focus

Prioritize correctness bugs, security issues, data loss, and contract/product regressions over style. Cite file paths. Skip speculative nits and drive-by refactors.
