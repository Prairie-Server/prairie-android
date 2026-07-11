package org.siloserver.silo.android.ui.screens.player

import android.content.Context
import android.media.AudioManager
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * - Double-tap left 35% zone: skip back 10 seconds (with a flash badge)
 * - Double-tap right 35% zone: skip forward 10 seconds (with a flash badge)
 * - Hold: temporary 2x playback while held
 * - Two-finger pinch: step video gravity — pinch-out steps Fit -> Fill ->
 *   Stretch, pinch-in steps back, clamped at both ends (iOS parity)
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
    // When false (a Watch Together guest without seek authority), the outer
    // double-tap skip bands fall through to a controls toggle rather than
    // firing a gated no-op skip and flashing a "+10s" badge that lies about a
    // position that never moves. Mirrors PlayerControls' seekEnabled gating.
    seekEnabled: Boolean = true,
    onFastForwardHold: (Boolean) -> Unit = {},
    // Called once per completed pinch: true = pinch-out (step toward
    // fill/stretch), false = pinch-in (step back toward fit).
    onPinchVideoGravity: (Boolean) -> Unit = {},
    onDismiss: () -> Unit = {},
    // Swipe-down-to-dismiss is suppressed until playback is actually established.
    // During the initial open the media is still loading, and a downward volume/
    // brightness swipe that drifts inward would otherwise be read as "close the
    // player" — which then strands the user (Jim, Fold).
    dismissEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // Keep latest position/duration available inside long-lived gesture coroutines
    // without re-keying pointerInput (re-keying on every position tick — ~every 500ms —
    // tears down the coroutine and drops in-flight taps and double-taps).
    val currentPosition by rememberUpdatedState(position)
    val currentDuration by rememberUpdatedState(duration)
    val currentSeekEnabled by rememberUpdatedState(seekEnabled)

    // Live scrub feedback shown while a seek drag is in progress (direction +
    // delta + target time); null when not seeking.
    var seekPreview by remember { mutableStateOf<SeekPreview?>(null) }
    var suppressTapAfterFastForwardHold by remember { mutableStateOf(false) }

    // Double-tap skip feedback (iOS MobilePlayerGestureLayer skipFlash parity):
    // the badge appears instantly, holds 700ms, then fades out. The nonce makes
    // rapid re-taps restart the hold timer; the retained copy keeps content
    // rendered through the exit fade (same pattern as the Up Next card).
    var skipFlash by remember { mutableStateOf<SkipFlash?>(null) }
    var retainedSkipFlash by remember { mutableStateOf<SkipFlash?>(null) }
    var skipFlashNonce by remember { mutableLongStateOf(0L) }
    LaunchedEffect(skipFlash) {
        if (skipFlash != null) {
            retainedSkipFlash = skipFlash
            delay(SkipFlashHoldMs)
            skipFlash = null
        } else {
            delay(220)
            retainedSkipFlash = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // iOS videoGravityPinchGesture parity: accumulate the scale
                // over the whole gesture and evaluate ONCE on release — one
                // directional step per pinch, with a dead zone between the
                // pinch-in and pinch-out thresholds.
                awaitEachGesture {
                    var zoomAccumulator = 1f
                    var isPinch = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            isPinch = true
                            val first = pressed[0]
                            val second = pressed[1]
                            val previousDistance = pointerDistance(first.previousPosition, second.previousPosition)
                            val currentDistance = pointerDistance(first.position, second.position)
                            if (previousDistance > 0f && currentDistance > 0f) {
                                zoomAccumulator *= currentDistance / previousDistance
                            }
                            // Keep two-finger events away from the seek /
                            // brightness drag detectors below.
                            pressed.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (isPinch) {
                        when {
                            zoomAccumulator > PinchGravityThreshold -> onPinchVideoGravity(true)
                            zoomAccumulator < 1f / PinchGravityThreshold -> onPinchVideoGravity(false)
                        }
                    }
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
                        // iOS skipZoneFraction (0.35): outer bands skip ±10s
                        // with a flash badge; the middle band keeps the
                        // controls toggle.
                        val skipZoneWidth = size.width * SkipZoneFraction
                        when {
                            // Guest without seek authority: no gated no-op skip,
                            // no lying flash — behave like the center band.
                            !currentSeekEnabled -> onToggleControls()
                            offset.x < skipZoneWidth -> {
                                onSkipBackward()
                                skipFlash = SkipFlash(forward = false, nonce = ++skipFlashNonce)
                            }
                            offset.x > size.width - skipZoneWidth -> {
                                onSkipForward()
                                skipFlash = SkipFlash(forward = true, nonce = ++skipFlashNonce)
                            }
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
                        if (dismissEnabled && mode == VerticalDragMode.DismissCandidate) {
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
                // Low-level loop instead of detectHorizontalDragGestures: that
                // detector reports onDragStart only AFTER crossing (and consuming)
                // horizontal slop, so the start position is post-slop and the edge
                // event is already claimed — too late to (a) honor the safe-box on
                // the true down position, or (b) leave an edge swipe for the system
                // back gesture. Here we read the raw down, apply the safe-box
                // BEFORE consuming anything, and only then claim the drag.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val edge = size.width * SeekEdgeExclusionFraction
                    // Safe-box: a drag beginning in the outer band (or with seek
                    // gated off) is left entirely unconsumed so it can be a system
                    // back-swipe, not a scrub.
                    if (!currentSeekEnabled ||
                        down.position.x < edge ||
                        down.position.x > size.width - edge
                    ) {
                        return@awaitEachGesture
                    }
                    // Claim only once the motion is actually horizontal (lets taps,
                    // vertical brightness/volume/dismiss drags win otherwise).
                    var accumulator = 0f
                    val slopChange = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        change.consume()
                        accumulator += overSlop
                    } ?: return@awaitEachGesture

                    val startPosition = currentPosition
                    fun publishPreview() {
                        val dur = currentDuration
                        val seekAmount = (accumulator / size.width.toFloat()) * dur.toFloat() * 0.5f
                        val target = (startPosition + seekAmount).coerceIn(0.0, dur)
                        seekPreview = SeekPreview(
                            deltaSeconds = target - startPosition,
                            targetSeconds = target,
                        )
                    }
                    slopChange.consume()
                    publishPreview()

                    horizontalDrag(down.id) { change ->
                        accumulator += change.positionChange().x
                        change.consume()
                        publishPreview()
                    }

                    // Released: commit the seek to the previewed target.
                    val dur = currentDuration
                    val seekAmount = (accumulator / size.width.toFloat()) * dur.toFloat() * 0.5f
                    onSeek((startPosition + seekAmount).coerceIn(0.0, dur))
                    seekPreview = null
                }
            },
    ) {
        // Double-tap skip flash badge — vertically centered in the tapped
        // half (iOS positions it at 18% / 82% of the width; the edge padding
        // approximates that). Non-interactive: it sits under no pointer
        // handlers of its own and gestures keep flowing to this Box.
        val flash = retainedSkipFlash
        if (flash != null) {
            AnimatedVisibility(
                visible = skipFlash != null,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(durationMillis = 200)),
                modifier = Modifier
                    .align(if (flash.forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 56.dp),
            ) {
                SkipFlashBadge(forward = flash.forward)
            }
        }

        // Live scrub feedback while a horizontal seek drag is in progress.
        val preview = seekPreview
        if (preview != null) {
            SeekPreviewBadge(preview, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/**
 * Live scrubbing HUD shown during a horizontal seek drag: a direction glyph,
 * the signed delta from the drag's start position, and the target time. No
 * animation — it tracks the finger and vanishes on release.
 */
@Composable
private fun SeekPreviewBadge(preview: SeekPreview, modifier: Modifier = Modifier) {
    val forward = preview.deltaSeconds >= 0
    Row(
        modifier = modifier
            .background(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (forward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatSeekDelta(preview.deltaSeconds),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = formatSeekClock(preview.targetSeconds),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }
    }
}

private data class SeekPreview(val deltaSeconds: Double, val targetSeconds: Double)

private fun formatSeekClock(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun formatSeekDelta(deltaSeconds: Double): String {
    val sign = if (deltaSeconds >= 0) "+" else "−"
    return sign + formatSeekClock(kotlin.math.abs(deltaSeconds))
}

/**
 * iOS `skipFlashView` parity: a 74dp circle with the circular-arrow skip
 * glyph above a "+10s"/"−10s" caption.
 */
@Composable
private fun SkipFlashBadge(forward: Boolean) {
    Column(
        modifier = Modifier
            .size(74.dp)
            .background(color = Color.Black.copy(alpha = 0.55f), shape = CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (forward) Icons.Filled.Forward10 else Icons.Filled.Replay10,
            contentDescription = if (forward) "Skipped forward 10 seconds" else "Skipped back 10 seconds",
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = if (forward) "+10s" else "−10s",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** iOS pinch dead zone: >1.08 steps out, <1/1.08 steps in, between = no-op. */
private const val PinchGravityThreshold = 1.08f

/** iOS skipZoneFraction: outer double-tap bands (35% each side) skip ±10s. */
private const val SkipZoneFraction = 0.35f

/**
 * Horizontal seek safe-box: a drag must START within the inner 80% (this much
 * excluded on each side) to scrub. Drags beginning in the outer band are left
 * unconsumed so an edge swipe stays a system back-swipe, not a rewind (Jim).
 */
private const val SeekEdgeExclusionFraction = 0.1f

/** iOS skip flash hold before the fade-out begins. */
private const val SkipFlashHoldMs = 700L

private data class SkipFlash(val forward: Boolean, val nonce: Long)

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
