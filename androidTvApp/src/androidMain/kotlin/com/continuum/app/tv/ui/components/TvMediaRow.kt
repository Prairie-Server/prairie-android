package com.continuum.app.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.ExperimentalComposeUiApi
import com.continuum.app.model.section.SectionItem
import com.continuum.app.tv.ui.theme.Spacing

/** Visual style of cards inside a [TvMediaRow]. */
enum class TvRowStyle { Poster, Backdrop }
enum class TvRowCardLayout { Default, ReferenceShelf }

/**
 * Horizontal row with a header and a lazy list of cards. This is the workhorse
 * of the home screen and library-detail recommended views. The row adds a
 * little vertical padding so the focus lift on cards doesn't clip against the
 * top/bottom of its bounds.
 *
 * @param items the section items to render.
 * @param onItemClick fired when a card is pressed.
 * @param showProgress if true, displays the item's resume progress (used by
 *  continue-watching rows).
 * @param style whether to render items as 2:3 posters or 16:9 backdrops.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvMediaRow(
    title: String,
    items: List<SectionItem>,
    onItemClick: (contentId: String) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onSeeAllClick: (() -> Unit)? = null,
    showProgress: Boolean = false,
    style: TvRowStyle = TvRowStyle.Poster,
    cardLayout: TvRowCardLayout = TvRowCardLayout.Default,
    horizontalPadding: androidx.compose.ui.unit.Dp = Spacing.safeArea,
    startPadding: androidx.compose.ui.unit.Dp = horizontalPadding,
    endPadding: androidx.compose.ui.unit.Dp = horizontalPadding,
    itemSpacing: androidx.compose.ui.unit.Dp = 20.dp,
    rowTopPadding: androidx.compose.ui.unit.Dp = 24.dp,
    rowBottomPadding: androidx.compose.ui.unit.Dp = 24.dp,
    eyebrow: String? = null,
    itemCardModifier: Modifier = Modifier,
    upFocusRequester: FocusRequester? = null,
    firstItemFocusRequester: FocusRequester? = null,
    firstItemFocusRequest: Int = 0,
    firstItemCardModifier: Modifier = Modifier,
    /** Fired (on focus GAIN only) with whichever card the user focuses, so the
     *  Skyline marquee + backdrop can preview the focused item. */
    onItemFocused: ((SectionItem) -> Unit)? = null,
    cardActions: (SectionItem) -> TvMediaCardActions = { TvMediaCardActions() },
) {
    if (items.isEmpty()) return
    val rowState = rememberLazyListState()

    LaunchedEffect(firstItemFocusRequest) {
        if (firstItemFocusRequest > 0 && firstItemFocusRequester != null) {
            runCatching { firstItemFocusRequester.requestFocus() }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvSectionHeader(
            title = title,
            icon = icon,
            onSeeAllClick = onSeeAllClick,
            eyebrow = eyebrow,
            modifier = Modifier.padding(start = startPadding, end = endPadding),
        )
        LazyRow(
            state = rowState,
            // focusRestorer remembers the last-focused card inside this row.
            // After the user UPs to the menu and DOWNs back to the row,
            // focus returns to that exact card instead of jumping back to
            // index 0. The fallback (used the very first time, when nothing
            // is remembered yet) is the explicit firstItemFocusRequester
            // attached to index 0 — or Compose's default first-focusable
            // search if the row wasn't given one.
            modifier = Modifier.focusRestorer(
                firstItemFocusRequester ?: FocusRequester.Default,
            ),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            contentPadding = PaddingValues(
                start = startPadding,
                end = endPadding,
                top = rowTopPadding,
                bottom = rowBottomPadding,
            ),
        ) {
            itemsIndexed(items, key = { _, item -> item.contentId }) { index, item ->
                val progress = if (showProgress) item.progressFraction() else null
                val remaining = if (showProgress) item.remainingMinutes() else null
                // Always anchor firstItemFocusRequester to index 0 so it can
                // serve as a stable fallback target for focusRestorer and for
                // imperative requestFocus() calls from parent screens.
                val itemFocusRequester = firstItemFocusRequester.takeIf { index == 0 }
                val appliedCardModifier = itemCardModifier.then(
                    if (index == 0) firstItemCardModifier else Modifier,
                ).then(
                    if (upFocusRequester != null) {
                        Modifier.focusProperties { up = upFocusRequester }
                    } else {
                        Modifier
                    },
                ).then(
                    if (onItemFocused != null) {
                        Modifier.onFocusChanged { st ->
                            if (st.isFocused) onItemFocused(item)
                        }
                    } else {
                        Modifier
                    },
                )
                val itemActions = cardActions(item)
                when (cardLayout) {
                    TvRowCardLayout.ReferenceShelf -> TvReferenceShelfCard(
                        title = item.shelfTitle(showProgress = showProgress),
                        imageUrl = item.bestBackdropUrl(),
                        imageThumbhash = item.bestBackdropThumbhash(),
                        subtitle = item.shelfSubtitle(showProgress = showProgress),
                        detail = if (showProgress) remaining?.let { "${it}m left" } else null,
                        progress = progress,
                        onClick = { onItemClick(item.contentId) },
                        focusRequester = itemFocusRequester,
                        cardModifier = appliedCardModifier,
                        userState = item.userState,
                        actions = itemActions,
                    )
                    TvRowCardLayout.Default -> when (style) {
                        TvRowStyle.Backdrop -> TvEpisodeCard(
                            title = item.title,
                            stillUrl = item.bestBackdropUrl(),
                            stillThumbhash = item.bestBackdropThumbhash(),
                            seriesTitle = item.seriesTitle,
                            seasonNumber = item.seasonNumber,
                            episodeNumber = item.episodeNumber,
                            progress = progress,
                            remainingMinutes = remaining,
                            onClick = { onItemClick(item.contentId) },
                            focusRequester = itemFocusRequester,
                            cardModifier = appliedCardModifier,
                            userState = item.userState,
                            actions = itemActions,
                        )
                        TvRowStyle.Poster -> TvMediaCard(
                            title = item.title,
                            posterUrl = item.posterUrl,
                            posterThumbhash = item.posterThumbhash,
                            year = item.year.takeIf { it > 0 },
                            userState = item.userState,
                            progress = progress,
                            onClick = { onItemClick(item.contentId) },
                            focusRequester = itemFocusRequester,
                            cardModifier = appliedCardModifier,
                            actions = itemActions,
                        )
                    }
                }
            }
        }
    }
}

/** Fraction [0..1] of item consumed for "continue watching" progress bars. */
private fun SectionItem.progressFraction(): Float? {
    val pos = positionSeconds ?: return null
    val dur = durationSeconds ?: return null
    if (dur <= 0) return null
    return (pos / dur).toFloat().coerceIn(0f, 1f)
}

/** Minutes remaining, used as the "12m left" caption on continue-watching cards. */
private fun SectionItem.remainingMinutes(): Int? {
    val pos = positionSeconds ?: return null
    val dur = durationSeconds ?: return null
    if (dur <= 0 || pos >= dur) return null
    return ((dur - pos) / 60.0).toInt()
}

/**
 * Episodes have backdrop/still frames on `posterUrl`; movies have them on
 * `backdropUrl`. Prefer the more specific field when present.
 */
private fun SectionItem.bestBackdropUrl(): String? {
    val isEpisode = seriesTitle != null
    return if (isEpisode) posterUrl ?: backdropUrl else backdropUrl ?: posterUrl
}

private fun SectionItem.bestBackdropThumbhash(): String? {
    val isEpisode = seriesTitle != null
    return if (isEpisode) {
        posterThumbhash ?: backdropThumbhash
    } else {
        backdropThumbhash ?: posterThumbhash
    }
}

private fun SectionItem.shelfTitle(showProgress: Boolean): String {
    return if (showProgress && !seriesTitle.isNullOrBlank()) {
        seriesTitle ?: title
    } else {
        title
    }
}

private fun SectionItem.shelfSubtitle(showProgress: Boolean): String? {
    if (showProgress) {
        val tag = formatShelfEpisodeTag(seasonNumber, episodeNumber)
        return listOfNotNull(tag, title.takeIf { it.isNotBlank() }).joinToString(" • ").ifBlank { null }
    }
    return when {
        year > 0 && genres.isNotEmpty() -> "${year} • ${genres.first()}"
        year > 0 -> year.toString()
        genres.isNotEmpty() -> genres.first()
        else -> null
    }
}

private fun formatShelfEpisodeTag(season: Int?, episode: Int?): String? {
    if (season == null && episode == null) return null
    val s = season?.let { "S${it}" } ?: ""
    val e = episode?.let { "E${it}" } ?: ""
    return "$s $e".trim().ifBlank { null }
}
