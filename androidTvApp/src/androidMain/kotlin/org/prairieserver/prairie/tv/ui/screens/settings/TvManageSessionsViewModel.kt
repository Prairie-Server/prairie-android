package org.prairieserver.prairie.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.model.auth.AuthSession
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.errorMessage
import org.prairieserver.prairie.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvManageSessionsUiState(
    val isLoading: Boolean = true,
    val sessions: List<AuthSession> = emptyList(),
    val busyIds: Set<String> = emptySet(),
    val error: String? = null,
    val message: String? = null,
)

/**
 * TV "Manage Sessions" — lists the signed-in user's own active login sessions
 * and revokes them, via [AuthRepository.getSessions]/[AuthRepository.deleteSession]
 * (the same shared API the phone's Settings → Manage Sessions uses).
 */
class TvManageSessionsViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvManageSessionsUiState())
    val uiState: StateFlow<TvManageSessionsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = authRepository.getSessions()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        // Only active (non-revoked) sessions are manageable.
                        sessions = result.data.filter { s -> s.revokedAt == null },
                        error = null,
                    )
                }
                is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, error = result.errorMessage("Failed to load sessions"))
                }
            }
        }
    }

    fun revoke(id: String) {
        if (id in _uiState.value.busyIds) return
        viewModelScope.launch {
            _uiState.update { it.copy(busyIds = it.busyIds + id) }
            when (val result = authRepository.deleteSession(id)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            sessions = it.sessions.filterNot { s -> s.id == id },
                            busyIds = it.busyIds - id,
                            message = "Session revoked",
                        )
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                    it.copy(busyIds = it.busyIds - id, message = result.errorMessage("Failed to revoke session"))
                }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
