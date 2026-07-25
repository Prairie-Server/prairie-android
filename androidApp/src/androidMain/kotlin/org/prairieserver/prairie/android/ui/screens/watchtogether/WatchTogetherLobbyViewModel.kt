package org.prairieserver.prairie.android.ui.screens.watchtogether

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.android.ui.navigation.Route
import org.prairieserver.prairie.model.watchtogether.PromoteSuggestionRequest
import org.prairieserver.prairie.model.watchtogether.RoomPhase
import org.prairieserver.prairie.model.watchtogether.RoomSnapshot
import org.prairieserver.prairie.model.watchtogether.Suggestion
import org.prairieserver.prairie.repository.WatchTogetherRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Pure: should the lobby jump to the synced player? Only once the room is
 * actually playing AND a title is selected. Returns the player route or null.
 *
 * Compares against the real [RoomPhase] enum (not a wire string) — the landed
 * shared model uses lenient enums, see WatchTogetherModels.kt.
 */
fun lobbyPlayerDestinationOrNull(room: RoomSnapshot): String? =
    if (room.phase == RoomPhase.Playing && !room.selectedContentId.isNullOrBlank()) {
        Route.Player(
            contentId = room.selectedContentId!!,
            fileId = room.selectedFileId,
            roomId = room.roomId,
        ).route
    } else {
        null
    }

/**
 * Backs [WatchTogetherLobbyScreen]. Binds the per-room websocket on enter so
 * snapshots/suggestions flow into the state flows, and exposes vote / unvote /
 * host-promote / host-pick / close-room ops.
 *
 * The repository owns the room JWT and reads the active roomId from its own
 * snapshot, so the room-scoped ops take only request/id params (not a roomId).
 * [connect] is suspend and runs the reconnect-with-backoff loop until the
 * scope is cancelled, so it is launched in [viewModelScope].
 */
class WatchTogetherLobbyViewModel(
    private val roomId: String,
    private val repository: WatchTogetherRepository,
) : ViewModel() {

    init {
        // The repo owns reconnect/backoff; we just bind on enter. connect()
        // suspends for the lifetime of the socket, so launch it (don't call bare).
        viewModelScope.launch { repository.connect(roomId) }
    }

    val room: StateFlow<RoomSnapshot?> = repository.roomSnapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.roomSnapshot.value)
    val suggestions: StateFlow<List<Suggestion>> = repository.suggestions
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.suggestions.value)
    val roomClosedReason: StateFlow<String?> = repository.roomClosedReason
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.roomClosedReason.value)

    fun vote(suggestionId: String) = viewModelScope.launch { repository.vote(suggestionId) }
    fun unvote(suggestionId: String) = viewModelScope.launch { repository.unvote(suggestionId) }
    fun removeSuggestion(suggestionId: String) =
        viewModelScope.launch { repository.deleteSuggestion(suggestionId) }

    /** Host: promote a suggestion to the room selection (moves everyone to the player). */
    fun promote(suggestionId: String) =
        viewModelScope.launch { repository.promoteSuggestion(PromoteSuggestionRequest(suggestionId = suggestionId)) }

    fun closeRoom() = viewModelScope.launch { repository.closeRoom() }

    /** Guest/host leave: tear down the WS + clear room state. */
    fun leave() {
        repository.reset()
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT reset here — the player binding reuses the same repo connection
        // when we auto-navigate into the synced player. reset() is called
        // explicitly via leave() when the user backs out without playing.
    }
}
