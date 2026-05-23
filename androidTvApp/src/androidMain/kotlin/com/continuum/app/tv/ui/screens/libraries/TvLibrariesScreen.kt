package com.continuum.app.tv.ui.screens.libraries

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.MaterialTheme
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.tv.ui.components.TvCatalogEmptyState
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvFullScreenPicker
import com.continuum.app.tv.ui.components.TvFullScreenPickerOption
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.continuum.app.tv.ui.screens.library.TvLibraryDetailScreen
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TvLibrariesScreen(
    onItemClick: (contentId: String) -> Unit,
    onLibraryCollectionClick: (libraryId: Int, collectionId: String, title: String) -> Unit,
    onInitialContentFocus: () -> Unit = {},
    viewModel: TvLibrariesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val selectedLibrary = state.libraries.firstOrNull { it.id == state.selectedLibraryId }
        ?: state.libraries.firstOrNull()

    var showLibraryPicker by remember { mutableStateOf(false) }

    when {
        state.isLoading && state.libraries.isEmpty() -> TvLoadingScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
        state.error != null && state.libraries.isEmpty() -> TvErrorScreen(
            message = state.error!!,
            onRetry = viewModel::load,
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
        state.libraries.isEmpty() -> TvCatalogEmptyState(
            message = "No libraries available for this profile.",
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
        selectedLibrary != null -> {
            key(selectedLibrary.id) {
                TvLibraryDetailScreen(
                    libraryId = selectedLibrary.id,
                    libraryTitle = selectedLibrary.name,
                    libraryType = selectedLibrary.type,
                    canSwitchLibrary = state.libraries.size > 1,
                    onSwitchLibrary = {
                        if (state.libraries.size > 1) {
                            showLibraryPicker = true
                        }
                    },
                    onItemClick = onItemClick,
                    onCollectionClick = { collectionId, title ->
                        onLibraryCollectionClick(selectedLibrary.id, collectionId, title)
                    },
                    onInitialContentFocus = onInitialContentFocus,
                )
            }
        }
    }

    if (showLibraryPicker && state.libraries.size > 1) {
        TvFullScreenPicker(
            title = "Switch Library",
            options = state.libraries.map { library ->
                TvFullScreenPickerOption(
                    id = library.id.toString(),
                    title = library.name,
                    subtitle = library.pickerSubtitle(),
                    icon = library.iconVector(),
                )
            },
            selectedId = selectedLibrary?.id?.toString(),
            onSelect = { id ->
                id.toIntOrNull()?.let(viewModel::onLibrarySelected)
                showLibraryPicker = false
            },
            onDismiss = { showLibraryPicker = false },
        )
    }
}

private fun UserLibrary.iconVector(): ImageVector = when (type.lowercase()) {
    "movies", "movie" -> Icons.Filled.LocalMovies
    "series", "tv", "shows" -> Icons.Filled.Tv
    else -> Icons.Filled.VideoLibrary
}

private fun UserLibrary.pickerSubtitle(): String = when (type.lowercase()) {
    "movies", "movie" -> "Movies library"
    "series", "tv", "shows" -> "TV library"
    else -> "Library"
}
