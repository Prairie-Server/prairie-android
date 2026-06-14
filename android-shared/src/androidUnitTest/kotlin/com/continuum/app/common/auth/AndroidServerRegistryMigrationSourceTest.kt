package com.continuum.app.common.auth

import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidServerRegistryMigrationSourceTest {
    private val source = java.io.File(
        "../shared/src/androidMain/kotlin/com/continuum/app/network/AndroidServerRegistry.kt",
    ).readText()

    @Test
    fun migrationClearsLegacyPlaintextAuthPrefsOnEveryExitPath() {
        val callCount = Regex("""clearLegacyAuthPrefs\(context\)""")
            .findAll(source)
            .count()

        assertTrue(
            callCount >= 4,
            "AndroidServerRegistry migration must scrub legacy plaintext auth prefs after importing or skipping them",
        )
    }
}
