package org.siloserver.silo.tv.ui.screens.profiles

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

data class TvProfileSelectionUiState(
    val profiles: List<Profile> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedProfileId: String? = null,
    // Manage mode: tapping a profile opens edit, plus a delete affordance shows.
    val isManageMode: Boolean = false,
    // PIN entry flow.
    val pinProfile: Profile? = null,
    val isVerifyingPin: Boolean = false,
    val pinError: String? = null,
    // Profile pending delete confirmation (manage mode).
    val deleteCandidate: Profile? = null,
    val isDeleting: Boolean = false,
)

/**
 * TV profile picker. PIN-protected profiles now work: selecting one opens a
 * [org.siloserver.silo.tv.ui.components.TvPinEntryDialog] which drives the PIN
 * flow in this ViewModel. Non-PIN profiles still route directly.
 */
class TvProfileSelectionViewModel(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvProfileSelectionUiState())
    val uiState: StateFlow<TvProfileSelectionUiState> = _uiState.asStateFlow()

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
        _uiState.update {
            it.copy(
                isManageMode = !it.isManageMode,
                // Leaving manage mode clears any pending delete prompt.
                deleteCandidate = if (it.isManageMode) null else it.deleteCandidate,
            )
        }
    }

    /**
     * Called when a profile is activated on the selection grid. In manage mode
     * the screen routes to edit instead (it inspects [TvProfileSelectionUiState.isManageMode]),
     * so this only runs the select/PIN flow.
     */
    fun onProfileSelected(profile: Profile) {
        if (_uiState.value.isManageMode) {
            // Manage-mode taps open edit, handled by the screen composable.
            return
        }
        if (profile.hasPin) {
            // Open the PIN dialog; actual selection happens in onPinEntered.
            _uiState.update {
                it.copy(pinProfile = profile, pinError = null, isVerifyingPin = false)
            }
            return
        }
        commitSelection(profile)
    }

    fun onPinDialogDismissed() {
        _uiState.update {
            it.copy(pinProfile = null, pinError = null, isVerifyingPin = false)
        }
    }

    fun onPinEntered(pin: String) {
        val profile = _uiState.value.pinProfile ?: return
        _uiState.update { it.copy(isVerifyingPin = true, pinError = null) }
        viewModelScope.launch {
            when (val r = profileRepository.verifyPin(profile.id, pin)) {
                is ApiResult.Success -> {
                    // The server returns 200 with valid=false for a wrong PIN, so
                    // gate selection on .valid (matches phone) — never commit on a
                    // bare 200.
                    if (r.data.valid) {
                        // Repository stores the profile token and active profile.
                        commitSelection(profile)
                        _uiState.update { it.copy(pinProfile = null, isVerifyingPin = false) }
                    } else {
                        _uiState.update { it.copy(isVerifyingPin = false, pinError = "Incorrect PIN") }
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        isVerifyingPin = false,
                        pinError = r.message.ifBlank { "Incorrect PIN" },
                    )
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(
                        isVerifyingPin = false,
                        pinError = "Network error. Please try again.",
                    )
                }
            }
        }
    }

    private fun commitSelection(profile: Profile) {
        viewModelScope.launch {
            profileRepository.selectProfile(profile.id)
            _uiState.update { it.copy(selectedProfileId = profile.id) }
        }
    }

    fun onSelectionConsumed() {
        _uiState.update { it.copy(selectedProfileId = null) }
    }

    /** Opens the delete confirmation for [profile] (manage mode). */
    fun requestDelete(profile: Profile) {
        _uiState.update { it.copy(deleteCandidate = profile) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(deleteCandidate = null) }
    }

    /** Confirms deletion of the pending candidate and reloads on success. */
    fun confirmDelete() {
        val profile = _uiState.value.deleteCandidate ?: return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (profileRepository.deleteProfile(profile.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isDeleting = false, deleteCandidate = null) }
                    loadProfiles()
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deleteCandidate = null,
                        error = "Failed to delete profile",
                    )
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deleteCandidate = null,
                        error = "Network error. Please try again.",
                    )
                }
            }
        }
    }
}
