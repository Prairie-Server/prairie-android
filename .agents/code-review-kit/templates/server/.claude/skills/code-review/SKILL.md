---
name: code-review
description: >-
  Review Silo Server (Go backend + React web) branch diffs for correctness, migration safety, API contract risk, and multi-repo client impact. Use when reviewing PRs, local changes, or before merge.
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

- Irreversible migration mistakes (`goose fix`, paired up/down SQL, renumbering legacy versions, renaming encrypted `server_settings` keys)
- Introducing Live TV / IPTV / `.strm` remote-URL playback (permanent non-goal)
- Shipping a client-visible API break without Apple/Android coordination and pre-lock removals table updates when required
- Committing `.silo-dev.env`, secrets, or local absolute filesystem paths in docs

## After the review

Fix confirmed Critical/Important findings (or rebut in the PR) before merge.
Cap fix→re-review loops at 2–3. Do not gold-plate Suggestions.
