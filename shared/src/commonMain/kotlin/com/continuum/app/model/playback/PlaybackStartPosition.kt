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

/** Below this resume point we do not nudge backwards (too close to the start to matter). */
const val MinResumeForRewindSeconds: Double = 30.0

/**
 * Skip-back-on-resume: when resuming a partially-watched item, begin a few
 * seconds before the saved position to re-establish context.
 *
 * Applies ONLY to a genuine resume — never to a fresh start (resolved position
 * 0) and never to an explicit override (Start Over / a commanded position /
 * Watch Together anchor / retry, all flagged via [isExplicitOverride]). Resumes
 * below [MinResumeForRewindSeconds] are left untouched. The result is clamped
 * at 0.
 */
fun applyResumeRewind(
    resolvedStartPosition: Double,
    isExplicitOverride: Boolean,
    rewindSeconds: Double,
): Double {
    // Defensive: a non-finite/negative start is treated as a fresh start, and a
    // non-finite/non-positive rewind disables the nudge — so a bad setting or a
    // future direct caller can never produce an invalid seek position.
    val start = resolvedStartPosition.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    if (isExplicitOverride) return start
    val rewind = rewindSeconds.takeIf { it.isFinite() && it > 0.0 } ?: return start
    if (start < MinResumeForRewindSeconds) return start
    return (start - rewind).coerceAtLeast(0.0)
}
