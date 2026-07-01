package org.siloserver.silo.network

import io.ktor.client.plugins.api.*

/**
 * Ktor plugin that attaches auth headers to every request.
 * Full implementation provided by Agent 2 in AuthInterceptorImpl.kt.
 */
val SiloAuth = createClientPlugin("SiloAuth") {
    // Stub - Agent 2 provides the real implementation
}
