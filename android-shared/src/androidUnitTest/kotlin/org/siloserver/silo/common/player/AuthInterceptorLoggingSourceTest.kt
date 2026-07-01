package org.siloserver.silo.common.player

import kotlin.test.Test
import kotlin.test.assertFalse

class AuthInterceptorLoggingSourceTest {
    private val source = java.io.File(
        "../shared/src/commonMain/kotlin/org/siloserver/silo/network/AuthInterceptorImpl.kt",
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
            source.contains("[SiloAuth]"),
            "common auth refresh path must not emit ad-hoc SiloAuth logs",
        )
    }
}
