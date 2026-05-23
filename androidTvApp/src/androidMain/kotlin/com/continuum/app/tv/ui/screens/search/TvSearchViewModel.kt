package com.continuum.app.tv.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CatalogRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Media type filter in the search header. */
enum class TvSearchMediaType(val label: String, val wire: String?) {
    All("All", null),
    Movies("Movies", "movie"),
    Series("Series", "series"),
}

@OptIn(FlowPreview::class)
class TvSearchViewModel(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val mediaType: TvSearchMediaType = TvSearchMediaType.All,
        val items: List<BrowseItem> = emptyList(),
        val total: Int = 0,
        val hasMore: Boolean = false,
        val isLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private val pageSize = 40
    private val debounceMs = 300L

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        loadMoreJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    items = emptyList(),
                    total = 0,
                    hasMore = false,
                    isLoading = false,
                    isLoadingMore = false,
                    error = null,
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(debounceMs)
            runSearchInternal(reset = true)
        }
    }

    fun onMediaTypeChanged(mediaType: TvSearchMediaType) {
        _uiState.update { it.copy(mediaType = mediaType) }
        searchJob?.cancel()
        loadMoreJob?.cancel()
        if (_uiState.value.query.isNotBlank()) {
            searchJob = viewModelScope.launch { runSearchInternal(reset = true) }
        }
    }

    fun submitSearch() {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        val query = _uiState.value.query
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    items = emptyList(),
                    total = 0,
                    hasMore = false,
                    isLoading = false,
                    isLoadingMore = false,
                    error = null,
                )
            }
            return
        }
        searchJob = viewModelScope.launch { runSearchInternal(reset = true) }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore || state.query.isBlank()) return
        if (loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch { runSearchInternal(reset = false) }
    }

    private suspend fun runSearchInternal(reset: Boolean) {
        val state = _uiState.value
        val requestedQuery = state.query
        val requestedMediaType = state.mediaType
        val offset = if (reset) 0 else state.items.size
        _uiState.update {
            if (reset) it.copy(isLoading = true, error = null)
            else it.copy(isLoadingMore = true)
        }

        val result = catalogRepository.browse(
            source = "query",
            query = requestedQuery,
            mediaType = requestedMediaType.wire,
            offset = offset,
            limit = pageSize,
        )

        val current = _uiState.value
        if (current.query != requestedQuery || current.mediaType != requestedMediaType) {
            return
        }

        when (result) {
            is ApiResult.Success -> {
                val response = result.data
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        items = if (reset) response.items else it.items + response.items,
                        total = response.total,
                        hasMore = response.hasMore,
                        error = null,
                    )
                }
            }
            is ApiResult.Error -> _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = result.message.ifBlank { "Search failed" },
                )
            }
            is ApiResult.NetworkError -> _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = "Network error: ${result.exception.message ?: "unknown"}",
                )
            }
        }
    }
}
