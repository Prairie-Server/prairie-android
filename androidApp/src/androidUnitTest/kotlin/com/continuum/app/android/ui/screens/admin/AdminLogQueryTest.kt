package com.continuum.app.android.ui.screens.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminLogQueryTest {

    @Test fun blankFieldsAreOmitted() {
        val q = buildLogQuery(level = null, query = "  ", component = "", limit = 100)
        assertFalse(q.containsKey("level")); assertFalse(q.containsKey("q"))
        assertFalse(q.containsKey("component")); assertEquals("100", q["limit"])
    }

    @Test fun setFieldsAreTrimmedAndIncluded() {
        val q = buildLogQuery(level = "error", query = "  timeout ", component = "scanner", limit = 50)
        assertEquals("error", q["level"]); assertEquals("timeout", q["q"])
        assertEquals("scanner", q["component"]); assertEquals("50", q["limit"])
    }

    @Test fun limitIsClampedToServerMax() {
        assertEquals("200", buildLogQuery(null, null, null, 500)["limit"])
        assertEquals("1", buildLogQuery(null, null, null, 0)["limit"])
    }

    @Test fun allLevelSentinelIsTreatedAsNoFilter() {
        assertFalse(buildLogQuery(level = "All", query = null, component = null, limit = 100).containsKey("level"))
    }

    @Test fun auditRowDetailLineCombinesMethodPathStatus() {
        assertEquals("GET /api/v1/admin/stats → 200", auditSummaryLine("get", "/api/v1/admin/stats", 200))
    }

    @Test fun appLevelSeverityOrderingForBadgeColorSelection() {
        assertTrue(logLevelRank("error") > logLevelRank("warn"))
        assertTrue(logLevelRank("warn") > logLevelRank("info"))
        assertEquals(0, logLevelRank("trace"))
    }
}
