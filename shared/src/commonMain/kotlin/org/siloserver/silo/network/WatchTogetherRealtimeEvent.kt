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
 * StateFlows / feeds them to the RoomSyncEngine.
 */
sealed class RoomRealtimeEvent {
    /** A physical connection completed its handshake and is writable. */
    data object Opened : RoomRealtimeEvent()

    data class SnapshotEvent(val room: RoomSnapshot) : RoomRealtimeEvent()
    data class TransportCommandEvent(val command: TransportCommand) : RoomRealtimeEvent()
    data class SuggestionsEvent(val suggestions: List<Suggestion>) : RoomRealtimeEvent()
    data class Pong(
        val clientSentAt: String,
        val serverReceivedAt: String,
        val serverSentAt: String,
    ) : RoomRealtimeEvent()

    /** Explicit server `room_closed{reason}`. Terminal at the protocol layer. */
    data class Closed(val reason: String? = null) : RoomRealtimeEvent()

    /** Physical EOF, handshake failure, or socket I/O failure. Transient. */
    data class TransportTerminated(val cause: Throwable? = null) : RoomRealtimeEvent()

    /** Server `error{code,message}`. */
    data class Error(val code: String, val message: String) : RoomRealtimeEvent()
}
