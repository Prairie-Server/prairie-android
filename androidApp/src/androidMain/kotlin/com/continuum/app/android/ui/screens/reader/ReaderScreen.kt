package com.continuum.app.android.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.continuum.app.common.ebook.ReaderCapabilities
import com.continuum.app.common.ebook.ReaderDisplaySettings
import com.continuum.app.common.ebook.ReaderSection
import com.continuum.app.common.ebook.ReaderTheme
import com.continuum.app.android.ui.screens.reader.reflow.ReflowableReader
import com.continuum.app.model.book.BookFormat
import com.continuum.app.model.ebook.EbookAnnotation
import com.continuum.app.model.ebook.ebookPageNumberFromProgressLocation
import org.koin.compose.viewmodel.koinViewModel

/**
 * Top-level book reader. Dispatches on [BookFormat] to the right
 * format-specific renderer. Each renderer owns its own pagination and
 * input model; this screen just provides the back button + shared
 * loading / error chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBackClick: () -> Unit,
    viewModel: ReaderViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showBookmarks by remember { mutableStateOf(false) }
    var showSections by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val supportsSettings = state.capabilities.supportsTextSize ||
        state.capabilities.supportsMargins ||
        state.capabilities.supportsTheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Keep the toolbar + page label out from under the status bar /
            // camera cutout; the background still fills behind it.
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { showBookmarks = true }, enabled = state.capabilities.supportsBookmarks) {
                Icon(Icons.Default.Bookmarks, contentDescription = "Bookmarks")
            }
            IconButton(
                onClick = { showSections = true },
                enabled = state.capabilities.supportsSections && state.sections.isNotEmpty(),
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Sections")
            }
            IconButton(onClick = { showSettings = true }, enabled = supportsSettings) {
                Icon(Icons.Default.Tune, contentDescription = "Reader settings")
            }
            IconButton(
                onClick = viewModel::addBookmark,
                enabled = state.fileId != null && state.capabilities.supportsBookmarks,
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = "Add bookmark")
            }
        }

        val progressLabel = when (state.format) {
            BookFormat.Epub, BookFormat.Fb2, BookFormat.Fbz, BookFormat.Txt, BookFormat.Markdown ->
                "${(state.progressPercent * 100).toInt()}%"
            else ->
                "${(state.progressPercent * 100).toInt()}% · Page ${state.currentPage + 1}" +
                    state.pageCount?.let { " of $it" }.orEmpty()
        }
        Text(
            text = progressLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (state.syncError != null) {
            Text(
                text = state.syncError ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else if (state.isSyncing) {
            Text(
                text = "Syncing reading progress",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        when {
            state.isLoading -> CenteredText("Loading book…")
            state.error != null -> CenteredText(state.error ?: "Failed to load")
            state.fileUrl.isNullOrBlank() -> CenteredText("No file available for this book.")
            else -> when (state.format) {
                BookFormat.Epub, BookFormat.Fb2, BookFormat.Fbz, BookFormat.Txt, BookFormat.Markdown ->
                    ReflowableReader(
                        format = state.format,
                        fileUrl = state.fileUrl!!,
                        settings = state.displaySettings,
                        initialLocator = state.progressLocation,
                        onLocatorChanged = viewModel::onLocatorChanged,
                        onSectionsKnown = viewModel::setSections,
                        onTextScaleNudge = viewModel::nudgeTextScale,
                    )
                BookFormat.Pdf -> PdfReader(
                    fileUrl = state.fileUrl!!,
                    title = state.title,
                    initialPage = state.currentPage,
                    onPageChanged = viewModel::onPageChanged,
                    onPageCountKnown = viewModel::onPageCountKnown,
                )
                BookFormat.Cbz -> ComicReader(
                    fileUrl = state.fileUrl!!,
                    title = state.title,
                    initialPage = state.currentPage,
                    onPageChanged = viewModel::onPageChanged,
                    onPageCountKnown = viewModel::onPageCountKnown,
                )
                else -> CenteredText(
                    "Format not supported yet: ${state.format.displayName}\n" +
                        "Open the file on a desktop reader for now.",
                )
            }
        }
    }

    if (showBookmarks) {
        BookmarkSheet(
            bookmarks = state.bookmarks,
            onJumpTo = { bookmark ->
                ebookPageNumberFromProgressLocation(bookmark.location)?.let(viewModel::jumpToPage)
                showBookmarks = false
            },
            onDelete = viewModel::deleteBookmark,
            onDismiss = { showBookmarks = false },
        )
    }
    if (showSections) {
        SectionsSheet(
            sections = state.sections,
            onJumpTo = { section ->
                ebookPageNumberFromProgressLocation(section.location)?.let(viewModel::jumpToPage)
                showSections = false
            },
            onDismiss = { showSections = false },
        )
    }
    if (showSettings && supportsSettings) {
        ReaderSettingsSheet(
            settings = state.displaySettings,
            capabilities = state.capabilities,
            onSettingsChange = viewModel::setDisplaySettings,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(32.dp),
        )
    }
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
        Text("Reader settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        if (capabilities.supportsTheme) {
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                listOf(ReaderTheme.System, ReaderTheme.Light, ReaderTheme.Sepia, ReaderTheme.Dark).forEach { theme ->
                    TextButton(
                        onClick = { onSettingsChange(settings.copy(theme = theme).normalized()) },
                    ) {
                        Text(theme.name)
                    }
                }
            }
        }
        if (capabilities.supportsTextSize) {
            Slider(
                value = settings.textScale,
                onValueChange = { onSettingsChange(settings.copy(textScale = it).normalized()) },
                valueRange = 0.8f..1.6f,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (capabilities.supportsMargins) {
            Slider(
                value = settings.marginScale,
                onValueChange = { onSettingsChange(settings.copy(marginScale = it).normalized()) },
                valueRange = 0.75f..1.5f,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
