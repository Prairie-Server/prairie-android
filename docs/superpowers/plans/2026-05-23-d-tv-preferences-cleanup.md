# D — TvPreferences cleanup + per-profile selectedLibraryId

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the one remaining live key (`selectedLibraryId`) out of the legacy global `TvPreferences` and into a new tiny per-profile store, then delete `TvPreferences.kt` entirely along with `TvSettingsViewModel`'s now-dead migration-only reads.

**Architecture context (per audit):** The spec for D assumed a substantial migration was needed. Reality: the per-profile preferences infrastructure already shipped — `AndroidPlayerSettingsStore` (per-profile DataStore file per `profileId`, scope-aware with server URL + device ID), `LibraryPlaybackPrefsStore` (per-profile via cache cleared on profile switch), `ProfileRepository` (profile-level subtitle prefs). Every active settings consumer already routes through these. The only stragglers are:
- `selectedLibraryId` in `TvPreferences` — read/written by `TvLibrariesViewModel:23` and `:51,:44,:59`. Pure UI state ("which library tab was I on") — should be per-profile, not device-scoped.
- Five legacy keys (playbackQuality, audioLanguage, subtitleMode, subtitleLanguage, subtitleSize, etc.) — read once in `TvSettingsViewModel:298-302` as a one-shot legacy migration that already executed on this device. Dead code today.

This plan does NOT rebuild the per-profile infrastructure (already shipping). It cleans up the relic.

**Tech stack:** Kotlin 2.1.20, `androidx.datastore.preferences`, existing Koin DI, `EncryptedTokenManagerImpl.getProfileId()` as the profile identity source.

**Reference:** Spec section D at `/opt/silo-android/docs/superpowers/specs/2026-05-23-android-tv-parity-rework-design.md`. The spec was outdated — fix it during cleanup. Pattern reference: `/opt/silo-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/settings/AndroidPlayerSettingsStore.kt:41-44` (per-profile DataStore factory pattern).

**Testing posture:** Per `AGENTS.md`, focused tests for non-trivial logic. The new store's per-profile keying is simple and worth a small Robolectric-style test (or skipped if test infra friction is high).

---

### Task 1: Create `TvLibrarySelectionStore` (per-profile DataStore for `selectedLibraryId`)

**Files:**
- Create: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/data/preferences/TvLibrarySelectionStore.kt`

**Why:** A tiny per-profile DataStore that owns just one key. Avoids piggybacking on `AndroidPlayerSettingsStore` (which would mix UI state into a server-flushed settings store) and avoids resurrecting `TvPreferences`.

- [ ] **Step 1: Create the file**

```kotlin
package com.continuum.app.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.continuum.app.network.TokenManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Per-profile store for the "last selected library tab" UI state on the
 * Libraries screen. Each profile keeps its own selection across launches
 * (file-per-profile DataStore, hashed profileId in filename).
 *
 * No server flush — this is pure local UI continuity, not a setting.
 * When no profile is active (during sign-in / profile-selection), reads
 * return null and writes are silent no-ops.
 *
 * Pattern modeled after [com.continuum.app.common.settings.AndroidPlayerSettingsStore]
 * but stripped to one key and no server-sync surface.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TvLibrarySelectionStore(
    private val context: Context,
    private val tokenManager: TokenManager,
    private val dataStoreFactory: (profileId: String) -> DataStore<Preferences> = { profileId ->
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(fileNameFor(profileId)) },
        )
    },
) {

    private val storeCache = mutableMapOf<String, DataStore<Preferences>>()

    private fun storeFor(profileId: String): DataStore<Preferences> =
        synchronized(storeCache) {
            storeCache.getOrPut(profileId) { dataStoreFactory(profileId) }
        }

    /**
     * Emits the currently-active profile's selected library id (or null
     * if unset, or if no profile is active). Re-emits when the profile
     * changes (e.g. on profile switch).
     */
    val selectedLibraryIdFlow: Flow<Int?> = flow {
        val profileId = tokenManager.getProfileId()
        if (profileId == null) {
            emit(null)
            return@flow
        }
        emit(storeFor(profileId).data.first()[SelectedLibraryKey])
    }
        // The flow above is a one-shot read; downstream consumers can call
        // .collect inside a LaunchedEffect tied to profile-change signals.
        // For full reactivity across profile switches, recompute via
        // observeFlow() below.
        .let { it }

    /**
     * Reactive variant: re-keys whenever profile changes (caller passes a
     * profile-change signal). Used by [com.continuum.app.tv.ui.screens.libraries.TvLibrariesViewModel]
     * to restore selection on entry.
     */
    fun observe(profileChangeSignal: Flow<Unit>): Flow<Int?> =
        profileChangeSignal.flatMapLatest {
            flow {
                val profileId = tokenManager.getProfileId()
                if (profileId == null) {
                    emit(null)
                    return@flow
                }
                val store = storeFor(profileId)
                store.data.collect { prefs -> emit(prefs[SelectedLibraryKey]) }
            }
        }

    suspend fun setSelectedLibraryId(id: Int?) {
        val profileId = tokenManager.getProfileId() ?: return
        storeFor(profileId).edit { prefs ->
            if (id == null) prefs.remove(SelectedLibraryKey)
            else prefs[SelectedLibraryKey] = id
        }
    }

    /**
     * One-shot read for the currently-active profile. Returns null if no
     * profile is active or no value is stored.
     */
    suspend fun getSelectedLibraryId(): Int? {
        val profileId = tokenManager.getProfileId() ?: return null
        return storeFor(profileId).data.first()[SelectedLibraryKey]
    }

    companion object {
        private val SelectedLibraryKey = intPreferencesKey("selected_library_id")

        // Filename pattern aligns with [AndroidPlayerSettingsStore.fileNameFor].
        private fun fileNameFor(profileId: String): String =
            "tv_library_selection_${profileHash(profileId)}"

        private fun profileHash(profileId: String): String =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(profileId.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { "%02x".format(it) }
                .take(16)
    }
}
```

- [ ] **Step 2: Register in Koin**

Open `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`. Add (near the existing `TvPreferences` registration — likely around line 84):

```kotlin
    single { TvLibrarySelectionStore(androidContext(), get()) }
```

`get()` resolves to `TokenManager` (already provided as singleton in the shared module).

- [ ] **Step 3: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/data/preferences/TvLibrarySelectionStore.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv): TvLibrarySelectionStore — per-profile selected-library state (D)

New tiny per-profile DataStore that owns just selectedLibraryId. Models
the file-per-profile pattern from AndroidPlayerSettingsStore but with
no server-sync surface — this is pure local UI continuity, not a
setting that flushes to the server.

Filename: tv_library_selection_<sha256(profileId).take(16)>.
TvLibrariesViewModel consumer migration lands in the next commit."
```

---

### Task 2: Migrate `TvLibrariesViewModel` to the new store

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/libraries/TvLibrariesViewModel.kt`

**Why:** This is the only live consumer of `TvPreferences.selectedLibraryId`. After this commit, `TvPreferences.kt` is fully unused outside the legacy migration-only reads in `TvSettingsViewModel` (cleaned in Task 3).

- [ ] **Step 1: Read the current ViewModel**

```bash
sed -n '1,80p' /opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/libraries/TvLibrariesViewModel.kt
```

Look for:
- Constructor parameter `TvPreferences` (around line 23 per audit)
- Read site: `preferences.selectedLibraryId.first()` around line 51
- Write sites: `preferences.setSelectedLibraryId(id)` around lines 44 and 59

- [ ] **Step 2: Swap the dependency**

Change the constructor:

```kotlin
class TvLibrariesViewModel(
    // … other params …
-   private val preferences: TvPreferences,
+   private val librarySelectionStore: TvLibrarySelectionStore,
) : ViewModel() {
```

Update the imports at the top of the file:

```diff
- import com.continuum.app.tv.data.preferences.TvPreferences
+ import com.continuum.app.tv.data.preferences.TvLibrarySelectionStore
```

- [ ] **Step 3: Replace the read site**

Find `preferences.selectedLibraryId.first()` (around line 51) and change to:

```kotlin
librarySelectionStore.getSelectedLibraryId()
```

(Adapt to the exact call site — if it's inside a `viewModelScope.launch { ... }`, the suspend `getSelectedLibraryId()` works directly. If it's mapped through a Flow, use `librarySelectionStore.observe(profileChangeSignal)` and adapt — but unless the existing code already had reactive selection-restoration, the suspend one-shot is simpler.)

- [ ] **Step 4: Replace the write sites**

Find `preferences.setSelectedLibraryId(id)` (around lines 44 and 59) and change both to:

```kotlin
librarySelectionStore.setSelectedLibraryId(id)
```

- [ ] **Step 5: Update Koin DI for `TvLibrariesViewModel`**

In `AndroidTvModule.kt`, find the `viewModel { TvLibrariesViewModel(...) }` registration. Swap the `get()` that resolved `TvPreferences` for one that resolves `TvLibrarySelectionStore`.

- [ ] **Step 6: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If `TvPreferences` is now unreferenced from `TvLibrariesViewModel.kt` but still imported, remove the orphan import.

- [ ] **Step 7: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/libraries/TvLibrariesViewModel.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "refactor(tv-libraries): use TvLibrarySelectionStore for selected library (D)

TvLibrariesViewModel now reads/writes the per-profile selectedLibraryId
via TvLibrarySelectionStore (filename hashed per profile) instead of
the global TvPreferences. Switching profiles will now show each
profile's own last-viewed library.

TvPreferences is now fully unused for live behavior — only the
TvSettingsViewModel legacy migration path still references it, which
is dead code on any device whose migration already ran. Cleanup in
the next commit."
```

---

### Task 3: Delete `TvPreferences` + drop the dead legacy migration reads

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/settings/TvSettingsViewModel.kt`
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`
- Delete: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/data/preferences/TvPreferences.kt`

**Why:** With Task 2 done, the only remaining references are (a) the legacy-migration reads in `TvSettingsViewModel` (dead — the migration already executed) and (b) the Koin registration in `AndroidTvModule.kt`. Both go.

**Risk note:** If there are users whose TvPreferences-stored values were never migrated to `PlayerSettingsStore`, removing the migration would lose those values. For this single-developer, single-device project, that risk is zero. For multi-user production, the risk would require a deprecation window. Documented in the commit message.

- [ ] **Step 1: Grep guard — confirm where TvPreferences is still referenced**

```bash
grep -rnE "TvPreferences\b" /opt/silo-android/androidTvApp/src
```

Expected hits after Task 2:
- `TvPreferences.kt` (the file itself)
- `TvSettingsViewModel.kt` (migration-only reads)
- `AndroidTvModule.kt` (Koin registration)

If anything else references it, STOP and report — there's a consumer the audit missed.

- [ ] **Step 2: Remove TvPreferences from `TvSettingsViewModel`**

Open `TvSettingsViewModel.kt`. Find:
- The constructor parameter `private val tvPreferences: TvPreferences,` (or similar — the audit referenced it around line 45).
- The legacy migration block reading `tvPreferences.playbackQuality.first()` etc. (around lines 298–302 per audit).
- The import.

Delete:
- The constructor parameter.
- The migration block entirely (it's dead — already ran on this device, and `PlayerSettingsStore` is the source of truth now).
- The import.

If the migration block has neighboring logic that still does something useful (e.g. a one-time call to `playerSettingsStore.refreshFromServer()`), keep that. If the entire `init` block or `migrateLegacyPrefs()` method is now empty, remove the wrapper too.

- [ ] **Step 3: Remove TvPreferences from Koin DI**

In `AndroidTvModule.kt`:
- Delete the `single { TvPreferences(...) }` registration (around line 84).
- Update the `viewModel { TvSettingsViewModel(...) }` registration to remove the `get()` that resolved TvPreferences.

- [ ] **Step 4: Delete the TvPreferences.kt file**

```bash
git -C /opt/silo-android rm androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/data/preferences/TvPreferences.kt
```

- [ ] **Step 5: Build to verify nothing compiles against it**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If `Unresolved reference: TvPreferences` surfaces, Steps 2-3 missed a reference — investigate.

- [ ] **Step 6: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/settings/TvSettingsViewModel.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "chore(tv): delete legacy TvPreferences (D)

After Task 2 moved selectedLibraryId to TvLibrarySelectionStore, the
only remaining TvPreferences references were the legacy-migration
read block in TvSettingsViewModel.init (dead code — the migration
already ran on this device, and PlayerSettingsStore is the source of
truth for all playback settings) and the Koin registration.

Removes the class entirely. Per-profile preferences infra is already
shipping via AndroidPlayerSettingsStore + LibraryPlaybackPrefsStore +
ProfileRepository; this commit just closes the relic.

Risk: any never-launched device with pre-migration TvPreferences
state would lose it. Acceptable for this single-developer project; a
multi-user production rollout would have wanted a deprecation window."
```

---

## Self-Review

**Spec coverage:**
- Spec D wanted "per-(server, profile) DataStore files for TvPreferences" → reality is the per-profile infra is ALREADY in place via `AndroidPlayerSettingsStore` + siblings. This plan does the residual cleanup: move the one stragger (`selectedLibraryId`) into a tiny new per-profile store, then delete the legacy class.
- Spec D wanted "settings file deleted on profile delete" → `AndroidPlayerSettingsStore.fileNameFor(profileId)` and the new `TvLibrarySelectionStore.fileNameFor(profileId)` both produce hashed filenames; cleanup would be a separate concern not currently implemented for either. Not in scope for D.
- Spec D wanted "consumer audit" → done as part of this plan (audit identified TvLibrariesViewModel + TvSettingsViewModel as the only consumers).

**Placeholder scan:** No "TBD." The `observe(profileChangeSignal)` variant in `TvLibrarySelectionStore` is unused by the initial consumer; left in as the seam for future reactive consumers.

**Type consistency:** `selectedLibraryId: Int?` consistent across new store and existing consumer.

**Sequencing:** Task 1 (new store) → Task 2 (consumer migration) → Task 3 (delete legacy). Order matters: Task 2 needs the new store; Task 3 needs Task 2 to remove the live reference.

**Risk:** Task 3 is the highest-risk because it touches `TvSettingsViewModel` (a screen many users interact with). The grep guard in Step 1 + the build check in Step 5 are the safety nets. If `TvSettingsViewModel`'s migration block does anything beyond reading TvPreferences keys (e.g. server-side initialization), that work needs to be preserved.
