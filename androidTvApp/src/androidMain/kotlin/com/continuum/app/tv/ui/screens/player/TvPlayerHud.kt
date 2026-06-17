package com.continuum.app.tv.ui.screens.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.continuum.app.common.player.SleepTimerState
import com.continuum.app.model.catalog.VersionChapter
import com.continuum.app.tv.ui.theme.Spacing

/**
 * Right-side player panel. Hosts capability-aware sections — Info, Video,
 * Audio, plus Stats/Chapters when backing data exists — in a
 * side-panel layout that keeps the main transport dock visible and gives
 * secondary controls a stable home.
 *
 * Data-only tabs are hidden when empty so the HUD does not expose dead ends:
 * Stats appears after analytics has renderable fields, and Chapters appears
 * when the active file version includes server-extracted chapters. Video and
 * Audio stay visible because they expose playback controls even when there is
 * only one selected track.
 *
 * Focus model:
 *  - The tab pill bar is a horizontal `focusGroup`. Moving focus L/R between
 *    pills swaps the pane immediately (focus-driven selection — no Select
 *    needed). Up arrow inside the panel returns focus to the active tab.
 *  - The panel content is a vertical `focusGroup`. Chapters still use
 *    focus-to-seek; track pickers commit on Select so focus traversal is safe.
 */
@Composable
fun TvPlayerHud(
    title: String,
    positionSec: Double,
    durationSec: Double,
    audioTracks: List<PlayerTrackEntry>,
    videoTracks: List<PlayerTrackEntry>,
    stats: PlayerStatsSnapshot,
    videoFillMode: VideoFillMode,
    onSelectAudio: (Int) -> Unit,
    onSelectVideo: (Int) -> Unit,
    onVideoFillModeChanged: (VideoFillMode) -> Unit,
    playbackSpeed: Double,
    onPlaybackSpeedChanged: (Double) -> Unit,
    sleepTimerState: SleepTimerState,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    audioDelayMs: Int,
    onAudioDelayChanged: (Int) -> Unit,
    hdrEnabled: Boolean,
    onHdrEnabledChanged: (Boolean) -> Unit,
    chapters: List<VersionChapter>,
    onSelectChapter: (Int) -> Unit,
    onDismiss: () -> Unit,
    initialTab: HudTab = HudTab.Info,
    modifier: Modifier = Modifier,
) {
    val tabs = visibleHudTabs(stats = stats, chapters = chapters)
    var selectedTab by remember {
        mutableStateOf(initialTab.takeIf { it in tabs } ?: tabs.first())
    }

    val tabFocusRequesters = remember(tabs) { tabs.associateWith { FocusRequester() } }

    LaunchedEffect(initialTab, tabs) {
        selectedTab = initialTab.takeIf { it in tabs } ?: tabs.first()
    }

    // Seed initial focus on the active tab pill.
    LaunchedEffect(selectedTab, tabs) {
        tabFocusRequesters[selectedTab]?.let { runCatching { it.requestFocus() } }
    }

    PlayerSidePanel(
        modifier = modifier
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyUp &&
                    (ev.key == Key.Back || ev.key == Key.Escape)
                ) {
                    onDismiss(); true
                } else false
            },
    ) {
        Column(
            modifier = Modifier
                .width(168.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 22.dp),
        ) {
            when (selectedTab) {
                HudTab.Info -> HudInfoPane(
                    title = title,
                    positionSec = positionSec,
                    durationSec = durationSec,
                )
                HudTab.Stats -> HudStatsPane(stats)
                HudTab.Video -> HudVideoPane(
                    hdrEnabled = hdrEnabled,
                    onHdrEnabledChanged = onHdrEnabledChanged,
                    fillMode = videoFillMode,
                    onFillModeChanged = onVideoFillModeChanged,
                    playbackSpeed = playbackSpeed,
                    onPlaybackSpeedChanged = onPlaybackSpeedChanged,
                    sleepTimerState = sleepTimerState,
                    onStartSleepTimer = onStartSleepTimer,
                    onCancelSleepTimer = onCancelSleepTimer,
                    videoTracks = videoTracks,
                    onSelectVideo = onSelectVideo,
                )
                HudTab.Audio -> HudAudioPane(
                    audioTracks = audioTracks,
                    onSelectAudio = onSelectAudio,
                    audioDelayMs = audioDelayMs,
                    onAudioDelayChanged = onAudioDelayChanged,
                )
                HudTab.Chapters -> HudChaptersPane(
                    chapters = chapters,
                    onSelectChapter = onSelectChapter,
                )
            }
        }
    }
}

@Composable
private fun PlayerSidePanel(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .width(560.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp),
            )
            .padding(horizontal = 24.dp, vertical = 34.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        content = content,
    )
}

enum class HudTab(val label: String) {
    Info("Info"),
    Stats("Stats"),
    Video("Video"),
    Audio("Audio"),
    Chapters("Chapters"),
}

internal fun visibleHudTabs(
    stats: PlayerStatsSnapshot,
    chapters: List<VersionChapter>,
): List<HudTab> = buildList {
    add(HudTab.Info)
    if (stats.hasHudRows()) add(HudTab.Stats)
    add(HudTab.Video)
    add(HudTab.Audio)
    if (chapters.isNotEmpty()) add(HudTab.Chapters)
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
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
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

/**
 * Stats pane — renders the live [PlayerStatsSnapshot] populated by
 * [PlaybackAnalyticsListener] events. Fields populate as events arrive
 * (format change, decoder init, bandwidth estimate); the pane shows only
 * non-null rows. Cumulative counters (dropped frames, audio underruns) are
 * only surfaced when non-zero — they'd otherwise add noise to a healthy
 * playback session.
 *
 * Falls back to the empty-state message when no fields have populated yet
 * (e.g. before the first format event arrives after Play).
 */
@Composable
private fun HudStatsPane(stats: PlayerStatsSnapshot, modifier: Modifier = Modifier) {
    val rows = stats.hudRows()

    if (rows.isEmpty()) {
        HudEmptyStatePane("Stats unavailable", modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Playback-speed presets — mirrors the phone player (PlayerSettingsSheet). */
private val PLAYBACK_SPEED_OPTIONS = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0)

/** 1.0 -> "1", 1.25 -> "1.25", 1.5 -> "1.5" (trim trailing zeros). */
private fun formatTvPlaybackSpeed(speed: Double): String {
    if (speed % 1.0 == 0.0) return speed.toInt().toString()
    return speed.toString().trimEnd('0').trimEnd('.')
}

/**
 * Video pane — playback speed + HDR toggle + Fill mode (Letterbox vs Zoom,
 * matching tvOS video-gravity). When a stream advertises multiple video tracks
 * they're appended below as a secondary picker.
 */
/** Sleep-timer presets (minutes) — mirrors the phone SleepTimerSheet. */
private val SLEEP_TIMER_PRESETS = listOf(15, 30, 45, 60, 90)

private fun formatSleepRemaining(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val m = s / 60
    val sec = s % 60
    return if (m > 0) "${m}m ${sec}s" else "${sec}s"
}

@Composable
private fun HudVideoPane(
    hdrEnabled: Boolean,
    onHdrEnabledChanged: (Boolean) -> Unit,
    fillMode: VideoFillMode,
    onFillModeChanged: (VideoFillMode) -> Unit,
    playbackSpeed: Double,
    onPlaybackSpeedChanged: (Double) -> Unit,
    sleepTimerState: SleepTimerState,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    videoTracks: List<PlayerTrackEntry>,
    onSelectVideo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Playback speed — mirrors the phone's Speed presets (0.5×–3×). Uses a
        // click-committed chip (NOT the focus-committed HudOptionChip) so
        // D-pad-traversing the 9 presets doesn't change speed on every chip.
        Text(
            text = "Speed",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                HudClickChip(
                    label = "${formatTvPlaybackSpeed(speed)}×",
                    selected = kotlin.math.abs(playbackSpeed - speed) < 0.01,
                    onClick = { onPlaybackSpeedChanged(speed) },
                )
            }
        }

        // Sleep timer — preset chips when idle; remaining time + Cancel when armed.
        Text(
            text = "Sleep timer",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        val activeSleep = sleepTimerState as? SleepTimerState.Active
        if (activeSleep != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sleeping in ${formatSleepRemaining(activeSleep.remainingSeconds)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                )
                HudClickChip(label = "Cancel", selected = false, onClick = onCancelSleepTimer)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SLEEP_TIMER_PRESETS.forEach { minutes ->
                    HudClickChip(
                        label = if (minutes >= 60) "${minutes / 60}h${if (minutes % 60 != 0) " ${minutes % 60}m" else ""}" else "${minutes}m",
                        selected = false,
                        onClick = { onStartSleepTimer(minutes) },
                    )
                }
            }
        }

        Text(
            text = "HDR",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            HudOptionChip(
                label = "On",
                selected = hdrEnabled,
                onClick = { onHdrEnabledChanged(true) },
            )
            HudOptionChip(
                label = "Off",
                selected = !hdrEnabled,
                onClick = { onHdrEnabledChanged(false) },
            )
        }

        Text(
            text = "Fill mode",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            VideoFillMode.entries.forEach { mode ->
                HudOptionChip(
                    label = when (mode) {
                        VideoFillMode.Fit -> "Letterbox"
                        VideoFillMode.Zoom -> "Zoom (crop)"
                    },
                    selected = fillMode == mode,
                    onClick = { onFillModeChanged(mode) },
                )
            }
        }
        if (videoTracks.size > 1) {
            Text(
                text = "Video track",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.md),
            )
            HudPickerPane(
                options = videoTracks.map {
                    TrackOption(it.index, it.displayLabel, it.isSelected)
                },
                onSelect = onSelectVideo,
            )
        }
    }
}

/**
 * Focus-driven option pill used by the HUD Video pane for both HDR and Fill
 * mode rows. Mirrors `HudTabPill`'s color/scale idiom — focused = white fill
 * on dark text, selected (but unfocused) = subtle highlight, idle = ghost.
 * Moving focus to a chip commits its selection (no Select press needed).
 */
@Composable
private fun HudOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) onClick()
    }
    val bg = when {
        isFocused -> Color.White.copy(alpha = 0.94f)
        selected -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    val fg = when {
        isFocused -> Color.Black
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.72f)
    }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.96f,
        animationSpec = tween(120),
        label = "hudOptionChipScale",
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(bg)
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

/**
 * Like [HudOptionChip] but commits on explicit Select (click), NOT on focus —
 * use for multi-option rows (e.g. the 9 speed presets) where focus-driven
 * commit would change the value while the user is just traversing chips.
 */
@Composable
private fun HudClickChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bg = when {
        isFocused -> Color.White.copy(alpha = 0.94f)
        selected -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    val fg = when {
        isFocused -> Color.Black
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.72f)
    }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.96f,
        animationSpec = tween(120),
        label = "hudClickChipScale",
    )
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * Audio pane — combines the existing track picker (when >1 audio track) with
 * an audio-delay stepper row. The stepper is always rendered: even single-
 * track titles benefit from sync adjustment when the upstream has A/V drift.
 *
 * Values bind to per-profile [com.continuum.app.common.settings.PlayerSettingsStore.audioSyncMsFlow]
 * via the ViewModel. Writes flow through [setAudioSyncMs] which is already
 * ±500ms-coerced. The active [com.continuum.app.common.player.audio.DelayAudioProcessor]
 * picks up the new value through the service binding (E T3).
 */
@Composable
private fun HudAudioPane(
    audioTracks: List<PlayerTrackEntry>,
    onSelectAudio: (Int) -> Unit,
    audioDelayMs: Int,
    onAudioDelayChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        if (audioTracks.size > 1) {
            Text(
                text = "Audio track",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HudPickerPane(
                options = audioTracks.map {
                    TrackOption(it.index, it.displayLabel, it.isSelected)
                },
                onSelect = onSelectAudio,
            )
        }

        // Audio delay stepper — always shown; works regardless of track count.
        Text(
            text = "Audio delay: ${audioDelayMs} ms",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        DelayStepperRow(
            valueMs = audioDelayMs,
            onChange = onAudioDelayChanged,
        )
    }
}

/**
 * Full-width action row for HUD panes — [HudPickerRow]'s visual idiom but a
 * true click target like [DelayStepperButton]: an explicit Select press is
 * required (focus-driven commit would fire dialogs during plain traversal).
 */
@Composable
private fun HudActionRow(
    label: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = if (isFocused) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.06f)
    val fg = if (isFocused) Color.Black else Color.White.copy(alpha = 0.86f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
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

/**
 * D-pad-friendly stepper row: −50 / −10 / Reset / +10 / +50 ms. Each button
 * clamps to ±500ms (matches the AudioSyncMs / SubtitleSyncMs coercion).
 * Explicit steppers over a continuous slider per Infuse convention — remote
 * control + fixed-granularity bumps is more usable than precise drag on a
 * TV remote. Shared by audio + subtitles so both delays use the same row.
 */
@Composable
private fun DelayStepperRow(
    valueMs: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        // No local clamp — PlayerSettingsStore.setAudioSyncMs / setSubtitleSyncMs
        // clamp to iOS-parity ranges (±5000 audio, ±10000 subtitle). The
        // displayed value reflects the stored (clamped) value via the flow, so
        // presses at the boundary become no-ops without per-row range plumbing.
        DelayStepperButton(label = "−50", onClick = { onChange(valueMs - 50) })
        DelayStepperButton(label = "−10", onClick = { onChange(valueMs - 10) })
        DelayStepperButton(label = "Reset", onClick = { onChange(0) })
        DelayStepperButton(label = "+10", onClick = { onChange(valueMs + 10) })
        DelayStepperButton(label = "+50", onClick = { onChange(valueMs + 50) })
    }
}

/**
 * Pill button styled to match [VideoFillModeChip] / [HudTabPill] — white fill
 * on focus, ghost when idle, animated scale. Unlike [VideoFillModeChip] this
 * is a true click target (not focus-to-commit) — a stepper needs an explicit
 * Select press per click so users can land on a button without changing state.
 */
@Composable
private fun DelayStepperButton(
    label: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = if (isFocused) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.06f)
    val fg = if (isFocused) Color.Black else Color.White.copy(alpha = 0.72f)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.96f,
        animationSpec = tween(120),
        label = "delayStepperScale",
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
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

private fun PlayerStatsSnapshot.hasHudRows(): Boolean = hudRows().isNotEmpty()

private fun PlayerStatsSnapshot.hudRows(): List<Pair<String, String>> = buildList {
    backendDisplayName?.let { add("Backend" to it) }
    backendRoute?.let { add("Route" to it) }
    subtitleRendering?.let { add("Subtitles" to it) }
    hardContainers?.let { add("Hard containers" to it) }
    videoCodec?.let { add("Video codec" to it) }
    resolution?.let { add("Resolution" to it) }
    frameRate?.let { add("Frame rate" to "%.3f fps".format(it)) }
    hdrMode?.let { add("HDR mode" to it) }
    videoDecoderName?.let { add("Video decoder" to it) }
    audioCodec?.let { add("Audio codec" to it) }
    audioDecoderName?.let { add("Audio decoder" to it) }
    bitrateBps?.let { add("Bitrate" to formatBitrate(it)) }
    if (droppedFrames > 0) add("Dropped frames" to droppedFrames.toString())
    if (audioUnderruns > 0) add("Audio underruns" to audioUnderruns.toString())
}

private fun formatBitrate(bps: Long): String = when {
    bps >= 1_000_000 -> "%.1f Mbps".format(bps / 1_000_000.0)
    bps >= 1_000 -> "%.0f Kbps".format(bps / 1_000.0)
    else -> "$bps bps"
}

/**
 * Empty pane used only for tabs that remain useful when their optional picker
 * data is empty, or for transient Stats rendering if analytics data disappears
 * between tab selection and composition.
 */
@Composable
private fun HudEmptyStatePane(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Chapters pane — renders [VersionChapter]s from the active FileVersion as a
 * focus-driven picker. Selecting a chapter seeks the player to its start time
 * (the screen owns the MediaController and performs the seek). The tab itself
 * is hidden when the file has no chapters.
 *
 * Server-supplied via `FileVersion.chapters` (extracted from MP4/MKV at
 * ingest by FFprobe) — mirrors Apple's HUD chapter list. Thumbnails
 * (`thumbnailUrl` + `thumbnailThumbhash`) are intentionally not rendered in
 * the first cut; text rows are a complete shipping pane.
 */
@Composable
private fun HudChaptersPane(
    chapters: List<VersionChapter>,
    onSelectChapter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chapters.isEmpty()) {
        HudEmptyStatePane("No chapters in this title", modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(chapters, key = { _, c -> c.index }) { idx, ch ->
            HudChapterRow(
                chapter = ch,
                onFocused = { onSelectChapter(idx) },
            )
        }
    }
}

@Composable
private fun HudChapterRow(
    chapter: VersionChapter,
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = formatTime(chapter.startSeconds),
            color = fg.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = chapter.title.ifBlank { "Chapter ${chapter.index + 1}" },
            color = fg,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
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
                onSelect = {
                    onSelect(opt.id)
                },
            )
        }
    }
}

@Composable
private fun HudPickerRow(
    option: TrackOption,
    onSelect: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent
    val fg = if (isFocused) Color.White else Color.White.copy(alpha = 0.86f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(interactionSource = interactionSource, indication = null) { onSelect() }
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
