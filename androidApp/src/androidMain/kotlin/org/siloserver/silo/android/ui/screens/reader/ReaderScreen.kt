package org.siloserver.silo.android.ui.screens.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.siloserver.silo.common.ebook.ReaderEngineKind
import org.siloserver.silo.model.ebook.ebookPageNumberFromProgressLocation
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReaderScreen(
    onBackClick: () -> Unit,
    viewModel: ReaderViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val isReflowable = state.capabilities.engineKind == ReaderEngineKind.Reflowable

    ReaderShell(
        state = state,
        onBackClick = onBackClick,
        onAddBookmark = viewModel::addBookmark,
        onDeleteBookmark = viewModel::deleteBookmark,
        onJumpToBookmark = { bookmark ->
            if (isReflowable) {
                bookmark.location?.let(viewModel::jumpToLocation)
            } else {
                ebookPageNumberFromProgressLocation(bookmark.location)?.let(viewModel::jumpToPage)
            }
        },
        onJumpToSection = { section ->
            if (isReflowable) {
                viewModel.jumpToLocation(section.location)
            } else {
                ebookPageNumberFromProgressLocation(section.location)?.let(viewModel::jumpToPage)
            }
        },
        onSettingsChange = viewModel::setDisplaySettings,
    ) { onToggleChrome ->
        ReaderEngineHost(
            state = state,
            onPageChanged = viewModel::onPageChanged,
            onPageCountKnown = viewModel::onPageCountKnown,
            onLocatorChanged = viewModel::onLocatorChanged,
            onSectionsKnown = viewModel::setSections,
            onTextScaleNudge = viewModel::nudgeTextScale,
            onJumpConsumed = viewModel::consumeJump,
            onToggleChrome = onToggleChrome,
        )
    }
}
