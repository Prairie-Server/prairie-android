package org.siloserver.silo.network

import org.siloserver.silo.model.watchtogether.RoomSnapshot
import org.siloserver.silo.model.watchtogether.Suggestion
import org.siloserver.silo.model.watchtogether.TransportCommand

/** Room WS server-frame type discriminators (mirrors the server `"type"` strings). */
object WatchTogetherRealtime {
    const val TypeSnapshot = "snapshot"
    const val TypeTransportCommand = "transport_command"
    const val TypeSuggestionsUpdate = "suggestions_update"
    const val TypeRoomClosed = "room_closed"
    const val TypePong = "pong"
    const val TypeError = "error"
}

/**
 * A decoded room realtime event. The repository folds these into its
 * StateFlows / feeds them to the RoomSyncEngine. [Closed]
 * is emitted by the client when the socket itself ends (distinct from a server
 * [Closed]-with-reason `room_closed`, surfaced as the same event).
 */
sealed class RoomRealtimeEvent {
    data class SnapshotEvent(val room: RoomSnapshot) : RoomRealtimeEvent()
    data class TransportCommandEvent(val command: TransportCommand) : RoomRealtimeEvent()
    data class SuggestionsEvent(val suggestions: List<Suggestion>) : RoomRealtimeEvent()
    data class Pong(
        val clientSentAt: String,
        val serverReceivedAt: String,
        val serverSentAt: String,
    ) : RoomRealtimeEvent()

    /** Server `room_closed{reason}`. The repository stops reconnecting on this. */
    data class Closed(val reason: String? = null) : RoomRealtimeEvent()

    /** Server `error{code,message}`. */
    data class Error(val code: String, val message: String) : RoomRealtimeEvent()
}
