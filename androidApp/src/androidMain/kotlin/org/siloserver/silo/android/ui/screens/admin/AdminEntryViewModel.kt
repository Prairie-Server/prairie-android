package org.siloserver.silo.android.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.siloserver.silo.model.admin.shouldShowClientAdminSurface
import org.siloserver.silo.model.auth.isActingAdmin
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.AuthRepository
import org.siloserver.silo.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Resolves whether the acting user may see admin surfaces. Client admin is
 * disabled for now, so this folds the server-side acting-admin result through
 * the shared client policy before exposing [AdminUiState.isAdminVisible].
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
            val visible = shouldShowClientAdminSurface(gateProvider())
            _uiState.update { it.copy(isLoading = false, isAdminVisible = visible) }
        }
    }
}
