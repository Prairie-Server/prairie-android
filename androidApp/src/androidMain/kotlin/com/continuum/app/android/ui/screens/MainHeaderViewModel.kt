package com.continuum.app.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.profile.Profile
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainHeaderUiState(
    val isLoading: Boolean = true,
    val activeProfile: Profile? = null,
)

class MainHeaderViewModel(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainHeaderUiState())
    val uiState: StateFlow<MainHeaderUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val activeProfileId = profileRepository.getActiveProfileId()
            if (activeProfileId == null) {
                _uiState.update { it.copy(isLoading = false, activeProfile = null) }
                return@launch
            }

            when (val result = profileRepository.listProfiles()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeProfile = result.data.firstOrNull { profile ->
                                profile.id == activeProfileId
                            },
                        )
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }
}
