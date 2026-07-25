package org.prairieserver.prairie.network

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.util.AttributeKey

enum class DiagnosticsProfileHeaderMode {
    ACTIVE,
    SUPPRESS,
    EXACT,
}

data class DiagnosticsRequestScope(
    val mode: DiagnosticsProfileHeaderMode,
    val exactProfileId: String? = null,
) {
    init {
        require(mode == DiagnosticsProfileHeaderMode.EXACT || exactProfileId == null) {
            "exactProfileId is only valid for EXACT diagnostics requests"
        }
        require(mode != DiagnosticsProfileHeaderMode.EXACT || !exactProfileId.isNullOrBlank()) {
            "EXACT diagnostics requests require a profile id"
        }
    }
}

val DiagnosticsRequestScopeKey: AttributeKey<DiagnosticsRequestScope> =
    AttributeKey("PrairieDiagnosticsRequestScope")

fun HttpRequestBuilder.diagnosticsProfileScope(capturedProfileId: String?) {
    attributes.put(
        DiagnosticsRequestScopeKey,
        capturedProfileId?.let {
            DiagnosticsRequestScope(DiagnosticsProfileHeaderMode.EXACT, exactProfileId = it)
        } ?: DiagnosticsRequestScope(DiagnosticsProfileHeaderMode.SUPPRESS),
    )
}
