package com.continuum.app.tv.ui.theme

import androidx.compose.ui.unit.dp

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
