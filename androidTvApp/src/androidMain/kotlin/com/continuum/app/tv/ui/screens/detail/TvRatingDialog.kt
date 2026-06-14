package com.continuum.app.tv.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.tv.ui.theme.DarkBackground
import com.continuum.app.tv.ui.theme.FocusedContainer
import com.continuum.app.tv.ui.theme.FocusedContent

private val TvRatingStarTint = Color(0xFFFFC107)

/**
 * D-pad rating dialog: five stars navigable left/right, OK sets the
 * rating, plus a "Remove rating" row (D-pad down) when a rating exists.
 * Panel + focus idiom mirrors TvOptionDialog.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvRatingDialog(
    currentRating: Int?,
    onSetRating: (Int) -> Unit,
    onClearRating: () -> Unit,
    onDismiss: () -> Unit,
) {
    val initialStarFocus = remember { FocusRequester() }
    var preview by remember { mutableIntStateOf(currentRating ?: 0) }
    val initialStar = currentRating?.coerceIn(1, 5) ?: 1

    LaunchedEffect(Unit) {
        runCatching { initialStarFocus.requestFocus() }
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 72.dp, top = 120.dp, end = 72.dp, bottom = 84.dp),
            contentAlignment = Alignment.Center,
        ) {
            val panelShape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .background(
                        color = DarkBackground.copy(alpha = 0.68f),
                        shape = panelShape,
                    )
                    .border(1.2.dp, Color.White.copy(alpha = 0.20f), panelShape)
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "RATE THIS TITLE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 16.sp,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White.copy(alpha = 0.58f),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    (1..5).forEach { star ->
                        TvRatingStar(
                            filled = star <= preview,
                            contentDescription = "Rate $star of 5",
                            onFocused = { preview = star },
                            onClick = { onSetRating(star) },
                            modifier = if (star == initialStar) {
                                Modifier.focusRequester(initialStarFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                }

                if (currentRating != null) {
                    TvRatingClearRow(onClick = onClearRating)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvRatingStar(
    filled: Boolean,
    contentDescription: String,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = CircleShape

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.34f),
            contentColor = if (filled) TvRatingStarTint else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = if (filled) TvRatingStarTint else Color.Black,
            pressedContainerColor = Color.White,
            pressedContentColor = if (filled) TvRatingStarTint else Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.32f)),
                shape = shape,
            ),
            focusedBorder = Border(
                border = BorderStroke(2.0.dp, Color.Black.copy(alpha = 0.82f)),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.White.copy(alpha = 0.16f),
                elevation = 14.dp,
            ),
        ),
        modifier = modifier
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.98f), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = contentDescription,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvRatingClearRow(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.04f),
            contentColor = Color.White,
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
            pressedContainerColor = FocusedContainer,
            pressedContentColor = FocusedContent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.025f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, DarkBackground.copy(alpha = 0.82f)),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.White.copy(alpha = 0.18f),
                elevation = 16.dp,
            ),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.98f), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Remove rating",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isFocused) FocusedContent else Color.White,
            )
        }
    }
}
