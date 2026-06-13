package com.continuum.app.model.playback

/**
 * Resolves the optional position sent to playback/start. A non-null override
 * is an explicit command, so zero is valid there; stored detail progress only
 * counts when it represents a real resume point.
 */
fun resolvePlaybackStartRequestPosition(
    overridePosition: Double?,
    detailPosition: Double?,
): Double? =
    overridePosition?.takeIf { it.isFinite() && it >= 0.0 }
        ?: detailPosition?.takeIf { it.isFinite() && it > 0.0 }

/**
 * Resolves the actual player start position after the server answers. Prefer
 * the server/session position over detail progress so stale zero values from
 * the detail payload cannot erase a real resume point.
 */
fun resolvePlaybackStartPosition(
    overridePosition: Double?,
    sessionPosition: Double,
    detailPosition: Double?,
): Double =
    overridePosition?.takeIf { it.isFinite() && it >= 0.0 }
        ?: sessionPosition.takeIf { it.isFinite() && it > 0.0 }
        ?: detailPosition?.takeIf { it.isFinite() && it > 0.0 }
        ?: 0.0
