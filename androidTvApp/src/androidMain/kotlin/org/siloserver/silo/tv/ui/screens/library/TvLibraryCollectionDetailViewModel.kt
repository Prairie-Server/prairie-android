package org.siloserver.silo.tv.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.siloserver.silo.model.catalog.BrowseItem
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.SectionRepository
import org.siloserver.silo.tv.ui.util.visibleOnTv
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TvLibraryCollectionDetailViewModel(
    private val sectionRepository: SectionRepository,
    private val libraryId: Int,
    private val collectionId: String,
    val title: String,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val items: List<BrowseItem> = emptyList(),
        val hasMore: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetchedCount = 0
            when (val result = fetchVisiblePage(fromOffset = 0)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = result.data.items,
                        hasMore = result.data.hasMore,
                        error = null,
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message.ifBlank { "Failed to load collection" },
                    )
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Network error: ${result.exception.message ?: "unknown"}",
                    )
                }
            }
        }
    }

    private data class VisiblePage(val items: List<BrowseItem>, val hasMore: Boolean)

    /**
     * Fetches pages starting at [fromOffset] until one yields at least one
     * TV-visible item or the collection is exhausted. Reading/ebook entries
     * are filtered out per page ([visibleOnTv]); without draining, a page
     * that filters to empty with `hasMore=true` would strand the grid —
     * TvCatalogGrid skips pagination while its list is empty, so a
     * book-fronted collection would wrongly render as empty (Codex).
     * Advances [fetchedCount] by RAW page sizes as it goes.
     */
    private suspend fun fetchVisiblePage(fromOffset: Int): ApiResult<VisiblePage> {
        var offset = fromOffset
        while (true) {
            when (val result = sectionRepository.getLibraryCollectionItems(
                collectionId,
                offset = offset,
                limit = PAGE_SIZE,
            )) {
                is ApiResult.Success -> {
                    fetchedCount = offset + result.data.items.size
                    val visible = result.data.items.visibleOnTv()
                    val hasMore = result.data.hasMore && result.data.items.isNotEmpty()
                    if (visible.isNotEmpty() || !hasMore) {
                        return ApiResult.Success(VisiblePage(visible, hasMore))
                    }
                    offset = fetchedCount
                }
                is ApiResult.Error -> return ApiResult.Error(result.code, result.error, result.message)
                is ApiResult.NetworkError -> return ApiResult.NetworkError(result.exception)
            }
        }
    }

    /**
     * Offsets track RAW fetched count, not the rendered list size — the TV
     * grid filters reading items out via [visibleOnTv], so paging by
     * `items.size` would re-fetch overlapping windows on book-heavy
     * collections.
     */
    private var fetchedCount = 0

    fun loadMore() {
        val current = _uiState.value
        if (current.isLoading || current.isLoadingMore || !current.hasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            when (val result = fetchVisiblePage(fromOffset = fetchedCount)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        items = it.items + result.data.items,
                        hasMore = result.data.hasMore,
                    )
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 60
    }
}
