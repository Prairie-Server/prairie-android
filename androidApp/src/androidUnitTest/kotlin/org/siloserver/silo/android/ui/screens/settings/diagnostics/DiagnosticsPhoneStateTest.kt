package org.siloserver.silo.android.ui.screens.settings.diagnostics

import org.siloserver.silo.common.diagnostics.DiagnosticsAvailabilityUi
import org.siloserver.silo.common.diagnostics.DiagnosticsConsentMode
import org.siloserver.silo.common.diagnostics.DiagnosticsReportSummary
import org.siloserver.silo.common.diagnostics.DiagnosticsUiState
import org.siloserver.silo.common.diagnostics.PendingReportStatus
import org.siloserver.silo.model.diagnostics.DiagnosticsReportType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsPhoneStateTest {
    @Test
    fun childHasNoDiagnosticsEntry() {
        val state = DiagnosticsUiState(
            availability = DiagnosticsAvailabilityUi.INELIGIBLE,
            profileEligible = false,
        )

        assertFalse(shouldShowDiagnosticsEntry(state))
    }

    @Test
    fun eligibleProfileKeepsEntryWhenServerIsDisabled() {
        val state = DiagnosticsUiState(
            availability = DiagnosticsAvailabilityUi.DISABLED,
            profileEligible = true,
        )

        assertTrue(shouldShowDiagnosticsEntry(state))
    }

    @Test
    fun disabledServerStillShowsPendingAndDeleteButNotUpload() {
        val model = diagnosticsPhoneScreenModel(
            DiagnosticsUiState(
                availability = DiagnosticsAvailabilityUi.DISABLED,
                profileEligible = true,
                pending = listOf(REPORT),
            ),
        )

        assertTrue(model.showPending)
        assertFalse(model.canUpload)
        assertTrue(model.canDelete)
    }

    @Test
    fun alwaysConsentRequiresExplicitConfirmation() {
        val action = consentActionModel(
            current = DiagnosticsConsentMode.ASK,
            requested = DiagnosticsConsentMode.ALWAYS,
        )

        assertTrue(action.requiresConfirmation)
    }

    private companion object {
        val REPORT = DiagnosticsReportSummary(
            id = "report-1",
            type = DiagnosticsReportType.CRASH,
            capturedAt = "2026-07-22T00:00:00Z",
            capturedAtEpochMs = 1_000,
            expiresAtEpochMs = 2_000,
            evidenceBytes = 512,
            destinationServerInstanceId = "server-1",
            capturedProfileId = "adult-1",
            archiveEntries = listOf("manifest.json", "device.json"),
            uploadStatus = PendingReportStatus.PENDING,
            uploadErrorCode = null,
        )
    }
}
