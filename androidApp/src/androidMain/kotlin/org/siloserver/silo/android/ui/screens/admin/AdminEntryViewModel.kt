package org.siloserver.silo.android.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.siloserver.silo.model.admin.shouldShowClientAdminSurface
import org.siloserver.silo.model.auth.isActingAdmin
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.AuthRepository
import org.siloserver.silo.repository.ProfileRepository
import kotlinx.coroutines.delay
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
            // Bounded retry on an unresolved profile, matching the settings
            // ViewModels. isActingAdmin fails closed, and this gate guards a
            // DESTINATION: a single null read would leave a genuine owner on
            // "not authorized" for the lifetime of that back-stack entry, with
            // no way to recover once the profile resolved. getActiveProfile
            // collapses "network failed", "no active id" and "not found" into
            // null, so a retry is the only signal available.
            //
            // Bounded because not being an admin is the ordinary case, and an
            // unbounded retry would poll for every non-admin who ever lands
            // here.
            var profile = profileRepository.getActiveProfile()
            var attempt = 1
            while (profile == null && attempt < PROFILE_RESOLVE_ATTEMPTS) {
                delay(PROFILE_RESOLVE_RETRY_MS)
                profile = profileRepository.getActiveProfile()
                attempt += 1
            }
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

/** Matches the settings ViewModels: a few quick attempts, then fail closed. */
private const val PROFILE_RESOLVE_ATTEMPTS = 3
private const val PROFILE_RESOLVE_RETRY_MS = 400L
