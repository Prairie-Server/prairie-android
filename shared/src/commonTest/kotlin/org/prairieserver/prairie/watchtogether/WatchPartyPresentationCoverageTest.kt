package org.prairieserver.prairie.watchtogether

import org.prairieserver.prairie.model.watchtogether.MemberRole
import org.prairieserver.prairie.model.watchtogether.RoomMember
import org.prairieserver.prairie.model.watchtogether.RoomPhase
import org.prairieserver.prairie.model.watchtogether.RoomPlaybackState
import org.prairieserver.prairie.model.watchtogether.RoomSelectionMode
import org.prairieserver.prairie.model.watchtogether.RoomSnapshot
import org.prairieserver.prairie.model.watchtogether.Suggestion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchPartyPresentationCoverageTest {
    private fun playing(
        content: String? = "movie:a",
        file: Int? = 7,
        position: Double = 12.5,
        paused: Boolean = false,
        state: RoomPlaybackState = RoomPlaybackState.Playing,
        phase: RoomPhase = RoomPhase.Playing,
    ) = RoomSnapshot(
        roomId = "r",
        phase = phase,
        playbackState = state,
        selectionRevision = 3L,
        selectedContentId = content,
        selectedFileId = file,
        selectedLibraryId = 9,
        isPaused = paused,
        anchorPositionSeconds = position,
    )

    @Test
    fun playbackContextCarriesTheRoomsExactSelection() {
        val context = assertNotNull(playing().playbackContext())
        assertEquals("r", context.roomId)
        assertEquals(3L, context.selectionRevision)
        assertEquals("movie:a", context.contentId)
        assertEquals(7, context.fileId)
        assertEquals(9, context.libraryId)
        assertEquals(12.5, context.positionSeconds)
        assertEquals(false, context.paused)
    }

    @Test
    fun playbackContextIsNullUntilTheRoomIsPlayingWithAFile() {
        assertNull(playing(phase = RoomPhase.Lobby).playbackContext())
        assertNull(playing(content = null).playbackContext())
        assertNull(playing(content = " ").playbackContext())
        assertNull(playing(file = null).playbackContext())
        assertNull(playing(file = 0).playbackContext())
    }

    @Test
    fun playbackContextStartsPausedUnlessTheRoomIsPlaying() {
        assertTrue(assertNotNull(playing(paused = true).playbackContext()).paused)
        assertTrue(assertNotNull(playing(state = RoomPlaybackState.Paused).playbackContext()).paused)
        assertEquals(0.0, assertNotNull(playing(position = -1.0).playbackContext()).positionSeconds)
        assertEquals(0.0, assertNotNull(playing(position = Double.NaN).playbackContext()).positionSeconds)
    }

    private val host = RoomMember(userId = "1", profileId = "a", displayName = "Ana", isHost = true, connected = true)
    private val guest = RoomMember(userId = "2", profileId = "b", displayName = "ben", connected = true)

    @Test
    fun seatStatesAndStatusWords() {
        val phase = RoomPhase.Playing
        assertEquals(WatchPartySeatState.Away, watchPartySeatState(guest.copy(connected = false), phase))
        assertEquals(WatchPartySeatState.Buffering, watchPartySeatState(guest.copy(isBuffering = true), phase))
        assertEquals(WatchPartySeatState.Syncing, watchPartySeatState(guest.copy(isSyncing = true), phase))
        assertEquals(WatchPartySeatState.Watching, watchPartySeatState(guest.copy(isReady = true), phase))
        assertEquals(WatchPartySeatState.Joining, watchPartySeatState(guest, phase))
        assertEquals(WatchPartySeatState.Ready, watchPartySeatState(host, RoomPhase.Lobby))
        assertEquals(WatchPartySeatState.NotReady, watchPartySeatState(guest, RoomPhase.Lobby))

        assertNull(watchPartySeatStatus(WatchPartySeatState.Ready))
        assertNull(watchPartySeatStatus(WatchPartySeatState.Watching))
        assertEquals("not ready", watchPartySeatStatus(WatchPartySeatState.NotReady))
        assertEquals("reconnecting", watchPartySeatStatus(WatchPartySeatState.Away))
        assertEquals("syncing", watchPartySeatStatus(WatchPartySeatState.Syncing))
        assertEquals("buffering", watchPartySeatStatus(WatchPartySeatState.Buffering))
        assertEquals("joining", watchPartySeatStatus(WatchPartySeatState.Joining))
    }

    @Test
    fun seatOrderAndPresenceSummary() {
        val self = guest.copy(userId = "3", displayName = "Zed", isSelf = true)
        val other = guest.copy(userId = "4", displayName = "Cy")
        assertEquals(listOf("Ana", "Zed", "Cy"), watchPartySeatOrder(listOf(other, self, host)).map { it.displayName })

        assertEquals("nobody connected", watchPartyPresenceSummary(emptyList(), RoomPhase.Lobby))
        assertEquals("only you so far", watchPartyPresenceSummary(listOf(host), RoomPhase.Lobby))
        assertEquals("1 of 2 ready", watchPartyPresenceSummary(listOf(host, guest), RoomPhase.Lobby))
        assertEquals("1 of 2 watching", watchPartyPresenceSummary(listOf(host.copy(isReady = true), guest), RoomPhase.Playing))
    }

    private fun lobby(
        role: MemberRole,
        mode: RoomSelectionMode = RoomSelectionMode.HostPick,
        content: String? = null,
        phase: RoomPhase = RoomPhase.Lobby,
        hostConnected: Boolean = true,
    ) = RoomSnapshot(
        roomId = "r",
        phase = phase,
        selfRole = role,
        selfCanManageRoom = role == MemberRole.Host,
        selectionMode = mode,
        selectedContentId = content,
        hostConnected = hostConnected,
        members = listOf(host.copy(isSelf = role == MemberRole.Host), guest.copy(isSelf = role == MemberRole.Guest)),
    )

    private fun suggestion(id: String) = Suggestion(
        id = id, roomId = "r", contentId = "movie:$id", contentType = "movie", title = id,
        voteCount = 0, createdAt = "2026-09-25T10:00:00Z",
    )

    @Test
    fun lobbyHeroForVotingChosenAndOpenRooms() {
        val vote = lobby(MemberRole.Host, mode = RoomSelectionMode.Vote)
        assertTrue(watchPartyLobbyHero(vote, listOf(suggestion("a"))).isVoteQuestion)
        val one = watchPartyLobbyHero(vote, listOf(suggestion("a"))).eyebrow
        val two = watchPartyLobbyHero(vote, listOf(suggestion("a"), suggestion("b"))).eyebrow
        assertTrue(one.contains("1 title") && !one.contains("titles"))
        assertTrue(two.contains("2 titles"))
        assertTrue(watchPartyLobbyHero(lobby(MemberRole.Guest, mode = RoomSelectionMode.Vote), emptyList()).supporting!!.contains("vote"))

        val chosenByYou = watchPartyLobbyHero(lobby(MemberRole.Host, content = "movie:a"), emptyList(), selectionUnavailable = true)
        assertEquals("You chose this", chosenByYou.supporting)
        assertEquals("A title you can't see", chosenByYou.title)
        assertTrue(watchPartyLobbyHero(lobby(MemberRole.Guest, content = "movie:a"), emptyList()).supporting!!.endsWith("chose this"))
        assertTrue(watchPartyLobbyHero(lobby(MemberRole.Guest, content = "movie:a", phase = RoomPhase.Playing), emptyList()).eyebrow.startsWith("Now watching"))

        assertEquals("Pick something\nto watch", watchPartyLobbyHero(lobby(MemberRole.Host), emptyList()).title)
        assertTrue(watchPartyLobbyHero(lobby(MemberRole.Guest), emptyList()).title!!.startsWith("Waiting for"))
    }

    @Test
    fun connectionLabelAndBanner() {
        val open = lobby(MemberRole.Host)
        assertEquals(WatchPartyConnectionTone.Good to "2 here", watchPartyConnectionLabel(open, connected = true, reconnecting = false))
        val solo = open.copy(members = listOf(host))
        assertEquals(WatchPartyConnectionTone.Good to "Open", watchPartyConnectionLabel(solo, connected = true, reconnecting = false))
        val hostGone = lobby(MemberRole.Guest, hostConnected = false)
        assertEquals(WatchPartyConnectionTone.Warning to "Host away", watchPartyConnectionLabel(hostGone, connected = true, reconnecting = false))
        assertEquals(WatchPartyConnectionTone.Warning to "Reconnecting", watchPartyConnectionLabel(open, connected = false, reconnecting = true))
        assertEquals(WatchPartyConnectionTone.Neutral to "Connecting", watchPartyConnectionLabel(open, connected = false, reconnecting = false))

        assertTrue(watchPartyLobbyBanner(open, selectionUnavailable = true, reconnecting = false)!!.contains("Choose something else"))
        assertTrue(watchPartyLobbyBanner(lobby(MemberRole.Guest), selectionUnavailable = true, reconnecting = false)!!.contains("pick something else"))
        assertTrue(watchPartyLobbyBanner(hostGone, selectionUnavailable = false, reconnecting = false)!!.contains("lost connection"))
        assertEquals("Reconnecting to the party…", watchPartyLobbyBanner(open, selectionUnavailable = false, reconnecting = true))
        assertNull(watchPartyLobbyBanner(open, selectionUnavailable = false, reconnecting = false))
    }

    @Test
    fun avatarIndexIsStableAndBounded() {
        assertEquals(0, watchPartyAvatarIndex(guest, 0))
        val index = watchPartyAvatarIndex(guest, 6)
        assertTrue(index in 0..5)
        assertEquals(index, watchPartyAvatarIndex(guest, 6))
    }
}
