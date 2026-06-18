package com.continuum.app.tv.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * tvOS-faithful spacing scale. Values mirror ContinuumTheme.swift for the tvOS
 * branch (~2x of the iPhone scale): 24 / 48 / 60 / 80 dp.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 18.dp
    val xl = 24.dp
    val xxl = 30.dp
    val xxxl = 40.dp

    /** Horizontal content inset — matches tvOS overscan safe-area padding. */
    val safeArea = 40.dp

    /** Top safe area for hero surfaces. */
    val heroTopSafe = 60.dp

    /** Vertical gap between rows on Home and detail screens (tvOS rowSpacing = 60pt). */
    val sectionSpacing = 30.dp

    /** Inset applied around the Infuse-style player HUD panel (A.3). */
    val hudPanelInset = 24.dp

    /** Nominal height reserved for the floating top menu bar (A.1). */
    val topMenuBarHeight = 64.dp

    /** Vertical distance over which the home-hero backdrop fades into the rows (A.2). */
    val heroBackdropFade = 120.dp
}

/**
 * Skyline top-bar chrome metrics, mirrored from `ContinuumTheme.Skyline` in
 * `iosApp/iosApp/Theme/ContinuumTheme.swift`. tvOS values are points at
 * 1920×1080; Android TV chrome renders at the ~960dp layout under the theme's
 * 0.86 density scale, so each tvOS point maps to dp at ~0.5x to preserve the
 * same on-screen proportions. Names match the Swift tokens 1:1 so audits
 * against the iOS source stay mechanical.
 */
object TvSkyline {
    /** Root horizontal inset for chrome and content — tvOS `safeAreaX` (88pt). */
    val safeAreaX = 44.dp

    /** Top bar offset from the screen's top edge — tvOS `barTopInset` (56pt). */
    val barTopInset = 28.dp

    /** Top bar row height — tvOS `barHeight` (64pt). */
    val barHeight = 46.dp

    /** Gap between tab capsules in the bar's center cluster — tvOS `tabSpacing` (8pt). */
    val tabSpacing = 6.dp

    /** Tab label size — tvOS `tabLabelSize` (23pt). */
    val tabLabelSize = 14.sp

    /** Tab capsule horizontal padding — tvOS `tabPaddingHorizontal` (26pt). */
    val tabPaddingHorizontal = 16.dp

    /** Tab capsule vertical padding — tvOS `tabPaddingVertical` (11pt). */
    val tabPaddingVertical = 7.dp

    /** Square hit target of the search button and the avatar — tvOS `barIconSize` (52pt). */
    val barIconSize = 30.dp

    /** Gap between the search button and the avatar — tvOS `barTrailingSpacing` (22pt). */
    val barTrailingSpacing = 12.dp

    /** Wordmark size — tvOS `wordmarkSize` (26pt). */
    val wordmarkSize = 18.sp

    /** Wordmark letter tracking — tvOS `wordmarkTracking` (+0.34 em). */
    val wordmarkTracking = 0.34.em

    /** Bar opacity while focus is down in the content zone — tvOS `barDimmedOpacity`. */
    const val barDimmedOpacity = 0.70f
}

object HeroDimens {
    /** tvOS detail hero is tall enough to hold logo, metadata, overview, facts, and actions. */
    val Height = 600.dp

    /** Home hero matches the Browse-row reveal — shorter than detail. */
    val HomeHeight = 300.dp
}

object RowDimens {
    /** 2:3 poster card. tvOS uses 260×390pt; Android TV maps to 200×300dp at scale. */
    val PosterHeight = 195.dp
    val PosterWidth = 130.dp

    /** 16:9 episode/backdrop card — tvOS 360×200pt → 280×156dp. */
    val BackdropHeight = 100.dp
    val BackdropWidth = 180.dp
}
