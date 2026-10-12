package org.prairieserver.prairie.android.profiles

/** What the phone does about Profile Selection when Prairie opens or returns. */
enum class ProfileLaunchAction {
    /** Keep the current profile; Prairie is in use again. */
    KeepProfile,

    /** Clear the current profile and show Who's Watching. */
    AskWhoIsWatching,

    /**
     * The setting asks, but the server cannot be reached and this profile
     * has downloads. Who's Watching cannot load profiles or check a PIN
     * offline, so keep the profile and its downloads usable, and ask once
     * the server is back.
     */
    KeepProfileOffline,
}

/**
 * The phone's launch and return decision. [requiresSelection] is the shared
 * Profile Selection rule; [startsOfflineOnDownloads] says the server cannot be
 * used and the active profile has local downloads.
 */
fun profileLaunchAction(requiresSelection: Boolean, startsOfflineOnDownloads: Boolean): ProfileLaunchAction =
    when {
        !requiresSelection -> ProfileLaunchAction.KeepProfile
        startsOfflineOnDownloads -> ProfileLaunchAction.KeepProfileOffline
        else -> ProfileLaunchAction.AskWhoIsWatching
    }
