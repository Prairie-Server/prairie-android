package org.siloserver.silo.common.player.video

import org.siloserver.silo.model.catalog.AudioTrack

/**
 * What the viewer wants the audio to be, as a CATALOG ordinal into
 * `FileVersion.audioTracks`.
 *
 * [explicit] separates a fresh decision from a restore: a launch pick or a
 * persisted fingerprint puts playback back where it was and must not be
 * recorded as a new choice for episode carry-over, whereas a picker or remote
 * selection must.
 *
 * [fileId] scopes it. Audio ordinals are per-file and the outgoing version can
 * stay interactive while a replacement loads, so an intent made in that window
 * must not be reconciled against a different file.
 */
data class DesiredAudio(
    val generation: Long,
    val catalogOrdinal: Int,
    val explicit: Boolean,
    val fileId: Int?,
    val confirmed: Boolean = false,
)

/**
 * A pending local audio switch: select [targetOrdinal] on the player, and on
 * confirmation commit [catalogOrdinal] as the viewer's choice.
 */
data class LocalAudioSelection(
    val generation: Long,
    val catalogOrdinal: Int,
    /** Media3 audio-group ordinal — what AudioTrackManager expects. */
    val targetOrdinal: Int,
    /**
     * Distinct per issuance. StateFlow conflates equal values, so re-applying
     * after a remount has to look different or the collector never fires.
     */
    val attempt: Long,
)

/**
 * What to do about the desired audio, given one track snapshot.
 *
 * Extracted from the ViewModel so the decision is testable on its own: the
 * ViewModel takes fourteen constructor dependencies, and every review round of
 * this area has turned up a case that neither reasoning nor the pure-helper
 * tests caught. The orchestration around it — generations, persistence, the
 * request flow — stays in the ViewModel; only the decision lives here.
 */
sealed interface AudioReconcileAction {
    /** Nothing to do with this snapshot. */
    data object None : AudioReconcileAction

    /** The intent belongs to a different file and must be abandoned. */
    data object DropForeignFile : AudioReconcileAction

    /** The player is on the wanted track. */
    data object Confirm : AudioReconcileAction

    /** Select this mounted ordinal on the player. */
    data class Apply(val targetOrdinal: Int) : AudioReconcileAction
}

/**
 * Decides what a snapshot means for [desired].
 *
 * @param selectedOrdinal the Media3 ordinal currently selected, if any.
 * @param planAudioOrdinal the catalog ordinal the server says it delivered.
 */
fun reconcileDesiredAudioAction(
    desired: DesiredAudio?,
    activeFileId: Int?,
    catalog: List<AudioTrack>,
    mounted: List<MountedAudioTrack>,
    selectedOrdinal: Int?,
    planAudioOrdinal: Int?,
): AudioReconcileAction {
    if (desired == null) return AudioReconcileAction.None
    // An empty or partial snapshot is not evidence of anything. The intent must
    // survive it: discarding on the first callback is what made a launch pick
    // silently fail.
    if (mounted.isEmpty()) return AudioReconcileAction.None

    // Audio ordinals are per-file, and the outgoing version stays interactive
    // while a replacement loads, so an intent from that window would otherwise
    // name a different track here.
    if (desired.fileId != null && desired.fileId != activeFileId) {
        return AudioReconcileAction.DropForeignFile
    }

    val wanted = catalog.getOrNull(desired.catalogOrdinal) ?: return AudioReconcileAction.None

    // Resolved ONCE against the whole snapshot. Matching a one-element list
    // asks a different question: the matcher stops as soon as one candidate
    // remains, so a main mix and its commentary — same language, same codec —
    // would confirm each other.
    val target = matchMountedAudioTrack(wanted, mounted)
        ?: return if (planAudioOrdinal == desired.catalogOrdinal) {
            // Not in this stream, but the server says it delivered this row: a
            // transcode's recoded output cannot identity-match its own source,
            // so this is satisfied rather than retried forever.
            AudioReconcileAction.Confirm
        } else {
            AudioReconcileAction.None
        }

    // Both ordinals come from this same snapshot, and target was resolved by
    // identity, so comparing them IS the identity comparison.
    return if (selectedOrdinal == target.ordinal) {
        AudioReconcileAction.Confirm
    } else {
        AudioReconcileAction.Apply(target.ordinal)
    }
}
