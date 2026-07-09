package org.siloserver.silo.android.ui.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared user-facing formatters. Single source of truth so the
 * downloads, player, and detail surfaces can't drift apart.
 */

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val index = digitGroups.coerceAtMost(units.size - 1)
    val value = bytes / Math.pow(1024.0, index.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[index])
}

/**
 * Formats a duration in seconds as H:MM:SS, or M:SS under an hour.
 * Truncates sub-second values; NaN and negatives render as 0:00.
 */
internal fun formatClockTime(seconds: Double): String {
    val total = if (seconds.isNaN()) 0L else seconds.toLong().coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Cached "MMM d, yyyy" formatter; building one per call is wasteful when it
 *  runs per card bind in a date-sorted grid. */
private val cardDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

/** "Jul 8, 2026" caption for date-sorted grids; null when the item has no date. */
internal fun formatCardDate(iso: String?): String? {
    val datePart = iso?.take(10)?.takeIf { it.length == 10 } ?: return null
    return runCatching {
        LocalDate.parse(datePart).format(cardDateFormatter)
    }.getOrNull()
}
