package org.siloserver.silo.android.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderShellParitySourceTest {
    private val shell = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/reader/ReaderShell.kt",
    ).readText()

    @Test
    fun readerChromeFloatsLikeDedicatedReadingApp() {
        assertTrue(
            shell.contains("FloatingReaderChromeContainer(") &&
                shell.contains("RoundedCornerShape(28.dp)") &&
                shell.contains(".shadow("),
            "Reader chrome should be floating, rounded, and elevated instead of full-width app bars.",
        )
        assertTrue(
            shell.contains("state.displaySettings.readerChromeSurface") &&
                shell.contains("state.displaySettings.readerChromeText"),
            "Reader chrome should follow the active reader theme, not the generic app surface.",
        )
        assertFalse(
            shell.contains(".background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))"),
            "Reader chrome should not regress to rigid full-width Material surface bars.",
        )
    }
}
