package org.prairieserver.prairie.model.diagnostics

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DiagnosticsStatusValidationTest {
    private val valid = DiagnosticsStatusResponse(
        status = DiagnosticsAvailabilityStatus.AVAILABLE,
        serverInstanceId = "srv-1",
        acceptedSchemaVersions = listOf(1, 2),
        maxBundleBytes = 1_000_000,
        maxManifestBytes = 64_000,
        retentionDays = 30,
        consentNoticeVersion = 1,
    )

    @Test
    fun wellFormedStatusValidates() {
        valid.validate()
    }

    @Test
    fun eachInvalidFieldIsReportedByPath() {
        val cases = mapOf(
            "status.server_instance_id" to valid.copy(serverInstanceId = ""),
            "status.accepted_schema_versions" to valid.copy(acceptedSchemaVersions = emptyList()),
            "status.accepted_schema_versions: must contain positive" to valid.copy(acceptedSchemaVersions = listOf(1, 0)),
            "status.max_bundle_bytes" to valid.copy(maxBundleBytes = 0),
            "status.max_manifest_bytes" to valid.copy(maxManifestBytes = -1),
            "status.retention_days" to valid.copy(retentionDays = 0),
            "status.consent_notice_version" to valid.copy(consentNoticeVersion = 0),
        )
        for ((path, status) in cases) {
            val error = assertFailsWith<DiagnosticsValidationException> { status.validate() }
            assertTrue(error.message.orEmpty().startsWith(path), "expected $path, got ${error.message}")
        }
    }
}
