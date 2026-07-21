package org.siloserver.silo.tv.ui.screens.detail

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvDetailPlaybackSelectionSourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvItemDetailScreen.kt",
    ).readText()
    private val viewModelSource = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvItemDetailViewModel.kt",
    ).readText()

    @Test
    fun playActionKeepsAutoUnpinnedUnlessTrackOverrideIsSelected() {
        assertTrue(
            source.contains("state.preferredQuality,"),
            "Auto display resolution must observe the same preferred quality used by playback startup.",
        )
        assertTrue(
            source.contains("preferredQuality = state.preferredQuality"),
            "Auto display resolution must pass the user's preferred quality into version selection.",
        )
        assertTrue(source.contains("val selectedFileId = selectedVersion?.fileId"))
        assertTrue(source.contains("val hasTrackOverride = selectorAudioIndex != null || selectorSubtitleIndex != null"))
        assertTrue(source.contains("val playFileId = selectorSelectedFileId ?: selectedFileId.takeIf { hasTrackOverride }"))
        assertTrue(
            source.contains("playContentId, playFileId,"),
            "Track overrides should pin the displayed file id so selected track indexes match the displayed version.",
        )
        assertFalse(
            source.contains("playContentId, selectedFileId,"),
            "The display fallback must not be sent directly; Auto should stay unpinned unless a track override exists.",
        )
    }

    @Test
    fun nextUpSelectorsUseSessionAndDurableTrackPersistence() {
        assertTrue(viewModelSource.contains("rememberNextUpTrackSelection()"))
        assertTrue(viewModelSource.contains("persistNextUpTrackSelection()"))
        assertTrue(viewModelSource.contains("restoreNextUpTrackSelection(episodeContentId"))
        val restoreBlock = viewModelSource
            .substringAfter("private fun restoreNextUpTrackSelection")
            .substringBefore("private fun seedPersistedNextUpTrackSelection")
        assertTrue(restoreBlock.contains("seedPersistedNextUpTrackSelection(episodeContentId, detail)"))
        assertTrue(restoreBlock.substringBefore("seedPersistedNextUpTrackSelection").contains("return").not())
    }
}
