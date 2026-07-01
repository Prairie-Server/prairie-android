package org.siloserver.silo.android.ui.screens.watchtogether

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.siloserver.silo.android.ui.navigation.Route
import org.siloserver.silo.model.watchtogether.CreateRoomRequest
import org.siloserver.silo.model.watchtogether.JoinRoomRequest
import org.siloserver.silo.model.watchtogether.RoomSnapshot
import org.siloserver.silo.model.watchtogether.SetSelectionRequest
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.errorMessage
import org.siloserver.silo.repository.WatchTogetherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Pure navigation decision: after create/join, where do we send the user?
 * If the room already has a selected title -> straight to the synced player
 * (carrying roomId). Otherwise -> the lobby to wait / vote / pick.
 *
 * Kept top-level + pure so it is unit-testable without Compose or the repo.
 */
fun watchTogetherDestination(room: RoomSnapshot): String =
    if (!room.selectedContentId.isNullOrBlank()) {
        Route.Player(
            contentId = room.selectedContentId!!,
            fileId = room.selectedFileId,
            roomId = room.roomId,
        ).route
    } else {
        Route.WatchTogetherLobby(roomId = room.roomId).route
    }

/**
 * Backs the [WatchTogetherEntrySheet]. Hosts a room (create + set this title as
 * the room selection) or joins an existing room by invite code, then resolves a
 * navigation [UiState.destination] the sheet observes.
 *
 * The repository stores the room JWT internally on create/join and reads the
 * active roomId from its own snapshot, so [setSelection] takes only the request.
 * Create/join/selection all return a `{room, room_access_token}` [RoomResponse]
 * wrapper; the snapshot lives at `.data.room`.
 */
class WatchTogetherEntryViewModel(
    private val repository: WatchTogetherRepository,
) : ViewModel() {

    data class UiState(
        val busy: Boolean = false,
        val error: String? = null,
        /** Set once a create/join resolves — the sheet observes this and navigates. */
        val destination: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Host flow: create a room with this title pre-selected as the room selection. */
    fun host(contentId: String, fileId: Int?) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val created = repository.createRoom(CreateRoomRequest())) {
                is ApiResult.Success -> {
                    // Set this title as the room selection so everyone lands on it.
                    when (
                        val sel = repository.setSelection(
                            SetSelectionRequest(contentId = contentId, fileId = fileId),
                        )
                    ) {
                        is ApiResult.Success -> finish(sel.data.room)
                        is ApiResult.Error, is ApiResult.NetworkError ->
                            fail(sel.errorMessage("Failed to set selection"))
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError ->
                    fail(created.errorMessage("Failed to create room"))
            }
        }
    }

    /** Join flow: resolve an invite code; route to player if a selection exists, else lobby. */
    fun joinByCode(code: String) {
        val trimmed = code.trim()
        if (_uiState.value.busy || trimmed.isBlank()) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val joined = repository.joinRoom(JoinRoomRequest(code = trimmed))) {
                is ApiResult.Success -> finish(joined.data.room)
                is ApiResult.Error, is ApiResult.NetworkError ->
                    fail(joined.errorMessage("Could not join — check the code"))
            }
        }
    }

    private fun finish(room: RoomSnapshot) {
        _uiState.update { it.copy(busy = false, error = null, destination = watchTogetherDestination(room)) }
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(busy = false, error = message) }
    }

    fun consumeDestination() = _uiState.update { it.copy(destination = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }
}
