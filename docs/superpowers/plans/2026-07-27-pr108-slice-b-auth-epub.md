# PR 108 Slice B: Auth, Origin, Cleartext Consent, and EPUB Security

**Goal:** Forward-split PR 108's authenticated-origin and EPUB hardening onto
the reviewed slice-A head as one security-focused, compiling vertical slice.

**Stack:** `split/108-b-auth-epub` targets
`split/108-a-supply-chain`; archival PR 108 remains unchanged.

**Original commits:**

- `9597eeee` — prerequisite auth refresh/session-safety state in the client audit
- `65c4b316` — prerequisite credential-generation scope state
- `85890c6d` — parse and sanitize EPUB markup with an allowlist
- `5db84af8` — validate SVG paint references
- `14b41b7f` — bound remote and archive content
- `5a040e0d` — isolate EPUB resources behind WebView asset loading
- `fd545fab` — remount the WebView when its source changes
- `eda4a4a2` — keep hardened EPUB paths compatible with Android API 24
- `1fecf9b1` — centralize authenticated HTTP origin comparison
- `cbf398fa` — reject ambiguous HTTP authorities
- `5d5f0562` — keep credentials on the configured Silo origin
- `e18d092e` — reject authentication scopes whose credentials were replaced
- `c07d9f9f` — require explicit consent before cleartext login

## Task 1: Authenticated Origin Policy

1. Import the final origin-policy tests from `cbf398fa` without production
   code and run the shared common tests to record RED.
2. Import the final origin-policy production implementation and run the same
   tests to GREEN.
3. Confirm default-port normalization, IPv4/IPv6 handling, user-info
   rejection, ambiguous-authority rejection, and HTTPS/HTTP separation.

## Task 2: Credential Scoping and Replacement Races

1. Import the tests changed by `5d5f0562` and `e18d092e` first, then run
   focused shared and Android-shared tests to record RED.
2. Port only the related token-manager, auth-interceptor, media-auth session,
   data-source, and HTTP-client net changes.
3. Run focused tests to GREEN, including redirects, host/port/scheme changes,
   token-generation replacement, and reader-file authentication.

## Task 3: Explicit Cleartext Consent

1. Import cleartext consent and mobile/TV persistence tests first and run them
   to record RED.
2. Port the consent store, dependency injection, setup view-model state
   machines, and confirmation UI for mobile and TV.
3. Verify credentials are never persisted or sent before the user confirms
   the exact cleartext origin, and that changing the entered origin invalidates
   prior consent.

## Task 4: EPUB Sanitization and Resource Isolation

1. Import sanitizer/resource-path/WebView tests first and run focused reader
   tests to record RED.
2. Port the parsed allowlist sanitizer, isolated asset-loader origin,
   resource-path handler, reader HTML/JavaScript bridge changes, and API-24
   compatibility correction.
3. Verify scripts, event handlers, dangerous URLs, traversal, encoded
   traversal, external origins, and cross-book paths are rejected while valid
   EPUB-local resources remain readable.

## Task 5: Dependency State, Traceability, and Verification

1. Regenerate slice-B lockfiles and SHA-256 metadata after adding parser and
   WebView dependencies; never copy later-slice dependency state wholesale.
2. Add a path/purpose traceability note mapping all eight original commits to
   forward-split commits and document any conflict-resolution deviations.
3. Run:

   - focused security tests from Tasks 1–4
   - `./scripts/test-check-build-supply-chain.sh`
   - `./scripts/check-build-supply-chain.sh`
   - `./gradlew testDebugUnitTest`
   - `./gradlew :androidApp:assembleRelease :androidTvApp:assembleRelease`
   - `git diff --check`

4. Obtain independent code/security review and fix all Critical or Important
   findings.
5. Push the branch and open a draft stacked PR against
   `split/108-a-supply-chain`. Do not merge either PR.
