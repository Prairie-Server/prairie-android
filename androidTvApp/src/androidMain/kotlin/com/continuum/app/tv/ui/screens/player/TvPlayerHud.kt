package com.continuum.app.tv.ui.screens.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.common.player.SleepTimerState
import com.continuum.app.model.catalog.VersionChapter
import com.continuum.app.model.settings.SubtitleAppearance
import com.continuum.app.model.settings.SubtitleBackgroundStylePreset
import com.continuum.app.model.settings.SubtitleFontSizePreset
import com.continuum.app.model.settings.SubtitlePositionPreset
import com.continuum.app.tv.ui.theme.Spacing

/**
 * Floating top-center player HUD mirroring `iosApp/.../tvOS/TVPlayerInfoHUD.swift`.
 *
 * A frosted/opaque dark card (~1100dp wide, ~380dp tall, corner ~28dp) drops on
 * top of the video with NO full-screen dim so playback stays visible. A
 * horizontal pill TAB BAR sits at the top of the card; the selected pane renders
 * below it.
 *
 * Tab set: Info · Stats · Video · Audio · Subtitles · Chapters. Info / Video are
 * always present; Stats / Audio / Subtitles / Chapters are hidden when their
 * backing data is empty (Infuse hides rather than disables, keeping the bar
 * tidy). The Subtitles tab folds in what used to be the separate subtitle drawer
 * + style dialog: track list, delay stepper, appearance chips, plus the
 * Android-only Search / AI-Translate rows.
 *
 * Focus model:
 *  - The tab pill bar is a horizontal row. Moving focus L/R between pills swaps
 *    the pane immediately (focus-driven selection — no Select needed). Up arrow
 *    inside the panel returns focus to the active tab.
 *  - The panel content is a vertical group. Chapters/tracks use focus-to-seek or
 *    click-commit as appropriate so focus traversal is safe.
 */
@Composable
fun TvPlayerHud(
    title: String,
    positionSec: Double,
    durationSec: Double,
    seasonNumber: Int?,
    episodeNumber: Int?,
    audioTracks: List<PlayerTrackEntry>,
    videoTracks: List<PlayerTrackEntry>,
    subtitleTracks: List<PlayerTrackEntry>,
    stats: PlayerStatsSnapshot,
    videoFillMode: VideoFillMode,
    onSelectAudio: (Int) -> Unit,
    onSelectVideo: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onVideoFillModeChanged: (VideoFillMode) -> Unit,
    playbackSpeed: Double,
    onPlaybackSpeedChanged: (Double) -> Unit,
    sleepTimerState: SleepTimerState,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    autoSkipIntro: Boolean,
    onAutoSkipIntroChanged: (Boolean) -> Unit,
    autoPlayNext: Boolean,
    onAutoPlayNextChanged: (Boolean) -> Unit,
    audioDelayMs: Int,
    onAudioDelayChanged: (Int) -> Unit,
    subtitleDelayMs: Int,
    onSubtitleDelayChanged: (Int) -> Unit,
    subtitleAppearance: SubtitleAppearance,
    onSubtitleAppearanceChanged: (SubtitleAppearance) -> Unit,
    onSubtitlesPaneShown: () -> Unit,
    onSearchSubtitles: (() -> Unit)?,
    onTranslateWithAi: (() -> Unit)?,
    hdrEnabled: Boolean,
    onHdrEnabledChanged: (Boolean) -> Unit,
    chapters: List<VersionChapter>,
    onSelectChapter: (Int) -> Unit,
    onDismiss: () -> Unit,
    initialTab: HudTab = HudTab.Info,
    modifier: Modifier = Modifier,
) {
    val tabs = visibleHudTabs(
        stats = stats,
        audioTracks = audioTracks,
        subtitleTracks = subtitleTracks,
        chapters = chapters,
    )
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

    // Top-center card. No full-screen scrim — the video stays visible behind it.
    Box(
        modifier = modifier
            .widthIn(max = 1100.dp)
            .fillMaxWidth()
            .height(380.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(28.dp),
            )
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyUp &&
                    (ev.key == Key.Back || ev.key == Key.Escape)
                ) {
                    onDismiss(); true
                } else false
            }
            .padding(horizontal = 28.dp, vertical = 22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Horizontal pill tab bar at the top.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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

            // Content pane below the tab bar.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (selectedTab) {
                    HudTab.Info -> HudInfoPane(
                        title = title,
                        positionSec = positionSec,
                        durationSec = durationSec,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        stats = stats,
                        subtitleTracks = subtitleTracks,
                        chapters = chapters,
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
                        autoSkipIntro = autoSkipIntro,
                        onAutoSkipIntroChanged = onAutoSkipIntroChanged,
                        autoPlayNext = autoPlayNext,
                        onAutoPlayNextChanged = onAutoPlayNextChanged,
                        videoTracks = videoTracks,
                        onSelectVideo = onSelectVideo,
                    )
                    HudTab.Audio -> HudAudioPane(
                        audioTracks = audioTracks,
                        onSelectAudio = onSelectAudio,
                        audioDelayMs = audioDelayMs,
                        onAudioDelayChanged = onAudioDelayChanged,
                    )
                    HudTab.Subtitles -> HudSubtitlesPane(
                        subtitleTracks = subtitleTracks,
                        onSelectSubtitle = onSelectSubtitle,
                        subtitleDelayMs = subtitleDelayMs,
                        onSubtitleDelayChanged = onSubtitleDelayChanged,
                        appearance = subtitleAppearance,
                        onAppearanceChanged = onSubtitleAppearanceChanged,
                        onPaneShown = onSubtitlesPaneShown,
                        onSearchSubtitles = onSearchSubtitles,
                        onTranslateWithAi = onTranslateWithAi,
                    )
                    HudTab.Chapters -> HudChaptersPane(
                        chapters = chapters,
                        onSelectChapter = onSelectChapter,
                    )
                }
            }
        }
    }
}

enum class HudTab(val label: String) {
    Info("Info"),
    Stats("Stats"),
    Video("Video"),
    Audio("Audio"),
    Subtitles("Subtitles"),
    Chapters("Chapters"),
}

/**
 * Tabs shown for the current session. Info + Video are always present; Stats,
 * Audio, Subtitles, Chapters appear only when their backing data exists, in a
 * stable order matching tvOS (Info · Stats · Video · Audio · Subtitles ·
 * Chapters).
 */
internal fun visibleHudTabs(
    stats: PlayerStatsSnapshot,
    audioTracks: List<PlayerTrackEntry>,
    subtitleTracks: List<PlayerTrackEntry>,
    chapters: List<VersionChapter>,
): List<HudTab> = buildList {
    add(HudTab.Info)
    if (stats.hasHudRows()) add(HudTab.Stats)
    add(HudTab.Video)
    if (audioTracks.isNotEmpty()) add(HudTab.Audio)
    // Subtitles is always available (unlike tvOS, which hides it when empty):
    // the pane hosts Android-only Search-subtitles / AI-Translate / style
    // controls that must stay reachable even when a title carries no tracks —
    // there is no longer a separate subtitles button to reach them.
    add(HudTab.Subtitles)
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
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 24.dp),
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
 * Info pane — two-column Title + Stream layout mirroring tvOS. The Android
 * player state exposes far less metadata than the Apple PlayerViewModel, so we
 * omit rows we don't have (series eyebrow, year, overview, audio layout) rather
 * than invent data: Title = title + episode tag + runtime; Stream = HDR/route/
 * codec badges, current subtitle, current chapter.
 */
@Composable
private fun HudInfoPane(
    title: String,
    positionSec: Double,
    durationSec: Double,
    seasonNumber: Int?,
    episodeNumber: Int?,
    stats: PlayerStatsSnapshot,
    subtitleTracks: List<PlayerTrackEntry>,
    chapters: List<VersionChapter>,
    modifier: Modifier = Modifier,
) {
    val episodeTag = if (seasonNumber != null && episodeNumber != null) {
        "S$seasonNumber · E$episodeNumber"
    } else {
        null
    }
    val metaBits = buildList {
        if (durationSec > 0) add(formatTime(durationSec))
    }
    val streamRows = buildList<Pair<String, String>> {
        stats.backendRoute?.let { add("Route" to it) }
        stats.videoCodec?.let { add("Video" to it.uppercase()) }
        stats.audioCodec?.let { add("Audio" to it.uppercase()) }
        val sub = subtitleTracks.firstOrNull { it.isSelected }
        add("Subtitles" to (sub?.displayLabel?.ifBlank { "On" } ?: "Off"))
        currentChapterTitle(chapters, positionSec)?.let { add("Chapter" to it) }
    }
    val badges = buildList {
        stats.hdrMode?.takeIf { it.isNotBlank() && !it.equals("SDR", ignoreCase = true) }?.let { add(it) }
        stats.resolution?.let { add(it) }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        // Title column.
        PaneColumn("Title", modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { "Now Playing" },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (episodeTag != null) {
                Text(
                    text = episodeTag,
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (metaBits.isNotEmpty()) {
                Text(
                    text = metaBits.joinToString("  ·  "),
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Stream column.
        PaneColumn("Stream", modifier = Modifier.weight(1f)) {
            if (badges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    badges.forEach { badge ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(50),
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = badge,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                }
            }
            streamRows.forEach { (label, value) ->
                LabelValueRow(label = label, value = value)
            }
        }
    }
}

private fun currentChapterTitle(chapters: List<VersionChapter>, positionSec: Double): String? {
    if (chapters.isEmpty()) return null
    val current = chapters.lastOrNull { it.startSeconds <= positionSec } ?: return null
    return current.title.ifBlank { "Chapter ${current.index + 1}" }
}

@Composable
private fun PaneColumn(
    header: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = header.uppercase(),
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        content()
    }
}

@Composable
private fun LabelValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        )
        Box(modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Stats pane — renders the live [PlayerStatsSnapshot] populated by
 * [PlaybackAnalyticsListener] events. Fields populate as events arrive
 * (format change, decoder init, bandwidth estimate); the pane shows only
 * non-null rows.
 */
@Composable
private fun HudStatsPane(stats: PlayerStatsSnapshot, modifier: Modifier = Modifier) {
    val rows = stats.hudRows()

    if (rows.isEmpty()) {
        HudEmptyStatePane("Stats unavailable", modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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

/** Sleep-timer presets (minutes) — mirrors the phone SleepTimerSheet. */
private val SLEEP_TIMER_PRESETS = listOf(15, 30, 45, 60, 90)

private fun formatSleepRemaining(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val m = s / 60
    val sec = s % 60
    return if (m > 0) "${m}m ${sec}s" else "${sec}s"
}

/**
 * Video pane — playback speed + HDR toggle + Fill mode (Letterbox vs Zoom,
 * matching tvOS video-gravity). When a stream advertises multiple video tracks
 * they're appended below as a secondary picker.
 */
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
    autoSkipIntro: Boolean,
    onAutoSkipIntroChanged: (Boolean) -> Unit,
    autoPlayNext: Boolean,
    onAutoPlayNextChanged: (Boolean) -> Unit,
    videoTracks: List<PlayerTrackEntry>,
    onSelectVideo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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
                        VideoFillMode.Stretch -> "Stretch"
                    },
                    selected = fillMode == mode,
                    onClick = { onFillModeChanged(mode) },
                )
            }
        }

        // In-player quick toggles for the per-profile auto behaviors (phone
        // exposes these in the player settings sheet too, not just Settings).
        Text(
            text = "Auto-skip intro",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            HudClickChip("On", autoSkipIntro) { onAutoSkipIntroChanged(true) }
            HudClickChip("Off", !autoSkipIntro) { onAutoSkipIntroChanged(false) }
        }
        Text(
            text = "Auto-play next episode",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            HudClickChip("On", autoPlayNext) { onAutoPlayNextChanged(true) }
            HudClickChip("Off", !autoPlayNext) { onAutoPlayNextChanged(false) }
        }

        if (videoTracks.size > 1) {
            Text(
                text = "Video track",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.md),
            )
            HudPickerPane(
                modifier = Modifier.heightIn(max = 220.dp),
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
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        if (audioTracks.size > 1) {
            Text(
                text = "Audio track",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HudPickerPane(
                modifier = Modifier.heightIn(max = 180.dp),
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
 * Subtitles pane — folds the retired separate subtitle drawer + style dialog
 * into a HUD tab: track list (Off + each track), delay stepper, appearance
 * chips (size / font / colors / background / opacity / outline / position), plus
 * the Android-only Search / AI-Translate rows. Hidden entirely when the stream
 * has no subtitle tracks (the tab is dropped by [visibleHudTabs]).
 */
@Composable
private fun HudSubtitlesPane(
    subtitleTracks: List<PlayerTrackEntry>,
    onSelectSubtitle: (Int) -> Unit,
    subtitleDelayMs: Int,
    onSubtitleDelayChanged: (Int) -> Unit,
    appearance: SubtitleAppearance,
    onAppearanceChanged: (SubtitleAppearance) -> Unit,
    onPaneShown: () -> Unit,
    onSearchSubtitles: (() -> Unit)?,
    onTranslateWithAi: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onPaneShown() }

    val trackOptions = buildList {
        add(TrackOption(-1, "Off", subtitleTracks.none { it.isSelected }))
        addAll(subtitleTracks.map { TrackOption(it.index, it.displayLabel, it.isSelected) })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "Tracks",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            trackOptions.forEach { opt ->
                HudPickerRow(option = opt, onSelect = { onSelectSubtitle(opt.id) })
            }
        }

        Text(
            text = "Subtitle delay: ${subtitleSyncCompactValue(subtitleDelayMs)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        DelayStepperRow(valueMs = subtitleDelayMs, onChange = onSubtitleDelayChanged)

        // Appearance — folded from the retired TvSubtitleStyleDialog.
        StyleSection("Text size") {
            FONT_SIZES.forEach { (preset, label) ->
                HudClickChip(label, appearance.fontSize == preset) {
                    onAppearanceChanged(appearance.copy(fontSize = preset))
                }
            }
        }
        StyleSection("Font") {
            FONT_FAMILIES.forEach { (value, label) ->
                HudClickChip(label, appearance.fontFamily == value) {
                    onAppearanceChanged(appearance.copy(fontFamily = value))
                }
            }
        }
        StyleSection("Text color") {
            TEXT_COLOR_SWATCHES.forEach { hex ->
                StyleColorSwatch(hex, appearance.fontColor.equals(hex, ignoreCase = true)) {
                    onAppearanceChanged(appearance.copy(fontColor = hex))
                }
            }
        }
        StyleSection("Background") {
            BACKGROUND_STYLES.forEach { (preset, label) ->
                HudClickChip(label, appearance.backgroundStyle == preset) {
                    onAppearanceChanged(appearance.copy(backgroundStyle = preset))
                }
            }
        }
        StyleSection("Background color") {
            BACKGROUND_COLOR_SWATCHES.forEach { hex ->
                StyleColorSwatch(hex, appearance.backgroundColor.equals(hex, ignoreCase = true)) {
                    onAppearanceChanged(appearance.copy(backgroundColor = hex))
                }
            }
        }
        StyleSection("Background opacity") {
            OPACITY_STEPS.forEach { pct ->
                HudClickChip("$pct%", appearance.backgroundOpacity == pct) {
                    onAppearanceChanged(appearance.copy(backgroundOpacity = pct))
                }
            }
        }
        StyleSection("Outline") {
            HudClickChip("On", appearance.textOutline) { onAppearanceChanged(appearance.copy(textOutline = true)) }
            HudClickChip("Off", !appearance.textOutline) { onAppearanceChanged(appearance.copy(textOutline = false)) }
        }
        if (appearance.textOutline) {
            StyleSection("Outline color") {
                TEXT_COLOR_SWATCHES.forEach { hex ->
                    StyleColorSwatch(hex, appearance.textOutlineColor.equals(hex, ignoreCase = true)) {
                        onAppearanceChanged(appearance.copy(textOutlineColor = hex))
                    }
                }
            }
        }
        StyleSection("Position") {
            POSITIONS.forEach { (preset, label) ->
                HudClickChip(label, appearance.position == preset) {
                    onAppearanceChanged(appearance.copy(position = preset))
                }
            }
        }

        // Android-only sidecar acquisition rows — kept from the old drawer.
        if (onSearchSubtitles != null || onTranslateWithAi != null) {
            Column(
                modifier = Modifier.padding(top = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (onSearchSubtitles != null) {
                    HudActionRow(label = "Search subtitles", onClick = onSearchSubtitles)
                }
                if (onTranslateWithAi != null) {
                    HudActionRow(label = "Translate with AI", onClick = onTranslateWithAi)
                }
            }
        }
    }
}

@Composable
private fun StyleSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = Spacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

@Composable
private fun StyleColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val ring = when {
        isFocused -> Color.White
        selected -> Color.White.copy(alpha = 0.85f)
        else -> Color.White.copy(alpha = 0.25f)
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(hexToColor(hex))
            .border(width = if (isFocused || selected) 3.dp else 1.dp, color = ring, shape = CircleShape)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
    )
}

private fun hexToColor(hex: String): Color = try {
    val cleaned = if (hex.startsWith("#")) hex.drop(1) else hex
    Color(0xFF000000.toInt() or (cleaned.toLong(16).toInt() and 0x00FFFFFF))
} catch (_: NumberFormatException) {
    Color.White
}

private val FONT_SIZES = listOf(
    SubtitleFontSizePreset.Small to "Small",
    SubtitleFontSizePreset.Medium to "Medium",
    SubtitleFontSizePreset.Large to "Large",
    SubtitleFontSizePreset.XLarge to "X-Large",
    SubtitleFontSizePreset.XXLarge to "XX-Large",
)
private val FONT_FAMILIES = listOf(
    SubtitleAppearance.SANS_SERIF to "Sans-serif",
    SubtitleAppearance.SERIF to "Serif",
    SubtitleAppearance.MONOSPACE to "Monospace",
)
private val BACKGROUND_STYLES = listOf(
    SubtitleBackgroundStylePreset.Box to "Box",
    SubtitleBackgroundStylePreset.Shadow to "Shadow",
    SubtitleBackgroundStylePreset.Outline to "Outline",
    SubtitleBackgroundStylePreset.None to "None",
)
private val POSITIONS = listOf(
    SubtitlePositionPreset.Bottom to "Bottom",
    SubtitlePositionPreset.LowerThird to "Lower Third",
    SubtitlePositionPreset.Top to "Top",
)
private val OPACITY_STEPS = listOf(0, 25, 50, 75, 100)
private val TEXT_COLOR_SWATCHES = listOf(
    "#ffffff", "#facc15", "#22c55e", "#06b6d4", "#d946ef", "#ef4444", "#3b82f6", "#000000",
)
private val BACKGROUND_COLOR_SWATCHES = listOf(
    "#000000", "#374151", "#1e3a5f", "#7f1d1d", "#14532d",
)

// Signed current offset — matches the phone's formatDelayMs (true minus sign).
private fun subtitleSyncCompactValue(valueMs: Int): String =
    when {
        valueMs > 0 -> "+$valueMs ms"
        valueMs < 0 -> "−${-valueMs} ms"
        else -> "0 ms"
    }

/**
 * Full-width action row for HUD panes — a true click target: an explicit Select
 * press is required (focus-driven commit would fire dialogs during plain
 * traversal).
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
 * D-pad-friendly stepper row: −50 / −10 / Reset / +10 / +50 ms. Shared by audio
 * + subtitles so both delays use the same row. PlayerSettingsStore clamps to
 * iOS-parity ranges, so boundary presses become no-ops.
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
        DelayStepperButton(label = "−50", onClick = { onChange(valueMs - 50) })
        DelayStepperButton(label = "−10", onClick = { onChange(valueMs - 10) })
        DelayStepperButton(label = "Reset", onClick = { onChange(0) })
        DelayStepperButton(label = "+10", onClick = { onChange(valueMs + 10) })
        DelayStepperButton(label = "+50", onClick = { onChange(valueMs + 50) })
    }
}

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
 * focus-driven picker. Selecting a chapter seeks the player to its start time.
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
        modifier = modifier.fillMaxSize(),
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
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
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
