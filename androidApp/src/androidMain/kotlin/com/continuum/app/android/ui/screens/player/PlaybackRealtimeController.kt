package com.continuum.app.android.ui.screens.player

import com.continuum.app.network.PlaybackRealtimeClient
import com.continuum.app.network.PlaybackRealtimeEvent
import com.continuum.app.playback.PlaybackAction
import com.continuum.app.playback.decidePlaybackAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Binds the per-session playback control socket to [PlayerViewModel] for the
 * lifetime of a player screen. Active whenever a playback [sessionId] exists.
 * Applies remote commands via the VM's remote-control surface and acks/results
 * each one. Socket loss does not interrupt playback — the loop reconnects with
 * capped backoff; hello is sent on the [PlaybackRealtimeEvent.Opened] event so
 * the server marks the connection control-ready (R2).
 */
class PlaybackRealtimeController(
    private val sessionId: String,
    private val client: PlaybackRealtimeClient,
    private val viewModel: PlayerViewModel,
    private val scope: CoroutineScope,
) {
    private companion object {
        const val BACKOFF_START_MS = 2_000L
        const val BACKOFF_MAX_MS = 30_000L
        const val STATUS_COMPLETED = "completed"
        const val STATUS_REJECTED = "rejected"
    }

    fun start() {
        scope.launch {
            var backoff = BACKOFF_START_MS
            while (isActive) {
                try {
                    client.connect(sessionId).collect { event ->
                        when (event) {
                            is PlaybackRealtimeEvent.Opened -> client.sendHello(sessionId)
                            is PlaybackRealtimeEvent.Command -> handleCommand(event)
                            is PlaybackRealtimeEvent.ServerEvent -> handleServerEvent(event)
                            is PlaybackRealtimeEvent.Closed -> { /* fall through to reconnect */ }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e // screen left / sessionId changed — stop the loop
                } catch (_: Throwable) {
                    // A send failed during a close race etc. — don't let the
                    // controller die; fall through to the backoff + reconnect.
                }
                // socket closed / errored; back off and retry.
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(BACKOFF_MAX_MS)
            }
        }
    }

    private suspend fun handleCommand(cmd: PlaybackRealtimeEvent.Command) {
        client.sendAck(sessionId, cmd.commandId)
        val action = decidePlaybackAction(cmd)
        val status = if (action is PlaybackAction.Reject) STATUS_REJECTED else STATUS_COMPLETED
        // Send the result BEFORE applying: a Stop tears the screen down (which
        // cancels this coroutine), so applying first could drop the result.
        client.sendResult(sessionId, cmd.commandId, status)
        applyAction(action)
    }

    private fun applyAction(action: PlaybackAction) {
        when (action) {
            is PlaybackAction.Pause -> viewModel.remotePause()
            is PlaybackAction.Unpause -> viewModel.remoteUnpause()
            is PlaybackAction.TogglePlayPause -> viewModel.remoteTogglePlayPause()
            is PlaybackAction.SeekTo -> viewModel.remoteSeek(action.positionSeconds)
            is PlaybackAction.ShowMessage -> viewModel.remoteDisplayMessage(action.message)
            is PlaybackAction.Stop -> viewModel.remoteStop()
            is PlaybackAction.Ignore, is PlaybackAction.Reject -> Unit
        }
    }

    private fun handleServerEvent(event: PlaybackRealtimeEvent.ServerEvent) {
        when (event.name) {
            "subtitle_ready" -> viewModel.refreshSubtitles()
            // markers_updated / chapter_thumbnail_ready handled by existing flows.
            else -> { /* ignore */ }
        }
    }
}
