package com.continuum.app.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.continuum.app.domain.MediaActionsCoordinator
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.catalog.MediaItemUserState
import com.continuum.app.network.ApiResult
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * TV-side counterpart to
 * [com.continuum.app.android.ui.components.rememberBrowseItemCardActions].
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

    var state by remember(item.contentId) {
        mutableStateOf(item.userState ?: MediaItemUserState())
    }

    val actions = TvMediaCardActions(
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

    return actions to state
}
