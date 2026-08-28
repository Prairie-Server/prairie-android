package org.prairieserver.prairie.tv.ui.components

import androidx.compose.ui.graphics.Color
import org.prairieserver.prairie.tv.ui.theme.FocusedContainer
import org.prairieserver.prairie.tv.ui.theme.FocusedContent
import org.prairieserver.prairie.tv.ui.theme.PrairieOnSurface

internal data class TvSelectorRowVisualState(
    val container: Color,
    val content: Color,
    val border: Color,
)

internal fun tvSelectorRowVisualState(
    focused: Boolean,
    selected: Boolean,
    enabled: Boolean,
): TvSelectorRowVisualState = when {
    // Rows sit on the Skyline glass panel (tvSkylinePanelChrome), so idle and
    // disabled rows are transparent like the cascade's; only focus (inverted
    // capsule) and the current selection (soft tint) paint a fill.
    !enabled -> TvSelectorRowVisualState(
        Color.Transparent,
        PrairieOnSurface.copy(alpha = 0.38f),
        Color.Transparent,
    )
    focused -> TvSelectorRowVisualState(
        FocusedContainer,
        FocusedContent,
        FocusedContent.copy(alpha = 0.22f),
    )
    selected -> TvSelectorRowVisualState(
        PrairieOnSurface.copy(alpha = 0.14f),
        PrairieOnSurface,
        PrairieOnSurface.copy(alpha = 0.28f),
    )
    else -> TvSelectorRowVisualState(Color.Transparent, PrairieOnSurface, Color.Transparent)
}
