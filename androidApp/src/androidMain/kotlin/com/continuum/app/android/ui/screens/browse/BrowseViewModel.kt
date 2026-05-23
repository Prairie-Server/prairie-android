package com.continuum.app.android.ui.screens.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.catalog.CatalogFiltersResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Active filter selections for browsing.
 */
data class BrowseFilters(
    val genres: Set<String> = emptySet(),
    val contentRatings: Set<String> = emptySet(),
    val sort: String = "added_at",
    val order: String = "desc",
)

/**
 * UI state for the browse / catalog screen.
 */
data class BrowseUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val items: List<BrowseItem> = emptyList(),
    val hasMore: Boolean = false,
    val total: Int = 0,
    val filters: BrowseFilters = BrowseFilters(),
    val availableFilters: CatalogFiltersResponse? = null,
    val libraryId: Int? = null,
    val title: String = "Browse",
    val error: String? = null,
)

/**
 * ViewModel for the catalog/browse screen.
 *
 * Manages catalog browsing with filtering, sorting, and infinite-scroll
 * pagination. Loads available filters on first launch.
 */
class BrowseViewModel(
    private val catalogRepository: CatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private val pageSize = 40

    init {
        val libraryId = savedStateHandle.get<String>("libraryId")?.toIntOrNull()
        _uiState.update { it.copy(libraryId = libraryId) }
        loadFilters()
        loadItems(reset = true)
    }

    /**
     * Applies new filters and reloads the catalog from the beginning.
     */
    fun applyFilters(filters: BrowseFilters) {
        _uiState.update { it.copy(filters = filters) }
        loadItems(reset = true)
    }

    /**
     * Resets all filters to defaults and reloads.
     */
    fun resetFilters() {
        applyFilters(BrowseFilters())
    }

    /**
     * Loads the next page of items for infinite scroll.
     */
    fun loadMore() {
        val current = _uiState.value
        if (current.isLoadingMore || !current.hasMore) return
        loadItems(reset = false)
    }

    /**
     * Full refresh: reloads filters and items.
     */
    fun refresh() {
        loadFilters()
        loadItems(reset = true)
    }

    private fun loadFilters() {
        viewModelScope.launch {
            when (val result = catalogRepository.getFilters(_uiState.value.libraryId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(availableFilters = result.data) }
                }
                else -> { /* Filters are non-critical; ignore errors */ }
            }
        }
    }

    private fun loadItems(reset: Boolean) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val offset = if (reset) 0 else currentState.items.size
            _uiState.update {
                if (reset) it.copy(isLoading = true, error = null)
                else it.copy(isLoadingMore = true, error = null)
            }

            val filters = currentState.filters
            val result = catalogRepository.browse(
                libraryId = currentState.libraryId,
                genre = filters.genres.firstOrNull(), // API takes single genre
                contentRating = filters.contentRatings.firstOrNull(),
                sort = filters.sort,
                order = filters.order,
                offset = offset,
                limit = pageSize,
            )

            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            items = if (reset) response.items else it.items + response.items,
                            hasMore = response.hasMore,
                            total = response.total,
                            title = response.title ?: "Browse",
                            error = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = result.message.ifBlank { "Failed to load catalog" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = "Network error. Check your connection.",
                        )
                    }
                }
            }
        }
    }
}
