---
name: code-review
description: >-
  Review Silo Apple (iOS/tvOS/macOS) branch diffs for bugs, regressions, and client/server contract issues. Use when reviewing PRs, local changes, or before merge.
---

# Code review

Review the current branch (or stated files) against the base branch. Prefer the
`code-reviewer` subagent for an isolated pass; this skill is the in-session playbook.

## Protocol

Follow `.agents/code-review-kit/shared/REVIEW_PROTOCOL.md` for severity, report format, and anti-overflag rules.
If this repo does not yet vendor the kit, use the same protocol embedded in
`.cursor/agents/code-reviewer.md`.

## Steps

1. `git status` and `git diff --stat origin/main…HEAD` (or the repo default base).
2. Read every behavior-changing hunk.
3. Apply the checklist in `.cursor/agents/code-reviewer.md`.
4. Confirm each finding against the cited lines; drop phantoms.
5. Emit the protocol report (`approve` or `needs-attention`).

## Hard stops (Critical if violated)

- Breaking auth/session token storage or Keychain identity without migration
- Shipping Live TV / IPTV / `.strm` remote-URL playback (org non-goal)
- Leaving Android behind on a client-visible API behavior without filing or coordinating follow-up
- Committing signing material, provisioning profiles, or secrets

## After the review

Fix confirmed Critical/Important findings (or rebut in the PR) before merge.
Cap fix→re-review loops at 2–3. Do not gold-plate Suggestions.
