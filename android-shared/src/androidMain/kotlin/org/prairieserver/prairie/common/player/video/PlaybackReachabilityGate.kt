package org.prairieserver.prairie.common.player.video

import org.prairieserver.prairie.common.network.ServerReachabilityMonitor
import org.prairieserver.prairie.common.network.ServerReachabilityStatus

/**
 * Pre-play server-reachability decision shared by the phone and TV video
 * starters, so the gate that guards a doomed player launch lives in exactly one
 * place (issue #33 / prairie-apple `ItemDetailView.swift:817-857`).
 */
sealed interface PreplayReachabilityDecision {
    /** Reachable / Unknown status, or a forced attempt — hit the server. */
    data object Proceed : PreplayReachabilityDecision

    /** Server is down but a completed local download exists — play it offline. */
    data object PlayLocalOffline : PreplayReachabilityDecision

    /** Server is down and there is no local copy — surface "Can't reach server". */
    data object ServerUnreachable : PreplayReachabilityDecision
}

/**
 * Full three-way pre-play decision.
 *
 * Unknown passes optimistically (matches Apple): a cold start whose first health
 * probe has not landed yet still attempts playback rather than blocking. This
 * reads the monitor's last cached status only — it never issues a probe, so it
 * cannot block; the fresh re-probe belongs to the UI's Retry action.
 */
suspend fun evaluatePreplayReachability(
    monitor: ServerReachabilityMonitor,
    force: Boolean,
    hasLocalDownload: suspend () -> Boolean,
): PreplayReachabilityDecision {
    if (force) return PreplayReachabilityDecision.Proceed
    return when (monitor.state.value.status) {
        ServerReachabilityStatus.Reachable,
        ServerReachabilityStatus.Unknown -> PreplayReachabilityDecision.Proceed
        ServerReachabilityStatus.Unreachable ->
            if (hasLocalDownload()) {
                PreplayReachabilityDecision.PlayLocalOffline
            } else {
                PreplayReachabilityDecision.ServerUnreachable
            }
    }
}

/**
 * Convenience for callers whose offline/local playback path already runs
 * *before* the starter — the phone player VM's offline-first `tryLocalPlayback`,
 * and TV which has no downloads at all. For them the local branch is moot: by
 * the time an Unreachable status reaches the starter there is no local copy, so
 * only the server decision matters.
 *
 * @return true to proceed to the server, false to short-circuit as unreachable.
 */
suspend fun shouldReachServerForPlayback(
    monitor: ServerReachabilityMonitor,
    force: Boolean,
): Boolean =
    evaluatePreplayReachability(
        monitor = monitor,
        force = force,
        hasLocalDownload = { false },
    ) is PreplayReachabilityDecision.Proceed
