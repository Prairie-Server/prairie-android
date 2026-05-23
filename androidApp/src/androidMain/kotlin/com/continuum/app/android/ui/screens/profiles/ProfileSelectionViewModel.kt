package com.continuum.app.android.ui.screens.profiles

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

data class ProfileSelectionUiState(
    val profiles: List<Profile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isManageMode: Boolean = false,
    /** Non-null when a PIN-protected profile was tapped and the dialog should show. */
    val pinDialogProfile: Profile? = null,
    val pinIsVerifying: Boolean = false,
    val pinError: String? = null,
    /** Set after a profile is successfully selected. */
    val selectedProfileId: String? = null,
)

class ProfileSelectionViewModel(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileSelectionUiState())
    val uiState: StateFlow<ProfileSelectionUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
    }

    fun loadProfiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = profileRepository.listProfiles()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, profiles = result.data)
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message.ifBlank { "Failed to load profiles" },
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Network error. Please try again.")
                    }
                }
            }
        }
    }

    fun toggleManageMode() {
        _uiState.update { it.copy(isManageMode = !it.isManageMode) }
    }

    /**
     * Called when the user taps on a profile card.
     * If the profile has a PIN, opens the PIN dialog.
     * Otherwise, selects the profile immediately.
     */
    fun onProfileTapped(profile: Profile) {
        if (_uiState.value.isManageMode) {
            // In manage mode, tapping opens edit -- handled by the screen composable.
            return
        }

        if (profile.hasPin) {
            _uiState.update {
                it.copy(
                    pinDialogProfile = profile,
                    pinIsVerifying = false,
                    pinError = null,
                )
            }
        } else {
            selectProfile(profile.id)
        }
    }

    /**
     * Verifies the entered PIN against the server for the profile in the dialog.
     */
    fun onPinEntered(pin: String) {
        val profile = _uiState.value.pinDialogProfile ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(pinIsVerifying = true, pinError = null) }

            when (val result = profileRepository.verifyPin(profile.id, pin)) {
                is ApiResult.Success -> {
                    if (result.data.valid) {
                        _uiState.update { it.copy(pinIsVerifying = false, pinDialogProfile = null) }
                        selectProfile(profile.id)
                    } else {
                        _uiState.update {
                            it.copy(pinIsVerifying = false, pinError = "Incorrect PIN")
                        }
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            pinIsVerifying = false,
                            pinError = result.message.ifBlank { "Verification failed" },
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(pinIsVerifying = false, pinError = "Network error")
                    }
                }
            }
        }
    }

    fun dismissPinDialog() {
        _uiState.update {
            it.copy(pinDialogProfile = null, pinIsVerifying = false, pinError = null)
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            when (profileRepository.deleteProfile(profileId)) {
                is ApiResult.Success -> loadProfiles()
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = "Failed to delete profile") }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update { it.copy(error = "Network error") }
                }
            }
        }
    }

    /** Resets after the UI has navigated away. */
    fun onProfileSelectedConsumed() {
        _uiState.update { it.copy(selectedProfileId = null) }
    }

    private fun selectProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.selectProfile(profileId)
            _uiState.update { it.copy(selectedProfileId = profileId) }
        }
    }
}
