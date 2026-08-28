---
name: code-review
description: >-
  Review Prairie Smart TV (Tizen/webOS) diffs for playback/API regressions and parity gaps. Use when reviewing Smart TV PRs.
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

- Shipping secrets or vendor signing material
- Breaking Tizen/webOS packaging requirements
- Preferring unsupported streaming shortcuts that violate org non-goals

## After the review

Fix confirmed Critical/Important findings (or rebut in the PR) before merge.
Cap fix→re-review loops at 2–3. Do not gold-plate Suggestions.
