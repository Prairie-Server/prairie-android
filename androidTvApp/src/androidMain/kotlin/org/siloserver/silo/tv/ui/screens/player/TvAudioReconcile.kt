package org.siloserver.silo.tv.ui.screens.player

import org.siloserver.silo.common.player.video.MountedAudioTrack
import org.siloserver.silo.common.player.video.matchMountedAudioTrack
import org.siloserver.silo.model.catalog.AudioTrack

/**
 * What to do about the desired audio, given one track snapshot.
 *
 * Extracted from the ViewModel so the decision is testable on its own: the
 * ViewModel takes fourteen constructor dependencies, and every review round of
 * this area has turned up a case that neither reasoning nor the pure-helper
 * tests caught. The orchestration around it — generations, persistence, the
 * request flow — stays in the ViewModel; only the decision lives here.
 */
internal sealed interface TvAudioReconcileAction {
    /** Nothing to do with this snapshot. */
    data object None : TvAudioReconcileAction

    /** The intent belongs to a different file and must be abandoned. */
    data object DropForeignFile : TvAudioReconcileAction

    /** The player is on the wanted track. */
    data object Confirm : TvAudioReconcileAction

    /** Select this mounted ordinal on the player. */
    data class Apply(val targetOrdinal: Int) : TvAudioReconcileAction
}

/**
 * Decides what a snapshot means for [desired].
 *
 * @param selectedOrdinal the Media3 ordinal currently selected, if any.
 * @param planAudioOrdinal the catalog ordinal the server says it delivered.
 */
internal fun reconcileDesiredAudioAction(
    desired: TvDesiredAudio?,
    activeFileId: Int?,
    catalog: List<AudioTrack>,
    mounted: List<MountedAudioTrack>,
    selectedOrdinal: Int?,
    planAudioOrdinal: Int?,
): TvAudioReconcileAction {
    if (desired == null) return TvAudioReconcileAction.None
    // An empty or partial snapshot is not evidence of anything. The intent must
    // survive it: discarding on the first callback is what made a launch pick
    // silently fail.
    if (mounted.isEmpty()) return TvAudioReconcileAction.None

    // Audio ordinals are per-file, and the outgoing version stays interactive
    // while a replacement loads, so an intent from that window would otherwise
    // name a different track here.
    if (desired.fileId != null && desired.fileId != activeFileId) {
        return TvAudioReconcileAction.DropForeignFile
    }

    val wanted = catalog.getOrNull(desired.catalogOrdinal) ?: return TvAudioReconcileAction.None

    // Resolved ONCE against the whole snapshot. Matching a one-element list
    // asks a different question: the matcher stops as soon as one candidate
    // remains, so a main mix and its commentary — same language, same codec —
    // would confirm each other.
    val target = matchMountedAudioTrack(wanted, mounted)
        ?: return if (planAudioOrdinal == desired.catalogOrdinal) {
            // Not in this stream, but the server says it delivered this row: a
            // transcode's recoded output cannot identity-match its own source,
            // so this is satisfied rather than retried forever.
            TvAudioReconcileAction.Confirm
        } else {
            TvAudioReconcileAction.None
        }

    // Both ordinals come from this same snapshot, and target was resolved by
    // identity, so comparing them IS the identity comparison.
    return if (selectedOrdinal == target.ordinal) {
        TvAudioReconcileAction.Confirm
    } else {
        TvAudioReconcileAction.Apply(target.ordinal)
    }
}
