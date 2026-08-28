---
name: code-review
description: >-
  Review Silo first-party plugin branch diffs for SDK contract breaks, release/CI hazards, and provider correctness. Use when reviewing plugin PRs or before merge.
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

- Committing filesystem-local `replace` directives or `go.work` that break CI (`GOWORK=off`)
- Breaking protobuf/SDK contracts without a coordinated `silo-plugin-sdk` change
- Shipping API keys, tokens, or credentials
- Catalog release hooks that publish broken artifacts

## After the review

Fix confirmed Critical/Important findings (or rebut in the PR) before merge.
Cap fix→re-review loops at 2–3. Do not gold-plate Suggestions.
