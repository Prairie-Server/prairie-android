package org.siloserver.silo.network

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.util.AttributeKey

/**
 * An auth scope captured at a point in time, used to **pin** a single API
 * request to a specific server + profile regardless of the globally-active
 * scope (Track B outbox drain). Without this, a background replay started for
 * server A could be sent with server B's headers if the user switches mid-send.
 *
 * Only the *profile* identity is frozen: [profileId] and [profileToken] (the
 * per-server stored profile token, of which there is exactly one — a p1→p2→p1
 * switch would otherwise lose p1's token). The *server* access/refresh tokens
 * are read live by [serverId] at send time, because they are per-server-account
 * (shared across profiles) and rotate on refresh — freezing them would break
 * the second op in a drain after the first triggers a token rotation.
 */
data class AuthScopeSnapshot(
    val serverId: String,
    val profileId: String?,
    val serverUrl: String,
    val profileToken: String?,
)

/** Attribute carrying the [AuthScopeSnapshot] that [SiloAuthPlugin] honors. */
val AuthScopeAttributeKey: AttributeKey<AuthScopeSnapshot> = AttributeKey("SiloAuthScope")

/**
 * Attribute marking a request as intentionally unauthenticated.
 *
 * Used for candidate-server probes before the active server scope is changed:
 * those calls must not leak the current server's bearer/profile credentials or
 * trigger refresh/invalidation against the active account on a candidate 401.
 */
val SkipSiloAuthAttributeKey: AttributeKey<Boolean> = AttributeKey("SiloSkipAuth")

/** Pin this request to [snapshot]'s scope; [SiloAuthPlugin] uses it verbatim. */
fun HttpRequestBuilder.authScope(snapshot: AuthScopeSnapshot) {
    attributes.put(AuthScopeAttributeKey, snapshot)
}

/** Opt this request out of Silo auth/profile headers and auth-refresh handling. */
fun HttpRequestBuilder.skipSiloAuth() {
    attributes.put(SkipSiloAuthAttributeKey, true)
}
