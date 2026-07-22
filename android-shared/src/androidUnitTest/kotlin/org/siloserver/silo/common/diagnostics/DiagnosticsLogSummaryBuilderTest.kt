package org.siloserver.silo.common.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.siloserver.silo.model.diagnostics.DiagnosticsLogCategory

class DiagnosticsLogSummaryBuilderTest {
    @Test
    fun summaryUsesPhysicalLinesAndDistinctRecognizedCategories() {
        val logs = (
            "{\"cat\":\"playback\",\"msg\":\"ready\"}\n" +
                "malformed\n" +
                "{\"cat\":\"network\",\"msg\":\"request\"}\n" +
                "{\"cat\":\"playback\",\"msg\":\"ended\"}"
            ).encodeToByteArray()

        val summary = DiagnosticsLogSummaryBuilder.build(
            logBytes = logs,
            droppedLines = 3,
            debugLogging = true,
        )

        assertEquals(4, summary.lines)
        assertTrue(summary.bytesGzip > 0)
        assertEquals(listOf(DiagnosticsLogCategory.PLAYBACK, DiagnosticsLogCategory.NETWORK), summary.categories)
        assertEquals(3, summary.droppedLines)
        assertTrue(summary.debugLogging)
    }

    @Test
    fun emptyLogsHaveAnEmptySummary() {
        val summary = DiagnosticsLogSummaryBuilder.build(null, droppedLines = -4, debugLogging = false)

        assertEquals(0, summary.lines)
        assertEquals(0, summary.bytesGzip)
        assertEquals(emptyList(), summary.categories)
        assertEquals(0, summary.droppedLines)
    }
}
