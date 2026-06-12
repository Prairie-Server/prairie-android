package com.continuum.app.tv.ui.screens.player

import com.continuum.app.model.watchtogether.GuestControlPolicy
import com.continuum.app.model.watchtogether.MemberRole
import com.continuum.app.model.watchtogether.RoomPhase
import com.continuum.app.model.watchtogether.RoomSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure transport-authority gate tests. The gate decides whether a local
 * transport intent (play/pause vs seek) is allowed to be broadcast as a
 * `transport_request`. It now delegates to the shared `roomTransportAuthorized`
 * (the single source of truth mirroring silo-server): seek is host-only,
 * play/pause follows `self_can_control_transport`, nothing outside Playing.
 *
 * Fixtures mirror server truth: `self_can_control_transport` = isHost OR
 * (Playing && guest_play_pause), so a guest_play_pause guest in the Playing
 * phase has canControl=true (and is still blocked from seek by role).
 */
class TvRoomTransportGateTest {

    private fun fixture(
        policy: GuestControlPolicy,
        role: MemberRole,
        canControl: Boolean,
        phase: RoomPhase = RoomPhase.Playing,
    ): RoomSnapshot = RoomSnapshot(
        roomId = "room-1",
        phase = phase,
        guestControlPolicy = policy,
        selfRole = role,
        selfCanControlTransport = canControl,
    )

    @Test
    fun hostMayPlayPauseAndSeek() {
        val s = fixture(GuestControlPolicy.HostOnly, MemberRole.Host, canControl = true)
        assertEquals(TransportGate.Send, tvRoomTransportGate(s, TvTransportIntent.PlayPause))
        assertEquals(TransportGate.Send, tvRoomTransportGate(s, TvTransportIntent.Seek))
    }

    @Test
    fun guestHostOnlyBlocked() {
        val s = fixture(GuestControlPolicy.HostOnly, MemberRole.Guest, canControl = false)
        assertEquals(TransportGate.Blocked, tvRoomTransportGate(s, TvTransportIntent.PlayPause))
        assertEquals(TransportGate.Blocked, tvRoomTransportGate(s, TvTransportIntent.Seek))
    }

    @Test
    fun guestPlayPausePolicy() {
        val s = fixture(GuestControlPolicy.GuestPlayPause, MemberRole.Guest, canControl = true)
        assertEquals(TransportGate.Send, tvRoomTransportGate(s, TvTransportIntent.PlayPause))
        assertEquals(TransportGate.Blocked, tvRoomTransportGate(s, TvTransportIntent.Seek))
    }

    @Test
    fun noTransportOutsidePlaying() {
        val s = fixture(GuestControlPolicy.GuestPlayPause, MemberRole.Host, canControl = true, phase = RoomPhase.Lobby)
        assertEquals(TransportGate.Blocked, tvRoomTransportGate(s, TvTransportIntent.PlayPause))
    }

    @Test
    fun nullBlocked() {
        assertEquals(TransportGate.Blocked, tvRoomTransportGate(null, TvTransportIntent.Seek))
    }
}
