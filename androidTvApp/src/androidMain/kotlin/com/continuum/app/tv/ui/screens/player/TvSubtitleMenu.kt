package com.continuum.app.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.tv.ui.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun TvSubtitleMenu(
    subtitleTracks: List<PlayerTrackEntry>,
    subtitleDelayMs: Int,
    onSelectSubtitle: (Int) -> Unit,
    onSubtitleDelayChanged: (Int) -> Unit,
    onPaneShown: () -> Unit,
    onSearchSubtitles: (() -> Unit)?,
    onTranslateWithAi: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = buildList {
        add(SubtitleMenuOption(-1, "Off", subtitleTracks.none { it.isSelected }))
        addAll(
            subtitleTracks.map {
                SubtitleMenuOption(it.index, it.displayLabel, it.isSelected)
            },
        )
    }
    val optionIds = options.map { it.id }
    val optionFocusRequesters = remember(optionIds) {
        optionIds.associateWith { FocusRequester() }
    }
    val initiallyFocusedId = subtitleTracks.firstOrNull { it.isSelected }?.index ?: -1

    LaunchedEffect(Unit) { onPaneShown() }
    LaunchedEffect(initiallyFocusedId) {
        delay(50)
        runCatching { optionFocusRequesters[initiallyFocusedId]?.requestFocus() }
    }

    Box(
        modifier = modifier
            .width(430.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp),
            )
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyUp &&
                    (ev.key == Key.Back || ev.key == Key.Escape)
                ) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = "Subtitles",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(options, key = { _, option -> option.id }) { index, opt ->
                    SubtitleMenuRow(
                        option = opt,
                        focusRequester = optionFocusRequesters[opt.id],
                        upFocusRequester = options.getOrNull(index - 1)
                            ?.let { optionFocusRequesters[it.id] },
                        downFocusRequester = options.getOrNull(index + 1)
                            ?.let { optionFocusRequesters[it.id] },
                        onSelect = { onSelectSubtitle(opt.id) },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onSearchSubtitles != null) {
                    SubtitleActionRow(label = "Search subtitles", onClick = onSearchSubtitles)
                }
                if (onTranslateWithAi != null) {
                    SubtitleActionRow(label = "Translate with AI", onClick = onTranslateWithAi)
                }
            }

            Text(
                text = "Delay: ${subtitleDelayMs} ms",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SubtitleDelayStepperRow(
                valueMs = subtitleDelayMs,
                onChange = onSubtitleDelayChanged,
            )
        }
    }
}

private data class SubtitleMenuOption(
    val id: Int,
    val label: String,
    val selected: Boolean,
)

@Composable
private fun SubtitleMenuRow(
    option: SubtitleMenuOption,
    focusRequester: FocusRequester?,
    upFocusRequester: FocusRequester?,
    downFocusRequester: FocusRequester?,
    onSelect: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val fg = if (isFocused) Color.White else Color.White.copy(alpha = 0.86f)
    val shape = RoundedCornerShape(12.dp)

    Surface(
        onClick = onSelect,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White.copy(alpha = 0.86f),
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.18f),
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                if (upFocusRequester != null) {
                    up = upFocusRequester
                }
                if (downFocusRequester != null) {
                    down = downFocusRequester
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = option.label.ifBlank { "Track ${option.id + 1}" },
                color = fg,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (option.selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SubtitleActionRow(
    label: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val fg = if (isFocused) Color.Black else Color.White.copy(alpha = 0.86f)
    val shape = RoundedCornerShape(12.dp)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = Color.White.copy(alpha = 0.86f),
            focusedContainerColor = Color.White.copy(alpha = 0.94f),
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.94f),
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = fg,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SubtitleDelayStepperRow(
    valueMs: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubtitleStepperButton(label = "-50", onClick = { onChange(valueMs - 50) })
        SubtitleStepperButton(label = "-10", onClick = { onChange(valueMs - 10) })
        SubtitleStepperButton(label = "Reset", onClick = { onChange(0) })
        SubtitleStepperButton(label = "+10", onClick = { onChange(valueMs + 10) })
        SubtitleStepperButton(label = "+50", onClick = { onChange(valueMs + 50) })
    }
}

@Composable
private fun SubtitleStepperButton(
    label: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val fg = if (isFocused) Color.Black else Color.White.copy(alpha = 0.72f)
    val shape = RoundedCornerShape(50)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = Color.White.copy(alpha = 0.72f),
            focusedContainerColor = Color.White.copy(alpha = 0.94f),
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.94f),
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = Modifier
            .clip(shape),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = fg,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
