package org.prairieserver.prairie.common.player

import kotlin.test.Test
import kotlin.test.assertFalse

class AuthInterceptorLoggingSourceTest {
    private val source = java.io.File(
        "../shared/src/commonMain/kotlin/org/prairieserver/prairie/network/AuthInterceptorImpl.kt",
    ).readText()

    @Test
    fun authRefreshPathDoesNotPrintSensitiveDiagnostics() {
        assertFalse(
            source.contains("println("),
            "common auth refresh path must not print diagnostics in production",
        )
        assertFalse(
            source.contains("bodyAsText()"),
            "common auth refresh path must not read and log raw response bodies",
        )
        assertFalse(
            source.contains("[PrairieAuth]"),
            "common auth refresh path must not emit ad-hoc PrairieAuth logs",
        )
    }
}
