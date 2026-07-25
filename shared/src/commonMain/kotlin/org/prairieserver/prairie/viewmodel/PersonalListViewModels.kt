package org.prairieserver.prairie.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.model.catalog.BrowseItem
import org.prairieserver.prairie.model.catalog.CatalogResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.repository.PersonalDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared UI state for paginated personal lists (favorites, watchlist, history).
 */
data class PersonalListUiState(
    val items: List<BrowseItem> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val total: Int = 0,
)

/**
 * Abstract base for personal-data list ViewModels. Subclasses provide the
 * repository fetch function; everything else (pagination, refresh, state
 * management) is shared.
 */
abstract class PersonalListViewModel(
    private val pageSize: Int = 40,
) : ViewModel() {

    protected val _uiState = MutableStateFlow(PersonalListUiState())
    val uiState: StateFlow<PersonalListUiState> = _uiState.asStateFlow()

    /**
     * True once the `init` load has settled with content on screen (a successful
     * page, or a failure while items are already showing). Screens use this to
     * gate their ON_RESUME re-fetch on "the initial load actually completed"
     * instead of counting resume events in composition-scoped state, which is
     * recreated (and so mis-counts) on every re-entry to the composition.
     */
    var hasLoadedOnce: Boolean = false
        private set

    protected abstract suspend fun fetchPage(offset: Int, limit: Int): ApiResult<CatalogResponse>

    protected fun loadInitial() {
        load(reset = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        load(reset = false)
    }

    fun retry() = load(reset = true)

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            val offset = 0
            when (val r = fetchPage(offset, pageSize)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        items = r.data.items,
                        hasMore = r.data.hasMore,
                        total = r.data.total,
                        isRefreshing = false,
                        error = null,
                    )
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    private fun load(reset: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            val offset = if (reset) 0 else state.items.size
            _uiState.update {
                if (reset) it.copy(isLoading = true, error = null)
                else it.copy(isLoadingMore = true)
            }
            when (val r = fetchPage(offset, pageSize)) {
                is ApiResult.Success -> {
                    hasLoadedOnce = true
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            items = if (reset) r.data.items else it.items + r.data.items,
                            hasMore = r.data.hasMore,
                            total = r.data.total,
                            error = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    if (_uiState.value.items.isNotEmpty()) hasLoadedOnce = true
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = r.message.ifBlank { "Failed to load" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    if (_uiState.value.items.isNotEmpty()) hasLoadedOnce = true
                    _uiState.update {
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
}

class FavoritesViewModel(
    private val personalDataRepository: PersonalDataRepository,
) : PersonalListViewModel() {

    init {
        loadInitial()
    }

    override suspend fun fetchPage(offset: Int, limit: Int) =
        personalDataRepository.listFavorites(offset = offset, limit = limit)

    fun toggleFavorite(itemId: String) {
        viewModelScope.launch {
            personalDataRepository.toggleFavorite(itemId, false)
            // Optimistically remove from list
            _uiState.update { state ->
                state.copy(
                    items = state.items.filter { it.contentId != itemId },
                    total = (state.total - 1).coerceAtLeast(0),
                )
            }
        }
    }
}

class WatchlistViewModel(
    private val personalDataRepository: PersonalDataRepository,
) : PersonalListViewModel() {

    init {
        loadInitial()
    }

    override suspend fun fetchPage(offset: Int, limit: Int) =
        personalDataRepository.listWatchlist(offset = offset, limit = limit)

    fun removeFromWatchlist(itemId: String) {
        viewModelScope.launch {
            personalDataRepository.toggleWatchlist(itemId, false)
            _uiState.update { state ->
                state.copy(
                    items = state.items.filter { it.contentId != itemId },
                    total = (state.total - 1).coerceAtLeast(0),
                )
            }
        }
    }
}

class HistoryViewModel(
    private val personalDataRepository: PersonalDataRepository,
) : PersonalListViewModel() {

    init {
        loadInitial()
    }

    override suspend fun fetchPage(offset: Int, limit: Int) =
        personalDataRepository.listHistory(offset = offset, limit = limit)
}
