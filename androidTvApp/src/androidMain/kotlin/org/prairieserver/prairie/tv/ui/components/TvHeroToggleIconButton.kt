package org.prairieserver.prairie.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import org.prairieserver.prairie.tv.ui.theme.PrairieBlueGlow
import org.prairieserver.prairie.tv.ui.theme.DarkBackground
import org.prairieserver.prairie.tv.ui.theme.FocusedContainer
import org.prairieserver.prairie.tv.ui.theme.FocusedContent

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvHeroToggleIconButton(
    selected: Boolean,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    downFocusRequester: FocusRequester? = null,
    onDirectionDown: (() -> Boolean)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(100.dp)
    val restBackground = if (selected) {
        selectedContainerColor
    } else {
        DarkBackground.copy(alpha = 0.48f)
    }
    val restContent = if (selected) {
        selectedContentColor
    } else {
        Color.White.copy(alpha = 0.86f)
    }
    val restBorder = if (selected) {
        selectedContentColor.copy(alpha = 0.3f)
    } else {
        Color.White.copy(alpha = 0.18f)
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = restBackground,
            contentColor = restContent,
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
            pressedContainerColor = FocusedContainer,
            pressedContentColor = FocusedContent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, restBorder),
                shape = shape,
            ),
            focusedBorder = Border(
                border = BorderStroke(3.dp, DarkBackground.copy(alpha = 0.78f)),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = PrairieBlueGlow,
                elevation = 24.dp,
            ),
        ),
        modifier = modifier
            .then(
                if (downFocusRequester != null) {
                    Modifier.focusProperties { down = downFocusRequester }
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
            )
            .width(56.dp)
            .height(54.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isFocused) FocusedContent else restContent,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
