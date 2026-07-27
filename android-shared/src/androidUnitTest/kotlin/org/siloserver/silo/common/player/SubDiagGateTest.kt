package org.siloserver.silo.common.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubDiagGateTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/common/player/SubtitleDiagnostics.kt",
    ).readText()

    @Test
    fun everyEmitIsBehindTheGate() {
        val emits = Regex("""Log\.[a-z]\(TAG""").findAll(source).count()
        val guards = Regex("""if \(enabled\) Log\.""").findAll(source).count()
        assertTrue(emits > 0)
        assertTrue(guards >= emits)
    }

    @Test
    fun tracingDoesNotShipAtErrorLevel() {
        assertFalse(source.contains("Log.e(TAG"))
    }
}
