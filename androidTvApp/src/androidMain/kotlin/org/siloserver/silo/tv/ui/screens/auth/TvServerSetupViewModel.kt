package org.siloserver.silo.tv.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.siloserver.silo.network.AndroidServerRegistry
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvServerSetupUiState(
    val serverUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Destination after a successful server probe; consumed by the screen. */
    val navigateTo: TvServerSetupDestination? = null,
)

/**
 * Where the URL probe lands the user. Mirrors the phone's
 * `ServerSetupDestination` so the TV flow matches the gold-standard logic:
 *
 *  - [Setup]   the server has no admin yet → first-admin creation form.
 *  - [Login]   the server is ready → sign-in. `signupEnabled` decides whether
 *              the login screen offers a "Create account" affordance.
 */
sealed class TvServerSetupDestination {
    data object Setup : TvServerSetupDestination()
    data class Login(val signupEnabled: Boolean) : TvServerSetupDestination()
}

/**
 * TV variant of the phone app's `ServerSetupViewModel`.
 *
 * Probes the entered server and routes to the matching flow:
 *  1. needs setup → first-admin creation ([TvSetupScreen]).
 *  2. set up → login ([TvLoginScreen]); signup availability is forwarded so
 *     the login screen can surface the invite-code signup ([TvSignupScreen]).
 *
 * Parity note: earlier TV builds intentionally dead-ended un-set-up servers
 * ("complete setup on a phone first"). That restriction is lifted — a TV can
 * now bootstrap a fresh server and join via invite, matching the phone.
 */
class TvServerSetupViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvServerSetupUiState())
    val uiState: StateFlow<TvServerSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = authRepository.getServerUrl()
            // Skip the shared TokenManager's `http://localhost:8090` placeholder
            // default — a TV device can't reach its own localhost, and showing
            // that URL forces every user to delete it before typing their real
            // server. The placeholder prop on the TextField gives better UX.
            if (saved.isNotBlank() && saved != LOCALHOST_PLACEHOLDER) {
                _uiState.update { it.copy(serverUrl = saved) }
            }
        }
    }

    private companion object {
        private const val LOCALHOST_PLACEHOLDER = "http://localhost:8090"
    }

    fun onServerUrlChanged(url: String) {
        _uiState.update { it.copy(serverUrl = url, error = null) }
    }

    fun onConnectClick() {
        val raw = _uiState.value.serverUrl.trim()
        if (raw.isBlank()) {
            _uiState.update { it.copy(error = "Enter a server URL") }
            return
        }
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) {
            raw
        } else "https://$raw"
        val normalized = AndroidServerRegistry.normalizeUrl(withScheme)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // Save the URL so all subsequent API calls target this server.
            authRepository.setServerUrl(normalized)

            // Step 1: does the server need first-time setup (no admin yet)?
            when (val result = authRepository.getSetupStatus()) {
                is ApiResult.Success -> {
                    if (result.data.needsSetup) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                navigateTo = TvServerSetupDestination.Setup,
                            )
                        }
                        return@launch
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Could not reach server: ${result.message}",
                        )
                    }
                    return@launch
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Network error. Check the URL and try again.",
                        )
                    }
                    return@launch
                }
            }

            // Step 2: server is set up — check whether public signup is enabled.
            val signupEnabled = when (val signupResult = authRepository.getSignupStatus()) {
                is ApiResult.Success -> signupResult.data.enabled
                else -> false // If we can't determine, default to no signup.
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    navigateTo = TvServerSetupDestination.Login(signupEnabled = signupEnabled),
                )
            }
        }
    }

    fun onNavigationConsumed() {
        _uiState.update { it.copy(navigateTo = null) }
    }
}
