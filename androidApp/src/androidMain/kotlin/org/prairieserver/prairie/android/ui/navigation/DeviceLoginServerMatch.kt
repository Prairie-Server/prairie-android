package org.prairieserver.prairie.android.ui.navigation

import org.prairieserver.prairie.model.server.ServerEntry

/**
 * What to do with a pairing request that names the server which issued it.
 *
 * A pairing code is only meaningful on its own server. The origin used to be
 * discarded and the code looked up against whichever server happened to be
 * active, which normally reported a perfectly valid request as invalid or
 * expired. Refusing that is right — but refusing it *silently* just moves the
 * confusion, so the request is still delivered and the screen explains itself.
 */
sealed interface DeviceLoginServerMatch {
    /** Proceed: the link names this server, or names none. */
    data object Active : DeviceLoginServerMatch

    /** The link belongs to [entry], which the user has but is not using. */
    data class SwitchRequired(val entry: ServerEntry) : DeviceLoginServerMatch

    /** The link names [origin], which is not a server the user has. */
    data class UnknownServer(val origin: String) : DeviceLoginServerMatch
}

/**
 * Resolves [requiredOrigin] against the known servers.
 *
 * [activeServerUrl] null means no server is configured yet — the user is on
 * their way to adding one, so there is nothing to contradict and pairing
 * proceeds through the normal setup gates.
 */
fun deviceLoginServerMatch(
    requiredOrigin: String?,
    activeServerUrl: String?,
    entries: List<ServerEntry>,
): DeviceLoginServerMatch {
    if (requiredOrigin == null) return DeviceLoginServerMatch.Active
    if (activeServerUrl == null) return DeviceLoginServerMatch.Active
    if (deviceLoginOriginMatchesServer(requiredOrigin, activeServerUrl)) {
        return DeviceLoginServerMatch.Active
    }
    val known = entries.firstOrNull { deviceLoginOriginMatchesServer(requiredOrigin, it.url) }
    return known
        ?.let(DeviceLoginServerMatch::SwitchRequired)
        ?: DeviceLoginServerMatch.UnknownServer(requiredOrigin)
}
