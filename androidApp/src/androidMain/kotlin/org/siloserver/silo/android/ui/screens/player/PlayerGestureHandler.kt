package org.siloserver.silo.android.ui.screens.player

import android.content.Context
import android.media.AudioManager
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Gesture handler overlay for the video player.
 *
 * Supported gestures:
 * - Single tap center: toggle controls visibility
 * - Double-tap left third: skip back 10 seconds
 * - Double-tap right third: skip forward 10 seconds
 * - Hold: temporary 2x playback while held
 * - Two-finger pinch: cycle video gravity Fit -> Fill -> Stretch
 * - Vertical swipe in the left edge zone: brightness adjustment
 * - Vertical swipe in the right edge zone: volume adjustment
 * - Vertical swipe down in the center: dismiss the player (iOS
 *   MobilePlayerGestureLayer parity — evaluated on release, mostly-vertical
 *   drags over 140dp only; no interactive transform, no velocity check)
 * - Horizontal swipe: seek through the video
 */
@Composable
fun PlayerGestureHandler(
    position: Double,
    duration: Double,
    onToggleControls: () -> Unit,
    onSeek: (Double) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onFastForwardHold: (Boolean) -> Unit = {},
    onCycleVideoGravity: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // Keep latest position/duration available inside long-lived gesture coroutines
    // without re-keying pointerInput (re-keying on every position tick — ~every 500ms —
    // tears down the coroutine and drops in-flight taps and double-taps).
    val currentPosition by rememberUpdatedState(position)
    val currentDuration by rememberUpdatedState(duration)

    var seekDragStartPosition by remember { mutableDoubleStateOf(0.0) }
    var seekDragAccumulator by remember { mutableFloatStateOf(0f) }
    var suppressTapAfterFastForwardHold by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    var zoomAccumulator = 1f
                    var cycled = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2 && !cycled) {
                            val first = pressed[0]
                            val second = pressed[1]
                            val previousDistance = pointerDistance(first.previousPosition, second.previousPosition)
                            val currentDistance = pointerDistance(first.position, second.position)
                            if (previousDistance > 0f && currentDistance > 0f) {
                                zoomAccumulator *= currentDistance / previousDistance
                                if (
                                    zoomAccumulator >= PinchGravityThreshold ||
                                    zoomAccumulator <= 1f / PinchGravityThreshold
                                ) {
                                    pressed.forEach { it.consume() }
                                    onCycleVideoGravity()
                                    cycled = true
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        var holdTriggered = false
                        coroutineScope {
                            val holdJob = launch {
                                delay(500)
                                holdTriggered = true
                                onFastForwardHold(true)
                            }
                            try {
                                tryAwaitRelease()
                            } finally {
                                holdJob.cancel()
                                if (holdTriggered) {
                                    suppressTapAfterFastForwardHold = true
                                    onFastForwardHold(false)
                                }
                            }
                        }
                    },
                    onTap = {
                        if (suppressTapAfterFastForwardHold) {
                            suppressTapAfterFastForwardHold = false
                        } else {
                            onToggleControls()
                        }
                    },
                    onDoubleTap = { offset ->
                        suppressTapAfterFastForwardHold = false
                        val thirdWidth = size.width / 3f
                        when {
                            offset.x < thirdWidth -> onSkipBackward()
                            offset.x > thirdWidth * 2 -> onSkipForward()
                            else -> onToggleControls()
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                // iOS edgeAndDismissDrag: the start x picks the mode once —
                // left 88dp edge = brightness, right 88dp edge = volume, and a
                // center drag becomes a dismiss candidate judged on release.
                var mode = VerticalDragMode.None
                var totalDrag = Offset.Zero
                val edgeZonePx = EdgeZoneWidthDp.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { start ->
                        totalDrag = Offset.Zero
                        mode = when {
                            start.x < edgeZonePx -> VerticalDragMode.Brightness
                            start.x > size.width - edgeZonePx -> VerticalDragMode.Volume
                            else -> VerticalDragMode.DismissCandidate
                        }
                    },
                    onDragEnd = {
                        if (mode == VerticalDragMode.DismissCandidate) {
                            val dy = totalDrag.y
                            val dx = totalDrag.x
                            if (dy > DismissDragThresholdDp.dp.toPx() &&
                                kotlin.math.abs(dx) < dy * 0.6f
                            ) {
                                onDismiss()
                            }
                        }
                        mode = VerticalDragMode.None
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += change.position - change.previousPosition
                        val sensitivity = 0.01f
                        when (mode) {
                            VerticalDragMode.Brightness ->
                                adjustBrightness(context, -dragAmount * sensitivity)
                            VerticalDragMode.Volume ->
                                adjustVolume(audioManager, -dragAmount * sensitivity)
                            else -> Unit
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        seekDragStartPosition = currentPosition
                        seekDragAccumulator = 0f
                    },
                    onDragEnd = {
                        val dur = currentDuration
                        val seekAmount = (seekDragAccumulator / size.width.toFloat()) * dur.toFloat() * 0.5f
                        val newPosition = (seekDragStartPosition + seekAmount).coerceIn(0.0, dur)
                        onSeek(newPosition)
                        seekDragAccumulator = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        seekDragAccumulator += dragAmount
                    },
                )
            }
    )
}

private const val PinchGravityThreshold = 1.16f

private enum class VerticalDragMode { None, Brightness, Volume, DismissCandidate }

/** iOS edge-zone width (88pt) for brightness/volume vertical drags. */
private const val EdgeZoneWidthDp = 88

/** iOS dismiss threshold: a mostly-vertical downward drag over 140pt. */
private const val DismissDragThresholdDp = 140

private fun pointerDistance(first: Offset, second: Offset): Float =
    hypot(first.x - second.x, first.y - second.y)

/**
 * Adjusts the screen brightness. Values are clamped to [0.01, 1.0].
 * Uses the window's layout params for per-activity brightness control.
 */
private fun adjustBrightness(context: Context, delta: Float) {
    val activity = context as? android.app.Activity ?: return
    val window: Window = activity.window
    val layoutParams = window.attributes
    val currentBrightness = if (layoutParams.screenBrightness < 0) 0.5f else layoutParams.screenBrightness
    val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1.0f)
    layoutParams.screenBrightness = newBrightness
    window.attributes = layoutParams
}

/**
 * Adjusts the media volume. Delta is normalized, so we scale to the max volume.
 */
private fun adjustVolume(audioManager: AudioManager, delta: Float) {
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val volumeStep = (delta * maxVolume).toInt()
    val newVolume = (currentVolume + volumeStep).coerceIn(0, maxVolume)
    if (newVolume != currentVolume) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
    }
}
