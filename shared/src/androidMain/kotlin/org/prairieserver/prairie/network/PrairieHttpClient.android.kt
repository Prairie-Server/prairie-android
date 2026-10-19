package org.prairieserver.prairie.network

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import org.prairieserver.prairie.network.localroute.localRoute

/**
 * Android platform HttpClient using the OkHttp engine.
 *
 * Concurrency tuning and the shared connection pool live in [PrairieOkHttp]; the
 * dispatcher is built fresh per client because Ktor's OkHttp engine derives a
 * new `OkHttpClient` for each Ktor client (force-resetting the dispatcher in
 * the process), and per-client dispatchers keep API, diagnostics, and image
 * traffic from queueing behind each other.
 *
 * Every client can take the active server's local route
 * ([org.prairieserver.prairie.network.localroute.LocalRouteInterceptor]).
 */
actual fun createPlatformHttpClient(retryOnConnectionFailure: Boolean): HttpClient {
    return HttpClient(OkHttp) {
        engine {
            config {
                dispatcher(PrairieOkHttp.newDispatcher())
                connectionPool(PrairieOkHttp.connectionPool)
                retryOnConnectionFailure(retryOnConnectionFailure)
                localRoute()
            }
        }
    }
}
