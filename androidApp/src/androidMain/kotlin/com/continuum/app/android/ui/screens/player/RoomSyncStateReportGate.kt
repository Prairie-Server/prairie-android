package com.continuum.app.android.ui.screens.player

import kotlin.math.abs

/**
 * Pure state-report cadence/suppression decision used by [RoomSyncController].
 * We emit a `state_report` once per [cadenceMs], EXCEPT inside a +/- [suppressWindowMs]
 * window around a pending transport command's local execute time — reporting our
 * position right as we are about to seek/play would feed the server a stale
 * pre-execute sample and fight the sync barrier.
 *
 * @param nowMs current local (monotonic) clock in ms.
 * @param lastReportMs local time the last state_report was emitted.
 * @param cadenceMs minimum spacing between reports.
 * @param pendingExecuteAtMs local (monotonic) execute time of the next command, or null.
 * @param suppressWindowMs half-width of the +/- suppression window around a pending execute.
 */
fun shouldEmitStateReport(
    nowMs: Long,
    lastReportMs: Long,
    cadenceMs: Long,
    pendingExecuteAtMs: Long?,
    suppressWindowMs: Long,
): Boolean {
    if (nowMs - lastReportMs < cadenceMs) return false
    if (pendingExecuteAtMs != null && abs(nowMs - pendingExecuteAtMs) <= suppressWindowMs) return false
    return true
}
