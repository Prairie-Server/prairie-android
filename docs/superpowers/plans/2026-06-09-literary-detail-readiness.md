# Literary Detail Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete mobile literary detail pages with explicit ebook version selection and real audiobook/ebook download actions, without exposing ebooks on Android TV.

**Architecture:** Keep `ItemDetailViewModel` as the action owner and reuse its existing `downloads`, `downloadRecordFor()`, `selectVersion()`, and `onDownloadTapped()` behavior. Make `ItemDetailScreen` derive selected literary versions and download state, then pass stateless action data into `BookDetailContent` and `AudiobookDetailContent`.

**Tech Stack:** Kotlin, Jetpack Compose Material3, shared catalog/download models, existing Android download enqueuer, existing ebook reader and audiobook player routes.

---

## File Structure

- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt`
  - Derives selected literary versions, download state, and callbacks.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/book/BookDetailContent.kt`
  - Renders explicit version selector, read/download actions, and external-only format copy.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookDetailContent.kt`
  - Renders audiobook download action beside play behavior.
- Verify existing `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt`
  - Confirms requested readable file, fallback order, and external-only format behavior are already covered.

---

### Task 1: Wire Literary Download State In Item Detail

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt`

- [ ] **Step 1: Add ebook helper imports**

Add imports:

```kotlin
import com.continuum.app.model.catalog.FileVersion
import com.continuum.app.model.ebook.chooseEbookVersion
import com.continuum.app.model.ebook.isInAppReadableEbookVersion
import com.continuum.app.model.ebook.isSupportedEbookVersion
```

- [ ] **Step 2: Add local download-state helper**

Add this data class and helper below `ItemDetailScreen`:

```kotlin
private data class DetailDownloadState(
    val isDownloaded: Boolean = false,
    val progress: Float? = null,
)

private fun downloadStateFor(
    version: FileVersion?,
    records: List<com.continuum.app.model.download.DownloadRecord>,
): DetailDownloadState {
    val record = version?.let { v -> records.firstOrNull { it.mediaFileId == v.fileId } }
    val status = record?.statusEnum()
    val progress = record
        ?.takeIf {
            status == com.continuum.app.model.download.DownloadStatus.Downloading ||
                status == com.continuum.app.model.download.DownloadStatus.Queued
        }
        ?.let { rec ->
            if (rec.fileSize > 0) {
                (rec.bytesSent.toFloat() / rec.fileSize.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    return DetailDownloadState(
        isDownloaded = status == com.continuum.app.model.download.DownloadStatus.Completed,
        progress = progress,
    )
}
```

- [ ] **Step 3: Use helper for movie download state**

Replace the inline movie `downloadRecord`, `isDownloaded`, and `downloadProgress` derivation with:

```kotlin
val downloadRecords by viewModel.downloads.collectAsState()
val selectedVersion = detail.versions.getOrNull(effectiveSelectedVersionIndex)
val downloadState = downloadStateFor(selectedVersion, downloadRecords)
```

Then pass:

```kotlin
isDownloaded = downloadState.isDownloaded,
downloadProgress = downloadState.progress,
```

- [ ] **Step 4: Derive audiobook download state and callback**

Inside the `audiobook` branch, before calling `AudiobookDetailContent`, collect records and derive state:

```kotlin
val downloadRecords by viewModel.downloads.collectAsState()
val audiobookVersion = effectiveAudiobookFileId
    ?.let { fileId -> detail.versions.firstOrNull { it.fileId == fileId } }
    ?: detail.versions.firstOrNull()
val downloadState = downloadStateFor(audiobookVersion, downloadRecords)
```

Pass:

```kotlin
isDownloaded = downloadState.isDownloaded,
downloadProgress = downloadState.progress,
onDownloadClick = audiobookVersion?.let { version ->
    { viewModel.onDownloadTapped(version, detail.title) }
},
```

- [ ] **Step 5: Derive book selected version and download state**

Inside the `book`, `ebook`, `comic`, `manga` branch, before calling `BookDetailContent`, derive selected version:

```kotlin
val selectedBookVersion = if (state.hasExplicitVersionSelection) {
    detail.versions.getOrNull(state.selectedVersionIndex)
} else {
    chooseEbookVersion(detail.versions, requestedFileId = detail.userData?.lastFileId)
        ?: detail.versions.firstOrNull { it.isSupportedEbookVersion() }
        ?: detail.versions.firstOrNull()
}
val selectedBookVersionIndex = selectedBookVersion
    ?.let { version -> detail.versions.indexOfFirst { it.fileId == version.fileId } }
    ?.takeIf { it >= 0 }
    ?: 0
val downloadRecords by viewModel.downloads.collectAsState()
val downloadState = downloadStateFor(selectedBookVersion, downloadRecords)
```

Pass these new args:

```kotlin
selectedVersionIndex = selectedBookVersionIndex,
onVersionSelected = { viewModel.selectVersion(it) },
canReadSelectedVersion = selectedBookVersion?.isInAppReadableEbookVersion() == true,
isDownloaded = downloadState.isDownloaded,
downloadProgress = downloadState.progress,
onDownloadClick = selectedBookVersion?.takeIf { it.isSupportedEbookVersion() }?.let { version ->
    { viewModel.onDownloadTapped(version, detail.title) }
},
```

Update `onReadClick` to:

```kotlin
onReadClick = { fileId -> onBookReadClick(detail.contentId, fileId) },
```

- [ ] **Step 6: Compile mobile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: FAIL until Tasks 2 and 3 add the new content parameters.

Do not commit Task 1 separately; it intentionally depends on the content component updates.

---

### Task 2: Add Book Version And Download Actions

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/book/BookDetailContent.kt`

- [ ] **Step 1: Update imports**

Add imports:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import com.continuum.app.model.ebook.ebookFormatDisplayName
import com.continuum.app.model.ebook.isSupportedEbookVersion
```

Remove the `remember` and `chooseEbookVersion` imports once unused.

- [ ] **Step 2: Expand `BookDetailContent` signature**

Change the signature to include:

```kotlin
selectedVersionIndex: Int,
onVersionSelected: (Int) -> Unit,
canReadSelectedVersion: Boolean,
isDownloaded: Boolean = false,
downloadProgress: Float? = null,
```

Keep `onDownloadClick: (() -> Unit)? = null`.

- [ ] **Step 3: Replace internal selected-version derivation**

Replace:

```kotlin
val selectedVersion = remember(detail.versions) {
    chooseEbookVersion(detail.versions, requestedFileId = null)
}
```

with:

```kotlin
val selectedVersion = detail.versions.getOrNull(selectedVersionIndex)
```

Set:

```kotlin
val canDownloadSelectedVersion = selectedVersion?.isSupportedEbookVersion() == true && onDownloadClick != null
```

- [ ] **Step 4: Use selected version for Read**

Update the Read button:

```kotlin
Button(
    onClick = { selectedVersion?.let { onReadClick(it.fileId) } },
    enabled = selectedVersion != null && canReadSelectedVersion,
    modifier = Modifier.fillMaxWidth(),
) {
    Icon(Icons.Filled.MenuBook, contentDescription = null)
    Spacer(modifier = Modifier.width(6.dp))
    Text(
        when {
            selectedVersion == null -> "Unavailable"
            canReadSelectedVersion -> "Read"
            else -> "Open from Downloads"
        },
    )
}
```

- [ ] **Step 5: Add Download action below Read**

Immediately after the Read button, add:

```kotlin
OutlinedButton(
    onClick = { onDownloadClick?.invoke() },
    enabled = canDownloadSelectedVersion && !isDownloaded,
    modifier = Modifier.fillMaxWidth(),
) {
    Icon(
        imageVector = if (isDownloaded) Icons.Filled.Check else Icons.Filled.Download,
        contentDescription = null,
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
        when {
            isDownloaded -> "Downloaded"
            downloadProgress != null -> "Cancel Download"
            canDownloadSelectedVersion -> "Download"
            else -> "Download Unavailable"
        },
    )
}
downloadProgress?.let { progress ->
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
    )
}
```

- [ ] **Step 6: Update unsupported copy**

Replace the existing unsupported-format copy condition with:

```kotlin
if (selectedVersion != null && !canReadSelectedVersion) {
    Text(
        text = "This format can be downloaded in its original file format and opened from Downloads or another reader.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

- [ ] **Step 7: Make versions selectable**

Replace passive version text rows with clickable rows:

```kotlin
detail.versions.forEachIndexed { index, version ->
    val label = version.ebookFormatDisplayName()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (index == selectedVersionIndex) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
            )
            .clickable { onVersionSelected(index) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "file ${version.fileId}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 8: Compile mobile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: still FAIL until Task 3 adds audiobook parameters, or PASS if Task 3 is already applied.

---

### Task 3: Add Audiobook Download Action

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookDetailContent.kt`

- [ ] **Step 1: Update imports**

Add imports:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
```

- [ ] **Step 2: Expand `AudiobookDetailContent` signature**

Add:

```kotlin
isDownloaded: Boolean = false,
downloadProgress: Float? = null,
```

Keep `onDownloadClick: (() -> Unit)? = null`.

- [ ] **Step 3: Add download button below Play**

Immediately after the Play `Button`, add:

```kotlin
OutlinedButton(
    onClick = { onDownloadClick?.invoke() },
    enabled = onDownloadClick != null && !isDownloaded,
    modifier = Modifier.fillMaxWidth(),
) {
    Icon(
        imageVector = if (isDownloaded) Icons.Filled.Check else Icons.Filled.Download,
        contentDescription = null,
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
        when {
            isDownloaded -> "Downloaded"
            downloadProgress != null -> "Cancel Download"
            onDownloadClick != null -> "Download"
            else -> "Download Unavailable"
        },
    )
}
downloadProgress?.let { progress ->
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
    )
}
```

- [ ] **Step 4: Compile mobile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 5: Commit literary detail actions**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/book/BookDetailContent.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookDetailContent.kt
git commit -m "feat: complete literary detail actions"
```

---

### Task 4: Full Verification And Review

**Files:**
- Verify all modified files and existing ebook-version tests.

- [ ] **Step 1: Run focused ebook tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.ebook.EbookVersionSelectionTest"
```

Expected: PASS.

- [ ] **Step 2: Run full verification**

Run:

```bash
git diff --check && ./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 3: Review platform scope**

Run:

```bash
git diff HEAD~1..HEAD -- androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/book/BookDetailContent.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookDetailContent.kt
git status --short
```

Expected:

- no Android TV files changed;
- no new TV Reading or ebook routes;
- working tree clean.

- [ ] **Step 4: Commit verification fixes when verification reveals a defect**

If verification reveals a defect, fix the smallest scoped issue, rerun the failed command, and commit:

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/book/BookDetailContent.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookDetailContent.kt
git commit -m "fix: stabilize literary detail actions"
```

If no fixes are needed, leave the branch at Task 3's commit.
