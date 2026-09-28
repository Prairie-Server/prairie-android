package org.prairieserver.prairie.network.apiv2

import io.ktor.client.request.HttpRequestBuilder
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.authScope
import org.prairieserver.prairie.network.requirePrairieAuth

/** Managed byte streams require the saved request identity even after token refresh. */
fun HttpRequestBuilder.managedDownloadAuth(scope: AuthScopeSnapshot) {
    authScope(scope)
    requirePrairieAuth()
}
