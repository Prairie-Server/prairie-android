package org.prairieserver.prairie.tv.ui.screens.detail

import kotlinx.coroutines.withTimeoutOrNull

internal enum class TvSimilarFocusRestoreResult {
    Restored,
    Fallback,
    Revoked,
}

/**
 * Drives a pending More Like This return without tying its policy to Compose.
 *
 * Data and attachment use separate deadlines because they wait for different
 * things. Once the card exists, [maxFocusAttempts] bounds how often focus can
 * be requested while [attachmentTimeoutMillis] bounds the real time spent if
 * frame production stalls. Ownership is checked between attempts and after
 * every suspension so an obsolete restore neither keeps requesting focus nor
 * moves focus to the fallback on the viewer's behalf.
 */
internal suspend fun restoreMoreLikeThisFocus(
    awaitTarget: suspend () -> Unit,
    stillOwned: () -> Boolean,
    onTargetResolved: () -> Unit,
    isTargetFocused: () -> Boolean,
    requestTargetFocus: () -> Unit,
    awaitFocusAttempt: suspend () -> Unit,
    scrollToFallback: suspend () -> Unit,
    requestFallbackFocus: () -> Unit,
    dataTimeoutMillis: Long,
    attachmentTimeoutMillis: Long,
    maxFocusAttempts: Int = 40,
): TvSimilarFocusRestoreResult {
    val resolved = withTimeoutOrNull(dataTimeoutMillis) {
        awaitTarget()
        true
    } ?: false

    var restored = false
    if (resolved && stillOwned()) {
        onTargetResolved()
        var revoked = false
        withTimeoutOrNull(attachmentTimeoutMillis) {
            repeat(maxFocusAttempts) {
                if (isTargetFocused()) {
                    restored = true
                    return@withTimeoutOrNull
                }
                if (!stillOwned()) {
                    revoked = true
                    return@withTimeoutOrNull
                }
                requestTargetFocus()
                awaitFocusAttempt()
            }
        }
        if (revoked || !stillOwned()) return TvSimilarFocusRestoreResult.Revoked
        // A request made by the final attempt can be observed just after that
        // attempt completes, so check the callback-owned state once more.
        if (!restored) restored = isTargetFocused()
    }

    if (!stillOwned()) return TvSimilarFocusRestoreResult.Revoked
    if (restored) return TvSimilarFocusRestoreResult.Restored

    scrollToFallback()
    if (!stillOwned()) return TvSimilarFocusRestoreResult.Revoked
    // Scrolling suspends and can compose the target card; do not let fallback
    // focus steal a restore that completed while the scroll was in flight.
    if (isTargetFocused()) return TvSimilarFocusRestoreResult.Restored

    requestFallbackFocus()
    return TvSimilarFocusRestoreResult.Fallback
}
