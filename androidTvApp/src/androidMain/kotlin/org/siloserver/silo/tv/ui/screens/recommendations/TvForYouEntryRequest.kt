package org.siloserver.silo.tv.ui.screens.recommendations

enum class SavedListSelection {
    Watchlist,
    Favorites,
}

data class TvForYouEntryRequest(
    val sequence: Int = 0,
    val selection: SavedListSelection? = null,
) {
    fun next(selection: SavedListSelection?): TvForYouEntryRequest =
        TvForYouEntryRequest(sequence = sequence + 1, selection = selection)
}

internal data class AppliedForYouSelection(
    val selection: SavedListSelection?,
    val lastAppliedSequence: Int,
)

internal fun applyForYouEntryRequest(
    currentSelection: SavedListSelection?,
    lastAppliedSequence: Int,
    request: TvForYouEntryRequest,
): AppliedForYouSelection =
    if (request.sequence <= lastAppliedSequence) {
        AppliedForYouSelection(
            selection = currentSelection,
            lastAppliedSequence = lastAppliedSequence,
        )
    } else {
        AppliedForYouSelection(
            selection = request.selection,
            lastAppliedSequence = request.sequence,
        )
    }
