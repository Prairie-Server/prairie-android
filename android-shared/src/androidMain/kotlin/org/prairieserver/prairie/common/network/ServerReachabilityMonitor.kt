package org.prairieserver.prairie.common.network

import android.os.SystemClock
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.HealthApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ServerReachabilityStatus {
    Unknown,
    Reachable,
    Unreachable,
}

data class ServerReachabilityState(
    val status: ServerReachabilityStatus = ServerReachabilityStatus.Unknown,
    val lastCheckedAtMs: Long? = null,
    val message: String? = null,
) {
    val canUseServer: Boolean
        get() = status != ServerReachabilityStatus.Unreachable
}

/**
 * Foreground server liveness monitor.
 *
 * Device connectivity only proves the network path exists; it does not prove
 * the configured Prairie origin is alive. This monitor probes `/api/v1/health`
 * while the app is foregrounded and treats only a decoded health payload as
 * reachable, so proxy/tunnel error pages cannot masquerade as a healthy server.
 */
class ServerReachabilityMonitor(
    private val healthApi: HealthApi,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val onServerReconnected: suspend () -> Unit = {},
) {
    private val _state = MutableStateFlow(ServerReachabilityState())
    val state: StateFlow<ServerReachabilityState> = _state.asStateFlow()

    private val probeLock = Mutex()
    private var foregroundJob: Job? = null

    fun startForeground() {
        if (foregroundJob?.isActive == true) return
        foregroundJob = scope.launch {
            while (isActive) {
                val probed = probeNow()
                val nextDelay = if (probed.status == ServerReachabilityStatus.Unreachable) {
                    UNREACHABLE_PROBE_INTERVAL_MS
                } else {
                    REACHABLE_PROBE_INTERVAL_MS
                }
                delay(nextDelay)
            }
        }
    }

    fun stopForeground() {
        foregroundJob?.cancel()
        foregroundJob = null
    }

    suspend fun retryNow(): ServerReachabilityState = probeNow()

    private suspend fun probeNow(): ServerReachabilityState = probeLock.withLock {
        val wasUnreachable = _state.value.status == ServerReachabilityStatus.Unreachable
        val result = healthApi.checkHealth()
        val next = when (result) {
            is ApiResult.Success -> ServerReachabilityState(
                status = ServerReachabilityStatus.Reachable,
                lastCheckedAtMs = nowMs(),
            )
            is ApiResult.Error -> ServerReachabilityState(
                status = ServerReachabilityStatus.Unreachable,
                lastCheckedAtMs = nowMs(),
                message = result.message.ifBlank { result.error.ifBlank { "Server is offline" } },
            )
            is ApiResult.NetworkError -> ServerReachabilityState(
                status = ServerReachabilityStatus.Unreachable,
                lastCheckedAtMs = nowMs(),
                message = result.exception.message ?: "Server is offline",
            )
        }
        _state.value = next
        if (wasUnreachable && next.status == ServerReachabilityStatus.Reachable) {
            onServerReconnected()
        }
        next
    }

    companion object {
        const val REACHABLE_PROBE_INTERVAL_MS: Long = 20_000L
        const val UNREACHABLE_PROBE_INTERVAL_MS: Long = 8_000L
    }
}
