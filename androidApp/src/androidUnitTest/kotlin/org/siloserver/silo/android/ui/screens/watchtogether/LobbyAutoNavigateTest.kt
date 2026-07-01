package org.siloserver.silo.android.ui.screens.watchtogether

import org.siloserver.silo.android.ui.navigation.Route
import org.siloserver.silo.model.watchtogether.RoomPhase
import org.siloserver.silo.model.watchtogether.RoomPlaybackState
import org.siloserver.silo.model.watchtogether.RoomSelectionMode
import org.siloserver.silo.model.watchtogether.RoomSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Asserts on real Route.route strings, which call android.net.Uri.encode —
// Robolectric provides the real Android impl under plain JVM unit tests.
// Pinned to SDK 34 (matches WatchTogetherEntryDestinationTest).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LobbyAutoNavigateTest {

    private fun room(selectedContentId: String?, phase: RoomPhase) = RoomSnapshot(
        roomId = "r1",
        phase = phase,
        playbackState = RoomPlaybackState.Idle,
        selectionMode = RoomSelectionMode.HostPick,
        selectionRevision = 0L,
        selectedContentId = selectedContentId,
        selectedFileId = 3,
    )

    @Test fun playing_with_selection_navigates_to_player() {
        val dest = lobbyPlayerDestinationOrNull(room("c9", phase = RoomPhase.Playing))
        assertEquals(Route.Player(contentId = "c9", fileId = 3, roomId = "r1").route, dest)
    }

    @Test fun lobby_phase_does_not_navigate() {
        assertNull(lobbyPlayerDestinationOrNull(room("c9", phase = RoomPhase.Lobby)))
    }

    @Test fun playing_without_selection_does_not_navigate() {
        assertNull(lobbyPlayerDestinationOrNull(room(null, phase = RoomPhase.Playing)))
    }
}
