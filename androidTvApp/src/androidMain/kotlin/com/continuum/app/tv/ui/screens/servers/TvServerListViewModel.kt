package com.continuum.app.tv.ui.screens.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.server.ServerEntry
import com.continuum.app.network.ServerRegistry
import com.continuum.app.network.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TvServerSwitchDestination { Home, ProfileSelection, Login }

data class TvServerListUiState(
    val servers: List<ServerEntry> = emptyList(),
    val activeId: String? = null,
    val pendingSwitchToId: String? = null,
    val switchedTo: TvServerSwitchDestination? = null,
)

/**
 * TV-side counterpart of [com.continuum.app.android.ui.screens.servers.ServerListViewModel].
 * Shares the same wire-up against [ServerRegistry] / [TokenManager]; only the
 * presentation differs.
 */
class TvServerListViewModel(
    private val serverRegistry: ServerRegistry,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvServerListUiState())
    val uiState: StateFlow<TvServerListUiState> = _uiState.asStateFlow()

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

    fun onSelect(serverId: String) {
        if (_uiState.value.activeId == serverId) return
        _uiState.update { it.copy(pendingSwitchToId = serverId) }
        viewModelScope.launch {
            serverRegistry.switchTo(serverId)
            tokenManager.switchActiveServer(serverId)

            // Land on the deepest screen the new server's stored credentials
            // can reach — preserves the signed-in user when tokens are present.
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

    fun onSwitchConsumed() {
        _uiState.update { it.copy(switchedTo = null) }
    }

    fun onRemove(serverId: String) {
        viewModelScope.launch {
            serverRegistry.remove(serverId)
        }
    }

    /** Set a user override display name for a saved server (blank clears it). */
    fun onRename(serverId: String, name: String) {
        viewModelScope.launch {
            serverRegistry.rename(serverId, name.trim().ifBlank { null })
        }
    }
}
