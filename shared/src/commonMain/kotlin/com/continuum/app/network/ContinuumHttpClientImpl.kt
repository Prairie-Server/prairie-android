package com.continuum.app.network

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * JSON configuration used for all API serialization/deserialization.
 */
val ContinuumJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * Creates a fully configured Ktor [HttpClient] for Continuum API communication.
 *
 * The client is built on top of the platform-specific engine (OkHttp on Android,
 * Darwin on iOS) provided by [createPlatformHttpClient], and layers on:
 *
 * - **Content Negotiation**: JSON serialization with lenient, unknown-key-tolerant parsing
 * - **Authentication**: Custom [ContinuumAuthPlugin] that manages Bearer tokens,
 *   profile headers, and automatic 401 token refresh
 * - **Logging**: Configurable request/response logging (disabled by default)
 * - **Timeouts**: 30s connect, 60s overall request timeout
 * - **Default Request**: JSON content type
 */
fun createContinuumClient(
    tokenManager: TokenManager,
    deviceMetadataProvider: DeviceMetadataProvider? = null,
): HttpClient {
    val platformClient = createPlatformHttpClient()

    return platformClient.config {
        install(ContentNegotiation) {
            json(ContinuumJson)
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.NONE
        }

        install(ContinuumAuthPlugin) {
            this.tokenManager = tokenManager
            this.deviceMetadataProvider = deviceMetadataProvider
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }

        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }
}
