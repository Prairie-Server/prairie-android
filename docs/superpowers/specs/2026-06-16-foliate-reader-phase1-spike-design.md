# Foliate Reader — Phase 1: feasibility spike (design)

**Date:** 2026-06-16
**Status:** Design approved (brainstorm). Spec for the first phase of a multi-phase project.
**Repos:** client `silo-android` (this repo); server/web `silo-server` @ `origin/main 203d9be7` (read-only reference).

## Why (decisions locked during brainstorming)

The web reader (`silo-server` `web/src/reader/FoliateBookReader.tsx`) is built on **foliate-js** and persists reading position/bookmarks as **EPUB CFI** (and `fraction:<n>`) in the server's `ebook` `location`/`cfi_range` fields. The Android reflow reader writes its own `ReflowLocator` JSON into the same `location` field, so **web ↔ Android position/bookmarks don't interoperate today**.

Goal of the overall project: **precise cross-client (web ↔ Android) reading position + bookmarks via EPUB CFI**. To guarantee CFI compatibility rather than hand-matching the spec, Android will **adopt the same foliate-js engine in its WebView** (Path 3b), as a **capability-gated primary engine with the existing reflow engine as fallback** for WebViews that can't run foliate-js.

This spec covers **Phase 1 only: a feasibility spike.** It does not touch the production reader. Its job is to answer, with evidence, whether we can run foliate-js in Android's WebView down to a defensible floor, and to establish that floor.

## Roadmap (for context; later phases are separate specs)
- **Phase 1 (this spec):** feasibility spike — foliate-js renders an EPUB in an Android WebView, generates/resolves web-compatible CFIs, and we establish the WebView-capability floor.
- **Phase 2:** capability-gated `FoliateReaderEngine` wired into `ReaderEngineHost` as primary for EPUB, reflow engine as fallback; **resume via CFI** (read/write `location` as CFI / `fraction:<n>`, matching the web reader).
- **Phase 3:** bookmarks/annotations interop — bookmarks via `location`-CFI, highlights/notes via `cfi_range` + `selected_text`/`note`/`style`/`color`, with selection UI.
- **Phase 4:** migration/fallback polish for existing `ReflowLocator` locators, and engine-selection UX.

## Hard constraints
- **Android 7 / API-24 build floor** stays. The *foliate reader* may require a newer **WebView** than the build floor — establishing that gap is the spike's point.
- Phase 1 changes are **isolated behind a debug-only entry point**; production reading is untouched.

## Key facts established from the foliate-js source (drive the design)
- foliate-js is an **ESM Custom Element**: `view.js` `export class View extends HTMLElement`; host does `document.createElement("foliate-view")` then `await view.open(book)`.
- **EPUB delivery = a `File`/`Blob` of the raw `.epub`**; foliate unzips it itself in JS via `fflate`. (Not an unpacked dir.)
- **CFI API:** `view.getCFI(index, range)` (generate), `view.goTo(cfiOrTarget)` / `view.goToFraction(frac)` (navigate), and a `relocate` event whose detail carries the current CFI + fraction. `epubcfi.js` exports `fromRange/toRange/fromElements/toElement/compare/parse/collapse`.
- **WebView floor drivers (runtime APIs, not transpilable):** Custom Elements v1 (Chrome 54+), Shadow DOM v1 (53+), **`adoptedStyleSheets`/Constructable Stylesheets (73+)**, ResizeObserver (64+). Plus syntax `?.`/`??`/`replaceAll` (≈Chrome 80/85) → **must transpile** the bundle for engines below ~80. Net effective floor ≈ **Chrome 73+**; polyfilling below that is heavy/unreliable and out of scope.

## Architecture (all new, isolated)

1. **Foliate bundle** — `androidApp/src/androidMain/assets/reader/foliate/foliate-bundle.js`: the vendored `silo-server/web/vendor/foliate-js` (`view.js` + its deps incl. `fflate`) bundled into one file by **esbuild** (or rollup), transpiled to a conservative ES target (start at `es2017`; the spike confirms the needed target). Committed asset **plus** a checked-in regen script (`scripts/build-foliate-bundle.*`) documenting source commit + build command. Also a tiny **host HTML** (`foliate-host.html`) that imports the bundle and the glue.

2. **Host glue JS** (`foliate-host.js`) — runs in the WebView: feature-detects capabilities (Custom Elements, Shadow DOM, `adoptedStyleSheets`, ResizeObserver) and reports via the bridge; on `load(url)` fetches the `.epub` bytes → `new File([blob], name)` → creates `<foliate-view>` → `view.open(book)`; forwards `relocate` (CFI + fraction) to the bridge; implements `goToCfi(cfi)` and `goToFraction(f)`.

3. **`FoliateWebView`** (Compose `AndroidView` + `WebView`) — loads `foliate-host.html` via a **`WebViewAssetLoader`** (so JS `fetch()` of same-origin URLs works), serves the `.epub` bytes through a `shouldInterceptRequest`/asset-loader path resolving to the local downloaded/cached EPUB file (reusing the existing reader file resolver), and bridges via a `@JavascriptInterface`:
   - JS→Kotlin: `onReady()`, `onCapabilities(json)`, `onRelocated(cfi, fraction)`, `onError(msg)`.
   - Kotlin→JS (`evaluateJavascript`): `load(epubUrl)`, `goToCfi(cfi)`, `goToFraction(f)`.

4. **`WebViewCapability` probe** — a Kotlin/JS feature test returning whether the device's WebView supports the foliate floor (Custom Elements + Shadow DOM + `adoptedStyleSheets` + ResizeObserver). Records the WebView package version (`WebView.getCurrentWebViewPackage()`).

5. **Spike screen** — a **debug-only** Compose screen (behind a build-config/debug flag, not in the normal nav) that opens a bundled sample EPUB in `FoliateWebView` and runs a **self-test**: capture CFI at a position → `goToFraction(0)` away → `goToCfi(capturedCfi)` back → assert the reported location matches; surface the captured CFI string on-screen for eyeball comparison against a web-reader CFI for the same book; show the capability/WebView-version readout.

## Data flow
`.epub` on disk → asset-loader serves bytes to the WebView → `foliate-host.js` builds a `Blob`/`File` → `view.open(book)` renders/paginates → `relocate` emits a CFI+fraction → bridge → Kotlin (logged/asserted). `goToCfi`/`goToFraction` drive the reverse.

## Error handling
- **Capability unsupported** (WebView below floor): `onCapabilities` reports it; the spike shows "unsupported on this WebView" — it does **not** crash. (In Phase 2 this is the signal to fall back to the reflow engine.)
- foliate **load/parse/render error** → `onError` → shown on the spike screen; never crashes the host activity.
- `goToCfi` with an unresolvable CFI → foliate no-ops / the host catches and reports; the self-test records a failure rather than throwing.

## Testing & verification (the deliverable)
- **JS self-test** (in the spike): CFI round-trip pass/fail, capability readout.
- **Device QA matrix (mandatory):** an **API-24 emulator**, a modern phone, and — critically — the **oldest realistic WebView** we can obtain (downgrade WebView on an emulator or test an un-updated image). Confirm: foliate loads, renders, paginates; CFI round-trips; the CFI **string format matches** what the web reader produces for the same book (open the same EPUB in the web reader, compare CFIs).
- **Kotlin unit tests:** bridge plumbing where extractable (e.g. capability-JSON parsing, the self-test result reducer) via the repo's `kotlin.test` conventions; the WebView/foliate behavior itself is device-verified (source-assertion for the host wiring per repo convention).
- **Findings note** (committed alongside): answers the gating questions — *Does foliate-js run on API-24 with an updated WebView? With a stale one? What transpile target/polyfills are required? What's the established WebView floor? Perf (first render, navigation)? Do CFIs match the web reader?* — and a go/no-go for Phase 2 (or a fallback recommendation: 3a-port or Path-2-`fraction:` if foliate proves infeasible at an acceptable floor).

## Out of scope for Phase 1 (YAGNI)
Wiring into `ReaderEngineHost`/production reading; resume/progress persistence; bookmarks/highlights; removing or modifying the current `paginator.js`/reflow engine; comics/PDF (foliate supports them but not in scope); the engine-selection capability gate itself (Phase 2). Phase 1 is feasibility + the bridge, nothing user-facing beyond a hidden debug screen.

## Success criteria
A debug screen that, on a supported WebView, renders a sample EPUB via foliate-js and demonstrates a CFI round-trip whose CFI matches the web reader's format — plus a committed findings note that establishes the WebView floor and gives a clear go/no-go (and the bundling recipe) for Phase 2.
