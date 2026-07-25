package org.prairieserver.prairie.tv.ui.screens.watchtogether

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.model.watchtogether.CreateRoomRequest
import org.prairieserver.prairie.model.watchtogether.JoinRoomRequest
import org.prairieserver.prairie.model.watchtogether.RoomSnapshot
import org.prairieserver.prairie.model.watchtogether.SetSelectionRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.errorMessage
import org.prairieserver.prairie.repository.WatchTogetherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the TV Watch Together entry + join-code dialogs. Hosts a room (create +
 * set this title as the room selection) or joins an existing room by invite code,
 * then surfaces a one-shot [UiState.result] [RoomSnapshot] the detail screen routes
 * on (host-with-selection → synced player, no selection → lobby).
 *
 * The repository stores the room JWT internally on create/join and reads the
 * active roomId from its own snapshot, so [WatchTogetherRepository.setSelection]
 * takes only the request. createRoom does NOT auto-select, so the host flow must
 * createRoom THEN setSelection(contentId, fileId) so the host lands on the player.
 * The ordering is safe because createRoom synchronously stores the snapshot before
 * returning. Create/join/selection all return a `{room, room_access_token}`
 * wrapper; the snapshot lives at `.data.room`.
 *
 * Mirrors the mobile `WatchTogetherEntryViewModel` host()/joinByCode() shape.
 */
class TvWatchTogetherViewModel(
    private val repository: WatchTogetherRepository,
) : ViewModel() {

    data class UiState(
        val isBusy: Boolean = false,
        val error: String? = null,
        /** Set once a create/join resolves — the screen observes this and navigates. */
        val result: RoomSnapshot? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Host flow: create a room with this title pre-selected as the room selection. */
    fun createRoom(contentId: String, fileId: Int?) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val created = repository.createRoom(CreateRoomRequest())) {
                is ApiResult.Success -> {
                    // createRoom does NOT auto-select; set this title as the room
                    // selection so the host lands on the synced player. The repo
                    // already stored the snapshot synchronously, so setSelection
                    // reads the right roomId/token.
                    if (created.data.room.selectedContentId.isNullOrBlank()) {
                        when (
                            val sel = repository.setSelection(
                                SetSelectionRequest(contentId = contentId, fileId = fileId),
                            )
                        ) {
                            is ApiResult.Success -> finish(sel.data.room)
                            is ApiResult.Error, is ApiResult.NetworkError ->
                                fail(sel.errorMessage("Failed to set selection"))
                        }
                    } else {
                        finish(created.data.room)
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError ->
                    fail(created.errorMessage("Failed to create room"))
            }
        }
    }

    /** Join flow: resolve an invite code; the screen routes to player or lobby. */
    fun joinRoom(code: String) {
        val trimmed = code.trim().uppercase()
        if (_uiState.value.isBusy || trimmed.isBlank()) return
        _uiState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val joined = repository.joinRoom(JoinRoomRequest(code = trimmed))) {
                is ApiResult.Success -> finish(joined.data.room)
                is ApiResult.Error, is ApiResult.NetworkError ->
                    fail(joined.errorMessage("Could not join — check the code"))
            }
        }
    }

    private fun finish(room: RoomSnapshot) {
        _uiState.update { it.copy(isBusy = false, error = null, result = room) }
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(isBusy = false, error = message) }
    }

    fun consumeResult() = _uiState.update { it.copy(result = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }
}
