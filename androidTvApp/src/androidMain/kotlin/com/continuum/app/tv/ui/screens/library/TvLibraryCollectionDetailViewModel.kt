package com.continuum.app.tv.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.SectionRepository
import com.continuum.app.tv.ui.util.visibleOnTv
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
        val items: List<BrowseItem> = emptyList(),
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
            when (val result = sectionRepository.getLibraryCollectionItems(libraryId, collectionId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = result.data.items.visibleOnTv(),
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
}
