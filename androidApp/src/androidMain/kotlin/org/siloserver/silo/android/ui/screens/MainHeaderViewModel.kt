package org.siloserver.silo.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.siloserver.silo.model.profile.Profile
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.ProfileRepository
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
