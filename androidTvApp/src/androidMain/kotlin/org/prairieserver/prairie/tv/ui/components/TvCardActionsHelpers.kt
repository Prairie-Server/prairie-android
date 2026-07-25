package org.prairieserver.prairie.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import org.prairieserver.prairie.domain.MediaActionsCoordinator
import org.prairieserver.prairie.model.catalog.BrowseItem
import org.prairieserver.prairie.model.catalog.MediaItemUserState
import org.prairieserver.prairie.network.ApiResult
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * TV-side counterpart to
 * [org.prairieserver.prairie.android.ui.components.rememberBrowseItemCardActions].
 * Builds a [TvMediaCardActions] backed by [MediaActionsCoordinator] for grids
 * (catalog, library detail, search) whose ViewModels don't manage these
 * actions directly. Optimistic state is held in Compose; failures roll back.
 */
@Composable
fun rememberTvBrowseItemCardActions(
    item: BrowseItem,
): Pair<TvMediaCardActions, MediaItemUserState> {
    val coordinator: MediaActionsCoordinator = koinInject()
    val scope = rememberCoroutineScope()

    // Key on userState too (phone parity): when refreshed data re-emits with a
    // changed userState, the card must reflect new watched/favorite/watchlist
    // badges instead of keeping the stale snapshot.
    var state by remember(item.contentId, item.userState) {
        mutableStateOf(item.userState ?: MediaItemUserState())
    }

    val actions = remember(item.contentId, coordinator, scope) {
        TvMediaCardActions(
            onSetWatched = { watched ->
                val previous = state
                state = state.copy(played = watched)
                scope.launch {
                    if (coordinator.setWatched(item.contentId, watched) !is ApiResult.Success) {
                        state = previous
                    }
                }
            },
            onToggleFavorite = { favorite ->
                val previous = state
                state = state.copy(isFavorite = favorite)
                scope.launch {
                    if (coordinator.toggleFavorite(item.contentId, favorite) !is ApiResult.Success) {
                        state = previous
                    }
                }
            },
            onToggleWatchlist = { inWatchlist ->
                val previous = state
                state = state.copy(inWatchlist = inWatchlist)
                scope.launch {
                    if (coordinator.toggleWatchlist(item.contentId, inWatchlist) !is ApiResult.Success) {
                        state = previous
                    }
                }
            },
        )
    }

    return actions to state
}
