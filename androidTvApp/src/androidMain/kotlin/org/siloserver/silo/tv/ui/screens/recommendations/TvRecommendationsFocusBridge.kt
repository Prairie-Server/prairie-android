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

internal suspend fun requestRecommendationRowFocus(
    requestRowContainer: () -> Boolean,
    awaitFrame: suspend () -> Unit,
    requestFirstCard: () -> Boolean,
): Boolean {
    if (!requestRowContainer()) return false
    awaitFrame()
    requestFirstCard()
    return true
}

internal fun shouldBridgeRecommendationsDown(
    showingRecommendations: Boolean,
    hasVisibleRecommendations: Boolean,
): Boolean = showingRecommendations && hasVisibleRecommendations
