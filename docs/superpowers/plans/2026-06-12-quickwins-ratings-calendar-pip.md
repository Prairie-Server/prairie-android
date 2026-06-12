# Quick Wins (Ratings, Calendar, PiP) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement personal star ratings (mobile + TV), a calendar/upcoming surface (mobile screen + TV home row), and picture-in-picture video playback (mobile), per the approved spec `docs/superpowers/specs/2026-06-12-quickwins-ratings-calendar-pip-design.md`.

**Architecture:** Shared KMP module gains the wire-contract fixes and new calendar stack (models → api → repository → viewmodel); mobile and TV consume them following each app's established detail/action and navigation patterns. PiP is a self-contained mobile addition: a singleton `PipController` bridging MainActivity callbacks and the player composition.

**Tech Stack:** Kotlin Multiplatform (shared), Jetpack Compose + TV Compose, Ktor client, kotlinx.serialization, Koin, Media3. Tests: kotlin-test in `commonTest`/`androidUnitTest`, `./gradlew :<module>:testDebugUnitTest`.

**Server contracts:** verified against silo-server `origin/main` — `PUT/GET/DELETE /api/v1/ratings/{item_id}` (integer 1–5; `user_rating` embedded in item detail) and `GET /api/v1/calendar?start&end&filter&library_id&timezone` (day-grouped events, ≤31-day span).

**Task sections:** R = ratings, C = calendar. Section P (picture-in-picture) was DROPPED by user decision on 2026-06-12 — tasks P1/P2 below are retained for reference but must not be executed.

---

## Section R: Personal ratings

### Task R1: Shared rating contract — integer rating + `user_rating` on ItemDetail

**Files:**
- Modify: shared/src/commonMain/kotlin/com/continuum/app/model/personal/PersonalDataModels.kt
- Modify: shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt
- Modify: shared/src/commonMain/kotlin/com/continuum/app/repository/PersonalDataRepository.kt
- Modify: shared/src/commonMain/kotlin/com/continuum/app/model/catalog/CatalogModels.kt
- Test (create): shared/src/commonTest/kotlin/com/continuum/app/model/personal/PersonalDataModelsSerializationTest.kt
- Test (modify): shared/src/commonTest/kotlin/com/continuum/app/model/catalog/MediaSurfaceContractSerializationTest.kt

Caller audit (already done — verified with `grep -rn "setRating\|getRating\|deleteRating\|SetRatingRequest" --include="*.kt"`): the only references are `PersonalDataApi.kt`, `PersonalDataRepository.kt`, and the model file itself. No UI callers exist yet, so the `Double → Int` signature change breaks nothing else. This task also absorbs the JSON-integer serialization test.

- [ ] **Step 1: Write the failing test**

Create `shared/src/commonTest/kotlin/com/continuum/app/model/personal/PersonalDataModelsSerializationTest.kt`:

```kotlin
package com.continuum.app.model.personal

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PersonalDataModelsSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun encodesSetRatingRequestAsJsonInteger() {
        // Go's int unmarshal rejects "4.0" — the wire value must be a bare
        // JSON integer with no decimal point.
        val encoded = json.encodeToString(
            SetRatingRequest.serializer(),
            SetRatingRequest(rating = 4),
        )
        assertEquals("""{"rating":4}""", encoded)
    }
}
```

Append these two tests inside `MediaSurfaceContractSerializationTest` (before the closing brace of the class; `assertEquals`/`assertNull` are already imported in that file):

```kotlin
    @Test
    fun decodesUserRatingOnItemDetail() {
        val detail = json.decodeFromString<ItemDetail>(
            """
            { "content_id": "m1", "type": "movie", "title": "Rated Movie", "user_rating": 4 }
            """.trimIndent(),
        )
        assertEquals(4, detail.userRating)
    }

    @Test
    fun userRatingDefaultsToNullWhenAbsent() {
        val detail = json.decodeFromString<ItemDetail>(
            """
            { "content_id": "m2", "type": "movie", "title": "Unrated Movie" }
            """.trimIndent(),
        )
        assertNull(detail.userRating)
    }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :shared:testDebugUnitTest
```

Expected failure: compilation errors — `SetRatingRequest(rating = 4)` is a type mismatch (constructor currently takes `Double`), and `detail.userRating` is an unresolved reference (`ItemDetail` has no such property).

- [ ] **Step 3: Implementation**

In `shared/src/commonMain/kotlin/com/continuum/app/model/personal/PersonalDataModels.kt`, replace:

```kotlin
@Serializable
data class SetRatingRequest(
    val rating: Double
)
```

with:

```kotlin
@Serializable
data class SetRatingRequest(
    val rating: Int
)
```

In `shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt` (lines 102–107), change the `setRating` signature from `rating: Double` to `rating: Int` (body otherwise unchanged):

```kotlin
    suspend fun setRating(itemId: String, rating: Int): ApiResult<Unit> = safeApiCall {
        client.put("/api/v1/ratings/$itemId") {
            contentType(ContentType.Application.Json)
            setBody(SetRatingRequest(rating))
        }
    }
```

In `shared/src/commonMain/kotlin/com/continuum/app/repository/PersonalDataRepository.kt` (lines 90–92):

```kotlin
    /** Sets or updates the user's star rating (integer 1-5) for a specific item. */
    suspend fun setRating(itemId: String, rating: Int): ApiResult<Unit> =
        personalDataApi.setRating(itemId, rating)
```

In `shared/src/commonMain/kotlin/com/continuum/app/model/catalog/CatalogModels.kt`, inside `ItemDetail` (line ~123), after the `userData` property add:

```kotlin
    /** The current user's personal star rating (1-5), when set. */
    @SerialName("user_rating") val userRating: Int? = null,
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :shared:testDebugUnitTest
```

All shared tests pass, including the three new ones.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/personal/PersonalDataModels.kt shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt shared/src/commonMain/kotlin/com/continuum/app/repository/PersonalDataRepository.kt shared/src/commonMain/kotlin/com/continuum/app/model/catalog/CatalogModels.kt shared/src/commonTest/kotlin/com/continuum/app/model/personal/PersonalDataModelsSerializationTest.kt shared/src/commonTest/kotlin/com/continuum/app/model/catalog/MediaSurfaceContractSerializationTest.kt && git commit -m "Fix rating wire contract to integer and expose user_rating

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Task R2: Mobile rating UI — star button, RatingSheet, ViewModel actions

**Files:**
- Modify: androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailViewModel.kt
- Modify: androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/DetailSharedComponents.kt
- Create: androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/RatingSheet.kt
- Modify: androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/MovieDetailContent.kt
- Modify: androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/SeriesDetailContent.kt
- Modify: androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt
- Test: none (Compose UI + ViewModel wiring)

Depends on Task R1. `HeroActionStack` call sites (verified by grep): `MovieDetailContent.kt:118` and `SeriesDetailContent.kt:86` only — `AudiobookDetailContent`/`BookDetailContent` use their own layout and do not use `HeroActionStack`, so they are out of scope per the design.

- [ ] **Step 1: Write the failing test**

Not unit-testable (Compose UI + optimistic-update wiring through Koin-injected repositories; the androidApp module has no Compose UI test harness). Manual check, on a phone build against a live server:
1. Open a movie detail → an outline star button appears in the circle action row after the watched toggle.
2. Tap star → bottom sheet shows five outline stars and **no** "Remove rating" row.
3. Tap star 4 → sheet closes, hero star turns filled/gold; `PUT /api/v1/ratings/{id}` body is `{"rating":4}` (verify integer in server logs).
4. Reopen the sheet → 4 stars filled, "Remove rating" row visible. Tap "Remove rating" → sheet closes, hero star returns to outline.
5. Leave and re-enter the detail screen → rating state matches the server (`user_rating` seed).
6. Repeat steps 1–4 on a series detail.
7. Airplane-mode test: rate while offline → star briefly fills, then reverts (optimistic rollback).

- [ ] **Step 2: Run test to verify it fails**

N/A (manual check above; performed after implementation in Step 4).

- [ ] **Step 3: Implementation**

**3a — `ItemDetailViewModel.kt`.** In `ItemDetailUiState`, after `isInWatchlist` add:

```kotlin
    val userRating: Int? = null,
```

In `loadDetail()`'s `ApiResult.Success` branch, add `userRating = detail.userRating,` to the `_uiState.update { it.copy(...) }` that sets `detail = detail`.

Add these two functions directly after `toggleFavorite()` (after its closing brace at line ~282):

```kotlin
    /**
     * Sets the user's star rating, clamped to 1..5. Mirrors the
     * [toggleFavorite] optimistic-update pattern: update state, call the
     * repository, revert on any non-Success result.
     */
    fun setRating(stars: Int) {
        val target = stars.coerceIn(1, 5)
        viewModelScope.launch {
            val previous = _uiState.value.userRating
            // Optimistic update
            _uiState.update { it.copy(userRating = target) }
            when (personalDataRepository.setRating(contentId, target)) {
                is ApiResult.Success -> { /* already updated */ }
                else -> {
                    // Revert on failure
                    _uiState.update { it.copy(userRating = previous) }
                }
            }
        }
    }

    /** Removes the user's rating with optimistic update + revert on failure. */
    fun clearRating() {
        viewModelScope.launch {
            val previous = _uiState.value.userRating ?: return@launch
            // Optimistic update
            _uiState.update { it.copy(userRating = null) }
            when (personalDataRepository.deleteRating(contentId)) {
                is ApiResult.Success -> { /* already updated */ }
                else -> {
                    // Revert on failure
                    _uiState.update { it.copy(userRating = previous) }
                }
            }
        }
    }
```

**3b — `DetailSharedComponents.kt`.** Add two imports to the existing icon import block (after `import androidx.compose.material.icons.filled.PlayArrow`):

```kotlin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
```

In `HeroActionStack` (lines 525–590): add the parameters `userRating: Int? = null,` and `onRateClick: (() -> Unit)? = null,` after `onToggleWatched`, and insert this block in the button `Row` after the watched `CircleActionButton` and before the `downloadSlot` block:

```kotlin
            if (onRateClick != null) {
                CircleActionButton(
                    icon = Icons.Filled.StarBorder,
                    activeIcon = Icons.Filled.Star,
                    isActive = userRating != null,
                    contentDescription = userRating?.let { "Rated $it of 5" } ?: "Rate",
                    onClick = onRateClick,
                    activeTint = Color(0xFFFFC107),
                )
            }
```

NOTE FOR IMPLEMENTER: read the current `HeroActionStack` and `CircleActionButton` signatures first — keep every existing parameter and button exactly as-is (the favorite button's `activeTint = Color(0xFFEF5350)` idiom shows `activeTint` exists; verify and match the real parameter names).

**3c — Create `RatingSheet.kt`** (matches the `MediaSelectors.kt` picker-sheet idiom: `ModalBottomSheet` + bold header + divider):

```kotlin
package com.continuum.app.android.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val RatingStarTint = Color(0xFFFFC107)

/**
 * Bottom sheet for the personal 1-5 star rating. Five large tappable
 * stars reflecting the current rating, plus a "Remove rating" action
 * shown only when a rating is set. Styled after the picker sheets in
 * MediaSelectors.kt (header + divider + surface container).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingSheet(
    currentRating: Int?,
    onSetRating: (Int) -> Unit,
    onClearRating: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = "Rate",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (1..5).forEach { star ->
                val filled = currentRating != null && star <= currentRating
                IconButton(
                    onClick = { onSetRating(star) },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Rate $star of 5",
                        tint = if (filled) RatingStarTint else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }

        if (currentRating != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClearRating)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Remove rating",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
```

**3d — `MovieDetailContent.kt`.** Add to the signature after `onWatchlistClick`:

```kotlin
    userRating: Int? = null,
    onSetRating: (Int) -> Unit = {},
    onClearRating: () -> Unit = {},
```

Add `var showRatingSheet by remember { mutableStateOf(false) }` next to the other sheet-visibility state vars. In the `HeroActionStack(` call add:

```kotlin
                    userRating = userRating,
                    onRateClick = { showRatingSheet = true },
```

At the end of the composable, after the existing sheet blocks, add:

```kotlin
    if (showRatingSheet) {
        RatingSheet(
            currentRating = userRating,
            onSetRating = { stars ->
                onSetRating(stars)
                showRatingSheet = false
            },
            onClearRating = {
                onClearRating()
                showRatingSheet = false
            },
            onDismiss = { showRatingSheet = false },
        )
    }
```

**3e — `SeriesDetailContent.kt`.** Same three additions as 3d (signature params after `onWatchlistClick`, `showRatingSheet` state var — adding the `mutableStateOf`/`remember`/`setValue` imports if not already present — the two `HeroActionStack` args, and the trailing `RatingSheet` block placed after the `LazyColumn` and before the function's closing brace).

**3f — `ItemDetailScreen.kt`.** In BOTH the `SeriesDetailContent(` and `MovieDetailContent(` calls, after the `onWatchlistClick` argument add:

```kotlin
                            userRating = state.userRating,
                            onSetRating = { viewModel.setRating(it) },
                            onClearRating = { viewModel.clearRating() },
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidApp:compileDebugKotlinAndroid :androidApp:testDebugUnitTest
```

Then run the manual checklist from Step 1 on a device/emulator.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailViewModel.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/DetailSharedComponents.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/RatingSheet.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/MovieDetailContent.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/SeriesDetailContent.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt && git commit -m "Add personal star rating to mobile detail screens

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Task R3: TV rating UI — focusable star action + D-pad rating dialog

**Files:**
- Modify: androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailViewModel.kt
- Create: androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvRatingDialog.kt
- Modify: androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailScreen.kt
- Test: none (TV Compose UI)

Depends on Task R1.

- [ ] **Step 1: Write the failing test**

Not unit-testable (TV Compose focus/D-pad behavior). Manual check, on a TV emulator/device with D-pad:
1. Open a movie detail → a star circle button sits in the hero action row after the watched toggle; it is focusable left/right between the other buttons.
2. Press OK on it → rating dialog opens with 5 stars; focus lands on star 1 (unrated) or the current rating star (rated).
3. D-pad left/right moves across the 5 stars; the fill preview follows the focused star.
4. OK on star 3 → dialog closes, hero star becomes filled; server receives `PUT /api/v1/ratings/{id}` `{"rating":3}`.
5. Reopen the dialog → 3 stars filled and a "Remove rating" row below (D-pad down to reach it). OK on it → dialog closes, hero star back to outline.
6. Back button dismisses the dialog without changes.
7. Rapid double-press OK on a star → only one request fires (`isTogglingRating` guard).

- [ ] **Step 2: Run test to verify it fails**

N/A (manual check above; performed after implementation in Step 4).

- [ ] **Step 3: Implementation**

**3a — `TvItemDetailViewModel.kt`.** In `TvItemDetailUiState`, after `isTogglingWatchlist` add:

```kotlin
    val userRating: Int? = null,
    val isTogglingRating: Boolean = false,
```

In `loadDetail()`'s success branch, add `userRating = detail.userRating,` to the state copy that sets `detail`.

Add these two functions directly after `onToggleWatchlist()` (mirroring the favorite-toggle pattern at lines 142–158):

```kotlin
    fun onSetRating(stars: Int) {
        val current = _uiState.value
        if (current.isTogglingRating) return
        val target = stars.coerceIn(1, 5)
        val previous = current.userRating
        _uiState.update { it.copy(isTogglingRating = true, userRating = target) }
        viewModelScope.launch {
            val result = personalDataRepository.setRating(contentId, target)
            if (result !is ApiResult.Success) {
                // Roll back on error.
                _uiState.update {
                    it.copy(isTogglingRating = false, userRating = previous)
                }
            } else {
                _uiState.update { it.copy(isTogglingRating = false) }
            }
        }
    }

    fun onClearRating() {
        val current = _uiState.value
        if (current.isTogglingRating) return
        val previous = current.userRating ?: return
        _uiState.update { it.copy(isTogglingRating = true, userRating = null) }
        viewModelScope.launch {
            val result = personalDataRepository.deleteRating(contentId)
            if (result !is ApiResult.Success) {
                // Roll back on error.
                _uiState.update {
                    it.copy(isTogglingRating = false, userRating = previous)
                }
            } else {
                _uiState.update { it.copy(isTogglingRating = false) }
            }
        }
    }
```

**3b — Create `TvRatingDialog.kt`** (Popup panel + focus idiom copied from `TvOptionDialog.kt`; horizontal star row):

```kotlin
package com.continuum.app.tv.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.tv.ui.theme.DarkBackground
import com.continuum.app.tv.ui.theme.FocusedContainer
import com.continuum.app.tv.ui.theme.FocusedContent

private val TvRatingStarTint = Color(0xFFFFC107)

/**
 * D-pad rating dialog: five stars navigable left/right, OK sets the
 * rating, plus a "Remove rating" row (D-pad down) when a rating exists.
 * Panel + focus idiom mirrors TvOptionDialog.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvRatingDialog(
    currentRating: Int?,
    onSetRating: (Int) -> Unit,
    onClearRating: () -> Unit,
    onDismiss: () -> Unit,
) {
    val initialStarFocus = remember { FocusRequester() }
    var preview by remember { mutableIntStateOf(currentRating ?: 0) }
    val initialStar = currentRating?.coerceIn(1, 5) ?: 1

    LaunchedEffect(Unit) {
        runCatching { initialStarFocus.requestFocus() }
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 72.dp, top = 120.dp, end = 72.dp, bottom = 84.dp),
            contentAlignment = Alignment.Center,
        ) {
            val panelShape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .background(
                        color = DarkBackground.copy(alpha = 0.68f),
                        shape = panelShape,
                    )
                    .border(1.2.dp, Color.White.copy(alpha = 0.20f), panelShape)
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "RATE THIS TITLE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        letterSpacing = 1.8.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White.copy(alpha = 0.58f),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    (1..5).forEach { star ->
                        TvRatingStar(
                            filled = star <= preview,
                            contentDescription = "Rate $star of 5",
                            onFocused = { preview = star },
                            onClick = { onSetRating(star) },
                            modifier = if (star == initialStar) {
                                Modifier.focusRequester(initialStarFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                }

                if (currentRating != null) {
                    TvRatingClearRow(onClick = onClearRating)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvRatingStar(
    filled: Boolean,
    contentDescription: String,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = CircleShape

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.34f),
            contentColor = if (filled) TvRatingStarTint else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = if (filled) TvRatingStarTint else Color.Black,
            pressedContainerColor = Color.White,
            pressedContentColor = if (filled) TvRatingStarTint else Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.32f)),
                shape = shape,
            ),
            focusedBorder = Border(
                border = BorderStroke(2.0.dp, Color.Black.copy(alpha = 0.82f)),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.White.copy(alpha = 0.16f),
                elevation = 14.dp,
            ),
        ),
        modifier = modifier
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.98f), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = contentDescription,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvRatingClearRow(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.04f),
            contentColor = Color.White,
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
            pressedContainerColor = FocusedContainer,
            pressedContentColor = FocusedContent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.025f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, DarkBackground.copy(alpha = 0.82f)),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.White.copy(alpha = 0.18f),
                elevation = 16.dp,
            ),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.98f), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Remove rating",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isFocused) FocusedContent else Color.White,
            )
        }
    }
}
```

NOTE FOR IMPLEMENTER: verify the TvOptionDialog idiom and the theme color names (`DarkBackground`, `FocusedContainer`, `FocusedContent`) against the actual files before using; adapt to what exists.

**3c — `TvItemDetailScreen.kt`.** Add imports `androidx.compose.material.icons.filled.Star` and `androidx.compose.material.icons.outlined.StarBorder`. In `HeroActionRow`: add `var ratingOpen by remember(detail.contentId) { mutableStateOf(false) }` next to `moreOpen`; insert after the watched `CircleAction` block:

```kotlin
            CircleAction(
                icon = if (state.userRating != null) Icons.Filled.Star else Icons.Outlined.StarBorder,
                onClick = { ratingOpen = true },
                contentDescription = state.userRating?.let { "Rated $it of 5" } ?: "Rate",
                isActive = state.userRating != null,
            )
```

(NOTE: verify `HeroActionRow` has access to `state`/the ViewModel — read the actual parameter list and thread `userRating` + the two callbacks through parameters if the row only receives `detail`; match how favorite/watchlist actions are wired.)

At the end of `HeroActionRow`, after the existing `if (moreOpen && hasOverflowMenu) { ... }` block, add:

```kotlin
    if (ratingOpen) {
        TvRatingDialog(
            currentRating = state.userRating,
            onSetRating = { stars ->
                ratingOpen = false
                viewModel.onSetRating(stars)
            },
            onClearRating = {
                ratingOpen = false
                viewModel.onClearRating()
            },
            onDismiss = { ratingOpen = false },
        )
    }
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidTvApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest
```

Then run the manual D-pad checklist from Step 1 on a TV emulator.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailViewModel.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvRatingDialog.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailScreen.kt && git commit -m "Add personal star rating to TV detail screen

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

## Section C: Calendar / upcoming

### Task C1: Shared calendar response models + serialization test

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/model/calendar/CalendarModels.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/model/calendar/CalendarModelsSerializationTest.kt`

- [ ] **Step 1: Write the failing test**

Create `shared/src/commonTest/kotlin/com/continuum/app/model/calendar/CalendarModelsSerializationTest.kt`:

```kotlin
package com.continuum.app.model.calendar

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarModelsSerializationTest {

    // Mirrors ContinuumJson (network/ContinuumHttpClientImpl.kt) so decode
    // behavior in tests matches production wire handling exactly.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `decodes calendar response with episode and movie items`() {
        val payload = """
            {
              "events": [
                {
                  "date": "2026-06-08",
                  "items": [
                    {
                      "content_id": "ep-101",
                      "type": "episode",
                      "title": "Severance",
                      "episode_title": "Cold Harbor",
                      "series_id": "series-7",
                      "season_number": 2,
                      "episode_number": 10,
                      "air_date": "2026-06-08",
                      "air_time": "21:00",
                      "air_at": "2026-06-09T01:00:00Z",
                      "air_timezone": "America/New_York",
                      "local_air_date": "2026-06-08",
                      "poster_url": "/posters/severance.jpg",
                      "poster_thumbhash": "1QcSHQRnh493V4dIh4eXh1h4kJUI",
                      "watched": false,
                      "badges": ["finale"]
                    }
                  ]
                },
                {
                  "date": "2026-06-10",
                  "items": [
                    {
                      "content_id": "movie-9",
                      "type": "movie",
                      "title": "Dune Part Three",
                      "air_date": "2026-06-10",
                      "local_air_date": "2026-06-10",
                      "watched": true
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString(CalendarResponse.serializer(), payload)

        assertEquals(2, response.events.size)

        val episode = response.events[0].items.single()
        assertEquals("2026-06-08", response.events[0].date)
        assertEquals("ep-101", episode.contentId)
        assertEquals(CalendarItemType.Episode, episode.type)
        assertTrue(episode.isEpisode)
        assertEquals("Cold Harbor", episode.episodeTitle)
        assertEquals(2, episode.seasonNumber)
        assertEquals(10, episode.episodeNumber)
        assertEquals("21:00", episode.airTime)
        assertEquals("America/New_York", episode.airTimezone)
        assertEquals("1QcSHQRnh493V4dIh4eXh1h4kJUI", episode.posterThumbhash)
        assertFalse(episode.watched)
        assertEquals(listOf(CalendarBadge.Finale), episode.badges)
        // Episodes route to the series detail page.
        assertEquals("series-7", episode.detailContentId)

        val movie = response.events[1].items.single()
        assertFalse(movie.isEpisode)
        assertTrue(movie.watched)
        assertTrue(movie.badges.isEmpty())
        assertNull(movie.posterUrl)
        // Movies route to their own content id.
        assertEquals("movie-9", movie.detailContentId)
    }

    @Test
    fun `episode without series id falls back to its own content id for routing`() {
        val payload = """
            {
              "content_id": "ep-55",
              "type": "episode",
              "title": "Orphan Episode",
              "air_date": "2026-06-11",
              "local_air_date": "2026-06-11"
            }
        """.trimIndent()

        val item = json.decodeFromString(CalendarItem.serializer(), payload)

        assertEquals("ep-55", item.detailContentId)
        assertFalse(item.watched)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.calendar.*"
```

Expected: compilation failure of the test source set — `Unresolved reference: CalendarResponse` / `CalendarItem` / `CalendarBadge` (the `model/calendar` package does not exist yet). A compile failure of the new test is the failing state for this step.

- [ ] **Step 3: Implementation**

Create `shared/src/commonMain/kotlin/com/continuum/app/model/calendar/CalendarModels.kt`:

```kotlin
package com.continuum.app.model.calendar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Server filter presets for GET /api/v1/calendar. Web exposes Following / Trending / All. */
object CalendarFilter {
    const val All = "all"
    const val Everything = "everything"
    const val Following = "following"
    const val Favorites = "favorites"
    const val Watchlist = "watchlist"
    const val Popular = "popular"
    const val Trending = "trending"
}

object CalendarItemType {
    const val Movie = "movie"
    const val Episode = "episode"
}

object CalendarBadge {
    const val SeriesPremiere = "series_premiere"
    const val SeasonPremiere = "season_premiere"
    const val Finale = "finale"
}

@Serializable
data class CalendarResponse(
    val events: List<CalendarDay> = emptyList(),
)

@Serializable
data class CalendarDay(
    val date: String,
    val items: List<CalendarItem> = emptyList(),
)

@Serializable
data class CalendarItem(
    @SerialName("content_id") val contentId: String,
    val type: String,
    val title: String,
    @SerialName("episode_title") val episodeTitle: String? = null,
    @SerialName("series_id") val seriesId: String? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("air_date") val airDate: String,
    @SerialName("air_time") val airTime: String? = null,
    @SerialName("air_at") val airAt: String? = null,
    @SerialName("air_timezone") val airTimezone: String? = null,
    @SerialName("local_air_date") val localAirDate: String,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("poster_thumbhash") val posterThumbhash: String? = null,
    val watched: Boolean = false,
    val badges: List<String> = emptyList(),
) {
    val isEpisode: Boolean get() = type == CalendarItemType.Episode

    /** Detail-route target: the series page for episodes, the item itself otherwise. */
    val detailContentId: String get() = if (isEpisode) seriesId ?: contentId else contentId
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.calendar.*"
```

Expected: both tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/calendar/CalendarModels.kt shared/src/commonTest/kotlin/com/continuum/app/model/calendar/CalendarModelsSerializationTest.kt && git commit -m "Add shared calendar response models

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task C2: Shared CalendarApi + CalendarRepository + DI registration

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/network/api/CalendarApi.kt`
- Create: `shared/src/commonMain/kotlin/com/continuum/app/repository/CalendarRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt`
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/repository/CalendarRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Create `shared/src/commonTest/kotlin/com/continuum/app/repository/CalendarRepositoryTest.kt`:

```kotlin
package com.continuum.app.repository

import com.continuum.app.model.calendar.CalendarDay
import com.continuum.app.model.calendar.CalendarFilter
import com.continuum.app.model.calendar.CalendarResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.CalendarApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarRepositoryTest {

    @Test
    fun `passes query arguments through to the api`() = runTest {
        val response = CalendarResponse(events = listOf(CalendarDay(date = "2026-06-08")))
        val api = RecordingCalendarApi(ApiResult.Success(response))
        val repository = CalendarRepository(api)

        val result = repository.getCalendar(
            start = "2026-06-08",
            end = "2026-06-14",
            filter = CalendarFilter.Following,
            libraryId = 3,
            timezone = "Europe/Amsterdam",
        )

        assertEquals(ApiResult.Success(response), result)
        assertEquals(
            listOf("2026-06-08|2026-06-14|following|3|Europe/Amsterdam"),
            api.calls,
        )
    }

    @Test
    fun `propagates api errors unchanged`() = runTest {
        val error = ApiResult.Error(code = 400, error = "bad_request", message = "span too large")
        val api = RecordingCalendarApi(error)
        val repository = CalendarRepository(api)

        val result = repository.getCalendar(start = "2026-06-08", end = "2026-06-14")

        assertEquals(error, result)
        assertEquals(listOf("2026-06-08|2026-06-14|all|null|null"), api.calls)
    }
}

private class RecordingCalendarApi(
    private val result: ApiResult<CalendarResponse>,
) : CalendarApi {

    val calls = mutableListOf<String>()

    override suspend fun getCalendar(
        start: String,
        end: String,
        filter: String,
        libraryId: Int?,
        timezone: String?,
    ): ApiResult<CalendarResponse> {
        calls += "$start|$end|$filter|$libraryId|$timezone"
        return result
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.CalendarRepositoryTest"
```

Expected: compilation failure — `Unresolved reference: CalendarApi` and `Unresolved reference: CalendarRepository`.

- [ ] **Step 3: Implementation**

Create `shared/src/commonMain/kotlin/com/continuum/app/network/api/CalendarApi.kt` (interface + default impl, matching the `RequestsApi` idiom so repository/ViewModel tests can fake the transport):

```kotlin
package com.continuum.app.network.api

import com.continuum.app.model.calendar.CalendarFilter
import com.continuum.app.model.calendar.CalendarResponse
import com.continuum.app.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Calendar / upcoming endpoint. Kept behind an interface so repository and
 * ViewModel tests can fake the transport, matching the RequestsApi shape.
 */
interface CalendarApi {

    /**
     * GET /api/v1/calendar — max 31-day span. Dates are ISO "YYYY-MM-DD";
     * [timezone] is an IANA id used by the server to compute local air dates.
     */
    suspend fun getCalendar(
        start: String,
        end: String,
        filter: String = CalendarFilter.All,
        libraryId: Int? = null,
        timezone: String? = null,
    ): ApiResult<CalendarResponse>
}

class DefaultCalendarApi(private val client: HttpClient) : CalendarApi {

    override suspend fun getCalendar(
        start: String,
        end: String,
        filter: String,
        libraryId: Int?,
        timezone: String?,
    ): ApiResult<CalendarResponse> = safeApiCall {
        client.get("/api/v1/calendar") {
            parameter("start", start)
            parameter("end", end)
            parameter("filter", filter)
            parameter("library_id", libraryId)
            parameter("timezone", timezone)
        }
    }
}
```

(Ktor's `parameter()` skips null values, so `library_id`/`timezone` are omitted when null — same as `RequestsApi.search`'s `media_type` handling. `safeApiCall` is the shared helper in `network/api/AuthApi.kt`.)

Create `shared/src/commonMain/kotlin/com/continuum/app/repository/CalendarRepository.kt`:

```kotlin
package com.continuum.app.repository

import com.continuum.app.model.calendar.CalendarFilter
import com.continuum.app.model.calendar.CalendarResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.CalendarApi

/** Thin pass-through over [CalendarApi]; the calendar holds no client-side cache state. */
class CalendarRepository(private val api: CalendarApi) {

    suspend fun getCalendar(
        start: String,
        end: String,
        filter: String = CalendarFilter.All,
        libraryId: Int? = null,
        timezone: String? = null,
    ): ApiResult<CalendarResponse> = api.getCalendar(start, end, filter, libraryId, timezone)
}
```

Modify `shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt` (the file uses `import com.continuum.app.network.api.*`, so no new import is needed):

```kotlin
// OLD
    single<RequestsApi> { DefaultRequestsApi(get()) }
// NEW
    single<RequestsApi> { DefaultRequestsApi(get()) }
    single<CalendarApi> { DefaultCalendarApi(get()) }
```

Modify `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt` — add the import:

```kotlin
// OLD
import com.continuum.app.repository.CatalogRepository
// NEW
import com.continuum.app.repository.CalendarRepository
import com.continuum.app.repository.CatalogRepository
```

and the registration:

```kotlin
// OLD
    single { CatalogRepository(get()) }
// NEW
    single { CatalogRepository(get()) }
    single { CalendarRepository(get()) }
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.CalendarRepositoryTest" && ./gradlew :shared:testDebugUnitTest
```

Expected: new tests pass and the full shared suite stays green.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/network/api/CalendarApi.kt shared/src/commonMain/kotlin/com/continuum/app/repository/CalendarRepository.kt shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt shared/src/commonTest/kotlin/com/continuum/app/repository/CalendarRepositoryTest.kt && git commit -m "Add calendar API and repository

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task C3: Shared CalendarViewModel with dependency-free week math

The shared module has **no kotlinx-datetime dependency** (verified: `shared/build.gradle.kts` commonMain deps are ktor/serialization/coroutines/koin/lifecycle only), so week math is implemented with epoch-day arithmetic over ISO date strings in a small commonMain util. "Today" and the IANA timezone are injected by the platform (no `Clock.System`-style untestable defaults).

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/util/IsoDate.kt`
- Create: `shared/src/commonMain/kotlin/com/continuum/app/viewmodel/CalendarViewModel.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/util/IsoDateTest.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/viewmodel/CalendarViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

Create `shared/src/commonTest/kotlin/com/continuum/app/util/IsoDateTest.kt`:

```kotlin
package com.continuum.app.util

import kotlin.test.Test
import kotlin.test.assertEquals

class IsoDateTest {

    @Test
    fun `epoch day round trips`() {
        assertEquals(0L, IsoDate.toEpochDay("1970-01-01"))
        assertEquals("1970-01-01", IsoDate.fromEpochDay(0L))
        assertEquals("2026-06-12", IsoDate.fromEpochDay(IsoDate.toEpochDay("2026-06-12")))
    }

    @Test
    fun `plusDays crosses month leap and year boundaries`() {
        assertEquals("2024-02-29", IsoDate.plusDays("2024-02-28", 1))
        assertEquals("2026-01-05", IsoDate.plusDays("2025-12-29", 7))
        assertEquals("2025-12-29", IsoDate.plusDays("2026-01-05", -7))
    }

    @Test
    fun `week starts on monday`() {
        // 2026-06-12 is a Friday; 1970-01-01 was a Thursday.
        assertEquals(4, IsoDate.isoDayOfWeek("1970-01-01"))
        assertEquals(5, IsoDate.isoDayOfWeek("2026-06-12"))
        assertEquals("2026-06-08", IsoDate.weekStart("2026-06-12"))
        assertEquals("2026-06-08", IsoDate.weekStart("2026-06-08")) // Monday is its own week start
        assertEquals("2026-06-08", IsoDate.weekStart("2026-06-14")) // Sunday belongs to the preceding Monday
    }
}
```

Create `shared/src/commonTest/kotlin/com/continuum/app/viewmodel/CalendarViewModelTest.kt` (same harness as `RequestsViewModelTest`: `Dispatchers.setMain(UnconfinedTestDispatcher())`, fake API behind the real repository):

```kotlin
package com.continuum.app.viewmodel

import com.continuum.app.model.calendar.CalendarDay
import com.continuum.app.model.calendar.CalendarFilter
import com.continuum.app.model.calendar.CalendarItem
import com.continuum.app.model.calendar.CalendarItemType
import com.continuum.app.model.calendar.CalendarResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.CalendarApi
import com.continuum.app.repository.CalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(api: FakeCalendarApi, today: String = "2026-06-12") = CalendarViewModel(
        repository = CalendarRepository(api),
        timezoneId = "Europe/Amsterdam",
        todayProvider = { today },
    )

    @Test
    fun `loads the monday-anchored week containing today on init`() = runTest(dispatcher) {
        val day = CalendarDay(date = "2026-06-09", items = listOf(stubItem("m1")))
        val api = FakeCalendarApi(ApiResult.Success(CalendarResponse(listOf(day))))

        val state = viewModel(api).uiState.value

        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("2026-06-12", state.today)
        assertEquals("2026-06-08", state.weekStart)
        assertEquals("2026-06-14", state.weekEnd)
        assertEquals(7, state.weekDates.size)
        assertEquals("2026-06-08", state.weekDates.first())
        assertEquals("2026-06-14", state.weekDates.last())
        assertTrue(state.isCurrentWeek)
        assertEquals(listOf(stubItem("m1")), state.itemsFor("2026-06-09"))
        assertTrue(state.itemsFor("2026-06-10").isEmpty())
        assertEquals(
            listOf(CalendarCall("2026-06-08", "2026-06-14", CalendarFilter.Following, null, "Europe/Amsterdam")),
            api.calls,
        )
    }

    @Test
    fun `next and prev week shift the anchor by seven days and reload`() = runTest(dispatcher) {
        val api = FakeCalendarApi(ApiResult.Success(CalendarResponse()))
        val vm = viewModel(api)

        vm.nextWeek()
        assertEquals("2026-06-15", vm.uiState.value.weekStart)
        assertEquals("2026-06-21", vm.uiState.value.weekEnd)
        assertFalse(vm.uiState.value.isCurrentWeek)

        vm.prevWeek()
        assertEquals("2026-06-08", vm.uiState.value.weekStart)

        assertEquals(
            listOf("2026-06-08", "2026-06-15", "2026-06-08"),
            api.calls.map { it.start },
        )
    }

    @Test
    fun `goToToday returns to the current week and skips reload when already there`() = runTest(dispatcher) {
        val api = FakeCalendarApi(ApiResult.Success(CalendarResponse()))
        val vm = viewModel(api)

        vm.goToToday() // already on the current week — no extra call
        assertEquals(1, api.calls.size)

        vm.nextWeek()
        vm.goToToday()
        assertEquals("2026-06-08", vm.uiState.value.weekStart)
        assertEquals(3, api.calls.size)
    }

    @Test
    fun `setFilter triggers a reload with the new preset`() = runTest(dispatcher) {
        val api = FakeCalendarApi(ApiResult.Success(CalendarResponse()))
        val vm = viewModel(api)

        vm.setFilter(CalendarFilter.Trending)

        assertEquals(CalendarFilter.Trending, vm.uiState.value.filter)
        assertEquals(CalendarFilter.Trending, api.calls.last().filter)
        assertEquals(2, api.calls.size)

        vm.setFilter(CalendarFilter.Trending) // no-op when unchanged
        assertEquals(2, api.calls.size)
    }

    @Test
    fun `setLibrary triggers a reload scoped to the library`() = runTest(dispatcher) {
        val api = FakeCalendarApi(ApiResult.Success(CalendarResponse()))
        val vm = viewModel(api)

        vm.setLibrary(3)

        assertEquals(3, vm.uiState.value.libraryId)
        assertEquals(3, api.calls.last().libraryId)

        vm.setLibrary(null)
        assertNull(api.calls.last().libraryId)
    }

    @Test
    fun `error surfaces the server message with fallback for blank messages`() = runTest(dispatcher) {
        val api = FakeCalendarApi(ApiResult.Error(code = 500, error = "internal", message = ""))

        assertEquals("Failed to load calendar", viewModel(api).uiState.value.error)
    }

    @Test
    fun `network failure surfaces the standard network copy`() = runTest(dispatcher) {
        val api = FakeCalendarApi(ApiResult.NetworkError(IllegalStateException("offline")))

        assertEquals("Network error. Check your connection.", viewModel(api).uiState.value.error)
    }
}

private data class CalendarCall(
    val start: String,
    val end: String,
    val filter: String,
    val libraryId: Int?,
    val timezone: String?,
)

private class FakeCalendarApi(
    var result: ApiResult<CalendarResponse>,
) : CalendarApi {

    val calls = mutableListOf<CalendarCall>()

    override suspend fun getCalendar(
        start: String,
        end: String,
        filter: String,
        libraryId: Int?,
        timezone: String?,
    ): ApiResult<CalendarResponse> {
        calls += CalendarCall(start, end, filter, libraryId, timezone)
        return result
    }
}

private fun stubItem(id: String): CalendarItem = CalendarItem(
    contentId = id,
    type = CalendarItemType.Movie,
    title = "Title $id",
    airDate = "2026-06-09",
    localAirDate = "2026-06-09",
)
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.util.IsoDateTest" --tests "com.continuum.app.viewmodel.CalendarViewModelTest"
```

Expected: compilation failure — `Unresolved reference: IsoDate` and `Unresolved reference: CalendarViewModel`.

- [ ] **Step 3: Implementation**

Create `shared/src/commonMain/kotlin/com/continuum/app/util/IsoDate.kt`:

```kotlin
package com.continuum.app.util

/**
 * Minimal proleptic-Gregorian day arithmetic over ISO "YYYY-MM-DD" strings.
 *
 * The shared module deliberately has no kotlinx-datetime dependency; the
 * calendar feature only needs day-of-week and plus/minus-days math, so the
 * classic civil-date <-> epoch-day algorithms (Howard Hinnant's
 * days_from_civil / civil_from_days) keep this dependency-free and fully
 * testable in commonTest.
 */
object IsoDate {

    /** Days since 1970-01-01 for an ISO "YYYY-MM-DD" string. */
    fun toEpochDay(iso: String): Long {
        val parts = iso.split("-")
        val y = parts[0].toInt()
        val m = parts[1].toInt()
        val d = parts[2].toInt()
        val yAdj = if (m <= 2) y - 1 else y
        val era = (if (yAdj >= 0) yAdj else yAdj - 399) / 400
        val yoe = yAdj - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    /** ISO "YYYY-MM-DD" for days since 1970-01-01. */
    fun fromEpochDay(epochDay: Long): String {
        val z = epochDay + 719468L
        val era = (if (z >= 0) z else z - 146096L) / 146097L
        val doe = z - era * 146097L
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val year = (if (m <= 2) y + 1 else y).toInt()
        return "${pad(year, 4)}-${pad(m, 2)}-${pad(d, 2)}"
    }

    fun plusDays(iso: String, days: Long): String = fromEpochDay(toEpochDay(iso) + days)

    /** ISO-8601 day of week: Monday = 1 .. Sunday = 7. 1970-01-01 was a Thursday. */
    fun isoDayOfWeek(iso: String): Int {
        val epochDay = toEpochDay(iso)
        return ((((epochDay + 3) % 7) + 7) % 7 + 1).toInt()
    }

    /** The Monday of the week containing [iso]. */
    fun weekStart(iso: String): String = plusDays(iso, -(isoDayOfWeek(iso) - 1).toLong())

    private fun pad(value: Int, width: Int): String = value.toString().padStart(width, '0')
}
```

Create `shared/src/commonMain/kotlin/com/continuum/app/viewmodel/CalendarViewModel.kt`:

```kotlin
package com.continuum.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.calendar.CalendarDay
import com.continuum.app.model.calendar.CalendarFilter
import com.continuum.app.model.calendar.CalendarItem
import com.continuum.app.network.ApiResult
import com.continuum.app.network.errorMessage
import com.continuum.app.repository.CalendarRepository
import com.continuum.app.util.IsoDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** ISO "YYYY-MM-DD" for the platform's current date. */
    val today: String = "",
    /** Monday anchoring the visible week, ISO "YYYY-MM-DD". */
    val weekStart: String = "",
    val filter: String = CalendarFilter.Following,
    val libraryId: Int? = null,
    /** Server-grouped day buckets for the visible week. */
    val days: List<CalendarDay> = emptyList(),
    val error: String? = null,
) {
    /** The 7 ISO dates of the visible week, Monday first. */
    val weekDates: List<String>
        get() = if (weekStart.isBlank()) emptyList() else (0L..6L).map { IsoDate.plusDays(weekStart, it) }

    val weekEnd: String
        get() = if (weekStart.isBlank()) "" else IsoDate.plusDays(weekStart, 6)

    val isCurrentWeek: Boolean
        get() = today.isNotBlank() && weekStart == IsoDate.weekStart(today)

    val hasAnyItems: Boolean
        get() = days.any { it.items.isNotEmpty() }

    fun itemsFor(date: String): List<CalendarItem> =
        days.firstOrNull { it.date == date }?.items.orEmpty()
}

/**
 * Shared calendar/upcoming ViewModel (pattern: RequestsViewModels). The
 * platform supplies "today" and the IANA timezone so week math stays
 * deterministic in commonTest — no Clock.System defaults baked in.
 */
class CalendarViewModel(
    private val repository: CalendarRepository,
    private val timezoneId: String,
    private val todayProvider: () -> String,
) : ViewModel() {

    private val _uiState: MutableStateFlow<CalendarUiState>
    val uiState: StateFlow<CalendarUiState>

    init {
        val today = todayProvider()
        _uiState = MutableStateFlow(
            CalendarUiState(today = today, weekStart = IsoDate.weekStart(today)),
        )
        uiState = _uiState.asStateFlow()
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetch()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            fetch()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun nextWeek() = moveWeek(7)

    fun prevWeek() = moveWeek(-7)

    fun goToToday() {
        val today = todayProvider()
        val weekStart = IsoDate.weekStart(today)
        if (weekStart == _uiState.value.weekStart) return
        _uiState.update { it.copy(today = today, weekStart = weekStart) }
        load()
    }

    fun setFilter(filter: String) {
        if (filter == _uiState.value.filter) return
        _uiState.update { it.copy(filter = filter) }
        load()
    }

    fun setLibrary(libraryId: Int?) {
        if (libraryId == _uiState.value.libraryId) return
        _uiState.update { it.copy(libraryId = libraryId) }
        load()
    }

    private fun moveWeek(days: Long) {
        _uiState.update { it.copy(weekStart = IsoDate.plusDays(it.weekStart, days)) }
        load()
    }

    private suspend fun fetch() {
        val state = _uiState.value
        val result = repository.getCalendar(
            start = state.weekStart,
            end = state.weekEnd,
            filter = state.filter,
            libraryId = state.libraryId,
            timezone = timezoneId,
        )
        when (result) {
            is ApiResult.Success -> _uiState.update {
                it.copy(isLoading = false, days = result.data.events, error = null)
            }
            is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                it.copy(isLoading = false, error = result.errorMessage("Failed to load calendar"))
            }
        }
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :shared:testDebugUnitTest
```

Expected: `IsoDateTest`, `CalendarViewModelTest`, and the full shared suite pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/util/IsoDate.kt shared/src/commonMain/kotlin/com/continuum/app/viewmodel/CalendarViewModel.kt shared/src/commonTest/kotlin/com/continuum/app/util/IsoDateTest.kt shared/src/commonTest/kotlin/com/continuum/app/viewmodel/CalendarViewModelTest.kt && git commit -m "Add shared CalendarViewModel with dependency-free week math

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task C4: Mobile CalendarScreen + route + menu entry point

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/calendar/CalendarScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/components/MainAppTopBar.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/home/HomeScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt`
- Test: manual checklist (UI only; the logic is covered by Task 3's shared tests)

- [ ] **Step 1: Write the failing test** — manual-check note: Compose screens here have no unit-test harness in androidApp; all calendar logic (week math, filter reload, error copy) is already unit-tested in Task 3. The verification gate for this task is `:androidApp:assembleDebug` plus the manual checklist in Step 4.

- [ ] **Step 2: Run test to verify it fails** — N/A (manual-check task). Optionally confirm the route does not exist yet: `grep -n "Calendar" androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt` returns nothing.

- [ ] **Step 3: Implementation**

**3a. `Routes.kt`** — add the route in the personal-data section:

```kotlin
// OLD
    // --- Personal data ---
    data object Favorites : Route("favorites")
// NEW
    // --- Calendar / upcoming ---
    data object Calendar : Route("calendar")

    // --- Personal data ---
    data object Favorites : Route("favorites")
```

**3b. Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/calendar/CalendarScreen.kt`** (full file):

```kotlin
package com.continuum.app.android.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.components.ContinuumTopBar
import com.continuum.app.android.ui.components.EmptyStateView
import com.continuum.app.android.ui.components.ErrorView
import com.continuum.app.android.ui.components.LoadingIndicator
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.model.calendar.CalendarBadge
import com.continuum.app.model.calendar.CalendarFilter
import com.continuum.app.model.calendar.CalendarItem
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.PersonalDataRepository
import com.continuum.app.viewmodel.CalendarViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Calendar / upcoming screen: week strip (7 day chips + prev/next + Today),
 * filter preset row (Following / Trending / All), library dropdown when the
 * user has more than one library, and a day-grouped list of event cards.
 * Taps route to the existing item-detail screen (series for episodes).
 */
@Composable
fun CalendarScreen(
    onBackClick: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Library list for the dropdown — same source MainScreen uses for
    // media-mode capabilities (PersonalDataRepository.listUserLibraries).
    val personalDataRepository: PersonalDataRepository = koinInject()
    val libraries by produceState(initialValue = emptyList<UserLibrary>()) {
        value = when (val result = personalDataRepository.listUserLibraries()) {
            is ApiResult.Success -> result.data
            else -> emptyList()
        }
    }

    Scaffold(
        topBar = {
            ContinuumTopBar(
                title = "Calendar",
                onBackClick = onBackClick,
                actions = {
                    if (libraries.size > 1) {
                        LibraryDropdown(
                            libraries = libraries,
                            selectedLibraryId = state.libraryId,
                            onSelect = viewModel::setLibrary,
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            FilterPresetRow(
                selected = state.filter,
                onSelect = viewModel::setFilter,
            )
            WeekStrip(
                weekDates = state.weekDates,
                today = state.today,
                isCurrentWeek = state.isCurrentWeek,
                onPrevWeek = viewModel::prevWeek,
                onNextWeek = viewModel::nextWeek,
                onToday = viewModel::goToToday,
            )
            when {
                state.isLoading -> LoadingIndicator()
                state.error != null -> ErrorView(
                    message = state.error ?: "Something went wrong",
                    onRetry = viewModel::load,
                )
                !state.hasAnyItems -> EmptyStateView(
                    title = "Nothing scheduled",
                    subtitle = emptyCopy(state.filter),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.weekDates.forEach { date ->
                        val dayItems = state.itemsFor(date)
                        if (dayItems.isEmpty()) return@forEach
                        item(key = "header-$date") {
                            DayHeader(date = date, isToday = date == state.today)
                        }
                        items(dayItems, key = { "$date-${it.contentId}" }) { item ->
                            CalendarEventCard(
                                item = item,
                                onClick = { onItemClick(item.detailContentId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPresetRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val presets = listOf(
        CalendarFilter.Following to "Following",
        CalendarFilter.Trending to "Trending",
        CalendarFilter.All to "All",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun WeekStrip(
    weekDates: List<String>,
    today: String,
    isCurrentWeek: Boolean,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToday: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevWeek) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous week",
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                weekDates.forEach { date ->
                    DayChip(date = date, isToday = date == today)
                }
            }
            IconButton(onClick = onNextWeek) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next week",
                )
            }
        }
        if (!isCurrentWeek) {
            TextButton(
                onClick = onToday,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Today")
            }
        }
    }
}

@Composable
private fun DayChip(date: String, isToday: Boolean) {
    val localDate = remember(date) { LocalDate.parse(date) }
    val background =
        if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor =
        if (isToday) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = localDate.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
        Text(
            text = localDate.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
    }
}

@Composable
private fun DayHeader(date: String, isToday: Boolean) {
    val localDate = remember(date) { LocalDate.parse(date) }
    Text(
        text = if (isToday) {
            "Today"
        } else {
            localDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun CalendarEventCard(
    item: CalendarItem,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.watched) 0.55f else 1f),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThumbhashImage(
                url = item.posterUrl,
                thumbhash = item.posterThumbhash,
                contentDescription = item.title,
                modifier = Modifier
                    .width(52.dp)
                    .height(78.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.isEpisode) {
                    val marker = listOfNotNull(
                        item.seasonNumber?.let { s ->
                            item.episodeNumber?.let { e -> "S${s}E$e" }
                        },
                        item.episodeTitle,
                    ).joinToString(" \u2022 ")
                    if (marker.isNotBlank()) {
                        Text(
                            text = marker,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                item.airTime?.takeIf { it.isNotBlank() }?.let { airTime ->
                    Text(
                        text = airTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.badges.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.badges.mapNotNull(::badgeLabel).forEach { label ->
                            BadgeChip(label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun LibraryDropdown(
    libraries: List<UserLibrary>,
    selectedLibraryId: Int?,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(libraries.firstOrNull { it.id == selectedLibraryId }?.name ?: "All libraries")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("All libraries") },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            libraries.forEach { library ->
                DropdownMenuItem(
                    text = { Text(library.name) },
                    onClick = {
                        expanded = false
                        onSelect(library.id)
                    },
                )
            }
        }
    }
}

private fun badgeLabel(badge: String): String? = when (badge) {
    CalendarBadge.SeriesPremiere -> "Series Premiere"
    CalendarBadge.SeasonPremiere -> "Season Premiere"
    CalendarBadge.Finale -> "Finale"
    else -> null
}

private fun emptyCopy(filter: String): String = when (filter) {
    CalendarFilter.Following -> "Nothing airing this week from shows you follow. Try Trending or All."
    CalendarFilter.Trending -> "No trending releases this week."
    else -> "Nothing scheduled this week."
}
```

**3c. `AndroidModule.kt`** — register the ViewModel (platform supplies today + timezone; fully-qualified name keeps the edit self-contained, matching the file's existing FQ style for reader/audiobook VMs):

```kotlin
// OLD
    viewModel { RequestsViewModel(get()) }
// NEW
    viewModel { RequestsViewModel(get()) }
    viewModel {
        com.continuum.app.viewmodel.CalendarViewModel(
            repository = get(),
            timezoneId = java.util.TimeZone.getDefault().id,
            todayProvider = { java.time.LocalDate.now().toString() },
        )
    }
```

**3d. `AppNavigation.kt`** — add the import (after the `PersonalListsScreen` import):

```kotlin
// OLD
import com.continuum.app.android.ui.screens.personal.PersonalListsScreen
// NEW
import com.continuum.app.android.ui.screens.calendar.CalendarScreen
import com.continuum.app.android.ui.screens.personal.PersonalListsScreen
```

and the nav-graph entry (after the `Route.PersonalLists` composable block):

```kotlin
// OLD
        composable(Route.PersonalLists.route) {
            PersonalListsScreen(
                onBackClick = { navController.popBackStack() },
                onItemClick = { contentId ->
                    navController.navigate(Route.ItemDetail(contentId).route)
                },
            )
        }
// NEW
        composable(Route.PersonalLists.route) {
            PersonalListsScreen(
                onBackClick = { navController.popBackStack() },
                onItemClick = { contentId ->
                    navController.navigate(Route.ItemDetail(contentId).route)
                },
            )
        }
        composable(Route.Calendar.route) {
            CalendarScreen(
                onBackClick = { navController.popBackStack() },
                onItemClick = { contentId ->
                    navController.navigate(Route.ItemDetail(contentId).route)
                },
            )
        }
```

**3e. `MainAppTopBar.kt`** — entry point in the same dropdown surface as "Favorites & Watchlist" (nullable default keeps other call sites compiling). Add to the signature:

```kotlin
// OLD
    onPersonalListsClick: () -> Unit,
    onRequestsClick: (() -> Unit)? = null,
// NEW
    onPersonalListsClick: () -> Unit,
    onCalendarClick: (() -> Unit)? = null,
    onRequestsClick: (() -> Unit)? = null,
```

Add the menu item with the CalendarMonth icon (after the "Favorites & Watchlist" item; `compose.materialIconsExtended` is already a dependency of androidApp). Also add `import androidx.compose.material.icons.outlined.CalendarMonth` next to the existing `androidx.compose.material.icons.outlined.Person` import:

```kotlin
// OLD
                        DropdownMenuItem(
                            text = { Text("Favorites & Watchlist") },
                            onClick = {
                                menuExpanded = false
                                onPersonalListsClick()
                            },
                        )
// NEW
                        DropdownMenuItem(
                            text = { Text("Favorites & Watchlist") },
                            onClick = {
                                menuExpanded = false
                                onPersonalListsClick()
                            },
                        )
                        if (onCalendarClick != null) {
                            DropdownMenuItem(
                                text = { Text("Calendar") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarMonth,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onCalendarClick()
                                },
                            )
                        }
```

**3f. `HomeScreen.kt`** — the Video tab paints its own chrome (`HomeProfileMenu`), so thread the callback through. Add `import androidx.compose.material.icons.outlined.CalendarMonth` next to the existing `androidx.compose.material.icons.outlined.Person` import, then three signature edits and two call-site edits:

```kotlin
// OLD (HomeScreen signature — unique via the modifier line)
    onSearchClick: () -> Unit,
    onPersonalListsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwitchProfileClick: () -> Unit,
    onSwitchServerClick: () -> Unit,
    modifier: Modifier = Modifier,
// NEW
    onSearchClick: () -> Unit,
    onPersonalListsClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwitchProfileClick: () -> Unit,
    onSwitchServerClick: () -> Unit,
    modifier: Modifier = Modifier,
```

```kotlin
// OLD (HomeFloatingChrome call inside HomeScreen)
        HomeFloatingChrome(
            scrollProgress = scrollProgress,
            activeProfile = activeProfile,
            onSearchClick = onSearchClick,
            onPersonalListsClick = onPersonalListsClick,
// NEW
        HomeFloatingChrome(
            scrollProgress = scrollProgress,
            activeProfile = activeProfile,
            onSearchClick = onSearchClick,
            onPersonalListsClick = onPersonalListsClick,
            onCalendarClick = onCalendarClick,
```

```kotlin
// OLD (HomeFloatingChrome signature)
private fun HomeFloatingChrome(
    scrollProgress: Float,
    activeProfile: Profile?,
    onSearchClick: () -> Unit,
    onPersonalListsClick: () -> Unit,
// NEW
private fun HomeFloatingChrome(
    scrollProgress: Float,
    activeProfile: Profile?,
    onSearchClick: () -> Unit,
    onPersonalListsClick: () -> Unit,
    onCalendarClick: () -> Unit,
```

```kotlin
// OLD (HomeProfileMenu call inside HomeFloatingChrome)
            HomeProfileMenu(
                activeProfile = activeProfile,
                onPersonalListsClick = onPersonalListsClick,
// NEW
            HomeProfileMenu(
                activeProfile = activeProfile,
                onPersonalListsClick = onPersonalListsClick,
                onCalendarClick = onCalendarClick,
```

```kotlin
// OLD (HomeProfileMenu signature)
private fun HomeProfileMenu(
    activeProfile: Profile?,
    onPersonalListsClick: () -> Unit,
// NEW
private fun HomeProfileMenu(
    activeProfile: Profile?,
    onPersonalListsClick: () -> Unit,
    onCalendarClick: () -> Unit,
```

and the menu item inside `HomeProfileMenu`'s `DropdownMenu`:

```kotlin
// OLD
            DropdownMenuItem(
                text = { Text("Favorites & Watchlist") },
                onClick = {
                    menuExpanded = false
                    onPersonalListsClick()
                },
            )
// NEW
            DropdownMenuItem(
                text = { Text("Favorites & Watchlist") },
                onClick = {
                    menuExpanded = false
                    onPersonalListsClick()
                },
            )
            DropdownMenuItem(
                text = { Text("Calendar") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onCalendarClick()
                },
            )
```

**3g. `MainScreen.kt`** — wire navigation at both call sites:

```kotlin
// OLD (HomeScreen call, Tab.Video branch — unique via activeProfile context)
                            activeProfile = headerState.activeProfile,
                            onSearchClick = { navController.navigate(Route.Search().route) },
                            onPersonalListsClick = { navController.navigate(Route.PersonalLists.route) },
                            onSettingsClick = { navController.navigate(Route.Settings.route) },
                            onSwitchProfileClick = {
                                navController.navigate(Route.ProfileSelection.route)
                            },
                            onSwitchServerClick = {
                                navController.navigate(Route.ServerList.route)
                            },
                        )
                    }
                    Tab.Audio -> {
// NEW
                            activeProfile = headerState.activeProfile,
                            onSearchClick = { navController.navigate(Route.Search().route) },
                            onPersonalListsClick = { navController.navigate(Route.PersonalLists.route) },
                            onCalendarClick = { navController.navigate(Route.Calendar.route) },
                            onSettingsClick = { navController.navigate(Route.Settings.route) },
                            onSwitchProfileClick = {
                                navController.navigate(Route.ProfileSelection.route)
                            },
                            onSwitchServerClick = {
                                navController.navigate(Route.ServerList.route)
                            },
                        )
                    }
                    Tab.Audio -> {
```

```kotlin
// OLD (Downloads tab MainAppTopBar)
                    onPersonalListsClick = { navController.navigate(Route.PersonalLists.route) },
                    onRequestsClick = { navController.navigate(Route.Requests.route) },
// NEW
                    onPersonalListsClick = { navController.navigate(Route.PersonalLists.route) },
                    onCalendarClick = { navController.navigate(Route.Calendar.route) },
                    onRequestsClick = { navController.navigate(Route.Requests.route) },
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :shared:testDebugUnitTest && ./gradlew :androidApp:assembleDebug
```

Expected: shared suite green, androidApp assembles.

Manual checklist (phone/emulator):
- Home tab → profile menu → "Calendar" entry (CalendarMonth icon) opens the screen; same from the Downloads tab top-bar menu.
- Week strip shows Mon–Sun of the current week with today highlighted; prev/next shift by exactly 7 days; "Today" appears only off the current week and returns to it.
- Presets Following / Trending / All reload the list; each preset shows its own empty copy when there's nothing.
- Library dropdown appears only with >1 library; "All libraries" + per-library filtering works.
- Event cards: poster with thumbhash placeholder, `SxEy • Episode Title` line for episodes, air time, premiere/finale badge chips, dimmed when watched.
- Tapping an episode opens the **series** detail; tapping a movie opens the movie detail; back returns to the calendar.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/calendar/CalendarScreen.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/components/MainAppTopBar.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/home/HomeScreen.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt && git commit -m "Add mobile calendar screen with week navigation and presets

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task C5: TV "Coming this week" home row

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/home/TvUpcomingViewModel.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/home/TvHomeScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`
- Test: manual check (client-side row; mapping logic is trivial and the repository/VM layers are covered by Tasks 2–3)

- [ ] **Step 1: Write the failing test** — manual-check note: the TV row is a thin fetch-once state holder plus a `TvMediaRow` render; there is no androidTvApp unit-test harness for Compose TV screens. Verification is `:androidTvApp:assembleDebug` plus the manual checklist in Step 4.

- [ ] **Step 2: Run test to verify it fails** — N/A (manual-check task).

- [ ] **Step 3: Implementation**

**3a. Create `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/home/TvUpcomingViewModel.kt`** (full file):

```kotlin
package com.continuum.app.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.calendar.CalendarFilter
import com.continuum.app.model.calendar.CalendarItem
import com.continuum.app.model.calendar.CalendarItemType
import com.continuum.app.model.section.SectionItem
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.TimeZone

/**
 * Client-side "Coming this week" row for TV home. Fetches once on home load:
 * today..today+6 with filter=following, falling back to filter=all when the
 * following set has nothing airing this week. Failures resolve to an empty
 * list, which simply hides the row (TvMediaRow returns early when empty).
 * No week paging on TV.
 */
class TvUpcomingViewModel(
    private val repository: CalendarRepository,
) : ViewModel() {

    private val _items = MutableStateFlow<List<SectionItem>>(emptyList())
    val items: StateFlow<List<SectionItem>> = _items.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val start = today.toString()
            val end = today.plusDays(6).toString()
            val timezone = TimeZone.getDefault().id

            val following = fetch(start, end, CalendarFilter.Following, timezone)
            val resolved = following.ifEmpty { fetch(start, end, CalendarFilter.All, timezone) }

            _items.value = resolved
                .distinctBy { it.detailContentId }
                .map { it.toSectionItem() }
        }
    }

    private suspend fun fetch(
        start: String,
        end: String,
        filter: String,
        timezone: String,
    ): List<CalendarItem> = when (
        val result = repository.getCalendar(start = start, end = end, filter = filter, timezone = timezone)
    ) {
        is ApiResult.Success -> result.data.events.flatMap { it.items }
        else -> emptyList()
    }
}

/**
 * Adapter into the TvMediaRow card model. Episodes collapse onto their series
 * (detailContentId) so clicking a card opens the TV series detail screen.
 */
private fun CalendarItem.toSectionItem(): SectionItem = SectionItem(
    contentId = detailContentId,
    type = if (isEpisode) "series" else CalendarItemType.Movie,
    title = title,
    seriesId = seriesId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    posterUrl = posterUrl,
    posterThumbhash = posterThumbhash,
)
```

**3b. `AndroidTvModule.kt`** — register it (fully-qualified, single-hunk edit):

```kotlin
// OLD
    viewModel { HomeViewModel(get(), get()) }
// NEW
    viewModel { HomeViewModel(get(), get()) }
    viewModel { com.continuum.app.tv.ui.screens.home.TvUpcomingViewModel(get()) }
```

(`CalendarRepository` resolves from `repositoryModule`, registered in Task 2.)

**3c. `TvHomeScreen.kt`** — four edits. Add the import next to the existing `ResolvedSection` import:

```kotlin
// OLD
import com.continuum.app.model.section.ResolvedSection
// NEW
import com.continuum.app.model.section.ResolvedSection
import com.continuum.app.model.section.SectionItem
```

Collect the row in `TvHomeScreen`:

```kotlin
// OLD
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
// NEW
    viewModel: HomeViewModel = koinViewModel(),
    upcomingViewModel: TvUpcomingViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val upcomingItems by upcomingViewModel.items.collectAsState()
```

Pass it into the content composable:

```kotlin
// OLD
        else -> TvHomeContent(
            sections = visibleSections,
// NEW
        else -> TvHomeContent(
            sections = visibleSections,
            upcomingItems = upcomingItems,
```

```kotlin
// OLD
private fun TvHomeContent(
    sections: List<ResolvedSection>,
// NEW
private fun TvHomeContent(
    sections: List<ResolvedSection>,
    upcomingItems: List<SectionItem> = emptyList(),
```

Append the row **after** the server-section rows inside the `LazyColumn` (anchor includes the trailing doc comment to make the old string unique):

```kotlin
// OLD
                        },
                    )
                }
            }
        }
    }
}

/** Spec 3.1 — 40dp between cards, 60dp between rows. */
// NEW
                        },
                    )
                }

                // Client-side calendar row — appended after all server sections.
                if (upcomingItems.isNotEmpty()) {
                    item(key = "upcoming-week") {
                        TvMediaRow(
                            title = "Coming this week",
                            items = upcomingItems,
                            onItemClick = onItemClick,
                            startPadding = Spacing.safeArea,
                            endPadding = Spacing.safeArea,
                            itemSpacing = TvHomeItemSpacing,
                            rowTopPadding = 0.dp,
                            rowBottomPadding = 0.dp,
                        )
                    }
                }
            }
        }
    }
}

/** Spec 3.1 — 40dp between cards, 60dp between rows. */
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :shared:testDebugUnitTest && ./gradlew :androidTvApp:assembleDebug
```

Expected: shared suite green, androidTvApp assembles.

Manual checklist (TV emulator/device):
- "Coming this week" row appears at the bottom of TV home, after all server sections, using the standard poster row styling.
- With followed series airing this week the row reflects `filter=following`; with none, it falls back to all upcoming items.
- D-pad focus reaches the row; clicking an episode card opens the **series** detail, a movie card opens the movie detail.
- When the calendar request fails or returns nothing for both filters, the row is absent (no error surfaced on home).

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/home/TvUpcomingViewModel.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/home/TvHomeScreen.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt && git commit -m "Add TV coming-this-week home row

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

## Section P: Picture-in-picture (mobile)

### Task P1: PiP plumbing — PipController, manifest, MainActivity

**Files:**
- Create: androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PipController.kt
- Modify: androidApp/src/androidMain/AndroidManifest.xml
- Modify: androidApp/src/androidMain/kotlin/com/continuum/app/android/MainActivity.kt

(`PipController` is created here, not in Task P2, because `MainActivity` delegates to it — each task must compile on its own.)

- [ ] **Step 1: Write the failing test**

Not unit-testable: Activity/window-manager plumbing (`enterPictureInPictureMode`, `setPictureInPictureParams`, manifest flags) with no JVM-testable seam worth extracting at this size. Manual check (after Task P2 wires the player; for this task alone): app launches normally, rotation/keyboard behavior unchanged (the `configChanges` addition must not regress config handling), and no screen ever enters PiP yet (nothing calls `register`, so `shouldAutoEnter` stays false and the manual path is gated off).

- [ ] **Step 2: Run test to verify it fails**

n/a (no unit test). Establish a green baseline before touching anything:
```bash
./gradlew :androidApp:assembleDebug
```

- [ ] **Step 3: Implementation**

**3a. Manifest** — `androidApp/src/androidMain/AndroidManifest.xml` (lines 24–28). Add `supportsPictureInPicture` and `smallestScreenSize` (PiP resizes the window; without `smallestScreenSize` in `configChanges` the activity is recreated on PiP enter/exit, killing the composition).

Before:
```xml
      <activity
          android:name=".MainActivity"
          android:exported="true"
          android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
          android:windowSoftInputMode="adjustResize">
```
After:
```xml
      <activity
          android:name=".MainActivity"
          android:exported="true"
          android:supportsPictureInPicture="true"
          android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden"
          android:windowSoftInputMode="adjustResize">
```

**3b. New file** — `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PipController.kt`, full content:

```kotlin
package com.continuum.app.android.ui.screens.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton bridge between [com.continuum.app.android.MainActivity]'s
 * picture-in-picture callbacks and the video-player composition.
 *
 * PlayerScreen [register]s while the video-player route is composed and
 * [unregister]s on dispose, so PiP can only ever engage from the video
 * player — never from browse screens, the audio player, or readers
 * (unregister re-publishes params with auto-enter disabled on Android 12+).
 *
 * Every framework call is wrapped in `runCatching`: some OEM builds throw
 * from `enterPictureInPictureMode` / `setPictureInPictureParams`, and
 * devices may lack FEATURE_PICTURE_IN_PICTURE entirely.
 *
 * Threading: all mutating calls happen on the main thread (Activity
 * callbacks + composition effects), so plain fields are safe.
 */
object PipController {

    /**
     * Platform-legal aspect-ratio bounds for setAspectRatio — anything
     * outside [1:2.39, 2.39:1] throws IllegalArgumentException.
     */
    private const val MIN_ASPECT = 100.0 / 239.0 // 0.41841...
    private const val MAX_ASPECT = 2.39

    private val _isInPipMode = MutableStateFlow(false)

    /** True while the activity is in PiP. PlayerScreen hides all chrome while set. */
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    private var registered = false
    private var playingVideo = false
    private var aspectRatio = Rational(16, 9)

    /**
     * Invoked when the user dismisses the PiP window via its close (X)
     * button. The activity is merely stopped in that case — the player
     * route is NOT popped — so the registrant must stop playback itself.
     */
    private var onPipDismissed: (() -> Unit)? = null

    /** PlayerScreen entered composition. */
    fun register(activity: Activity?, onDismissed: () -> Unit) {
        registered = true
        onPipDismissed = onDismissed
        pushParams(activity)
    }

    /** PlayerScreen left composition. Re-publishes params with auto-enter off. */
    fun unregister(activity: Activity?) {
        registered = false
        playingVideo = false
        onPipDismissed = null
        aspectRatio = Rational(16, 9)
        pushParams(activity)
    }

    /** Mirror of the player's isPlaying — auto-enter only while video is rolling. */
    fun setPlaying(activity: Activity?, playing: Boolean) {
        if (playingVideo == playing) return
        playingVideo = playing
        pushParams(activity)
    }

    /**
     * Feed from Player.Listener.onVideoSizeChanged. Width should already
     * include the pixel aspect ratio (anamorphic sources). Out-of-bounds
     * ratios are clamped to the platform-legal boundary values.
     */
    fun setVideoSize(activity: Activity?, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val raw = width.toDouble() / height.toDouble()
        val newRatio = when {
            raw > MAX_ASPECT -> Rational(239, 100)
            raw < MIN_ASPECT -> Rational(100, 239)
            else -> Rational(width, height)
        }
        if (newRatio == aspectRatio) return
        aspectRatio = newRatio
        pushParams(activity)
    }

    /**
     * Manual-enter path for API 26–30, called from MainActivity.onUserLeaveHint.
     * On Android 12+ (S) the setAutoEnterEnabled param handles the home press
     * natively (and with a smoother transition), so this is a no-op there.
     */
    fun enterPipIfEligible(activity: Activity?) {
        if (activity == null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (!registered || !playingVideo) return
        runCatching { activity.enterPictureInPictureMode(buildParams()) }
    }

    /**
     * Forwarded from MainActivity.onPictureInPictureModeChanged.
     *
     * Dismissal detection (documented Android pattern): when the user
     * closes the PiP window with X, the activity receives this callback
     * with isInPip=false while it is being stopped — its lifecycle state
     * is only CREATED. When the user instead expands PiP back to full
     * screen, the activity is heading to the foreground and its state is
     * at least STARTED. Only the dismissal case must stop playback
     * (otherwise the ExoPlayer in ContinuumPlaybackService keeps playing
     * audio behind the closed window).
     */
    fun onPipModeChanged(isInPip: Boolean, lifecycleState: Lifecycle.State) {
        _isInPipMode.value = isInPip
        if (!isInPip && lifecycleState == Lifecycle.State.CREATED) {
            onPipDismissed?.invoke()
        }
    }

    private fun pushParams(activity: Activity?) {
        if (activity == null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching { activity.setPictureInPictureParams(buildParams()) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(registered && playingVideo)
        }
        return builder.build()
    }
}
```

No `setSourceRectHint` (deliberately skipped per design). No custom RemoteActions — the system PiP window picks up play/pause from the `ContinuumPlaybackService` MediaSession automatically.

**3c. MainActivity** — `androidApp/src/androidMain/kotlin/com/continuum/app/android/MainActivity.kt`. MainActivity exposes nothing to compose via CompositionLocals today, so the lightest bridge is the singleton `PipController.isInPipMode` StateFlow that PlayerScreen collects directly.

Add imports `android.content.res.Configuration` (with the android imports) and `com.continuum.app.android.ui.screens.player.PipController` (with the app imports).

Add two overrides between `onNewIntent` and `onStop`:

```kotlin
    /**
     * Manual PiP entry for API 26–30. On Android 12+ this is a no-op:
     * PictureInPictureParams.setAutoEnterEnabled handles the home press
     * natively (PipController guards the version split internally).
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PipController.enterPipIfEligible(this)
    }

    /**
     * Bridges the PiP mode flag into compose (PlayerScreen collects
     * PipController.isInPipMode to hide its chrome). The lifecycle state
     * is forwarded so the controller can distinguish "PiP window closed
     * with X" (state == CREATED → stop playback) from "PiP expanded back
     * to full screen" (state >= STARTED → keep playing).
     */
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipController.onPipModeChanged(isInPictureInPictureMode, lifecycle.currentState)
    }
```

(The existing `onStop` settings-flush is untouched and does not conflict: it runs on every background, including PiP transitions, which is harmless.)

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidApp:assembleDebug
```
Manual checklist (plumbing-only — full PiP behavior lands in Task P2):
- App launches, login/home/navigation all behave as before.
- Rotate the device on a few screens — no activity-recreate regressions from the `configChanges` change.
- Press home from a non-player screen — app backgrounds normally, **never** enters PiP (nothing has registered).

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/AndroidManifest.xml androidApp/src/androidMain/kotlin/com/continuum/app/android/MainActivity.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PipController.kt
git commit -m "Add picture-in-picture plumbing (manifest, MainActivity, PipController)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Task P2: PlayerScreen PiP integration — register, aspect feed, chrome gating, dismiss-stops-playback

**Files:**
- Modify: androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt

(The optional PlayerViewModel change from the spec is **not needed**: `PipController.isInPipMode` is a singleton StateFlow collected directly in the screen, which is lighter than threading `isPipActive` through `PlayerUiState`. Do not touch PlayerViewModel.)

- [ ] **Step 1: Write the failing test**

Not unit-testable: Activity PiP transitions + Compose UI + a bound MediaController have no JVM seam. Manual check is the full checklist in Step 4, with one behavior to verify honestly: **closing the PiP window does NOT pop the route or run the existing `onDispose` stop path** (the activity is only stopped, the composition stays alive), so playback stop on dismissal MUST come from the `PipController.register` dismissal callback (pause/stop/clear on the controller, then `popBackStack` so a later re-open doesn't land on a dead player).

- [ ] **Step 2: Run test to verify it fails**

n/a (no unit test). Green baseline:
```bash
./gradlew :androidApp:assembleDebug
```
Behavioral baseline on a device: press home during video playback — today the app just backgrounds (audio keeps playing via the service); no PiP window appears.

- [ ] **Step 3: Implementation** — four edits to `PlayerScreen.kt` (`PipController` is in the same package; no new import needed).

**3a. Collect the PiP flag.** After `val uiState by viewModel.uiState.collectAsState()` (line ~82) add:

```kotlin
    val isInPipMode by PipController.isInPipMode.collectAsState()
```

**3b. Register/unregister + dismissal handler.** Insert directly after the MediaController `DisposableEffect(context) { ... }` block (after line ~129):

```kotlin
    // PiP participation: registered only while this (video) route is
    // composed, so no other screen can ever auto-enter PiP. The dismissal
    // callback covers the user closing the PiP window with X: the activity
    // is merely stopped in that case — this route is NOT popped, so the
    // MediaController onDispose stop path above does NOT run. We stop
    // playback explicitly (same pause/stop/clear sequence as the dispose
    // path) and pop the route so reopening the app doesn't land on a dead
    // player. `mediaController` is compose state, so the lambda reads the
    // live controller at invocation time.
    DisposableEffect(Unit) {
        PipController.register(activity) {
            mediaController?.let { controller ->
                runCatching {
                    controller.pause()
                    controller.stop()
                    controller.clearMediaItems()
                }
            }
            viewModel.onExit()
            navController.popBackStack()
        }
        onDispose { PipController.unregister(activity) }
    }

    // Auto-enter (Android 12+) is only armed while video is actually
    // playing — pausing disarms it, so a paused player never PiPs on home.
    LaunchedEffect(uiState.isPlaying) {
        PipController.setPlaying(activity, uiState.isPlaying)
    }
```

**3c. Feed the aspect ratio** from the existing video-size listener (lines ~296–298). Inside the existing `override fun onVideoSizeChanged(size: VideoSize)` body's `if (size.width > 0 && size.height > 0)` block, add at the top:

```kotlin
                        // Keep the PiP window's aspect ratio in sync with the
                        // content. pixelWidthHeightRatio folds in anamorphic
                        // sources; PipController clamps to the platform-legal
                        // [1:2.39, 2.39:1] range.
                        PipController.setVideoSize(
                            activity,
                            (size.width * size.pixelWidthHeightRatio).toInt(),
                            size.height,
                        )
```

(The existing frame-rate block below stays unchanged inside the same `if`.)

**3d. Hide ALL overlay chrome in PiP.** All chrome — gesture layer, buffering spinner, notice overlay, transport controls, skip/next buttons, sheets — lives inside `PlayerOverlay`, so gate the single `PlayerOverlay` call site (lines ~424–441) behind the flag; subtitles keep rendering since they belong to the `PlayerView` itself:

```kotlin
            // All overlay chrome (gestures, spinner, notices, transport
            // controls, sheets) is hidden while in the PiP window; the
            // system supplies play/pause there via the media session.
            // Restored automatically when PiP expands back to full screen.
            if (!isInPipMode) {
                PlayerOverlay(
                    /* existing arguments unchanged — wrap the existing call */
                )
            }
```

NOTE FOR IMPLEMENTER: wrap the EXISTING `PlayerOverlay(...)` call with all its current arguments verbatim — do not retype them from this plan.

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidApp:assembleDebug
```
Manual checklist (device/emulator, Android 12+ primary; plus one API 26–30 device/emulator if available):
- Play a video, press home → app enters PiP; window aspect matches the content (try a 16:9 episode and a 2.35:1+ movie — wide content clamps to 2.39:1 without crashing).
- In PiP: all overlay chrome is gone (no controls, no notice toasts, no spinner overlay); subtitles still render if enabled.
- PiP window play/pause buttons work — they come from the `ContinuumPlaybackService` media session, no custom actions.
- Pause the video first, then press home → app backgrounds **without** entering PiP (auto-enter disarmed while paused). Audio continues via the service exactly as today — unchanged behavior.
- Close the PiP window with X → playback stops (no audio ghosting); reopen the app → you are back on the previous screen, not a dead player.
- Tap the PiP window → expand to full screen → overlay chrome works again (tap toggles controls), playback continues.
- From home/detail/settings/audio-player/reader screens, press home → never enters PiP.
- API 26–30 fallback: the home press goes through `onUserLeaveHint` → `enterPipIfEligible`. Same checklist; the transition is just less smooth (no source-rect animation — expected platform limitation).

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt
git commit -m "Enter picture-in-picture from the video player

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
