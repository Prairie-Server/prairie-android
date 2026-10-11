package org.prairieserver.prairie.android.ui.screens.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.prairieserver.prairie.android.ui.util.formatClockTime
import org.prairieserver.prairie.common.player.TrickplayTileImage
import org.prairieserver.prairie.model.catalog.TimeRange
import org.prairieserver.prairie.model.catalog.VersionChapter
import org.prairieserver.prairie.playback.TrickplayInfo
import org.prairieserver.prairie.playback.resolveTrickplayTile

/**
 * Seek bar with current/total time and a buffered-ahead track, mirroring
 * iOS `MobilePlayerControls.progressSlider` plus web trickplay scrub previews:
 *
 * - three track regions — played, buffered (safe to seek into), base;
 * - detected marker ranges tinted as bands (intro cyan, recap green,
 *   credits orange, preview purple);
 * - a 2dp chapter tick per chapter, drawn under the played fill;
 * - while scrubbing, a preview bubble above the thumb with trickplay tiles
 *   when available, the target time, and the chapter title at that point.
 * The seek bar. Like iOS, the whole bar is the handle: it grows from 5 to
 * 11dp under the finger, a bubble above shows the target time, the chapter,
 * and how far the scrub has moved, and a tick marks where it started. Chapters
 * show as gaps in the bar rather than tick marks, and a light haptic fires as
 * the scrub crosses into a new chapter.
 * Track regions: played (Paper), buffered (safe to seek into), base. Detected
 * markers are tinted bands under the played fill (intro cyan, recap green,
 * credits orange, preview purple).
 */
@Composable
fun PlayerProgressBar(
    position: Double,
    duration: Double,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
    bufferedPosition: Double = 0.0,
    enabled: Boolean = true,
    chapters: List<VersionChapter> = emptyList(),
    intro: TimeRange? = null,
    trickplay: TrickplayInfo? = null,
    credits: TimeRange? = null,
    recap: TimeRange? = null,
    preview: TimeRange? = null,
    showTimes: Boolean = true,
    onScrubbingChange: (Boolean) -> Unit = {},
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableDoubleStateOf(0.0) }
    var scrubStart by remember { mutableDoubleStateOf(0.0) }
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var bubbleWidthPx by remember { mutableIntStateOf(0) }
    var lastChapter by remember { mutableIntStateOf(-1) }
    var showTotal by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnScrubbing by rememberUpdatedState(onScrubbingChange)
    // The gesture coroutine outlives recompositions, so it reads these
    // through state rather than the values it started with.
    val latestPosition by rememberUpdatedState(position)
    val latestChapters by rememberUpdatedState(chapters)

    val hasKnownDuration = duration.isFinite() && duration > 0.0
    val seekable = enabled && hasKnownDuration
    val displayPosition = (if (isSeeking) seekPosition else position)
        .let { if (hasKnownDuration) it.coerceIn(0.0, duration) else it.coerceAtLeast(0.0) }
    val playedFraction = if (hasKnownDuration) (displayPosition / duration).toFloat() else 0f
    val bufferedFraction = if (hasKnownDuration) {
        (bufferedPosition.coerceIn(0.0, duration) / duration).toFloat().coerceAtLeast(playedFraction)
    } else {
        0f
    }
    val trickplayTile = if (isSeeking) {
        resolveTrickplayTile(trickplay, seekPosition.toDouble())
    } else {
        null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (isSeeking) {
                val density = LocalDensity.current
                val barWidthDp = with(density) { barWidthPx.toDp() }
                val clampPad = 80.dp
                val bubbleX = (barWidthDp * playedFraction)
                    .coerceIn(clampPad, (barWidthDp - clampPad).coerceAtLeast(clampPad))
                val chapterTitle = chapterTitleAt(chapters, seekPosition.toDouble())
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset(x = bubbleX - clampPad)
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.82f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    if (trickplayTile != null) {
                        TrickplayTileImage(
                            tile = trickplayTile,
                            previewWidth = 176.dp,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Text(
                        text = formatClockTime(seekPosition.toDouble()),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                    )
                    chapterTitle?.let { title ->
                        Text(
                            text = title,
                            modifier = Modifier.widthIn(max = 160.dp),
                            fontSize = 12.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                }
            }
        }

        Slider(
            value = sliderPosition,
            enabled = enabled && hasKnownDuration,
            onValueChange = { value ->
                isSeeking = true
                seekPosition = value
            },
            onValueChangeFinished = {
                isSeeking = false
                onSeek(seekPosition.toDouble())
            valueRange = 0f..maxDuration,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            thumb = {
                Box(
                        .size(if (isSeeking) 20.dp else 14.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            track = {
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .onSizeChanged { barWidthPx = it.width.toFloat() },
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bufferedFraction)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.52f)),
                    // Marker bands — tinted segments for each detected marker
                    // kind (intro/recap/credits/preview), drawn under the played
                    // fill so the playhead still reads clearly over them.
    val barHeight by animateDpAsState(
        targetValue = if (isSeeking) 11.dp else 5.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "seekBarHeight",
    )
    val timesAlpha by animateFloatAsState(if (isSeeking) 0f else 1f, label = "seekTimesAlpha")
    fun positionAt(x: Float): Double =
        if (barWidthPx <= 0f) 0.0 else (x / barWidthPx).coerceIn(0f, 1f).toDouble() * duration
    fun chapterIndexAt(seconds: Double): Int = latestChapters.indexOfLast { it.startSeconds <= seconds }
    Column(modifier = modifier.fillMaxWidth()) {
        if (showTimes) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(timesAlpha),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatClockTime(displayPosition),
                    style = PlayerType.Time,
                    modifier = Modifier.weight(1f),
                // Tap to switch between time left and total length (iOS).
                    text = if (showTotal && hasKnownDuration) {
                        formatClockTime(duration)
                    } else {
                        remainingTimeLabel(displayPosition, duration)
                    },
                    style = PlayerType.Time.copy(color = PlayerChrome.Graphite),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Switch between time left and total length",
                    ) { showTotal = !showTotal },
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .semantics {
                    contentDescription = "Playback position"
                    stateDescription = "${formatClockTime(displayPosition)} of ${formatClockTime(duration)}"
                    if (hasKnownDuration) {
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = displayPosition.toFloat(),
                            range = 0f..duration.toFloat(),
                        )
                    }
                    if (seekable) {
                        setProgress { target ->
                            latestOnSeek(target.toDouble().coerceIn(0.0, duration))
                            true
                        }
                    }
                }
                .pointerInput(seekable, duration) {
                    if (!seekable) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        scrubStart = latestPosition
                        seekPosition = positionAt(down.position.x)
                        lastChapter = chapterIndexAt(seekPosition)
                        isSeeking = true
                        latestOnScrubbing(true)
                        var pointerId = down.id
                        // A restart of this block (seekability or duration
                        // changed mid-scrub) cancels the gesture: end the
                        // scrub without seeking so the chrome comes back.
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()?.also { pointerId = it.id }
                                    ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                if (change.positionChange() != Offset.Zero) {
                                    change.consume()
                                    seekPosition = positionAt(change.position.x)
                                    val chapter = chapterIndexAt(seekPosition)
                                    if (chapter != lastChapter) {
                                        lastChapter = chapter
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                        } finally {
                            isSeeking = false
                            latestOnScrubbing(false)
                        }
                        latestOnSeek(seekPosition)
                    }
                    Box(
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val markers = listOfNotNull(
                intro?.let { it to Color(0xFF22D3EE) },
                recap?.let { it to Color(0xFF8BC34A) },
                credits?.let { it to Color(0xFFFFB74D) },
                preview?.let { it to Color(0xFFBA68C8) },
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight + 12.dp),
            ) {
                drawSeekBar(
                    barHeightPx = barHeight.toPx(),
                    playedFraction = playedFraction,
                    bufferedFraction = bufferedFraction,
                    chapterFractions = if (hasKnownDuration) {
                        chapters.map { (it.startSeconds / duration).toFloat() }
                    } else {
                        emptyList()
                    },
                    markers = if (hasKnownDuration) {
                        markers.map { (range, color) ->
                            Triple((range.start / duration).toFloat(), (range.end / duration).toFloat(), color)
                        }
                    startFraction = if (isSeeking && hasKnownDuration) (scrubStart / duration).toFloat() else null,
                    showHead = seekable && !isSeeking,
                    dimmed = !enabled,
                )
            }

            if (isSeeking && hasKnownDuration) {
                val x = barWidthPx * playedFraction
                val half = bubbleWidthPx / 2f
                val clampedX = if (barWidthPx > bubbleWidthPx) x.coerceIn(half, barWidthPx - half) else barWidthPx / 2f
                val delta = seekPosition - scrubStart
                val chapterTitle = chapterTitleAt(chapters, seekPosition)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .wrapContentSize(unbounded = true)
                        .onSizeChanged { bubbleWidthPx = it.width }
                        .offset {
                            IntOffset(
                                x = (clampedX - half).roundToInt(),
                                y = -with(density) { 52.dp.toPx() }.roundToInt(),
                            )
                        .clip(RoundedCornerShape(14.dp))
                        .background(PlayerChrome.Smoke)
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(text = formatClockTime(seekPosition), style = PlayerType.BubbleTime)
                    Text(
                        text = listOfNotNull(chapterTitle, formatSeekDelta(delta)).joinToString(" · "),
                        style = PlayerType.BubbleDetail,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .widthIn(max = 220.dp),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawSeekBar(
    barHeightPx: Float,
    playedFraction: Float,
    bufferedFraction: Float,
    chapterFractions: List<Float>,
    markers: List<Triple<Float, Float, Color>>,
    startFraction: Float?,
    showHead: Boolean,
    dimmed: Boolean,
) {
    val width = size.width
    val top = (size.height - barHeightPx) / 2f
    val radius = CornerRadius(barHeightPx / 2f)
    val gap = 2.dp.toPx()
    // Chapter boundaries become gaps only while every segment stays long
    // enough to read as a segment; a title with dozens of short chapters
    // falls back to one continuous bar.
    val boundaries = chapterFractions.filter { it > 0.001f && it < 0.999f }.sorted()
    val edges = (listOf(0f) + boundaries + listOf(1f)).map { it * width }
    val useGaps = boundaries.isNotEmpty() &&
        edges.zipWithNext().all { (a, b) -> b - a >= barHeightPx * 2f + gap }
    val segments = if (useGaps) edges.zipWithNext() else listOf(0f to width)
    val alpha = if (dimmed) 0.35f else 1f

    segments.forEachIndexed { index, (rawStart, rawEnd) ->
        val start = if (index == 0) rawStart else rawStart + gap / 2f
        val end = if (index == segments.lastIndex) rawEnd else rawEnd - gap / 2f
        val segmentSize = Size(end - start, barHeightPx)
        val segmentTopLeft = Offset(start, top)
        fun fill(color: Color, untilX: Float, fromX: Float = start) {
            if (untilX <= fromX) return
            clipRect(left = fromX, top = 0f, right = untilX.coerceAtMost(end), bottom = size.height) {
                drawRoundRect(color = color, topLeft = segmentTopLeft, size = segmentSize, cornerRadius = radius)
            }
        }
        drawRoundRect(
            color = Color.White.copy(alpha = 0.22f * alpha),
            topLeft = segmentTopLeft,
            size = segmentSize,
            cornerRadius = radius,
        )
        fill(Color.White.copy(alpha = 0.22f * alpha), bufferedFraction * width)
        markers.forEach { (from, to, color) ->
            fill(color.copy(alpha = 0.45f * alpha), untilX = to * width, fromX = maxOf(start, from * width))
        }
        fill(PlayerChrome.Paper.copy(alpha = alpha), playedFraction * width)
    }

    startFraction?.let { fraction ->
        val x = fraction * width
        val tickWidth = 2.dp.toPx()
        drawRoundRect(
            color = PlayerChrome.Paper.copy(alpha = 0.55f),
            topLeft = Offset(x - tickWidth / 2f, top - 5.dp.toPx()),
            size = Size(tickWidth, barHeightPx + 10.dp.toPx()),
            cornerRadius = CornerRadius(tickWidth / 2f),
        )
    }

    if (showHead) {
        val center = Offset(playedFraction * width, size.height / 2f)
        drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = 8.dp.toPx(), center = center)
        drawCircle(color = PlayerChrome.Paper, radius = 6.5.dp.toPx(), center = center)
    }
}

private fun formatSeekDelta(delta: Double): String? {
    if (kotlin.math.abs(delta) < 1.0) return null
    val sign = if (delta > 0) "+" else "−"
    return sign + formatClockTime(kotlin.math.abs(delta))
}

internal fun remainingTimeLabel(position: Double, duration: Double): String =
    if (duration.isFinite() && duration > 0.0) {
        "−${formatClockTime((duration - position).coerceAtLeast(0.0))}"
    } else {
        "−−:−−"
    }

/** iOS `chapterTitle(at:)`: the last chapter starting at or before [seconds],
 *  falling back to "Chapter N" when the chapter is untitled. */
internal fun chapterTitleAt(chapters: List<VersionChapter>, seconds: Double): String? {
    val chapter = chapters.lastOrNull { it.startSeconds <= seconds } ?: return null
    return chapter.title.ifBlank { "Chapter ${chapter.index + 1}" }
}
