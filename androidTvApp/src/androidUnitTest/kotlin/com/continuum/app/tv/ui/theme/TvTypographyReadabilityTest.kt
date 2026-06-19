package com.continuum.app.tv.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvTypographyReadabilityTest {
    private val source = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/theme/Type.kt",
    ).readText()

    @Test
    fun sharedTvTypographyAvoidsTinyTenFootText() {
        assertFalse(source.contains("fontSize = 9.sp"))
        assertFalse(source.contains("fontSize = 10.sp"))
        assertFalse(source.contains("fontSize = 11.sp"))
        assertFalse(source.contains("fontSize = 12.sp"))
        assertFalse(source.contains("fontSize = 13.sp"))
        assertFalse(source.contains("fontSize = 14.sp"))
        assertFalse(source.contains("fontSize = 15.sp"))
        assertTrue(source.contains("titleSmall = TextStyle("))
        assertTrue(source.contains("fontSize = 18.sp"))
        assertTrue(source.contains("bodySmall = TextStyle("))
        assertTrue(source.contains("fontSize = 16.sp"))
        assertTrue(source.contains("labelSmall = TextStyle("))
        assertTrue(source.contains("sectionEyebrow = TextStyle("))
    }

    @Test
    fun sharedTvTypographyUsesNeutralLetterSpacing() {
        assertFalse(source.contains("letterSpacing = (-"))
    }

    @Test
    fun tvScreensAvoidHardcodedTinyTextOutsideTheTheme() {
        val tinyFontPattern = Regex("""fontSize\s*=\s*(9|10|11|12|13|14|15)\.sp""")
        val tvOsMappedSmallTextFiles = setOf(
            "com/continuum/app/tv/ui/components/TvAnchoredSelectorMenu.kt",
            "com/continuum/app/tv/ui/components/TvEpisodeCard.kt",
            "com/continuum/app/tv/ui/components/TvFullScreenPicker.kt",
            "com/continuum/app/tv/ui/components/TvOptionDialog.kt",
            "com/continuum/app/tv/ui/components/TvPinEntryDialog.kt",
            "com/continuum/app/tv/ui/components/TvTextInputDialog.kt",
            "com/continuum/app/tv/ui/screens/detail/TvCastCrewSection.kt",
            "com/continuum/app/tv/ui/screens/detail/TvDetailEpisodeRail.kt",
            "com/continuum/app/tv/ui/screens/detail/TvDetailFactsTable.kt",
            "com/continuum/app/tv/ui/screens/detail/TvDetailHero.kt",
            "com/continuum/app/tv/ui/screens/detail/TvMediaInfoDialog.kt",
            "com/continuum/app/tv/ui/screens/detail/TvDetailSectionHeader.kt",
            "com/continuum/app/tv/ui/screens/people/TvPersonDetailScreen.kt",
            "com/continuum/app/tv/ui/screens/player/TvHoldSeekIndicator.kt",
            "com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt",
            "com/continuum/app/tv/ui/screens/profiles/TvProfileForm.kt",
            "com/continuum/app/tv/ui/screens/settings/TvSettingsScreen.kt",
        )
        val offenders = File("src/androidMain/kotlin/com/continuum/app/tv")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.invariantSeparatorsPath.endsWith("/ui/theme/Type.kt") }
            .filterNot { file ->
                file.relativeTo(File("src/androidMain/kotlin")).invariantSeparatorsPath in tvOsMappedSmallTextFiles
            }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (tinyFontPattern.containsMatchIn(line)) {
                        "${file.relativeTo(File("src/androidMain/kotlin")).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertTrue(
            offenders.isEmpty(),
            "TV screens should use shared readable typography instead of hardcoded tiny font sizes:\n" +
                offenders.joinToString(separator = "\n"),
        )
    }
}
