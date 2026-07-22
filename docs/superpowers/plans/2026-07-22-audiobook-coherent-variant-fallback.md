# Audiobook Coherent Variant Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish Android PR #75 by choosing a complete audiobook presentation variant when the preferred variant is incomplete and by preserving the downloaded-file preference during offline resume mapping.

**Architecture:** Keep variant selection inside `audioParts`, where logical parts are already grouped. Resolve one complete presentation key before selecting files, then pass the downloaded file ID into the existing cached-timeline builder so offline playback uses the same selection contract.

**Tech Stack:** Kotlin Multiplatform, Kotlin/JVM tests, Gradle.

## Global Constraints

- Prefer the selected file's presentation variant only when it covers every indexed logical part.
- Otherwise select the first complete presentation variant in deterministic candidate order.
- If no complete variant exists, retain deterministic per-part fallback behavior.
- Do not add a new playback error or change server APIs, downloads, persistence, or online session behavior.
- Do not trigger remote CI or release builds.

---

### Task 1: Reject incomplete preferred presentation variants

**Files:**
- Modify: `shared/src/commonTest/kotlin/org/siloserver/silo/audiobook/AudiobookTimelineTest.kt`
- Modify: `shared/src/commonMain/kotlin/org/siloserver/silo/audiobook/AudiobookTimeline.kt:208-227`

**Interfaces:**
- Consumes: `audioParts(versions: List<FileVersion>, preferredFileId: Int? = null): List<FileVersion>` and `FileVersion.presentationVariantKey(): String`.
- Produces: a deterministic list containing one file per indexed logical part, using a complete presentation key whenever one exists.

- [ ] **Step 1: Add the failing incomplete-preference regression test**

Add this test after `fallback keeps one presentation variant across all parts`:

```kotlin
@Test
fun `incomplete preferred variant falls back to a complete variant`() {
    val versions = listOf(
        part(fileId = 401, duration = 100.0, partIndex = 0)
            .copy(presentationGroupKey = "lossless"),
        part(fileId = 402, duration = 100.0, partIndex = 0)
            .copy(presentationGroupKey = "compressed"),
        part(fileId = 403, duration = 200.0, partIndex = 1)
            .copy(presentationGroupKey = "compressed"),
    )

    val timeline = buildAudiobookTimeline(
        versions = versions,
        serverTotalSeconds = null,
        preferredFileId = 401,
    )!!

    assertEquals(listOf(402, 403), timeline.tracks.map { it.fileId })
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :shared:testDebugUnitTest \
  --tests 'org.siloserver.silo.audiobook.AudiobookTimelineTest'
```

Expected: FAIL because the current selector returns file IDs `[401, 403]`.

- [ ] **Step 3: Select the preferred key only when it is complete**

Replace the indexed selection block in `audioParts` with:

```kotlin
val selected = if (indexed.isNotEmpty()) {
    val byPart = indexed
        .groupBy { it.presentationPartIndex }
        .toSortedMap(compareBy(nullsLast()) { it })
    val candidateKeys = candidates
        .map { it.presentationVariantKey() }
        .distinct()
    fun isCompleteVariant(key: String): Boolean = byPart.values.all { partVersions ->
        partVersions.any { it.presentationVariantKey() == key }
    }
    val selectedKey = preferredKey
        ?.takeIf(::isCompleteVariant)
        ?: candidateKeys.firstOrNull(::isCompleteVariant)

    byPart.values.mapNotNull { partVersions ->
        selectedKey?.let { key ->
            partVersions.firstOrNull { it.presentationVariantKey() == key }
        } ?: partVersions.firstOrNull { it.fileId == preferredFileId }
            ?: partVersions.firstOrNull()
    }
} else {
    listOf(
        preferred
            ?: preferredKey?.let { key -> candidates.firstOrNull { it.presentationVariantKey() == key } }
            ?: candidates.first(),
    )
}
```

- [ ] **Step 4: Run the focused timeline suite and verify GREEN**

Run:

```bash
./gradlew :shared:testDebugUnitTest \
  --tests 'org.siloserver.silo.audiobook.AudiobookTimelineTest'
```

Expected: PASS, including the existing complete-preferred and coherent-fallback tests.

- [ ] **Step 5: Commit the timeline fix**

```bash
git add \
  shared/src/commonMain/kotlin/org/siloserver/silo/audiobook/AudiobookTimeline.kt \
  shared/src/commonTest/kotlin/org/siloserver/silo/audiobook/AudiobookTimelineTest.kt
git commit -m "fix(audiobook): require complete preferred variants [skip ci]"
```

### Task 2: Preserve the downloaded-file preference offline

**Files:**
- Modify: `android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/AudiobookPlayerStartPositionTest.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/AudiobookPlayerViewModel.kt:758-763`

**Interfaces:**
- Consumes: `buildAudiobookTimeline(versions: List<FileVersion>, serverTotalSeconds: Double?, preferredFileId: Int? = null)` and `OfflineMedia.fileId` exposed as `media.fileId`.
- Produces: a cached offline timeline built with `preferredFileId = media.fileId`, allowing `offlinePart` lookup to map whole-book resume time into part-local time.

- [ ] **Step 1: Add the failing offline wiring regression test**

Add this test to `AudiobookPlayerStartPositionTest`:

```kotlin
@Test
fun offlineCachedTimelinePrefersDownloadedFile() {
    val offlineBody = source
        .substringAfter("private suspend fun loadOfflineOnly")
        .substringBefore("fun onPositionChanged")

    assertTrue(
        offlineBody.contains("preferredFileId = media.fileId"),
        "offline cached timeline must select the downloaded presentation variant",
    )
}
```

- [ ] **Step 2: Run the focused Android test and verify RED**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest \
  --tests 'org.siloserver.silo.common.player.AudiobookPlayerStartPositionTest'
```

Expected: FAIL with `offline cached timeline must select the downloaded presentation variant`.

- [ ] **Step 3: Pass the downloaded file ID to timeline construction**

Update the cached timeline call in `loadOfflineOnly` to:

```kotlin
val cachedTimeline = catalogRepository.getCachedItemDetail(contentId)?.let { cached ->
    buildAudiobookTimeline(
        versions = cached.versions,
        serverTotalSeconds = cached.audiobook?.totalDurationSeconds?.toDouble(),
        preferredFileId = media.fileId,
    )
}?.takeIf { !it.isSingle }
```

- [ ] **Step 4: Run the focused Android test and verify GREEN**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest \
  --tests 'org.siloserver.silo.common.player.AudiobookPlayerStartPositionTest'
```

Expected: PASS.

- [ ] **Step 5: Commit the offline wiring fix**

```bash
git add \
  android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/AudiobookPlayerViewModel.kt \
  android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/AudiobookPlayerStartPositionTest.kt
git commit -m "fix(audiobook): preserve offline variant selection [skip ci]"
```

### Task 3: Verify the complete PR update

**Files:**
- Verify: `shared/src/commonMain/kotlin/org/siloserver/silo/audiobook/AudiobookTimeline.kt`
- Verify: `shared/src/commonTest/kotlin/org/siloserver/silo/audiobook/AudiobookTimelineTest.kt`
- Verify: `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/AudiobookPlayerViewModel.kt`
- Verify: `android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/AudiobookPlayerStartPositionTest.kt`

**Interfaces:**
- Consumes: the two completed fixes.
- Produces: a reviewable PR #75 head with focused test evidence and no remote workflow run.

- [ ] **Step 1: Run both focused suites together**

```bash
./gradlew :shared:testDebugUnitTest \
  --tests 'org.siloserver.silo.audiobook.AudiobookTimelineTest'
./gradlew :android-shared:testDebugUnitTest \
  --tests 'org.siloserver.silo.common.player.AudiobookPlayerStartPositionTest'
```

Expected: `BUILD SUCCESSFUL` with both test classes passing.

- [ ] **Step 2: Verify patch hygiene and scope**

```bash
git diff --check origin/pr-75-review..HEAD
git diff --stat origin/pr-75-review..HEAD
git status --short --branch
```

Expected: no whitespace errors; only the approved design, plan, two production files, and two test files differ from the prior PR head; working tree is clean.

- [ ] **Step 3: Update the PR head without running CI**

Push the commits, whose messages all contain `[skip ci]`, to PR #75's existing head branch:

```bash
git push <pr-head-remote> HEAD:fix/audiobook-chapters-pr
```

Expected: PR #75 updates to the verified head; GitHub Actions records no new workflow run for that SHA.
