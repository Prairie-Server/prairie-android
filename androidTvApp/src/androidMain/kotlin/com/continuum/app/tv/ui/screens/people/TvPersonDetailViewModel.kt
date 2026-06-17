package com.continuum.app.tv.ui.screens.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.catalog.Person
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.tv.ui.util.visibleOnTv
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Filmography media-type filter — mirrors the phone `PersonMediaFilter`. */
enum class TvPersonMediaFilter(val title: String, val mediaType: String?) {
    All("All", null),
    Movies("Movies", "movie"),
    Series("Series", "series"),
}

data class TvPersonDetailUiState(
    val isLoading: Boolean = true,
    val person: Person? = null,
    val items: List<BrowseItem> = emptyList(),
    val isLoadingItems: Boolean = false,
    val selectedFilter: TvPersonMediaFilter = TvPersonMediaFilter.All,
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
    private val personId: Int,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvPersonDetailUiState())
    val uiState: StateFlow<TvPersonDetailUiState> = _uiState.asStateFlow()

    init {
        if (personId > 0) reload()
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
                    loadItems(_uiState.value.selectedFilter)
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
        loadItems(filter)
    }

    private var itemsGeneration = 0

    private fun loadItems(filter: TvPersonMediaFilter) {
        val gen = ++itemsGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingItems = true) }
            val result = catalogRepository.getPersonItems(
                personId = personId,
                mediaType = filter.mediaType,
                offset = 0,
                limit = 60,
            )
            // Drop a stale response from a superseded filter selection.
            if (gen != itemsGeneration) return@launch
            when (result) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isLoadingItems = false,
                        // TV hides ebook/comic/etc. media types — keep the
                        // filmography consistent with the rest of the TV catalog.
                        items = result.data.items.visibleOnTv(),
                    )
                }
                else -> _uiState.update { it.copy(isLoadingItems = false) }
            }
        }
    }
}
