package com.continuum.app.android.ui.navigation

import com.continuum.app.model.navigation.MediaMode
import com.continuum.app.model.navigation.MediaModeCapabilities

val Tab.isUtilityTab: Boolean
    get() = this == Tab.Downloads

fun tabForMediaMode(mode: MediaMode): Tab = when (mode) {
    MediaMode.Video -> Tab.Video
    MediaMode.Audio -> Tab.Audio
    MediaMode.Reading -> Tab.Reading
}

fun visibleMobileTabs(
    capabilities: MediaModeCapabilities,
    showDownloads: Boolean,
): List<Tab> = buildList {
    capabilities.mobileModes().forEach { add(tabForMediaMode(it)) }
    if (showDownloads) add(Tab.Downloads)
}

fun fallbackMobileTab(
    visibleTabs: List<Tab>,
    defaultTab: Tab,
): Tab? {
    if (defaultTab in visibleTabs) return defaultTab
    return visibleTabs.firstOrNull { !it.isUtilityTab } ?: visibleTabs.firstOrNull()
}
