package com.continuum.app.android.ui.navigation

import com.continuum.app.model.navigation.MediaMode
import com.continuum.app.model.navigation.MediaModeCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobileMediaTabsTest {

    // Apple/web-aligned shell: Home · Libraries · For You, independent of which
    // media types the libraries contain. Library content (video / audio /
    // reading) is reached through the Libraries picker. Downloads only appears
    // when the user has downloads.
    private val base = listOf(Tab.Home, Tab.Libraries, Tab.ForYou)

    @Test
    fun fixedTabsAppendDownloadsWhenPresent() {
        val tabs = visibleMobileTabs(
            capabilities = MediaModeCapabilities(listOf(MediaMode.Video)),
            showDownloads = true,
        )

        assertEquals(base + Tab.Downloads, tabs)
    }

    @Test
    fun tabsAreFixedRegardlessOfLibraryTypes() {
        assertEquals(
            base,
            visibleMobileTabs(MediaModeCapabilities(listOf(MediaMode.Audio)), showDownloads = false),
        )
        assertEquals(
            base,
            visibleMobileTabs(MediaModeCapabilities(listOf(MediaMode.Reading)), showDownloads = false),
        )
        assertEquals(
            base,
            visibleMobileTabs(MediaModeCapabilities(emptyList()), showDownloads = false),
        )
    }

    @Test
    fun downloadsStaysHiddenWhenNoDownloadsExist() {
        val tabs = visibleMobileTabs(
            capabilities = MediaModeCapabilities(listOf(MediaMode.Video, MediaMode.Audio)),
            showDownloads = false,
        )

        assertEquals(base, tabs)
        assertFalse(Tab.Downloads in tabs)
    }

    @Test
    fun choosesFirstVisibleMediaTabBeforeDownloads() {
        assertEquals(
            Tab.Home,
            fallbackMobileTab(
                visibleTabs = listOf(Tab.Home, Tab.Libraries, Tab.Downloads),
                defaultTab = Tab.ForYou,
            ),
        )
    }

    @Test
    fun keepsCurrentTabWhenStillVisible() {
        assertEquals(
            Tab.Downloads,
            fallbackMobileTab(
                visibleTabs = listOf(Tab.Home, Tab.Downloads),
                defaultTab = Tab.Downloads,
            ),
        )
        assertTrue(Tab.Downloads.isUtilityTab)
    }
}
