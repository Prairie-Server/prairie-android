package org.siloserver.silo.tv.ui.screens.audiobook

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvAudiobookSleepPanelSourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/audiobook/TvAudiobookSleepPanel.kt",
    ).readText()

    @Test
    fun sleepTimerOptionsUseScrollableListSoEndOfBookIsReachable() {
        assertTrue(source.contains("rememberLazyListState()"))
        assertTrue(source.contains("LazyColumn("))
        assertTrue(source.contains("itemsIndexed(SLEEP_OPTIONS)"))
        assertTrue(source.contains("listState.scrollToItem(focusIndex)"))
        assertTrue(source.contains(".weight(1f)"))
        assertFalse(
            source.contains("SLEEP_OPTIONS.forEachIndexed"),
            "Sleep timer must not render into a fixed column; the bottom options clip on TV.",
        )
    }
}
