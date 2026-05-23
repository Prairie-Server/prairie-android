package com.continuum.app.tv.ui.screens.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.network.ApiResult
import com.continuum.app.network.TokenManager
import com.continuum.app.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvLoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
)

/**
 * [AuthRepository.login] returns a [com.continuum.app.model.auth.User] and silently
 * stores the tokens inside [TokenManager]. On success we read those back out and
 * persist them to SharedPreferences so the next launch can skip re-authentication.
 */
class TvLoginViewModel(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvLoginUiState())
    val uiState: StateFlow<TvLoginUiState> = _uiState.asStateFlow()

    fun onUsernameChanged(v: String) = _uiState.update { it.copy(username = v, error = null) }
    fun onPasswordChanged(v: String) = _uiState.update { it.copy(password = v, error = null) }

    fun onLoginClick(context: Context) {
        val s = _uiState.value
        if (s.username.isBlank()) {
            _uiState.update { it.copy(error = "Username is required") }
            return
        }
        if (s.password.isBlank()) {
            _uiState.update { it.copy(error = "Password is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = authRepository.login(s.username, s.password)) {
                is ApiResult.Success -> {
                    persistAuth(context)
                    _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                }
                is ApiResult.Error -> {
                    val msg = when (result.code) {
                        401 -> "Invalid username or password"
                        403 -> "Account is disabled"
                        else -> result.message.ifBlank { "Login failed" }
                    }
                    _uiState.update { it.copy(isLoading = false, error = msg) }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Network error. Check your connection.")
                    }
                }
            }
        }
    }

    private suspend fun persistAuth(context: Context) {
        val serverUrl = tokenManager.getServerUrl()
        val accessToken = tokenManager.getAccessToken()
        val refreshToken = tokenManager.getRefreshToken()
        val prefs = context.getSharedPreferences("continuum_auth", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("serverUrl", serverUrl)
            .putString("accessToken", accessToken)
            .putString("refreshToken", refreshToken)
            .apply()
    }

    fun onLoginSuccessConsumed() {
        _uiState.update { it.copy(loginSuccess = false) }
    }
}
