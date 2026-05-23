package com.continuum.app.android.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.network.AndroidServerRegistry
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerSetupUiState(
    val serverUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Destination after successful server validation. */
    val navigateTo: ServerSetupDestination? = null,
)

sealed class ServerSetupDestination {
    /** Server needs first-time setup (admin creation). */
    data object Setup : ServerSetupDestination()

    /** Server is ready -- go to login. Signup may or may not be available. */
    data class Login(val signupEnabled: Boolean) : ServerSetupDestination()
}

class ServerSetupViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerSetupUiState())
    val uiState: StateFlow<ServerSetupUiState> = _uiState.asStateFlow()

    init {
        // Pre-populate with previously saved server URL, if any.
        viewModelScope.launch {
            val saved = authRepository.getServerUrl()
            if (saved.isNotBlank()) {
                _uiState.update { it.copy(serverUrl = saved) }
            }
        }
    }

    fun onServerUrlChanged(url: String) {
        _uiState.update { it.copy(serverUrl = url, error = null) }
    }

    /**
     * Validates the entered server URL:
     * 1. Normalize and save the URL.
     * 2. Check setup status -- if the server needs setup, navigate there.
     * 3. Otherwise check signup status and navigate to login.
     */
    fun onConnectClick() {
        val rawUrl = _uiState.value.serverUrl.trim()
        if (rawUrl.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a server URL") }
            return
        }

        val withScheme = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else "https://$rawUrl"
        val normalized = AndroidServerRegistry.normalizeUrl(withScheme)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Save the URL so all future API calls target this server.
            authRepository.setServerUrl(normalized)

            // Step 1: check if the server needs first-time setup.
            when (val setupResult = authRepository.getSetupStatus()) {
                is ApiResult.Success -> {
                    if (setupResult.data.needsSetup) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                navigateTo = ServerSetupDestination.Setup,
                            )
                        }
                        return@launch
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Could not connect: ${setupResult.message}",
                        )
                    }
                    return@launch
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Could not reach server. Check the URL and try again.",
                        )
                    }
                    return@launch
                }
            }

            // Step 2: server exists and is set up -- check signup availability.
            val signupEnabled = when (val signupResult = authRepository.getSignupStatus()) {
                is ApiResult.Success -> signupResult.data.enabled
                else -> false // If we can't determine, default to no signup.
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    navigateTo = ServerSetupDestination.Login(signupEnabled = signupEnabled),
                )
            }
        }
    }

    /** Resets navigation state after the UI has consumed the event. */
    fun onNavigationConsumed() {
        _uiState.update { it.copy(navigateTo = null) }
    }
}
