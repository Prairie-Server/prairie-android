package org.prairieserver.prairie.android.ui.screens.reader

private const val PREFETCH_MIN_FREE_RAM_MB = 80

// Order: for each distance d from 1..radius, emit the page behind then ahead
// (nearest-first), skipping out-of-range indices.
fun comicPrefetchTargets(current: Int, pageCount: Int, radius: Int, freeRamMb: Int): List<Int> {
    if (freeRamMb < PREFETCH_MIN_FREE_RAM_MB || pageCount <= 1) return emptyList()
    val out = mutableListOf<Int>()
    for (d in 1..radius) {
        val behind = current - d
        val ahead = current + d
        if (behind in 0 until pageCount) out += behind
        if (ahead in 0 until pageCount) out += ahead
    }
    return out
}
