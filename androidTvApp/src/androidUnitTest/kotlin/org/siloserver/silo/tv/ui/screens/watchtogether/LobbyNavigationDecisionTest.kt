package org.siloserver.silo.tv.ui.screens.watchtogether

import org.siloserver.silo.model.watchtogether.RoomPhase
import org.siloserver.silo.model.watchtogether.RoomSnapshot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure decision test for the lobby → synced-player hand-off. Mirrors the mobile
 * lobby's `lobbyPlayerDestinationOrNull` semantics: enter only when the room is
 * actually [RoomPhase.Playing] AND a (non-blank) selection has landed.
 *
 * Route-independent — [shouldEnterSyncedPlayer] returns a Boolean over the
 * landed [RoomSnapshot] (no Android types), so this stays a plain JVM unit test.
 */
class LobbyNavigationDecisionTest {

    private fun fixture(phase: RoomPhase, selectedContentId: String?): RoomSnapshot =
        RoomSnapshot(
            roomId = "room-1",
            phase = phase,
            selectedContentId = selectedContentId,
        )

    @Test
    fun entersWhenSelectionSetAndPlaying() {
        assertTrue(
            shouldEnterSyncedPlayer(
                fixture(phase = RoomPhase.Playing, selectedContentId = "m1"),
            ),
        )
    }

    @Test
    fun staysInLobbyWhenNoSelection() {
        assertFalse(
            shouldEnterSyncedPlayer(
                fixture(phase = RoomPhase.Lobby, selectedContentId = null),
            ),
        )
    }

    @Test
    fun staysInLobbyWhenSelectionButNotPlaying() {
        assertFalse(
            shouldEnterSyncedPlayer(
                fixture(phase = RoomPhase.Lobby, selectedContentId = "m1"),
            ),
        )
    }

    @Test
    fun nullSnapshotDoesNotEnter() {
        assertFalse(shouldEnterSyncedPlayer(null))
    }
}
