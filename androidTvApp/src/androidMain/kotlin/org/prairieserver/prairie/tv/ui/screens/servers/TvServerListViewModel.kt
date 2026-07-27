package org.prairieserver.prairie.tv.ui.screens.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.discovery.DiscoveryHit
import org.prairieserver.prairie.discovery.LanDiscovery
import org.prairieserver.prairie.discovery.LanScanOptions
import org.prairieserver.prairie.discovery.normalizeDiscoveryUrl
import org.prairieserver.prairie.model.server.ServerEntry
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.ServerRegistry
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TvServerSwitchDestination { Home, ProfileSelection, Login, Setup }

data class TvServerListUiState(
    val servers: List<ServerEntry> = emptyList(),
    val activeId: String? = null,
    val pendingSwitchToId: String? = null,
    val switchedTo: TvServerSwitchDestination? = null,
    /**
     * Set once when the *active* server was removed and no server remains to
     * fall back to — stay on the list (first-run / empty) rather than bouncing
     * to manual URL entry. One-shot; cleared by [onEmptyRegistryConsumed].
     */
    val emptyRegistry: Boolean = false,
    val discovered: List<DiscoveryHit> = emptyList(),
    val isScanning: Boolean = false,
    val scanStatus: String? = null,
    val scanError: String? = null,
    val isConnecting: Boolean = false,
)

/**
 * TV-side counterpart of [org.prairieserver.prairie.android.ui.screens.servers.ServerListViewModel].
 * Shares the same wire-up against [ServerRegistry] / [TokenManager] / LAN discovery.
 */
class TvServerListViewModel(
    private val serverRegistry: ServerRegistry,
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository,
    private val lanDiscovery: LanDiscovery,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvServerListUiState())
    val uiState: StateFlow<TvServerListUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var didAutoScan = false

    private fun discoveryBaseHosts(): List<String> {
        fun extractHost(url: String): String? {
            val normalized = normalizeDiscoveryUrl(url)
            val schemeSep = normalized.indexOf("://")
            if (schemeSep < 0) return null
            val rest = normalized.substring(schemeSep + 3)
            val authority = rest.substringBefore('/')
            if (authority.isBlank()) return null
            return if (authority.startsWith("[")) {
                val close = authority.indexOf(']')
                if (close <= 1) null else authority.substring(1, close)
            } else {
                authority.substringBefore(':')
            }?.takeIf { it.isNotBlank() }
        }

        val out = LinkedHashSet<String>()
        for (entry in serverRegistry.entries.value) {
            extractHost(entry.url)?.let { out += it }
        }
        serverRegistry.activeEntry.value?.let { active ->
            extractHost(active.url)?.let { out += it }
        }
        return out.toList()
    }

    init {
        viewModelScope.launch { authRepository.refreshActiveServerName() }
        viewModelScope.launch {
            combine(
                serverRegistry.entries,
                serverRegistry.activeServerId,
            ) { entries, activeId ->
                val sorted = entries.sortedWith(
                    compareByDescending<ServerEntry> { it.id == activeId }
                        .thenByDescending { it.lastUsedAtEpochMs },
                )
                sorted to activeId
            }.collect { (sorted, activeId) ->
                _uiState.update { it.copy(servers = sorted, activeId = activeId) }
            }
        }
    }

    fun maybeAutoScan() {
        if (didAutoScan) return
        didAutoScan = true
        startScan(includeDeep = true)
    }

    fun startScan(includeDeep: Boolean = true) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    scanError = null,
                    discovered = emptyList(),
                    scanStatus = "Looking for Prairie servers on your network…",
                )
            }
            try {
                val baseHosts = discoveryBaseHosts()
                var hits = lanDiscovery.scan(
                    LanScanOptions(
                        deepScan = false,
                        baseHosts = baseHosts,
                        onHit = { next ->
                            _uiState.update { state -> state.copy(discovered = next) }
                        },
                        onProgress = { done, total ->
                            _uiState.update {
                                it.copy(scanStatus = "Quick scan $done/$total…")
                            }
                        },
                    ),
                )
                _uiState.update { it.copy(discovered = hits) }

                if (includeDeep) {
                    _uiState.update { it.copy(scanStatus = "Deep LAN scan…") }
                    val deepHits = lanDiscovery.scan(
                        LanScanOptions(
                            deepScan = true,
                            baseHosts = baseHosts,
                            onHit = { next ->
                                val merged = mergeDiscoveryLists(hits, next)
                                _uiState.update { state -> state.copy(discovered = merged) }
                            },
                            onProgress = { done, total ->
                                _uiState.update {
                                    it.copy(scanStatus = "Deep scan $done/$total…")
                                }
                            },
                        ),
                    )
                    hits = mergeDiscoveryLists(hits, deepHits)
                    _uiState.update { it.copy(discovered = hits) }
                }

                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanStatus = if (hits.isEmpty()) {
                            "No Prairie servers found — add one manually or scan again"
                        } else {
                            "Found ${hits.size} server(s)"
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanStatus = null,
                        scanError = error.message?.takeIf { msg -> msg.isNotBlank() }
                            ?: "Scan failed",
                    )
                }
            }
        }
    }

    fun onSelect(serverId: String) {
        if (_uiState.value.activeId == serverId) return
        _uiState.update { it.copy(pendingSwitchToId = serverId) }
        viewModelScope.launch {
            serverRegistry.switchTo(serverId)
            tokenManager.switchActiveServer(serverId)

            val accessToken = tokenManager.getAccessToken()
            val activeEntry = serverRegistry.activeEntry.value
            val profileId = activeEntry?.profileId ?: tokenManager.getProfileId()
            val destination = when {
                accessToken.isNullOrBlank() -> TvServerSwitchDestination.Login
                profileId.isNullOrBlank() -> TvServerSwitchDestination.ProfileSelection
                else -> TvServerSwitchDestination.Home
            }

            _uiState.update {
                it.copy(pendingSwitchToId = null, switchedTo = destination)
            }
        }
    }

    fun selectDiscovered(url: String, serverName: String) {
        if (_uiState.value.isConnecting || _uiState.value.isScanning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, scanError = null) }
            val normalized = normalizeDiscoveryUrl(url)
            when (val setupResult = authRepository.getSetupStatus(normalized)) {
                is ApiResult.Success -> {
                    if (setupResult.data.needsSetup) {
                        authRepository.setServerUrl(normalized)
                        applyFetchedName(serverName)
                        _uiState.update {
                            it.copy(
                                isConnecting = false,
                                switchedTo = TvServerSwitchDestination.Setup,
                            )
                        }
                        return@launch
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            scanError = setupResult.message
                                ?.takeIf { msg -> msg.isNotBlank() }
                                ?.let { msg -> "Could not connect: $msg" }
                                ?: "Could not reach a Prairie server at that address.",
                        )
                    }
                    return@launch
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            scanError = "Could not reach a Prairie server at that address.",
                        )
                    }
                    return@launch
                }
            }

            authRepository.setServerUrl(normalized)
            applyFetchedName(serverName)
            _uiState.update {
                it.copy(
                    isConnecting = false,
                    switchedTo = TvServerSwitchDestination.Login,
                )
            }
        }
    }

    private suspend fun applyFetchedName(serverName: String) {
        val trimmed = serverName.trim()
        if (trimmed.isEmpty()) return
        val activeId = serverRegistry.activeServerId.value ?: return
        serverRegistry.setFetchedName(activeId, trimmed)
    }

    fun onSwitchConsumed() {
        _uiState.update { it.copy(switchedTo = null) }
    }

    fun onEmptyRegistryConsumed() {
        _uiState.update { it.copy(emptyRegistry = false) }
    }

    fun onRemove(serverId: String) {
        viewModelScope.launch {
            val wasActive = serverRegistry.activeServerId.value == serverId
            serverRegistry.remove(serverId)

            if (!wasActive) return@launch

            val promotedId = serverRegistry.activeServerId.value
            if (promotedId == null) {
                // No server left — stay on the list (scan / add manually).
                _uiState.update { it.copy(emptyRegistry = true) }
                return@launch
            }

            tokenManager.switchActiveServer(promotedId)

            val accessToken = tokenManager.getAccessToken()
            val activeEntry = serverRegistry.activeEntry.value
            val profileId = activeEntry?.profileId ?: tokenManager.getProfileId()
            val destination = when {
                accessToken.isNullOrBlank() -> TvServerSwitchDestination.Login
                profileId.isNullOrBlank() -> TvServerSwitchDestination.ProfileSelection
                else -> TvServerSwitchDestination.Home
            }

            _uiState.update { it.copy(switchedTo = destination) }
        }
    }
}

internal fun mergeDiscoveryLists(
    base: List<DiscoveryHit>,
    extra: List<DiscoveryHit>,
): List<DiscoveryHit> {
    val map = LinkedHashMap<String, DiscoveryHit>()
    for (hit in base) map[hit.url] = hit
    for (hit in extra) map[hit.url] = hit
    return map.values.toList()
}
