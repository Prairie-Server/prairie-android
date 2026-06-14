package com.continuum.app.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.tv.ui.theme.DarkBackground
import com.continuum.app.tv.ui.theme.DarkOnPrimary
import com.continuum.app.tv.ui.theme.FocusedContainer
import com.continuum.app.tv.ui.theme.FocusedContent
import com.continuum.app.tv.ui.theme.SelectedContainer
import com.continuum.app.tv.ui.theme.navRailLabel

/** Visual variant of a hero/action pill. */
enum class TvPillVariant {
    /** Filled white primary action (Play, Resume, Sign In). */
    Filled,

    /** Dark filled pill with a subtle border — secondary (More Info, Trailer). */
    Hollow,

    /** Quiet tertiary pill used for less dominant affordances. */
    Ghost,
}

/**
 * Signature action pill used on heroes, detail screens, auth screens, and
 * any primary/secondary CTA in the TV UI. Focused state always inverts to a
 * white tvOS-style capsule so navigation reads consistently across variants.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHeroActionPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    variant: TvPillVariant = TvPillVariant.Filled,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onDirectionDown: (() -> Boolean)? = null,
    heightOverride: androidx.compose.ui.unit.Dp? = null,
    horizontalPaddingOverride: androidx.compose.ui.unit.Dp? = null,
    labelStyle: TextStyle = navRailLabel,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val (restBg, restFg, restBorder) = when (variant) {
        TvPillVariant.Filled -> Triple(
            Color.White.copy(alpha = 0.92f),
            DarkOnPrimary,
            Color.White.copy(alpha = 0.42f),
        )
        TvPillVariant.Hollow -> Triple(
            DarkBackground.copy(alpha = 0.42f),
            Color.White,
            Color.White.copy(alpha = 0.24f),
        )
        TvPillVariant.Ghost -> Triple(
            SelectedContainer,
            Color.White.copy(alpha = 0.85f),
            Color.Transparent,
        )
    }

    val pillShape = RoundedCornerShape(100.dp)
    val controlHeight = heightOverride ?: when (variant) {
        TvPillVariant.Filled -> 58.dp
        TvPillVariant.Hollow -> 44.dp
        TvPillVariant.Ghost -> 40.dp
    }
    val horizontalPadding = horizontalPaddingOverride ?: when (variant) {
        TvPillVariant.Filled -> 32.dp
        TvPillVariant.Hollow -> 28.dp
        TvPillVariant.Ghost -> 16.dp
    }
    val focusedBorderColor = DarkBackground.copy(alpha = 0.86f)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = pillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = restBg,
            contentColor = restFg,
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
            pressedContainerColor = FocusedContainer,
            pressedContentColor = FocusedContent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    width = if (restBorder != Color.Transparent) 1.2.dp else 0.dp,
                    color = restBorder,
                ),
                shape = pillShape,
            ),
            focusedBorder = Border(
                border = BorderStroke(2.2.dp, focusedBorderColor),
                shape = pillShape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.White.copy(alpha = 0.20f),
                elevation = 20.dp,
            ),
        ),
        modifier = modifier
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 2.2.dp,
                        color = Color.White.copy(alpha = 0.98f),
                        shape = pillShape,
                    )
                } else {
                    Modifier
                },
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (upFocusRequester != null || downFocusRequester != null) {
                    Modifier.focusProperties {
                        upFocusRequester?.let { up = it }
                        downFocusRequester?.let { down = it }
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (onDirectionDown != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            onDirectionDown()
                        } else {
                            false
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .height(controlHeight)
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (variant == TvPillVariant.Filled) 20.dp else 18.dp),
                )
                if (variant != TvPillVariant.Ghost || label.isNotBlank()) {
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    style = labelStyle,
                    color = if (isFocused) FocusedContent else restFg,
                    maxLines = 1,
                )
            }
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
