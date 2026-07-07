package org.siloserver.silo.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.siloserver.silo.network.HomeRealtimeClient
import org.siloserver.silo.network.HomeRealtimeEvent
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.network.homeRefreshTrigger

/**
 * Owns the live-home websocket lifecycle (Apple realtime-updates spec):
 * reconnect with capped backoff, drop foreign-profile user_state events, and
 * coalesce bursts (foreign playback progress every ~10s, catalog scan storms)
 * behind a trailing debounce. Collectors ([refreshSignals]) get one signal
 * per quiet window and refetch Home over REST — the socket never carries
 * authoritative data.
 *
 * The foreground starters own [connect]'s scope: connected only while the
 * app is foregrounded, reconnected on profile switch.
 */
class HomeRealtimeCoordinator(
    private val client: HomeRealtimeClient,
    private val tokenManager: TokenManager,
) {
    private val triggers = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    /** Debounced refetch signal — collect and call the Home refresh. */
    @OptIn(FlowPreview::class)
    val refreshSignals: Flow<Unit> = triggers.debounce(DEBOUNCE_MS)

    fun connect(scope: CoroutineScope): Job = scope.launch {
        var backoffMs = INITIAL_BACKOFF_MS
        while (true) {
            var established = false
            try {
                client.connect().collect { event ->
                    if (!established && event !is HomeRealtimeEvent.Closed) {
                        backoffMs = INITIAL_BACKOFF_MS
                        established = true
                    }
                    if (homeRefreshTrigger(event, tokenManager.getProfileId())) {
                        triggers.tryEmit(Unit)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // fall through to backoff-reconnect
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 2_000L
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
