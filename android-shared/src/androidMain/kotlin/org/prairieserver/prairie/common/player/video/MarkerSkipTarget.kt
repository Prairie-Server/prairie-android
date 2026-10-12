package org.prairieserver.prairie.common.player.video

import org.prairieserver.prairie.model.catalog.TimeRange

enum class ManualMarkerKind { Recap, Credits }
data class MarkerSkipTarget(val kind: ManualMarkerKind, val endSeconds: Double)

/** The active range is end-exclusive. Recap wins if provider ranges overlap. */
fun markerSkipTarget(position: Double, recap: TimeRange?, credits: TimeRange?): MarkerSkipTarget? {
    if (!position.isFinite()) return null
    fun TimeRange.active() = start.isFinite() && end.isFinite() && start >= 0 &&
        end > start && position >= start && position < end
    return when {
        recap?.active() == true -> MarkerSkipTarget(ManualMarkerKind.Recap, recap.end)
        credits?.active() == true -> MarkerSkipTarget(ManualMarkerKind.Credits, credits.end)
        else -> null
    }
}
