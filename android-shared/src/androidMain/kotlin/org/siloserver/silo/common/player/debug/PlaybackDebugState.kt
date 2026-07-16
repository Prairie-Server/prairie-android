package org.siloserver.silo.common.player.debug

/**
 * Process-wide mirror of the player screen's error banner for
 * [PlaybackDebugReceiver]. Screen-level failures (e.g. a terminal server plan
 * like `no_alternate_version`) never reach the Media3 [androidx.media3.common.Player],
 * which just sits idle — so a scripted test polling player state alone can't
 * distinguish "starting up" from "failed with an on-screen error". The player
 * ViewModels publish their `uiState.error` here; the receiver reports it as
 * `screenError` in the status JSON.
 */
object PlaybackDebugState {
    @Volatile
    var screenError: String? = null

    // The seek bar renders from the ViewModel's uiState, not from the raw
    // Media3 player — a growing transcode manifest makes the two disagree, so
    // scripted tests need the screen's view of position/duration too.
    @Volatile
    var screenPositionSec: Double? = null

    @Volatile
    var screenDurationSec: Double? = null
}
