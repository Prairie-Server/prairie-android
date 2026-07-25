package org.prairieserver.prairie.common.diagnostics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import org.prairieserver.prairie.model.diagnostics.DiagnosticsLogCategory
import org.prairieserver.prairie.model.diagnostics.DiagnosticsLogLevel
import org.prairieserver.prairie.model.diagnostics.decodeDiagnosticsLogLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PrairieLogTest {
    private val redactor = DiagnosticsRedactor(knownServerHosts = setOf("secret.example"))

    @After
    fun tearDown() {
        PrairieLog.installSink(null)
        PrairieLog.resetRendererForTests()
        ShadowLog.clear()
    }

    @Test
    fun rendererProducesCanonicalSanitizedJsonLine() {
        val renderer = DiagnosticsLogRenderer(
            redactor = redactor,
            strictAttributeRegistry = true,
            runId = "run-test-1",
            nowEpochMillis = { 1_774_113_749_412L },
        )

        val rendered = renderer.render(
            level = DiagnosticsLogLevel.INFO,
            category = DiagnosticsLogCategory.NETWORK,
            tag = "HTTPClient",
            message = "request completed for https://secret.example/a?token=raw",
            attributes = mapOf(
                "method" to PrairieLogAttribute.Text("GET"),
                "path" to PrairieLogAttribute.Url("https://secret.example/api/v1/items?q=secret"),
                "status" to PrairieLogAttribute.Integer(200),
                "duration_ms" to PrairieLogAttribute.Integer(84),
            ),
        )

        val decoded = decodeDiagnosticsLogLine(assertNotNull(rendered))
        assertEquals("2026-03-21T17:22:29.412Z", decoded.timestamp)
        assertEquals("run-test-1", decoded.run)
        assertEquals(DiagnosticsLogLevel.INFO, decoded.level)
        assertEquals(DiagnosticsLogCategory.NETWORK, decoded.category)
        assertFalse(rendered.contains("secret.example"), rendered)
        assertFalse(rendered.contains("token=raw"), rendered)
        assertFalse(rendered.contains("q=secret"), rendered)
        assertEquals(200L, decoded.attributes?.get("status")?.jsonPrimitive?.content?.toLong())
    }

    @Test
    fun debugRendererRejectsUnregisteredAndMismatchedAttributes() {
        val renderer = DiagnosticsLogRenderer(redactor, strictAttributeRegistry = true)

        assertFailsWith<IllegalArgumentException> {
            renderer.render(
                DiagnosticsLogLevel.INFO,
                DiagnosticsLogCategory.NETWORK,
                "HTTPClient",
                "completed",
                mapOf("body" to PrairieLogAttribute.Text("secret")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            renderer.render(
                DiagnosticsLogLevel.INFO,
                DiagnosticsLogCategory.NETWORK,
                "HTTPClient",
                "completed",
                mapOf("status" to PrairieLogAttribute.Text("200")),
            )
        }
    }

    @Test
    fun strictRendererAcceptsRegisteredSeekPerformanceAttributes() {
        val renderer = DiagnosticsLogRenderer(redactor, strictAttributeRegistry = true)

        val rendered = assertNotNull(
            renderer.render(
                DiagnosticsLogLevel.INFO,
                DiagnosticsLogCategory.PLAYBACK,
                "Media3Analytics",
                "player stats snapshot",
                mapOf(
                    "seek_count" to PrairieLogAttribute.Integer(1),
                    "seek_last_ms" to PrairieLogAttribute.Integer(275),
                    "seek_total_ms" to PrairieLogAttribute.Integer(275),
                    "seek_max_ms" to PrairieLogAttribute.Integer(275),
                ),
            ),
        )

        val attributes = Json.parseToJsonElement(rendered).jsonObject["attrs"]?.jsonObject
        assertEquals(setOf("seek_count", "seek_last_ms", "seek_total_ms", "seek_max_ms"), attributes?.keys)
    }

    @Test
    fun productionRendererDropsInvalidAttributes() {
        val renderer = DiagnosticsLogRenderer(redactor, strictAttributeRegistry = false)

        val rendered = assertNotNull(
            renderer.render(
                DiagnosticsLogLevel.INFO,
                DiagnosticsLogCategory.NETWORK,
                "HTTPClient",
                "completed",
                mapOf(
                    "body" to PrairieLogAttribute.Text("secret"),
                    "status" to PrairieLogAttribute.Text("wrong type"),
                    "method" to PrairieLogAttribute.Text("GET"),
                ),
            ),
        )
        val attrs = Json.parseToJsonElement(rendered).jsonObject["attrs"]?.jsonObject
        assertEquals(setOf("method"), attrs?.keys)
        assertFalse(rendered.contains("secret"), rendered)
        assertFalse(rendered.contains("wrong type"), rendered)
    }

    @Test
    fun allLevelsForwardToLogcatAndOfferOnlySanitizedLines() {
        val lines = mutableListOf<String>()
        PrairieLog.setRendererForTests(
            DiagnosticsLogRenderer(
                redactor = redactor,
                strictAttributeRegistry = true,
                runId = "run-test-2",
                nowEpochMillis = { 1_774_113_749_412L },
            ),
        )
        PrairieLog.installSink { lines += it }

        PrairieLog.v(DiagnosticsLogCategory.OTHER, "Test", "verbose")
        PrairieLog.d(DiagnosticsLogCategory.OTHER, "Test", "debug")
        PrairieLog.i(DiagnosticsLogCategory.LIFECYCLE, "Test", "active", mapOf("state" to PrairieLogAttribute.Text("active")))
        PrairieLog.w(DiagnosticsLogCategory.OTHER, "Test", "warning")
        PrairieLog.e(
            DiagnosticsLogCategory.CRASH,
            "Test",
            "failed",
            attributes = mapOf("source" to PrairieLogAttribute.Text("ueh")),
            throwable = IllegalStateException("Authorization: Bearer secret-token user@example.com"),
        )

        assertEquals(5, lines.size)
        assertEquals(listOf("V", "D", "I", "W", "E"), lines.map {
            Json.parseToJsonElement(it).jsonObject.getValue("lvl").jsonPrimitive.contentOrNull
        })
        assertFalse(lines.last().contains("secret-token"), lines.last())
        assertFalse(lines.last().contains("user@example.com"), lines.last())
        assertTrue(lines.last().contains("IllegalStateException"), lines.last())

        val logcat = ShadowLog.getLogsForTag("Test")
        assertEquals(5, logcat.size)
        assertEquals("failed", logcat.last().msg)
        assertTrue(logcat.last().throwable is IllegalStateException)
    }

    @Test
    fun onlyExplicitBreadcrumbsReachThePersistentSink() {
        val lines = mutableListOf<String>()
        PrairieLog.setRendererForTests(
            DiagnosticsLogRenderer(
                redactor = redactor,
                strictAttributeRegistry = true,
                runId = "run-breadcrumb",
            ),
        )
        PrairieLog.installSink(null)
        PrairieLog.installBreadcrumbSink { lines += it }
        try {
            PrairieLog.i(DiagnosticsLogCategory.LIFECYCLE, "Test", "ordinary")
            PrairieLog.breadcrumb(DiagnosticsLogCategory.LIFECYCLE, "Test", "foreground")
        } finally {
            PrairieLog.installBreadcrumbSink(null)
        }

        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("foreground"))
    }
}
