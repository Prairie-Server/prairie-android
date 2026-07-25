package org.prairieserver.prairie.common.network

import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.HealthApi
import org.prairieserver.prairie.network.api.HealthStatus
import io.ktor.client.HttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ServerReachabilityMonitorTest {

    @Test
    fun `unknown status is optimistic`() = runTest {
        val monitor = ServerReachabilityMonitor(
            healthApi = FakeHealthApi(),
            scope = backgroundScope,
        )

        assertEquals(ServerReachabilityStatus.Unknown, monitor.state.value.status)
        assertTrue(monitor.state.value.canUseServer)
    }

    @Test
    fun `startForeground probes immediately and marks reachable on decoded health`() = runTest {
        val health = FakeHealthApi(
            results = ArrayDeque(listOf(ApiResult.Success(HealthStatus(status = "ok")))),
        )
        val monitor = ServerReachabilityMonitor(
            healthApi = health,
            scope = backgroundScope,
            nowMs = { 1_000L },
        )

        monitor.startForeground()
        runCurrent()

        assertEquals(1, health.callCount)
        assertEquals(ServerReachabilityStatus.Reachable, monitor.state.value.status)
        assertTrue(monitor.state.value.canUseServer)
        assertEquals(1_000L, monitor.state.value.lastCheckedAtMs)
    }

    @Test
    fun `gateway error marks server unreachable`() = runTest {
        val health = FakeHealthApi(
            results = ArrayDeque(listOf(ApiResult.Error(503, "unavailable", "origin down"))),
        )
        val monitor = ServerReachabilityMonitor(
            healthApi = health,
            scope = backgroundScope,
        )

        monitor.startForeground()
        runCurrent()

        assertEquals(ServerReachabilityStatus.Unreachable, monitor.state.value.status)
        assertFalse(monitor.state.value.canUseServer)
        assertEquals("origin down", monitor.state.value.message)
    }

    @Test
    fun `foreground loop uses fast cadence while unreachable and refreshes on reconnect`() = runTest {
        var reconnectRefreshes = 0
        val health = FakeHealthApi(
            results = ArrayDeque(
                listOf(
                    ApiResult.NetworkError(RuntimeException("socket closed")),
                    ApiResult.Success(HealthStatus(status = "ok")),
                ),
            ),
        )
        val monitor = ServerReachabilityMonitor(
            healthApi = health,
            scope = backgroundScope,
            onServerReconnected = { reconnectRefreshes++ },
        )

        monitor.startForeground()
        runCurrent()
        assertEquals(ServerReachabilityStatus.Unreachable, monitor.state.value.status)
        assertEquals(1, health.callCount)

        advanceTimeBy(ServerReachabilityMonitor.UNREACHABLE_PROBE_INTERVAL_MS)
        runCurrent()

        assertEquals(2, health.callCount)
        assertEquals(ServerReachabilityStatus.Reachable, monitor.state.value.status)
        assertEquals(1, reconnectRefreshes)
    }

    @Test
    fun `foreground loop uses slower cadence while reachable`() = runTest {
        val health = FakeHealthApi(
            results = ArrayDeque(
                listOf(
                    ApiResult.Success(HealthStatus(status = "ok")),
                    ApiResult.Success(HealthStatus(status = "ok")),
                ),
            ),
        )
        val monitor = ServerReachabilityMonitor(
            healthApi = health,
            scope = backgroundScope,
        )

        monitor.startForeground()
        runCurrent()
        assertEquals(1, health.callCount)

        advanceTimeBy(ServerReachabilityMonitor.REACHABLE_PROBE_INTERVAL_MS - 1)
        runCurrent()
        assertEquals(1, health.callCount)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, health.callCount)
    }
}

private class FakeHealthApi(
    private val results: ArrayDeque<ApiResult<HealthStatus>> = ArrayDeque(),
) : HealthApi(client = HttpClient()) {
    var callCount = 0

    override suspend fun checkHealth(): ApiResult<HealthStatus> {
        callCount++
        return results.removeFirstOrNull() ?: ApiResult.Success(HealthStatus(status = "ok"))
    }
}
