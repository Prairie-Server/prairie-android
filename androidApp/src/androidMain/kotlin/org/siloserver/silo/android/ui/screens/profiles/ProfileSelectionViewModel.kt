package org.siloserver.silo.android.ui.screens.profiles

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
    /** Non-null when a delete was requested and the confirm dialog should show. */
    val deleteDialogProfile: Profile? = null,
    /** The profile this session is signed in as — deleting it needs a
     *  stronger warning and clears the local selection first. */
    val activeProfileId: String? = null,
)

class ProfileSelectionViewModel(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileSelectionUiState())
    val uiState: StateFlow<ProfileSelectionUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
    }

    /**
     * @param clearError false keeps an existing error banner (e.g. a failed
     * delete's explanation) visible across the follow-up list refresh, which
     * would otherwise silently swallow it.
     */
    fun loadProfiles(clearError: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = if (clearError) null else it.error) }

            val activeId = profileRepository.getActiveProfileId()
            when (val result = profileRepository.listProfiles()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, profiles = result.data, activeProfileId = activeId)
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

    /** Manage-mode delete tap — opens the confirmation dialog. */
    fun requestDeleteProfile(profile: Profile) {
        _uiState.update { it.copy(deleteDialogProfile = profile) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(deleteDialogProfile = null) }
    }

    fun confirmDeleteProfile() {
        val profile = _uiState.value.deleteDialogProfile ?: return
        _uiState.update { it.copy(deleteDialogProfile = null) }
        viewModelScope.launch {
            // Order matters: the DELETE itself is authorized by the signed-in
            // profile's X-Profile-Id/X-Profile-Token headers, so credentials
            // must stay intact until the server has answered. Only after a
            // successful delete of the signed-in profile do we clear the local
            // selection — BEFORE reloading, so the list refresh doesn't ride
            // the now-invalidated profile token (previously that errored and
            // rendered an empty list — "all profiles gone").
            when (val result = profileRepository.deleteProfile(profile.id)) {
                is ApiResult.Success -> {
                    if (profile.id == _uiState.value.activeProfileId) {
                        profileRepository.clearProfile()
                        _uiState.update { it.copy(activeProfileId = null) }
                    }
                    loadProfiles()
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(error = result.message.ifBlank { "Failed to delete profile" })
                    }
                    loadProfiles(clearError = false)
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
