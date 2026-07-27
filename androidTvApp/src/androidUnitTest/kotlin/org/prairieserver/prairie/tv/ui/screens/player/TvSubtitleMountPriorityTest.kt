package org.prairieserver.prairie.tv.ui.screens.player

import org.prairieserver.prairie.common.player.subtitleArtifactTrackId
import org.prairieserver.prairie.model.playback.SubtitleIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TV drives subtitle mounting from two pipelines at once: the subtitle
 * transaction, and the legacy restore/auto machinery that reacts to track
 * changes. They share a single remount latch, and a transaction's own replan
 * republishes the track list — so the legacy pipeline reliably fires *after*
 * the transaction and used to overwrite the selection the user just made.
 *
 * Authority, not timing, decides who owns the mount.
 */
class TvSubtitleMountPriorityTest {

    private fun track(index: Int, trackId: String?) = PlayerTrackEntry(
        index = index,
        label = "English",
        language = "en",
        isSelected = false,
        codecOrMime = "subrip",
        isForced = false,
        trackId = trackId,
    )

    @Test
    fun `a transport restore cannot evict an in-flight user selection`() {
        val latch = SubtitleRemountReselection()
        val chosen = SubtitleIdentity.ServerSidecar(serverIndex = 0)
        latch.arm(chosen, generation = 1, priority = TvSubtitleMountPriority.UserTransaction)

        // The legacy transport remount arms from the PRE-transaction committed
        // state — Off — exactly as it did on device.
        latch.arm(SubtitleIdentity.Off, generation = 2, priority = TvSubtitleMountPriority.Restore)

        assertEquals(TvSubtitleMountPriority.UserTransaction, latch.pendingPriority)
        val event = assertIs<TvSubtitleRemountEvent.Select>(
            latch.consume(
                subtitleTracks = listOf(track(20, "1:" + subtitleArtifactTrackId(0))),
                snapshotKey = "tracks",
                settled = true,
            ),
        )
        assertEquals(20, event.trackIndex, "the user's track must still be the one mounted")
    }

    @Test
    fun `auto selection cannot evict an in-flight user selection`() {
        val latch = SubtitleRemountReselection()
        latch.arm(
            SubtitleIdentity.ServerSidecar(serverIndex = 0),
            generation = 1,
            priority = TvSubtitleMountPriority.UserTransaction,
        )

        latch.arm(SubtitleIdentity.Off, generation = 2, priority = TvSubtitleMountPriority.Auto)

        assertEquals(TvSubtitleMountPriority.UserTransaction, latch.pendingPriority)
    }

    @Test
    fun `a user selection may always take the latch from a restore`() {
        val latch = SubtitleRemountReselection()
        latch.arm(SubtitleIdentity.Off, generation = 1, priority = TvSubtitleMountPriority.Restore)

        val chosen = SubtitleIdentity.ServerSidecar(serverIndex = 2)
        latch.arm(chosen, generation = 2, priority = TvSubtitleMountPriority.UserTransaction)

        assertEquals(TvSubtitleMountPriority.UserTransaction, latch.pendingPriority)
        val event = assertIs<TvSubtitleRemountEvent.Select>(
            latch.consume(
                subtitleTracks = listOf(track(7, "1:" + subtitleArtifactTrackId(2))),
                snapshotKey = "tracks",
                settled = true,
            ),
        )
        assertEquals(7, event.trackIndex)
    }

    @Test
    fun `equal authority still replaces so a newer pick wins`() {
        val latch = SubtitleRemountReselection()
        latch.arm(
            SubtitleIdentity.ServerSidecar(serverIndex = 0),
            generation = 1,
            priority = TvSubtitleMountPriority.UserTransaction,
        )
        latch.arm(
            SubtitleIdentity.ServerSidecar(serverIndex = 5),
            generation = 2,
            priority = TvSubtitleMountPriority.UserTransaction,
        )

        val event = assertIs<TvSubtitleRemountEvent.Select>(
            latch.consume(
                subtitleTracks = listOf(
                    track(3, "1:" + subtitleArtifactTrackId(0)),
                    track(9, "1:" + subtitleArtifactTrackId(5)),
                ),
                snapshotKey = "tracks",
                settled = true,
            ),
        )
        assertEquals(9, event.trackIndex, "the most recent user pick owns the mount")
    }

    @Test
    fun `a rollback-armed Off cannot evict a pick between its match and its ack`() {
        // The device sequence: the user's sidecar matched at 09.038, a rollback
        // armed Off at 09.057, the sidecar mounted at 09.113 — and at 09.735 the
        // Off fired and switched it off. The latch was unowned between match and
        // acknowledgement, so the lower-authority Off walked straight in.
        val latch = SubtitleRemountReselection()
        val chosen = SubtitleIdentity.ServerSidecar(serverIndex = 0)
        latch.arm(chosen, generation = 1, priority = TvSubtitleMountPriority.UserTransaction)

        val matched = assertIs<TvSubtitleRemountEvent.Select>(
            latch.consume(
                listOf(track(20, "1:" + subtitleArtifactTrackId(0))),
                snapshotKey = "tracks",
                settled = true,
            ),
        )
        assertEquals(20, matched.trackIndex)

        // Rollback arms the predecessor identity while the mount is in flight.
        latch.arm(SubtitleIdentity.Off, generation = 3, priority = TvSubtitleMountPriority.Restore)
        assertEquals(
            TvSubtitleMountPriority.UserTransaction,
            latch.pendingPriority,
            "the resolved pick must keep defending its mount until acknowledged",
        )
        assertNull(
            latch.consume(emptyList(), snapshotKey = null, settled = true),
            "the rejected Off must not produce a disable",
        )

        // Once acknowledged, ordinary restores work again.
        latch.acknowledgeResolved(generation = 1)
        latch.arm(SubtitleIdentity.Off, generation = 4, priority = TvSubtitleMountPriority.Restore)
        assertEquals(TvSubtitleMountPriority.Restore, latch.pendingPriority)
    }

    @Test
    fun `the same sidecar merged twice still resolves`() {
        // Observed on device: Media3 reported the one sidecar under two merge
        // child indices, "1:prairie-subtitle:3" and "2:prairie-subtitle:3". Both
        // denote the same authored artifact, so refusing them as ambiguous left
        // the mount unresolved until the deadline blew and the whole
        // transaction rolled back to Off.
        val latch = SubtitleRemountReselection()
        latch.arm(
            SubtitleIdentity.ServerSidecar(serverIndex = 3),
            generation = 1,
            priority = TvSubtitleMountPriority.UserTransaction,
        )

        val event = assertIs<TvSubtitleRemountEvent.Select>(
            latch.consume(
                listOf(
                    track(11, "1:" + subtitleArtifactTrackId(3)),
                    track(12, "2:" + subtitleArtifactTrackId(3)),
                ),
                snapshotKey = "duplicated",
                settled = true,
            ),
        )
        assertEquals(11, event.trackIndex, "resolve deterministically to the first merge")
    }

    @Test
    fun `a lost acknowledgement is released so mounting cannot wedge`() {
        // The ack travels over a replay-0 flow collected in a backend-scoped
        // effect; a backend swap drops it. Without an explicit release the
        // resolved claim would defend a mount that can never be confirmed.
        val latch = SubtitleRemountReselection()
        latch.arm(
            SubtitleIdentity.ServerSidecar(serverIndex = 0),
            generation = 1,
            priority = TvSubtitleMountPriority.UserTransaction,
        )
        latch.consume(
            listOf(track(20, "1:" + subtitleArtifactTrackId(0))),
            snapshotKey = "tracks",
            settled = true,
        )

        latch.releaseResolved()

        latch.arm(SubtitleIdentity.Off, generation = 2, priority = TvSubtitleMountPriority.Restore)
        assertEquals(TvSubtitleMountPriority.Restore, latch.pendingPriority)
    }

    @Test
    fun `a restore owns the latch when no transaction is in flight`() {
        val latch = SubtitleRemountReselection()
        latch.arm(
            SubtitleIdentity.ServerSidecar(serverIndex = 1),
            generation = 1,
            priority = TvSubtitleMountPriority.Restore,
        )

        assertEquals(TvSubtitleMountPriority.Restore, latch.pendingPriority)
        assertTrue(latch.hasPendingOwner)
    }

    @Test
    fun `releasing the latch lets a lower authority arm again`() {
        val latch = SubtitleRemountReselection()
        latch.arm(
            SubtitleIdentity.ServerSidecar(serverIndex = 0),
            generation = 1,
            priority = TvSubtitleMountPriority.UserTransaction,
        )
        latch.clear()
        assertNull(latch.pendingPriority)

        latch.arm(SubtitleIdentity.Off, generation = 2, priority = TvSubtitleMountPriority.Restore)

        assertEquals(TvSubtitleMountPriority.Restore, latch.pendingPriority)
    }
}
