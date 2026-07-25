package org.prairieserver.prairie.tv.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color

/**
 * Favorite toggle styled to sit cleanly in the hero action row alongside the
 * main CTA pills.
 */
@Composable
fun TvFavoriteButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    downFocusRequester: FocusRequester? = null,
    onDirectionDown: (() -> Boolean)? = null,
) {
    TvHeroToggleIconButton(
        selected = isFavorite,
        selectedContainerColor = Color(0xFF5A2028).copy(alpha = 0.88f),
        selectedContentColor = Color(0xFFFFA4AE),
        icon = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
        onClick = onToggle,
        modifier = modifier,
        downFocusRequester = downFocusRequester,
        onDirectionDown = onDirectionDown,
    )
}
