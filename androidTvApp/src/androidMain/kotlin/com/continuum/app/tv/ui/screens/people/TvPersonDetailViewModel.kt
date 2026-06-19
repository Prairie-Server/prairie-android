package com.continuum.app.tv.ui.screens.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.catalog.Person
import com.continuum.app.model.catalog.personWorksFiltersForTv
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.tv.ui.util.visibleOnTv
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TvPersonWorksPageSize = 60

/** Filmography media-type filter — mirrors the phone `PersonMediaFilter`, minus Reading. */
enum class TvPersonMediaFilter(val key: String, val title: String, val mediaType: String?) {
    All("all", "All", null),
    Movies("movie", "Movies", "movie"),
    Series("series", "TV", "series"),
    Audiobooks("audiobook", "Audiobooks", "audiobook"),
    Music("music", "Music", "music");

    companion object {
        fun fromKey(key: String): TvPersonMediaFilter? =
            entries.firstOrNull { it.key == key }
    }
}

private val TvPersonMediaFilters: List<TvPersonMediaFilter> =
    personWorksFiltersForTv().mapNotNull { TvPersonMediaFilter.fromKey(it.key) }

data class TvPersonDetailUiState(
    val isLoading: Boolean = true,
    val person: Person? = null,
    val items: List<BrowseItem> = emptyList(),
    val isLoadingItems: Boolean = false,
    val selectedFilter: TvPersonMediaFilter = TvPersonMediaFilter.All,
    val availableFilters: List<TvPersonMediaFilter> = TvPersonMediaFilters,
    val totalItems: Int = 0,
    val hasMore: Boolean = false,
    val pagingError: String? = null,
    val error: String? = null,
)

/**
 * Drives the Android TV person detail screen — the cast/crew profile plus their
 * filmography. Replicates the phone-only `PersonDetailViewModel` (which lives in
 * `androidApp`) against the same shared [CatalogRepository], loading the person
 * from `/api/v1/people/{id}` and the filmography from
 * `/api/v1/catalog?source=person&person_id=…`.
 *
 * Receives `personId` via Koin `parametersOf()` (see
 * [com.continuum.app.tv.di.androidTvModule]), matching the [TvItemDetailViewModel]
 * wiring pattern rather than the phone's `SavedStateHandle` injection.
 */
class TvPersonDetailViewModel(
    private val catalogRepository: CatalogRepository,
    private val personId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvPersonDetailUiState())
    val uiState: StateFlow<TvPersonDetailUiState> = _uiState.asStateFlow()

    init {
        if (personId > 0L) reload()
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = catalogRepository.getPerson(personId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            person = result.data,
                            error = null,
                        )
                    }
                    loadItems(_uiState.value.selectedFilter, reset = true)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message.ifBlank { "Failed to load person" },
                    )
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Network error. Check your connection.",
                    )
                }
            }
        }
    }

    fun applyFilter(filter: TvPersonMediaFilter) {
        if (filter == _uiState.value.selectedFilter) return
        _uiState.update { it.copy(selectedFilter = filter, items = emptyList()) }
        loadItems(filter, reset = true)
    }

    private var itemsGeneration = 0
    private var nextRawOffset = 0
    private var snapshotAt: String? = null

    fun loadMoreIfNeeded() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoadingItems) return
        loadItems(state.selectedFilter, reset = false)
    }

    private fun resetPaging() {
        nextRawOffset = 0
        snapshotAt = null
    }

    private fun loadItems(filter: TvPersonMediaFilter, reset: Boolean) {
        val gen = if (reset) ++itemsGeneration else itemsGeneration
        if (reset) resetPaging()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingItems = true,
                    pagingError = null,
                    items = if (reset) emptyList() else it.items,
                    totalItems = if (reset) 0 else it.totalItems,
                    hasMore = if (reset) false else it.hasMore,
                )
            }
            val result = catalogRepository.getPersonItems(
                personId = personId,
                mediaType = filter.mediaType,
                offset = nextRawOffset,
                limit = TvPersonWorksPageSize,
                snapshotAt = snapshotAt,
            )
            // Drop a stale response from a superseded filter selection.
            if (gen != itemsGeneration) return@launch
            when (result) {
                is ApiResult.Success -> {
                    if (snapshotAt == null) snapshotAt = result.data.snapshot
                    nextRawOffset += result.data.items.size
                    val visibleItems = result.data.items.visibleOnTv()
                    _uiState.update {
                        it.copy(
                            isLoadingItems = false,
                            // TV hides ebook/comic/etc. media types — keep the
                            // works grid consistent with the rest of the TV catalog.
                            items = if (reset) visibleItems else it.items + visibleItems,
                            totalItems = result.data.total,
                            hasMore = result.data.hasMore,
                            pagingError = null,
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        isLoadingItems = false,
                        pagingError = result.message.ifBlank { "Failed to load works" },
                    )
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(
                        isLoadingItems = false,
                        pagingError = "Network error. Check your connection.",
                    )
                }
            }
        }
    }
}
