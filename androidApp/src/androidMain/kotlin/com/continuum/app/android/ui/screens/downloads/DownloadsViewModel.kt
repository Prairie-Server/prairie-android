package com.continuum.app.android.ui.screens.downloads

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Stub model representing a downloaded media item.
 * Actual download functionality will be implemented later.
 */
data class DownloadItem(
    val id: String,
    val contentId: String,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val posterThumbhash: String? = null,
    val fileSizeBytes: Long = 0,
    val progress: Float = 0f,
    val isComplete: Boolean = false,
)

data class DownloadsUiState(
    val downloads: List<DownloadItem> = emptyList(),
    val isLoading: Boolean = false,
)

/**
 * ViewModel for the Downloads screen.
 *
 * Currently a stub -- the download list is empty because actual download
 * functionality is not yet implemented. The UI is fully built and ready
 * to be wired to a real download manager later.
 */
class DownloadsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    fun removeDownload(id: String) {
        _uiState.update { state ->
            state.copy(downloads = state.downloads.filter { it.id != id })
        }
    }
}
