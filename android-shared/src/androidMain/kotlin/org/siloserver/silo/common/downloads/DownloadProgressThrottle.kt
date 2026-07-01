package org.siloserver.silo.common.downloads

/**
 * Pure time-based throttle for [DownloadWorker] progress reporting.
 *
 * - [Decision.report] gates the per-tick work (`setProgress` + the
 *   repository upsert): at most once per [intervalMs].
 * - [Decision.updateForeground] gates the expensive work (rebuilding the
 *   notification + `createCancelPendingIntent` via `setForeground`):
 *   only when the integer percent actually changed since the last
 *   foreground update.
 *
 * The old gate also fired on every 1 MB of bytes, which on a fast
 * network meant ~100 notification rebuilds per second.
 */
class DownloadProgressThrottle(
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
) {

    data class Decision(
        val report: Boolean,
        val updateForeground: Boolean,
        val percent: Int,
    )

    private var lastReportedMs = -intervalMs
    private var lastForegroundPercent = -1

    fun onBytes(nowMs: Long, written: Long, total: Long): Decision {
        if (nowMs - lastReportedMs < intervalMs) {
            return Decision(report = false, updateForeground = false, percent = lastForegroundPercent)
        }
        lastReportedMs = nowMs
        val percent = percentOf(written, total)
        val updateForeground = percent != lastForegroundPercent
        if (updateForeground) lastForegroundPercent = percent
        return Decision(report = true, updateForeground = updateForeground, percent = percent)
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 200L

        fun percentOf(written: Long, total: Long): Int =
            if (total > 0) ((written * 100) / total).toInt().coerceIn(0, 100) else 0
    }
}
