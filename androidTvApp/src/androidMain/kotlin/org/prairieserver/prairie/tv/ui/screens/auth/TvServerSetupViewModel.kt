package org.prairieserver.prairie.tv.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.model.auth.SetupStatusResponse
import org.prairieserver.prairie.model.auth.SignupStatusResponse
import org.prairieserver.prairie.network.AndroidServerRegistry
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.repository.AuthRepository
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
) {
    /** True when the entered address will connect over unencrypted HTTP.
     *  Informational only — does not block LAN/IP connections. */
    val usesCleartext: Boolean get() = serverSetupUsesCleartext(serverUrl)
}

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

internal sealed class TvServerSetupProbeResult {
    data class Success(
        val serverUrl: String,
        val destination: TvServerSetupDestination,
    ) : TvServerSetupProbeResult()

    data class Failure(
        val message: String,
        val serverUrl: String? = null,
    ) : TvServerSetupProbeResult()
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
        if (_uiState.value.isLoading) return
        val raw = _uiState.value.serverUrl.trim()
        if (raw.isBlank()) {
            _uiState.update { it.copy(error = "Enter a server URL") }
            return
        }
        val candidates = serverSetupUrlProbeCandidates(raw)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = probeTvServerSetupCandidates(
                candidates = candidates,
                getSetupStatus = { candidate -> authRepository.getSetupStatus(candidate) },
                getSignupStatus = { candidate -> authRepository.getSignupStatus(candidate) },
            )) {
                is TvServerSetupProbeResult.Success -> {
                    authRepository.setServerUrl(result.serverUrl)
                    _uiState.update {
                        it.copy(
                            serverUrl = result.serverUrl,
                            isLoading = false,
                            navigateTo = result.destination,
                        )
                    }
                }
                is TvServerSetupProbeResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            serverUrl = result.serverUrl ?: it.serverUrl,
                            isLoading = false,
                            error = result.message,
                        )
                    }
                }
            }
        }
    }

    fun onNavigationConsumed() {
        _uiState.update { it.copy(navigateTo = null) }
    }
}

internal suspend fun probeTvServerSetupCandidates(
    candidates: List<String>,
    getSetupStatus: suspend (String) -> ApiResult<SetupStatusResponse>,
    getSignupStatus: suspend (String) -> ApiResult<SignupStatusResponse>,
): TvServerSetupProbeResult {
    for ((index, candidate) in candidates.withIndex()) {
        val isLastCandidate = index == candidates.lastIndex

        when (val result = getSetupStatus(candidate)) {
            is ApiResult.Success -> {
                if (result.data.needsSetup) {
                    return TvServerSetupProbeResult.Success(
                        serverUrl = candidate,
                        destination = TvServerSetupDestination.Setup,
                    )
                }
            }
            is ApiResult.Error -> {
                if (!isLastCandidate) continue
                return TvServerSetupProbeResult.Failure(
                    serverUrl = candidate,
                    message = "Could not reach server: ${result.message}",
                )
            }
            is ApiResult.NetworkError -> {
                if (!isLastCandidate) continue
                return TvServerSetupProbeResult.Failure(
                    message = "Network error. Check the URL and try again.",
                )
            }
        }

        val signupEnabled = when (val signupResult = getSignupStatus(candidate)) {
            is ApiResult.Success -> signupResult.data.enabled
            else -> false
        }
        return TvServerSetupProbeResult.Success(
            serverUrl = candidate,
            destination = TvServerSetupDestination.Login(signupEnabled = signupEnabled),
        )
    }

    return TvServerSetupProbeResult.Failure(
        message = "Network error. Check the URL and try again.",
    )
}

/**
 * True when the entered address will connect over unencrypted HTTP: the probe
 * resolves to a single `http://` candidate (only when the user explicitly
 * typed `http://`). Informational only — never blocks LAN/IP connections.
 */
internal fun serverSetupUsesCleartext(raw: String): Boolean {
    val candidates = serverSetupUrlProbeCandidates(raw)
    return candidates.size == 1 && candidates.first().startsWith("http://", ignoreCase = true)
}

internal fun serverSetupUrlProbeCandidates(raw: String): List<String> {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isBlank()) return emptyList()
    return if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        listOf(AndroidServerRegistry.normalizeUrl(trimmed))
    } else {
        listOf(
            AndroidServerRegistry.normalizeUrl("https://$trimmed"),
            AndroidServerRegistry.normalizeUrl("http://$trimmed"),
        )
    }
}
