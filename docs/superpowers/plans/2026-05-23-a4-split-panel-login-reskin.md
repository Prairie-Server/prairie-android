# A.4 — Split-panel login reskin (form + QR placeholder)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reskin `TvLoginScreen` from its current single-column form into a side-by-side split-panel: left = existing credential form, right = QR-code placeholder pane (sized and positioned ready for sub-project C to populate with the live device-login QR). This is the **visual** change only; the actual QR rendering and OAuth Device Authorization Grant polling come in sub-project C.

**Architecture:** One file modified (`TvLoginScreen.kt`). No new files, no new dependencies. The brand header sits above; the two panels live in a `Row` below, the existing card (form) on the left, a new placeholder card on the right. The right panel is non-focusable until C wires the QR — D-pad right from form fields will be a no-op for now.

**Tech stack:** Kotlin 2.1.20, Compose-for-TV 1.0.1, existing infrastructure (Material 3 OutlinedTextField, `TvHeroActionPill`, `TvAnimatedMeshBackground`, theme tokens).

**Reference:** Spec section A.4 at `/opt/prairie-android/docs/superpowers/specs/2026-05-23-android-tv-parity-rework-design.md`. Apple sibling: split-panel login on `TVLoginView` (left form, right QR pane).

**Testing posture:** Per `AGENTS.md`, no UI test for visual restructure.

---

### Task 1: Restructure `TvLoginScreen` into split-panel layout

**Files:**
- Modify: `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/auth/TvLoginScreen.kt`

**Why:** This is the entire A.4 change. The brand header stays on top. Below it: a `Row` with two equal-ish panels — the existing form card on the left, a new QR placeholder card on the right.

**Layout target** (post-change):

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│                  [Prairie wordmark]                            │
│                                                             │
│   ┌─────────────────────────┐  ┌────────────────────────┐  │
│   │ Sign in                 │  │ Sign in with your phone│  │
│   │ Use the account…        │  │                        │  │
│   │                         │  │       [QR placeholder] │  │
│   │ [Username field]        │  │       320×320 dp       │  │
│   │ [Password field]        │  │                        │  │
│   │                         │  │  Loading device-login  │  │
│   │     [Sign In button]    │  │  code…                 │  │
│   └─────────────────────────┘  └────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

- [ ] **Step 1: Read the current file to know structure**

The full file is at `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/auth/TvLoginScreen.kt` (244 lines). Key structure:
- `Box { TvAnimatedMeshBackground(); Column(width=620dp, TopCenter) { BrandHeader; form card } }`
- Form card is a `Column` with header text, two `OutlinedTextField`s, error text, and `TvHeroActionPill` for sign-in.

The whole inner card lives in `Column { ... }` after the `BrandHeader()` and a `Spacer`. We're wrapping that card inside a `Row` and adding a sibling placeholder card.

- [ ] **Step 2: Replace the single-column structure with a brand header + split-panel Row**

Inside the `Box { TvAnimatedMeshBackground(); Column(...) { BrandHeader; Spacer; form card } }`, the outer `Column` currently has `width(620.dp).fillMaxSize()` and stacks the brand and the form vertically.

Restructure as follows. Replace the outer `Column` body:

```kotlin
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()  // remove the 620.dp width so the Row can span wider
                .verticalScroll(rememberScrollState())
                .padding(top = 64.dp, bottom = Spacing.lg, start = Spacing.lg, end = Spacing.lg),
        ) {
            BrandHeader()

            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Left panel — existing form card
                CredentialFormCard(
                    state = state,
                    usernameFocus = usernameFocus,
                    usernameBringIntoView = usernameBringIntoView,
                    passwordBringIntoView = passwordBringIntoView,
                    signInBringIntoView = signInBringIntoView,
                    onUsernameChanged = viewModel::onUsernameChanged,
                    onPasswordChanged = viewModel::onPasswordChanged,
                    onLoginClick = { viewModel.onLoginClick(context) },
                    scope = scope,
                    modifier = Modifier.width(620.dp),
                )

                // Right panel — QR placeholder (sub-project C wires the real QR)
                QrPlaceholderCard(modifier = Modifier.width(480.dp))
            }
        }
```

This requires extracting the form card body into a private `CredentialFormCard` composable (Step 3) and adding a `QrPlaceholderCard` (Step 4).

- [ ] **Step 3: Extract the form card into `private @Composable fun CredentialFormCard(...)`**

Move the existing inner `Column { … the form card … }` (currently around lines 108–223 of the file) into a new private composable. Signature:

```kotlin
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CredentialFormCard(
    state: TvLoginUiState,  // adapt to whatever the actual state type is — likely TvLoginViewModel.UiState or similar
    usernameFocus: FocusRequester,
    usernameBringIntoView: BringIntoViewRequester,
    passwordBringIntoView: BringIntoViewRequester,
    signInBringIntoView: BringIntoViewRequester,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClick: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier
            .background(ElevatedSurface, RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        // … existing form body, unchanged: Sign in header, subtitle,
        // username/password fields, error, Sign In button.
    }
}
```

Look up the actual state type in `TvLoginViewModel.kt` (it's referenced as `state` in the original via `viewModel.uiState.collectAsState()`). Match the type exactly.

- [ ] **Step 4: Add `QrPlaceholderCard` private composable**

Add this private composable to the same file (below `CredentialFormCard`):

```kotlin
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QrPlaceholderCard(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier
            .background(ElevatedSurface, RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        Text(
            text = "Sign in with your phone",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Scan the code with your phone's camera. Coming soon.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 320×320 dp neutral placeholder where the real QR will render
        // once sub-project C ships the device-login flow.
        Box(
            modifier = Modifier
                .size(320.dp)
                .background(
                    Color.White.copy(alpha = 0.06f),
                    RoundedCornerShape(16.dp),
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Loading device-login code…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

The placeholder card is non-focusable (no `Modifier.focusable()`). D-pad right from the form fields will be a no-op until sub-project C wires actual focusable controls into this pane.

- [ ] **Step 5: Verify the outer width allows both panels to fit**

Removing the `.width(620.dp)` from the outer `Column` lets it span the full screen. The Row inside has Form (620dp) + Spacing.lg (18dp) + QR (480dp) = 1118dp. On a 1080p TV (1920dp logical), this fits with margin. On smaller TV emulators, it may overflow — that's acceptable for this skeleton; if QA reveals real issues, downgrade widths or use `weight(1f)`.

Confirm the `verticalScroll` modifier on the outer `Column` is still in place — it provides scroll if the IME pushes content up on small screens.

- [ ] **Step 6: Build**

```bash
cd /opt/prairie-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. The only new imports likely needed are already present (`Row`, `Box`, `size`, `Modifier.size`, `BringIntoViewRequester`, `kotlinx.coroutines.CoroutineScope`) — but verify and add anything missing.

- [ ] **Step 7: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/auth/TvLoginScreen.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android commit -m "feat(tv-auth): split-panel login layout with QR placeholder (A.4)

Existing credential form extracted to private CredentialFormCard.
New QrPlaceholderCard renders a 320×320 dp neutral panel on the right
with 'Loading device-login code…' text. Non-focusable until
sub-project C wires the real OAuth Device Authorization Grant flow.

Layout: BrandHeader above; Row of (form 620dp, gap, QR 480dp) below.
Fits a 1080p TV comfortably; smaller emulators may need width tuning
if QA flags it."
```

---

## Self-Review

**Spec coverage** (against A.4 in the spec doc):
- "Two-column Row: left = credential form, right = QR-code panel" → Task 1 ✓
- "QR panel for this sub-project is a static placeholder with 'Loading device-login code…' string and a 320×320 dp neutral panel" → Task 1 Step 4 ✓
- "Focus default lands on username; D-pad right traverses into the QR panel (which, until C lands, is a non-focusable surface)" → preserved (the existing `usernameFocus.requestFocus()` `LaunchedEffect` stays; right panel is non-focusable as specified)

**Placeholder scan:** No "TBD." The "Coming soon" text in the QR panel is product copy, not a developer placeholder.

**Type consistency:** `CredentialFormCard` and `QrPlaceholderCard` use the same card styling (ElevatedSurface, 24dp radius, 1dp white-8% border, 32/28 dp padding) — visual parity. `state` parameter type adapted to actual `TvLoginViewModel` state at implementation time.

**Sequencing:** Single task; can't sequence further. Steps within Task 1 build on each other: extract form (Step 3) → add placeholder (Step 4) → wrap both in Row (Step 2, written first for the layout target but executed after Steps 3 + 4 conceptually exist).

**Risk:** The `state` parameter type is the one thing the plan doesn't pin precisely — the implementer must check `TvLoginViewModel.kt` for the actual type. The 1118dp Row width is fine on 1080p (1920 logical), tight on 720p TVs (1280 logical). Acceptable risk for a skeleton.
