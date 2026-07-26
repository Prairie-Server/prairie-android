package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.skipPrairieAuth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthStatus(
    val status: String,
    @SerialName("server_name")
    val serverName: String? = null,
    @SerialName("server_id")
    val serverId: String? = null,
)

open class HealthApi(private val client: HttpClient) {

    open suspend fun checkHealth(): ApiResult<HealthStatus> = safeApiCall {
        client.get("/api/v1/health") {
            timeout {
                connectTimeoutMillis = HEALTH_TIMEOUT_MS
                requestTimeoutMillis = HEALTH_TIMEOUT_MS
                socketTimeoutMillis = HEALTH_TIMEOUT_MS
            }
        }
    }

    /**
     * Absolute-URL health probe for LAN discovery / candidate servers.
     * Skips Prairie auth so credentials for the active server never leak onto
     * an untrusted candidate, and uses a short timeout (~400ms) for scan fan-out.
     */
    open suspend fun checkHealth(serverUrl: String): ApiResult<HealthStatus> = safeApiCall {
        client.get("${serverUrl.trimEnd('/')}/api/v1/health") {
            skipPrairieAuth()
            timeout {
                connectTimeoutMillis = HEALTH_PROBE_TIMEOUT_MS
                requestTimeoutMillis = HEALTH_PROBE_TIMEOUT_MS
                socketTimeoutMillis = HEALTH_PROBE_TIMEOUT_MS
            }
        }
    }

    private companion object {
        const val HEALTH_TIMEOUT_MS = 6_000L
        const val HEALTH_PROBE_TIMEOUT_MS = 400L
    }
}
