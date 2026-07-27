package org.prairieserver.prairie.android.ui.screens.servers

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

/**
 * Where to land after a successful server switch or discovery connect. The list
 * view emits this once the new server's tokens (or setup probe) resolve so the
 * navigator can route to the deepest screen the credentials allow — Login /
 * Setup when the target has no stored auth.
 */
enum class ServerSwitchDestination { Home, ProfileSelection, Login, Setup }

data class ServerListUiState(
    val servers: List<ServerEntry> = emptyList(),
    val activeId: String? = null,
    val pendingSwitchToId: String? = null,
    val switchedTo: ServerSwitchDestination? = null,
    val discovered: List<DiscoveryHit> = emptyList(),
    val isScanning: Boolean = false,
    val scanStatus: String? = null,
    val scanError: String? = null,
    val isConnecting: Boolean = false,
)

/**
 * Drives [ServerListScreen]. Owns no auth state itself — every action
 * round-trips through [ServerRegistry] / [TokenManager] / [AuthRepository].
 *
 * Server entries are surfaced sorted with the active one first, then
 * most-recently-used; the screen renders this list directly.
 */
class ServerListViewModel(
    private val serverRegistry: ServerRegistry,
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository,
    private val lanDiscovery: LanDiscovery,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerListUiState())
    val uiState: StateFlow<ServerListUiState> = _uiState.asStateFlow()

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

    /** Kick off a LAN scan once when landing from cold start / change-server. */
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
        if (_uiState.value.activeId == serverId) {
            // Already active — nothing to do, don't fire a navigation event.
            return
        }
        _uiState.update { it.copy(pendingSwitchToId = serverId) }
        viewModelScope.launch {
            serverRegistry.switchTo(serverId)
            // Force the token manager to flush its cache and reload from the
            // new server's slot before the navigator advances. Without this,
            // the destination screen could read stale tokens for one frame.
            tokenManager.switchActiveServer(serverId)

            // Route to the deepest screen the new server's stored credentials
            // can populate. Mirrors MainActivity.resolveStartDestination so a
            // user with valid tokens for the target server skips Login + the
            // profile picker entirely.
            val accessToken = tokenManager.getAccessToken()
            val activeEntry = serverRegistry.activeEntry.value
            val profileId = activeEntry?.profileId ?: tokenManager.getProfileId()
            val destination = when {
                accessToken.isNullOrBlank() -> ServerSwitchDestination.Login
                profileId.isNullOrBlank() -> ServerSwitchDestination.ProfileSelection
                else -> ServerSwitchDestination.Home
            }

            _uiState.update {
                it.copy(pendingSwitchToId = null, switchedTo = destination)
            }
        }
    }

    /**
     * Connect to a LAN-discovered server: persist URL, probe setup status,
     * then navigate to Login or first-time Setup (credentials only).
     */
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
                                switchedTo = ServerSwitchDestination.Setup,
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
                    switchedTo = ServerSwitchDestination.Login,
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

    fun onRename(serverId: String, newName: String) {
        viewModelScope.launch {
            serverRegistry.rename(serverId, newName.takeIf { it.isNotBlank() })
        }
    }

    fun onRemove(serverId: String) {
        viewModelScope.launch {
            serverRegistry.remove(serverId)
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
