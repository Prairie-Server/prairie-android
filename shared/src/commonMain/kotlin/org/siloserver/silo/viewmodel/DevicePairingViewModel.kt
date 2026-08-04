package org.siloserver.silo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.siloserver.silo.model.auth.DeviceLoginLookupResponse
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.DeviceLoginRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DevicePairingUiState(
    val token: String? = null,
    val code: String = "",
    val lookup: DeviceLoginLookupResponse? = null,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val completedStatus: String? = null,
    val error: String? = null,
) {
    /**
     * Whether approving or denying is a real, informed decision right now.
     *
     * The identifier being present is not the test. A `silo://device?token=…`
     * deep link arrives with a token already set and starts its lookup
     * automatically, so "there is something to submit" is true from
     * construction — before the server has said which device is asking, from
     * where, or with which match code. Those details are the entire content of
     * the decision, and they only exist once [lookup] resolves. Gating on the
     * identifier let a viewer approve a sign-in they could not see, and kept
     * approving available after a failed lookup had cleared [lookup] and
     * reported the request invalid or expired.
     */
    val canDecide: Boolean
        get() = lookup != null && !isSubmitting
}

class DevicePairingViewModel(
    private val repository: DeviceLoginRepository,
    initialToken: String?,
    initialCode: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DevicePairingUiState(
            token = initialToken?.takeIf { it.isNotBlank() },
            code = initialCode.orEmpty(),
        ),
    )
    val uiState: StateFlow<DevicePairingUiState> = _uiState.asStateFlow()

    init {
        if (_uiState.value.token != null || _uiState.value.code.isNotBlank()) {
            lookup()
        }
    }

    fun onCodeChanged(value: String) {
        _uiState.update {
            it.copy(
                code = value.trim().uppercase(),
                lookup = null,
                completedStatus = null,
                error = null,
            )
        }
    }

    /**
     * Bumped by every lookup, and by every decision.
     *
     * canDecide stays true while an EXISTING lookup refreshes — the previous
     * result is deliberately left on screen rather than blanked — so a viewer
     * can approve while a lookup is still in flight. If that lookup lands last
     * it overwrites the outcome: a lookup error painted over a successful
     * approval, or a decision's error quietly cleared. A decision is the more
     * authoritative event, so starting one retires any lookup already running.
     */
    private var lookupGeneration = 0

    fun lookup() {
        val current = _uiState.value
        val token = current.token?.takeIf { it.isNotBlank() }
        val code = current.code.takeIf { it.isNotBlank() }
        if (token == null && code == null) {
            _uiState.update { it.copy(error = "Enter the code shown on your TV.") }
            return
        }

        val generation = ++lookupGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, completedStatus = null) }
            val lookupResult = repository.lookup(token = token, code = code)
            // Retired while in flight: a newer lookup or, more importantly, a
            // decision has superseded this answer. Clearing isLoading is still
            // this request's job, but nothing else it has to say is current.
            if (generation != lookupGeneration) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            when (val result = lookupResult) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, lookup = result.data, error = null)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lookup = null,
                            error = pairingError(result.code, result.message),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Network error. Check your connection.")
                    }
                }
            }
        }
    }

    fun approve() {
        decide(approve = true)
    }

    fun deny() {
        decide(approve = false)
    }

    private fun decide(approve: Boolean) {
        val current = _uiState.value
        val token = current.token?.takeIf { it.isNotBlank() }
        val code = current.code.takeIf { it.isNotBlank() }
        if (token == null && code == null) {
            _uiState.update { it.copy(error = "Enter the code shown on your TV.") }
            return
        }

        // Retires any lookup already running, before it can report back over
        // the decision this is about to make.
        lookupGeneration++
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null, completedStatus = null) }
            val result = if (approve) {
                repository.approve(token = token, code = code)
            } else {
                repository.deny(token = token, code = code)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            completedStatus = result.data.status,
                            error = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = pairingError(result.code, result.message),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(isSubmitting = false, error = "Network error. Check your connection.")
                    }
                }
            }
        }
    }

    private fun pairingError(code: Int, message: String): String = when (code) {
        401 -> "Sign in before approving this device."
        404 -> "This device sign-in request was not found."
        409 -> message.ifBlank { "This device sign-in request is no longer active." }
        410 -> "This device sign-in request has expired."
        else -> message.ifBlank { "Device pairing failed." }
    }
}
