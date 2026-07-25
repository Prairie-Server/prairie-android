package org.prairieserver.prairie.android.ui.navigation

import org.prairieserver.prairie.model.navigation.MediaModeCapabilities

val Tab.isUtilityTab: Boolean
    get() = this == Tab.Downloads

// Apple-aligned shell: Home · Libraries · For You · Calendar, plus a Downloads
// tab only when the user has downloads. There is no per-media-type tab —
// library content (video / audio / reading) is reached through the Libraries picker.
fun visibleMobileTabs(
    @Suppress("UNUSED_PARAMETER") capabilities: MediaModeCapabilities,
    showDownloads: Boolean,
): List<Tab> = buildList {
    add(Tab.Home)
    add(Tab.Libraries)
    add(Tab.ForYou)
    add(Tab.Calendar)
    if (showDownloads) add(Tab.Downloads)
}

fun fallbackMobileTab(
    visibleTabs: List<Tab>,
    defaultTab: Tab,
): Tab? {
    if (defaultTab in visibleTabs) return defaultTab
    return visibleTabs.firstOrNull { !it.isUtilityTab } ?: visibleTabs.firstOrNull()
}
