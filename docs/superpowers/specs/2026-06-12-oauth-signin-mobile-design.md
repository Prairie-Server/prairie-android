# OAuth Sign-In (Mobile) — Design

**Date:** 2026-06-12
**Status:** Approved
**Scope:** Sub-project 6 of the feature-parity push. Adds server-configured OIDC provider sign-in ("Sign in with &lt;Provider&gt;") to the **mobile** login screen, alongside the existing username/password. Server: silo-server `origin/main`. **Mobile only** — TV keeps its existing device-code QR sign-in.

## Goal

Let a mobile user sign in with a server-configured OIDC provider, reusing the same access/refresh token + session model that password login already produces, and degrading silently to password-only when the server exposes no OAuth providers.

## Server contract (verified against silo-server origin/main)

OAuth on silo-server is **plugin-driven generic OIDC** — there are no built-in Google/Apple/Plex sign-in providers; any installed plugin exposing an `auth_provider.v1` capability with `auth_modes: ["oauth2"]` becomes a provider. The flow is **browser/redirect-based with a server-mediated one-time completion code** — there is **no PKCE, no custom URL scheme, and no native redirect**. All flows mint the same `{access_token, refresh_token, expires_in}` pair as password login.

- **Discovery:** `GET /api/v1/auth/providers` → array of `{id, display_name, mode("credentials"|"oauth"), default, icon_url?, installation_id?}`. OAuth-mode entries appear **only** when OAuth routes are mounted (server has `PublicURL` configured + DB). The client detects OAuth availability by the presence of `mode == "oauth"` entries.
- **Init:** `POST /api/v1/auth/oauth/{installId}/init?next=/path` → signs server-side state, stores an `oauth_session`, and returns a **302 redirect to the IdP authorize URL** (in the `Location` header). `next` is sanitized server-side to a path beginning with `/`.
- **Callback (browser):** `GET /api/v1/auth/oauth/{installId}/callback?code=&state=` — the IdP redirects the browser here; server exchanges, provisions/looks up the user, mints tokens, stores a one-time completion code (1-min TTL), then **302 redirects to `{PublicURL}/login/oauth-complete?code=…`**. On failure: **302 to `/login?error=oauth_failed&reason=…`** (reasons include `state_invalid`, `session_expired`, `exchange_failed`, `login_failed`).
- **Complete:** `POST /api/v1/auth/oauth/complete` body `{code}` → `{access_token, refresh_token, expires_in, next}`. Errors: 401 invalid/expired code, 400 missing code, 503 store unavailable.

## Why an in-app WebView (the decisive constraint)

Because the server completes OAuth by redirecting a **browser** to `{PublicURL}/login/oauth-complete?code=…` and offers no app-scheme/PKCE redirect, the client cannot receive the completion through a Chrome Custom Tab without a server-side App Links file per self-hosted domain (rejected: unreliable, larger scope). A **WebView gives full URL interception**, which is exactly what is needed: load the IdP authorize URL, watch each navigation, and capture the completion redirect's `code`. This is the approved mechanism.

**Known limitation:** WebView-based OAuth is fine for self-hosted OIDC (what silo uses) but Google-branded sign-in disallows embedded WebViews (`disallowed_useragent`). Documented, not worked around.

## Client design

### Shared (`shared/`)
- **`model/auth/AuthProvider.kt`** — `AuthProvider(id, displayName, mode, default, iconUrl?, installationId?)` with `@SerialName` matching the server tags; a helper `isOAuth = mode == "oauth"`. Plus `OAuthCompleteRequest(code)` and `OAuthCompleteResponse(accessToken, refreshToken, expiresIn, next)`. Serialization tests.
- **`network/api/AuthApi.kt`** — add:
  - `getAuthProviders(): List<AuthProvider>` → `GET /api/v1/auth/providers`.
  - `oauthInit(installId, next): String?` → `POST /api/v1/auth/oauth/{installId}/init?next={next}` with redirect-following **disabled**, returning the `Location` header (the IdP authorize URL). Implemented with a request that does not auto-follow 3xx (Ktor `HttpRedirect` is installed globally, so this call must opt out per-request via `followRedirects = false`).
  - `oauthComplete(code): OAuthCompleteResponse` → `POST /api/v1/auth/oauth/complete`.
- **`network/OAuthRedirectParser.kt`** (pure, unit-tested) — `classifyOAuthRedirect(url, completePath = "/login/oauth-complete"): OAuthRedirect` returning `Complete(code)` when the URL path ends with the complete path and carries a non-blank `code`, `Failed(reason)` when it matches `…/login?error=oauth_failed&reason=…` (reason may be null), else `Continue`. This is the only branching logic and is thoroughly tested (complete/failed/continue, missing code, trailing slash, extra query params, case of the path).
- **Discovery lives on `AuthRepository`** (single source): add `authProviders(): ApiResult<List<AuthProvider>>` wrapping `authApi.getAuthProviders()`. The setup/login flow already depends on `AuthRepository`, so discovery needs no new dependency. `OAuthLoginRepository` does **not** duplicate discovery.
- **`repository/OAuthLoginRepository.kt`** (singleton) — orchestrates only the handshake: `beginAuthorize(installId): ApiResult<String>` (calls `oauthInit`, returns the authorize URL, errors if no `Location`); `complete(code): ApiResult<User>` (calls `oauthComplete`, then `tokenManager.saveTokens(access, refresh, expiresIn)`, then `authApi.getMe()` for the `User`). The repository never touches the WebView; it only does HTTP + token persistence. Registered in `RepositoryModule`.

### Mobile (`androidApp`)
- **Discovery seam:** `ServerSetupViewModel` already calls `getSetupStatus`/`getSignupStatus` after the user connects to a server. Add a provider-discovery call there (or in `LoginViewModel.init`, whichever keeps the server URL settled) and pass the OAuth providers into `LoginUiState.oauthProviders`. Failure or empty → `oauthProviders = emptyList()` (password-only, unchanged behavior).
- **`LoginScreen`** — below the existing username/password form, render one "Sign in with &lt;displayName&gt;" button per OAuth provider (with `iconUrl` via the existing image loader when present). No providers → nothing extra renders.
- **`LoginViewModel`** — holds `oauthProviders`; `onOAuthProviderClick(provider)` calls `OAuthLoginRepository.beginAuthorize(provider.installationId)` and, on success, exposes the authorize URL as a one-shot navigation event to the WebView screen; surfaces `init` errors inline.
- **New route `OAuthWebView(authorizeUrl)`** + **`OAuthWebViewScreen`** — hosts an `AndroidView(WebView)` (JS enabled, no persistent cookies beyond the flow) with a `WebViewClient` whose `shouldOverrideUrlLoading`/`onPageStarted` runs each URL through `classifyOAuthRedirect`:
  - `Complete(code)` → call `OAuthLoginRepository.complete(code)`; on success navigate to **ProfileSelection** (popping Login + the WebView, identical to the password-login landing); on failure → back to Login with an error.
  - `Failed(reason)` → pop back to Login, map the reason to a user message.
  - `Continue` → let the WebView load it.
  - A top bar with a Cancel/close affordance pops back to Login with no side effects.
- **DI (`AndroidModule`)** — no new ViewModel strictly required for the WebView screen if it is driven by `LoginViewModel` + the repo; if a small `OAuthWebViewViewModel` is cleaner for holding the in-flight state, register it. `OAuthLoginRepository` is injected from shared.

### Token landing
Every path still ends at `tokenManager.saveTokens(access, refresh, expiresIn)` → the existing ProfileSelection gate. Profiles, PIN verification, and multi-server are untouched.

## Error handling
- Discovery failure / no OAuth providers → silently password-only.
- `oauthInit` failure (network, no `Location`, 4xx) → inline error on Login, stay put.
- WebView `Failed(reason)` → pop to Login; map known server reasons (`state_invalid`/`session_expired` → "Sign-in expired, try again"; `exchange_failed`/`login_failed` → "Sign-in failed"; default → generic).
- `oauthComplete` 401/expired/400/503 → "Sign-in expired, please try again."
- User cancels the WebView (Cancel or system back) → return to Login, no state change.

## Testing
- **Shared:** serialization tests for `AuthProvider` (incl. absent `icon_url`/`installation_id`, both modes), `OAuthCompleteResponse`; exhaustive `classifyOAuthRedirect` unit tests; `OAuthLoginRepository` tests with a fake `AuthApi` + `TokenManager` (begin returns the Location URL; complete saves tokens and returns the user; error mapping). The `oauthInit` no-follow behavior is verified with a MockEngine returning a 302 + `Location`.
- **Mobile/UI:** compile gates + a manual checklist on a device against a server configured with an OIDC plugin — provider button appears only when discovery returns an OAuth provider; tapping opens the WebView; completing the IdP flow lands on ProfileSelection with a valid session; cancel returns cleanly; a forced server error redirect shows the mapped message.

## Out of scope
- **TV** — keeps device-code QR sign-in (D-pad credential entry in a WebView is poor UX; QR already covers TV).
- Chrome Custom Tabs / Android App Links / `assetlinks.json`.
- PKCE / native redirect (server doesn't support it).
- Account **linking** (server handler stubs `LinkingUserID = 0`).
- Picking among multiple **credentials**-mode providers — the password form continues to use the default/local provider; the unused `LoginRequest.provider` field stays unused this round.
- The mobile device-code "device" role (mobile remains the approver only).
