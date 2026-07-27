package org.prairieserver.prairie.android.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.common.network.CleartextConsentStore
import org.prairieserver.prairie.common.network.cleartextOrigin
import org.prairieserver.prairie.model.auth.SetupStatusResponse
import org.prairieserver.prairie.model.auth.SignupStatusResponse
import org.prairieserver.prairie.network.AndroidServerRegistry
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.repository.AuthRepository
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerSetupUiState(
    val serverUrl: String = "",
    val selectedScheme: ServerSetupScheme = ServerSetupScheme.Auto,
    val port: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Destination after successful server validation. */
    val navigateTo: ServerSetupDestination? = null,
    val pendingCleartextUrl: String? = null,
) {
    /** True when the entered address will connect over unencrypted HTTP.
     *  Informational only — does not block connecting to LAN/IP servers. */
    val usesCleartext: Boolean
        get() = serverSetupUsesCleartext(serverUrl, selectedScheme, port)
}

enum class ServerSetupScheme(val label: String, val urlScheme: String?) {
    Auto("Auto", null),
    Https("HTTPS", "https"),
    Http("HTTP", "http"),
}

class ServerSetupValidationException(message: String) : IllegalArgumentException(message)

sealed class ServerSetupDestination {
    /** Server needs first-time setup (admin creation). */
    data object Setup : ServerSetupDestination()

    /** Server is ready -- go to login. Signup may or may not be available. */
    data class Login(val signupEnabled: Boolean) : ServerSetupDestination()
}

class ServerSetupViewModel(
    private val authRepository: AuthRepository,
    private val cleartextConsentStore: CleartextConsentStore,
    private val getSetupStatus: suspend (String) -> ApiResult<SetupStatusResponse> = {
        authRepository.getSetupStatus(it)
    },
    private val getSignupStatus: suspend (String) -> ApiResult<SignupStatusResponse> = {
        authRepository.getSignupStatus(it)
    },
    private val candidateUrls: (ServerSetupUiState) -> List<String> = {
        buildServerSetupCandidateUrls(it.serverUrl, it.selectedScheme, it.port)
    },
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerSetupUiState())
    val uiState: StateFlow<ServerSetupUiState> = _uiState.asStateFlow()
    private var pendingCleartextConnection: PendingCleartextConnection? = null

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

    fun onSchemeSelected(scheme: ServerSetupScheme) {
        _uiState.update { it.copy(selectedScheme = scheme, error = null) }
    }

    fun onPortChanged(port: String) {
        _uiState.update { it.copy(port = port, error = null) }
    }

    /**
     * Validates the entered server URL:
     * 1. Build candidate URLs from the host, protocol, and optional port.
     * 2. Try each candidate until setup status responds.
     * 3. Otherwise check signup status and navigate to login.
     */
    fun onConnectClick() {
        if (_uiState.value.isLoading) return
        val current = _uiState.value
        val candidates = try {
            candidateUrls(current)
        } catch (error: ServerSetupValidationException) {
            _uiState.update { it.copy(error = error.message) }
            return
        }

        viewModelScope.launch {
            pendingCleartextConnection = null
            _uiState.update {
                it.copy(isLoading = true, error = null, pendingCleartextUrl = null)
            }

            var lastError: String? = null
            for (candidate in candidates) {
                when (val setupResult = getSetupStatus(candidate)) {
                    is ApiResult.Success -> {
                        if (setupResult.data.needsSetup) {
                            handleSuccessfulConnection(candidate, ServerSetupDestination.Setup)
                            return@launch
                        }
                    }

                    is ApiResult.Error -> {
                        lastError = setupResult.message
                        continue
                    }

                    is ApiResult.NetworkError -> {
                        lastError = null
                        continue
                    }
                }

                val signupEnabled = when (val signupResult = getSignupStatus(candidate)) {
                    is ApiResult.Success -> signupResult.data.enabled
                    else -> false // If we can't determine, default to no signup.
                }

                handleSuccessfulConnection(
                    candidate,
                    ServerSetupDestination.Login(signupEnabled = signupEnabled),
                )
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = lastError
                        ?.takeIf { message -> message.isNotBlank() }
                        ?.let { message -> "Could not connect: $message" }
                        ?: "Could not reach a Prairie server at that address.",
                )
            }
        }
    }

    fun confirmCleartextConnection() {
        if (_uiState.value.isLoading) return
        val pending = pendingCleartextConnection ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            cleartextConsentStore.approve(pending.origin)
            if (pendingCleartextConnection !== pending) return@launch
            pendingCleartextConnection = null
            persistAndNavigate(pending.serverUrl, pending.destination)
        }
    }

    fun cancelCleartextConnection() {
        pendingCleartextConnection = null
        _uiState.update {
            it.copy(pendingCleartextUrl = null, isLoading = false)
        }
    }

    /** Resets navigation state after the UI has consumed the event. */
    fun onNavigationConsumed() {
        _uiState.update { it.copy(navigateTo = null) }
    }

    private suspend fun handleSuccessfulConnection(
        serverUrl: String,
        destination: ServerSetupDestination,
    ) {
        if (serverUrl.startsWith("http://", ignoreCase = true)) {
            val origin = cleartextOrigin(serverUrl)
            if (origin == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Could not safely identify this HTTP server.",
                        pendingCleartextUrl = null,
                        navigateTo = null,
                    )
                }
                return
            }
            if (cleartextConsentStore.isApproved(origin)) {
                persistAndNavigate(serverUrl, destination)
                return
            }
            pendingCleartextConnection = PendingCleartextConnection(
                serverUrl = serverUrl,
                origin = origin,
                destination = destination,
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    pendingCleartextUrl = origin,
                    navigateTo = null,
                )
            }
            return
        }
        persistAndNavigate(serverUrl, destination)
    }

    private suspend fun persistAndNavigate(
        serverUrl: String,
        destination: ServerSetupDestination,
    ) {
        authRepository.setServerUrl(serverUrl)
        _uiState.update {
            it.copy(
                isLoading = false,
                pendingCleartextUrl = null,
                navigateTo = destination,
            )
        }
    }
}

private data class PendingCleartextConnection(
    val serverUrl: String,
    val origin: String,
    val destination: ServerSetupDestination,
)

/**
 * True when the address WILL connect over plaintext HTTP: an explicit `http`
 * scheme selection, or an `http://` prefix in the input. Auto mode is not
 * flagged pre-connect — it tries HTTPS first and only falls back to HTTP.
 * Informational only (does not block connecting to LAN/IP servers).
 */
internal fun serverSetupUsesCleartext(
    rawInput: String,
    selectedScheme: ServerSetupScheme,
    port: String,
): Boolean {
    if (rawInput.isBlank()) return false
    if (selectedScheme == ServerSetupScheme.Http) return true
    if (selectedScheme == ServerSetupScheme.Https) return false
    return runCatching {
        buildServerSetupCandidateUrls(rawInput, selectedScheme, port)
    }.getOrNull()?.let { candidates ->
        // Auto with an explicit http:// input resolves to a single http
        // candidate; a bare host yields https first, so no pre-warning.
        candidates.size == 1 && candidates.first().startsWith("http://")
    } ?: false
}

internal fun buildServerSetupCandidateUrls(
    rawInput: String,
    selectedScheme: ServerSetupScheme,
    port: String,
): List<String> {
    val input = rawInput.trim()
    if (input.isBlank()) {
        throw ServerSetupValidationException("Please enter a server host.")
    }

    val parsed = parseServerSetupInput(input)
    val explicitPort = parsed.port ?: parsePort(port)
    val schemes = when {
        selectedScheme.urlScheme != null -> listOf(selectedScheme.urlScheme)
        parsed.scheme != null -> listOf(parsed.scheme)
        else -> listOf("https", "http")
    }

    val candidates = schemes.map { scheme ->
        buildServerSetupUrl(
            scheme = scheme,
            host = parsed.host,
            port = explicitPort,
            path = parsed.path,
        )
    }.toMutableList()

    if (selectedScheme == ServerSetupScheme.Auto && parsed.scheme == null && explicitPort == null) {
        candidates += buildServerSetupUrl(
            scheme = "http",
            host = parsed.host,
            port = DefaultPrairieServerPort,
            path = parsed.path,
        )
    }

    return candidates.distinct()
}

private const val DefaultPrairieServerPort = 8090

private data class ParsedServerSetupInput(
    val scheme: String?,
    val host: String,
    val port: Int?,
    val path: String,
)

private fun parseServerSetupInput(input: String): ParsedServerSetupInput {
    val hasScheme = input.contains("://")
    val parseTarget = if (hasScheme) input else "https://$input"
    val uri = runCatching { URI(parseTarget) }.getOrElse {
        throw ServerSetupValidationException("Enter a valid server address.")
    }
    val host = uri.host?.takeIf { it.isNotBlank() }
        ?: throw ServerSetupValidationException("Enter a valid server address.")
    val parsedPort = uri.port.takeIf { it > 0 }?.also { validatePort(it) }
    val rawPath = uri.rawPath.orEmpty().trimEnd('/')
    val path = rawPath.takeUnless { it.isBlank() || it == "/" }.orEmpty()
    return ParsedServerSetupInput(
        scheme = if (hasScheme) uri.scheme?.lowercase() else null,
        host = host,
        port = parsedPort,
        path = path,
    )
}

private fun parsePort(port: String): Int? {
    val trimmed = port.trim()
    if (trimmed.isBlank()) return null
    val parsed = trimmed.toIntOrNull()
        ?: throw ServerSetupValidationException("Port must be a number between 1 and 65535.")
    validatePort(parsed)
    return parsed
}

private fun validatePort(port: Int) {
    if (port !in 1..65535) {
        throw ServerSetupValidationException("Port must be a number between 1 and 65535.")
    }
}

private fun buildServerSetupUrl(
    scheme: String,
    host: String,
    port: Int?,
    path: String,
): String {
    val authority = if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
    val portSuffix = port?.let { ":$it" }.orEmpty()
    return AndroidServerRegistry.normalizeUrl("$scheme://$authority$portSuffix$path")
}
