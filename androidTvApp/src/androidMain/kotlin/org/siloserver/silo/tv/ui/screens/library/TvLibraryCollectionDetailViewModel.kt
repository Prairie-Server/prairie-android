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
            when (
                val result = sectionRepository.getLibraryCollectionItems(
                    collectionId,
                    offset = 0,
                    limit = PAGE_SIZE,
                )
            ) {
                is ApiResult.Success -> {
                    fetchedCount = result.data.items.size
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = result.data.items.visibleOnTv(),
                            hasMore = result.data.hasMore,
                            error = null,
                        )
                    }
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
            when (
                val result = sectionRepository.getLibraryCollectionItems(
                    collectionId,
                    offset = fetchedCount,
                    limit = PAGE_SIZE,
                )
            ) {
                is ApiResult.Success -> {
                    fetchedCount += result.data.items.size
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            items = it.items + result.data.items.visibleOnTv(),
                            hasMore = result.data.hasMore,
                        )
                    }
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
