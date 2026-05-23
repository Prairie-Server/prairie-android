package com.continuum.app.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.continuum.app.common.player.SleepTimerState
import kotlinx.coroutines.launch

/**
 * Glass-style player settings bottom sheet — Phase 1 subset:
 * speed, aspect (video gravity), HDR, auto-skip intro, auto-play next.
 *
 * Mirrors iOS `PlayerSettingsSheet.swift` for the rows it covers; subtitle
 * styling, sleep timer, sync, and route-diagnostic rows arrive in later phases.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    playbackSpeed: Double,
    onSetPlaybackSpeed: (Double) -> Unit,
    videoGravity: String,
    onSetVideoGravity: (String) -> Unit,
    autoSkipIntroEnabled: Boolean,
    onSetAutoSkipIntro: (Boolean) -> Unit,
    autoPlayNextEnabled: Boolean,
    onSetAutoPlayNext: (Boolean) -> Unit,
    hdrEnabled: Boolean,
    onSetHdrEnabled: (Boolean) -> Unit,
    onOpenSubtitleStyle: () -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
    sleepTimerState: SleepTimerState = SleepTimerState.Idle,
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(isVisible) {
        if (isVisible) sheetState.show()
    }

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch { sheetState.hide() }
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = Color.White,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1F2937).copy(alpha = 0.95f),
                            Color.Black.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Playback Settings",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                )

                SectionHeader(text = "Playback")

                SpeedRow(
                    selected = playbackSpeed,
                    onSelect = onSetPlaybackSpeed,
                )

                AspectRow(
                    selected = videoGravity,
                    onSelect = onSetVideoGravity,
                )

                ToggleRow(
                    label = "HDR",
                    subtitle = null,
                    checked = hdrEnabled,
                    onCheckedChange = onSetHdrEnabled,
                )

                SectionHeader(text = "Episodes")

                ToggleRow(
                    label = "Auto-skip Intro",
                    subtitle = "Skip intro after a 5-second countdown.",
                    checked = autoSkipIntroEnabled,
                    onCheckedChange = onSetAutoSkipIntro,
                )

                ToggleRow(
                    label = "Auto-play Next Episode",
                    subtitle = null,
                    checked = autoPlayNextEnabled,
                    onCheckedChange = onSetAutoPlayNext,
                )

                SectionHeader(text = "Subtitles")

                TapRow(
                    label = "Subtitle Style",
                    subtitle = "Font, color, background, position",
                    onClick = {
                        scope.launch { sheetState.hide() }
                        onDismiss()
                        onOpenSubtitleStyle()
                    },
                )

                SectionHeader(text = "Timers")

                TapRow(
                    label = "Sleep Timer",
                    subtitle = formatSleepTimerSubtitle(sleepTimerState),
                    onClick = {
                        scope.launch { sheetState.hide() }
                        onDismiss()
                        onOpenSleepTimer()
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SpeedRow(
    selected: Double,
    onSelect: (Double) -> Unit,
) {
    val options = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0)
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Speed",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${formatPlaybackSpeed(selected)}×",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { value ->
                SpeedPill(
                    value = value,
                    isSelected = isSameSpeed(value, selected),
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

@Composable
private fun SpeedPill(
    value: Double,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val baseModifier = Modifier
        .clickable(onClick = onClick)
        .padding(horizontal = 1.dp)
    Box(
        modifier = if (isSelected) {
            baseModifier.background(color = Color.White, shape = shape)
        } else {
            baseModifier
                .background(color = Color.Transparent, shape = shape)
                .border(width = 1.dp, color = Color.White, shape = shape)
        },
    ) {
        Text(
            text = "${formatPlaybackSpeed(value)}×",
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun AspectRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val options = listOf("fit" to "Fit", "fill" to "Fill", "stretch" to "Stretch")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Aspect",
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, label) ->
                AspectPill(
                    label = label,
                    isSelected = selected == value,
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

@Composable
private fun AspectPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val baseModifier = Modifier
        .clickable(onClick = onClick)
    Box(
        modifier = if (isSelected) {
            baseModifier.background(color = Color.White, shape = shape)
        } else {
            baseModifier
                .background(color = Color.Transparent, shape = shape)
                .border(width = 1.dp, color = Color.White, shape = shape)
        },
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TapRow(
    label: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 16.sp,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "›",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 22.sp,
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 16.sp,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF06B6D4),
            ),
        )
    }
}

/**
 * Subtitle text for the Sleep Timer row — shows "Off" when idle and the
 * remaining countdown when active. Reuses [formatRemaining] for parity with
 * the on-screen chip.
 */
private fun formatSleepTimerSubtitle(state: SleepTimerState): String {
    return when (state) {
        is SleepTimerState.Idle -> "Off"
        is SleepTimerState.Active -> "Pausing in ${formatRemaining(state.remainingSeconds)}"
    }
}

/**
 * Format the playback speed for display: trim trailing zeros / decimal point so
 * 1.0 → "1", 1.25 → "1.25", 1.50 → "1.5".
 */
private fun formatPlaybackSpeed(speed: Double): String {
    if (speed % 1.0 == 0.0) return speed.toInt().toString()
    val formatted = String.format("%.2f", speed)
    return formatted.trimEnd('0').trimEnd('.')
}

/**
 * Compare doubles loosely so 1.0 from a flow matches 1.0 from the option list
 * across float-rounding hiccups.
 */
private fun isSameSpeed(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) < 0.001
