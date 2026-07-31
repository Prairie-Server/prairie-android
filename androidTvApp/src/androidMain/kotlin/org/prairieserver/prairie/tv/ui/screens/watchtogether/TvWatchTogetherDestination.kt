package org.prairieserver.prairie.tv.ui.screens.watchtogether

import org.prairieserver.prairie.model.watchtogether.RoomSnapshot
import org.prairieserver.prairie.tv.ui.navigation.TvRoute
import org.prairieserver.prairie.watchtogether.WatchTogetherEntryTarget
import org.prairieserver.prairie.watchtogether.watchTogetherEntryTarget

fun tvWatchTogetherDestination(room: RoomSnapshot): String =
    when (watchTogetherEntryTarget(room)) {
        WatchTogetherEntryTarget.Lobby ->
            TvRoute.WatchTogetherLobby(room.roomId).route
        WatchTogetherEntryTarget.Player ->
            TvRoute.Player(
                contentId = requireNotNull(room.selectedContentId),
                fileId = room.selectedFileId,
                roomId = room.roomId,
                resumePositionSeconds = room.anchorPositionSeconds
                    .takeIf { it.isFinite() && it > 0.0 },
            ).route
    }
