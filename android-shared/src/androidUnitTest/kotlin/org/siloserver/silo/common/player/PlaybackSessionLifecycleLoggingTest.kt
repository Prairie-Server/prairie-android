package org.siloserver.silo.common.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackSessionLifecycleLoggingTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/org/siloserver/silo/common/player/PlaybackSessionLifecycle.kt",
    ).readLines()

    @Test
    fun lifecycleLogsDoNotIncludeRawSessionIds() {
        val logLines = source.filter { it.contains("Log.") }

        assertFalse(
            logLines.any { it.contains("staleSessionId") || it.contains("currentSession.sessionId") },
            "Playback lifecycle logs must not include raw playback session identifiers",
        )
    }

    @Test
    fun aNewStartWaitsForAnAsynchronousStopToFinish() {
        val text = source.joinToString("\n")

        assertTrue(text.contains("private var pendingStopJob: Job?"))
        assertTrue(text.contains("private suspend fun awaitPendingStop()"))
        assertTrue(
            text.substringAfter("suspend fun start(params: StartParams)")
                .substringBefore("suspend fun adoptActiveSession(")
                .contains("awaitPendingStop()"),
        )
        assertTrue(
            text.substringAfter("suspend fun adoptActiveSession(")
                .substringBefore("private suspend fun startInternal(")
                .contains("awaitPendingStop()"),
        )
    }
}
