package org.prairieserver.prairie.tv.ui.screens.search

internal const val TV_SEARCH_QUERY_MAX_LENGTH = 200

/**
 * The search field's "keyboard open" flag, kept in step with the real IME.
 *
 * [keyboardOpen] is what makes the field editable and hands D-pad Down to the
 * text field instead of the screen. The app raises it on Select, but cannot
 * always lower it: Back pressed while the IME is up is consumed by the IME
 * itself, so the screen's BackHandler never runs. [imeSeen] records that the
 * IME actually appeared after the field opened, so the IME later going away
 * closes the field, while the frames between `show()` and the IME surfacing
 * do not.
 */
internal data class TvSearchKeyboardState(
    val keyboardOpen: Boolean = false,
    val imeSeen: Boolean = false,
)

internal fun TvSearchKeyboardState.onImeVisibilityChanged(imeVisible: Boolean): TvSearchKeyboardState =
    when {
        !keyboardOpen -> copy(imeSeen = false)
        imeVisible -> copy(imeSeen = true)
        imeSeen -> TvSearchKeyboardState()
        else -> this
    }
