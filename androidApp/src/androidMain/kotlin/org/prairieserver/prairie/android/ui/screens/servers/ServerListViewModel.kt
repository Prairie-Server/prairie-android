package org.prairieserver.prairie.android.ui.screens.servers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.discovery.DiscoveryHit
import org.prairieserver.prairie.discovery.LanDiscovery
import org.prairieserver.prairie.discovery.LanScanOptions
import org.prairieserver.prairie.discovery.normalizeDiscoveryUrl
import org.prairieserver.prairie.network.ApiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.prairieserver.prairie.android.auth.ActiveProfileReset
import org.prairieserver.prairie.android.push.AndroidPushRegistrar
import org.prairieserver.prairie.android.push.PushRemoval
import org.prairieserver.prairie.android.push.tolerateFailure
import org.prairieserver.prairie.android.ui.navigation.hasOtherSession
import org.prairieserver.prairie.common.downloads.DownloadSummary
import org.prairieserver.prairie.model.server.AccountEntry
import org.prairieserver.prairie.model.server.ServerEntry
import org.prairieserver.prairie.network.ServerRegistry
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.localroute.LocalRoute
import org.prairieserver.prairie.network.localroute.LocalRoutes
import org.prairieserver.prairie.repository.AuthRepository

/**
 * Where to land after a successful server switch or discovery connect. The list
 * view emits this once the new server's tokens (or setup probe) resolve so the
 * navigator can route to the deepest screen the credentials allow — Login /
 * Setup when the target has no stored auth.
 */
enum class ServerSwitchDestination { Home, ProfileSelection, ChangeAccount, Login, ServerSetup, Setup }

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
    /** The server whose requests use its local route right now, if any. */
    val onLocalNetworkId: String? = null,
    /** Every saved account (Settings › Accounts, spec §4.5). */
    val accounts: List<AccountEntry> = emptyList(),
    val activeAccountId: String? = null,
    /** Each account's downloads, by [AccountEntry.localAccountId]; missing until read. */
    val downloads: Map<String, DownloadSummary> = emptyMap(),
    /** A sign-out or removal is running. */
    val isWorking: Boolean = false,
    /** The server a removal stopped on because one of its accounts didn't sign out. */
    val removeFailedServerId: String? = null,
) {
    /** [serverId]'s accounts in the order they were added. */
    fun accountsOn(serverId: String): List<AccountEntry> =
        accounts.filter { it.serverId == serverId }.sortedBy { it.addedAtEpochMs }
}

/**
 * Drives [AccountsScreen], [AccountDetailScreen] and the first-run [ServerListScreen].
 * Owns no auth state itself — every action round-trips through
 * [ServerRegistry] / [TokenManager] / [AuthRepository].
 *
 * Server entries are surfaced sorted with the active one first, then
 * most-recently-used; the screen renders this list directly.
 */
class ServerListViewModel(
    private val serverRegistry: ServerRegistry,
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository,
    private val lanDiscovery: LanDiscovery,
    private val activeProfileReset: ActiveProfileReset,
    /** Removes this device's push registration on a server ([AndroidPushRegistrar.unregisterDevice]). */
    private val unregisterPush: suspend (serverEntryId: String) -> PushRemoval? = { null },
    /** Undoes that removal when the server then stays saved ([AndroidPushRegistrar.restoreRegistration]). */
    private val restorePush: suspend (PushRemoval) -> Unit = {},
    private val localRoute: StateFlow<LocalRoute?> = LocalRoutes.state.active,
    /** Signs out of one account, active or not ([org.prairieserver.prairie.android.auth.SignOutTeardown.signOutAccount]). */
    private val signOutAccount: suspend (serverId: String, accountId: String) -> Boolean = { _, _ -> true },
    private val downloadSummary: suspend (serverId: String, accountId: String) -> DownloadSummary? = { _, _ -> null },
    /** Pushes settings still waiting to sync while the account they were made on is active. */
    private val flushPendingSettings: suspend () -> Unit = {},
    /** Drops the per-profile caches once another account is active, as a server switch does. */
    private val clearProfileCaches: () -> Unit = {},
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
                localRoute,
            ) { entries, activeId, route ->
                val sorted = entries.sortedWith(
                    compareByDescending<ServerEntry> { it.id == activeId }
                        .thenByDescending { it.lastUsedAtEpochMs },
                )
                Triple(sorted, activeId, route?.serverId?.takeIf { it == activeId })
            }.collect { (sorted, activeId, onLocalNetworkId) ->
                _uiState.update { it.copy(servers = sorted, activeId = activeId, onLocalNetworkId = onLocalNetworkId) }
            }
        }
        viewModelScope.launch {
            combine(serverRegistry.accounts, serverRegistry.activeAccountId) { accounts, active -> accounts to active }
                .collect { (accounts, active) ->
                    _uiState.update { it.copy(accounts = accounts, activeAccountId = active) }
                }
        }
        viewModelScope.launch {
            serverRegistry.accounts.collectLatest { accounts ->
                val summaries = accounts.mapNotNull { account ->
                    runCatching { downloadSummary(account.serverId, account.localAccountId) }.getOrNull()
                        ?.let { account.localAccountId to it }
                }.toMap()
                _uiState.update { it.copy(downloads = summaries) }
            }
        }
    }

    /**
     * "Add an account on {server}": that server becomes active with no
     * account, so the sign-in that follows adds one. Its other accounts stay
     * saved.
     */
    fun onAddAccount(serverId: String) = switchForSignIn(serverId, accountId = null)

    /** "Sign in again" on an account whose credentials expired: sign-in with its username filled in. */
    fun onSignInAgain(accountId: String) {
        val account = _uiState.value.accounts.firstOrNull { it.localAccountId == accountId } ?: return
        switchForSignIn(account.serverId, accountId)
    }

    private fun switchForSignIn(serverId: String, accountId: String?) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            try {
                val switched = switchActiveAccount(
                    serverRegistry, authRepository, serverId, accountId, flushPendingSettings, clearProfileCaches,
                )
                if (!switched) return@launch
                _uiState.update { it.copy(switchedTo = ServerSwitchDestination.Login) }
            } finally {
                _uiState.update { it.copy(isWorking = false) }
            }
        }
    }

    /**
     * Sign out of [accountId] (spec §4.4): its downloads, preferences and
     * tokens go. Signing out of the active account leaves for Change Account
     * while another account has a session, else sign-in; any other account
     * just leaves the list.
     */
    fun onSignOutAccount(accountId: String) {
        val account = _uiState.value.accounts.firstOrNull { it.localAccountId == accountId } ?: return
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            try {
                val wasActive = serverRegistry.activeAccountId.value == accountId
                if (!signOutAccount(account.serverId, accountId) || !wasActive) return@launch
                _uiState.update { it.copy(switchedTo = destinationWithoutAccount()) }
            } finally {
                _uiState.update { it.copy(isWorking = false) }
            }
        }
    }

    /** Where the active account's sign-out leaves the person: see [org.prairieserver.prairie.android.ui.navigation.routeAfterSignOut]. */
    private fun destinationWithoutAccount(): ServerSwitchDestination = when {
        serverRegistry.entries.value.isEmpty() -> ServerSwitchDestination.ServerSetup
        serverRegistry.hasOtherSession() -> ServerSwitchDestination.ChangeAccount
        else -> ServerSwitchDestination.Login
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
            // Switch the registry + token scope (so the destination screen never
            // reads stale tokens) and probe the target server.s contract.
            authRepository.switchToServer(serverId)

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
                // Profile Selection asks on a server switch as it does at
                // launch, so a protected profile there needs its PIN. Cleared
                // before navigating, so the picker loads under the new server.
                activeProfileReset.clearIfSelectionRequired() -> ServerSwitchDestination.ProfileSelection
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
            _uiState.update { it.copy(isWorking = true, removeFailedServerId = null) }
            try {
                if (!removeServer(serverId)) _uiState.update { it.copy(removeFailedServerId = serverId) }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Log.w("ServerListViewModel", "server removal failed", failure)
                _uiState.update { it.copy(removeFailedServerId = serverId) }
            } finally {
                _uiState.update { it.copy(isWorking = false) }
            }
        }
    }

    fun onRemoveFailureConsumed() {
        _uiState.update { it.copy(removeFailedServerId = null) }
    }

    /**
     * Remove a server (spec §4.4): sign out of every account on it (each
     * session ends and its downloads go), then forget the server. False, with
     * the server still saved, when an account didn't sign out: removing the
     * server anyway would drop that account's credentials with its session
     * still live and its push registration still in place.
     */
    private suspend fun removeServer(serverId: String): Boolean {
        val wasActive = serverRegistry.activeServerId.value == serverId
        // Inactive accounts first, so the active one's sign-out is the last
        // identity change on this server.
        val accounts = serverRegistry.accounts.value
            .filter { it.serverId == serverId }
            .sortedBy { serverRegistry.activeAccountId.value == it.localAccountId }
        for (account in accounts) {
            val signedOut = try {
                signOutAccount(serverId, account.localAccountId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (!signedOut) return false
        }
        // Before removal wipes the server's credentials, which a server
        // without the retirement route needs; a retirement it has not
        // answered is sent again later without them. Failing to remove the
        // registration never stops the removal; cancellation still does,
        // before anything is removed.
        var removal: PushRemoval? = null
        tolerateFailure { removal = unregisterPush(serverId) }
        try {
            serverRegistry.remove(serverId)
        } catch (failure: Exception) {
            // Not removed, so it keeps its login, and its pushes with it.
            if (failure !is CancellationException) removal?.let { tolerateFailure { restorePush(it) } }
            throw failure
        }

        // Removing the ACTIVE server promotes the next-MRU entry inside
        // the registry, which never probes: without this the promoted
        // server keeps whatever contract verdict an older build stored
        // (or UNKNOWN). Route it through the same durable switch path as
        // every other promotion: the registry already points at it, so
        // switchToServer is idempotent there, and its bounded probe hands
        // a replacement to the repository's process-lifetime background
        // scope — popping this screen cancels viewModelScope, which must
        // not strand the promoted server on a stale verdict.
        val promotedId = serverRegistry.activeServerId.value
        if (wasActive && promotedId != null) {
            authRepository.switchToServer(promotedId)
            // A promotion is a server switch too: when Profile Selection
            // asks, the promoted server's saved profile needs choosing
            // (and its PIN) again, so leave for Who's Watching rather
            // than back to a shell that would reopen it. Without a
            // saved profile there is nothing to protect.
            val hasProfile = !tokenManager.getAccessToken().isNullOrBlank() &&
                !(serverRegistry.activeEntry.value?.profileId ?: tokenManager.getProfileId()).isNullOrBlank()
            val destination = when {
                tokenManager.getAccessToken().isNullOrBlank() -> destinationWithoutAccount()
                !hasProfile -> ServerSwitchDestination.ProfileSelection
                activeProfileReset.clearIfSelectionRequired() -> ServerSwitchDestination.ProfileSelection
                else -> ServerSwitchDestination.Home
            }
            _uiState.update { it.copy(switchedTo = destination) }
        } else if (wasActive) {
            // The last server is gone.
            _uiState.update { it.copy(switchedTo = ServerSwitchDestination.ServerSetup) }
        }
        return true
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
