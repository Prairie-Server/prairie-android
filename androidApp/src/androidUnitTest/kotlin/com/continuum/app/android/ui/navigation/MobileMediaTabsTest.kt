package com.continuum.app.android.ui.navigation

import com.continuum.app.model.navigation.MediaMode
import com.continuum.app.model.navigation.MediaModeCapabilities
import com.continuum.app.model.navigation.mobileMediaModeCapabilities
import com.continuum.app.model.personal.UserLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobileMediaTabsTest {
    @Test
    fun videoOnlyAccountShowsVideoAndDownloads() {
        val tabs = visibleMobileTabs(
            capabilities = MediaModeCapabilities(listOf(MediaMode.Video)),
            showDownloads = true,
        )

        assertEquals(listOf(Tab.Video, Tab.Downloads), tabs)
    }

    @Test
    fun audiobookOnlyAccountShowsAudioAndDownloads() {
        val tabs = visibleMobileTabs(
            capabilities = listOf(userLibrary(type = "audiobook")).mobileMediaModeCapabilities(),
            showDownloads = true,
        )

        assertEquals(listOf(Tab.Audio, Tab.Downloads), tabs)
        assertFalse(Tab.Reading in tabs)
    }

    @Test
    fun musicOnlyAccountShowsAudioAndDownloads() {
        val tabs = visibleMobileTabs(
            capabilities = listOf(userLibrary(type = "music")).mobileMediaModeCapabilities(),
            showDownloads = true,
        )

        assertEquals(listOf(Tab.Audio, Tab.Downloads), tabs)
    }

    @Test
    fun readingOnlyAccountShowsReadingAndDownloads() {
        val tabs = visibleMobileTabs(
            capabilities = MediaModeCapabilities(listOf(MediaMode.Reading)),
            showDownloads = true,
        )

        assertEquals(listOf(Tab.Reading, Tab.Downloads), tabs)
    }

    @Test
    fun allModesKeepStableOrder() {
        val tabs = visibleMobileTabs(
            capabilities = MediaModeCapabilities(listOf(MediaMode.Video, MediaMode.Audio, MediaMode.Reading)),
            showDownloads = true,
        )

        assertEquals(listOf(Tab.Video, Tab.Audio, Tab.Reading, Tab.Downloads), tabs)
    }

    @Test
    fun downloadsCanStayHiddenWhenNoDownloadsExist() {
        val tabs = visibleMobileTabs(
            capabilities = MediaModeCapabilities(listOf(MediaMode.Video, MediaMode.Audio)),
            showDownloads = false,
        )

        assertEquals(listOf(Tab.Video, Tab.Audio), tabs)
        assertFalse(Tab.Downloads in tabs)
    }

    @Test
    fun choosesFirstVisibleMediaTabBeforeDownloads() {
        assertEquals(
            Tab.Audio,
            fallbackMobileTab(
                visibleTabs = listOf(Tab.Audio, Tab.Downloads),
                defaultTab = Tab.Video,
            ),
        )
    }

    @Test
    fun keepsCurrentTabWhenStillVisible() {
        assertEquals(
            Tab.Downloads,
            fallbackMobileTab(
                visibleTabs = listOf(Tab.Audio, Tab.Downloads),
                defaultTab = Tab.Downloads,
            ),
        )
        assertTrue(Tab.Downloads.isUtilityTab)
    }

    private fun userLibrary(type: String): UserLibrary =
        UserLibrary(id = 1, name = "Library", type = type)
}
