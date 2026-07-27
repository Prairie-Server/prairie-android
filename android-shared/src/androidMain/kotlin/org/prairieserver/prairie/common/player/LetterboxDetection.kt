package org.prairieserver.prairie.common.player

import kotlin.math.min
import kotlin.math.roundToInt

data class LetterboxInsets(
    val topFraction: Float,
    val bottomFraction: Float,
) {
    val isDetected: Boolean
        get() = topFraction > 0f || bottomFraction > 0f

    fun intersect(other: LetterboxInsets): LetterboxInsets = LetterboxInsets(
        topFraction = min(topFraction, other.topFraction),
        bottomFraction = min(bottomFraction, other.bottomFraction),
    )

    companion object {
        val NONE = LetterboxInsets(0f, 0f)
    }
}

internal fun SubtitleVideoRect.insetByLetterbox(insets: LetterboxInsets): SubtitleVideoRect {
    if (!insets.isDetected || height <= 0) return this
    val topInset = (height * insets.topFraction).roundToInt()
    val bottomInset = (height * insets.bottomFraction).roundToInt()
    val remaining = height - topInset - bottomInset
    if (remaining <= 0) return this
    return copy(top = top + topInset, height = remaining)
}

internal fun SubtitleVideoRect.insetByTitleSafe(fraction: Float): SubtitleVideoRect {
    if (fraction <= 0f || width <= 0 || height <= 0) return this
    val horizontal = (width * fraction).roundToInt()
    val vertical = (height * fraction).roundToInt()
    val remainingWidth = width - horizontal * 2
    val remainingHeight = height - vertical * 2
    if (remainingWidth <= 0 || remainingHeight <= 0) return this
    return copy(
        left = left + horizontal,
        top = top + vertical,
        width = remainingWidth,
        height = remainingHeight,
    )
}
