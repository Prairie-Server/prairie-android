package org.siloserver.silo.network

import io.ktor.client.*

/**
 * Factory for creating the Ktor HttpClient.
 * Implementation provided by Agent 2 in SiloHttpClientImpl.kt.
 */
expect fun createPlatformHttpClient(): HttpClient
