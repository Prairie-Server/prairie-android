package org.prairieserver.prairie.android.ui.screens.reader.reflow

/** Book-level progress estimate from per-section text lengths, so we never
 *  pre-render unseen sections. */
class SectionWeights(private val approxChars: List<Int>) {
    private val total = approxChars.sum().coerceAtLeast(1)
    private val cumulativeBefore = IntArray(approxChars.size).also {
        var acc = 0
        for (i in approxChars.indices) { it[i] = acc; acc += approxChars[i] }
    }
    fun bookProgression(sectionIndex: Int, pageProgression: Double): Double {
        if (approxChars.isEmpty()) return pageProgression.coerceIn(0.0, 1.0)
        val i = sectionIndex.coerceIn(0, approxChars.lastIndex)
        val before = cumulativeBefore[i].toDouble() / total
        val weight = approxChars[i].toDouble() / total
        // Fall back to raw page progression for zero-length sections.
        val span = if (weight <= 0.0 && approxChars.size == 1) 1.0 else weight
        return (before + span * pageProgression.coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)
    }
}
