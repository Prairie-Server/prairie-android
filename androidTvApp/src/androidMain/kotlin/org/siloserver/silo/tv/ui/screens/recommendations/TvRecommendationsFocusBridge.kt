package org.siloserver.silo.tv.ui.screens.recommendations

internal data class ForYouFocusTarget(
    val sectionId: String,
    val contentId: String,
    val rowIndex: Int,
    val cardIndex: Int,
)

internal data class ForYouFocusRow(
    val sectionId: String,
    val contentIds: List<String>,
)

internal data class ResolvedForYouFocusTarget(
    val rowIndex: Int,
    val cardIndex: Int,
    val exact: Boolean,
)

internal data class ForYouDetailReturnState(
    val requestId: Int,
    val pending: Boolean,
)

internal data class ForYouReturnFocusLocation(
    val requestId: Int,
    val rowIndex: Int,
    val cardIndex: Int,
    val sectionId: String,
    val contentId: String,
)

internal enum class ForYouReturnTargetState {
    NotAttached,
    Attached,
    Disposed,
}

internal enum class ForYouReturnFocusResult {
    Focused,
    Exhausted,
    Disposed,
}

internal fun shouldFallbackForYouReturnToFilter(
    resolved: ResolvedForYouFocusTarget?,
): Boolean = resolved == null

internal enum class FocusRequestOutcome {
    Handled,
    Rejected,
    Disposed,
}

internal fun requestFocusSafely(
    requestFocus: () -> Boolean,
): FocusRequestOutcome = runCatching(requestFocus).fold(
    onSuccess = { handled ->
        if (handled) FocusRequestOutcome.Handled else FocusRequestOutcome.Rejected
    },
    onFailure = { FocusRequestOutcome.Disposed },
)

internal fun resolveForYouReturnTarget(
    target: ForYouFocusTarget,
    rows: List<ForYouFocusRow>,
): ResolvedForYouFocusTarget? {
    if (rows.isEmpty()) return null
    val stableRowIndex = rows.indexOfFirst { it.sectionId == target.sectionId }
    if (stableRowIndex >= 0) {
        val cards = rows[stableRowIndex].contentIds
        if (cards.isEmpty()) return null
        val stableCardIndex = cards.indexOf(target.contentId)
        return if (stableCardIndex >= 0) {
            ResolvedForYouFocusTarget(stableRowIndex, stableCardIndex, true)
        } else {
            ResolvedForYouFocusTarget(
                stableRowIndex,
                target.cardIndex.coerceIn(cards.indices),
                false,
            )
        }
    }
    val fallbackRowIndex = target.rowIndex.coerceIn(rows.indices)
    val fallbackCards = rows[fallbackRowIndex].contentIds
    if (fallbackCards.isEmpty()) return null
    return ResolvedForYouFocusTarget(fallbackRowIndex, 0, false)
}

internal fun beginForYouDetailReturn(
    previousRequestId: Int,
): ForYouDetailReturnState = ForYouDetailReturnState(
    requestId = previousRequestId + 1,
    pending = true,
)

internal fun consumeForYouDetailReturn(
    state: ForYouDetailReturnState,
    completedRequestId: Int,
): ForYouDetailReturnState = if (state.pending && state.requestId == completedRequestId) {
    state.copy(pending = false)
} else {
    state
}

internal fun resolvePendingForYouReturnLocation(
    state: ForYouDetailReturnState,
    launchTarget: ForYouFocusTarget?,
    rows: List<ForYouFocusRow>,
): ForYouReturnFocusLocation? {
    if (!state.pending || launchTarget == null) return null
    val resolved = resolveForYouReturnTarget(launchTarget, rows) ?: return null
    val row = rows.getOrNull(resolved.rowIndex) ?: return null
    val contentId = row.contentIds.getOrNull(resolved.cardIndex) ?: return null
    return ForYouReturnFocusLocation(
        requestId = state.requestId,
        rowIndex = resolved.rowIndex,
        cardIndex = resolved.cardIndex,
        sectionId = row.sectionId,
        contentId = contentId,
    )
}

internal suspend fun requestPendingForYouReturnFocus(
    maxAttempts: Int,
    awaitFrame: suspend () -> Unit,
    targetState: () -> ForYouReturnTargetState,
    requestRowContainer: () -> FocusRequestOutcome,
    awaitRowFrame: suspend () -> Unit,
    requestCard: () -> FocusRequestOutcome,
): ForYouReturnFocusResult {
    repeat(maxAttempts) {
        awaitFrame()
        when (targetState()) {
            ForYouReturnTargetState.NotAttached -> Unit
            ForYouReturnTargetState.Disposed -> return ForYouReturnFocusResult.Disposed
            ForYouReturnTargetState.Attached -> {
                when (requestRowContainer()) {
                    FocusRequestOutcome.Rejected -> Unit
                    FocusRequestOutcome.Disposed -> return ForYouReturnFocusResult.Disposed
                    FocusRequestOutcome.Handled -> {
                        awaitRowFrame()
                        when (requestCard()) {
                            FocusRequestOutcome.Handled -> return ForYouReturnFocusResult.Focused
                            FocusRequestOutcome.Rejected -> Unit
                            FocusRequestOutcome.Disposed -> return ForYouReturnFocusResult.Disposed
                        }
                    }
                }
            }
        }
    }
    return ForYouReturnFocusResult.Exhausted
}

internal suspend fun requestRecommendationRowFocus(
    requestRowContainer: () -> Boolean,
    awaitFrame: suspend () -> Unit,
    requestFirstCard: () -> Boolean,
): Boolean {
    if (!requestRowContainer()) return false
    awaitFrame()
    return requestFirstCard()
}

internal fun shouldBridgeRecommendationsDown(
    showingRecommendations: Boolean,
    hasVisibleRecommendations: Boolean,
): Boolean = showingRecommendations && hasVisibleRecommendations
