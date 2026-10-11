package org.prairieserver.prairie.android.profiles

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileLaunchActionTest {

    @Test
    fun `asks who's watching only when the setting asks and the server can be used`() {
        assertEquals(
            ProfileLaunchAction.KeepProfile,
            profileLaunchAction(requiresSelection = false, startsOfflineOnDownloads = false),
        )
        assertEquals(
            ProfileLaunchAction.KeepProfile,
            profileLaunchAction(requiresSelection = false, startsOfflineOnDownloads = true),
        )
        assertEquals(
            ProfileLaunchAction.AskWhoIsWatching,
            profileLaunchAction(requiresSelection = true, startsOfflineOnDownloads = false),
        )
    }

    @Test
    fun `offline with downloads keeps the profile instead of stranding the picker`() {
        assertEquals(
            ProfileLaunchAction.KeepProfileOffline,
            profileLaunchAction(requiresSelection = true, startsOfflineOnDownloads = true),
        )
    }
}
