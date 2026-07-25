package org.prairieserver.prairie.android.ui.screens.settings.diagnostics

import org.prairieserver.prairie.common.diagnostics.DiagnosticsAvailabilityUi
import org.prairieserver.prairie.common.diagnostics.DiagnosticsConsentMode
import org.prairieserver.prairie.common.diagnostics.DiagnosticsUiState

typealias DiagnosticsViewModel = org.prairieserver.prairie.common.diagnostics.DiagnosticsViewModel

data class DiagnosticsPhoneScreenModel(
    val showPending: Boolean,
    val canUpload: Boolean,
    val canDelete: Boolean,
    val canCapture: Boolean,
)

data class DiagnosticsConsentActionModel(
    val requiresConfirmation: Boolean,
)

fun shouldShowDiagnosticsEntry(state: DiagnosticsUiState): Boolean = state.profileEligible

fun diagnosticsPhoneScreenModel(state: DiagnosticsUiState): DiagnosticsPhoneScreenModel =
    DiagnosticsPhoneScreenModel(
        showPending = state.pending.isNotEmpty(),
        canUpload = state.profileEligible && state.availability == DiagnosticsAvailabilityUi.AVAILABLE,
        canDelete = state.profileEligible && state.pending.isNotEmpty(),
        canCapture = state.profileEligible && state.availability != DiagnosticsAvailabilityUi.OFFLINE,
    )

fun consentActionModel(
    current: DiagnosticsConsentMode,
    requested: DiagnosticsConsentMode,
): DiagnosticsConsentActionModel = DiagnosticsConsentActionModel(
    requiresConfirmation = requested == DiagnosticsConsentMode.ALWAYS && current != DiagnosticsConsentMode.ALWAYS,
)
