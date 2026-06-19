package com.continuum.app.tv

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class MainTvActivityStartupSourceTest {
    private val source = File(
        "src/androidMain/kotlin/com/continuum/app/tv/MainTvActivity.kt",
    ).readText()

    @Test
    fun splashBranchIsWrappedInFocusableContainerToPreventInputDispatchTimeoutAnr() {
        assertTrue(
            source.contains("focusable()"),
            "Splash branch must use .focusable() so the activity always has a focused window " +
                "during startup — without it, D-pad/Back during the splash causes an ANR.",
        )
        assertTrue(
            source.contains("FocusRequester"),
            "Splash branch must use FocusRequester to programmatically grab focus on entry.",
        )
        assertTrue(
            source.contains("onPreviewKeyEvent"),
            "Splash branch must use onPreviewKeyEvent to consume key events and prevent " +
            "input-dispatch-timeout ANR while the splash is visible.",
        )
    }

    @Test
    fun startupRequiresTokenManagerProfileIdBeforeEnteringMain() {
        assertTrue(
            source.contains("val profileId = tokenManager.getProfileId()"),
            "Startup must require the same TokenManager profile id used by request headers.",
        )
        assertTrue(
            !source.contains("activeEntry.profileId ?: tokenManager.getProfileId()"),
            "A stale registry profile id cannot route to Main because requests would lack X-Profile-Id.",
        )
    }
}
