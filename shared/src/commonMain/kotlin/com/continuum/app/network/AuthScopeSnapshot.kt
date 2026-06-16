package com.continuum.app.network

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

/** Attribute carrying the [AuthScopeSnapshot] that [ContinuumAuthPlugin] honors. */
val AuthScopeAttributeKey: AttributeKey<AuthScopeSnapshot> = AttributeKey("ContinuumAuthScope")

/** Pin this request to [snapshot]'s scope; [ContinuumAuthPlugin] uses it verbatim. */
fun HttpRequestBuilder.authScope(snapshot: AuthScopeSnapshot) {
    attributes.put(AuthScopeAttributeKey, snapshot)
}
