package org.siloserver.silo.android.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.siloserver.silo.network.AndroidServerRegistry
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.AuthRepository
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
)

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
            buildServerSetupCandidateUrls(
                rawInput = current.serverUrl,
                selectedScheme = current.selectedScheme,
                port = current.port,
            )
        } catch (error: ServerSetupValidationException) {
            _uiState.update { it.copy(error = error.message) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            var lastError: String? = null
            for (candidate in candidates) {
                when (val setupResult = authRepository.getSetupStatus(candidate)) {
                    is ApiResult.Success -> {
                        if (setupResult.data.needsSetup) {
                            authRepository.setServerUrl(candidate)
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
                        lastError = setupResult.message
                        continue
                    }

                    is ApiResult.NetworkError -> {
                        lastError = null
                        continue
                    }
                }

                val signupEnabled = when (val signupResult = authRepository.getSignupStatus(candidate)) {
                    is ApiResult.Success -> signupResult.data.enabled
                    else -> false // If we can't determine, default to no signup.
                }

                authRepository.setServerUrl(candidate)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        navigateTo = ServerSetupDestination.Login(signupEnabled = signupEnabled),
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = lastError
                        ?.takeIf { message -> message.isNotBlank() }
                        ?.let { message -> "Could not connect: $message" }
                        ?: "Could not reach a Silo server at that address.",
                )
            }
        }
    }

    /** Resets navigation state after the UI has consumed the event. */
    fun onNavigationConsumed() {
        _uiState.update { it.copy(navigateTo = null) }
    }
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
            port = DefaultSiloServerPort,
            path = parsed.path,
        )
    }

    return candidates.distinct()
}

private const val DefaultSiloServerPort = 8090

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
