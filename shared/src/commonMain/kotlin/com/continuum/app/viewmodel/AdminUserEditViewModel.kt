package com.continuum.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.admin.AdminUser
import com.continuum.app.model.admin.CreateUserRequest
import com.continuum.app.model.admin.UpdateUserRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.errorMessage
import com.continuum.app.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Known role options for the create/edit form's role picker. */
val ADMIN_USER_ROLES: List<String> = listOf("user", "admin")

data class AdminUserEditUiState(
    /** null in create mode; the existing user id in edit mode. */
    val userId: Int? = null,
    val username: String = "",
    val email: String = "",
    /** Create: the new password. Edit: optional reset value (blank = unchanged). */
    val password: String = "",
    val role: String = "user",
    val enabled: Boolean = true,
    /** Comma-separated library id list, surfaced verbatim for editing. */
    val libraryIdsText: String = "",
    val maxStreamsText: String = "",
    val maxTranscodesText: String = "",
    val maxProfilesText: String = "",
    val downloadAllowed: Boolean = false,
    val downloadTranscodeAllowed: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** Inline validation / load / save error. */
    val error: String? = null,
    /** Set once the save succeeds so the screen can pop back. */
    val saveSuccess: Boolean = false,
) {
    val isEditMode: Boolean get() = userId != null
}

/**
 * Shared ViewModel for the admin user create/edit form. Constructed without an
 * id (mirroring EditProfileViewModel); the screen calls [load] once with the
 * target id (or null for create). [submit] validates via the pure helpers in
 * AdminUserForm and routes to create/update, omitting unset optional fields so
 * the server's pointer "keep current value" semantics apply.
 */
class AdminUserEditViewModel(
    private val repository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUserEditUiState())
    val uiState: StateFlow<AdminUserEditUiState> = _uiState.asStateFlow()

    private var loaded = false

    /** Loads the user for edit, or initialises create mode. Idempotent. */
    fun load(userId: Int?) {
        if (loaded) return
        loaded = true
        if (userId == null) return // create mode keeps defaults
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.getUser(userId)) {
                is ApiResult.Success -> _uiState.update { it.fromUser(result.data) }
                is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, error = result.errorMessage("Failed to load user"))
                }
            }
        }
    }

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, error = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun onRoleChange(value: String) = _uiState.update { it.copy(role = value) }
    fun onEnabledChange(value: Boolean) = _uiState.update { it.copy(enabled = value) }
    fun onLibraryIdsChange(value: String) = _uiState.update { it.copy(libraryIdsText = value) }
    fun onMaxStreamsChange(value: String) = _uiState.update { it.copy(maxStreamsText = value) }
    fun onMaxTranscodesChange(value: String) = _uiState.update { it.copy(maxTranscodesText = value) }
    fun onMaxProfilesChange(value: String) = _uiState.update { it.copy(maxProfilesText = value) }
    fun onDownloadAllowedChange(value: Boolean) = _uiState.update { it.copy(downloadAllowed = value) }
    fun onDownloadTranscodeAllowedChange(value: Boolean) =
        _uiState.update { it.copy(downloadTranscodeAllowed = value) }

    fun submit() {
        val state = _uiState.value
        val validation = if (state.isEditMode) {
            validatePasswordReset(state.password)
        } else {
            validateCreateUser(state.username, state.email, state.password)
        }
        if (validation != null) {
            _uiState.update { it.copy(error = validation) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val result = if (state.userId == null) state.create() else state.update(state.userId)
            when (result) {
                is ApiResult.Success -> _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isSaving = false, error = result.errorMessage("Failed to save user"))
                }
            }
        }
    }

    private suspend fun AdminUserEditUiState.create(): ApiResult<AdminUser> =
        repository.createUser(
            CreateUserRequest(
                username = username.trim(),
                email = email.trim(),
                password = password,
                role = role,
                libraryIds = parseLibraryIds(libraryIdsText),
                maxStreams = parseQuota(maxStreamsText),
                maxTranscodes = parseQuota(maxTranscodesText),
                maxProfiles = parseQuota(maxProfilesText),
                downloadAllowed = downloadAllowed,
                downloadTranscodeAllowed = downloadTranscodeAllowed,
            ),
        )

    private suspend fun AdminUserEditUiState.update(id: Int): ApiResult<AdminUser> =
        repository.updateUser(
            id,
            UpdateUserRequest(
                role = role,
                enabled = enabled,
                password = password.ifBlank { null },
                libraryIds = parseLibraryIdsOrNull(libraryIdsText),
                maxStreams = parseQuota(maxStreamsText),
                maxTranscodes = parseQuota(maxTranscodesText),
                maxProfiles = parseQuota(maxProfilesText),
                downloadAllowed = downloadAllowed,
                downloadTranscodeAllowed = downloadTranscodeAllowed,
            ),
        )
}

private fun AdminUserEditUiState.fromUser(user: AdminUser): AdminUserEditUiState = copy(
    userId = user.id,
    username = user.username,
    email = user.email,
    password = "",
    role = user.role,
    enabled = user.enabled,
    libraryIdsText = user.libraryIds.joinToString(", "),
    maxStreamsText = user.maxStreams.takeIf { it > 0 }?.toString().orEmpty(),
    maxTranscodesText = user.maxTranscodes.takeIf { it > 0 }?.toString().orEmpty(),
    maxProfilesText = user.maxProfiles.takeIf { it > 0 }?.toString().orEmpty(),
    downloadAllowed = user.downloadAllowed,
    downloadTranscodeAllowed = user.downloadTranscodeAllowed,
    isLoading = false,
    error = null,
)
