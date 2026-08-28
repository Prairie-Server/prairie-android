package org.prairieserver.prairie.watchtogether

import org.prairieserver.prairie.model.watchtogether.MemberRole
import org.prairieserver.prairie.model.watchtogether.RoomPhase
import org.prairieserver.prairie.model.watchtogether.RoomSnapshot

enum class WatchTogetherEntryTarget {
    Lobby,
    Player,
}

fun watchTogetherEntryTarget(room: RoomSnapshot): WatchTogetherEntryTarget =
    if (
        !room.selectedContentId.isNullOrBlank() &&
        !(room.selfRole == MemberRole.Host && room.memberCount <= 1)
    ) {
        WatchTogetherEntryTarget.Player
    } else {
        WatchTogetherEntryTarget.Lobby
    }

fun resumableWatchTogetherRoom(room: RoomSnapshot?): RoomSnapshot? =
    room?.takeIf { it.roomId.isNotBlank() && it.phase != RoomPhase.Ended }
