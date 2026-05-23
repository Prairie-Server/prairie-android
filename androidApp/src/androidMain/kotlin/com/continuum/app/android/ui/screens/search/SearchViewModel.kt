package com.continuum.app.android.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CatalogRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the search screen.
 */
data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<BrowseItem> = emptyList(),
    val hasMore: Boolean = false,
    val total: Int = 0,
    val error: String? = null,
    val hasSearched: Boolean = false,
)

/**
 * ViewModel for the search screen.
 *
 * Performs real-time search with 300ms debounce as the user types.
 * Results are loaded in a grid with the same [BrowseItem] model as browse.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _queryFlow = MutableStateFlow("")

    private val pageSize = 60

    init {
        // Debounce search queries
        viewModelScope.launch {
            _queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _uiState.update {
                            it.copy(
                                results = emptyList(),
                                hasMore = false,
                                total = 0,
                                isSearching = false,
                                error = null,
                                hasSearched = false,
                            )
                        }
                    } else {
                        performSearch(query, reset = true)
                    }
                }
        }
    }

    /**
     * Called as the user types in the search field.
     */
    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        _queryFlow.value = query
    }

    /**
     * Clears the search query and results.
     */
    fun clearSearch() {
        _uiState.update {
            SearchUiState()
        }
        _queryFlow.value = ""
    }

    /**
     * Loads more results for the current query.
     */
    fun loadMore() {
        val current = _uiState.value
        if (current.isSearching || !current.hasMore || current.query.isBlank()) return
        viewModelScope.launch {
            performSearch(current.query, reset = false)
        }
    }

    private suspend fun performSearch(query: String, reset: Boolean) {
        val currentState = _uiState.value
        val offset = if (reset) 0 else currentState.results.size

        _uiState.update { it.copy(isSearching = true, error = null) }

        when (val result = catalogRepository.browse(
            source = "query",
            query = query,
            offset = offset,
            limit = pageSize,
        )) {
            is ApiResult.Success -> {
                val response = result.data
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        results = if (reset) response.items else it.results + response.items,
                        hasMore = response.hasMore,
                        total = response.total,
                        error = null,
                        hasSearched = true,
                    )
                }
            }
            is ApiResult.Error -> {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        error = result.message.ifBlank { "Search failed" },
                        hasSearched = true,
                    )
                }
            }
            is ApiResult.NetworkError -> {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        error = "Network error. Check your connection.",
                        hasSearched = true,
                    )
                }
            }
        }
    }
}
