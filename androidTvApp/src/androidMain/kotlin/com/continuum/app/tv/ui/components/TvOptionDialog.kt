package com.continuum.app.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

data class TvDialogOption(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvOptionDialog(
    title: String,
    options: List<TvDialogOption>,
    onDismiss: () -> Unit,
) {
    val firstRowFocus = remember { FocusRequester() }
    val focusedKey = options.firstOrNull { it.selected && it.enabled }?.key
        ?: options.firstOrNull { it.enabled }?.key

    LaunchedEffect(title, focusedKey) {
        runCatching { firstRowFocus.requestFocus() }
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
                    .width(340.dp)
                    .background(
                        color = DarkBackground.copy(alpha = 0.68f),
                        shape = panelShape,
                    )
                    .border(1.2.dp, Color.White.copy(alpha = 0.20f), panelShape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 16.sp,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White.copy(alpha = 0.58f),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(options, key = { it.key }) { option ->
                        TvOptionDialogRow(
                            title = option.title,
                            subtitle = option.subtitle,
                            selected = option.selected,
                            enabled = option.enabled,
                            onClick = option.onClick,
                            modifier = if (option.key == focusedKey) {
                                Modifier.focusRequester(firstRowFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvOptionDialogRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    val restContent = if (enabled) Color.White else Color.White.copy(alpha = 0.42f)

    Surface(
        onClick = {
            if (enabled) onClick()
        },
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.04f),
            contentColor = restContent,
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
            pressedContainerColor = FocusedContainer,
            pressedContentColor = FocusedContent,
            disabledContainerColor = Color.White.copy(alpha = 0.03f),
            disabledContentColor = Color.White.copy(alpha = 0.38f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.025f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    width = if (selected) 1.dp else 0.dp,
                    color = Color.White.copy(alpha = if (selected) 0.22f else 0f),
                ),
                shape = shape,
            ),
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
        modifier = modifier
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.72f)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (isFocused) FocusedContent else restContent,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                        ),
                        color = if (isFocused) {
                            FocusedContent.copy(alpha = 0.70f)
                        } else {
                            Color.White.copy(alpha = if (enabled) 0.56f else 0.32f)
                        },
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (isFocused) FocusedContent else Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Spacer(modifier = Modifier.size(22.dp))
            }
        }
    }
}
