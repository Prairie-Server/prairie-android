package org.siloserver.silo.android.ui.screens.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.siloserver.silo.model.catalog.BrowseItem
import org.siloserver.silo.model.catalog.CatalogFiltersResponse
import org.siloserver.silo.model.catalog.MediaItemUserState
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.CatalogRepository
import org.siloserver.silo.repository.port.LocalContentState
import org.siloserver.silo.repository.port.NoOpUserItemStatePort
import org.siloserver.silo.repository.port.UserItemStatePort
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
    val selectedNamePrefix: String? = null,
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
    private val userItemState: UserItemStatePort = NoOpUserItemStatePort,
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

    fun selectNamePrefix(prefix: String?) {
        val normalizedPrefix = normalizeCatalogNamePrefix(prefix)
        if (_uiState.value.selectedNamePrefix == normalizedPrefix) return
        _uiState.update {
            it.copy(
                selectedNamePrefix = normalizedPrefix,
                items = emptyList(),
                total = 0,
                hasMore = false,
            )
        }
        loadItems(reset = true)
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

    /**
     * Overlay local optimistic watched/favorite onto the grid items (local non-null
     * wins), so a mutation made offline — or just-made online before the next
     * refresh — shows immediately instead of the stale server snapshot. Mirrors
     * [org.siloserver.silo.viewmodel.HomeViewModel]. No-op on the default port.
     */
    private suspend fun overlayLocalState(items: List<BrowseItem>): List<BrowseItem> {
        val ids = items.map { it.contentId }.distinct()
        if (ids.isEmpty()) return items
        val local: Map<String, LocalContentState> = userItemState.localContentStates(ids)
        if (local.isEmpty()) return items
        return items.map { item ->
            val ls = local[item.contentId] ?: return@map item
            val base = item.userState ?: MediaItemUserState()
            item.copy(
                userState = base.copy(
                    played = ls.watched ?: base.played,
                    isFavorite = ls.favorite ?: base.isFavorite,
                ),
            )
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
                namePrefix = currentState.selectedNamePrefix,
            )

            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    // Overlay local optimistic watched/favorite so an offline mutation
                    // shows immediately on the cached grid (mirrors Home).
                    val overlaid = overlayLocalState(response.items)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            items = if (reset) overlaid else it.items + overlaid,
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
