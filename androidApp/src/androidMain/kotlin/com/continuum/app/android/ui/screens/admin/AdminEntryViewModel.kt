package com.continuum.app.android.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.auth.isActingAdmin
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.AuthRepository
import com.continuum.app.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Resolves whether the acting user may see admin surfaces. The Settings
 * "Admin" entry and the [AdminHubScreen] itself both gate on
 * [AdminUiState.isAdminVisible], mirroring the server's RequireActingAdmin
 * gate (account role == "admin" AND the active household profile is primary).
 *
 * The acting-admin decision itself lives in the shared, separately-tested
 * [isActingAdmin]; this view model only folds the current user + active
 * profile into UI state. The [gateProvider] constructor is the seam the unit
 * test drives so the folding can be verified without standing up the (final)
 * repositories — production always uses the repo-backed primary constructor.
 */
class AdminEntryViewModel(
    private val gateProvider: suspend () -> Boolean,
) : ViewModel() {

    constructor(
        authRepository: AuthRepository,
        profileRepository: ProfileRepository,
    ) : this(
        gateProvider = {
            val user = (authRepository.getCurrentUser() as? ApiResult.Success)?.data
            val profile = profileRepository.getActiveProfile()
            isActingAdmin(user, profile)
        },
    )

    data class AdminUiState(
        val isLoading: Boolean = true,
        val isAdminVisible: Boolean = false,
    )

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val visible = gateProvider()
            _uiState.update { it.copy(isLoading = false, isAdminVisible = visible) }
        }
    }
}
