package com.continuum.app.tv.ui.screens.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.PersonalDataRepository
import com.continuum.app.tv.data.preferences.TvPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Powers the top-level Android TV Libraries surface. Loads all visible
 * libraries, restores the last selected library, and persists future
 * selections so the tab reopens where the user left it.
 */
class TvLibrariesViewModel(
    private val personalDataRepository: PersonalDataRepository,
    private val preferences: TvPreferences,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val libraries: List<UserLibrary> = emptyList(),
        val selectedLibraryId: Int? = null,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onLibrarySelected(libraryId: Int) {
        if (_uiState.value.selectedLibraryId == libraryId) return
        _uiState.update { it.copy(selectedLibraryId = libraryId) }
        viewModelScope.launch {
            preferences.setSelectedLibraryId(libraryId)
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val storedLibraryId = preferences.selectedLibraryId.first()
            when (val result = personalDataRepository.listUserLibraries()) {
                is ApiResult.Success -> {
                    val libraries = result.data.sortedBy { lib -> lib.sortOrder }
                    val resolvedLibraryId = libraries.firstOrNull { it.id == storedLibraryId }?.id
                        ?: libraries.firstOrNull()?.id

                    if (resolvedLibraryId != storedLibraryId) {
                        preferences.setSelectedLibraryId(resolvedLibraryId)
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            libraries = libraries,
                            selectedLibraryId = resolvedLibraryId,
                            error = null,
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message.ifBlank { "Failed to load libraries" },
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
