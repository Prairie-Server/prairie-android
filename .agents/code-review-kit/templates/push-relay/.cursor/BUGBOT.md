# Bugbot — Silo Push Relay

Project rules for Cursor Bugbot / Agent Review on this repository.

## Privacy and delivery

- No notification content, user identities, raw device tokens, or server URLs in logs/storage.
- Hash device tokens before persistence. Reject unknown fields.
- Ambiguous APNs transport failures are delivery-unknown — do not blind-retry.

## Config

- Never commit `.dev.vars`, PEM/`.p8` keys, or provider secrets.

## Review focus

Prioritize correctness bugs, security issues, data loss, and contract/product regressions over style. Cite file paths. Skip speculative nits and drive-by refactors.
