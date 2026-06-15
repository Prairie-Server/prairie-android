package com.continuum.app.android.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.continuum.app.common.ebook.ReaderCapabilities
import com.continuum.app.common.ebook.ReaderDisplaySettings
import com.continuum.app.common.ebook.ReaderEngineKind
import com.continuum.app.common.ebook.ReaderSection
import com.continuum.app.common.ebook.ReaderShellEvent
import com.continuum.app.common.ebook.ReaderShellUiState
import com.continuum.app.common.ebook.ReaderSheet
import com.continuum.app.common.ebook.ReaderTheme
import com.continuum.app.common.ebook.reduceReaderShellState
import com.continuum.app.model.ebook.EbookAnnotation
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderShell(
    state: ReaderUiState,
    onBackClick: () -> Unit,
    onAddBookmark: () -> Unit,
    onDeleteBookmark: (EbookAnnotation) -> Unit,
    onJumpToBookmark: (EbookAnnotation) -> Unit,
    onJumpToSection: (ReaderSection) -> Unit,
    onSettingsChange: (ReaderDisplaySettings) -> Unit,
    content: @Composable (onToggleChrome: () -> Unit) -> Unit,
) {
    var shellState by remember { mutableStateOf(ReaderShellUiState()) }
    val supportsSettings = state.capabilities.supportsTextSize ||
        state.capabilities.supportsMargins ||
        state.capabilities.supportsTheme

    fun send(event: ReaderShellEvent) {
        shellState = reduceReaderShellState(shellState, event)
    }
    val onToggleChrome = { send(ReaderShellEvent.ToggleChrome) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        content(onToggleChrome)

        if (shellState.chromeVisible) {
            ReaderTopChrome(
                state = state,
                supportsSettings = supportsSettings,
                onBackClick = onBackClick,
                onBookmarksClick = { send(ReaderShellEvent.OpenSheet(ReaderSheet.Bookmarks)) },
                onSectionsClick = { send(ReaderShellEvent.OpenSheet(ReaderSheet.Sections)) },
                onSettingsClick = { send(ReaderShellEvent.OpenSheet(ReaderSheet.Settings)) },
                onAddBookmark = onAddBookmark,
            )
            ReaderBottomChrome(state = state)
        }
    }

    when (shellState.activeSheet) {
        ReaderSheet.Bookmarks -> BookmarkSheet(
            bookmarks = state.bookmarks,
            onJumpTo = {
                onJumpToBookmark(it)
                send(ReaderShellEvent.DismissSheet)
            },
            onDelete = onDeleteBookmark,
            onDismiss = { send(ReaderShellEvent.DismissSheet) },
        )
        ReaderSheet.Sections -> SectionsSheet(
            sections = state.sections,
            onJumpTo = {
                onJumpToSection(it)
                send(ReaderShellEvent.DismissSheet)
            },
            onDismiss = { send(ReaderShellEvent.DismissSheet) },
        )
        ReaderSheet.Settings -> ReaderSettingsSheet(
            settings = state.displaySettings,
            capabilities = state.capabilities,
            onSettingsChange = onSettingsChange,
            onDismiss = { send(ReaderShellEvent.DismissSheet) },
        )
        ReaderSheet.None,
        ReaderSheet.More -> Unit
    }
}

@Composable
private fun ReaderTopChrome(
    state: ReaderUiState,
    supportsSettings: Boolean,
    onBackClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onSectionsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddBookmark: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .consumeChromeTouches()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onBookmarksClick, enabled = state.capabilities.supportsBookmarks) {
            Icon(Icons.Default.Bookmarks, contentDescription = "Bookmarks")
        }
        IconButton(
            onClick = onSectionsClick,
            enabled = state.capabilities.supportsSections && state.sections.isNotEmpty(),
        ) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Sections")
        }
        IconButton(onClick = onSettingsClick, enabled = supportsSettings) {
            Icon(Icons.Default.Tune, contentDescription = "Reader settings")
        }
        IconButton(
            onClick = onAddBookmark,
            enabled = state.fileId != null && state.capabilities.supportsBookmarks,
        ) {
            Icon(Icons.Default.BookmarkAdd, contentDescription = "Add bookmark")
        }
    }
}

@Composable
private fun BoxScope.ReaderBottomChrome(state: ReaderUiState) {
    val progressLabel = readerBottomChromeLabel(state)
    val syncError = state.syncError

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .navigationBarsPadding()
            .consumeChromeTouches()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (progressLabel != null) {
            Text(
                text = progressLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (syncError != null) {
            Text(
                text = syncError,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (state.isSyncing) {
            Text(
                text = "Syncing reading progress",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun readerBottomChromeLabel(state: ReaderUiState): String? {
    val progressPercent = (state.progressPercent * 100).toInt()
    return when (state.capabilities.engineKind) {
        ReaderEngineKind.Reflowable -> "$progressPercent%"
        ReaderEngineKind.FixedDocument,
        ReaderEngineKind.ComicManga -> "$progressPercent% - Page ${state.currentPage + 1}" +
            state.pageCount?.let { " of $it" }.orEmpty()
        ReaderEngineKind.External -> state.formatDisplayName.ifBlank { "External reader" }
    }
}

private fun Modifier.consumeChromeTouches() =
    pointerInput(Unit) {
        detectTapGestures(onTap = {})
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkSheet(
    bookmarks: List<EbookAnnotation>,
    onJumpTo: (EbookAnnotation) -> Unit,
    onDelete: (EbookAnnotation) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Bookmarks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        if (bookmarks.isEmpty()) {
            Text("No bookmarks yet", modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn {
                items(bookmarks, key = { it.id }) { bookmark ->
                    ListItem(
                        headlineContent = { Text(bookmark.location ?: "Saved bookmark") },
                        supportingContent = { Text(bookmark.createdAt.orEmpty()) },
                        modifier = Modifier.clickable { onJumpTo(bookmark) },
                        trailingContent = {
                            IconButton(onClick = { onDelete(bookmark) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete bookmark")
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionsSheet(
    sections: List<ReaderSection>,
    onJumpTo: (ReaderSection) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Sections", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        LazyColumn {
            items(sections, key = { it.index }) { section ->
                ListItem(
                    headlineContent = { Text(section.title.ifBlank { "Section ${section.index + 1}" }) },
                    supportingContent = { Text("Page ${section.index + 1}") },
                    modifier = Modifier.clickable { onJumpTo(section) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    settings: ReaderDisplaySettings,
    capabilities: ReaderCapabilities,
    onSettingsChange: (ReaderDisplaySettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Reader settings", style = MaterialTheme.typography.titleLarge)
            if (capabilities.supportsTheme) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    Row {
                        listOf(ReaderTheme.System, ReaderTheme.Light, ReaderTheme.Sepia, ReaderTheme.Dark).forEach { theme ->
                            TextButton(
                                onClick = { onSettingsChange(settings.copy(theme = theme).normalized()) },
                            ) {
                                Text(theme.name)
                            }
                        }
                    }
                }
            }
            if (capabilities.supportsTextSize) {
                ReaderSettingSlider(
                    label = "Text size",
                    valueLabel = settings.textScale.readerPercentLabel(),
                    value = settings.textScale,
                    valueRange = 0.6f..3.0f,
                    onValueChange = { onSettingsChange(settings.copy(textScale = it).normalized()) },
                )
            }
            if (capabilities.supportsMargins) {
                ReaderSettingSlider(
                    label = "Margins",
                    valueLabel = settings.marginScale.readerPercentLabel(),
                    value = settings.marginScale,
                    valueRange = 0.75f..1.5f,
                    onValueChange = { onSettingsChange(settings.copy(marginScale = it).normalized()) },
                )
            }
        }
    }
}

@Composable
private fun ReaderSettingSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

private fun Float.readerPercentLabel(): String {
    val percent = (this * 100).roundToInt()
    return if (percent == 100) "Normal (100%)" else "$percent%"
}
