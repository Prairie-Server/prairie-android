package com.continuum.app.android.ui.screens.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.catalog.Person
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PersonMediaFilter(val title: String, val mediaType: String?) {
    All("All", null),
    Movies("Movies", "movie"),
    Series("Series", "series"),
}

data class PersonDetailUiState(
    val isLoading: Boolean = true,
    val person: Person? = null,
    val items: List<BrowseItem> = emptyList(),
    val isLoadingItems: Boolean = false,
    val selectedFilter: PersonMediaFilter = PersonMediaFilter.All,
    val error: String? = null,
)

/**
 * Person detail viewmodel. Loads the person profile and their
 * filmography from `/api/v1/catalog?source=person&person_id=…`,
 * mirroring iOS's `PersonDetailViewModel`.
 */
class PersonDetailViewModel(
    private val catalogRepository: CatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val personId: Int = savedStateHandle.get<Int>("personId") ?: 0

    private val _uiState = MutableStateFlow(PersonDetailUiState())
    val uiState: StateFlow<PersonDetailUiState> = _uiState.asStateFlow()

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
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message.ifBlank { "Failed to load person" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Network error. Check your connection.",
                        )
                    }
                }
            }
        }
    }

    fun applyFilter(filter: PersonMediaFilter) {
        if (filter == _uiState.value.selectedFilter) return
        _uiState.update { it.copy(selectedFilter = filter, items = emptyList()) }
        loadItems(filter)
    }

    private fun loadItems(filter: PersonMediaFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingItems = true) }
            when (
                val result = catalogRepository.getPersonItems(
                    personId = personId,
                    mediaType = filter.mediaType,
                    offset = 0,
                    limit = 60,
                )
            ) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingItems = false,
                            items = result.data.items,
                        )
                    }
                }
                else -> {
                    _uiState.update { it.copy(isLoadingItems = false) }
                }
            }
        }
    }
}
