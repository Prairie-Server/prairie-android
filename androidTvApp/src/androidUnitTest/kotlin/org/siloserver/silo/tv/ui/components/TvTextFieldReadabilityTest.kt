package org.siloserver.silo.tv.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvTextFieldReadabilityTest {
    @Test
    fun tvOutlinedTextFieldsUseSharedReadableColors() {
        val tvSourceRoot = File("src/androidMain/kotlin/org/siloserver/silo/tv")
        val offenders = tvSourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.name == "TvTextFieldDefaults.kt" }
            .filter { it.readText().contains("OutlinedTextField(") }
            .filterNot { it.readText().contains("tvOutlinedTextFieldColors(") }
            .map { it.relativeTo(File("src/androidMain/kotlin")).path }
            .toList()

        assertTrue(
            offenders.isEmpty(),
            "TV OutlinedTextFields must use tvOutlinedTextFieldColors: $offenders",
        )
    }
}
