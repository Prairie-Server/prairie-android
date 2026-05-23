package com.continuum.app.tv.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bundle of optional callbacks for the TV long-press / DPAD-center-hold
 * context menu on a media card. Mirrors
 * [com.continuum.app.android.ui.components.MediaCardActions] from the phone
 * app — kept separate to avoid pulling phone components into the TV module.
 */
data class TvMediaCardActions(
    val onSetWatched: ((watched: Boolean) -> Unit)? = null,
    val onToggleFavorite: ((favorite: Boolean) -> Unit)? = null,
    val onToggleWatchlist: ((inWatchlist: Boolean) -> Unit)? = null,
    val onRemoveFromContinueWatching: (() -> Unit)? = null,
) {
    val isEmpty: Boolean
        get() = onSetWatched == null &&
            onToggleFavorite == null &&
            onToggleWatchlist == null &&
            onRemoveFromContinueWatching == null
}

/**
 * Long-press context menu rendered as a Material 3 DropdownMenu anchored to
 * a focused TV card. The DPAD captures focus inside the popup, so users can
 * navigate the actions with the d-pad and press DPAD_CENTER to select.
 */
@Composable
fun TvMediaCardContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: TvMediaCardActions,
    isPlayed: Boolean,
    isFavorite: Boolean,
    isInWatchlist: Boolean,
) {
    if (actions.isEmpty) return

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        actions.onSetWatched?.let { setWatched ->
            TvMenuRow(
                text = if (isPlayed) "Mark as Unwatched" else "Mark as Watched",
                icon = if (isPlayed) Icons.Default.VisibilityOff else Icons.Default.Check,
                onClick = {
                    setWatched(!isPlayed)
                    onDismiss()
                },
            )
        }
        actions.onToggleFavorite?.let { toggle ->
            TvMenuRow(
                text = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                onClick = {
                    toggle(!isFavorite)
                    onDismiss()
                },
            )
        }
        actions.onToggleWatchlist?.let { toggle ->
            TvMenuRow(
                text = if (isInWatchlist) "Remove from Watchlist" else "Add to Watchlist",
                icon = if (isInWatchlist) Icons.Default.BookmarkRemove else Icons.Default.BookmarkAdd,
                onClick = {
                    toggle(!isInWatchlist)
                    onDismiss()
                },
            )
        }
        actions.onRemoveFromContinueWatching?.let { remove ->
            TvMenuRow(
                text = "Remove from Continue Watching",
                icon = Icons.Default.Close,
                onClick = {
                    remove()
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun TvMenuRow(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
