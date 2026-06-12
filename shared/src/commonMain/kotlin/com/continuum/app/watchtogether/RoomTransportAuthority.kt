package com.continuum.app.watchtogether

import com.continuum.app.model.watchtogether.MemberRole
import com.continuum.app.model.watchtogether.RoomPhase
import com.continuum.app.model.watchtogether.RoomSnapshot

/** A local transport intent originating from the UI: a play/pause toggle or a seek. */
enum class RoomTransportIntent { PlayPause, Seek }

/**
 * Single source of truth for Watch Together transport authority, shared across
 * mobile and TV. Mirrors silo-server `internal/watchtogether/service.go`:
 *  - no transport outside the [RoomPhase.Playing] phase;
 *  - SEEK is host-only — the server rejects a guest's seek regardless of policy,
 *    so the affordance is disabled for ALL guests (even under guest_play_pause);
 *  - PLAY/PAUSE follows `self_can_control_transport` (true for the host always,
 *    and for guests only under the guest_play_pause policy).
 *
 * Returns true when the local member may broadcast the [intent] as a
 * `transport_request`.
 */
fun roomTransportAuthorized(snapshot: RoomSnapshot?, intent: RoomTransportIntent): Boolean {
    if (snapshot == null || snapshot.phase != RoomPhase.Playing) return false
    return when (intent) {
        RoomTransportIntent.Seek -> snapshot.selfRole == MemberRole.Host && snapshot.selfCanControlTransport
        RoomTransportIntent.PlayPause -> snapshot.selfCanControlTransport
    }
}
