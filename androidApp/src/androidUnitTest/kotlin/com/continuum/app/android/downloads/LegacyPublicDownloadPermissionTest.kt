package com.continuum.app.android.downloads

import android.os.Build
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyPublicDownloadPermissionTest {
    @Test
    fun `legacy public download permission is required on Android 7 through 9`() {
        assertTrue(requiresLegacyPublicDownloadPermission(Build.VERSION_CODES.N))
        assertTrue(requiresLegacyPublicDownloadPermission(Build.VERSION_CODES.N_MR1))
        assertTrue(requiresLegacyPublicDownloadPermission(Build.VERSION_CODES.O))
        assertTrue(requiresLegacyPublicDownloadPermission(Build.VERSION_CODES.O_MR1))
        assertTrue(requiresLegacyPublicDownloadPermission(Build.VERSION_CODES.P))
    }

    @Test
    fun `legacy public download permission is not required on scoped storage devices`() {
        assertFalse(requiresLegacyPublicDownloadPermission(Build.VERSION_CODES.Q))
        assertFalse(requiresLegacyPublicDownloadPermission(Build.VERSION_CODES.TIRAMISU))
    }
}
