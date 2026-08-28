---
name: code-review
description: >-
  Review Prairie Android branch diffs for bugs, regressions, product-exposure
  violations, and auth/storage upgrade hazards. Use when reviewing PRs, local
  changes, before merge, or when asked for a Bugbot-style / adversarial review.
paths:
  - "**/*.{kt,kts,xml,gradle,md}"
---

# Code review (Prairie Android)

Review the current branch (or stated files) against the base branch. Prefer the
`code-reviewer` subagent for an isolated pass; this skill is the playbook when
reviewing in-session.

## Protocol

Follow `.agents/code-review-kit/shared/REVIEW_PROTOCOL.md` for severity, report
format, and anti-overflag rules.

## Steps

1. `git status` and `git diff --stat origin/main…HEAD` (or the stated base).
2. Read every behavior-changing hunk in Kotlin/Compose/Gradle/manifests.
3. Apply the Android checklist in `.cursor/agents/code-reviewer.md`.
4. Confirm each finding against the cited lines; drop phantoms.
5. Emit the protocol report (`approve` or `needs-attention`).

## Hard stops (Critical if violated)

- Changing `applicationId` or `prairie_secure_tokens` without migration, or clearing secure prefs on upgrade
- Exposing Reading/ebooks on TV, Watch Together, or rich admin screens without product decision
- Writing playback progress / download state under the wrong content ID
- Shipping secrets, signing material, or device-local config

## After the review

If Critical or Important findings are confirmed and you are also the implementer,
fix them (or rebut in the PR) before merge. Cap fix→re-review loops at 2–3.
Do not gold-plate Suggestions.
