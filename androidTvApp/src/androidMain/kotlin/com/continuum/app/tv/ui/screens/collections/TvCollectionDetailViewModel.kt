package com.continuum.app.tv.ui.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CollectionRepository
import com.continuum.app.tv.ui.util.visibleOnTv
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for an individual collection's item grid. Receives `collectionId`
 * and `title` via Koin `parametersOf()` at construction time.
 */
class TvCollectionDetailViewModel(
    private val collectionRepository: CollectionRepository,
    private val collectionId: String,
    val title: String,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val items: List<BrowseItem> = emptyList(),
        val hasMore: Boolean = false,
        val total: Int = 0,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val pageSize = 40

    // Raw (pre-visibleOnTv-filter) loaded count = the next-page server offset.
    // Using filtered items.size would skip/duplicate when a page has hidden
    // (ebook) entries.
    private var rawLoaded = 0

    init {
        load(reset = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        load(reset = false)
    }

    fun retry() = load(reset = true)

    private fun load(reset: Boolean) {
        if (reset) rawLoaded = 0
        viewModelScope.launch {
            val state = _uiState.value
            val offset = if (reset) 0 else rawLoaded
            _uiState.update {
                if (reset) it.copy(isLoading = true, error = null)
                else it.copy(isLoadingMore = true)
            }
            when (val r = collectionRepository.getItems(collectionId, offset, pageSize)) {
                is ApiResult.Success -> _uiState.update {
                    rawLoaded = if (reset) r.data.items.size else rawLoaded + r.data.items.size
                    val visible = r.data.items.visibleOnTv()
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        items = if (reset) visible else it.items + visible,
                        hasMore = r.data.hasMore,
                        total = r.data.total,
                        error = null,
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = r.message.ifBlank { "Failed to load collection" },
                    )
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = "Network error: ${r.exception.message ?: "unknown"}",
                    )
                }
            }
        }
    }
}
