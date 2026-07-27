package org.prairieserver.prairie.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {
    @Test
    fun parse_accepts_v_prefix_and_build_metadata() {
        assertEquals(AppVersion(1, 4, 0), AppVersion.parse("v1.4.0"))
        assertEquals(AppVersion(1, 4, 0), AppVersion.parse("1.4.0+2"))
        assertEquals(AppVersion(0, 3, 11), AppVersion.parse("0.3.11"))
    }

    @Test
    fun parse_marks_prerelease() {
        val version = AppVersion.parse("v1.4.0-rc.1")
        assertEquals(AppVersion(1, 4, 0, prerelease = true), version)
    }

    @Test
    fun compare_orders_core_then_prerelease() {
        assertTrue(AppVersion(1, 4, 1) > AppVersion(1, 4, 0))
        assertTrue(AppVersion(1, 4, 0) > AppVersion(1, 4, 0, prerelease = true))
        assertEquals(0, AppVersion(1, 0, 0).compareTo(AppVersion(1, 0, 0)))
    }
}

class AppUpdateStatusResolveTest {
    @Test
    fun resolve_update_available_when_latest_is_newer() {
        val status = resolveAppUpdateStatus(
            currentVersionName = "0.3.11",
            latestVersionName = "v1.4.0",
            releaseUrl = "https://example.com/r",
        )
        val available = assertIs<AppUpdateStatus.UpdateAvailable>(status)
        assertEquals("0.3.11", available.currentVersion)
        assertEquals("1.4.0", available.latestVersion)
        assertEquals("https://example.com/r", available.releaseUrl)
        assertEquals("https://example.com/r", available.changelogUrl)
        assertEquals("Update available", status.statusLabel())
        assertEquals("1.4.0", status.latestVersionLabel())
        assertEquals("https://example.com/r", status.changelogUrlOrNull())
    }

    @Test
    fun resolve_up_to_date_keeps_changelog_url() {
        val same = resolveAppUpdateStatus(
            currentVersionName = "1.4.0",
            latestVersionName = "v1.4.0",
            releaseUrl = "https://example.com/r",
            changelogUrl = "https://example.com/changelog",
        )
        val upToDate = assertIs<AppUpdateStatus.UpToDate>(same)
        assertEquals("Up to date", same.statusLabel())
        assertEquals("https://example.com/changelog", upToDate.changelogUrl)
        assertEquals("https://example.com/changelog", same.changelogUrlOrNull())

        val olderLatest = resolveAppUpdateStatus("1.5.0", "1.4.0", null)
        assertIs<AppUpdateStatus.UpToDate>(olderLatest)
    }

    @Test
    fun resolve_unavailable_on_empty_or_unparseable_latest() {
        assertIs<AppUpdateStatus.Unavailable>(
            resolveAppUpdateStatus("1.0.0", null, null),
        )
        assertIs<AppUpdateStatus.Unavailable>(
            resolveAppUpdateStatus("1.0.0", "not-a-version", null),
        )
        assertNull(resolveAppUpdateStatus("1.0.0", null, null).latestVersionLabel())
        assertNull(resolveAppUpdateStatus("1.0.0", null, null).releaseUrlOrNull())
        assertEquals(
            "https://example.com/releases",
            resolveAppUpdateStatus(
                currentVersionName = "1.0.0",
                latestVersionName = null,
                releaseUrl = null,
                changelogUrl = "https://example.com/releases",
            ).changelogUrlOrNull(),
        )
    }
}
