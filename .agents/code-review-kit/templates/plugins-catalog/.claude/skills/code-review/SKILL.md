---
name: code-review
description: >-
  Review silo-plugins catalog/manifest diffs for broken release metadata and security issues. Use when reviewing catalog PRs.
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

- Publishing manifest entries that point at missing/wrong release assets
- Injecting untrusted plugin sources without review
- Committing secrets

## After the review

Fix confirmed Critical/Important findings (or rebut in the PR) before merge.
Cap fix→re-review loops at 2–3. Do not gold-plate Suggestions.
