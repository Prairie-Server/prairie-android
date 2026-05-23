package com.continuum.app.network

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*

/**
 * Android platform HttpClient using OkHttp engine.
 * Agent 2 will expand this with full configuration.
 */
actual fun createPlatformHttpClient(): HttpClient {
    return HttpClient(OkHttp)
}
