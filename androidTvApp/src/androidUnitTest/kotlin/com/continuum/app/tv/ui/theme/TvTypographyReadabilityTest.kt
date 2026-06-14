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
        val offenders = File("src/androidMain/kotlin/com/continuum/app/tv")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.invariantSeparatorsPath.endsWith("/ui/theme/Type.kt") }
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
