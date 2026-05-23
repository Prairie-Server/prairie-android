package com.continuum.app.android.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.continuum.app.android.ui.components.CardStyle
import com.continuum.app.android.ui.components.MediaCardActions
import com.continuum.app.android.ui.components.MediaRow
import com.continuum.app.model.section.ResolvedSection
import com.continuum.app.model.section.SectionItem

/**
 * Renders a single home screen section as a horizontal row of media cards.
 *
 * Wraps [MediaRow] with section-specific logic such as determining whether
 * to show progress bars (for "continue watching" type sections) and mapping
 * the section type to the appropriate "See All" navigation action.
 *
 * @param section The resolved section containing items.
 * @param onItemClick Callback with content ID when a card is tapped.
 * @param onSeeAllClick Callback when the "See All" button is tapped.
 * @param modifier Compose modifier.
 */
@Composable
fun HomeSectionRow(
    section: ResolvedSection,
    onItemClick: (String) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    onSetWatched: ((String, Boolean) -> Unit)? = null,
    onToggleFavorite: ((String, Boolean) -> Unit)? = null,
    onToggleWatchlist: ((String, Boolean) -> Unit)? = null,
    onDismissContinueWatching: ((SectionItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (section.items.isEmpty()) return

    val isContinueWatching = section.sectionType == "continue_watching" ||
        section.sectionType == "in_progress"
    val showProgress = isContinueWatching

    val useBackdrop = showProgress || section.sectionType == "next_up"
    val cardStyle = if (useBackdrop) CardStyle.Backdrop else CardStyle.Poster

    MediaRow(
        title = section.title,
        items = section.items,
        onItemClick = onItemClick,
        onSeeAllClick = onSeeAllClick,
        showProgress = showProgress,
        cardStyle = cardStyle,
        modifier = modifier,
        cardActions = { item ->
            MediaCardActions(
                onSetWatched = onSetWatched?.let { cb -> { watched -> cb(item.contentId, watched) } },
                onToggleFavorite = onToggleFavorite?.let { cb -> { fav -> cb(item.contentId, fav) } },
                onToggleWatchlist = onToggleWatchlist?.let { cb -> { wl -> cb(item.contentId, wl) } },
                onRemoveFromContinueWatching = if (isContinueWatching && onDismissContinueWatching != null && item.progressUpdatedAt != null) {
                    { onDismissContinueWatching(item) }
                } else null,
            )
        },
    )
}
