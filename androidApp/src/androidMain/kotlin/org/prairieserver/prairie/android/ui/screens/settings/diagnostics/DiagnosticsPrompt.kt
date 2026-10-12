package org.prairieserver.prairie.android.ui.screens.settings.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.prairieserver.prairie.android.ui.components.PrairieConfirmDialog
import org.prairieserver.prairie.android.ui.components.PrairieDialog
import org.prairieserver.prairie.android.ui.components.PrairieDialogAction
import org.prairieserver.prairie.android.ui.components.PrairieDialogActionStyle
import org.prairieserver.prairie.common.diagnostics.DiagnosticsPrompt

@Composable
fun DiagnosticsPromptDialog(
    prompt: DiagnosticsPrompt,
    onReview: () -> Unit,
    onSend: () -> Unit,
    onAlwaysSend: () -> Unit,
    onDontSend: () -> Unit,
    allowAlwaysSend: Boolean = true,
) {
    var confirmAlways by remember { mutableStateOf(false) }
    if (confirmAlways && allowAlwaysSend) {
        PrairieConfirmDialog(
            title = "Always send crash reports?",
            body = "Future eligible reports may be uploaded automatically until you change this setting.",
            confirmLabel = "Always send",
            destructive = false,
            onConfirm = onAlwaysSend,
            onDismiss = { confirmAlways = false },
        )
        return
    }
    val reportDescription =
        if (prompt.reportCount == 1) {
            "A ${prompt.reportType.displayName().lowercase()} report is ready. " +
                "Review it before deciding whether to send it."
        } else {
            "${prompt.reportCount} diagnostics reports are ready. " +
                "Review them before deciding whether to send them."
        }
    PrairieDialog(
        title = "Prairie encountered a problem",
        message = if (allowAlwaysSend) {
            reportDescription
        } else {
            "$reportDescription\n\nThe report includes the Prairie app version and build, Android version, " +
                "device model, crash details, and diagnostic logs. Its pseudonymous credential is not " +
                "linked to an account on your self-hosted server. Username, email, profile, server " +
                "address, and playback session IDs are omitted. It never sends automatically and may " +
                "be retained for up to 30 days."
        },
        onDismissRequest = onDontSend,
        actions = buildList {
            add(PrairieDialogAction("Review", onReview, PrairieDialogActionStyle.Primary))
            add(PrairieDialogAction("Send", onSend))
            if (allowAlwaysSend) add(PrairieDialogAction("Always send", { confirmAlways = true }))
            add(PrairieDialogAction("Don't send", onDontSend))
        },
    )
}
