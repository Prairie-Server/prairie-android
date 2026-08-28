# Silo / Prairie code-review protocol

Shared process for in-session code-review skills and `code-reviewer` subagents.
Repo-specific checklists live beside each template; do not skip this protocol.

## When to run

- After implementing a feature or fix, before opening or updating a PR
- When the user asks for a code review, Bugbot-style review, or adversarial pass
- On `origin/<default>…HEAD` (or the stated base), not on uncommitted guesses

## Scope

1. Identify the change set: `git diff --stat <base>…HEAD` and the full diff.
2. Read every changed file that can affect runtime behavior. Do not review from summaries alone.
3. Prefer findings grounded in reachable code paths over speculative style nits.
4. Stay inside the diff’s concern. Do not propose drive-by refactors.

## Severity

| Level | Meaning | Action |
| --- | --- | --- |
| **Critical** | Correctness bug, data loss, auth/security break, upgrade wipe, crash/ANR likely in production | Must fix before merge |
| **Important** | Real regression risk, product-exposure violation, missing test for high-risk logic, contract mismatch across repos | Fix or explicitly rebut in the PR |
| **Suggestion** | Clarity, test gaps for low-risk code, maintainability | Optional; do not gold-plate |

LLM reviewers over-flag. For every finding: open the cited lines, confirm the issue is real and reachable, then fix or rebut. Drop phantoms (wrong file, outside diff, impossible path).

## Report format

```text
## Verdict
approve | needs-attention

## Findings
### Critical
- `path:line` — problem — why it matters — concrete fix

### Important
- …

### Suggestions
- …

## Residual risk
- What was not verified (no device, no server, flaky area)
```

If there are no Critical/Important findings, verdict is `approve`.

## Cross-repo awareness

Client-visible API, auth, playback, session, library, or metadata changes are incomplete until Apple, Android, and (when relevant) jellycompat / other clients are handled or explicitly ruled out. Plugins: decide whether the change belongs in the host, SDK, catalog, or a specific plugin repo.

## What not to do

- Do not rewrite large unrelated areas
- Do not add abstractions for hypothetical cases
- Do not weaken security, skip verification, or clear durable auth/storage identity
- Do not expose product surfaces that AGENTS.md marks off-limits without an explicit product decision
