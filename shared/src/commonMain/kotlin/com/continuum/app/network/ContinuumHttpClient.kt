package com.continuum.app.network

import io.ktor.client.*

/**
 * Factory for creating the Ktor HttpClient.
 * Implementation provided by Agent 2 in ContinuumHttpClientImpl.kt.
 */
expect fun createPlatformHttpClient(): HttpClient
