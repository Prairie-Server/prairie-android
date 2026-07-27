# PR 108 Forward Split: Slice B Traceability

Slice B is stacked on reviewed slice A:

```text
base  19bf0dd75d3ffbc9b8734a184a622a50df53baf6
head  split/108-b-auth-epub
```

PR 108 remains the archival integration reference. This branch contains only
the auth/origin/cleartext and EPUB-security vertical slice plus the dependency
state needed by that slice.

## Original path/purpose mapping

| PR 108 commit | Purpose in slice B | Forward-split disposition |
| --- | --- | --- |
| `9597eeee` | Auth refresh/session-safety prerequisite | Relevant `AuthInterceptorImpl` net state is carried by `af886bc7` |
| `65c4b316` | Credential-generation scope prerequisite | Relevant token/scope net state is carried by `af886bc7` |
| `1fecf9b1` | Central HTTP origin model | Folded with its follow-up into `fccbf63c` |
| `cbf398fa` | Reject ambiguous authorities and normalize safe HTTP origins | Folded with `1fecf9b1` into `fccbf63c` |
| `5d5f0562` | Never attach or refresh Silo credentials off-origin | Ported as `af886bc7` |
| `e18d092e` | Reject stale persistent credential generations | Ported in `af886bc7` |
| `c07d9f9f` | Require explicit cleartext-origin consent | Ported as `39aa23f6` |
| `85890c6d` | Parsed EPUB allowlist sanitizer | Cherry-picked with `-x` as `b592319f` |
| `5db84af8` | Restrict SVG paint references to safe local forms | Cherry-picked with `-x` as `18049b41` |
| `14b41b7f` | Bound downloads, ZIP entries, and extracted content | Cherry-picked with `-x` as `a3d3367d` |
| `5a040e0d` | Isolate EPUB resources behind a WebView asset-loader origin | Cherry-picked with `-x` as `91e2a61d` |
| `fd545fab` | Remount reader content when the source changes | Cherry-picked with `-x` as `b0c28d25` |
| `eda4a4a2` | Remove API-26-only hardened-path calls | Cherry-picked with `-x` as `ca6fa611` |

`c00c2ff1` resolves the stacked slice's dependency state. The jsoup and
AndroidX WebKit additions produced seven new artifact checksums; all seven
exactly match the independently generated metadata in archival PR 108.

## Conflict and net-diff decisions

- The late auth commits assume credential-generation machinery introduced in
  `9597eeee` and `65c4b316`. Rather than importing either broad commit, this
  slice takes the final PR 108 state only for the affected auth/token paths.
- Current upstream already declares MockWebServer. The duplicate catalog alias
  introduced by replaying `85890c6d` was removed; no dependency changed.
- `14b41b7f` predates `5d5f0562`. Replaying it after the auth work malformed
  the combined fake data source, so the final PR 108 net state was used for
  `AuthenticatedDataSourceFactory` and its test.
- `ReaderEngineHostSourceTest` did not yet exist on current upstream, although
  `fd545fab` modifies it. Its complete prerequisite test contract was retained
  so the source-remount regression remains executable; no unrelated production
  path was imported.

These decisions intentionally produce different patch IDs for the combined auth
commits while retaining `-x` ancestry for the independently applicable reader
commits.

## TDD and verification record

- Origin-policy RED: the adversarial common tests failed to compile without
  `HttpOriginPolicy`; GREEN after the centralized parser/comparator was added.
- Credential-scope RED: final tests exposed missing scoped refresh and
  generation APIs; GREEN after final auth/token path state was ported.
- Cleartext-consent RED: mobile/TV/store tests failed without an explicit
  consent state machine; GREEN after the store, DI, view models, and UI were
  added.
- EPUB RED: bounded-stream and reader security tests failed without the new
  limits, sanitizer, and isolated resource handler.
- Integration RED: Gradle rejected a duplicate MockWebServer catalog alias
  caused by upstream overlap; removal restored a single unchanged alias.
- Integration RED: reverse replay of the reader/auth commits malformed the
  authenticated data-source test double; using the final two-commit net state
  fixed the ordering conflict.
- Focused GREEN suites cover origin parsing, cross-origin redirects, scoped
  refresh, credential replacement, mobile/TV cleartext persistence, stream and
  ZIP limits, HTML/SVG sanitization, traversal/encoded traversal, resource
  isolation, source remounting, and API-24-compatible paths.
