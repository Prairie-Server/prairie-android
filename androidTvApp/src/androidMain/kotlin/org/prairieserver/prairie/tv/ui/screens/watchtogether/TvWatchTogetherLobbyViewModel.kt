package org.prairieserver.prairie.tv.ui.screens.watchtogether

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.model.watchtogether.PromoteSuggestionRequest
import org.prairieserver.prairie.model.watchtogether.RoomSnapshot
import org.prairieserver.prairie.model.watchtogether.Suggestion
import org.prairieserver.prairie.model.watchtogether.UpdatePolicyRequest
import org.prairieserver.prairie.repository.WatchTogetherRepository
import org.prairieserver.prairie.watchtogether.RoomSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs [TvWatchTogetherLobbyScreen]. Binds the per-room websocket on enter so
 * snapshots/suggestions flow into the state flows, and exposes vote / unvote /
 * host-promote / host-pick / policy / close-room ops. Mirrors the phone
 * [org.prairieserver.prairie.android.ui.screens.watchtogether.WatchTogetherLobbyViewModel].
 *
 * The repository owns the room JWT and reads the active roomId from its own
 * snapshot, so the room-scoped ops take only request/id params (not a roomId).
 * The application-scoped [RoomSession] owns reconnect and survives navigation
 * from this lobby into the synced player.
 */
class TvWatchTogetherLobbyViewModel(
    private val roomId: String,
    private val repository: WatchTogetherRepository,
    private val roomSession: RoomSession,
) : ViewModel() {

    init {
        roomSession.adopt(roomId)
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
        viewModelScope.launch {
            repository.promoteSuggestion(PromoteSuggestionRequest(suggestionId = suggestionId))
        }

    /** Host: change the guest-control policy. */
    fun updatePolicy(guestControlPolicyWire: String) =
        viewModelScope.launch {
            repository.updatePolicy(UpdatePolicyRequest(guestControlPolicy = guestControlPolicyWire))
        }

    /** Host: close the room for everyone. */
    fun closeRoom() = viewModelScope.launch { repository.closeRoom() }

    /**
     * Leave the room: tear down our own WS and reset shared repo state. Matches
     * mobile — leaving does NOT close the room for everyone. A host closes the
     * room explicitly via [closeRoom] (and stays on the lobby until the server's
     * `room_closed` broadcast reactively backs them out); otherwise the server's
     * host-left timeout reaps it. Closing on leave used to race the closeRoom
     * network call against viewModelScope cancellation during screen dispose.
     */
    fun leave() {
        roomSession.depart()
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT reset here — the player binding reuses the same repo connection
        // when we auto-navigate into the synced player. reset() is called
        // explicitly via leave() when the user backs out without playing.
    }
}
