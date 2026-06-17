package com.continuum.app.playback

import com.continuum.app.network.PlaybackCommandNames
import com.continuum.app.network.PlaybackRealtimeEvent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** A resolved player action for a remote command. [Reject] → result status "rejected". */
sealed interface PlaybackAction {
    data object Pause : PlaybackAction
    data object Unpause : PlaybackAction
    data object TogglePlayPause : PlaybackAction
    data class SeekTo(val positionSeconds: Double) : PlaybackAction
    data class ShowMessage(val message: String) : PlaybackAction
    data object Stop : PlaybackAction
    data object Ignore : PlaybackAction
    data object Reject : PlaybackAction
}

/**
 * True for actions that drive the player's transport. These are gated while a
 * Watch Together room is active (the room is authoritative for transport, so an
 * admin command must not desync members); informational actions like
 * [PlaybackAction.ShowMessage] are not transport and are always allowed.
 */
val PlaybackAction.isTransport: Boolean
    get() = this is PlaybackAction.Pause ||
        this is PlaybackAction.Unpause ||
        this is PlaybackAction.TogglePlayPause ||
        this is PlaybackAction.SeekTo ||
        this is PlaybackAction.Stop

/**
 * Pure mapping from a realtime command to a player action. Unsupported or
 * malformed commands map to [PlaybackAction.Reject]; informational commands we
 * accept but do nothing about map to [PlaybackAction.Ignore].
 */
fun decidePlaybackAction(command: PlaybackRealtimeEvent.Command): PlaybackAction {
    // Numeric tokens only: a quoted number ("42") or a non-finite value is NOT
    // a valid position — reject rather than silently seek somewhere wrong.
    fun double(key: String): Double? =
        (command.payload[key] as? JsonPrimitive)
            ?.takeIf { !it.isString }
            ?.doubleOrNull
            ?.takeIf { it.isFinite() }
    fun string(key: String): String? =
        (command.payload[key] as? JsonPrimitive)?.let { if (it.isString) it.content else null }

    return when (command.name) {
        PlaybackCommandNames.Pause -> PlaybackAction.Pause
        PlaybackCommandNames.Unpause -> PlaybackAction.Unpause
        PlaybackCommandNames.PlayPause -> PlaybackAction.TogglePlayPause
        PlaybackCommandNames.Seek ->
            // R3: the issuer may send any of these spellings.
            (double("position_seconds") ?: double("position") ?: double("seconds"))
                ?.let { PlaybackAction.SeekTo(it) } ?: PlaybackAction.Reject
        PlaybackCommandNames.DisplayMessage ->
            string("message")?.let { PlaybackAction.ShowMessage(it) } ?: PlaybackAction.Reject
        PlaybackCommandNames.Stop, PlaybackCommandNames.Terminate -> PlaybackAction.Stop
        // Informational broadcasts we accept but take no client action on.
        PlaybackCommandNames.ServerRestarting,
        PlaybackCommandNames.ServerShuttingDown -> PlaybackAction.Ignore
        // Everything we don't advertise (incl. set_volume, play_media, track
        // commands) is rejected rather than silently no-op'd.
        else -> PlaybackAction.Reject
    }
}
