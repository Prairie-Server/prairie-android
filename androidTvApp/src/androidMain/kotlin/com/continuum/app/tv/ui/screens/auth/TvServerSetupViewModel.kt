package com.continuum.app.tv.ui.screens.auth

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

data class TvServerSetupUiState(
    val serverUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val serverReady: Boolean = false,
)

/**
 * TV variant of the phone app's ServerSetupViewModel, simplified for Phase 1.
 *
 * Only handles the "already set up" path. First-time-server setup (admin creation)
 * is not part of the TV app — TVs join existing servers, not bootstrap them.
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
            authRepository.setServerUrl(normalized)

            when (val result = authRepository.getSetupStatus()) {
                is ApiResult.Success -> {
                    if (result.data.needsSetup) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "This server is not set up yet. Complete setup on a phone or web browser first.",
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false, serverReady = true) }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Could not reach server: ${result.message}",
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Network error. Check the URL and try again.",
                        )
                    }
                }
            }
        }
    }

    fun onNavigationConsumed() {
        _uiState.update { it.copy(serverReady = false) }
    }
}
