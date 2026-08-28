package org.prairieserver.prairie.network.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.skipPrairieAuth

@Serializable
data class BrandingStatus(
    @SerialName("server_name")
    val serverName: String? = null,
)

open class BrandingApi(private val client: HttpClient) {
    open suspend fun getBranding(): ApiResult<BrandingStatus> = safeApiCall {
        client.get("/api/v1/theme/branding") {
            // Public identity probe, like checkHealth() which it replaced as
            // the primary source of a server's display name. Without this it
            // carries a bearer it never needed, so a dead session would fail
            // it and silently fall back to the compatibility name this
            // endpoint exists to stop using.
            skipPrairieAuth()
            timeout {
                connectTimeoutMillis = BRANDING_TIMEOUT_MS
                requestTimeoutMillis = BRANDING_TIMEOUT_MS
                socketTimeoutMillis = BRANDING_TIMEOUT_MS
            }
        }
    }

    private companion object {
        const val BRANDING_TIMEOUT_MS = 6_000L
    }
}
