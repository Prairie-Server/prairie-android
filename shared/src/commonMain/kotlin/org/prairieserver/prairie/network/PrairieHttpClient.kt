package org.prairieserver.prairie.network

import io.ktor.client.*

/**
 * Factory for creating the Ktor HttpClient.
 * Implementation provided by Agent 2 in PrairieHttpClientImpl.kt.
 */
expect fun createPlatformHttpClient(): HttpClient
