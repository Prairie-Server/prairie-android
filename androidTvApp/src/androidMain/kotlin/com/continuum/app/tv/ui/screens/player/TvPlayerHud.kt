package com.continuum.app.tv.ui.screens.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Floating top-center HUD that mirrors `TVPlayerInfoHUD` on tvOS. Hosts up to
 * six pill tabs along the top — Info, Stats, Video, Audio, Subtitles,
 * Chapters — and a `regularMaterial`-style glass panel below containing the
 * focused tab's content.
 *
 * Tabs only render if their backing data is non-empty, so a movie with no
 * chapters or no subtitle tracks won't show those tabs at all.
 *
 * Focus model:
 *  - The tab pill bar is a horizontal `focusGroup`. Moving focus L/R between
 *    pills swaps the pane immediately (focus-driven selection — no Select
 *    needed). Up arrow inside the panel returns focus to the active tab.
 *  - The panel content is a vertical `focusGroup`. For pickers (audio,
 *    subtitle, chapters), moving focus to a row auto-selects that value —
 *    matches tvOS's "move = pick" pattern.
 */
@Composable
fun TvPlayerHud(
    title: String,
    positionSec: Double,
    durationSec: Double,
    audioTracks: List<PlayerTrackEntry>,
    subtitleTracks: List<PlayerTrackEntry>,
    videoTracks: List<PlayerTrackEntry>,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onSelectVideo: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Compute which tabs to render. tvOS hides tabs whose backing data is
    // empty — we mirror that so the bar contracts gracefully on bare-bones
    // titles (no audio choices, no subtitles, no chapters).
    val tabs = remember(audioTracks, subtitleTracks, videoTracks) {
        buildList {
            add(HudTab.Info)
            // Stats / Chapters: stubbed until MediaController exposes
            // network/codec/chapter metadata; gated per the "only visible
            // if content available" rule from spec §4.2.
            if (videoTracks.size > 1) add(HudTab.Video)
            if (audioTracks.size > 1) add(HudTab.Audio)
            if (subtitleTracks.isNotEmpty()) add(HudTab.Subtitles)
        }
    }
    var selectedTab by remember(tabs) { mutableStateOf(tabs.first()) }

    val tabFocusRequesters = remember(tabs) { tabs.associateWith { FocusRequester() } }

    // Seed initial focus on the active tab pill.
    LaunchedEffect(selectedTab) {
        tabFocusRequesters[selectedTab]?.let { runCatching { it.requestFocus() } }
    }

    Column(
        modifier = modifier
            .widthIn(max = 1100.dp)
            .padding(top = 48.dp)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyUp &&
                    (ev.key == Key.Back || ev.key == Key.Escape)
                ) {
                    onDismiss(); true
                } else false
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Tab pill bar
        Row(
            modifier = Modifier.height(52.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                HudTabPill(
                    label = tab.label,
                    isSelected = tab == selectedTab,
                    focusRequester = tabFocusRequesters[tab]
                        ?: remember(tab) { FocusRequester() },
                    onFocused = {
                        // Focus-driven selection — no Select press required.
                        selectedTab = tab
                    },
                )
            }
        }

        // Panel — `regularMaterial` glass on tvOS. Compose's blur API requires
        // API 31+ so we settle for a high-alpha surface tint that still reads
        // as a translucent panel against any backdrop.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 380.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(horizontal = 28.dp, vertical = 22.dp),
        ) {
            when (selectedTab) {
                HudTab.Info -> HudInfoPane(
                    title = title,
                    positionSec = positionSec,
                    durationSec = durationSec,
                )
                HudTab.Video -> HudPickerPane(
                    options = videoTracks.map { TrackOption(it.index, it.label, it.isSelected) },
                    onSelect = onSelectVideo,
                )
                HudTab.Audio -> HudPickerPane(
                    options = audioTracks.map { TrackOption(it.index, it.label, it.isSelected) },
                    onSelect = onSelectAudio,
                )
                HudTab.Subtitles -> HudPickerPane(
                    options = buildList {
                        // "Off" is the canonical first entry per tvOS spec.
                        add(TrackOption(-1, "Off", subtitleTracks.none { it.isSelected }))
                        addAll(subtitleTracks.map { TrackOption(it.index, it.label, it.isSelected) })
                    },
                    onSelect = onSelectSubtitle,
                )
                else -> Box(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private enum class HudTab(val label: String) {
    Info("Info"),
    Stats("Stats"),
    Video("Video"),
    Audio("Audio"),
    Subtitles("Subtitles"),
    Chapters("Chapters"),
}

@Composable
private fun HudTabPill(
    label: String,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    val bg = when {
        isFocused -> Color.White.copy(alpha = 0.94f)
        isSelected -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    val fg = when {
        isFocused -> Color.Black
        isSelected -> Color.White
        else -> Color.White.copy(alpha = 0.72f)
    }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.96f,
        animationSpec = tween(120),
        label = "hudTabScale",
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun HudInfoPane(
    title: String,
    positionSec: Double,
    durationSec: Double,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "${formatTime(positionSec)} / ${formatTime(durationSec)}",
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private data class TrackOption(val id: Int, val label: String, val selected: Boolean)

@Composable
private fun HudPickerPane(
    options: List<TrackOption>,
    onSelect: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(options, key = { it.id }) { opt ->
            HudPickerRow(
                option = opt,
                onFocused = {
                    // Focus-driven selection — moving focus to a row commits.
                    onSelect(opt.id)
                },
            )
        }
    }
}

@Composable
private fun HudPickerRow(
    option: TrackOption,
    onFocused: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) { if (isFocused) onFocused() }

    val bg = if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent
    val fg = if (isFocused) Color.White else Color.White.copy(alpha = 0.86f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                tint = Color.White,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

private fun formatTime(seconds: Double): String {
    if (seconds <= 0 || seconds.isNaN()) return "0:00"
    val total = seconds.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
