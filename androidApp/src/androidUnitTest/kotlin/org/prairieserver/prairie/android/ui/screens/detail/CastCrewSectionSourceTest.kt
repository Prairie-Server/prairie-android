package org.prairieserver.prairie.android.ui.screens.detail

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CastCrewSectionSourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/detail/CastCrewSection.kt",
    ).readText()

    @Test
    fun castPortraitImageFillsCircularFrame() {
        assertTrue(source.contains("ThumbhashImage("))
        assertTrue(
            source.contains(".fillMaxSize()"),
            "Cast portraits must fill the fixed circular frame so empty image placeholders cannot collapse.",
        )
        assertFalse(
            source.contains(".fillMaxWidth()\n                    .clip(CircleShape)"),
            "Width-only image sizing leaves the placeholder with no stable height.",
        )
    }
}
