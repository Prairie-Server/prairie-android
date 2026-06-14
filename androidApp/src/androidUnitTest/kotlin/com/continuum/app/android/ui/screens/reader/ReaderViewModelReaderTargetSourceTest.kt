package com.continuum.app.android.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderViewModelReaderTargetSourceTest {
    @Test
    fun viewModelUsesReaderTargetSelectionInsteadOfInAppOnlySelection() {
        val source = java.io.File(
            "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt",
        ).readText()

        assertTrue(source.contains("chooseReaderVersion("))
        assertFalse(source.contains("chooseEbookVersion("))
    }

    @Test
    fun externalOnlyTargetsRemainVisibleInReaderState() {
        val source = java.io.File(
            "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt",
        ).readText()

        assertTrue(source.contains("EbookReadMode.ExternalOnly"))
        assertTrue(source.contains("readMode = target.support.readMode"))
        assertTrue(source.contains("Download this original to open it with another reader."))
    }
}
