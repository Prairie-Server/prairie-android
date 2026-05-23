package com.continuum.app.tv.ui.screens.profiles

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.profile.Profile
import com.continuum.app.network.ApiResult
import com.continuum.app.network.TokenManager
import com.continuum.app.repository.ProfileRepository
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
    // PIN entry flow.
    val pinProfile: Profile? = null,
    val isVerifyingPin: Boolean = false,
    val pinError: String? = null,
)

/**
 * TV profile picker. PIN-protected profiles now work: selecting one opens a
 * [com.continuum.app.tv.ui.components.TvPinEntryDialog] which drives the PIN
 * flow in this ViewModel. Non-PIN profiles still route directly.
 */
class TvProfileSelectionViewModel(
    private val profileRepository: ProfileRepository,
    private val tokenManager: TokenManager,
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

    fun onProfileSelected(profile: Profile, context: Context) {
        if (profile.hasPin) {
            // Open the PIN dialog; actual selection happens in onPinEntered.
            _uiState.update {
                it.copy(pinProfile = profile, pinError = null, isVerifyingPin = false)
            }
            return
        }
        commitSelection(profile, context)
    }

    fun onPinDialogDismissed() {
        _uiState.update {
            it.copy(pinProfile = null, pinError = null, isVerifyingPin = false)
        }
    }

    fun onPinEntered(pin: String, context: Context) {
        val profile = _uiState.value.pinProfile ?: return
        _uiState.update { it.copy(isVerifyingPin = true, pinError = null) }
        viewModelScope.launch {
            when (val r = profileRepository.verifyPin(profile.id, pin)) {
                is ApiResult.Success -> {
                    // Repository stores the profile token internally. Still persist
                    // the profileId to prefs so the next launch jumps to Main.
                    commitSelection(profile, context)
                    _uiState.update { it.copy(pinProfile = null, isVerifyingPin = false) }
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

    private fun commitSelection(profile: Profile, context: Context) {
        viewModelScope.launch {
            profileRepository.selectProfile(profile.id)
            context.getSharedPreferences("continuum_auth", Context.MODE_PRIVATE)
                .edit()
                .putString("profileId", profile.id)
                .apply()
            _uiState.update { it.copy(selectedProfileId = profile.id) }
        }
    }

    fun onSelectionConsumed() {
        _uiState.update { it.copy(selectedProfileId = null) }
    }
}
