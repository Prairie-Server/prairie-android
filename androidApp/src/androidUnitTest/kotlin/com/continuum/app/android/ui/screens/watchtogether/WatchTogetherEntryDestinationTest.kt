package com.continuum.app.android.ui.screens.watchtogether

import com.continuum.app.android.ui.navigation.Route
import com.continuum.app.model.watchtogether.RoomPhase
import com.continuum.app.model.watchtogether.RoomPlaybackState
import com.continuum.app.model.watchtogether.RoomSelectionMode
import com.continuum.app.model.watchtogether.RoomSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Asserts on real Route.route strings, which call android.net.Uri.encode —
// Robolectric provides the real Android impl under plain JVM unit tests.
// Pinned to SDK 34 (the project targetSdk 35 is newer than this Robolectric
// release ships an emulated runtime for).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WatchTogetherEntryDestinationTest {

    private fun snapshot(
        roomId: String = "room-1",
        selectedContentId: String? = null,
        selectedFileId: Int? = null,
    ) = RoomSnapshot(
        roomId = roomId,
        phase = RoomPhase.Lobby,
        playbackState = RoomPlaybackState.Idle,
        selectionMode = RoomSelectionMode.HostPick,
        selectionRevision = 0L,
        selectedContentId = selectedContentId,
        selectedFileId = selectedFileId,
    )

    @Test
    fun host_with_selection_goes_to_player_with_roomId() {
        val dest = watchTogetherDestination(snapshot(selectedContentId = "c1", selectedFileId = 7))
        assertEquals(Route.Player(contentId = "c1", fileId = 7, roomId = "room-1").route, dest)
    }

    @Test
    fun no_selection_goes_to_lobby() {
        val dest = watchTogetherDestination(snapshot(selectedContentId = null))
        assertEquals(Route.WatchTogetherLobby(roomId = "room-1").route, dest)
    }

    @Test
    fun selection_set_but_no_fileId_still_routes_to_player() {
        val dest = watchTogetherDestination(snapshot(selectedContentId = "c2", selectedFileId = null))
        assertEquals(Route.Player(contentId = "c2", fileId = null, roomId = "room-1").route, dest)
    }
}
