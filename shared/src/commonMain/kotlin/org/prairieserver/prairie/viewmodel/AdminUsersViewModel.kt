package org.prairieserver.prairie.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.model.admin.AdminUser
import org.prairieserver.prairie.model.admin.UpdateUserRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.errorMessage
import org.prairieserver.prairie.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUsersUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val users: List<AdminUser> = emptyList(),
    val error: String? = null,
    /** One-shot user-facing message after a mutation (toast/snackbar). */
    val message: String? = null,
)

/**
 * Shared admin users list ViewModel. Mirrors [AdminStatsViewModel]:
 * generation-gated fetches, pull-to-refresh and server-message error surfacing.
 * Owns the list + delete; create/edit are driven by [AdminUserEditViewModel],
 * after which the list re-loads on screen re-entry.
 */
class AdminUsersViewModel(
    private val repository: AdminRepository,
) : ViewModel() {

    private var loadGeneration = 0
    private val _uiState = MutableStateFlow(AdminUsersUiState())
    val uiState: StateFlow<AdminUsersUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetch(generation)
        }
    }

    fun refresh() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            fetch(generation)
            if (generation == loadGeneration) {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun deleteUser(id: Int) {
        viewModelScope.launch {
            when (val result = repository.deleteUser(id)) {
                is ApiResult.Success -> _uiState.update { s ->
                    s.copy(users = s.users.filter { it.id != id }, message = "User deleted")
                }
                is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                    it.copy(message = result.errorMessage("Failed to delete user"))
                }
            }
        }
    }

    /** Update a user's role ("admin"/"user") via the admin update endpoint. */
    fun setRole(id: Int, role: String) {
        viewModelScope.launch {
            when (val result = repository.updateUser(id, UpdateUserRequest(role = role))) {
                is ApiResult.Success -> _uiState.update { s ->
                    s.copy(users = s.users.map { if (it.id == id) result.data else it }, message = "Role updated")
                }
                is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                    it.copy(message = result.errorMessage("Failed to update role"))
                }
            }
        }
    }

    /** Enable or disable a user account. */
    fun setEnabled(id: Int, enabled: Boolean) {
        viewModelScope.launch {
            when (val result = repository.updateUser(id, UpdateUserRequest(enabled = enabled))) {
                is ApiResult.Success -> _uiState.update { s ->
                    s.copy(
                        users = s.users.map { if (it.id == id) result.data else it },
                        message = if (enabled) "User enabled" else "User disabled",
                    )
                }
                is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                    it.copy(message = result.errorMessage("Failed to update user"))
                }
            }
        }
    }

    /** Clears the one-shot [AdminUsersUiState.message] after it has been shown. */
    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private suspend fun fetch(generation: Int) {
        val result = repository.getUsers()
        if (generation != loadGeneration) return
        when (result) {
            is ApiResult.Success -> _uiState.update {
                it.copy(isLoading = false, users = result.data, error = null)
            }
            is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                it.copy(isLoading = false, error = result.errorMessage("Failed to load users"))
            }
        }
    }
}
