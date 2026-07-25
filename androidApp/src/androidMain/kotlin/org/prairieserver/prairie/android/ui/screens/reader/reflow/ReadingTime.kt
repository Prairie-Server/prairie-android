package org.prairieserver.prairie.android.ui.screens.reader.reflow
import kotlin.math.roundToInt
private const val WORDS_PER_MINUTE = 200.0
private const val CHARS_PER_WORD = 5.5
fun estimateMinutesRemaining(totalChars: Int, bookProgression: Double): Int {
    val remainingFraction = (1.0 - bookProgression).coerceIn(0.0, 1.0)
    val remainingWords = (totalChars * remainingFraction) / CHARS_PER_WORD
    return (remainingWords / WORDS_PER_MINUTE).roundToInt().coerceAtLeast(0)
}
