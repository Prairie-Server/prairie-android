package org.prairieserver.prairie.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.prairieserver.prairie.android.ui.theme.PrairieBorder
import org.prairieserver.prairie.android.ui.theme.PrairieSurfaceContainerHigh
import org.prairieserver.prairie.android.ui.theme.PrairieSurfaceContainerHighest
import org.prairieserver.prairie.common.ui.components.BoxSetSleeveColors
import org.prairieserver.prairie.model.catalog.CatalogBoxSet
import org.prairieserver.prairie.model.catalog.boxSetFilmCount
import org.prairieserver.prairie.model.catalog.cardCaption
import org.prairieserver.prairie.model.catalog.watchedFraction

/**
 * The card of a movie box set: one poster for the movies TMDB lists in one
 * collection, with the two sleeves peeking above it, the BOX SET pill and the
 * film count that set it apart from a single movie. It opens the box set and has no item menu of its own.
 */
@Composable
fun BoxSetCard(
    boxSet: CatalogBoxSet,
    onClick: () -> Unit,
    width: Dp,
    modifier: Modifier = Modifier,
    /** The card's poster: the box set's own, or a member's when it has none. */
    posterUrl: String? = boxSet.posterUrl,
    posterThumbhash: String? = boxSet.posterThumbhash,
) {
    MediaCard(
        title = boxSet.title,
        posterUrl = posterUrl,
        posterThumbhash = posterThumbhash,
        subtitle = boxSet.cardCaption(),
        type = "movie",
        progress = boxSet.watchedFraction,
        onClick = onClick,
        width = width,
        detailBackdropUrl = boxSet.backdropUrl,
        detailBackdropThumbhash = boxSet.backdropThumbhash,
        badge = "Box set",
        cornerLabel = boxSetFilmCount(boxSet.movieCount),
        sleeves = BoxSetSleeves,
        modifier = modifier,
    )
}

/** The web's `surface-raised`, `surface-hover` and `border`, from the nearest theme tokens. */
private val BoxSetSleeves = BoxSetSleeveColors(
    back = PrairieSurfaceContainerHighest,
    front = PrairieSurfaceContainerHigh,
    border = PrairieBorder,
)
